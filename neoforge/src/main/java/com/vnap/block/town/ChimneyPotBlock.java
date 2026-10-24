package com.vnap.block.town;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The pot on top of a chimney: a brick stack under a stone cornice, smoke curling out of it all day, no fire under it
 * (a campfire there burnt whoever stood on the roof and lit up the night sky).
 */
public class ChimneyPotBlock extends Block {
	//? if <26.3
	public static final MapCodec<ChimneyPotBlock> CODEC = simpleCodec(ChimneyPotBlock::new);
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);
	private final VoxelShape shape;
	private final double mouth;

	public ChimneyPotBlock(Properties properties) {
		this(properties, SHAPE, 1.0);
	}

	/** The small one for a thin chimney (a wall post's top): a pot on a collar, its mouth lower. */
	protected ChimneyPotBlock(Properties properties, VoxelShape shape, double mouth) {
		super(properties);
		this.shape = shape;
		this.mouth = mouth;
	}

	// a block has no codec of its own from 26.3
	//? if <26.3 {
	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}
	//?}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// as a campfire smokes (CampfireBlock.makeParticles), from the pot's mouth, a little thinner
		if (random.nextFloat() < 0.35F) {
			level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
					pos.getX() + 0.5 + random.nextDouble() / 4.0 * (random.nextBoolean() ? 1 : -1), pos.getY() + mouth,
					pos.getZ() + 0.5 + random.nextDouble() / 4.0 * (random.nextBoolean() ? 1 : -1), 0.0, 0.06, 0.0);
		}
		if (random.nextFloat() < 0.15F) {
			level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + mouth, pos.getZ() + 0.5, 0.0, 0.02, 0.0);
		}
	}
}
