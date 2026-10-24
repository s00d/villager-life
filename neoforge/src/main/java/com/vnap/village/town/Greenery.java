package com.vnap.village.town;

import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.Towns;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The town's green between its houses, on land nobody needs: never a lot of the plan, a road, a yard, a building or
 * its margin (Land).
 * <ul>
 * <li>Verges: the cells beside a road get plants in clumps with gaps — grass, ferns, flowers, the tall flowers that
 * look like shrubs (lilac, rose bush, peony). None of them stops a walker (no collision): the paths stay as they
 * were. Never by a door, a lamp or a crossing. The town's gardener sets them and puts back what was picked
 * (GardenBehavior); villagers pick flowers to give away (FlowerBehavior), the farmers' bees feed on them.</li>
 * <li>Nooks: a small pocket of free ground between houses (two and more from any road, building or lot, three from a
 * door) gets one small thing: a bench with a lamp looking out to the road (the lamp lights a dark corner monsters
 * spawned in), or a bush with flowers, or a young tree. The solid pieces stand only inside the pocket, never at its
 * edge: no way round a house is closed. Laid by the town's builders (Roads, the treasury), one nook a day.</li>
 * </ul>
 */
public final class Greenery {
	public static final String LABEL = "greens";
	/** Verge plants planned a day at most (the gardener sets them over the following days). */
	static final int VERGE_PER_DAY = 32;
	/** A pocket smaller than this is a gap; larger is open land the town may still want. */
	static final int NOOK_MIN = 6;
	static final int NOOK_MAX = 36;
	/** Nothing of the green this close to a door's step (manhattan). */
	static final int DOOR_CLEAR = 3;
	/** Nooks apart. */
	static final int NOOK_APART = 10;
	/** A town needs its roads before it gets a green. */
	static final int MIN_CELLS = 20;

	private Greenery() {
	}

	/** Roads.plan, once a day. */
	public static void plan(ServerLevel level, Roads roads, Roads.Network network) {
		if (network.cells.size() < MIN_CELLS) return;
		Land land = Land.around(level, network.bell);
		Towns.Territory territory = Towns.territory(level, network.bell);
		List<BlockPos> doors = Roads.nodes(level, network);
		verges(level, network, land, territory, doors);
		long day = com.vnap.dialogue.Family.day(level);
		// the gaps change slowly: looked for every few days, not every day
		if (day - network.nookDay < TownRules.RETRY_DAYS) return;
		network.nookDay = day;
		nook(level, roads, network, land, territory, doors);
	}

	// ---- verges ---------------------------------------------------------------------------------

