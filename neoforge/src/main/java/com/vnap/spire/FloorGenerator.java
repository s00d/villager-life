package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A floor out of its seed (docs/tower/DESIGN.md §4): a four by four grid of rooms joined as a maze with a loop or
 * two, the way from the entrance to the onward portal through the fights, rooms dressed by the floor's theme.
 * The seeds of its parts are their own (layout, dressing, guards), so a change to one leaves the others as they were.
 * A plan that does not hold together (something out of reach) is drawn again from the next seed, a few times.
 */
public final class FloorGenerator {
	/** Bumped when floors change shape: a floor of an older version is drawn as the new one draws it. */
	public static final int VERSION = 1;
	static final int CELLS = 4;
	static final int CELL = 16;
	/** The floor level of the rooms: below it the pools and cellars. */
	public static final int FLOOR = 8;
	private static final int TRIES = 4;
	/** Guards a floor has at most (a warden besides): a harder floor has stronger guards and worse company, not more. */
	static final int GUARDS = 15;
	private static final Theme[] CHAPTERS = {Theme.ROOTS, Theme.ARCHIVE, Theme.WORKSHOP, Theme.GARDENS, Theme.GALLERIES,
			Theme.ICE, Theme.SPORES, Theme.HIVE, Theme.OBSERVATORY, Theme.SUMMIT};

	enum Role {
		START, EXIT, ARENA, HALL, POOL, SIDE
	}

	private FloorGenerator() {
	}

	/** The main theme of a chapter (0 the first), round again past the tenth. */
	public static Theme chapter(int index) {
		return CHAPTERS[Math.floorMod(index, CHAPTERS.length)];
	}

	/** SplitMix64 of the two: a floor's seed from its spire's, a part's from the floor's. */
	public static long mix(long a, long b) {
		long z = a + 0x9E3779B97F4A7C15L * (b + 1);
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public static long seed(long spireSeed, int floor) {
		return mix(mix(spireSeed, floor), VERSION);
	}

	/** The chapter's theme mostly, now and then one of its neighbours'. */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "seeded: the same seed draws the same theme")
	public static Theme theme(long seed, int floor) {
		int chapter = Math.floorMod((floor - 1) / 10, CHAPTERS.length);
		Random random = new Random(mix(seed, 7));
		int roll = random.nextInt(10);
		if (roll < 6) return CHAPTERS[chapter];
		// a neighbouring chapter's look, never round the end: the first floors are not dressed as the summit
		if (roll < 8) return CHAPTERS[chapter > 0 ? chapter - 1 : chapter + 1];
		return CHAPTERS[chapter < CHAPTERS.length - 1 ? chapter + 1 : chapter - 1];
	}

