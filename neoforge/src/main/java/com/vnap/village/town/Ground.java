package com.vnap.village.town;

import com.vnap.village.build.BuildJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Where the ground of a column is, in the few senses the mod asks it:
 * <ul>
 * <li>{@link #surface}: the highest real block (roofs and treetops too);</li>
 * <li>{@link #near}: a sturdy block to stand on near a height (a bell, a counter), two free blocks over it;</li>
 * <li>{@link #top}: {@code near} the surface, where a villager or an animal is set down in the open;</li>
 * <li>{@link #natural}: the natural ground under trees and plants, for a lot that will be cleared and built on;</li>
 * <li>{@link RoadFinder#surface}: the cell a road may lie on (natural ground, its own road, water for a bridge).</li>
 * </ul>
 */
public final class Ground {
	private Ground() {
	}

	/** The highest real block of a column: the heightmap, without barriers (a test's ceiling) and the air under them. */
	public static int surface(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
		// what the heightmap skips (grass, flowers, torches) is skipped under a barrier roof too
		while (y > level.getMinBuildHeight() && (level.getBlockState(pos).is(Blocks.BARRIER)
				|| (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos).getFluidState().isEmpty()))) {
			pos.setY(--y);
		}
		return y;
	}

	/** The top of the ground within 8 blocks of {@code aroundY}: a sturdy block (not leaves) with open air over it. */
	public static @Nullable BlockPos near(ServerLevel level, int x, int z, int aroundY) {
		return near(level, x, z, aroundY, 8, 8);
	}

	/** {@link #near} looked for from {@code up} above {@code aroundY} down to {@code down} below it (highest first). */
	public static @Nullable BlockPos near(ServerLevel level, int x, int z, int aroundY, int up, int down) {
		for (int y = aroundY + up; y >= aroundY - down; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP) && !level.getBlockState(pos).is(BlockTags.LEAVES)
					&& level.getBlockState(pos.above()).canBeReplaced() && level.getBlockState(pos.above(2)).canBeReplaced()) {
				return pos;
			}
		}
		return null;
	}

	/** {@link #near} the column's {@link #surface}. */
	public static @Nullable BlockPos top(ServerLevel level, int x, int z) {
		return near(level, x, z, surface(level, x, z));
	}

	/**
	 * The natural ground of a column near {@code y} under trees and plants (they are cleared), or null: water, a
	 * building, nothing firm within reach.
	 */
	public static @Nullable Integer natural(ServerLevel level, int x, int z, int y) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y + TownRules.LOT_RISE + 8, z);
		for (int d = 0; d <= TownRules.LOT_RISE + 8 + TownRules.LOT_DIP + 2 + TownRules.LOT_TERRACE; d++) {
			pos.setY(y + TownRules.LOT_RISE + 8 - d);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
					|| state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(Blocks.SNOW)) {
				if (!state.getFluidState().isEmpty()) return null;
				continue;
			}
			return BuildJob.naturalGround(state) ? pos.getY() : null;
		}
		return null;
	}
}
