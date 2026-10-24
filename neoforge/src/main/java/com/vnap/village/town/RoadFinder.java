package com.vnap.village.town;

import com.vnap.village.build.BuildJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * The way a road takes over the ground, A* on the surface (as Millénaire's village paths and the GDMC settlements
 * do): four neighbours, a step of at most one block, steeper and turning costs more, existing road is cheap (new
 * roads join the network rather than run beside it), water is crossed by a bridge, buildings and their walls are kept
 * clear of. Only entity-ticking chunks are read: the rest of the world is not there for the road.
 */
public final class RoadFinder {
	/** Nodes looked at before giving up: a road a hundred blocks long over rough ground fits easily. */
	static final int BUDGET = 20000;
	static final int MAX_BRIDGE = 10;

	private RoadFinder() {
	}

	/** A column the road may lie on: the surface block (what it becomes) and whether it is a bridge over water. */
	public record Cell(BlockPos pos, boolean water) {
	}

	/** The surface of a column in a ticking chunk the road could lie on, or null (a building, a tree, a cliff, lava). */
	public static @Nullable Cell surface(ServerLevel level, int x, int z, Predicate<BlockPos> road) {
		BlockPos column = new BlockPos(x, 0, z);
		if (!level.isLoaded(column) || !level.isPositionEntityTicking(column)) return null;
		int y = Ground.surface(level, x, z);
		BlockPos pos = new BlockPos(x, y, z);
		BlockState top = level.getBlockState(pos);
		// a street lamp's arm and lantern hang over the road: the ground is under them (a fence standing on the ground
		// is still found in the way by the headroom check below)
		while ((top.getBlock() instanceof net.minecraft.world.level.block.FenceBlock || top.getBlock() instanceof net.minecraft.world.level.block.LanternBlock
				|| top.isAir()) && pos.getY() > level.getMinBuildHeight()) {
			pos = pos.below();
			top = level.getBlockState(pos);
		}
		if (top.getBlock() instanceof SnowLayerBlock) {
			pos = pos.below();
			top = level.getBlockState(pos);
		}
		// a road's half step on a rise stands on the road's own cell
		if (top.getBlock() instanceof net.minecraft.world.level.block.SlabBlock && road.test(pos.below())) {
			pos = pos.below();
			top = level.getBlockState(pos);
		}
		boolean water = top.getFluidState().is(FluidTags.WATER) && top.getFluidState().isSource();
		if (!water && !BuildJob.naturalGround(top) && !(Roads.roadSurface(top) && road.test(pos))) return null;
		if (top.getFluidState().is(FluidTags.LAVA)) return null;
		for (int up = 1; up <= 2; up++) {
			BlockState above = level.getBlockState(pos.above(up));
			if (up == 1 && above.getBlock() instanceof net.minecraft.world.level.block.SlabBlock && road.test(pos)) continue;
			if (!above.getCollisionShape(level, pos.above(up)).isEmpty() && !above.canBeReplaced() && !above.is(net.minecraft.world.level.block.Blocks.BARRIER)) return null;
			if (!above.getFluidState().isEmpty()) return null;
		}
		return new Cell(pos, water);
	}

	/**
	 * The cells from {@code start} (a surface cell by a door) to the first cell {@code goal} accepts, both included,
	 * or an empty list. {@code ours} keeps the road on its own town's land; {@code blocked} are the buildings.
	 */
	public static List<Cell> route(ServerLevel level, BlockPos start, Predicate<BlockPos> goal, Predicate<BlockPos> ours,
								   Land blocked, Predicate<BlockPos> road, BlockPos towards) {
		return route(level, start, goal, ours, blocked, road, towards, Integer.MAX_VALUE);
	}

