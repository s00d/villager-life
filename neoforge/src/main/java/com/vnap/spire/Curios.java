package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Where a floor's curios and its riddle go (FloorGenerator): along a room's walls, off the doorways' lanes, never on
 * what stands there already. A theme has its own curio (SpireCurioBlock, its one or few to a room); a riddle takes a
 * dead-end side room, its pieces against the wall facing the way in, its cache in the middle (Puzzles).
 */
final class Curios {
	private static final int CELL = FloorGenerator.CELL;
	private static final int FLOOR = FloorGenerator.FLOOR;

	private Curios() {
	}

	/** The curio of a theme, and how many of it a room has. */
	private static SpireCurioBlock curio(Theme theme) {
		return switch (theme.name()) {
			case "archive" -> SpireBlocks.LECTERN;
			case "workshop" -> SpireBlocks.CRANK;
			case "gardens" -> SpireBlocks.BUD;
			case "galleries" -> SpireBlocks.VALVE;
			case "spores" -> SpireBlocks.PUFFBALL;
			case "hive" -> SpireBlocks.HONEY_TAP;
			case "observatory" -> SpireBlocks.ORRERY;
			case "summit" -> SpireBlocks.RESONATOR;
			default -> SpireBlocks.GONG;
		};
	}

	static void place(FloorPlan plan, Theme theme, Random random, int x0, int z0) {
		if (theme.name().equals("ice")) {
			// the ice rooms' toy is a row of chimes: struck, each its own note
			List<BlockPos> spots = floorSpots(plan, random, x0, z0);
			for (int i = 0; i < Math.min(3, spots.size()); i++) {
				plan.set(spots.get(i), SpireBlocks.CHIME.defaultBlockState().setValue(SpirePuzzleBlock.NOTE, random.nextInt(4)));
				plan.set(spots.get(i).below(), theme.trim().defaultBlockState());
			}
			return;
		}
		SpireCurioBlock curio = curio(theme);
		if (curio.kind().onWall) {
			Wall wall = wallSpot(plan, random, x0, z0, FLOOR + 2);
			if (wall != null) plan.set(wall.pos(), curio.defaultBlockState().setValue(SpireCurioBlock.FACING, wall.facing()));
			return;
		}
		List<BlockPos> spots = floorSpots(plan, random, x0, z0);
		int count = curio == SpireBlocks.BUD || curio == SpireBlocks.PUFFBALL ? 3 : 1;
		for (int i = 0; i < Math.min(count, spots.size()); i++) {
			BlockPos at = spots.get(i);
			plan.set(at, curio.defaultBlockState().setValue(SpireCurioBlock.FACING, towardMiddle(x0, z0, at)));
			// stood on a plinth of the theme's trim: it reads as something set there, not a block lying about
			plan.set(at.below(), theme.trim().defaultBlockState());
		}
		if (curio == SpireBlocks.CRANK && !spots.isEmpty()) {
			// the cogs the crank turns, up on the nearest wall
			for (int i = 0; i < 3; i++) {
				Wall wall = wallSpot(plan, random, x0, z0, FLOOR + 3 + random.nextInt(2));
				if (wall != null) plan.set(wall.pos(), SpireBlocks.COG.defaultBlockState().setValue(SpireCurioBlock.FACING, wall.facing()));
			}
		}
	}

