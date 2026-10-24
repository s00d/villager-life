package com.vnap.block.town;

import com.vnap.block.LifeBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A nameplate's text: a sign's four lines (head, the home marker, spouse, children), read and written as any sign's. */
public final class HouseSignBlockEntity extends SignBlockEntity {
	public HouseSignBlockEntity(BlockPos pos, BlockState state) {
		super(LifeBlocks.HOUSE_SIGN_BE, pos, state);
	}
}