	/**
	 * The same, no longer than {@code maxCells}: a street's step wants a short way or none, and without the bound a step
	 * with no way looked over all BUDGET nodes (most of a town's daily plan went there).
	 */
	public static List<Cell> route(ServerLevel level, BlockPos start, Predicate<BlockPos> goal, Predicate<BlockPos> ours,
								   Land blocked, Predicate<BlockPos> road, BlockPos towards, int maxCells) {
		// a column's surface is looked at from up to four sides: once
		Map<Long, java.util.Optional<Cell>> columns = new HashMap<>();
		java.util.function.BiFunction<Integer, Integer, Cell> at = (x, z) -> columns.computeIfAbsent(BlockPos.asLong(x, 0, z),
				key -> java.util.Optional.ofNullable(surface(level, x, z, road))).orElse(null);
		Cell first = at.apply(start.getX(), start.getZ());
		if (first == null) return List.of();
		record Node(Key key, double f) {
		}
		Map<Key, Double> best = new HashMap<>();
		Map<Key, Key> from = new HashMap<>();
		Map<Key, Cell> cells = new HashMap<>();
		Map<Key, Integer> bridge = new HashMap<>();
		Map<Key, Direction> arrived = new HashMap<>();
		Map<Key, Integer> length = new HashMap<>();
		PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
		Key startKey = new Key(first.pos().asLong());
		best.put(startKey, 0.0);
		cells.put(startKey, first);
		bridge.put(startKey, 0);
		length.put(startKey, 1);
		open.add(new Node(startKey, heuristic(first.pos(), towards)));
		int looked = 0;
		while (!open.isEmpty() && looked++ < BUDGET) {
			Node node = open.poll();
			Cell cell = cells.get(node.key());
			double g = best.getOrDefault(node.key(), Double.MAX_VALUE);
			if (cell == null || node.f() - heuristic(cell.pos(), towards) > g + 1.0E-6) continue;
			if (!node.key().equals(startKey) && goal.test(cell.pos())) return path(node.key(), from, cells);
			Direction came = arrived.get(node.key());
			int cellsSoFar = length.getOrDefault(node.key(), 1);
			if (cellsSoFar >= maxCells) continue;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next2d = cell.pos().relative(side);
				Cell next = at.apply(next2d.getX(), next2d.getZ());
				if (next == null || Math.abs(next.pos().getY() - cell.pos().getY()) > 1) continue;
				if (!ours.test(next.pos()) || blocked.under(next.pos())) continue;
				int span = next.water() ? bridge.getOrDefault(node.key(), 0) + 1 : 0;
				if (span > MAX_BRIDGE) continue;
				double step = 1.0 + (next.pos().getY() != cell.pos().getY() ? 2.0 : 0.0);
				if (came != null && came != side) step += 0.3;
				if (next.water()) step += 3.0;
				if (blocked.beside(next.pos())) step += 1.5;
				if (road.test(next.pos())) step *= 0.3;
				Key nextKey = new Key(next.pos().asLong());
				double cost = g + step;
				if (cost >= best.getOrDefault(nextKey, Double.MAX_VALUE)) continue;
				best.put(nextKey, cost);
				from.put(nextKey, node.key());
				cells.put(nextKey, next);
				bridge.put(nextKey, span);
				arrived.put(nextKey, side);
				length.put(nextKey, cellsSoFar + 1);
				open.add(new Node(nextKey, cost + heuristic(next.pos(), towards)));
			}
		}
		return List.of();
	}

	private static List<Cell> path(Key end, Map<Key, Key> from, Map<Key, Cell> cells) {
		List<Cell> out = new ArrayList<>();
		Key at = end;
		while (at != null) {
			Cell cell = cells.get(at);
			if (cell != null) out.add(cell);
			at = from.get(at);
		}
		Collections.reverse(out);
		return out;
	}

	private record Key(long pos) {
	}

	/**
	 * The straight way there. Existing road costs less, so the estimate may overshoot near a road: the route found is
	 * then not always the cheapest, but it is found fast (a plain A* over a town's ground ran out of nodes).
	 */
	private static double heuristic(BlockPos pos, BlockPos towards) {
		return Math.abs(pos.getX() - towards.getX()) + Math.abs(pos.getZ() - towards.getZ());
	}
}
