package com.vnap.fab.neoforge.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** A block as it stood before a placement: where, and what was there. */
public final class BlockSnapshot {
	private final LevelAccessor level;
	private final BlockPos pos;
	private final BlockState state;

	private BlockSnapshot(LevelAccessor level, BlockPos pos, BlockState state) {
		this.level = level;
		this.pos = pos.immutable();
		this.state = state;
	}

	public static BlockSnapshot create(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, LevelAccessor level, BlockPos pos) {
		return new BlockSnapshot(level, pos, level.getBlockState(pos));
	}

	public LevelAccessor getLevel() {
		return level;
	}

	public BlockPos getPos() {
		return pos;
	}

	public BlockState getState() {
		return state;
	}
}
