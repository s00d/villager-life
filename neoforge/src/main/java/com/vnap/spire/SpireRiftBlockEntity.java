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

	/** What its renderer draws: the sheet and the beam above it. */
	public net.minecraft.world.phys.AABB renderBox() {
		return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(0.0, 1.5, 0.0).expandTowards(0.0, BEAM_HEIGHT, 0.0);
	}

	/** The beam's height over the rift. */
	public static final float BEAM_HEIGHT = 64.0F;
	//? if forge {

	/*@Override
	public net.minecraft.world.phys.AABB getRenderBoundingBox() {
		return renderBox();
	}
	*///?}
}