	private static Direction towardMiddle(int x0, int z0, BlockPos at) {
		double dx = x0 + 7.5 - at.getX();
		double dz = z0 + 7.5 - at.getZ();
		return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** Cells two in from a room's walls, off the doorway lanes, with floor under them and room over them. */
	private static List<BlockPos> floorSpots(FloorPlan plan, Random random, int x0, int z0) {
		List<BlockPos> spots = new ArrayList<>();
		for (int i = 3; i < CELL - 3; i++) {
			if (Math.abs(i - 7.5) < 2.5) continue;
			for (int[] at : new int[][] {{i, 2}, {i, CELL - 3}, {2, i}, {CELL - 3, i}}) {
				int x = x0 + at[0];
				int z = z0 + at[1];
				if (plan.solid(x, FLOOR, z) && plan.get(x, FLOOR + 1, z) == null && plan.get(x, FLOOR + 2, z) == null && plan.get(x, FLOOR + 3, z) == null) {
					spots.add(new BlockPos(x, FLOOR + 1, z));
				}
			}
		}
		Collections.shuffle(spots, random);
		// a cluster stays together: the rest sorted by their distance from the first
		if (spots.size() > 1) {
			BlockPos first = spots.get(0);
			spots.sort((a, b) -> Double.compare(a.distSqr(first), b.distSqr(first)));
		}
		return spots;
	}

	private record Wall(BlockPos pos, Direction facing) {
	}

	private record Room(int col, int row, Direction way) {
	}

	/** A cell against one of a room's walls at this height, off the doorways, the wall solid behind it. */
	private static @Nullable Wall wallSpot(FloorPlan plan, Random random, int x0, int z0, int y) {
		List<Wall> walls = new ArrayList<>();
		for (int along = 2; along < CELL - 2; along++) {
			if (Math.abs(along - 7.5) < 3) continue;
			walls.add(new Wall(new BlockPos(x0 + along, y, z0 + 1), Direction.SOUTH));
			walls.add(new Wall(new BlockPos(x0 + along, y, z0 + CELL - 2), Direction.NORTH));
			walls.add(new Wall(new BlockPos(x0 + 1, y, z0 + along), Direction.EAST));
			walls.add(new Wall(new BlockPos(x0 + CELL - 2, y, z0 + along), Direction.WEST));
		}
		Collections.shuffle(walls, random);
		for (Wall wall : walls) {
			BlockPos behind = wall.pos().relative(wall.facing().getOpposite());
			if (plan.get(wall.pos().getX(), wall.pos().getY(), wall.pos().getZ()) == null && plan.solid(behind.getX(), behind.getY(), behind.getZ())) return wall;
		}
		return null;
	}

	/** One dead-end side room of the floor gets a riddle (not the secret's, not the villager's note's). */
	static void riddle(FloorPlan plan, Theme theme, Random random, int floor, FloorGenerator.Role[][] roles, boolean[][] east, boolean[][] south) {
		List<Room> rooms = new ArrayList<>();
		int cells = roles.length;
		for (int col = 0; col < cells; col++) {
			for (int row = 0; row < cells; row++) {
				if (roles[col][row] != FloorGenerator.Role.SIDE) continue;
				List<Direction> ways = new ArrayList<>();
				if (row > 0 && south[col][row - 1]) ways.add(Direction.NORTH);
				if (south[col][row]) ways.add(Direction.SOUTH);
				if (col > 0 && east[col - 1][row]) ways.add(Direction.WEST);
				if (east[col][row]) ways.add(Direction.EAST);
				if (ways.size() != 1 || holds(plan.secret, col, row) || holds(plan.villagerNote, col, row)) continue;
				rooms.add(new Room(col, row, ways.get(0)));
			}
		}
		if (rooms.isEmpty()) return;
		Room room = rooms.get(random.nextInt(rooms.size()));
		int x0 = room.col() * CELL;
		int z0 = room.row() * CELL;
		Direction back = room.way().getOpposite();
		Direction into = back.getOpposite();
		Puzzles.Kind kind = theme.name().equals("ice") ? Puzzles.Kind.CHIMES : List.of(Puzzles.Kind.LEVERS, Puzzles.Kind.CHIMES, Puzzles.Kind.PLATES).get(random.nextInt(3));
		BlockPos cache = new BlockPos(x0 + 7, FLOOR + 1, z0 + 7);
		clear(plan, cache);
		List<BlockPos> parts = new ArrayList<>();
		List<BlockPos> glyphs = new ArrayList<>();
		List<Integer> tune = new ArrayList<>();
		int[] alongs = {4, 6, 9, 11};
		switch (kind) {
			case LEVERS -> {
				// a start that is not yet done: some glyphs dark (any four on a row can be put right)
				boolean[] lit = new boolean[4];
				do {
					for (int i = 0; i < 4; i++) lit[i] = random.nextBoolean();
				} while (lit[0] && lit[1] && lit[2] && lit[3]);
				for (int i = 0; i < 4; i++) {
					BlockPos lever = onWall(x0, z0, back, alongs[i], FLOOR + 2);
					BlockPos glyph = lever.above(2);
					clear(plan, lever);
					clear(plan, glyph);
					plan.set(lever, SpireBlocks.LEVER.defaultBlockState().setValue(SpirePuzzleBlock.FACING, into));
					plan.set(glyph, SpireBlocks.GLYPH.defaultBlockState().setValue(SpirePuzzleBlock.FACING, into).setValue(SpirePuzzleBlock.LIT, lit[i]));
					parts.add(lever);
					glyphs.add(glyph);
				}
			}
			case CHIMES -> {
				List<Integer> notes = new ArrayList<>(List.of(0, 1, 2, 3));
				Collections.shuffle(notes, random);
				for (int i = 0; i < 4; i++) {
					BlockPos chime = onWall(x0, z0, back, alongs[i], FLOOR + 1).relative(into, 2);
					clear(plan, chime);
					plan.set(chime, SpireBlocks.CHIME.defaultBlockState().setValue(SpirePuzzleBlock.NOTE, notes.get(i)));
					parts.add(chime);
				}
				BlockPos conductor = onWall(x0, z0, back, 7, FLOOR + 3);
				clear(plan, conductor);
				plan.set(conductor, SpireBlocks.GLYPH.defaultBlockState().setValue(SpirePuzzleBlock.FACING, into).setValue(SpirePuzzleBlock.LIT, true));
				glyphs.add(conductor);
				int length = 4 + Math.min(2, floor / 40);
				for (int i = 0; i < length; i++) tune.add(random.nextInt(4));
			}
			case PLATES -> {
				List<int[]> spots = new ArrayList<>(List.of(new int[] {3, 3}, new int[] {12, 3}, new int[] {3, 12}, new int[] {12, 12}, new int[] {5, 9},
						new int[] {10, 5}, new int[] {7, 3}, new int[] {3, 8}, new int[] {12, 8}, new int[] {8, 12}));
				Collections.shuffle(spots, random);
				for (int[] spot : spots) {
					if (parts.size() == 5) break;
					BlockPos plate = new BlockPos(x0 + spot[0], FLOOR + 1, z0 + spot[1]);
					if (!plan.solid(plate.getX(), FLOOR, plate.getZ())) continue;
					clear(plan, plate);
					plan.set(plate, SpireBlocks.PLATE.defaultBlockState());
					parts.add(plate);
				}
			}
		}
		if (parts.size() < 3) return;
		plan.puzzle = new FloorPlan.Puzzle(kind, List.copyOf(parts), List.copyOf(glyphs), List.copyOf(tune), cache);
	}

	/** The cell against the wall {@code side} of the room at {@code along} (counted along that wall). */
	private static BlockPos onWall(int x0, int z0, Direction side, int along, int y) {
		return switch (side) {
			case NORTH -> new BlockPos(x0 + along, y, z0 + 1);
			case SOUTH -> new BlockPos(x0 + along, y, z0 + CELL - 2);
			case WEST -> new BlockPos(x0 + 1, y, z0 + along);
			default -> new BlockPos(x0 + CELL - 2, y, z0 + along);
		};
	}

	private static void clear(FloorPlan plan, BlockPos pos) {
		BlockState there = plan.get(pos.getX(), pos.getY(), pos.getZ());
		if (there != null) plan.set(pos, null);
	}

	private static boolean holds(@Nullable BlockPos pos, int col, int row) {
		return pos != null && pos.getX() / CELL == col && pos.getZ() / CELL == row;
	}
}
