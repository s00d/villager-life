package com.vnap.village.town;

import com.vnap.village.home.HouseProject;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Placements;
import com.vnap.village.home.Towns;
import com.vnap.village.build.BuildJob;
import com.vnap.village.life.Agenda;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The mayor's work orders for the town's own ground, posted once a day from the hall's door (or the notice board,
 * without a hall): pits, ravine cuts and holes by the streets, lots, the square and the houses filled with earth up
 * to the ground round them, and the roads cleared of what lies on them (gravel and sand that slid down, fallen
 * leaves, snow, weeds; things dropped). The town's builders do it (RoadBehavior), the treasury pays. Water, deep
 * ravines, caves under the ground, buildings, the ditch, players' lots and houses, and anything that is no debris
 * (a block a player set may be anything) are left alone.
 */
public final class Upkeep {
	static final String PITS = "pits";
	static final String DEBRIS = "debris";
	static final String LEVELLING = "levelling";

	private Upkeep() {
	}

	/** Daily (Roads.plan): with a mayor, the day's orders; the mayor walks to where it posts them. */
	static void plan(ServerLevel level, Roads roads, Roads.Network network) {
		Mayors.Office office = Towns.of(level, network.bell);
		if (office == null || office.mayor == null) return;
		int pits = pits(level, roads, network);
		int debris = debris(level, roads, network);
		int levelling = levelling(level, roads, network);
		if (pits + debris + levelling == 0) return;
		Villager mayor = Mayors.mayorNear(level, network.bell);
		if (mayor == null || mayor.isNoAi()) return;
		BlockPos post = post(level, network, mayor);
		if (post != null) Agenda.go(mayor, "orders", post, Agenda.VISIT, null, null);
	}

	/** Where the mayor posts the town's work: before the door of its hall, or at the notice board. */
	static @Nullable BlockPos post(ServerLevel level, Roads.Network network, Villager mayor) {
		HouseProject hall = Mayors.hall(level, mayor);
		if (hall != null) {
			BlockPos step = Roads.step(level, hall.schematic(level), hall.origin, hall.rotation, hall.footprint(level));
			if (step != null) return step.above();
		}
		return NoticeBoard.board(level, network);
	}

	/** Whether the stretch of this kind ordered before is still being done (no second order on top of it). */
	private static boolean pending(Roads.Network network, String label) {
		return network.segments.stream().anyMatch(segment -> segment.label.equals(label));
	}

	private static Set<BlockPos> queued(Roads.Network network) {
		Set<BlockPos> out = new HashSet<>();
		for (Roads.Segment segment : network.segments) for (SchematicSpec.PlacedBlock block : segment.blocks) out.add(block.rel());
		return out;
	}

	// ---- pits --------------------------------------------------------------------------------------------------

	private static final byte NONE = 0;
	private static final byte NATURAL = 1;
	private static final byte ROAD = 2;
	private static final byte OTHER = 3;

