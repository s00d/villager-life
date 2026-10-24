package com.vnap.village.town;

import com.vnap.mood.MoodConfig;
import com.vnap.village.home.Mayors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

/**
 * How many people the town's land can hold, looked at once over the ground (as a town planner would before laying a
 * town out): every CELL×CELL square within MAX_REACH of the bell that is the town's (nearer its bell than any other)
 * and loaded is either buildable (natural ground, level enough for a lot, no water, nothing built) or not. Once most of
 * it was seen, the town's limit is the beds it has plus the houses the buildable ground takes, never more than the
 * config's maxVillagers: a village on a small island stops growing at what fits on it and sends its young off to
 * found hamlets, instead of waiting for room that is not there. Daily from the town's plan (Roads.plan).
 */
public final class TownSurvey {
	/** The side of a surveyed square. */
	static final int CELL = 8;
	/** Squares looked at a day (the first days of a town see all of what is loaded). */
	static final int PER_DAY = 400;
	/** The share of the town's squares seen before the land counts. */
	static final double SEEN = 0.8;
	/** Of buildable ground, the share lots take (streets, verges and odd corners take the rest). */
	static final double LOTS_SHARE = 0.45;
	/** People a house holds as it grows (a couple, their children, a storey added). */
	static final int PER_HOUSE = 4;
	/** A town is never held below this. */
	static final int LEAST = 8;

	private TownSurvey() {
	}

	/** The squares of the town not seen yet are looked at (loaded ones, a day's worth); the limit is worked out again. */
	static void survey(ServerLevel level, Roads.Network network, BlockPos bell) {
		int r = (int) (TownRules.MAX_REACH / CELL);
		int looked = 0;
		int total = 0;
		for (int i = -r; i <= r; i++) {
			for (int j = -r; j <= r; j++) {
				if (i * i + j * j > r * r) continue;
				BlockPos centre = new BlockPos(bell.getX() + i * CELL, bell.getY(), bell.getZ() + j * CELL);
				if (!nearest(level, bell, centre)) continue;
				total++;
				long key = BlockPos.asLong(i, 0, j);
				if (network.goodLand.contains(key) || network.badLand.contains(key) || looked >= PER_DAY) continue;
				if (!level.isPositionEntityTicking(centre)) continue;
				looked++;
				(buildable(level, centre, bell.getY()) ? network.goodLand : network.badLand).add(key);
			}
		}
		network.landCells = total;
		network.capacity = capacity(level, network, bell);
	}

	/**
	 * Ground changed round {@code at} (a Drain dried a flooded corner of the town): its squares found unfit are looked
	 * at again by the next days' survey, and the town's limit follows what is buildable now.
	 */
	public static void again(ServerLevel level, BlockPos at, int reach) {
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), at);
		if (network == null) return;
		BlockPos bell = network.bell;
		if (network.badLand.removeIf(key -> {
			BlockPos square = BlockPos.of(key);
			return Math.abs(bell.getX() + square.getX() * CELL - at.getX()) <= reach + CELL && Math.abs(bell.getZ() + square.getZ() * CELL - at.getZ()) <= reach + CELL;
		})) Roads.get(level.getServer()).setDirty();
	}

	/** Nearer this bell than any other town's: the town's own when it has grown to it. */
	private static boolean nearest(ServerLevel level, BlockPos bell, BlockPos pos) {
		double own = horizontal(bell, pos);
		for (Mayors.Office office : Mayors.get(level.getServer()).offices()) {
			if (office.dimension.equals(level.dimension()) && !office.bell.closerThan(bell, TownRules.SPAN) && horizontal(office.bell, pos) < own) return false;
		}
		return true;
	}

	/** Natural ground under the square's middle and corners, no water, no step bigger than a lot can take. */
	private static boolean buildable(ServerLevel level, BlockPos centre, int around) {
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		int half = CELL / 2 - 1;
		for (int[] d : new int[][]{{0, 0}, {-half, -half}, {half, half}, {-half, half}, {half, -half}}) {
			int x = centre.getX() + d[0];
			int z = centre.getZ() + d[1];
			Integer ground = Ground.natural(level, x, z, Ground.surface(level, x, z) - TownRules.LOT_RISE);
			if (ground == null) return false;
			low = Math.min(low, ground);
			high = Math.max(high, ground);
		}
		return high - low <= TownRules.LOT_DIP + TownRules.LOT_RISE && Math.abs(low - around) <= 24;
	}

	/** The beds the town has and the houses its buildable land takes; -1 while too little of it was seen. */
	static int capacity(ServerLevel level, Roads.Network network, BlockPos bell) {
		int seen = network.goodLand.size() + network.badLand.size();
		if (network.landCells == 0 || seen < network.landCells * SEEN) return -1;
		int beds = (int) level.getPoiManager().getCountInRange(type -> type.is(PoiTypes.HOME), bell, (int) TownRules.MAX_REACH, PoiManager.Occupancy.ANY);
		int lots = (int) (network.goodLand.size() * (double) (CELL * CELL) * LOTS_SHARE / (TownRules.LOT * TownRules.LOT));
		return Math.max(LEAST, beds + lots * PER_HOUSE);
	}

	/** The most people this town takes: what its land holds, never more than the config's limit. */
	public static int limit(Roads.@org.jspecify.annotations.Nullable Network network) {
		int max = MoodConfig.maxVillagers();
		return network == null || network.capacity < 0 ? max : Math.min(max, network.capacity);
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}
}
