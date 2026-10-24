package com.vnap.spire.outer;

import com.vnap.spire.FloorGenerator;
import com.vnap.spire.Genome;
import com.vnap.spire.SpireBlocks;
import com.vnap.spire.SpireGuards;
import com.vnap.spire.guard.SpireGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Sky Path: an archipelago in the void, without end every way, drawn round the players by the run's seed and
 * taken away where nobody is. The sky is cut into cells; each holds an island of its own shape (a blob, a ring, a
 * long one, a twin, a rock spire with a ledge, a square), its height from a slow swell over the cells, its ground from
 * the region it lies in (moss, snow, sand, amethyst, mud, honey). Every island is linked to the neighbour on its way
 * to the middle by stepping stones laid where a jump reaches (Gamasutra's "insane procedural platformers": the
 * generator knows the jump), one cell in three to another neighbour too: from the middle every island can be reached,
 * some more ways than one. Waystones stand every few cells; islands of guards wake as a player lands; some stones
 * crumble underfoot; three falls and the run is over.
 */
public final class Isles {
	/** A cell's side: one island each. */
	public static final int CELL = 12;
	/** Cells drawn round each player, and how far off they are kept. */
	static final int NEAR = 5;
	static final int KEEP = 8;
	/** A fall this far under the last waystone is a fall. */
	private static final int FALL = 40;
	/** Every so many cells along both ways stands a waystone. */
	static final int WAYSTONE_EVERY = 4;

	private Isles() {
	}

	/** What an island is for. */
	public enum Type {
		HOP, FIGHT, CRUMBLE, WAYSTONE, STONE
	}

	/** Its shape, drawn on its top. */
	public enum Form {
		BLOB, RING, LONG, TWIN, SPIRE, SQUARE
	}

	/** An island or a stepping stone: its top (x0..x1, z0..z1 at {@code top}), what it is, its shape. */
	public record Island(int x0, int z0, int x1, int z1, int top, Type type, Form form) {
		/** The air between two tops, as a jump crosses it (along X and Z, the shortest way). */
		public double gap(Island next) {
			int gx = Math.max(0, Math.max(next.x0 - x1, x0 - next.x1) - 1);
			int gz = Math.max(0, Math.max(next.z0 - z1, z0 - next.z1) - 1);
			return Math.sqrt(gx * gx + gz * gz);
		}

		/** Whether a sprinting jump from this one lands on the next: three across on the level or down, two up a block. */
		public boolean reaches(Island next) {
			int rise = next.top - top;
			if (rise > 1 || rise < -6) return false;
			return gap(next) <= (rise == 1 ? 2.0 : 3.0) + 1.0E-6;
		}

		/** Whether a top cell is solid ground of it (a ring's hole, a twin's gap are not). */
		public boolean solid(int x, int z) {
			if (x < x0 || x > x1 || z < z0 || z > z1) return false;
			double cx = (x0 + x1) / 2.0;
			double cz = (z0 + z1) / 2.0;
			double nx = (x - cx) / ((x1 - x0) / 2.0 + 0.5);
			double nz = (z - cz) / ((z1 - z0) / 2.0 + 0.5);
			double d = nx * nx + nz * nz;
			return switch (form) {
				case SQUARE, LONG -> true;
				case BLOB -> d <= 1.15;
				case RING -> d <= 1.15 && d >= 0.3;
				case TWIN -> d <= 1.15 && Math.abs(x - cx) >= 1.0;
				case SPIRE -> d <= 1.2;
			};
		}

