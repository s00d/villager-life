package com.vnap.village.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The village the world generated around the town's bell, taken into the town: the game knows it as one structure
 * start (its jigsaw pieces from the village pools), so its houses are read from there (the {@code houses/} pieces, no
 * guessing from blocks) and kept as the town's buildings (no lot, road or wall goes over them; the board's map shows
 * them), their doors joined to the roads like any house's, and its streets' trodden paths become the town's roads (paved
 * with the rest once the town is half full). Read once a day while its pieces are loaded; a house the player tore down
 * (no door left) drops out.
 */
public final class WorldHouses {
	private WorldHouses() {
	}

	/** The pieces of the generated village the bell stands in, or none (a bell a player hung, a hamlet). */
	static List<PoolElementStructurePiece> pieces(ServerLevel level, BlockPos bell) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(bell, StructureTags.VILLAGE);
		List<PoolElementStructurePiece> out = new ArrayList<>();
		if (!start.isValid()) return out;
		for (StructurePiece piece : start.getPieces()) if (piece instanceof PoolElementStructurePiece pool) out.add(pool);
		return out;
	}

	/** Once a day (TownPlan.plan): the generated village's houses and streets, where loaded, into the town. */
	static void scan(ServerLevel level, Roads.Network network, BlockPos bell) {
		List<BoundingBox> houses = new ArrayList<>();
		for (PoolElementStructurePiece piece : pieces(level, bell)) {
			String element = piece.getElement().toString();
			BoundingBox box = piece.getBoundingBox();
			BlockPos low = new BlockPos(box.minX(), box.minY(), box.minZ());
			BlockPos high = new BlockPos(box.maxX(), box.minY(), box.maxZ());
			if (!level.isLoaded(low) || !level.isLoaded(high)) {
				// out of sight: kept as it was
				for (BoundingBox known : network.worldHouses) if (known.equals(box)) houses.add(known);
				continue;
			}
			if (element.contains("/houses/")) {
				if (door(level, box) != null) houses.add(box);
			} else if (element.contains("/streets/")) {
				paths(level, network, box);
			}
		}
		network.worldHouses.clear();
		network.worldHouses.addAll(houses);
		// how far out the village the world made stands (Towns.reach takes it in)
		com.vnap.village.home.Mayors.Office office = com.vnap.village.home.Towns.of(level, bell);
		if (office != null) {
			int extent = 0;
			for (PoolElementStructurePiece piece : pieces(level, bell)) {
				BoundingBox box = piece.getBoundingBox();
				for (int x : new int[]{box.minX(), box.maxX()}) {
					for (int z : new int[]{box.minZ(), box.maxZ()}) {
						extent = Math.max(extent, (int) Math.sqrt((double) (x - bell.getX()) * (x - bell.getX()) + (double) (z - bell.getZ()) * (z - bell.getZ())));
					}
				}
			}
			if (extent != office.worldExtent) {
				office.worldExtent = extent;
				com.vnap.village.home.Mayors.get(level.getServer()).setDirty();
			}
		}
	}

	/** The street piece's trodden path cells become road (and are paved with the town's). */
	private static void paths(ServerLevel level, Roads.Network network, BoundingBox box) {
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.maxY(); y >= box.minY(); y--) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || state.canBeReplaced()) continue;
					if (state.is(Blocks.DIRT_PATH)) network.cells.add(pos);
					break;
				}
			}
		}
	}

	/** The ground just outside the house's front door (the lowest door), or null: no door left in it. */
	static @Nullable BlockPos door(ServerLevel level, BoundingBox box) {
		BlockPos best = null;
		BlockPos centre = box.getCenter();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
			Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
			Direction out = pos.relative(facing).distSqr(centre) >= pos.relative(facing.getOpposite()).distSqr(centre) ? facing : facing.getOpposite();
			BlockPos step = pos.relative(out).below();
			if (best == null || step.getY() < best.getY()) best = step.immutable();
		}
		return best;
	}

	/** The doors of the generated houses, as nodes the town's roads join (Roads.nodes). */
	static List<BlockPos> doors(ServerLevel level, Roads.Network network) {
		List<BlockPos> out = new ArrayList<>();
		for (BoundingBox box : network.worldHouses) {
			if (!level.isLoaded(box.getCenter())) continue;
			BlockPos step = door(level, box);
			if (step == null) continue;
			RoadFinder.Cell cell = RoadFinder.surface(level, step.getX(), step.getZ(), network.cells::contains);
			if (cell != null && Math.abs(cell.pos().getY() - step.getY()) <= 1) out.add(cell.pos());
		}
		return out;
	}
}
