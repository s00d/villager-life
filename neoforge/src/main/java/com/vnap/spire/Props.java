package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * What stands along a room's walls: small scenes of a few blocks each, the theme's own (a reading desk with its books
 * and candle, a workbench with its tools, a planter, a coral pool, ice growing out of a corner, a stand of mushrooms,
 * honey pots, a star table with its telescope, a statue's plinth with a banner), a few to a room, never in a doorway's
 * lane. In place of single blocks scattered along the walls, which read as clutter.
 */
final class Props {
	private static final int CELL = FloorGenerator.CELL;
	private static final int FLOOR = FloorGenerator.FLOOR;

	/** One block of a scene: along the wall, up, out from it (0 is the cell against the wall). */
	private record Piece(int along, int up, int out, BlockState state) {
	}

	private Props() {
	}

	private static Piece p(int along, int up, int out, Block block) {
		return new Piece(along, up, out, block.defaultBlockState());
	}

	private static Piece p(int along, int up, int out, BlockState state) {
		return new Piece(along, up, out, state);
	}

	private static BlockState candles(int count) {
		return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, count).setValue(CandleBlock.LIT, true);
	}

	private static BlockState lantern() {
		return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);
	}

	private static BlockState slabTop(Block slab) {
		return slab.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
	}

	/** The theme's scenes. */
	private static List<List<Piece>> scenes(Theme theme) {
		return switch (theme.name()) {
			case "archive" -> List.of(
					// a reading desk: a slab on two shelves, books and a candle on it
					List.of(p(0, 0, 0, Blocks.CHISELED_BOOKSHELF), p(2, 0, 0, Blocks.CHISELED_BOOKSHELF), p(1, 0, 0, slabTop(Blocks.SPRUCE_SLAB)),
							p(0, 1, 0, candles(3)), p(2, 1, 0, Blocks.LANTERN), p(1, 0, 1, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH))),
					// a stack of books in a corner, a ladder up a shelf
					List.of(p(0, 0, 0, Blocks.BOOKSHELF), p(0, 1, 0, Blocks.BOOKSHELF), p(1, 0, 0, Blocks.BOOKSHELF), p(0, 2, 0, candles(2)),
							p(1, 1, 0, Blocks.FLOWER_POT)),
					List.of(p(0, 0, 0, Blocks.CARTOGRAPHY_TABLE), p(1, 0, 0, Blocks.LECTERN), p(0, 1, 0, candles(1))));
			case "workshop" -> List.of(
					// a workbench: smithing table, grindstone, chain and a lantern over them
					List.of(p(0, 0, 0, Blocks.SMITHING_TABLE), p(1, 0, 0, Blocks.GRINDSTONE), p(2, 0, 0, Blocks.ANVIL), p(1, 1, 0, lantern())),
					// crates and a coil of chain
					List.of(p(0, 0, 0, Blocks.BARREL), p(1, 0, 0, Blocks.BARREL), p(0, 1, 0, Blocks.BARREL), p(1, 1, 0, Blocks.CHAIN)),
					List.of(p(0, 0, 0, Blocks.BLAST_FURNACE), p(1, 0, 0, Blocks.COPPER_BLOCK), p(1, 1, 0, Blocks.LIGHTNING_ROD)));
			case "gardens" -> List.of(
					// a planter: a bed of moss with an azalea and flowers round it
					List.of(p(0, 0, 0, Blocks.FLOWERING_AZALEA), p(1, 0, 0, Blocks.MOSS_CARPET), p(-1, 0, 0, Blocks.POPPY), p(0, 0, 1, Blocks.AZURE_BLUET),
							p(1, 0, 1, Blocks.PINK_PETALS)),
					List.of(p(0, 0, 0, Blocks.CHERRY_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true)), p(0, 1, 0, Blocks.FLOWER_POT),
							p(1, 0, 0, Blocks.ALLIUM), p(-1, 0, 0, Blocks.LILY_OF_THE_VALLEY)),
					List.of(p(0, 0, 0, Blocks.COMPOSTER), p(1, 0, 0, Blocks.FLOWER_POT), p(-1, 0, 0, Blocks.PINK_PETALS)));
			case "galleries" -> List.of(
					// a coral bed with its pickles alight
					List.of(p(0, 0, 0, Blocks.TUBE_CORAL_BLOCK), p(1, 0, 0, Blocks.BRAIN_CORAL_BLOCK), p(0, 1, 0, pickles(3)), p(1, 1, 0, pickles(2)),
							p(-1, 0, 0, Blocks.FIRE_CORAL_BLOCK)),
					List.of(p(0, 0, 0, Blocks.PRISMARINE_WALL), p(0, 1, 0, Blocks.SEA_LANTERN), p(1, 0, 0, pickles(4))),
					List.of(p(0, 0, 0, Blocks.DARK_PRISMARINE), p(1, 0, 0, Blocks.SPONGE), p(0, 1, 0, Blocks.CONDUIT)));
			case "ice" -> List.of(
					// ice growing out of a corner: blue ice, packed ice and a crown of snow
					List.of(p(0, 0, 0, Blocks.BLUE_ICE), p(0, 1, 0, Blocks.PACKED_ICE), p(1, 0, 0, Blocks.PACKED_ICE), p(0, 2, 0, Blocks.SNOW),
							p(1, 1, 0, Blocks.SNOW)),
					List.of(p(0, 0, 0, Blocks.POWDER_SNOW_CAULDRON), p(1, 0, 0, Blocks.CHISELED_QUARTZ_BLOCK), p(1, 1, 0, lantern())));
			case "spores" -> List.of(
					// a stand of mushrooms: a stout stem with its cap, little ones round it
					List.of(p(0, 0, 0, Blocks.MUSHROOM_STEM), p(0, 1, 0, Blocks.RED_MUSHROOM_BLOCK), p(1, 0, 0, Blocks.RED_MUSHROOM), p(-1, 0, 0, Blocks.BROWN_MUSHROOM),
							p(0, 0, 1, Blocks.MOSS_CARPET)),
					List.of(p(0, 0, 0, Blocks.MUSHROOM_STEM), p(0, 1, 0, Blocks.MUSHROOM_STEM), p(0, 2, 0, Blocks.BROWN_MUSHROOM_BLOCK), p(1, 0, 0, Blocks.SHROOMLIGHT)));
			case "hive" -> List.of(
					// honey pots by a comb wall: honey blocks, candles, a nest
					List.of(p(0, 0, 0, Blocks.HONEY_BLOCK), p(1, 0, 0, Blocks.HONEYCOMB_BLOCK), p(0, 1, 0, candles(2)), p(1, 1, 0, Blocks.BEE_NEST)),
					List.of(p(0, 0, 0, Blocks.HAY_BLOCK), p(1, 0, 0, Blocks.HAY_BLOCK), p(0, 1, 0, Blocks.HAY_BLOCK), p(1, 1, 0, lantern())));
			case "observatory" -> List.of(
					// a star table: a chart, a lodestone with a telescope (a rod pointing up) and a crystal
					List.of(p(0, 0, 0, Blocks.CARTOGRAPHY_TABLE), p(1, 0, 0, Blocks.LODESTONE), p(1, 1, 0, Blocks.LIGHTNING_ROD), p(0, 1, 0, candles(1))),
					List.of(p(0, 0, 0, Blocks.AMETHYST_BLOCK), p(0, 1, 0, Blocks.AMETHYST_CLUSTER), p(1, 0, 0, Blocks.RESPAWN_ANCHOR)));
			case "summit" -> List.of(
					// a statue's plinth with the Builders' banner over it
					List.of(p(0, 0, 0, Blocks.QUARTZ_PILLAR), p(0, 1, 0, Blocks.CHISELED_QUARTZ_BLOCK), p(0, 2, 0, lantern()), p(1, 0, 0, Blocks.FLOWER_POT)),
					List.of(p(0, 0, 0, Blocks.BELL), p(1, 0, 0, Blocks.QUARTZ_BRICKS), p(1, 1, 0, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false))));
			default -> List.of(
					// the roots: a mossy rubble heap with an amethyst, an old lantern on a stump
					List.of(p(0, 0, 0, Blocks.MOSSY_COBBLESTONE), p(1, 0, 0, Blocks.MOSSY_COBBLESTONE_WALL), p(0, 1, 0, Blocks.AMETHYST_CLUSTER),
							p(-1, 0, 0, Blocks.MOSS_CARPET), p(0, 0, 1, Blocks.MOSS_CARPET)),
					List.of(p(0, 0, 0, Blocks.STRIPPED_DARK_OAK_LOG), p(0, 1, 0, lantern()), p(1, 0, 0, Blocks.FLOWERING_AZALEA)));
		};
	}

	private static BlockState stair(Block stairs, Direction facing) {
		return stairs.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	private static BlockState pickles(int count) {
		return Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, count).setValue(SeaPickleBlock.WATERLOGGED, false);
	}

	/** What a wall niche of the theme holds. */
	private static BlockState niche(Theme theme, Random random) {
		return switch (theme.name()) {
			case "archive" -> random.nextBoolean() ? candles(1 + random.nextInt(3)) : Blocks.FLOWER_POT.defaultBlockState();
			case "workshop" -> random.nextBoolean() ? lantern() : Blocks.LIGHTNING_ROD.defaultBlockState();
			case "gardens" -> random.nextBoolean() ? Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState() : Blocks.POTTED_ALLIUM.defaultBlockState();
			case "galleries" -> pickles(1 + random.nextInt(4)).setValue(SeaPickleBlock.WATERLOGGED, false);
			case "ice" -> random.nextBoolean() ? Blocks.SNOW.defaultBlockState() : lantern();
			case "spores" -> random.nextBoolean() ? Blocks.POTTED_RED_MUSHROOM.defaultBlockState() : Blocks.POTTED_BROWN_MUSHROOM.defaultBlockState();
			case "hive" -> candles(1 + random.nextInt(3));
			case "observatory" -> random.nextBoolean() ? Blocks.AMETHYST_CLUSTER.defaultBlockState() : candles(1);
			case "summit" -> random.nextBoolean() ? Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false) : Blocks.POTTED_LILY_OF_THE_VALLEY.defaultBlockState();
			default -> random.nextBoolean() ? Blocks.AMETHYST_CLUSTER.defaultBlockState() : candles(2);
		};
	}

	/**
	 * Niches in a room's walls, in the bays by the pilasters (off the doorways' frames), two high over the foot belt,
	 * with a light or a thing of the theme in them: depth and a rhythm to a long wall. Only where there is wall
	 * behind (never through the floor's outer wall, never into the next room).
	 */
	static void niches(FloorPlan plan, Theme theme, Random random, int x0, int z0) {
		for (int side = 0; side < 4; side++) {
			Direction wall = Direction.from2DDataValue(side);
			for (int along : new int[] {4, 11}) {
				if (random.nextInt(10) >= 6) continue;
				BlockPos inner = cell(x0, z0, wall, along, FLOOR + 2, 0);
				BlockPos face = inner.relative(wall);
				BlockPos behind = face.relative(wall);
				if (!FloorPlan.inside(behind.getX(), behind.getY(), behind.getZ())) continue;
				boolean ok = true;
				for (int dy = 0; dy < 2; dy++) {
					ok &= plan.solid(face.getX(), face.getY() + dy, face.getZ()) && plan.solid(behind.getX(), behind.getY() + dy, behind.getZ());
					BlockState there = plan.get(face.getX(), face.getY() + dy, face.getZ());
					ok &= there != null && !there.is(theme.trim()) && !there.is(theme.pillar()) && !(there.getBlock() instanceof SpireDoorBlock);
				}
				ok &= plan.solid(face.getX(), face.getY() + 2, face.getZ()) && plan.solid(face.getX(), face.getY() - 1, face.getZ());
				if (!ok) continue;
				plan.set(face, turned(niche(theme, random), wall));
				plan.set(face.above(), null);
			}
		}
	}

	/**
	 * The room's bones: beams of the theme's pillar wood or stone across the ceiling, and in the middle of a wall with no
	 * doorway a window of the theme's glass with a light behind it in the wall between two rooms (never in the floor's
	 * outer wall, where there is nothing behind).
	 */
	static void architecture(FloorPlan plan, Theme theme, int x0, int z0, int height) {
		int top = FLOOR + height + 1;
		for (int beam : new int[] {4, 11}) {
			for (int i = 1; i < CELL - 1; i++) {
				if (plan.get(x0 + i, top - 1, z0 + beam) == null) plan.set(x0 + i, top - 1, z0 + beam, beamState(theme, Direction.Axis.X));
			}
		}
		int mid = FLOOR + 1 + height / 2;
		int low = height >= 8 ? mid + 1 : FLOOR + 2;
		int high = height >= 8 ? Math.min(top - 2, mid + 3) : mid - 1;
		if (high - low < 1) return;
		for (int side = 0; side < 4; side++) {
			Direction wall = Direction.from2DDataValue(side);
			boolean clear = true;
			List<BlockPos[]> cells = new ArrayList<>();
			for (int along = 6; along <= 9; along++) {
				for (int y = low; y <= high; y++) {
					BlockPos face = cell(x0, z0, wall, along, y, 0).relative(wall);
					BlockPos behind = face.relative(wall);
					BlockState there = plan.get(face.getX(), face.getY(), face.getZ());
					// a doorway's frame or opening in the way: no window on this wall
					// (or the next room's window already lit from this side)
					if (there == null || there.is(theme.trim()) || there.is(theme.light()) || there.is(theme.glass()) || !FloorPlan.inside(behind.getX(), behind.getY(), behind.getZ())
							|| !plan.solid(behind.getX(), behind.getY(), behind.getZ())) {
						clear = false;
					}
					if (along == 7 || along == 8) cells.add(new BlockPos[] {face, behind});
				}
			}
			if (!clear) continue;
			for (BlockPos[] pair : cells) {
				plan.set(pair[0], theme.glass().defaultBlockState());
				plan.set(pair[1], theme.light().defaultBlockState());
			}
		}
	}

	private static BlockState beamState(Theme theme, Direction.Axis axis) {
		BlockState state = theme.pillar().defaultBlockState();
		return state.hasProperty(BlockStateProperties.AXIS) ? state.setValue(BlockStateProperties.AXIS, axis) : state;
	}

	/** A free-standing piece of the theme, set in a quarter of a room (dx, up, dz from the quarter's middle). */
	private static List<Piece> feature(Theme theme) {
		Block leaves = Blocks.FLOWERING_AZALEA_LEAVES;
		return switch (theme.name()) {
			case "archive" -> List.of(p(0, 0, 0, Blocks.BOOKSHELF), p(1, 0, 0, Blocks.BOOKSHELF), p(0, 1, 0, Blocks.CHISELED_BOOKSHELF),
					p(1, 1, 0, Blocks.BOOKSHELF), p(0, 2, 0, candles(3)), p(1, 2, 0, lantern()));
			case "workshop" -> List.of(p(0, 0, 0, Blocks.BLAST_FURNACE), p(1, 0, 0, Blocks.WAXED_COPPER_BLOCK), p(1, 1, 0, Blocks.LIGHTNING_ROD),
					p(0, 1, 0, Blocks.CHAIN), p(0, 0, 1, Blocks.ANVIL));
			case "gardens" -> List.of(p(0, 0, 0, Blocks.MOSS_BLOCK), p(1, 0, 0, Blocks.MOSS_BLOCK), p(0, 0, 1, Blocks.MOSS_BLOCK), p(1, 0, 1, Blocks.MOSS_BLOCK),
					p(0, 1, 0, Blocks.FLOWERING_AZALEA), p(1, 1, 0, Blocks.POPPY), p(0, 1, 1, Blocks.ALLIUM), p(1, 1, 1, Blocks.AZURE_BLUET),
					p(0, 2, 0, leaves.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true)));
			case "galleries" -> List.of(p(0, -1, 0, Blocks.WATER), p(1, -1, 0, Blocks.WATER), p(0, 0, 0, pickles(3).setValue(SeaPickleBlock.WATERLOGGED, true)),
					p(1, 0, 0, Blocks.TUBE_CORAL_FAN), p(-1, 0, 0, Blocks.PRISMARINE_WALL), p(2, 0, 0, Blocks.PRISMARINE_WALL));
			case "ice" -> List.of(p(0, 0, 0, Blocks.PACKED_ICE), p(0, 1, 0, Blocks.PACKED_ICE), p(0, 2, 0, Blocks.BLUE_ICE), p(1, 0, 0, Blocks.BLUE_ICE),
					p(0, 0, 1, Blocks.SNOW_BLOCK), p(1, 1, 0, Blocks.SNOW));
			case "spores" -> List.of(p(0, 0, 0, Blocks.MUSHROOM_STEM), p(0, 1, 0, Blocks.MUSHROOM_STEM), p(0, 2, 0, Blocks.RED_MUSHROOM_BLOCK),
					p(1, 2, 0, Blocks.RED_MUSHROOM_BLOCK), p(-1, 2, 0, Blocks.RED_MUSHROOM_BLOCK), p(0, 2, 1, Blocks.RED_MUSHROOM_BLOCK),
					p(0, 2, -1, Blocks.RED_MUSHROOM_BLOCK), p(1, 0, 1, Blocks.BROWN_MUSHROOM));
			case "hive" -> List.of(p(0, 0, 0, Blocks.HONEYCOMB_BLOCK), p(0, 1, 0, Blocks.BEE_NEST), p(1, 0, 0, Blocks.HONEY_BLOCK), p(1, 1, 0, candles(2)),
					p(0, 2, 0, lantern()));
			case "observatory" -> List.of(p(0, 0, 0, Blocks.POLISHED_BLACKSTONE_BRICKS), p(0, 1, 0, Blocks.LODESTONE), p(0, 2, 0, Blocks.LIGHTNING_ROD),
					p(1, 0, 0, Blocks.CARTOGRAPHY_TABLE), p(1, 1, 0, Blocks.AMETHYST_CLUSTER));
			case "summit" -> List.of(p(0, 0, 0, Blocks.CHISELED_QUARTZ_BLOCK), p(0, 1, 0, Blocks.QUARTZ_PILLAR), p(0, 2, 0, Blocks.QUARTZ_PILLAR),
					p(0, 3, 0, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false)));
			default -> List.of(p(0, 0, 0, Blocks.MOSSY_COBBLESTONE), p(1, 0, 0, Blocks.MOSSY_STONE_BRICK_STAIRS), p(0, 1, 0, Blocks.MOSSY_COBBLESTONE_WALL),
					p(0, 2, 0, lantern()), p(1, 0, 1, Blocks.MOSS_CARPET), p(-1, 0, 0, Blocks.AMETHYST_CLUSTER));
		};
	}

	/**
	 * A quiet room's quarters: one or two of the theme's free-standing pieces (a book stack, a machine, a flower bed,
	 * a basin, an ice spike, a toadstool, a hive, a telescope, a statue, a rubble heap) in its corners, off the lanes
	 * that cross it from doorway to doorway, where the floor is free and solid.
	 */
	static void centre(FloorPlan plan, Theme theme, Random random, int x0, int z0) {
		List<int[]> quarters = new ArrayList<>(List.of(new int[] {4, 4}, new int[] {10, 4}, new int[] {4, 10}, new int[] {10, 10}));
		Collections.shuffle(quarters, random);
		int want = 2 + random.nextInt(2);
		for (int[] q : quarters) {
			if (want == 0) break;
			List<Piece> piece = feature(theme);
			boolean fits = true;
			for (Piece part : piece) {
				int x = x0 + q[0] + part.along();
				int z = z0 + q[1] + part.out();
				int y = FLOOR + 1 + part.up();
				// off the lanes (5..10 across the middle) and a cell clear of the walls
				fits &= x - x0 >= 2 && x - x0 <= 13 && z - z0 >= 2 && z - z0 <= 13 && (x - x0 < 5 || x - x0 > 10 || z - z0 < 5 || z - z0 > 10);
				fits &= (x - x0 < 6 || x - x0 > 9) && (z - z0 < 6 || z - z0 > 9);
				fits &= part.up() < 0 ? plan.solid(x, y, z) : plan.get(x, y, z) == null;
				fits &= part.up() != 0 || plan.solid(x, FLOOR, z);
			}
			if (!fits) continue;
			for (Piece part : piece) plan.set(new BlockPos(x0 + q[0] + part.along(), FLOOR + 1 + part.up(), z0 + q[1] + part.out()), part.state());
			want--;
		}
	}

	/** A few of the theme's scenes along a room's walls (fewer where a fight is), where their cells are free. */
	static void place(FloorPlan plan, Theme theme, Random random, int x0, int z0, int count) {
		List<List<Piece>> scenes = scenes(theme);
		List<int[]> spots = new ArrayList<>();
		for (int along = 3; along <= CELL - 4; along++) {
			if (Math.abs(along - 7.5) < 3) continue;
			for (int side = 0; side < 4; side++) spots.add(new int[] {side, along});
		}
		Collections.shuffle(spots, random);
		int placed = 0;
		for (int[] spot : spots) {
			if (placed >= count) break;
			Direction wall = Direction.from2DDataValue(spot[0]);
			List<Piece> scene = scenes.get(random.nextInt(scenes.size()));
			if (fits(plan, x0, z0, wall, spot[1], scene)) {
				for (Piece piece : scene) {
					BlockPos at = cell(x0, z0, wall, spot[1] + piece.along(), FLOOR + 1 + piece.up(), piece.out());
					plan.set(at, turned(piece.state(), wall));
				}
				placed++;
			}
		}
	}

	/** A stair or a facing block of a scene turned to face out of its wall. */
	private static BlockState turned(BlockState state, Direction wall) {
		Direction out = wall.getOpposite();
		if (state.hasProperty(StairBlock.FACING) && state.getBlock() instanceof StairBlock) return state.setValue(StairBlock.FACING, wall);
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return state.setValue(BlockStateProperties.HORIZONTAL_FACING, out);
		return state;
	}

	private static boolean fits(FloorPlan plan, int x0, int z0, Direction wall, int along, List<Piece> scene) {
		for (Piece piece : scene) {
			int a = along + piece.along();
			if (a < 2 || a > CELL - 3 || Math.abs(a - 7.5) < 2.5) return false;
			BlockPos at = cell(x0, z0, wall, a, FLOOR + 1 + piece.up(), piece.out());
			if (plan.get(at.getX(), at.getY(), at.getZ()) != null) return false;
			if (piece.up() == 0 && !plan.solid(at.getX(), FLOOR, at.getZ())) return false;
		}
		return true;
	}

	/** The cell {@code out} in from the wall {@code wall} of the room, {@code along} along it. */
	private static BlockPos cell(int x0, int z0, Direction wall, int along, int y, int out) {
		return switch (wall) {
			case NORTH -> new BlockPos(x0 + along, y, z0 + 1 + out);
			case SOUTH -> new BlockPos(x0 + CELL - 1 - along, y, z0 + CELL - 2 - out);
			case WEST -> new BlockPos(x0 + 1 + out, y, z0 + CELL - 1 - along);
			default -> new BlockPos(x0 + CELL - 2 - out, y, z0 + along);
		};
	}
}
