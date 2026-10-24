package com.vnap.village.home;

import com.vnap.dialogue.Speech;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.market.Materials;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A house grows by levels, as MineColonies and Millénaire buildings do: {@code house/<plan>_l2}, {@code _l3} are the
 * whole house of the next level in the same frame (a storey more, a cellar under it), and the family rebuilds its
 * house into it by hand while living in it: what the new level has no more (the old roof) is taken down first, then
 * what it adds goes up, paid for by what is missing only. The beds and doors keep their places, so nobody moves.
 * A cellar is found by itself: the level is laid over the last one at the depth where they match best, and so is a
 * level wider than the last (a stair flight outside a small house of the world).
 * <p>
 * The small houses of the generated villages have their plans too, {@code house/world/<template>}: copies of the
 * vanilla templates, each with a storey (its roof lifted, a stair flight outside up to a door, villagers walk no
 * ladders) and a cellar. A family in such a house takes it as its own where the world put it ({@link #adopt}) and
 * grows it like any other.
 */
public final class HouseLevels {
	/** Deepest cellar looked for under a ground floor. */
	static final int MAX_SINK = 8;
	/** The plans of the generated villages' small houses are {@code world/<template>}, never painted by a palette. */
	public static final String WORLD = "world/";
	/** The vanilla templates with a plan of their own (house/world/), by file name; a zombie village's house is the same house. */
	public static final List<String> WORLD_HOUSES = List.of(
			"plains_small_house_1", "plains_small_house_2", "plains_small_house_3", "plains_small_house_4",
			"plains_small_house_6", "plains_small_house_7", "plains_small_house_8",
			"desert_small_house_1", "desert_small_house_2", "desert_small_house_3", "desert_small_house_4",
			"desert_small_house_5", "desert_small_house_7", "desert_small_house_8",
			"savanna_small_house_1", "savanna_small_house_2", "savanna_small_house_3", "savanna_small_house_4",
			"savanna_small_house_6", "savanna_small_house_7", "savanna_small_house_8",
			"snowy_small_house_1", "snowy_small_house_2", "snowy_small_house_3", "snowy_small_house_4",
			"snowy_small_house_5", "snowy_small_house_6", "snowy_small_house_7", "snowy_small_house_8",
			"taiga_small_house_1", "taiga_small_house_2", "taiga_small_house_3", "taiga_small_house_4", "taiga_small_house_5");
	/** Why the last upgrade did not fit (for /vnap family room). */
	public static String refusal = "";

	private HouseLevels() {
	}

	public static int level(String plan) {
		int at = plan.lastIndexOf("_l");
		if (at < 0) return 1;
		try {
			return Integer.parseInt(plan.substring(at + 2));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	public static String base(String plan) {
		return level(plan) > 1 ? plan.substring(0, plan.lastIndexOf("_l")) : plan;
	}

	/** The plan of the next level if there is one (a hall, a stall and a plan without levels have none). */
	public static @Nullable String next(ServerLevel level, String plan) {
		String base = base(plan);
		if (Mayors.HALLS.contains(base) || com.vnap.village.market.Shops.STALLS.contains(base)) return null;
		String next = base + "_l" + (level(plan) + 1);
		return com.vnap.village.script.SchematicSpec.exists(level.getServer().getResourceManager(), "house/" + next)
				&& !Workshops.schematic(level, "house/" + next).blocks.isEmpty() ? next : null;
	}

	/**
	 * Where the last level lies in the next one's plan: how much deeper its ground floor (a cellar under it) and how far
	 * in (a wider level), where the two ground floors match best.
	 */
	public static BlockPos offset(SchematicSpec last, SchematicSpec next, int groundY) {
		Map<BlockPos, net.minecraft.world.level.block.Block> cells = new HashMap<>();
		for (SchematicSpec.PlacedBlock block : next.blocks) cells.put(block.rel(), block.state().getBlock());
		BlockPos best = BlockPos.ZERO;
		int bestScore = -1;
		for (int dy = 0; dy <= MAX_SINK; dy++) {
			for (int dx = 0; dx <= Math.max(0, next.sizeX - last.sizeX); dx++) {
				for (int dz = 0; dz <= Math.max(0, next.sizeZ - last.sizeZ); dz++) {
					int score = 0;
					// the ground floor and its first course only: a level with a storey more matches a storey up just as well
					for (SchematicSpec.PlacedBlock block : last.blocks) {
						if (block.rel().getY() <= groundY + 1 && !block.state().isAir()
								&& block.state().getBlock().equals(cells.get(block.rel().offset(dx, dy, dz)))) score++;
					}
					if (score > bestScore) {
						bestScore = score;
						best = new BlockPos(dx, dy, dz);
					}
				}
			}
		}
		return best;
	}

	/** The plan's ground floor: under its lowest door. */
	public static int floorOf(SchematicSpec spec) {
		int floor = Integer.MAX_VALUE;
		for (SchematicSpec.PlacedBlock block : spec.blocks) {
			if (block.state().getBlock() instanceof DoorBlock) floor = Math.min(floor, block.rel().getY() - 1);
		}
		return floor == Integer.MAX_VALUE ? 0 : floor;
	}

	/**
	 * A small house the world generated, taken as {@code head}'s own where it stands: a finished house of the plan copied
	 * from its template, ready to grow ({@link #upgrade}). Null when the template has no plan.
	 */
	public static @Nullable HouseProject adopt(ServerLevel level, Villager head, HomeProjects.WorldPiece piece, long day) {
		String plan = WORLD + piece.template();
		BlockPos bell = Towns.bellOf(head);
		if (bell == null || !WORLD_HOUSES.contains(piece.template())) return null;
		HouseProject house = new HouseProject(bell.asLong(), bell, head.getUUID(), plan, HousePalette.forBiome(level.getBiome(piece.origin())),
				piece.origin(), piece.rotation(), day);
		house.done = true;
		house.groundY = floorOf(house.schematic(level));
		return house;
	}

	/**
	 * Starts rebuilding the head's house into its next level: the cells to take down, the new project in place of the
	 * old one and the family's money for what has to go up (not while it owes the treasury a purse or more). Null when there is no next level or it does not fit.
	 */
	public static @Nullable HouseProject upgrade(ServerLevel level, Villager head, HouseProject house, long day) {
		String plan = next(level, house.plan);
		if (plan == null || house.rooms > 0 || !house.done || house.mending) return null;
		return rebuild(level, head, house, plan, house.palette, day);
	}

	/**
	 * The second age (docs/tower/CITY2.md §3): a finished house (of the mod or a grown one of the world, at any level: its
	 * next level keeps the palette) rebuilt in its biome's fine palette, the same plan in the same place, what differs taken down and set anew by hand, as a level is. Only with
	 * the household's own coins (no loan, nothing of the town's): it is no need. Null when it is not due or not affordable.
	 */
	public static @Nullable HouseProject restyle(ServerLevel level, Villager head, HouseProject house, long day) {
		if (HousePalette.fine(house.palette) || house.rooms > 0 || !house.done || house.mending
				|| Mayors.HALLS.contains(house.plan) || com.vnap.village.market.Shops.STALLS.contains(house.plan)) return null;
		return rebuild(level, head, house, house.plan, HousePalette.finer(house.palette), day);
	}

	/** What a restyle of {@code house} costs: what its fine palette sets that does not stand. */
	public static int restyleCost(ServerLevel level, HouseProject house) {
		SchematicSpec spec = Workshops.schematic(level, HousePalette.schematicName(house.plan, HousePalette.finer(house.palette)));
		return (int) Math.ceil(Materials.cost(bill(level, spec, house.origin, house)) * 1.1);
	}

	/**
	 * A town with a risen spire restyles one house a day (Roads.plan): none while one is still going up, the cheapest
	 * that its family can pay for first. The house restyled, or null.
	 */
	public static @Nullable HouseProject secondAge(ServerLevel level, com.vnap.village.town.Roads.Network network) {
		com.vnap.spire.Spires.Spire spire = com.vnap.spire.Spires.get(level.getServer()).ofTown(level.dimension(), network.bell);
		if (spire == null || spire.shown <= 0) return null;
		List<HouseProject> mine = new ArrayList<>();
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.bell.equals(network.bell)) continue;
			if (HousePalette.fine(house.palette) && !house.done) return null;
			if (house.done && level.getEntity(house.owner) instanceof Villager) mine.add(house);
		}
		mine.sort(Comparator.comparingInt(house -> restyleCost(level, house)));
		long day = com.vnap.dialogue.Family.day(level);
		for (HouseProject house : mine) {
			if (!(level.getEntity(house.owner) instanceof Villager owner) || owner.isBaby()) continue;
			HouseProject restyled = restyle(level, owner, house, day);
			if (restyled == null) continue;
			com.vnap.village.town.Chronicle.note(level, network.bell, "restyle", com.vnap.dialogue.VillagerHome.nameOf(owner));
			com.vnap.VillagerLife.trace("vnap restyle: {} at {} in {}", com.vnap.dialogue.VillagerHome.nameOf(owner), house.origin.toShortString(), restyled.palette);
			return restyled;
		}
		return null;
	}

	private static @Nullable HouseProject rebuild(ServerLevel level, Villager head, HouseProject house, String plan, String palette, long day) {
		boolean restyle = !palette.equals(house.palette);
		SchematicSpec last = house.schematic(level);
		SchematicSpec spec = Workshops.schematic(level, HousePalette.schematicName(plan, palette));
		if (spec.sizeX < last.sizeX || spec.sizeZ < last.sizeZ) return null;
		BlockPos shift = restyle ? BlockPos.ZERO : offset(last, spec, house.groundY);
		BlockPos origin = house.origin.subtract(shift.rotate(house.rotation));
		// the ground given away round it but its own (its own lot, the generated village's piece it stands for)
		BlockPos centre = house.footprint(level).getCenter();
		com.vnap.village.town.Land others = com.vnap.village.town.Land.around(level, house.origin).without(claim -> claim.box().equals(house.footprint(level))
				|| (claim.layer() != com.vnap.village.town.Land.Layer.WAY && claim.box().isInside(centre)));
		List<BlockPos> moved = new ArrayList<>();
		if (!fits(level, spec, origin, house, others, house.groundY + shift.getY(), moved)) return null;
		HouseProject grown = new HouseProject(house.village, house.bell, head.getUUID(), plan, palette, origin, house.rotation, day);
		grown.groundY = house.groundY + shift.getY();
		grown.clearFloor = Integer.MIN_VALUE;
		// the pictures on its walls stay: only an old house's own blocks are taken down
		grown.clearArea = null;
		grown.stash.addAll(house.stash);
		grown.clearing.addAll(moved);
		grown.clearing.addAll(takeDown(level, house, last, spec, origin));
		grown.clearing.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
		Map<Item, Integer> bill = bill(level, spec, origin, house);
		// the household pays first (a storey only on credit it can carry): the old house stays until it can
		boolean paid;
		if (restyle) {
			int cost = (int) Math.ceil(Materials.cost(bill) * 1.1);
			// its own coins, a bread's reserve left: the treasury takes them back before it lays out the bill, so it mints nothing
			paid = com.vnap.village.market.Market.funds(level, head) >= cost + com.vnap.village.town.TownRules.RESERVE * 2
					&& com.vnap.village.market.Housing.build(level, head, house.bell, grown.site, bill, com.vnap.village.market.Housing.Purpose.GROWTH, 0);
			if (paid) {
				VillageMoodSavedData.get(level).houses().add(grown);
				VillageMoodSavedData.get(level).markDirty();
			}
		} else {
			paid = HomeProjects.register(level, grown, bill);
		}
		if (!paid) {
			refusal = "cannot pay for it";
			return null;
		}
		VillageMoodSavedData.get(level).houses().remove(house);
		if (!restyle) Speech.queue(head, "Man Starts a Room", null, 2L);
		return grown;
	}

	/** Every cell of the next level is free, its own house's or natural ground (a cellar is dug into it). */
	private static boolean fits(ServerLevel level, SchematicSpec spec, BlockPos origin, HouseProject house, com.vnap.village.town.Land others, int groundY,
								List<BlockPos> moved) {
		java.util.Set<BlockPos> own = new java.util.HashSet<>();
		for (SchematicSpec.PlacedBlock block : house.schematic(level).blocks) own.add(BuildJob.at(house.origin, house.rotation, block.rel()));
		for (SchematicSpec.PlacedBlock block : spec.blocks) {
			BlockPos pos = BuildJob.at(origin, house.rotation, block.rel());
			if (!level.isLoaded(pos)) return refuse("not loaded at " + pos.toShortString());
			if (others.blocked(pos, 1)) return refuse("another building at " + pos.toShortString());
			if (block.state().isAir() || own.contains(pos)) continue;
			BlockState current = level.getBlockState(pos);
			if (current.is(block.state().getBlock())) continue;
			// the household's things in the way are moved out (kept in the stash); a sleeping baby's cradle is not
			if (com.vnap.dialogue.Infants.occupied(level, pos)) return refuse("a baby sleeps in its cradle at " + pos.toShortString());
			if (HomeProjects.furnishing(current) || current.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
				moved.add(pos.immutable());
				continue;
			}
			if (!current.getFluidState().isEmpty() || !BuildJob.canTake(current, block.rel().getY() <= groundY)) {
				return refuse(current.getBlock().getDescriptionId() + " at " + pos.toShortString());
			}
		}
		return true;
	}

	private static boolean refuse(String why) {
		refusal = why;
		return false;
	}

	/**
	 * The old level's blocks the new one has no more or has otherwise (its roof under the new storey), still standing,
	 * top down; beds, doors and anything holding things stay where they are.
	 */
	static List<BlockPos> takeDown(ServerLevel level, HouseProject house, SchematicSpec last, SchematicSpec spec, BlockPos origin) {
		Map<BlockPos, BlockState> wanted = new HashMap<>();
		for (SchematicSpec.PlacedBlock block : spec.blocks) wanted.put(BuildJob.at(origin, house.rotation, block.rel()), block.state());
		List<BlockPos> out = new ArrayList<>();
		for (SchematicSpec.PlacedBlock block : last.blocks) {
			BlockState state = block.state();
			// the ground under the floor stays; a snow house's snow above it is the house
			if (state.isAir() || (BuildJob.naturalGround(state) && block.rel().getY() <= house.groundY)
					|| state.getBlock() instanceof BedBlock || state.getBlock() instanceof DoorBlock) continue;
			BlockPos pos = BuildJob.at(house.origin, house.rotation, block.rel());
			BlockState there = level.getBlockState(pos);
			if (!there.is(state.getBlock()) || there.hasBlockEntity()) continue;
			BlockState next = wanted.get(pos);
			// the old eaves' stair where a step of the new flight goes is turned another way: it comes down
			if (next != null && next.is(state.getBlock()) && !turned(next.rotate(level, pos, house.rotation), there)) continue;
			out.add(pos.immutable());
		}
		out.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
		return out;
	}

	/** A stair or a slab the new level has the other way up or round (its shape follows the neighbours, so not that). */
	private static boolean turned(BlockState wanted, BlockState there) {
		if (wanted.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
			return wanted.getValue(net.minecraft.world.level.block.StairBlock.FACING) != there.getValue(net.minecraft.world.level.block.StairBlock.FACING)
					|| wanted.getValue(net.minecraft.world.level.block.StairBlock.HALF) != there.getValue(net.minecraft.world.level.block.StairBlock.HALF);
		}
		if (wanted.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
			return wanted.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) != there.getValue(net.minecraft.world.level.block.SlabBlock.TYPE);
		}
		return false;
	}

	/** What the next level takes that does not stand yet (what is taken down and set again is paid for too). */
	private static Map<Item, Integer> bill(ServerLevel level, SchematicSpec spec, BlockPos origin, HouseProject house) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (SchematicSpec.PlacedBlock block : spec.blocks) {
			if (block.state().isAir()) continue;
			BlockPos pos = BuildJob.at(origin, house.rotation, block.rel());
			if (level.getBlockState(pos).is(block.state().getBlock())) continue;
			Materials.add(out, Materials.items(block.state()));
		}
		return out;
	}

	/** Beds standing in the house (the head end of each). */
	public static int beds(ServerLevel level, HouseProject house) {
		BoundingBox box = house.footprint(level);
		int count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) count++;
		}
		return count;
	}
}
