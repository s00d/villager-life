package com.vnap.village.town;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Spots round a centre for every search that looks for room near something (a house, a shop, a grave, a kennel):
 * rings nearest first, each walked from the east the same way. Points keep the centre's height.
 */
public final class Rings {
	private Rings() {
	}

	/** The column {@code distance} blocks from the centre at {@code angle} (radians, 0 = east). */
	public static BlockPos at(BlockPos centre, double angle, double distance) {
		return new BlockPos(centre.getX() + (int) Math.round(Math.cos(angle) * distance), centre.getY(),
				centre.getZ() + (int) Math.round(Math.sin(angle) * distance));
	}

	/** {@code count} spots evenly round one ring. */
	public static List<BlockPos> ring(BlockPos centre, double radius, int count) {
		List<BlockPos> out = new ArrayList<>(count);
		for (int step = 0; step < count; step++) out.add(at(centre, Math.PI * 2.0 * step / count, radius));
		return out;
	}

	/** Rings from radius {@code from} to {@code to} (outward or inward) {@code step} apart, {@code perRing} spots each. */
	public static List<BlockPos> around(BlockPos centre, int from, int to, int step, int perRing) {
		return around(centre, from, to, step, radius -> perRing);
	}

	/** As above, the number of spots on a ring chosen by its radius. */
	public static List<BlockPos> around(BlockPos centre, int from, int to, int step, IntUnaryOperator perRing) {
		List<BlockPos> out = new ArrayList<>();
		int by = from <= to ? step : -step;
		for (int radius = from; from <= to ? radius <= to : radius >= to; radius += by) {
			out.addAll(ring(centre, radius, perRing.applyAsInt(radius)));
		}
		return out;
	}
}
