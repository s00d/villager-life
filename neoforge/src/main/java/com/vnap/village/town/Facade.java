package com.vnap.village.town;

import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HouseProject;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A built house's outside as it stands in the world: its doors and windows and which way each looks out. */
final class Facade {
	/** How far open ground must run from a door or window before it is out of the house's box. */
	private static final int OUT = 4;

	record Opening(BlockPos pos, Direction out) {
	}

	private Facade() {
	}

	/** The way out of the house from {@code pos} (a door, a window): free cells running out of the box, or null. */
	static @Nullable Direction outward(ServerLevel level, BoundingBox box, BlockPos pos) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			for (int i = 1; i <= OUT; i++) {
				BlockPos at = pos.relative(side, i);
				if (!box.isInside(at)) return side;
				BlockState state = level.getBlockState(at);
				if (!state.isAir() && !state.canBeReplaced()) break;
			}
		}
		return null;
	}

	/** The house's doors (their lower halves) that open to the outside, where they stand built. */
	static List<Opening> doors(ServerLevel level, HouseProject house) {
		return openings(level, house, true);
	}

	/** The doors (lower halves) in a box that open to the outside: a house of the village the world made. */
	static List<Opening> doors(ServerLevel level, BoundingBox box) {
		List<Opening> out = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.isLoaded(pos)) continue;
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
			Direction side = outward(level, box, pos);
			if (side != null) out.add(new Opening(pos.immutable(), side));
		}
		return out;
	}

	/** The house's windows (glass, panes) that look out, cell by cell. */
	static List<Opening> windows(ServerLevel level, HouseProject house) {
		return openings(level, house, false);
	}

	static boolean window(BlockState state) {
		return state.getBlock() instanceof TransparentBlock || (state.getBlock() instanceof IronBarsBlock && !state.is(net.minecraft.world.level.block.Blocks.IRON_BARS));
	}

	private static List<Opening> openings(ServerLevel level, HouseProject house, boolean doors) {
		BoundingBox box = house.footprint(level);
		List<Opening> out = new ArrayList<>();
		for (SchematicSpec.PlacedBlock block : house.schematic(level).blocks) {
			BlockPos pos = BuildJob.at(house.origin, house.rotation, block.rel());
			if (!level.isLoaded(pos)) continue;
			BlockState state = level.getBlockState(pos);
			boolean match = doors ? state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER : window(state);
			if (!match) continue;
			Direction side = outward(level, box, pos);
			if (side != null) out.add(new Opening(pos.immutable(), side));
		}
		return out;
	}
}
