package com.vnap.block.town;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/** A small smoking pot for a thin chimney (a stone wall post up through the roof), as {@link ChimneyPotBlock}. */
public final class SmallChimneyPotBlock extends ChimneyPotBlock {
	//? if <26.3
	public static final MapCodec<SmallChimneyPotBlock> CODEC = simpleCodec(SmallChimneyPotBlock::new);

	public SmallChimneyPotBlock(Properties properties) {
		super(properties, Block.box(4, 0, 4, 12, 9, 12), 0.6);
	}

	// a block has no codec of its own from 26.3
	//? if <26.3 {
	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}
	//?}
}
