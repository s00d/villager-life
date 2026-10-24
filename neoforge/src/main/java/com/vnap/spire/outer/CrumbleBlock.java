package com.vnap.spire.outer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Sky Path's crumbling stone: whole (0), cracked under a foot (1: it shakes and sheds grit for a second), fallen
 * (2: nothing to stand on, nothing to see) — and whole again a few seconds later. A run's islands of it are crossed at
 * a run, not stood on.
 */
public final class CrumbleBlock extends Block {
	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
	private static final int CRACKS = 16;
	private static final int REGROWS = 120;

	public CrumbleBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STAGE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(STAGE) == 2 ? Shapes.empty() : Shapes.block();
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (state.getValue(STAGE) == 0 && entity instanceof Player && level instanceof ServerLevel server) {
			server.setBlock(pos, state.setValue(STAGE, 1), 3);
			server.playSound(null, pos, SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.BLOCKS, 0.8F, 0.7F);
			server.scheduleTick(pos, this, CRACKS);
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int stage = state.getValue(STAGE);
		if (stage == 1) {
			level.setBlock(pos, state.setValue(STAGE, 2), 3);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5,
					pos.getZ() + 0.5, 24, 0.3, 0.3, 0.3, 0.1);
			level.playSound(null, pos, SoundEvents.TUFF_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
			level.scheduleTick(pos, this, REGROWS);
		} else if (stage == 2) {
			level.setBlock(pos, state.setValue(STAGE, 0), 3);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(STAGE) == 1) {
			level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.TUFF.defaultBlockState()), pos.getX() + random.nextDouble(), pos.getY(),
					pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
		}
	}
}