	public static FloorPlan plan(long spireSeed, int floor) {
		long seed = seed(spireSeed, floor);
		for (int attempt = 0; attempt < TRIES; attempt++) {
			FloorPlan plan = draw(floor, seed, mix(seed, 100 + attempt));
			if (holds(plan)) return plan;
		}
		// four drawings that did not hold: the plainest floor there is, which always does
		return draw(floor, seed, Long.MIN_VALUE);
	}

	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "seeded: the same seed draws the same floor")
	private static FloorPlan draw(int floor, long seed, long layoutSeed) {
		FloorPlan plan = new FloorPlan(floor, seed);
		Theme theme = theme(seed, floor);
		plan.theme = theme.name();
		boolean plain = layoutSeed == Long.MIN_VALUE;
		Random layout = new Random(layoutSeed);
		Random dress = new Random(mix(seed, 2));
		Random guards = new Random(mix(seed, 3));

		int startCol = plain ? 1 : layout.nextInt(CELLS);
		int endCol = plain ? 1 : layout.nextInt(CELLS);
		boolean[][] linkEast = new boolean[CELLS][CELLS];
		boolean[][] linkSouth = new boolean[CELLS][CELLS];
		if (plain) {
			// a straight run down the middle
			for (int row = 0; row < CELLS - 1; row++) linkSouth[1][row] = true;
		} else {
			maze(layout, startCol, linkEast, linkSouth);
			loops(layout, linkEast, linkSouth, 1 + layout.nextInt(2));
		}
		List<int[]> path = path(startCol, endCol, linkEast, linkSouth);
		Role[][] roles = new Role[CELLS][CELLS];
		for (int[] cell : path) roles[cell[0]][cell[1]] = Role.HALL;
		roles[startCol][0] = Role.START;
		roles[endCol][CELLS - 1] = Role.EXIT;
		List<int[]> middle = new ArrayList<>();
		for (int[] cell : path) if (roles[cell[0]][cell[1]] == Role.HALL) middle.add(cell);
		int arenas = Math.min(middle.size(), 2 + (floor % 5 == 0 ? 0 : guards.nextInt(2)));
		for (int i = 0; i < arenas; i++) {
			int[] cell = middle.get((int) ((i + 0.5) * middle.size() / arenas));
			roles[cell[0]][cell[1]] = Role.ARENA;
		}
		boolean pooled = false;
		for (int col = 0; col < CELLS; col++) {
			for (int row = 0; row < CELLS; row++) {
				if (roles[col][row] != null) continue;
				if (!plain && !pooled && theme.water() && dress.nextInt(3) == 0) {
					roles[col][row] = Role.POOL;
					pooled = true;
				} else {
					roles[col][row] = Role.SIDE;
				}
			}
		}
		// a side room nothing leads to is no room: in the plain floor the off-path cells are left solid
		int arena = 0;
		int[][] heights = new int[CELLS][CELLS];
		for (int col = 0; col < CELLS; col++) {
			for (int row = 0; row < CELLS; row++) {
				heights[col][row] = switch (roles[col][row]) {
					case START -> 7;
					case EXIT -> 9;
					case ARENA -> 9 + layout.nextInt(4);
					case POOL -> 8;
					default -> 6 + layout.nextInt(4);
				};
			}
		}
		// the grandest hall keeps the floor's wonder
		int[] grand = null;
		int grandHeight = 0;
		for (int col = 0; col < CELLS; col++) {
			for (int row = 0; row < CELLS; row++) {
				if (roles[col][row] == Role.HALL && heights[col][row] > grandHeight) {
					grandHeight = heights[col][row];
					grand = new int[] {col, row};
				}
			}
		}
		// two rooms of the way side by side, now and then one long hall (the wall between them goes, so does the door)
		List<int[]> merged = new ArrayList<>();
		boolean[][] joined = new boolean[CELLS][CELLS];
		for (int i = 0; !plain && i + 1 < path.size() && merged.size() < 2; i++) {
			int[] a = path.get(i);
			int[] b = path.get(i + 1);
			if (!fightable(roles[a[0]][a[1]]) || !fightable(roles[b[0]][b[1]]) || joined[a[0]][a[1]] || joined[b[0]][b[1]] || layout.nextInt(10) >= 3) continue;
			int height = Math.max(heights[a[0]][a[1]], heights[b[0]][b[1]]);
			heights[a[0]][a[1]] = height;
			heights[b[0]][b[1]] = height;
			joined[a[0]][a[1]] = true;
			joined[b[0]][b[1]] = true;
			merged.add(new int[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), a[1] == b[1] ? 1 : 0});
		}
		boolean[][] daised = new boolean[CELLS][CELLS];
		for (int col = 0; col < CELLS; col++) {
			for (int row = 0; row < CELLS; row++) {
				boolean reached = plain ? col == 1 : true;
				if (!reached) continue;
				Role role = roles[col][row];
				int height = heights[col][row];
				room(plan, theme, dress, col, row, height, role);
				boolean isGrand = grand != null && grand[0] == col && grand[1] == row;
				if (!plain && role == Role.HALL && !isGrand && !joined[col][row] && height >= 7 && floor >= 3 && layout.nextInt(10) < theme.daises()) {
					dais(plan, theme, col * CELL, row * CELL);
					daised[col][row] = true;
				}
				if (role == Role.SIDE && plan.villagerNote == null && dress.nextInt(3) != 0) {
					plan.villagerNote = new BlockPos(col * CELL + 8, FLOOR + 1, row * CELL + 8);
					plan.set(plan.villagerNote, null);
				}
				if (role == Role.ARENA) {
					int before = plan.guards.size();
					guard(plan, guards, col, row, 3 + guards.nextInt(2) + Math.min(3, floor / 15) + Math.min(2, Genome.round(floor)), arena++);
					// an arena's elite, one more each hundred floors climbed (to three)
					int elites = floor >= 8 ? 1 + Math.min(2, Genome.round(floor)) : 0;
					for (int i = 0; i < elites && before + i < plan.guards.size(); i++) plan.elites.add(plan.guards.get(before + i));
				} else if (role == Role.HALL && guards.nextInt(3) == 0) {
					guard(plan, guards, col, row, 1 + guards.nextInt(2), arena++);
				} else if (role == Role.POOL) {
					swimmers(plan, guards, col, row, arena++);
				}
			}
		}
		for (int[] pair : merged) join(plan, theme, dress, pair[0], pair[1], pair[2] == 1, heights[pair[0]][pair[1]]);
		// the floor's wonder in its grandest hall (none when the floor has no hall)
		if (grand != null && !plain) Wonders.build(plan, theme, dress, grand[0] * CELL + 8, grand[1] * CELL + 8, grandHeight - 1);
		// nobody stands where the wonder went up
		plan.guards.removeIf(guard -> !plan.empty(guard.pos().getX(), guard.pos().getY(), guard.pos().getZ())
				|| !plan.empty(guard.pos().getX(), guard.pos().getY() + 1, guard.pos().getZ()));
		if (Genome.schism(floor)) {
			// the Schism's pair in the last room, either side of its middle
			for (int i = 0; i < 2; i++) {
				FloorPlan.Spawn one = new FloorPlan.Spawn(new BlockPos(endCol * CELL + 5 + 6 * i, FLOOR + 1, (CELLS - 1) * CELL + 8), guards.nextLong() | 1L, arena);
				plan.guards.add(one);
				plan.duo.add(one);
			}
		}
		if (floor % 10 == 0 || Genome.trial(floor)) {
			// the keeper of the chapter (or the half-chapter's trial) has the last room to itself, the last of the guards
			FloorPlan.Spawn keeper = new FloorPlan.Spawn(new BlockPos(endCol * CELL + 8, FLOOR + 1, (CELLS - 1) * CELL + 8), guards.nextLong() | 1L, arena);
			plan.guards.add(keeper);
			plan.keeper = keeper;
			// past the hundredth floor a keeper does not stand alone: one of another chapter beside it, two past the
			// two hundredth (a trial gets an elite pack instead)
			int round = Genome.round(floor);
			for (int i = 0; i < Math.min(2, round); i++) {
				BlockPos at = new BlockPos(endCol * CELL + 3 + 10 * i, FLOOR + 1, (CELLS - 1) * CELL + 5 + 6 * i);
				FloorPlan.Spawn other = new FloorPlan.Spawn(at, guards.nextLong() | 1L, arena);
				plan.guards.add(other);
				if (floor % 10 == 0) {
					plan.companions.put(other, Math.floorMod(Genome.chapter(floor) + 3 + 4 * i, Genome.KEEPERS.size()));
				} else {
					plan.elites.add(other);
				}
			}
		}
		Random extras = new Random(mix(seed, 5));
		int[] doors = {0};
		for (int col = 0; col < CELLS; col++) {
			for (int row = 0; row < CELLS; row++) {
				// now and then a doorway has the Builders' door in it (a touch opens it): what is behind is not seen first
				if (linkEast[col][row] && !isMerged(merged, col, row, true)) door(plan, theme, col * CELL + 15, row * CELL + 7, true, !plain && floor >= 2 && extras.nextInt(5) == 0 && doors[0]++ < 3);
				if (linkSouth[col][row] && !isMerged(merged, col, row, false)) door(plan, theme, col * CELL + 7, row * CELL + 15, false, !plain && floor >= 2 && extras.nextInt(5) == 0 && doors[0]++ < 3);
			}
		}
		start(plan, theme, startCol);
		exit(plan, theme, endCol);
		Arenas.dress(plan, floor, endCol * CELL, (CELLS - 1) * CELL, heights[endCol][CELLS - 1]);
		if (!plain) secret(plan, new Random(mix(seed, 4)), floor, roles, linkEast, linkSouth);
		if (!plain) {
			for (int col = 0; col < CELLS; col++) {
				for (int row = 0; row < CELLS; row++) {
					Props.niches(plan, theme, extras, col * CELL, row * CELL);
					if (!joined[col][row]) Props.architecture(plan, theme, col * CELL, row * CELL, heights[col][row]);
					Role role = roles[col][row];
					if ((role == Role.HALL || role == Role.SIDE || role == Role.START) && !joined[col][row]) {
						Props.centre(plan, theme, extras, col * CELL, row * CELL);
					}
				}
			}
			for (int col = 0; col < CELLS; col++) {
				for (int row = 0; row < CELLS; row++) {
					Role role = roles[col][row];
					boolean quiet = role == Role.HALL || role == Role.SIDE || role == Role.START || role == Role.POOL;
					if (quiet && !joined[col][row] && extras.nextInt(10) < 4) Curios.place(plan, theme, extras, col * CELL, row * CELL);
				}
			}
			if (floor >= 3 && extras.nextInt(10) < 5) Curios.riddle(plan, theme, extras, floor, roles, linkEast, linkSouth);
			if (floor >= TRAPS_FROM) traps(plan, new Random(mix(seed, 9)), floor, roles, joined, daised, linkEast, linkSouth);
			// a pocket a climber drops into and cannot climb out of (between a wall and the dressing) is filled in
			for (int pass = 0; pass < 4; pass++) {
				List<BlockPos> traps = trapped(plan);
				if (traps.isEmpty()) break;
				for (BlockPos trap : traps) plan.set(trap, theme.wall(extras));
			}
			// nobody stands where the dressing went
			plan.guards.removeIf(guard -> !guard.equals(plan.keeper) && !plan.duo.contains(guard) && (!plan.empty(guard.pos().getX(), guard.pos().getY(), guard.pos().getZ())
					|| !plan.empty(guard.pos().getX(), guard.pos().getY() + 1, guard.pos().getZ())));
			plan.elites.removeIf(elite -> !plan.guards.contains(elite));
		}
		return plan;
	}

	// ---- the layout ------------------------------------------------------------------------

	/**
	 * Now and then (three floors in ten from the fourth, never more than one a floor) a dead-end side room is walled
	 * up: its one doorway laid with a cracked wall a climber can break (SpireCrackedWallBlock), a cache in the middle
	 * (SpireRewards: half a floor's shards and coins and a trinket, once a climber). Never the room of the villager's
	 * note — that one is to be found.
	 */
	private static void secret(FloorPlan plan, Random random, int floor, Role[][] roles, boolean[][] east, boolean[][] south) {
		if (floor < 4 || random.nextInt(10) >= 3) return;
		List<int[]> rooms = new ArrayList<>();
		for (int col = 0; col < CELLS; col++) {
			for (int row = 0; row < CELLS; row++) {
				if (roles[col][row] != Role.SIDE) continue;
				List<int[]> ways = neighbours(new int[] {col, row}, east, south);
				BlockPos note = plan.villagerNote;
				boolean noted = note != null && note.getX() / CELL == col && note.getZ() / CELL == row;
				if (ways.size() == 1 && !noted) rooms.add(new int[] {col, row, ways.get(0)[0], ways.get(0)[1]});
			}
		}
		if (rooms.isEmpty()) return;
		int[] room = rooms.get(random.nextInt(rooms.size()));
		int col = Math.min(room[0], room[2]);
		int row = Math.min(room[1], room[3]);
		boolean eastward = room[1] == room[3];
		int x = eastward ? col * CELL + 15 : col * CELL + 7;
		int z = eastward ? row * CELL + 7 : row * CELL + 15;
		for (int across = 0; across <= 2; across++) {
			for (int through = 0; through < 2; through++) {
				for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
					plan.set(eastward ? x + through : x + across, y, eastward ? z + across : z + through, SpireBlocks.CRACKED_WALL.defaultBlockState());
				}
			}
		}
		plan.secret = new BlockPos(room[0] * CELL + 8, FLOOR + 1, room[1] * CELL + 8);
		plan.set(plan.secret, SpireBlocks.CHEST.defaultBlockState());
	}

	private static boolean fightable(Role role) {
		return role == Role.HALL || role == Role.ARENA;
	}

	private static boolean isMerged(List<int[]> merged, int col, int row, boolean eastward) {
		for (int[] pair : merged) if (pair[0] == col && pair[1] == row && (pair[2] == 1) == eastward) return true;
		return false;
	}

	/**
	 * Two rooms made one long hall: the two walls between them taken out up to the ceiling, the floor laid across, and
	 * what hung on those walls taken down with them.
	 */
	private static void join(FloorPlan plan, Theme theme, Random random, int col, int row, boolean eastward, int height) {
		int top = FLOOR + height + 1;
		for (int along = 1; along < CELL - 1; along++) {
			for (int through = -1; through <= 2; through++) {
				int x = eastward ? col * CELL + CELL - 1 + through : col * CELL + along;
				int z = eastward ? row * CELL + along : row * CELL + CELL - 1 + through;
				boolean wall = through == 0 || through == 1;
				if (wall) plan.set(x, FLOOR, z, theme.floor(random));
				for (int y = FLOOR + 1; y < top; y++) {
					BlockState there = plan.get(x, y, z);
					if (wall || (there != null && there.getBlock() instanceof SpireFixtureBlock)) plan.set(x, y, z, null);
				}
				plan.set(x, top, z, theme.ceiling().defaultBlockState());
			}
		}
	}

	/**
	 * A dais in the middle of a hall: a platform two up, a flight of two steps in the middle of each side (the lanes
	 * between the doorways go over it), a balustrade round its edge, a rune and braziers on top. The hall's floor gets
	 * a second level without a drop anyone could be stuck in.
	 */
	private static void dais(FloorPlan plan, Theme theme, int x0, int z0) {
		BlockState body = theme.floor().get(0).defaultBlockState();
		for (int x = 5; x <= 10; x++) {
			for (int z = 5; z <= 10; z++) {
				plan.set(x0 + x, FLOOR + 1, z0 + z, body);
				plan.set(x0 + x, FLOOR + 2, z0 + z, x >= 7 && x <= 8 && z >= 7 && z <= 8 ? SpireBlocks.RUNE.defaultBlockState() : floorTile(theme, new Random(x * 31L + z), x, z));
				boolean edge = x == 5 || x == 10 || z == 5 || z == 10;
				boolean lane = (x >= 7 && x <= 8) || (z >= 7 && z <= 8);
				plan.set(x0 + x, FLOOR + 3, z0 + z, edge && !lane ? railing(theme) : null);
			}
		}
		for (int lane = 7; lane <= 8; lane++) {
			for (Direction up : Direction.Plane.HORIZONTAL) {
				// the outer step on the floor, the inner on a block of the dais
				for (int step = 0; step < 2; step++) {
					int out = 4 - step;
					int x = up == Direction.EAST ? out : up == Direction.WEST ? CELL - 1 - out : lane;
					int z = up == Direction.SOUTH ? out : up == Direction.NORTH ? CELL - 1 - out : lane;
					int y = FLOOR + 2 - step;
					if (y > FLOOR + 1) plan.set(x0 + x, FLOOR + 1, z0 + z, body);
					plan.set(x0 + x, y, z0 + z, steps(theme, up));
					for (int above = y + 1; above <= FLOOR + 4; above++) plan.set(x0 + x, above, z0 + z, null);
				}
			}
		}
		// braziers on the balustrade's corners
		for (int[] corner : new int[][] {{5, 5}, {10, 5}, {5, 10}, {10, 10}}) {
			plan.set(x0 + corner[0], FLOOR + 3, z0 + corner[1], theme.trim().defaultBlockState());
			plan.set(x0 + corner[0], FLOOR + 4, z0 + corner[1], SpireBlocks.BRAZIER.defaultBlockState());
			// the hall's lamps hang over these corners: not into a fire
			for (int y = FLOOR + 5; y < FloorPlan.SIZE_Y; y++) {
				BlockState hung = plan.get(x0 + corner[0], y, z0 + corner[1]);
				if (hung != null && (hung.is(Blocks.CHAIN) || hung.is(SpireBlocks.LAMP))) plan.set(x0 + corner[0], y, z0 + corner[1], null);
			}
		}
	}

	private static BlockState steps(Theme theme, Direction up) {
		Block stairs = family(theme, net.minecraft.world.level.block.StairBlock.class, "_stairs");
		if (stairs == null) return theme.trim().defaultBlockState();
		return stairs.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, up);
	}

	/** The first floor with traps on its way. */
	static final int TRAPS_FROM = 6;

	/**
	 * The Builders' traps (SpireTrapBlock) in a hall or two on the way, never in a fight's room: a pair of spike plates
	 * across a doorway's lane three in from the wall, or a dart wall in a side wall looking across a lane. Half the
	 * floors of the first chapters have one, later floors one or two.
	 */
	private static void traps(FloorPlan plan, Random random, int floor, Role[][] roles, boolean[][] joined, boolean[][] daised,
			boolean[][] east, boolean[][] south) {
		int count = floor < 20 ? (random.nextInt(10) < 5 ? 1 : 0) : 1 + random.nextInt(2);
		List<int[]> halls = new ArrayList<>();
		for (int col = 0; col < CELLS; col++) {
			for (int row = 0; row < CELLS; row++) {
				if (roles[col][row] == Role.HALL && !joined[col][row] && !daised[col][row]) halls.add(new int[] {col, row});
			}
		}
		java.util.Collections.shuffle(halls, random);
		for (int[] hall : halls) {
			if (count == 0) return;
			int col = hall[0];
			int row = hall[1];
			int x0 = col * CELL;
			int z0 = row * CELL;
			List<Direction> doors = new ArrayList<>();
			if (row > 0 && south[col][row - 1]) doors.add(Direction.NORTH);
			if (south[col][row]) doors.add(Direction.SOUTH);
			if (col > 0 && east[col - 1][row]) doors.add(Direction.WEST);
			if (east[col][row]) doors.add(Direction.EAST);
			if (doors.isEmpty()) continue;
			Direction door = doors.get(random.nextInt(doors.size()));
			boolean placed = random.nextBoolean() ? spikes(plan, x0, z0, door) : dart(plan, x0, z0, door);
			if (placed) count--;
		}
	}

	/** Two spike plates across the lane in from {@code door}, three cells from its wall. */
	private static boolean spikes(FloorPlan plan, int x0, int z0, Direction door) {
		List<BlockPos> cells = new ArrayList<>();
		for (int lane = 7; lane <= 8; lane++) {
			int x = door == Direction.WEST ? 3 : door == Direction.EAST ? CELL - 4 : lane;
			int z = door == Direction.NORTH ? 3 : door == Direction.SOUTH ? CELL - 4 : lane;
			cells.add(new BlockPos(x0 + x, FLOOR + 1, z0 + z));
		}
		for (BlockPos cell : cells) {
			if (!plan.solid(cell.getX(), FLOOR, cell.getZ()) || plan.solid(cell.getX(), cell.getY(), cell.getZ())
					|| plan.solid(cell.getX(), cell.getY() + 1, cell.getZ())) return false;
		}
		for (BlockPos cell : cells) plan.set(cell, SpireBlocks.SPIKES.defaultBlockState());
		return true;
	}

	/** A dart wall in the side wall four cells in from {@code door}, looking across the room over the lane. */
	private static boolean dart(FloorPlan plan, int x0, int z0, Direction door) {
		// the lane runs in from the door; the dart sits in a wall beside it, at knee height, aimed across
		boolean alongZ = door == Direction.NORTH || door == Direction.SOUTH;
		int in = door == Direction.NORTH || door == Direction.WEST ? 4 : CELL - 5;
		Direction looks = alongZ ? Direction.EAST : Direction.SOUTH;
		BlockPos wall = alongZ ? new BlockPos(x0, FLOOR + 1, z0 + in) : new BlockPos(x0 + in, FLOOR + 1, z0);
		BlockPos front = wall.relative(looks);
		if (!plan.solid(wall.getX(), wall.getY(), wall.getZ()) || plan.solid(front.getX(), front.getY(), front.getZ())) return false;
		plan.set(wall, SpireBlocks.DART.defaultBlockState().setValue(SpireTrapBlock.FACING, looks));
		return true;
	}

	/** A random depth-first maze over the grid from the start room: every room reachable, one way to each. */
	private static void maze(Random random, int startCol, boolean[][] east, boolean[][] south) {
		boolean[][] seen = new boolean[CELLS][CELLS];
		Deque<int[]> stack = new ArrayDeque<>();
		stack.push(new int[] {startCol, 0});
		seen[startCol][0] = true;
		while (!stack.isEmpty()) {
			int[] at = stack.peek();
			List<int[]> next = new ArrayList<>();
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				int col = at[0] + direction.getStepX();
				int row = at[1] + direction.getStepZ();
				if (col >= 0 && row >= 0 && col < CELLS && row < CELLS && !seen[col][row]) next.add(new int[] {col, row});
			}
			if (next.isEmpty()) {
				stack.pop();
				continue;
			}
			int[] to = next.get(random.nextInt(next.size()));
			link(at, to, east, south);
			seen[to[0]][to[1]] = true;
			stack.push(to);
		}
	}

	private static void loops(Random random, boolean[][] east, boolean[][] south, int count) {
		for (int i = 0, tries = 0; i < count && tries < 32; tries++) {
			int col = random.nextInt(CELLS);
			int row = random.nextInt(CELLS);
			if (random.nextBoolean()) {
				if (col < CELLS - 1 && !east[col][row]) {
					east[col][row] = true;
					i++;
				}
			} else if (row < CELLS - 1 && !south[col][row]) {
				south[col][row] = true;
				i++;
			}
		}
	}

	private static void link(int[] a, int[] b, boolean[][] east, boolean[][] south) {
		if (a[1] == b[1]) east[Math.min(a[0], b[0])][a[1]] = true;
		else south[a[0]][Math.min(a[1], b[1])] = true;
	}

	/** The rooms on the shortest way from the start room to the exit room. */
	private static List<int[]> path(int startCol, int endCol, boolean[][] east, boolean[][] south) {
		int[][] from = new int[CELLS * CELLS][];
		boolean[] seen = new boolean[CELLS * CELLS];
		Deque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {startCol, 0});
		seen[startCol] = true;
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			if (at[0] == endCol && at[1] == CELLS - 1) break;
			for (int[] to : neighbours(at, east, south)) {
				int key = to[1] * CELLS + to[0];
				if (seen[key]) continue;
				seen[key] = true;
				from[key] = at;
				queue.add(to);
			}
		}
		List<int[]> path = new ArrayList<>();
		int[] at = {endCol, CELLS - 1};
		while (at != null) {
			path.add(0, at);
			at = from[at[1] * CELLS + at[0]];
		}
		return path;
	}

	private static List<int[]> neighbours(int[] at, boolean[][] east, boolean[][] south) {
		List<int[]> out = new ArrayList<>();
		int col = at[0];
		int row = at[1];
		if (col < CELLS - 1 && east[col][row]) out.add(new int[] {col + 1, row});
		if (col > 0 && east[col - 1][row]) out.add(new int[] {col - 1, row});
		if (row < CELLS - 1 && south[col][row]) out.add(new int[] {col, row + 1});
		if (row > 0 && south[col][row - 1]) out.add(new int[] {col, row - 1});
		return out;
	}

	// ---- the rooms ---------------------------------------------------------------------------

	private static void room(FloorPlan plan, Theme theme, Random random, int col, int row, int height, Role role) {
		int x0 = col * CELL;
		int z0 = row * CELL;
		int top = FLOOR + height + 1;
		// each room its own pair of the theme's wall stones (upper wall, dado), so its rooms are not all one room
		List<Block> walls = theme.wall();
		int pair = random.nextInt(walls.size());
		BlockState upper = walls.get(pair).defaultBlockState();
		BlockState lower = walls.get((pair + 1) % walls.size()).defaultBlockState();
		for (int x = 0; x < CELL; x++) {
			for (int z = 0; z < CELL; z++) {
				boolean edge = x == 0 || z == 0 || x == CELL - 1 || z == CELL - 1;
				plan.set(x0 + x, FLOOR - 1, z0 + z, theme.wall(random));
				plan.set(x0 + x, FLOOR, z0 + z, edge ? theme.wall(random) : floorTile(theme, random, x, z));
				for (int y = FLOOR + 1; y < top; y++) {
					if (!edge) continue;
					// a belt of trim along the foot and the middle of the walls
					boolean belt = y == FLOOR + 1 || y == FLOOR + 1 + height / 2;
					// pilasters break the long walls into bays (the sconces hang on them)
					int along = x == 0 || x == CELL - 1 ? z : x;
					boolean pilaster = along == 3 || along == CELL - 4;
					// a dado of the theme's second wall stone under the middle belt, its main stone above: two tones, in
					// courses, never single odd blocks dotted about (they read as noise)
					boolean dado = y < FLOOR + 1 + height / 2;
					BlockState face = dado ? lower : upper;
					plan.set(x0 + x, y, z0 + z, belt ? theme.trim().defaultBlockState() : pilaster ? theme.pillar().defaultBlockState() : face);
				}
				plan.set(x0 + x, top, z0 + z, theme.ceiling().defaultBlockState());
			}
		}
		int shape = random.nextInt(10);
		boolean round = (role == Role.HALL || role == Role.ARENA || role == Role.SIDE) && shape < 3;
		boolean sunken = role == Role.ARENA && shape >= 6;
		boolean gallery = (role == Role.HALL || role == Role.EXIT) && height >= 9 && shape >= 5;
		if (round) round(plan, theme, x0, z0, top);
		if (height >= 8 && role != Role.POOL && !round) pillars(plan, theme, x0, z0, top, role == Role.ARENA);
		if (role == Role.POOL) pool(plan, theme, x0, z0);
		if (sunken) sunken(plan, theme, random, x0, z0);
		if (gallery) gallery(plan, theme, x0, z0);
		if (role == Role.HALL || role == Role.START || role == Role.EXIT) runner(plan, theme, x0, z0);
		props(plan, theme, random, x0, z0, role);
		hanging(plan, theme, random, x0, z0, top);
		if (!round) fixtures(plan, random, x0, z0, height, role);
	}

	/**
	 * What the Builders left in a room: sconces along the walls, their tapestries over the belt, runes set in the floor
	 * and braziers in the rooms that matter (never in a doorway's lane, never over what stands there already).
	 */
	private static void fixtures(FloorPlan plan, Random random, int x0, int z0, int height, Role role) {
		int y = FLOOR + 3;
		for (int along : new int[] {3, 12}) {
			wallFixture(plan, x0 + along, y, z0 + 1, Direction.SOUTH, SpireBlocks.SCONCE);
			wallFixture(plan, x0 + along, y, z0 + CELL - 2, Direction.NORTH, SpireBlocks.SCONCE);
			wallFixture(plan, x0 + 1, y, z0 + along, Direction.EAST, SpireBlocks.SCONCE);
			wallFixture(plan, x0 + CELL - 2, y, z0 + along, Direction.WEST, SpireBlocks.SCONCE);
		}
		boolean grand = role == Role.START || role == Role.EXIT || role == Role.ARENA || role == Role.HALL;
		int hang = FLOOR + 2 + height / 2;
		if (grand && hang < FLOOR + height) {
			for (int along : new int[] {4, 11}) {
				if (random.nextBoolean()) wallFixture(plan, x0 + along, hang, z0 + 1, Direction.SOUTH, SpireBlocks.TAPESTRY);
				if (random.nextBoolean()) wallFixture(plan, x0 + along, hang, z0 + CELL - 2, Direction.NORTH, SpireBlocks.TAPESTRY);
			}
		}
		if (grand && role != Role.HALL) {
			for (int[] spot : new int[][] {{5, 5}, {10, 5}, {5, 10}, {10, 10}}) {
				int x = x0 + spot[0];
				int z = z0 + spot[1];
				if (plan.solid(x, FLOOR, z) && plan.get(x, FLOOR + 1, z) == null) plan.set(x, FLOOR, z, SpireBlocks.RUNE.defaultBlockState());
			}
		}
		if (role == Role.ARENA || role == Role.EXIT) {
			for (int[] spot : new int[][] {{3, 3}, {12, 3}, {3, 12}, {12, 12}}) {
				int x = x0 + spot[0];
				int z = z0 + spot[1];
				if (plan.solid(x, FLOOR, z) && plan.get(x, FLOOR + 1, z) == null && plan.get(x, FLOOR + 2, z) == null) {
					plan.set(x, FLOOR + 1, z, SpireBlocks.BRAZIER.defaultBlockState());
				}
			}
		}
	}

	private static void wallFixture(FloorPlan plan, int x, int y, int z, Direction facing, SpireFixtureBlock block) {
		BlockPos wall = new BlockPos(x, y, z).relative(facing.getOpposite());
		if (plan.get(x, y, z) != null || !plan.solid(wall.getX(), wall.getY(), wall.getZ())) return;
		plan.set(x, y, z, block.defaultBlockState().setValue(SpireFixtureBlock.FACING, facing));
	}

	/** The middle of a room's floor gets a pattern of the theme's second tile: a ring, a cross or a chequer. */
	private static BlockState floorTile(Theme theme, Random random, int x, int z) {
		int dx = Math.abs(x * 2 - (CELL - 1));
		int dz = Math.abs(z * 2 - (CELL - 1));
		boolean pattern = theme.floor().size() > 1 && ((dx + dz) / 2 == 5 || ((dx <= 1 || dz <= 1) && dx + dz <= 8));
		return pattern ? theme.floor().get(1).defaultBlockState() : theme.floor(random);
	}

	private static void pillars(FloorPlan plan, Theme theme, int x0, int z0, int top, boolean open) {
		int near = open ? 2 : 4;
		int far = CELL - 1 - near;
		for (int[] spot : new int[][] {{near, near}, {far, near}, {near, far}, {far, far}}) {
			for (int y = FLOOR + 1; y < top; y++) plan.set(x0 + spot[0], y, z0 + spot[1], theme.pillar().defaultBlockState());
		}
	}

	/** A basin in the middle: three deep, a rim of floor round it, a fifth of the room at most. */
	private static void pool(FloorPlan plan, Theme theme, int x0, int z0) {
		for (int x = 5; x <= 10; x++) {
			for (int z = 5; z <= 10; z++) {
				plan.set(x0 + x, FLOOR - 4, z0 + z, theme.trim().defaultBlockState());
				for (int y = FLOOR - 3; y <= FLOOR; y++) {
					boolean wall = x == 5 || z == 5 || x == 10 || z == 10;
					plan.set(x0 + x, y, z0 + z, wall && y < FLOOR ? theme.trim().defaultBlockState() : wall ? theme.trim().defaultBlockState()
							: Blocks.WATER.defaultBlockState());
				}
			}
		}
		// a light at the bottom
		plan.set(x0 + 7, FLOOR - 4, z0 + 7, Blocks.SEA_LANTERN.defaultBlockState());
		plan.set(x0 + 8, FLOOR - 4, z0 + 8, Blocks.SEA_LANTERN.defaultBlockState());
	}

	/** A round hall: the corners walled in, so the room reads as a rotunda; the doorways at the sides' middles stay. */
	private static void round(FloorPlan plan, Theme theme, int x0, int z0, int top) {
		for (int x = 1; x < CELL - 1; x++) {
			for (int z = 1; z < CELL - 1; z++) {
				double d = Math.sqrt((x - 7.5) * (x - 7.5) + (z - 7.5) * (z - 7.5));
				if (d <= 6.9) continue;
				for (int y = FLOOR + 1; y < top; y++) plan.set(x0 + x, y, z0 + z, d < 7.6 && y == FLOOR + 3 && (x + z) % 4 == 0 ? theme.light().defaultBlockState() : theme.wall().get(0).defaultBlockState());
			}
		}
	}

	/** A fighting pit: the middle a step down, its floor the theme's pattern, steps at the middle of each side. */
	private static void sunken(FloorPlan plan, Theme theme, Random random, int x0, int z0) {
		for (int x = 4; x <= 11; x++) {
			for (int z = 4; z <= 11; z++) {
				plan.set(x0 + x, FLOOR - 2, z0 + z, theme.wall(random));
				plan.set(x0 + x, FLOOR - 1, z0 + z, floorTile(theme, random, x, z));
				boolean edge = x == 4 || z == 4 || x == 11 || z == 11;
				plan.set(x0 + x, FLOOR, z0 + z, edge && (x == 7 || x == 8 || z == 7 || z == 8) ? stair(theme, x, z) : null);
			}
		}
	}

	private static @org.jspecify.annotations.Nullable BlockState stair(Theme theme, int x, int z) {
		Block stairs = family(theme, net.minecraft.world.level.block.StairBlock.class, "_stairs");
		if (stairs == null) return theme.trim().defaultBlockState();
		Direction facing = x == 4 ? Direction.EAST : x == 11 ? Direction.WEST : z == 4 ? Direction.SOUTH : Direction.NORTH;
		return stairs.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, facing);
	}

	/** A gallery five blocks up round a tall hall, railed, its floor the theme's trim. */
	private static void gallery(FloorPlan plan, Theme theme, int x0, int z0) {
		int y = FLOOR + 5;
		for (int x = 1; x < CELL - 1; x++) {
			for (int z = 1; z < CELL - 1; z++) {
				boolean ring = x <= 2 || z <= 2 || x >= CELL - 3 || z >= CELL - 3;
				if (!ring || plan.get(x0 + x, y, z0 + z) != null) continue;
				plan.set(x0 + x, y, z0 + z, theme.trim().defaultBlockState());
			}
		}
		for (int x = 3; x < CELL - 3; x++) {
			for (int z : new int[] {2, CELL - 3}) plan.set(x0 + x, y + 1, z0 + z, railing(theme));
		}
		for (int z = 3; z < CELL - 3; z++) {
			for (int x : new int[] {2, CELL - 3}) plan.set(x0 + x, y + 1, z0 + z, railing(theme));
		}
	}

	private static BlockState railing(Theme theme) {
		Block wall = family(theme, net.minecraft.world.level.block.WallBlock.class, "_wall", "_fence");
		return wall != null ? wall.defaultBlockState() : Blocks.CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Y);
	}

	/**
	 * The theme's own block of a shape (stairs, wall, fence): from its trim, walls, floor or pillar, whichever has one,
	 * so a library's steps are dark oak stairs and its rail a dark oak fence rather than whatever the trim lacks.
	 */
	private static @org.jspecify.annotations.Nullable Block family(Theme theme, Class<? extends Block> kind, String... suffixes) {
		List<Block> sources = new ArrayList<>();
		sources.add(theme.trim());
		sources.addAll(theme.wall());
		sources.addAll(theme.floor());
		sources.add(theme.pillar());
		for (String suffix : suffixes) {
			for (Block source : sources) {
				net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(source);
				String path = id.getPath();
				for (String base : new String[] {path, path.replace("_bricks", "_brick"), path.replace("_planks", ""), path.replace("_tiles", "_tile"),
						path.replace("stripped_", "").replace("_log", "").replace("_wood", "")}) {
					Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id.withPath(base + suffix));
					boolean fits = suffix.equals("_fence") ? block instanceof net.minecraft.world.level.block.FenceBlock : kind.isInstance(block);
					if (fits) return block;
				}
			}
		}
		return null;
	}

	/** A carpet runner across the room on its two middle lanes, from doorway to doorway. */
	private static void runner(FloorPlan plan, Theme theme, int x0, int z0) {
		for (int i = 1; i < CELL - 1; i++) {
			for (int lane = 7; lane <= 8; lane++) {
				if (plan.get(x0 + i, FLOOR + 1, z0 + lane) == null && plan.solid(x0 + i, FLOOR, z0 + lane)) plan.set(x0 + i, FLOOR + 1, z0 + lane, theme.runner().defaultBlockState());
				if (plan.get(x0 + lane, FLOOR + 1, z0 + i) == null && plan.solid(x0 + lane, FLOOR, z0 + i)) plan.set(x0 + lane, FLOOR + 1, z0 + i, theme.runner().defaultBlockState());
			}
		}
	}

	/** The theme's scenes along the walls (Props), never in a doorway's lane, fewer where a fight is. */
	private static void props(FloorPlan plan, Theme theme, Random random, int x0, int z0, Role role) {
		Props.place(plan, theme, random, x0, z0, role == Role.ARENA ? 1 : role == Role.SIDE ? 3 : 2);
	}

	/** Chains with a light from the ceiling over the open floor. */
	private static void hanging(FloorPlan plan, Theme theme, Random random, int x0, int z0, int top) {
		// the Builders' lamps on chains in a square over the floor's middle, one of the theme's hanging things between
		int[][] spots = {{5, 5}, {10, 5}, {5, 10}, {10, 10}};
		for (int n = 0; n < spots.length; n++) {
			int x = x0 + spots[n][0];
			int z = z0 + spots[n][1];
			if (plan.get(x, top - 1, z) != null) continue;
			int length = 1 + random.nextInt(2);
			for (int y = top - 1; y > top - 1 - length; y--) plan.set(x, y, z, Blocks.CHAIN.defaultBlockState());
			plan.set(x, top - 1 - length, z, SpireBlocks.LAMP.defaultBlockState());
		}
		Block chain = theme.hanging().get(0);
		Block light = theme.hanging().get(theme.hanging().size() - 1);
		int x = x0 + 7 + random.nextInt(2);
		int z = z0 + 3 + random.nextInt(2) * 9;
		if (plan.get(x, top - 1, z) == null && random.nextBoolean()) {
			for (int y = top - 1; y > top - 3; y--) plan.set(x, y, z, chain.defaultBlockState());
			BlockState end = light.defaultBlockState();
			if (end.hasProperty(LanternBlock.HANGING)) end = end.setValue(LanternBlock.HANGING, true);
			plan.set(x, top - 3, z, end);
		}
	}

	/** A doorway through the two walls between rooms: three wide, four high, framed with trim. */
	private static void door(FloorPlan plan, Theme theme, int x, int z, boolean eastward, boolean leaf) {
		for (int across = -1; across <= 3; across++) {
			for (int through = 0; through < 2; through++) {
				int px = eastward ? x + through : x + across;
				int pz = eastward ? z + across : z + through;
				for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
					boolean frame = across == -1 || across == 3 || y == FLOOR + 5;
					plan.set(px, y, pz, frame ? theme.trim().defaultBlockState() : null);
					if (leaf && !frame && through == 0) {
						plan.set(px, y, pz, SpireBlocks.DOOR.defaultBlockState().setValue(SpireDoorBlock.FACING, eastward ? Direction.EAST : Direction.SOUTH)
								.setValue(SpireDoorBlock.ROW, y - FLOOR - 1).setValue(SpireDoorBlock.COL, across));
					}
				}
				plan.set(px, FLOOR, pz, theme.trim().defaultBlockState());
			}
		}
	}

	/**
	 * The way out, always open, against the start room's back wall; the climber stands in front of it. A portal stands a
	 * block inside the floor's edge, the wall behind it: set into the edge itself it had the void at its back, and
	 * whoever walked through before it took them dropped out of the floor.
	 */
	private static void start(FloorPlan plan, Theme theme, int col) {
		int x = col * CELL + 7;
		portal(plan, theme, x, 1, plan.exitPortal);
		plan.entrance = new BlockPos(x + 1, FLOOR + 1, 4);
		plan.facing = 0.0F;
		plan.note = new BlockPos(x + 4, FLOOR + 1, 6);
		plan.set(plan.note, null);
	}

	/** The onward portal on the exit room's far wall, the chest before it. */
	private static void exit(FloorPlan plan, Theme theme, int col) {
		int x = col * CELL + 7;
		int z = CELLS * CELL - 2;
		portal(plan, theme, x, z, plan.onwardPortal);
		plan.chest = new BlockPos(x + 1, FLOOR + 1, z - 4);
		plan.set(plan.chest, null);
	}

	private static void portal(FloorPlan plan, Theme theme, int x, int z, List<BlockPos> cells) {
		for (int dx = -1; dx <= 2; dx++) {
			for (int y = FLOOR; y <= FLOOR + 4; y++) {
				boolean inside = dx >= 0 && dx <= 1 && y >= FLOOR + 1 && y <= FLOOR + 3;
				BlockPos pos = new BlockPos(x + dx, y, z);
				if (inside) {
					cells.add(pos);
					plan.set(pos, null);
				} else {
					plan.set(pos, theme.trim().defaultBlockState());
				}
			}
		}
	}

	/** One or two in the pool's water, under its surface: the only place a floor has swimmers. */
	private static void swimmers(FloorPlan plan, Random random, int col, int row, int arena) {
		int count = 1 + random.nextInt(2);
		for (int i = 0; i < count; i++) {
			plan.swimmers.add(new FloorPlan.Spawn(new BlockPos(col * CELL + 7 + i, FLOOR - 2, row * CELL + 7 + i), random.nextLong(), arena));
		}
	}

	private static void guard(FloorPlan plan, Random random, int col, int row, int count, int arena) {
		int most = GUARDS + 3 * Math.min(3, Genome.round(plan.floor));
		for (int i = 0, tries = 0; i < count && tries < 40 && plan.guards.size() < most; tries++) {
			int x = col * CELL + 3 + random.nextInt(CELL - 6);
			int z = row * CELL + 3 + random.nextInt(CELL - 6);
			int y = plan.solid(x, FLOOR, z) ? FLOOR + 1 : plan.solid(x, FLOOR - 1, z) ? FLOOR : -1;
			if (y < 0 || !plan.empty(x, y, z) || !plan.empty(x, y + 1, z)) continue;
			plan.guards.add(new FloorPlan.Spawn(new BlockPos(x, y, z), random.nextLong(), arena));
			i++;
		}
	}

	// ---- checks ----------------------------------------------------------------------------

	/** Whether a climber gets from the entrance to the onward portal, the chest, the note and every guard on foot. */
	static boolean holds(FloorPlan plan) {
		Set<Long> reached = walk(plan);
		List<BlockPos> must = new ArrayList<>();
		must.add(plan.chest);
		if (plan.note != null) must.add(plan.note);
		must.add(plan.onwardPortal.get(0).relative(Direction.NORTH));
		for (FloorPlan.Spawn spawn : plan.guards) must.add(spawn.pos());
		for (BlockPos pos : must) {
			if (!near(reached, pos)) return false;
		}
		return true;
	}

	/**
	 * The cells a climber gets to from the entrance (steps of a block, drops of up to eight, ladders) but cannot get
	 * back to the entrance from.
	 */
	public static List<BlockPos> trapped(FloorPlan plan) {
		java.util.Map<BlockPos, List<BlockPos>> into = new java.util.HashMap<>();
		Set<BlockPos> reached = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		reached.add(plan.entrance);
		queue.add(plan.entrance);
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			for (BlockPos to : moves(plan, at)) {
				into.computeIfAbsent(to, key -> new ArrayList<>()).add(at);
				if (reached.add(to)) queue.add(to);
			}
		}
		Set<BlockPos> back = new HashSet<>();
		back.add(plan.entrance);
		queue.add(plan.entrance);
		while (!queue.isEmpty()) {
			for (BlockPos from : into.getOrDefault(queue.poll(), List.of())) if (back.add(from)) queue.add(from);
		}
		List<BlockPos> traps = new ArrayList<>();
		for (BlockPos cell : reached) if (!back.contains(cell)) traps.add(cell);
		return traps;
	}

	private static boolean ladder(FloorPlan plan, BlockPos pos) {
		BlockState state = plan.get(pos.getX(), pos.getY(), pos.getZ());
		return state != null && state.getBlock() instanceof net.minecraft.world.level.block.LadderBlock;
	}

	private static boolean free(FloorPlan plan, BlockPos pos) {
		return FloorPlan.inside(pos.getX(), pos.getY(), pos.getZ()) && (plan.empty(pos.getX(), pos.getY(), pos.getZ()) || ladder(plan, pos));
	}

	private static boolean stands(FloorPlan plan, BlockPos pos) {
		return free(plan, pos) && free(plan, pos.above()) && (ladder(plan, pos) || plan.get(pos.getX(), pos.getY() - 1, pos.getZ()) != null);
	}

	private static List<BlockPos> moves(FloorPlan plan, BlockPos at) {
		List<BlockPos> next = new ArrayList<>();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos side = at.relative(direction);
			if (free(plan, at.above(2)) && stands(plan, side.above())) next.add(side.above());
			if (stands(plan, side)) next.add(side);
			else if (free(plan, side) && free(plan, side.above())) {
				for (int drop = 1; drop <= 8; drop++) {
					if (stands(plan, side.below(drop))) {
						next.add(side.below(drop));
						break;
					}
					if (!free(plan, side.below(drop))) break;
				}
			}
		}
		if (ladder(plan, at) && stands(plan, at.above())) next.add(at.above());
		if (ladder(plan, at.below()) && stands(plan, at.below())) next.add(at.below());
		return next;
	}

	/** The cells a climber can stand in, walking from the entrance (no jumps over a block, no falls). */
	static Set<Long> walk(FloorPlan plan) {
		Set<Long> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(plan.entrance);
		seen.add(plan.entrance.asLong());
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos to = at.relative(direction).above(dy);
					if (!FloorPlan.inside(to.getX(), to.getY(), to.getZ())) continue;
					// a step up wants room over the head where it is
					if (dy > 0 && !plan.empty(at.getX(), at.getY() + 2, at.getZ())) continue;
					if (!standable(plan, to) || !seen.add(to.asLong())) continue;
					queue.add(to);
				}
			}
		}
		return seen;
	}

	/**
	 * The way on foot from {@code from} to the cell nearest {@code goal} it can reach (the bot's route, /vnap spire
	 * route): steps of a block up or down, the cells in order; empty when {@code from} is no cell to stand in.
	 */
	static List<BlockPos> route(FloorPlan plan, BlockPos from, BlockPos goal) {
		java.util.Map<Long, Long> came = new java.util.HashMap<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		BlockPos start = standable(plan, from) ? from : standable(plan, from.above()) ? from.above() : from.below();
		if (!FloorPlan.inside(start.getX(), start.getY(), start.getZ()) || !standable(plan, start)) return List.of();
		queue.add(start);
		came.put(start.asLong(), start.asLong());
		BlockPos best = start;
		double bestDistance = start.distSqr(goal);
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			double d = at.distSqr(goal);
			if (d < bestDistance) {
				best = at;
				bestDistance = d;
				if (d <= 2.0) break;
			}
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos to = at.relative(direction).above(dy);
					if (!FloorPlan.inside(to.getX(), to.getY(), to.getZ()) || came.containsKey(to.asLong())) continue;
					if (dy > 0 && !plan.empty(at.getX(), at.getY() + 2, at.getZ())) continue;
					if (!standable(plan, to)) continue;
					came.put(to.asLong(), at.asLong());
					queue.add(to);
				}
			}
		}
		List<BlockPos> way = new ArrayList<>();
		long at = best.asLong();
		while (at != start.asLong()) {
			way.add(0, BlockPos.of(at));
			Long back = came.get(at);
			if (back == null) break;
			at = back;
		}
		return way;
	}

	private static boolean standable(FloorPlan plan, BlockPos pos) {
		return plan.empty(pos.getX(), pos.getY(), pos.getZ()) && plan.empty(pos.getX(), pos.getY() + 1, pos.getZ())
				&& (plan.solid(pos.getX(), pos.getY() - 1, pos.getZ()) || plan.get(pos.getX(), pos.getY() - 1, pos.getZ()) != null);
	}

	private static boolean near(Set<Long> reached, BlockPos pos) {
		if (reached.contains(pos.asLong())) return true;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (reached.contains(pos.relative(direction).asLong())) return true;
		}
		return false;
	}
}
