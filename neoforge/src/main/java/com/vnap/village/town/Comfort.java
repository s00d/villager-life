package com.vnap.village.town;

import com.vnap.dialogue.Family;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Market;
import com.vnap.village.market.Materials;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A home grows past its floors as its family prospers (the town spends what it earns: the player only helps). After its
 * yard, a step at a time, out of the family's own purse and never on credit, laid by the town's builders: a porch
 * over the front door (posts, a roof of slabs, lanterns), a kitchen garden along the back (beds of farmland, crops,
 * a water channel), a lean-to shed on a side wall (log posts, a sloping roof, a chest, hay, a woodpile), and the
 * rooms dressed (carpets, a bookshelf, flowers and a candle where a thing may stand). One step a town a day; a step
 * with no room for it is skipped for the next.
 */
public final class Comfort {
	/** The steps, in the order a household takes them. */
	enum Step {
		PORCH, GARDEN, SHED, ROOMS, GREENHOUSE, ORCHARD
	}

	/** What its steps take, in every palette, for the shop's catalogue (Market.building): a house's worth of each. */
	public static List<SchematicSpec> kit() {
		List<SchematicSpec> out = new ArrayList<>();
		for (String palette : HousePalette.ALL) {
			List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
			int[] n = {0};
			java.util.function.BiConsumer<BlockState, Integer> add = (state, count) -> {
				for (int i = 0; i < count; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n[0]++, 0, 0), state));
			};
			add.accept(Roads.palette(Blocks.OAK_FENCE.defaultBlockState(), palette), 4);
			add.accept(Roads.palette(Blocks.OAK_SLAB.defaultBlockState(), palette), 6);
			add.accept(Roads.palette(Blocks.OAK_STAIRS.defaultBlockState(), palette), 6);
			add.accept(Roads.palette(Blocks.OAK_LOG.defaultBlockState(), palette), 4);
			add.accept(Blocks.OAK_LOG.defaultBlockState(), 6);
			add.accept(Blocks.LANTERN.defaultBlockState(), 3);
			add.accept(Blocks.FARMLAND.defaultBlockState(), 8);
			add.accept(Blocks.WHEAT.defaultBlockState(), 8);
			add.accept(Blocks.CHEST.defaultBlockState(), 1);
			add.accept(Blocks.HAY_BLOCK.defaultBlockState(), 1);
			add.accept(Blocks.GLASS.defaultBlockState(), 6);
			add.accept(Blocks.GLASS_PANE.defaultBlockState(), 2);
			add.accept(Blocks.MOSS_BLOCK.defaultBlockState(), 3);
			add.accept(Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(), 36);
			add.accept(Blocks.RED_CARPET.defaultBlockState(), 9);
			for (BlockState pot : new BlockState[] {Blocks.POTTED_ALLIUM.defaultBlockState(), Blocks.POTTED_ORANGE_TULIP.defaultBlockState(),
					Blocks.POTTED_BLUE_ORCHID.defaultBlockState(), Blocks.POTTED_RED_TULIP.defaultBlockState(), Blocks.POTTED_FERN.defaultBlockState(),
					Blocks.POTTED_AZALEA.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState(), Blocks.CHISELED_BOOKSHELF.defaultBlockState(), Blocks.CANDLE.defaultBlockState()}) {
				add.accept(pot, 1);
			}
			out.add(new SchematicSpec(n[0], 1, 1, List.of(), blocks, BlockPos.ZERO));
		}
		return out;
	}

	/** Why the last step had no room (debug). */
	private static String why = "";

	/** Days before a house that could not pay (or had no room) is looked at again. */
	static final int RETRY_DAYS = 3;

	private Comfort() {
	}

	private record Home(long key, BoundingBox box, List<Facade.Opening> doors, int floor, Villager payer, @Nullable BlockPos bed) {
	}

	/** Daily (Roads.plan): the next step for one household of the town that has its yard and the money for it. */
	public static void plan(ServerLevel level, Roads roads, Roads.Network network, long day) {
		for (Home home : homes(level, network)) {
			Long yard = network.yards.get(home.key);
			if (yard == null || yard != Yards.LAID) continue;
			long state = network.comfort.getOrDefault(home.key, 0L);
			int step = (int) (state & 0xFF);
			long tried = state >>> 8;
			if (step >= Step.values().length || (tried > 0 && day - tried < RETRY_DAYS)) continue;
			List<SchematicSpec.PlacedBlock> blocks = layout(level, home, Step.values()[step]);
			if (blocks == null || blocks.isEmpty()) {
				// no room for this one: on to the next step another day
				network.comfort.put(home.key, (day << 8) | (step + 1));
				roads.setDirty();
				continue;
			}
			int cost = (int) Math.ceil(Materials.cost(Materials.bill(new SchematicSpec(1, 1, 1, List.of(), blocks, BlockPos.ZERO))) * 1.1);
			if (Market.funds(level, home.payer) < cost + TownRules.RESERVE) {
				network.comfort.put(home.key, (day << 8) | step);
				roads.setDirty();
				continue;
			}
			roads.queueFor(level, network, blocks, "comfort_" + Step.values()[step].name().toLowerCase(java.util.Locale.ROOT), home.payer);
			for (SchematicSpec.PlacedBlock block : blocks) if (!block.state().isAir()) network.features.add(block.rel().immutable());
			network.comfort.put(home.key, (day << 8) | (step + 1));
			roads.setDirty();
			com.vnap.VillagerLife.trace("vnap comfort: " + Step.values()[step] + " for " + home.box + " (" + cost + ")");
			return;
		}
	}

	/** Debug (/vnap roads comfort): the next step of the house nearest {@code pos} laid out now, the treasury paying. */
	public static String debugNear(ServerLevel level, Roads roads, Roads.Network network, BlockPos pos) {
		Home home = homes(level, network).stream().min(java.util.Comparator.comparingDouble(h -> h.box.getCenter().distSqr(pos))).orElse(null);
		if (home == null) return "no house with a family near";
		int step = (int) (network.comfort.getOrDefault(home.key, 0L) & 0xFF);
		// all done: round again from the first (debug only, to look at the steps once more)
		if (step >= Step.values().length) step = 0;
		why = "";
		List<SchematicSpec.PlacedBlock> blocks = layout(level, home, Step.values()[step]);
		network.comfort.put(home.key, (long) (step + 1));
		roads.setDirty();
		if (blocks == null || blocks.isEmpty()) return Step.values()[step] + ": no room round " + home.box + (why.isEmpty() ? "" : " (" + why + ")");
		roads.queue(level, network, blocks, "comfort_" + Step.values()[step].name().toLowerCase(java.util.Locale.ROOT));
		return Step.values()[step] + ": " + blocks.size() + " blocks from " + blocks.get(0).rel().toShortString() + " for " + home.box;
	}

	/** The town's households in their homes (its own houses and the world's), each with who pays. */
	private static List<Home> homes(ServerLevel level, Roads.Network network) {
		List<Home> out = new ArrayList<>();
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.done || house.player != null || !Towns.ours(level, network.bell, house.origin) || !level.isLoaded(house.origin)) continue;
			if (!(level.getEntity(house.owner) instanceof Villager owner) || !owner.isAlive()) continue;
			BoundingBox box = house.footprint(level);
			out.add(new Home(BlockPos.asLong(box.minX(), box.minY(), box.minZ()), box, Facade.doors(level, house), house.origin.getY(), owner,
					owner instanceof VillagerLifeData data ? data.vnap$homeBed() : null));
		}
		for (BoundingBox box : network.worldHouses) {
			if (!level.isLoaded(BlockPos.containing(box.getCenter().getX(), box.getCenter().getY(), box.getCenter().getZ()))) continue;
			Villager head = level.getEntitiesOfClass(Villager.class, AABB.of(box).inflate(TownRules.NEAR_BELL), v -> v.isAlive() && Family.isHead(v)
					&& v instanceof VillagerLifeData data && data.vnap$homeBed() != null && box.isInside(data.vnap$homeBed())).stream().findFirst().orElse(null);
			if (head == null) continue;
			List<Facade.Opening> doors = Facade.doors(level, box);
			if (doors.isEmpty()) continue;
			out.add(new Home(BlockPos.asLong(box.minX(), box.minY(), box.minZ()), box, doors, doors.get(0).pos().getY() - 1, head,
					head instanceof VillagerLifeData data ? data.vnap$homeBed() : null));
		}
		return out;
	}

	private static @Nullable List<SchematicSpec.PlacedBlock> layout(ServerLevel level, Home home, Step step) {
		if (home.doors.isEmpty()) return null;
		String palette = HousePalette.forBiome(level.getBiome(home.doors.get(0).pos()));
		return switch (step) {
			case PORCH -> porch(level, home, palette);
			case GARDEN -> garden(level, home);
			case SHED -> shed(level, home, palette);
			case ROOMS -> rooms(level, home);
			case GREENHOUSE -> greenhouse(level, home, palette);
			case ORCHARD -> orchard(level, home);
		};
	}

	/**
	 * The cell, or the one under or over it, that is free to build in: a village door is often a step above its ground,
	 * and a cell at the door's height stood over air ("no room" at nearly every house of the world).
	 */
	private static @Nullable BlockPos settle(ServerLevel level, BlockPos at, int floor) {
		for (BlockPos cell : new BlockPos[] {at, at.below(), at.above()}) if (open(level, cell, floor)) return cell;
		return null;
	}

	/** A cell in front of the house that is free to build in: air (or grass) over solid ground at about the floor. */
	private static boolean open(ServerLevel level, BlockPos at, int floor) {
		BlockState there = level.getBlockState(at);
		BlockState under = level.getBlockState(at.below());
		// a path or a field before a village door is ground too (not a full block: it was "no room" at nearly every house)
		return Math.abs(at.getY() - 1 - floor) <= 1 && (there.isAir() || there.canBeReplaced() || clearable(there)) && level.getBlockState(at.above()).isAir()
				&& (under.isFaceSturdy(level, at.below(), Direction.UP) || under.is(Blocks.DIRT_PATH) || under.is(Blocks.FARMLAND)
				|| under.is(net.minecraft.tags.BlockTags.STAIRS) || under.is(net.minecraft.tags.BlockTags.SLABS));
	}

	/** What a step takes up where it goes: the yard's flower bed before the door (a porch was "no room" over its pots). */
	private static boolean clearable(BlockState state) {
		return state.is(net.minecraft.tags.BlockTags.FLOWER_POTS) || state.is(net.minecraft.tags.BlockTags.FLOWERS);
	}

	/** A porch: two posts a step out either side of the front door, a slab roof over them, a lantern under each side. */
	private static @Nullable List<SchematicSpec.PlacedBlock> porch(ServerLevel level, Home home, String palette) {
		Facade.Opening door = home.doors.get(0);
		Direction out = door.out();
		Direction across = out.getClockWise();
		BlockPos foot = door.pos();
		List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
		BlockState post = Roads.palette(Blocks.OAK_FENCE.defaultBlockState(), palette);
		BlockState roof = Roads.palette(Blocks.OAK_SLAB.defaultBlockState(), palette).trySetValue(SlabBlock.TYPE, SlabType.BOTTOM);
		for (int a = -1; a <= 1; a++) {
			for (int d = 1; d <= 2; d++) {
				BlockPos at = foot.relative(out, d).relative(across, a);
				BlockPos cell = settle(level, at, home.floor);
				// a lamp already over the door stays where it hangs: the roof goes round it
				// (and so does the house's sign: whatever hangs there with nothing to walk into)
				boolean light = cell != null && !level.getBlockState(cell.above(2)).isAir()
						&& level.getBlockState(cell.above(2)).getCollisionShape(level, cell.above(2)).isEmpty();
				if (cell == null || (!light && !level.getBlockState(cell.above(2)).isAir())) {
					why = "porch cell " + at.toShortString() + ": " + (cell == null ? level.getBlockState(at) + " over " + level.getBlockState(at.below())
							: "over it " + level.getBlockState(cell.above(2)));
					return null;
				}
				if (clearable(level.getBlockState(cell))) blocks.add(new SchematicSpec.PlacedBlock(cell, Blocks.AIR.defaultBlockState()));
				if (!light) blocks.add(new SchematicSpec.PlacedBlock(cell.above(2), roof));
				if (d == 2 && a != 0) {
					blocks.add(new SchematicSpec.PlacedBlock(cell, post));
					blocks.add(new SchematicSpec.PlacedBlock(cell.above(), post));
				}
				if (d == 1 && a != 0) blocks.add(new SchematicSpec.PlacedBlock(cell.above(), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)));
			}
		}
		return blocks;
	}

	/**
	 * A kitchen garden along the back wall, a row out from it: farmland with the crops of its biome, a channel of
	 * water down its middle to keep it wet.
	 */
	private static @Nullable List<SchematicSpec.PlacedBlock> garden(ServerLevel level, Home home) {
		// behind the house first; a house backed onto a slope or a neighbour has it by a side wall
		Direction out = home.doors.get(0).out();
		for (Direction back : new Direction[] {out.getOpposite(), out.getClockWise(), out.getCounterClockWise()}) {
			List<SchematicSpec.PlacedBlock> bed = garden(level, home, back);
			if (bed != null) return bed;
		}
		return null;
	}

	private static @Nullable List<SchematicSpec.PlacedBlock> garden(ServerLevel level, Home home, Direction back) {
		Direction along = back.getClockWise();
		BoundingBox box = home.box;
		int span = back.getAxis() == Direction.Axis.X ? box.getZSpan() : box.getXSpan();
		BlockPos edge = BlockPos.containing(box.getCenter().getX(), home.floor + 1, box.getCenter().getZ());
		while (box.isInside(edge)) edge = edge.relative(back);
		BlockState[] crops = {Blocks.WHEAT.defaultBlockState(), Blocks.CARROTS.defaultBlockState(), Blocks.POTATOES.defaultBlockState(),
				Blocks.BEETROOTS.defaultBlockState()};
		BlockState crop = crops[Math.floorMod(java.util.Objects.hash(box.minX(), box.minZ()), crops.length)];
		List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
		int planted = 0;
		for (int i = -(span / 2 - 1); i <= span / 2 - 1; i++) {
			BlockPos cell = settle(level, edge.relative(back).relative(along, i), home.floor);
			if (cell == null || !level.getBlockState(cell.below()).is(net.minecraft.tags.BlockTags.DIRT)) continue;
			if (clearable(level.getBlockState(cell))) blocks.add(new SchematicSpec.PlacedBlock(cell, Blocks.AIR.defaultBlockState()));
			if (i == 0) {
				blocks.add(new SchematicSpec.PlacedBlock(cell.below(), Blocks.WATER.defaultBlockState()));
				continue;
			}
			blocks.add(new SchematicSpec.PlacedBlock(cell.below(), Blocks.FARMLAND.defaultBlockState()));
			blocks.add(new SchematicSpec.PlacedBlock(cell, crop.trySetValue(CropBlock.AGE, 0)));
			planted++;
		}
		return planted >= 3 ? blocks : null;
	}

	/** A lean-to shed against a side wall: log posts, a sloping roof of stairs, a chest, a bale of hay and a woodpile under it. */
	private static @Nullable List<SchematicSpec.PlacedBlock> shed(ServerLevel level, Home home, String palette) {
		return leanTo(level, home, palette, false);
	}

	/**
	 * A glasshouse against the other side wall (the shed takes the first): log posts, a roof and sides of glass, flowers
	 * in pots and moss on the floor under it.
	 */
	private static @Nullable List<SchematicSpec.PlacedBlock> greenhouse(ServerLevel level, Home home, String palette) {
		return leanTo(level, home, palette, true);
	}

	private static @Nullable List<SchematicSpec.PlacedBlock> leanTo(ServerLevel level, Home home, String palette, boolean glass) {
		Direction front = home.doors.get(0).out();
		Direction[] sides = glass ? new Direction[] {front.getCounterClockWise(), front.getClockWise()} : new Direction[] {front.getClockWise(), front.getCounterClockWise()};
		for (Direction side : sides) {
			BoundingBox box = home.box;
			BlockPos wall = BlockPos.containing(box.getCenter().getX(), home.floor + 1, box.getCenter().getZ());
			while (box.isInside(wall)) wall = wall.relative(side);
			List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
			boolean room = true;
			// where its inside stands, one a step along the wall (settled to the ground)
			BlockPos[] inner = new BlockPos[3];
			BlockState log = Roads.palette(Blocks.OAK_LOG.defaultBlockState(), palette);
			BlockState stair = glass ? Blocks.GLASS.defaultBlockState()
					: Roads.palette(Blocks.OAK_STAIRS.defaultBlockState(), palette).trySetValue(StairBlock.FACING, side.getOpposite());
			for (int a = -1; a <= 1 && room; a++) {
				for (int d = 0; d <= 1; d++) {
					BlockPos cell = settle(level, wall.relative(front, a).relative(side, d), home.floor);
					if (cell == null || !level.getBlockState(cell.above(2)).isAir()) {
						room = false;
						break;
					}
					if (d == 0) inner[a + 1] = cell;
					if (clearable(level.getBlockState(cell))) blocks.add(new SchematicSpec.PlacedBlock(cell, Blocks.AIR.defaultBlockState()));
					blocks.add(new SchematicSpec.PlacedBlock(cell.above(2), stair));
					if (d == 1 && a != 0) {
						blocks.add(new SchematicSpec.PlacedBlock(cell, log));
						blocks.add(new SchematicSpec.PlacedBlock(cell.above(), log));
					}
				}
			}
			if (!room) continue;
			if (glass) {
				// glass walls on its open sides, pots of flowers on moss inside
				for (int a = -1; a <= 1; a += 2) {
					blocks.add(new SchematicSpec.PlacedBlock(inner[a + 1].above(), Blocks.GLASS_PANE.defaultBlockState()));
				}
				BlockState[] pots = {Blocks.POTTED_ALLIUM.defaultBlockState(), Blocks.POTTED_ORANGE_TULIP.defaultBlockState(), Blocks.POTTED_BLUE_ORCHID.defaultBlockState()};
				for (int a = -1; a <= 1; a++) {
					blocks.add(new SchematicSpec.PlacedBlock(inner[a + 1].below(), Blocks.MOSS_BLOCK.defaultBlockState()));
					blocks.add(new SchematicSpec.PlacedBlock(inner[a + 1], pots[a + 1]));
				}
				return blocks;
			}
			blocks.add(new SchematicSpec.PlacedBlock(inner[0], Blocks.CHEST.defaultBlockState()
					.setValue(net.minecraft.world.level.block.ChestBlock.FACING, side)));
			blocks.add(new SchematicSpec.PlacedBlock(inner[1], Blocks.HAY_BLOCK.defaultBlockState()));
			blocks.add(new SchematicSpec.PlacedBlock(inner[2], log.trySetValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
					front.getClockWise().getAxis())));
			return blocks;
		}
		return null;
	}

	/**
	 * Two flowering trees in the yard, back corners off the house: a short trunk under a round crown of flowering azalea
	 * leaves (kept: they do not fall), where the ground is the town's own and free.
	 */
	private static @Nullable List<SchematicSpec.PlacedBlock> orchard(ServerLevel level, Home home) {
		Direction out = home.doors.get(0).out();
		Direction back = out.getOpposite();
		BoundingBox box = home.box;
		List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
		int trees = 0;
		for (Direction side : new Direction[] {out.getClockWise(), out.getCounterClockWise()}) {
			BlockPos spot = BlockPos.containing(box.getCenter().getX(), home.floor + 1, box.getCenter().getZ());
			while (box.isInside(spot)) spot = spot.relative(side);
			// a step in from the yard's ring (three out), and back from the front
			spot = spot.relative(side, 1).relative(back, 2);
			spot = settle(level, spot, home.floor);
			if (spot == null || !level.getBlockState(spot.below()).is(net.minecraft.tags.BlockTags.DIRT)) continue;
			boolean clear = true;
			for (BlockPos cell : BlockPos.betweenClosed(spot.offset(-1, 2, -1), spot.offset(1, 4, 1))) clear &= level.getBlockState(cell).isAir();
			if (!clear) continue;
			BlockState leaves = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
			if (clearable(level.getBlockState(spot))) blocks.add(new SchematicSpec.PlacedBlock(spot, Blocks.AIR.defaultBlockState()));
			for (int y = 0; y < 3; y++) blocks.add(new SchematicSpec.PlacedBlock(spot.above(y), Blocks.OAK_LOG.defaultBlockState()));
			for (BlockPos cell : BlockPos.betweenClosed(spot.offset(-1, 2, -1), spot.offset(1, 3, 1))) {
				if (cell.getX() == spot.getX() && cell.getZ() == spot.getZ() && cell.getY() == spot.getY() + 2) continue;
				blocks.add(new SchematicSpec.PlacedBlock(cell.immutable(), leaves));
			}
			blocks.add(new SchematicSpec.PlacedBlock(spot.above(4), leaves));
			trees++;
		}
		return trees > 0 ? blocks : null;
	}

	/** The rooms dressed: a carpet down the middle of the main room, a bookshelf, flowers and a candle where a thing may stand. */
	private static @Nullable List<SchematicSpec.PlacedBlock> rooms(ServerLevel level, Home home) {
		if (home.bed == null) return null;
		java.util.Set<BlockPos> room = com.vnap.dialogue.FamilyHome.room(level, home.bed);
		if (room.isEmpty()) return null;
		BlockState[] carpets = {Blocks.RED_CARPET.defaultBlockState(), Blocks.BLUE_CARPET.defaultBlockState(), Blocks.GREEN_CARPET.defaultBlockState(),
				Blocks.BROWN_CARPET.defaultBlockState(), Blocks.CYAN_CARPET.defaultBlockState()};
		BlockState carpet = carpets[Math.floorMod(java.util.Objects.hash(home.box.minX(), home.box.minZ()), carpets.length)];
		double cx = room.stream().mapToInt(BlockPos::getX).average().orElse(0);
		double cz = room.stream().mapToInt(BlockPos::getZ).average().orElse(0);
		List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
		for (BlockPos cell : room) {
			if (Math.abs(cell.getX() - cx) <= 1.5 && Math.abs(cell.getZ() - cz) <= 1.5 && level.getBlockState(cell).isAir()
					&& level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) blocks.add(new SchematicSpec.PlacedBlock(cell, carpet));
		}
		List<BlockPos> spots = com.vnap.dialogue.FamilyHome.decorSpots(level, home.bed);
		BlockState[] things = {Blocks.BOOKSHELF.defaultBlockState(), Blocks.POTTED_RED_TULIP.defaultBlockState(), Blocks.CANDLE.defaultBlockState(),
				Blocks.POTTED_FERN.defaultBlockState(), Blocks.CHISELED_BOOKSHELF.defaultBlockState(), Blocks.POTTED_AZALEA.defaultBlockState()};
		// a lantern hung from the ceiling over the carpet's middle
		BlockPos middle = BlockPos.containing(cx, home.bed.getY(), cz);
		for (int up = 2; up <= 5; up++) {
			BlockPos under = middle.above(up);
			if (!level.getBlockState(under).isAir()) break;
			if (level.getBlockState(under.above()).isFaceSturdy(level, under.above(), Direction.DOWN) && up >= 2) {
				blocks.add(new SchematicSpec.PlacedBlock(under, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)));
				break;
			}
		}
		for (int i = 0; i < Math.min(spots.size(), things.length); i++) {
			BlockPos spot = spots.get(i);
			blocks.removeIf(block -> block.rel().equals(spot));
			blocks.add(new SchematicSpec.PlacedBlock(spot, things[i]));
		}
		return blocks.isEmpty() ? null : blocks;
	}
}
