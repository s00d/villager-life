package com.vnap.village.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The ground a town has given away, asked by column: its buildings (houses with their rooms, workshops, the town's
 * own buildings, graves, the bell), its ways (roads, lamps, the square's things, the wall and its ditch) and its lots
 * (the plan's ground kept for houses to come and the players' bought ground). Every place a house, a workshop, a
 * building or a road is looked for asks this one index: what used to be a list of boxes per caller, each with its own
 * margins and radius, walked over for every block of every plan tried.
 */
public final class Land {
	public enum Layer { BUILDING, WAY, LOT }

	public record Claim(Layer layer, BoundingBox box) {
	}

	private final List<Claim> claims;
	private @org.jspecify.annotations.Nullable Map<Long, List<Claim>> columns;

	public Land(List<Claim> claims) {
		this.claims = claims;
	}

	public static Land of(List<BoundingBox> buildings) {
		List<Claim> claims = new ArrayList<>();
		for (BoundingBox box : buildings) claims.add(new Claim(Layer.BUILDING, box));
		return new Land(claims);
	}

	/** Everything the town around {@code pos} has given away (and the houses and workshops near it without a town). */
	public static Land around(ServerLevel level, BlockPos pos) {
		return Roads.land(level, pos);
	}

	public List<Claim> claims() {
		return claims;
	}

	/** This land with more buildings on it. */
	public Land with(Layer layer, List<BoundingBox> boxes) {
		List<Claim> more = new ArrayList<>(claims);
		for (BoundingBox box : boxes) more.add(new Claim(layer, box));
		return new Land(more);
	}

	/** One more claim on this very land: a street grown or a lot cut a moment ago, in the plan that holds it. */
	public void add(Layer layer, BoundingBox box) {
		Claim claim = new Claim(layer, box);
		claims.add(claim);
		Map<Long, List<Claim>> built = columns;
		if (built == null) return;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) built.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> new ArrayList<>(2)).add(claim);
		}
	}

	/** This land without the claims {@code gone} picks (a house that comes down for the one asking, its own lot). */
	public Land without(Predicate<Claim> gone) {
		List<Claim> left = new ArrayList<>(claims);
		left.removeIf(gone);
		return new Land(left);
	}

	private Map<Long, List<Claim>> index() {
		Map<Long, List<Claim>> built = columns;
		if (built != null) return built;
		built = new HashMap<>();
		for (Claim claim : claims) {
			BoundingBox box = claim.box();
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) built.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> new ArrayList<>(2)).add(claim);
			}
		}
		columns = built;
		return built;
	}

	private List<Claim> at(int x, int z) {
		return index().getOrDefault(BlockPos.asLong(x, 0, z), List.of());
	}

	/** A claim holds this block (a building {@code margin} blocks round it too). */
	public boolean blocked(BlockPos pos, int margin) {
		for (int x = pos.getX() - margin; x <= pos.getX() + margin; x++) {
			for (int z = pos.getZ() - margin; z <= pos.getZ() + margin; z++) {
				boolean near = x != pos.getX() || z != pos.getZ();
				for (Claim claim : at(x, z)) {
					if (near && claim.layer() == Layer.WAY) continue;
					BoundingBox box = claim.box();
					if (pos.getY() >= box.minY() - margin && pos.getY() <= box.maxY() + margin) return true;
				}
			}
		}
		return false;
	}

	/** A claim overlaps the box: ways inside it, buildings and lots {@code margin} blocks round it. */
	public boolean intersects(BoundingBox box, int margin) {
		return hit(box, margin) != null;
	}

	/** The first claim that overlaps the box (as {@link #intersects}), or null. */
	public @org.jspecify.annotations.Nullable Claim hit(BoundingBox box, int margin) {
		BoundingBox around = box.inflatedBy(margin);
		for (int x = around.minX(); x <= around.maxX(); x++) {
			for (int z = around.minZ(); z <= around.maxZ(); z++) {
				boolean inside = x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
				for (Claim claim : at(x, z)) {
					if (!inside && claim.layer() == Layer.WAY) continue;
					if (claim.box().intersects(inside ? box : around)) return claim;
				}
			}
		}
		return null;
	}

	/** A building or a lot stands over this road cell (its column, from two blocks under it up): a road goes round. */
	public boolean under(BlockPos pos) {
		for (Claim claim : at(pos.getX(), pos.getZ())) {
			if (claim.layer() == Layer.WAY) continue;
			BoundingBox box = claim.box();
			if (pos.getY() >= box.minY() - 2 && pos.getY() <= box.maxY()) return true;
		}
		return false;
	}

	/** A building or lot right beside the cell: a road keeps off a wall when it can. */
	public boolean beside(BlockPos pos) {
		for (Direction side : Direction.Plane.HORIZONTAL) if (under(pos.relative(side))) return true;
		return false;
	}

	/** Columns of the land, for a map: who holds each one around {@code centre} within {@code radius}. */
	public Map<Long, Layer> map(BlockPos centre, int radius) {
		Map<Long, Layer> out = new HashMap<>();
		for (var entry : index().entrySet()) {
			BlockPos column = BlockPos.of(entry.getKey());
			if (Math.abs(column.getX() - centre.getX()) > radius || Math.abs(column.getZ() - centre.getZ()) > radius) continue;
			Layer layer = Layer.LOT;
			for (Claim claim : entry.getValue()) {
				if (claim.layer() == Layer.BUILDING || (claim.layer() == Layer.WAY && layer == Layer.LOT)) layer = claim.layer();
			}
			out.put(entry.getKey(), layer);
		}
		return out;
	}

	/** A box of a single column from {@code ground} up {@code height} blocks. */
	static BoundingBox column(BlockPos ground, int height) {
		return new BoundingBox(ground.getX(), ground.getY(), ground.getZ(), ground.getX(), ground.getY() + height, ground.getZ());
	}

	/** {@code under} over a short list of boxes (the square's and the wall's own few). */
	static boolean inside(List<BoundingBox> boxes, BlockPos pos) {
		for (BoundingBox box : boxes) {
			if (pos.getX() >= box.minX() && pos.getX() <= box.maxX() && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ()
					&& pos.getY() >= box.minY() - 2 && pos.getY() <= box.maxY()) return true;
		}
		return false;
	}
}