	/**
	 * Earth for the pits of the town's land (loaded ground only), nearest the bell first, up to a day's
	 * {@link TownRules#PIT_BLOCKS_PER_DAY}; the blocks queued (0: none, or yesterday's still going in).
	 */
	public static int pits(ServerLevel level, Roads roads, Roads.Network network) {
		if (pending(network, PITS)) return 0;
		BlockPos bell = network.bell;
		int reach = (int) Towns.reach(level, bell);
		int size = 2 * reach + 1;
		int x0 = bell.getX() - reach;
		int z0 = bell.getZ() - reach;
		Towns.Territory territory = Towns.territory(level, bell);
		int[] height = new int[size * size];
		byte[] kind = new byte[size * size];
		boolean[] ours = new boolean[size * size];
		BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
		for (int i = 0; i < size; i++) {
			for (int k = 0; k < size; k++) {
				column.set(x0 + i, bell.getY(), z0 + k);
				if (!level.isPositionEntityTicking(column)) continue;
				int y = Ground.surface(level, x0 + i, z0 + k);
				column.setY(y);
				BlockState state = level.getBlockState(column);
				int at = i * size + k;
				height[at] = y;
				ours[at] = territory.contains(column);
				kind[at] = !state.getFluidState().isEmpty() ? NONE : BuildJob.naturalGround(state) ? NATURAL : Roads.roadSurface(state) ? ROAD : OTHER;
			}
		}
		// the town's things and what must not be filled, by column
		Map<Long, Land.Layer> land = Land.around(level, bell).map(bell, reach);
		Set<Long> keepOff = new HashSet<>();
		land.forEach((key, layer) -> {
			if (layer == Land.Layer.BUILDING) keepOff.add(key);
		});
		for (BlockPos cell : network.moat) keepOff.add(BlockPos.asLong(cell.getX(), 0, cell.getZ()));
		List<BoundingBox> players = new ArrayList<>(network.boughtHouses.keySet());
		for (TownPlan.Lot lot : network.lots) if (lot.player != null) players.add(lot.box);
		boolean[] near = new boolean[size * size];
		Set<Long> things = new HashSet<>(land.keySet());
		for (BlockPos cell : network.cells) things.add(BlockPos.asLong(cell.getX(), 0, cell.getZ()));
		for (long key : things) {
			int cx = BlockPos.getX(key) - x0;
			int cz = BlockPos.getZ(key) - z0;
			for (int i = Math.max(0, cx - TownRules.PIT_NEAR); i <= Math.min(size - 1, cx + TownRules.PIT_NEAR); i++) {
				for (int k = Math.max(0, cz - TownRules.PIT_NEAR); k <= Math.min(size - 1, cz + TownRules.PIT_NEAR); k++) near[i * size + k] = true;
			}
		}
		Set<BlockPos> queued = queued(network);
		List<int[]> pits = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			for (int k = 0; k < size; k++) {
				int at = i * size + k;
				if (kind[at] != NATURAL || !ours[at] || !near[at]) continue;
				int x = x0 + i;
				int z = z0 + k;
				if (keepOff.contains(BlockPos.asLong(x, 0, z))) continue;
				int h = height[at];
				int target = Integer.MAX_VALUE;
				for (int[] axis : new int[][]{{1, 0}, {0, 1}}) {
					Integer one = bank(height, kind, size, i, k, axis[0], axis[1], h);
					Integer other = one == null ? null : bank(height, kind, size, i, k, -axis[0], -axis[1], h);
					if (one != null && other != null) target = Math.min(target, Math.min(one, other));
				}
				if (target == Integer.MAX_VALUE || target - h < TownRules.PIT_MIN || target - h > TownRules.PIT_MAX) continue;
				BlockPos floor = new BlockPos(x, h, z);
				if (players.stream().anyMatch(box -> box.minX() <= x && x <= box.maxX() && box.minZ() <= z && z <= box.maxZ())) continue;
				if (!open(level, floor, target, queued)) continue;
				pits.add(new int[]{x, h, z, target});
			}
		}
		pits.sort(Comparator.comparingDouble(pit -> new BlockPos(pit[0], pit[1], pit[2]).distSqr(bell)));
		String palette = HousePalette.forBiome(level.getBiome(bell));
		boolean desert = "desert".equals(palette);
		BlockState earth = desert ? Blocks.SAND.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		// the top under turf (bought as dirt): a filled pit of bare earth stood out as brown columns among the grass
		BlockState top = desert ? earth : Blocks.GRASS_BLOCK.defaultBlockState();
		List<SchematicSpec.PlacedBlock> fill = new ArrayList<>();
		for (int[] pit : pits) {
			if (fill.size() >= TownRules.PIT_BLOCKS_PER_DAY) break;
			for (int y = pit[1] + 1; y <= pit[3]; y++) fill.add(new SchematicSpec.PlacedBlock(new BlockPos(pit[0], y, pit[2]), y == pit[3] ? top : earth));
		}
		if (fill.isEmpty()) return 0;
		// from the bottom up: every layer stands on the last (and sand does not fall)
		fill.sort(Comparator.comparingInt(block -> block.rel().getY()));
		roads.queue(level, network, fill, PITS);
		com.vnap.VillagerLife.trace("vnap pits: {} blocks of earth for {} pits of {}", fill.size(), pits.size(), bell.toShortString());
		return fill.size();
	}

	/** What the last levelling did or why it did nothing (tests, the log). */
	public static String lastLevelling = "";

	/**
	 * Ground made fit to build on: the nearest square of LEVEL_TILE x LEVEL_TILE columns of the town's free land (room
	 * for a few houses) that is crooked or wet - a pond, a pit, a bump - and can be levelled with little earth: no
	 * column more than LEVEL_CUT over or LEVEL_FILL under the square's middle height. Bumps are dug off, water and holes
	 * filled with earth from the bottom up, turf on top; the town's builder does it like a road. One square a day.
	 */
	public static int levelling(ServerLevel level, Roads roads, Roads.Network network) {
		if (pending(network, LEVELLING)) return 0;
		BlockPos bell = network.bell;
		int reach = (int) Towns.reach(level, bell);
		Towns.Territory territory = Towns.territory(level, bell);
		Map<Long, Land.Layer> land = Land.around(level, bell).map(bell, reach);
		// a free lot of the plan is just where levelling helps; built, laid and bought ground is kept off
		Set<Long> busy = new HashSet<>();
		land.forEach((key, layer) -> {
			if (layer != Land.Layer.LOT) busy.add(key);
		});
		for (BlockPos cell : network.cells) busy.add(BlockPos.asLong(cell.getX(), 0, cell.getZ()));
		for (BlockPos cell : network.moat) busy.add(BlockPos.asLong(cell.getX(), 0, cell.getZ()));
		for (TownPlan.Lot lot : network.lots) {
			if (lot.player == null && !lot.taken) continue;
			for (int x = lot.box.minX(); x <= lot.box.maxX(); x++) for (int z = lot.box.minZ(); z <= lot.box.maxZ(); z++) busy.add(BlockPos.asLong(x, 0, z));
		}
		Set<BlockPos> queued = queued(network);
		int[] refused = new int[4];
		int tile = TownRules.LEVEL_TILE;
		List<BlockPos> corners = new ArrayList<>();
		for (int x = bell.getX() - reach; x + tile <= bell.getX() + reach; x += tile / 2) {
			for (int z = bell.getZ() - reach; z + tile <= bell.getZ() + reach; z += tile / 2) corners.add(new BlockPos(x, bell.getY(), z));
		}
		corners.sort(Comparator.comparingDouble(corner -> corner.offset(tile / 2, 0, tile / 2).distSqr(bell)));
		BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
		for (BlockPos corner : corners) {
			int[] ground = new int[tile * tile];
			int[] surface = new int[tile * tile];
			boolean ok = true;
			boolean wet = false;
			int outside = 0;
			for (int i = 0; i < tile && ok; i++) {
				for (int k = 0; k < tile && ok; k++) {
					int x = corner.getX() + i;
					int z = corner.getZ() + k;
					column.set(x, bell.getY(), z);
					// past the town's edge (it is round) or not loaded: left as it is
					if (!level.isPositionEntityTicking(column) || !territory.contains(column)) {
						ground[i * tile + k] = Integer.MIN_VALUE;
						outside++;
						continue;
					}
					if (busy.contains(BlockPos.asLong(x, 0, z))) {
						refused[0]++;
						ok = false;
						break;
					}
					int top = Ground.surface(level, x, z);
					int bed = top;
					while (bed > top - TownRules.LEVEL_FILL - 1 && !level.getFluidState(column.setY(bed)).isEmpty()) bed--;
					BlockState floor = level.getBlockState(column.setY(bed));
					if (!level.getFluidState(column).isEmpty() || !BuildJob.naturalGround(floor) || queued.contains(column.immutable())) {
						refused[1]++;
						ok = false;
						break;
					}
					wet |= bed < top;
					ground[i * tile + k] = bed;
					surface[i * tile + k] = top;
				}
			}
			if (!ok) continue;
			if (outside * 4 > tile * tile) {
				refused[0]++;
				continue;
			}
			int[] sorted = java.util.Arrays.stream(ground).filter(h -> h != Integer.MIN_VALUE).sorted().toArray();
			int middle = sorted[sorted.length / 2];
			int lowest = sorted[0];
			int highest = sorted[sorted.length - 1];
			// flat and dry already, or too much earth to move
			if (!wet && highest - lowest <= 1) {
				refused[2]++;
				continue;
			}
			if (highest - middle > TownRules.LEVEL_CUT || middle - lowest > TownRules.LEVEL_FILL) {
				refused[3]++;
				continue;
			}
			String palette = HousePalette.forBiome(level.getBiome(bell));
			boolean desert = "desert".equals(palette);
			BlockState earth = desert ? Blocks.SAND.defaultBlockState() : Blocks.DIRT.defaultBlockState();
			BlockState top = desert ? earth : Blocks.GRASS_BLOCK.defaultBlockState();
			List<SchematicSpec.PlacedBlock> work = new ArrayList<>();
			for (int i = 0; i < tile; i++) {
				for (int k = 0; k < tile; k++) {
					int x = corner.getX() + i;
					int z = corner.getZ() + k;
					int bed = ground[i * tile + k];
					if (bed == Integer.MIN_VALUE) continue;
					// a bump dug down to the middle (the plants on it go with it)
					for (int y = Math.max(surface[i * tile + k], bed) + 1; y > middle; y--) work.add(new SchematicSpec.PlacedBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState()));
					if (bed > middle) work.add(new SchematicSpec.PlacedBlock(new BlockPos(x, middle, z), top));
					for (int y = bed + 1; y <= middle; y++) work.add(new SchematicSpec.PlacedBlock(new BlockPos(x, y, z), y == middle ? top : earth));
				}
			}
			if (work.isEmpty()) continue;
			// the cuts first, top down, then the fill from the bottom up: every layer stands on the last
			work.sort(Comparator.comparingInt((SchematicSpec.PlacedBlock block) -> block.state().isAir() ? -block.rel().getY() - 1000 : block.rel().getY()));
			roads.queue(level, network, work, LEVELLING);
			lastLevelling = work.size() + " blocks at " + corner.toShortString() + " to y " + middle;
			com.vnap.VillagerLife.trace("vnap levelling: {} for {}", lastLevelling, bell.toShortString());
			return work.size();
		}
		lastLevelling = "none of " + corners.size() + " squares (taken " + refused[0] + ", not ground " + refused[1] + ", flat " + refused[2] + ", too steep " + refused[3] + ")";
		com.vnap.VillagerLife.trace("vnap levelling: {}", lastLevelling);
		return 0;
	}

	/**
	 * The ground's height this way along the line from column (i, k): the first natural ground or road at least
	 * {@link TownRules#PIT_MIN} over {@code h} within {@link TownRules#PIT_BANK}; null on water, unloaded ground, or
	 * none (a slope going down, a wide valley).
	 */
	private static @Nullable Integer bank(int[] height, byte[] kind, int size, int i, int k, int dx, int dz, int h) {
		for (int d = 1; d <= TownRules.PIT_BANK; d++) {
			int a = i + dx * d;
			int b = k + dz * d;
			if (a < 0 || b < 0 || a >= size || b >= size) return null;
			int at = a * size + b;
			if (kind[at] == NONE) return null;
			if (kind[at] == OTHER) continue;
			if (height[at] - h >= TownRules.PIT_MIN) return height[at];
		}
		return null;
	}

	/** Every cell of the column over its floor up to {@code top} free to fill: air or plants, no water, no one's work. */
	private static boolean open(ServerLevel level, BlockPos floor, int top, Set<BlockPos> queued) {
		for (int y = floor.getY() + 1; y <= top; y++) {
			BlockPos cell = floor.atY(y);
			BlockState state = level.getBlockState(cell);
			if (!state.canBeReplaced() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || queued.contains(cell) || Placements.pending(level, cell)) return false;
		}
		return true;
	}

	// ---- the roads ---------------------------------------------------------------------------------------------

	/** What lies on the roads (the two cells over each laid road cell) cleared off, up to a day's; the blocks queued. */
	public static int debris(ServerLevel level, Roads roads, Roads.Network network) {
		if (pending(network, DEBRIS)) return 0;
		Set<BlockPos> queued = queued(network);
		List<SchematicSpec.PlacedBlock> clear = new ArrayList<>();
		for (BlockPos cell : network.cells) {
			if (clear.size() >= TownRules.DEBRIS_PER_DAY) break;
			if (!level.isPositionEntityTicking(cell) || !Roads.roadSurface(level.getBlockState(cell))) continue;
			TownPlan.Lot lot = TownPlan.at(network, cell);
			if (lot != null && lot.player != null) continue;
			// the top first: gravel two high comes down a block at a time otherwise
			for (int up = 2; up >= 1; up--) {
				BlockPos pos = cell.above(up);
				if (!debris(level.getBlockState(pos)) || queued.contains(pos) || network.cells.contains(pos) || network.features.contains(pos)
						|| network.greens.containsKey(pos.asLong()) || Placements.pending(level, pos)) continue;
				clear.add(new SchematicSpec.PlacedBlock(pos, Blocks.AIR.defaultBlockState()));
			}
		}
		if (clear.isEmpty()) return 0;
		roads.queue(level, network, clear, DEBRIS);
		return clear.size();
	}

	/**
	 * Debris on a road: what the world puts there (gravel and sand slid down, fallen leaves, snow, weeds, cobwebs),
	 * never what someone set (a block entity, leaves placed by hand, a slab, a lamp...).
	 */
	static boolean debris(BlockState state) {
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
		if (state.is(BlockTags.LEAVES)) return state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
		return state.is(Blocks.GRAVEL) || state.is(BlockTags.SAND) || state.is(Blocks.SNOW) || state.is(Blocks.COBWEB)
				|| state.getBlock() instanceof net.minecraft.world.level.block.BushBlock;
	}

	/**
	 * Every {@link TownRules#LITTER_EVERY} ticks (TownLife.tick), with a mayor: things lying on the roads with no
	 * player by them are picked up by the nearest grown-up (at once with nobody about) and go to the lost and found.
	 */
	public static int litter(ServerLevel level, Roads.Network network) {
		Mayors.Office office = Towns.of(level, network.bell);
		if (office == null || office.mayor == null) return 0;
		double reach = Towns.reach(level, network.bell);
		List<ItemEntity> lying = level.getEntitiesOfClass(ItemEntity.class, new AABB(network.bell).inflate(reach, 24, reach),
				item -> item.isAlive() && onRoad(network, item.blockPosition())
						&& level.players().stream().noneMatch(player -> !player.isSpectator() && player.distanceToSqr(item) < 64.0));
		Set<ItemEntity> seen = new HashSet<>();
		BlockPos bell = network.bell;
		int looks = 0;
		for (ItemEntity item : lying) {
			if (looks >= TownRules.LITTER_PER_LOOK) break;
			if (seen.contains(item)) continue;
			BlockPos at = item.blockPosition();
			for (ItemEntity other : lying) if (other.blockPosition().closerThan(at, 2.0)) seen.add(other);
			looks++;
			Villager hand = Towns.people(level, bell, v -> !v.isBaby() && !v.isSleeping() && !v.isNoAi() && !com.vnap.village.life.Life.official(v) && v.blockPosition().closerThan(at, TownRules.NEAR_BELL))
					.stream().min(Comparator.comparingDouble(v -> v.blockPosition().distSqr(at))).orElse(null);
			if (hand == null) pickUp(level, bell, at);
			else Agenda.job(hand, "litter_" + at.asLong(), at, Agenda.ROUTINE, ItemStack.EMPTY, (l, v) -> pickUp(l, bell, at));
		}
		return looks;
	}

	private static boolean onRoad(Roads.Network network, BlockPos at) {
		return network.cells.contains(at.below()) || network.cells.contains(at);
	}

	/** The things lying round {@code at} into the town's lost and found. */
	private static void pickUp(ServerLevel level, BlockPos bell, BlockPos at) {
		List<ItemStack> found = new ArrayList<>();
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2.0), ItemEntity::isAlive)) {
			found.add(item.getItem().copy());
			item.discard();
		}
		NoticeBoard.lose(level, bell, found);
	}

	/** Earth for the stall's catalogue (Market.building): a day's pits come out of it. */
	static List<SchematicSpec> kit() {
		List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
		for (int i = 0; i < TownRules.PIT_BLOCKS_PER_DAY / 4; i++) {
			blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(i, 0, 0), Blocks.DIRT.defaultBlockState()));
			blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(i, 0, 1), Blocks.SAND.defaultBlockState()));
		}
		return List.of(new SchematicSpec(TownRules.PIT_BLOCKS_PER_DAY / 4, 1, 2, List.of(), blocks, BlockPos.ZERO));
	}
}
