package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Builders' lamp, hung on a chain: a brass cage with a crystal of the Core burning in it. */
public final class SpireLampBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(5, 3, 5, 11, 16, 11);

	public SpireLampBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) != 0) return;
		level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.WITCH, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
				pos.getY() + 0.4, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0.0, -0.01, 0.0);
	}
}