		/** Its solid top cell nearest a point: where a jump to or from there leaves or lands. */
		public BlockPos edge(int tx, int tz) {
			BlockPos best = new BlockPos((x0 + x1) / 2, top, (z0 + z1) / 2);
			double bestD = Double.MAX_VALUE;
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					if (!solid(x, z)) continue;
					double d = (x - tx) * (double) (x - tx) + (z - tz) * (double) (z - tz);
					if (d < bestD) {
						bestD = d;
						best = new BlockPos(x, top, z);
					}
				}
			}
			return best;
		}

		/** Its one solid cell nearest a point, as an island of one block (a jump's real ends). */
		public Island at(int tx, int tz) {
			BlockPos e = edge(tx, tz);
			return new Island(e.getX(), e.getZ(), e.getX(), e.getZ(), top, type, Form.SQUARE);
		}
	}

	/** Everything a cell holds: its island and the stones from it to its neighbours on the way in. */
	public record Cell(int cx, int cz, Island island, List<Island> stones) {
	}

	/** The swell the islands ride: slow over the cells. */
	static int height(long seed, int cx, int cz) {
		return (int) Math.round(7.0 * noise(seed, cx / 7.0, cz / 7.0) + 2.0 * noise(seed + 1, cx / 3.0, cz / 3.0));
	}

	private static double noise(long seed, double x, double z) {
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = x - x0;
		double fz = z - z0;
		double sx = fx * fx * (3 - 2 * fx);
		double sz = fz * fz * (3 - 2 * fz);
		double a = corner(seed, x0, z0);
		double b = corner(seed, x0 + 1, z0);
		double c = corner(seed, x0, z0 + 1);
		double d = corner(seed, x0 + 1, z0 + 1);
		double top = a + (b - a) * sx;
		return top + ((c + (d - c) * sx) - top) * sz;
	}

	private static double corner(long seed, int x, int z) {
		return Math.floorMod(FloorGenerator.mix(seed, x * 73856093L ^ z * 19349663L), 2001L) / 1000.0 - 1.0;
	}

	/** The island of a cell (in the run's own coordinates): its kind, shape, size, place in the cell, height. */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "seeded: the same seed draws the same island")
	public static Island island(long seed, int cx, int cz, int baseY) {
		Random random = new Random(FloorGenerator.mix(seed, cx * 341873128712L + cz * 132897987541L));
		boolean waystone = Math.floorMod(cx, WAYSTONE_EVERY) == 0 && Math.floorMod(cz, WAYSTONE_EVERY) == 0;
		Type type = waystone ? Type.WAYSTONE : random.nextInt(9) == 0 ? Type.FIGHT : Type.HOP;
		Form[] forms = Form.values();
		Form form = type == Type.FIGHT ? Form.BLOB : type == Type.WAYSTONE ? Form.SQUARE : forms[random.nextInt(forms.length)];
		int rx;
		int rz;
		switch (form) {
			case LONG -> {
				boolean alongX = random.nextBoolean();
				rx = alongX ? 3 + random.nextInt(2) : 1;
				rz = alongX ? 1 : 3 + random.nextInt(2);
			}
			case SPIRE -> {
				rx = 1;
				rz = 1;
			}
			case RING -> {
				rx = 3;
				rz = 3;
			}
			default -> {
				rx = type == Type.FIGHT ? 4 : 1 + random.nextInt(3);
				rz = type == Type.FIGHT ? 4 : 1 + random.nextInt(3);
			}
		}
		int room = CELL / 2 - 1;
		int mx = cx * CELL + CELL / 2 + (random.nextInt(3) - 1) * Math.max(0, room - rx - 1);
		int mz = cz * CELL + CELL / 2 + (random.nextInt(3) - 1) * Math.max(0, room - rz - 1);
		int top = baseY + height(seed, cx, cz) + (form == Form.SPIRE ? 2 : 0);
		return new Island(mx - rx, mz - rz, mx + rx, mz + rz, top, type, form);
	}

	/** The cell a cell is linked to on its way to the middle (none for the middle itself). */
	public static int[] toward(int cx, int cz) {
		if (cx == 0 && cz == 0) return new int[0];
		if (Math.abs(cx) >= Math.abs(cz)) return new int[] {cx - Integer.signum(cx), cz};
		return new int[] {cx, cz - Integer.signum(cz)};
	}

	/** Everything in a cell: its island, the stones to its way-in neighbour, and one cell in three to another (a loop). */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "seeded: the same seed draws the same cell")
	public static Cell cell(long seed, int cx, int cz, int baseY, int stage) {
		Island island = island(seed, cx, cz, baseY);
		List<Island> stones = new ArrayList<>();
		int[] in = toward(cx, cz);
		Random random = new Random(FloorGenerator.mix(seed, cx * 9719L + cz * 7177L + 5));
		if (in.length == 2) stones.addAll(stones(island, island(seed, in[0], in[1], baseY), random, stage));
		if (random.nextInt(3) == 0) {
			boolean alongX = random.nextBoolean();
			stones.addAll(stones(island, island(seed, cx + (alongX ? 1 : 0), cz + (alongX ? 0 : 1), baseY), random, stage));
		}
		return new Cell(cx, cz, island, stones);
	}

	/**
	 * Stepping stones from one island to another: a stone every few blocks along the line between their nearest
	 * edges, each in reach of the last both ways — a block up or down at most from one to the next, gaps of two or less.
	 * Every other one of some ways crumbles underfoot, more ways so further on.
	 */
	public static List<Island> stones(Island from, Island to, Random random, int stage) {
		List<Island> out = new ArrayList<>();
		BlockPos a = from.edge((to.x0() + to.x1()) / 2, (to.z0() + to.z1()) / 2);
		BlockPos b = to.edge(a.getX(), a.getZ());
		Island start = from.at(b.getX(), b.getZ());
		Island end = to.at(a.getX(), a.getZ());
		if (start.reaches(end) && end.reaches(start)) return out;
		double dx = b.getX() - a.getX();
		double dz = b.getZ() - a.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		int rise = b.getY() - a.getY();
		boolean crumbling = random.nextInt(10) < Math.min(4, 1 + stage / 6);
		// a straight line of stones when it is long enough for the climb; else bowed out to one side until it is
		for (int bow = 0; bow <= 8; bow++) {
			List<Island> line = line(a, b, start, end, length, rise, bow, crumbling);
			if (line != null) return line;
		}
		return out;
	}

	/** Stones along the line from {@code a} to {@code b} bowed {@code bow} blocks aside at its middle, or null when a jump of it is out of reach. */
	private static @org.jspecify.annotations.Nullable List<Island> line(BlockPos a, BlockPos b, Island start, Island end, double length, int rise, int bow,
			boolean crumbling) {
		List<Island> out = new ArrayList<>();
		double dx = b.getX() - a.getX();
		double dz = b.getZ() - a.getZ();
		double px = length < 1.0E-6 ? 1.0 : -dz / length;
		double pz = length < 1.0E-6 ? 0.0 : dx / length;
		int hops = Math.max((int) Math.ceil((length + 2.0 * bow) / 2.4), Math.abs(rise) + 1);
		Island last = start;
		for (int i = 1; i < hops; i++) {
			double t = (double) i / hops;
			double side = bow * 4.0 * t * (1.0 - t);
			int x = (int) Math.round(a.getX() + dx * t + px * side);
			int z = (int) Math.round(a.getZ() + dz * t + pz * side);
			int y = a.getY() + (int) Math.round(rise * t);
			Island stone = new Island(x, z, x, z, y, crumbling && i % 2 == 0 ? Type.CRUMBLE : Type.STONE, Form.SQUARE);
			if ((x == last.x0() && z == last.z0()) || !last.reaches(stone) || !stone.reaches(last)) return null;
			out.add(stone);
			last = stone;
		}
		return last.reaches(end) && end.reaches(last) ? out : null;
	}

	static void build(Outer.Run run) {
		Island middle = island(run.seed, 0, 0, run.origin.getY());
		BlockPos centre = middle.edge((middle.x0() + middle.x1()) / 2, (middle.z0() + middle.z1()) / 2);
		run.checkpoint = centre.offset(run.origin.getX(), 0, run.origin.getZ());
		near(run, run.checkpoint.getX(), run.checkpoint.getZ());
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	/** Draws the cells round a point that are not drawn yet. */
	private static void near(Outer.Run run, double x, double z) {
		int px = Math.floorDiv((int) Math.floor(x - run.origin.getX()), CELL);
		int pz = Math.floorDiv((int) Math.floor(z - run.origin.getZ()), CELL);
		for (int cx = px - NEAR; cx <= px + NEAR; cx++) {
			for (int cz = pz - NEAR; cz <= pz + NEAR; cz++) {
				long key = key(cx, cz);
				if (run.cells.containsKey(key)) continue;
				Cell cell = cell(run.seed, cx, cz, run.origin.getY(), run.stage);
				run.cells.put(key, cell);
				draw(run, cell.island());
				for (Island stone : cell.stones()) draw(run, stone);
			}
		}
	}

	/** An island at its place in the world (cells are counted from the run's middle). */
	private static Island shift(Outer.Run run, Island local) {
		int x = run.origin.getX();
		int z = run.origin.getZ();
		return new Island(local.x0() + x, local.z0() + z, local.x1() + x, local.z1() + z, local.top(), local.type(), local.form());
	}

	private static void draw(Outer.Run run, Island local) {
		Island island = shift(run, local);
		ServerLevel level = run.level;
		Block[] ground = GROUNDS[region(run.seed, island)];
		if (island.type() == Type.STONE || island.type() == Type.CRUMBLE) {
			BlockPos at = new BlockPos(island.x0(), island.top(), island.z0());
			level.setBlock(at, island.type() == Type.CRUMBLE ? OuterBlocks.CRUMBLE.defaultBlockState() : ground[1].defaultBlockState(), 2);
			if (island.type() == Type.STONE) level.setBlock(at.below(), ground[2].defaultBlockState(), 2);
			return;
		}
		int width = Math.max(island.x1() - island.x0(), island.z1() - island.z0()) + 1;
		int depth = island.form() == Form.SPIRE ? 9 + width : 2 + width / 2;
		double cx = (island.x0() + island.x1()) / 2.0;
		double cz = (island.z0() + island.z1()) / 2.0;
		double rx = (island.x1() - island.x0()) / 2.0 + 0.5;
		double rz = (island.z1() - island.z0()) / 2.0 + 0.5;
		for (int x = island.x0(); x <= island.x1(); x++) {
			for (int z = island.z0(); z <= island.z1(); z++) {
				if (!island.solid(x, z)) continue;
				level.setBlock(new BlockPos(x, island.top(), z), ground[0].defaultBlockState(), 2);
				for (int d = 1; d <= depth; d++) {
					// the underside rounds in as it goes down, a little ragged (a spire goes nearly straight down, a rock)
					double shrink = island.form() == Form.SPIRE ? 0.25 * d / depth : (double) d / (depth + 1);
					double nx = (x - cx) / (rx * (1.0 - shrink) + 0.3);
					double nz = (z - cz) / (rz * (1.0 - shrink) + 0.3);
					if (nx * nx + nz * nz > 1.0 + 0.3 * Math.floorMod(FloorGenerator.mix(x, z * 31L + d), 3L) / 3.0) break;
					level.setBlock(new BlockPos(x, island.top() - d, z), (d == 1 ? ground[1] : ground[2]).defaultBlockState(), 2);
				}
			}
		}
		decorate(run, island, depth);
		if (island.type() == Type.WAYSTONE) {
			BlockPos stone = new BlockPos((int) Math.floor(cx), island.top() + 1, (int) Math.floor(cz));
			level.setBlock(stone, SpireBlocks.RUNE.defaultBlockState(), 2);
			level.setBlock(stone.above(), SpireBlocks.BRAZIER.defaultBlockState(), 2);
		}
	}

	/** The ground of the region an island lies in (regions of several cells, from a slow noise of their own). */
	private static int region(long seed, Island island) {
		double n = noise(seed ^ 0x5EEDL, island.x0() / 60.0, island.z0() / 60.0);
		return Math.floorMod((int) Math.floor((n + 1.0) * 3.0), GROUNDS.length);
	}

	/** What grows on each ground (one in five cells of a top). */
	private static final Block[][] PLANTS = {
			{Blocks.MOSS_CARPET, Blocks.AZALEA, Blocks.FERN, Blocks.POPPY, Blocks.CORNFLOWER},
			{Blocks.SNOW, Blocks.SNOW, Blocks.SWEET_BERRY_BUSH, Blocks.SNOW, Blocks.SPRUCE_SAPLING},
			{Blocks.DEAD_BUSH, Blocks.DEAD_BUSH, Blocks.SHORT_GRASS, Blocks.DEAD_BUSH, Blocks.DEAD_BUSH},
			{Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER, Blocks.SMALL_AMETHYST_BUD},
			{Blocks.MOSS_CARPET, Blocks.MANGROVE_PROPAGULE, Blocks.FERN, Blocks.BLUE_ORCHID, Blocks.MOSS_CARPET},
			{Blocks.SUNFLOWER, Blocks.DANDELION, Blocks.OXEYE_DAISY, Blocks.SHORT_GRASS, Blocks.DANDELION}};

	/** The ground of a region: its top, the earth under it, the stone deeper. */
	private static final Block[][] GROUNDS = {
			{Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT, Blocks.STONE},
			{Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.CALCITE},
			{Blocks.SAND, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE},
			{Blocks.AMETHYST_BLOCK, Blocks.CALCITE, Blocks.SMOOTH_BASALT},
			{Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.MUD},
			{Blocks.HONEYCOMB_BLOCK, Blocks.HONEYCOMB_BLOCK, Blocks.TUFF}};

	/**
	 * Its dressing: plants of its ground on its top, roots or icicles under its rim, a light set in the heart of its
	 * underside (the islands round are seen from afar in the dark), a lantern on a post on the big ones.
	 */
	private static void decorate(Outer.Run run, Island island, int depth) {
		ServerLevel level = run.level;
		int kind = region(run.seed, island);
		Random random = new Random(FloorGenerator.mix(run.seed, island.x0() * 31L + island.z0()));
		int cx = (island.x0() + island.x1()) / 2;
		int cz = (island.z0() + island.z1()) / 2;
		for (int x = island.x0(); x <= island.x1(); x++) {
			for (int z = island.z0(); z <= island.z1(); z++) {
				if (!island.solid(x, z)) continue;
				BlockPos above = new BlockPos(x, island.top() + 1, z);
				if ((x != cx || z != cz) && random.nextInt(5) == 0 && island.type() == Type.HOP) {
					BlockState plant = PLANTS[kind][random.nextInt(PLANTS[kind].length)].defaultBlockState();
					if (plant.canSurvive(level, above)) level.setBlock(above, plant, 2);
				}
				boolean rim = !island.solid(x - 1, z) || !island.solid(x + 1, z) || !island.solid(x, z - 1) || !island.solid(x, z + 1);
				BlockPos under = new BlockPos(x, island.top() - 1, z);
				if (rim && random.nextInt(3) == 0 && level.getBlockState(under).isAir()) {
					level.setBlock(under, kind == 1 ? Blocks.POINTED_DRIPSTONE.defaultBlockState()
							.setValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION, net.minecraft.core.Direction.DOWN)
							: Blocks.HANGING_ROOTS.defaultBlockState(), 2);
				}
			}
		}
		if (island.solid(cx, cz)) level.setBlock(new BlockPos(cx, island.top() - Math.max(1, depth / 2), cz), Blocks.SHROOMLIGHT.defaultBlockState(), 2);
		if (island.x1() - island.x0() >= 4 && island.solid(island.x0() + 1, cz)) {
			BlockPos post = new BlockPos(island.x0() + 1, island.top() + 1, cz);
			level.setBlock(post, Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
			level.setBlock(post.above(), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
			level.setBlock(post.above(2), Blocks.LANTERN.defaultBlockState(), 2);
		}
	}

	static void tick(Outer.Run run, List<ServerPlayer> on) {
		ServerLevel level = run.level;
		for (ServerPlayer player : on) {
			if (player.getY() < run.checkpoint.getY() - FALL) {
				fell(run, player);
				continue;
			}
			if (level.getGameTime() % 10 == 0) near(run, player.getX(), player.getZ());
			int cx = Math.floorDiv((int) Math.floor(player.getX() - run.origin.getX()), CELL);
			int cz = Math.floorDiv((int) Math.floor(player.getZ() - run.origin.getZ()), CELL);
			Cell cell = run.cells.get(key(cx, cz));
			if (cell == null || !player.onGround()) continue;
			Island island = shift(run, cell.island());
			if (player.getY() < island.top() + 0.5 || player.getY() > island.top() + 2 || !island.solid(player.getBlockX(), player.getBlockZ())) continue;
			if (island.type() == Type.WAYSTONE) waystone(run, on, island, cx, cz);
			if (island.type() == Type.FIGHT) fight(run, island, cx, cz);
		}
		if (level.getGameTime() % 100 == 0) forget(run, on);
		// the flyers of the sky come at a player who lingers
		if (level.getGameTime() % 400 == 0 && run.livingGuards() < 3 + run.party()) {
			ServerPlayer target = on.get(level.getRandom().nextInt(on.size()));
			int floor = run.loadout.floor() + run.stage;
			String theme = Genome.THEMES.get(Math.floorMod(run.stage, Genome.THEMES.size()));
			Genome genome = Genome.decode(level.getRandom().nextLong(), floor, theme, false, false);
			if (genome.movement() == Genome.Movement.FLY) spawn(run, genome, theme, target.blockPosition().offset(6, 4, 0));
		}
		if (level.getGameTime() % 20 == 0) Outer.tell(run, on, run.lives, 3, false, false);
	}

	/** A waystone not stood on before: the run has come further, and a fall comes back here. */
	private static void waystone(Outer.Run run, List<ServerPlayer> on, Island island, int cx, int cz) {
		if (!run.reached.add(key(cx, cz))) return;
		run.checkpoint = new BlockPos((island.x0() + island.x1()) / 2, island.top(), (island.z0() + island.z1()) / 2);
		if (cx == 0 && cz == 0) return;
		run.stage++;
		run.level.playSound(null, run.checkpoint, com.vnap.sound.SpireSounds.PUZZLE_SOLVED, SoundSource.PLAYERS, 1.2F, 1.2F);
		if (run.stage % 5 == 0) Outer.reward(run, on, run.loadout.floor() + run.stage, run.stage % 10 == 0 ? 1 + run.stage / 30 : 0);
		else for (ServerPlayer player : on) Outer.record(player, run);
		for (ServerPlayer player : on) com.vnap.Mc.say(player, Component.translatable("outer.villager_life.isles.waystone", run.stage), true);
	}

	/** An island of guards wakes when a player lands on it: walkers on it, a flyer over it. */
	private static void fight(Outer.Run run, Island island, int cx, int cz) {
		if (!run.reached.add(key(cx, cz))) return;
		Random random = new Random(FloorGenerator.mix(run.seed, key(cx, cz)));
		int floor = run.loadout.floor() + run.stage;
		String theme = Genome.THEMES.get(Math.floorMod(region(run.seed, island) * 2 + run.stage / 5, Genome.THEMES.size()));
		int count = Math.min(6, 2 + run.stage / 3) + run.party() - 1;
		for (int i = 0; i < count; i++) {
			Genome genome = Genome.decode(random.nextLong(), floor, theme, false, false);
			BlockPos at = new BlockPos(island.x0() + 1 + random.nextInt(Math.max(1, island.x1() - island.x0() - 1)), island.top() + 1,
					island.z0() + 1 + random.nextInt(Math.max(1, island.z1() - island.z0() - 1)));
			spawn(run, genome, theme, genome.movement() == Genome.Movement.FLY ? at.above(3) : at);
		}
	}

	private static void spawn(Outer.Run run, Genome genome, String theme, BlockPos at) {
		AABB room = new AABB(at).inflate(16, 8, 16);
		SpireGuard guard = SpireGuards.make(run.level, genome, theme, at, room, room.inflate(16));
		if (guard != null) run.adopt(guard);
	}

	/** A fall: back on the last waystone, a life the less; none left, the run is over for the player. */
	private static void fell(Outer.Run run, ServerPlayer player) {
		run.lives--;
		if (run.lives <= 0) {
			com.vnap.Mc.say(player, Component.translatable("outer.villager_life.isles.over", run.stage), false);
			Outer.record(player, run);
			Outer.leave(player);
			return;
		}
		player.fallDistance = 0;
		com.vnap.Mc.teleport(player, run.level, run.checkpoint.getX() + 0.5, run.checkpoint.getY() + 1.0, run.checkpoint.getZ() + 0.5, 0.0F, 0.0F);
		com.vnap.Mc.say(player, Component.translatable("outer.villager_life.isles.fell", run.lives), true);
	}

	/** Cells nobody is near any more: taken away (drawn again the same if someone comes back). */
	private static void forget(Outer.Run run, List<ServerPlayer> on) {
		run.cells.entrySet().removeIf(entry -> {
			Cell cell = entry.getValue();
			int wx = run.origin.getX() + cell.cx() * CELL + CELL / 2;
			int wz = run.origin.getZ() + cell.cz() * CELL + CELL / 2;
			for (ServerPlayer player : on) {
				if (Math.abs(player.getX() - wx) <= KEEP * CELL && Math.abs(player.getZ() - wz) <= KEEP * CELL) return false;
			}
			erase(run, cell);
			return true;
		});
	}

	private static void erase(Outer.Run run, Cell cell) {
		List<Island> all = new ArrayList<>(cell.stones());
		all.add(cell.island());
		for (Island local : all) {
			Island island = shift(run, local);
			int depth = 14 + Math.max(island.x1() - island.x0(), island.z1() - island.z0());
			for (int x = island.x0(); x <= island.x1(); x++) {
				for (int z = island.z0(); z <= island.z1(); z++) {
					for (int y = island.top() - depth; y <= island.top() + 3; y++) run.level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	static void clear(Outer.Run run) {
		for (Map.Entry<Long, Cell> entry : run.cells.entrySet()) erase(run, entry.getValue());
		run.cells.clear();
	}
}