	static int verges(ServerLevel level, Roads.Network network, Land land, Towns.Territory territory, List<BlockPos> doors) {
		int planned = 0;
		for (BlockPos road : List.copyOf(network.cells)) {
			if (planned >= VERGE_PER_DAY) break;
			if (!level.isPositionEntityTicking(road) || crossing(network, road)) continue;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos ground = ground(level, road.relative(side), road.getY());
				if (ground == null) continue;
				BlockPos cell = ground.above();
				if (network.greens.containsKey(cell.asLong()) || network.cells.contains(ground) || network.features.contains(cell)
						|| land.blocked(cell, 0) || !territory.contains(cell) || nearDoor(doors, cell, DOOR_CLEAR) || nearLamp(network, ground)
						|| !clumped(cell)) continue;
				String palette = HousePalette.forBiome(level.getBiome(cell));
				Block plant = plant(palette, cell);
				if (plant == null || !plant.defaultBlockState().canSurvive(level, cell)) continue;
				network.greens.put(cell.asLong(), plant);
				planned++;
			}
		}
		return planned;
	}

	/** Clumps with gaps: a 4x4 patch of the land is green or not, and in a green patch two cells in three. */
	static boolean clumped(BlockPos cell) {
		long patch = (long) (cell.getX() >> 2) * 341873128712L + (long) (cell.getZ() >> 2) * 132897987541L;
		long one = (long) cell.getX() * 734287L + (long) cell.getZ() * 912931L;
		return Math.floorMod(patch, 5L) < 2 && Math.floorMod(one, 3L) != 0;
	}

	/** Natural ground at the column's height near {@code y} with room for a plant (or grass to replace) over it. */
	static @Nullable BlockPos ground(ServerLevel level, BlockPos column, int y) {
		for (int dy = 1; dy >= -1; dy--) {
			BlockPos pos = column.atY(y + dy);
			if (!level.isLoaded(pos) || !BuildJob.naturalGround(level.getBlockState(pos))) continue;
			BlockState over = level.getBlockState(pos.above());
			if ((over.isAir() || (over.canBeReplaced() && over.getFluidState().isEmpty())) && level.getBlockState(pos.above(2)).canBeReplaced()
					&& level.getBlockState(pos.above(2)).getFluidState().isEmpty()) return pos;
		}
		return null;
	}

	/** A road cell with three or more road neighbours: a crossing, kept clear. */
	private static boolean crossing(Roads.Network network, BlockPos road) {
		int n = 0;
		for (Direction side : Direction.Plane.HORIZONTAL) if (network.cells.contains(road.relative(side))) n++;
		return n >= 3;
	}

	private static boolean nearDoor(List<BlockPos> doors, BlockPos cell, int clear) {
		for (BlockPos door : doors) {
			if (Math.abs(door.getX() - cell.getX()) + Math.abs(door.getZ() - cell.getZ()) <= clear && Math.abs(door.getY() - cell.getY()) <= 3) return true;
		}
		return false;
	}

	private static boolean nearLamp(Roads.Network network, BlockPos ground) {
		for (BlockPos lamp : network.lamps) if (lamp.distManhattan(ground) <= 1) return true;
		return false;
	}

	/** The verge plant for a cell: by the biome's palette, a stable pick per cell (a third of them tall "shrubs"). */
	static @Nullable Block plant(String palette, BlockPos cell) {
		Block[] kinds = switch (palette) {
			case "desert" -> new Block[]{Blocks.DEAD_BUSH, Blocks.DEAD_BUSH, null};
			case "taiga" -> new Block[]{Blocks.FERN, Blocks.LARGE_FERN, Blocks.FERN, Blocks.LILY_OF_THE_VALLEY, Blocks.SHORT_GRASS, Blocks.LARGE_FERN};
			case "snowy" -> new Block[]{Blocks.FERN, Blocks.LARGE_FERN, Blocks.FERN, null};
			case "savanna" -> new Block[]{Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.DANDELION, Blocks.TALL_GRASS, Blocks.ORANGE_TULIP, Blocks.SHORT_GRASS};
			default -> new Block[]{Blocks.SHORT_GRASS, Blocks.LILAC, Blocks.POPPY, Blocks.ROSE_BUSH, Blocks.CORNFLOWER, Blocks.PEONY, Blocks.OXEYE_DAISY,
					Blocks.DANDELION, Blocks.AZURE_BLUET, Blocks.SHORT_GRASS};
		};
		return kinds[Math.floorMod(cell.getX() * 31 + cell.getZ() * 17 + cell.getY(), kinds.length)];
	}

	// ---- nooks ----------------------------------------------------------------------------------

	/** One nook a day: the first free pocket found round the town's buildings. */
	static @Nullable BlockPos nook(ServerLevel level, Roads roads, Roads.Network network, Land land, Towns.Territory territory, List<BlockPos> doors) {
		Set<BlockPos> seen = new HashSet<>();
		// the columns looked at, for every pocket of the day's search
		Map<Long, java.util.Optional<BlockPos>> free = new java.util.HashMap<>();
		for (Land.Claim claim : land.claims()) {
			if (claim.layer() != Land.Layer.BUILDING) continue;
			var box = claim.box();
			if (!territory.contains(BlockPos.containing(box.getCenter().getX(), box.getCenter().getY(), box.getCenter().getZ()))) continue;
			int y = box.minY();
			// seeds three out from the walls: the first ring a pocket's cell can be in (two free rings round it)
			for (int x = box.minX() - 3; x <= box.maxX() + 3; x++) {
				for (int z = box.minZ() - 3; z <= box.maxZ() + 3; z++) {
					if (x != box.minX() - 3 && x != box.maxX() + 3 && z != box.minZ() - 3 && z != box.maxZ() + 3) continue;
					BlockPos seed = new BlockPos(x, y, z);
					if (seen.contains(seed)) continue;
					Set<BlockPos> pocket = pocket(level, network, land, territory, doors, seed, seen, free);
					if (pocket.size() < NOOK_MIN || pocket.size() > NOOK_MAX) continue;
					BlockPos middle = middle(pocket);
					if (network.nooks.stream().anyMatch(other -> other.closerThan(middle, NOOK_APART))) continue;
					List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
					List<BlockPos> flowers = new ArrayList<>();
					if (!layout(level, network, pocket, blocks, flowers)) continue;
					network.nooks.add(middle);
					if (!blocks.isEmpty()) roads.queue(level, network, blocks, LABEL);
					for (SchematicSpec.PlacedBlock block : blocks) network.features.add(block.rel().immutable());
					String palette = HousePalette.forBiome(level.getBiome(middle));
					for (BlockPos flower : flowers) {
						Block plant = plant(palette, flower);
						if (plant != null && plant.defaultBlockState().canSurvive(level, flower)) network.greens.put(flower.asLong(), plant);
					}
					roads.setDirty();
					return middle;
				}
			}
		}
		return null;
	}

	/**
	 * The pocket a cell is in: the free cells (on the ground's top; two and more from any road, building, lot or nook,
	 * three from a door, natural ground) whose eight neighbours are free too, joined, flat within one. A strip of
	 * land a cell or two wide (along a road, between two walls) is no pocket: it is how people get about. Empty when
	 * the seed is not in one; stops past {@link #NOOK_MAX} (open land, not a gap).
	 */
	static Set<BlockPos> pocket(ServerLevel level, Roads.Network network, Land land, Towns.Territory territory, List<BlockPos> doors, BlockPos seed,
								Set<BlockPos> seen, Map<Long, java.util.Optional<BlockPos>> free) {
		java.util.function.BiFunction<Integer, Integer, @Nullable BlockPos> at = (x, z) -> free.computeIfAbsent(BlockPos.asLong(x, 0, z),
				key -> java.util.Optional.ofNullable(free(level, network, land, territory, doors, x, z, seed.getY()))).orElse(null);
		Set<BlockPos> out = new LinkedHashSet<>();
		BlockPos firstGround = at.apply(seed.getX(), seed.getZ());
		if (firstGround == null || !inner(at, seed.getX(), seed.getZ(), firstGround.getY())) return out;
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(firstGround.above()));
		Set<Long> visited = new HashSet<>(List.of(BlockPos.asLong(seed.getX(), 0, seed.getZ())));
		while (!queue.isEmpty() && out.size() <= NOOK_MAX) {
			BlockPos cell = queue.poll();
			out.add(cell);
			seen.add(cell.atY(seed.getY()));
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = cell.relative(side);
				if (!visited.add(BlockPos.asLong(next.getX(), 0, next.getZ()))) continue;
				BlockPos ground = at.apply(next.getX(), next.getZ());
				if (ground != null && Math.abs(ground.getY() - firstGround.getY()) <= 1 && inner(at, next.getX(), next.getZ(), firstGround.getY())) {
					queue.add(ground.above());
				}
			}
		}
		return out;
	}

	/** All eight columns round this one are free ground within one of the pocket's height. */
	private static boolean inner(java.util.function.BiFunction<Integer, Integer, @Nullable BlockPos> at, int x, int z, int y) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos ground = at.apply(x + dx, z + dz);
				if (ground == null || Math.abs(ground.getY() - y) > 1) return false;
			}
		}
		return true;
	}

	/** The ground of a free column (the cell over it, its top), or null. */
	private static @Nullable BlockPos free(ServerLevel level, Roads.Network network, Land land, Towns.Territory territory, List<BlockPos> doors,
										   int x, int z, int y) {
		BlockPos column = new BlockPos(x, y, z);
		if (!level.isPositionEntityTicking(column)) return null;
		BlockPos ground = ground(level, column, y);
		if (ground == null) return null;
		BlockPos cell = ground.above();
		if (!territory.contains(cell) || land.blocked(cell, 1) || nearDoor(doors, cell, DOOR_CLEAR)) return null;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (network.cells.contains(ground.offset(dx, 0, dz)) || network.features.contains(cell.offset(dx, 0, dz))
						|| network.cells.contains(ground.offset(dx, -1, dz)) || network.cells.contains(ground.offset(dx, 1, dz))) return null;
			}
		}
		return ground;
	}

	private static BlockPos middle(Set<BlockPos> pocket) {
		long x = 0;
		long z = 0;
		for (BlockPos cell : pocket) {
			x += cell.getX();
			z += cell.getZ();
		}
		BlockPos centre = new BlockPos((int) (x / pocket.size()), 0, (int) (z / pocket.size()));
		return pocket.stream().min(java.util.Comparator.comparingDouble(cell -> cell.atY(0).distSqr(centre))).orElse(centre);
	}

	/** Deep in the pocket: its four neighbours are pocket cells too (nothing solid at the pocket's edge). */
	private static boolean inner(Set<BlockPos> pocket, BlockPos cell) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos near = cell.relative(side);
			if (pocket.stream().noneMatch(p -> p.getX() == near.getX() && p.getZ() == near.getZ())) return false;
		}
		return true;
	}

	/**
	 * What the nook gets, by a stable pick: a bench with a lamp looking to the nearest road, or a bush with flowers
	 * round it, or a young tree in a deep pocket (no tree in the desert). False when the pocket has no inner cell.
	 */
	static boolean layout(ServerLevel level, Roads.Network network, Set<BlockPos> pocket, List<SchematicSpec.PlacedBlock> blocks, List<BlockPos> flowers) {
		List<BlockPos> inner = pocket.stream().filter(cell -> inner(pocket, cell)).toList();
		if (inner.isEmpty()) return false;
		BlockPos middle = middle(pocket);
		BlockPos heart = inner.stream().min(java.util.Comparator.comparingDouble(cell -> cell.distSqr(middle))).orElse(inner.get(0));
		String palette = HousePalette.forBiome(level.getBiome(heart));
		int pick = Math.floorMod(heart.getX() * 7 + heart.getZ() * 13, 3);
		Direction toRoad = toRoad(network, heart);
		if (pick == 0 && toRoad != null) {
			// the bench looks out to the road, the lamp beside it
			Direction along = toRoad.getClockWise();
			BlockPos lamp = heart.relative(along);
			if (!inner.contains(lamp)) lamp = heart.relative(along.getOpposite());
			blocks.add(new SchematicSpec.PlacedBlock(heart, Roads.palette(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, toRoad.getOpposite()), palette)));
			if (inner.contains(lamp)) {
				for (SchematicSpec.PlacedBlock post : Roads.lamp(lamp.below(), palette)) blocks.add(post);
			}
			for (BlockPos cell : pocket) if (cell.distManhattan(heart) == 2 && !inner(pocket, cell) && flowers.size() < 4) flowers.add(cell);
			return true;
		}
		BlockState sapling = sapling(palette);
		boolean deep = inner.stream().filter(cell -> cell.distManhattan(heart) <= 1).count() >= 5;
		// a grown spruce spreads its leaves three blocks round at head height: no tree that close to a road
		boolean offRoad = BlockPos.betweenClosedStream(heart.offset(-3, -2, -3), heart.offset(3, 2, 3)).noneMatch(network.cells::contains);
		if (pick == 1 && deep && offRoad && sapling != null) {
			blocks.add(new SchematicSpec.PlacedBlock(heart, sapling));
			return true;
		}
		BlockState leaves = Roads.palette(Blocks.OAK_LEAVES.defaultBlockState(), palette).trySetValue(LeavesBlock.PERSISTENT, true);
		if ("plains".equals(palette)) leaves = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		// a bush two high in the middle, a lower one beside it
		blocks.add(new SchematicSpec.PlacedBlock(heart, leaves));
		blocks.add(new SchematicSpec.PlacedBlock(heart.above(), leaves));
		BlockPos second = heart.relative(Direction.from2DDataValue(Math.floorMod(heart.getX() + heart.getZ(), 4)));
		if (inner.contains(second)) blocks.add(new SchematicSpec.PlacedBlock(second, leaves));
		for (BlockPos cell : pocket) if (cell.distManhattan(heart) == 2 && flowers.size() < 5 && !cell.equals(second)) flowers.add(cell);
		return true;
	}

	/** The way to the nearest road from a cell (within six), or null. */
	private static @Nullable Direction toRoad(Roads.Network network, BlockPos cell) {
		for (int d = 1; d <= 6; d++) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos at = cell.relative(side, d);
				if (network.cells.contains(at.below()) || network.cells.contains(at) || network.cells.contains(at.below(2))) return side;
			}
		}
		return null;
	}

	private static @Nullable BlockState sapling(String palette) {
		return switch (palette) {
			case "desert" -> null;
			case "taiga", "snowy" -> Blocks.SPRUCE_SAPLING.defaultBlockState();
			case "savanna" -> Blocks.ACACIA_SAPLING.defaultBlockState();
			default -> Blocks.BIRCH_SAPLING.defaultBlockState();
		};
	}

	/** What nooks take, for the stall's catalogue (Market.building): benches, lamps, bushes, saplings, every palette. */
	public static List<SchematicSpec> kit() {
		List<SchematicSpec> out = new ArrayList<>();
		for (String palette : HousePalette.ALL) {
			List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
			int n = 0;
			blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Roads.palette(Blocks.OAK_STAIRS.defaultBlockState(), palette)));
			for (SchematicSpec.PlacedBlock post : Roads.lamp(new BlockPos(n++, -1, 0), palette)) blocks.add(post);
			for (int i = 0; i < 2; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Roads.palette(Blocks.OAK_LEAVES.defaultBlockState(), palette)));
			blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState()));
			BlockState sapling = sapling(palette);
			if (sapling != null) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), sapling));
			out.add(new SchematicSpec(n, 4, 1, List.of(), blocks, BlockPos.ZERO));
		}
		return out;
	}

	/** Debug (/vnap roads greens): the verges and a nook planned now; what the town has. */
	public static String debug(ServerLevel level, Roads roads, Roads.Network network) {
		Land land = Land.around(level, network.bell);
		Towns.Territory territory = Towns.territory(level, network.bell);
		List<BlockPos> doors = Roads.nodes(level, network);
		int verges = verges(level, network, land, territory, doors);
		BlockPos nook = nook(level, roads, network, land, territory, doors);
		long missing = network.greens.keySet().stream().map(BlockPos::of).filter(pos -> level.isLoaded(pos) && !grows(level, pos)).count();
		return "greens: +" + verges + " verge plants, nook " + (nook == null ? "none (no free pocket)" : nook.toShortString())
				+ "; " + network.greens.size() + " plants planned, " + missing + " to set; " + network.nooks.size() + " nooks at "
				+ network.nooks.stream().map(BlockPos::toShortString).limit(6).toList() + "; set e.g. "
				+ network.greens.keySet().stream().map(BlockPos::of).filter(pos -> level.isLoaded(pos) && grows(level, pos)).limit(4).map(BlockPos::toShortString).toList();
	}

	/** Something green grows in the cell (the plan's plant or any other the player or the world put there). */
	public static boolean grows(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
				|| state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH) || state.is(BlockTags.SMALL_FLOWERS)
				|| state.getBlock() instanceof net.minecraft.world.level.block.TallFlowerBlock;
	}

	/** The gardener's work: the plan's plants missing in loaded land near {@code from}, nearest first. */
	public static List<BlockPos> missing(ServerLevel level, Roads.Network network, BlockPos from, int max) {
		List<BlockPos> out = new ArrayList<>();
		List<Map.Entry<Long, Block>> all = new ArrayList<>(network.greens.entrySet());
		all.sort(java.util.Comparator.comparingDouble(entry -> BlockPos.of(entry.getKey()).distSqr(from)));
		for (Map.Entry<Long, Block> entry : all) {
			BlockPos pos = BlockPos.of(entry.getKey());
			if (!level.isPositionEntityTicking(pos) || grows(level, pos)) continue;
			out.add(pos);
			if (out.size() >= max) break;
		}
		return out;
	}

	/**
	 * The gardener sets the plant (a tall one both halves) when the cell is still free; a cell taken by something else
	 * (a block, a road, a building) or whose ground is gone is struck off the plan. True when it was set.
	 */
	public static boolean set(ServerLevel level, Roads.Network network, BlockPos pos) {
		Block plant = network.greens.get(pos.asLong());
		if (plant == null) return false;
		BlockState here = level.getBlockState(pos);
		BlockState state = plant.defaultBlockState();
		boolean tall = plant instanceof net.minecraft.world.level.block.DoublePlantBlock;
		if (!(here.isAir() || (here.canBeReplaced() && here.getFluidState().isEmpty())) || !state.canSurvive(level, pos) || network.cells.contains(pos.below())
				|| (tall && !level.getBlockState(pos.above()).canBeReplaced())) {
			network.greens.remove(pos.asLong());
			return false;
		}
		if (tall) net.minecraft.world.level.block.DoublePlantBlock.placeAt(level, state, pos, Block.UPDATE_ALL);
		else level.setBlock(pos, state, Block.UPDATE_ALL);
		return true;
	}
}
