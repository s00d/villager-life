package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The rift's sheet: drawn on its upper and lower faces only, where nothing of the rift is next to it. */
public final class SpireRiftBlockEntity extends TheEndPortalBlockEntity {
	public SpireRiftBlockEntity(BlockPos pos, BlockState state) {
		super(SpireBlocks.RIFT_BE, pos, state);
	}

	@Override
	public boolean shouldRenderFace(Direction face) {
		return face.getAxis() == Direction.Axis.Y;
	}
}
