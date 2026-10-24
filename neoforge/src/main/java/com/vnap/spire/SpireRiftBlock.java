package com.vnap.spire;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Core's rift that cuts a spire off where it has grown to: a sheet of the starry void (drawn like the End portal,
 * client/spire/SpireRiftRenderer) lying over the worn top, motes rising off it and falling back.
 */
public final class SpireRiftBlock extends BaseEntityBlock {
	//? if <26.3
	public static final MapCodec<SpireRiftBlock> CODEC = simpleCodec(SpireRiftBlock::new);
	/** The middle of the rift, where its beam stands. */
	public static final BooleanProperty HEART = BooleanProperty.create("heart");
	private static final VoxelShape SHAPE = Block.box(0, 6, 0, 16, 12, 16);

	public SpireRiftBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HEART, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HEART);
	}

	//? if <26.3 {
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	//?}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SpireRiftBlockEntity(pos, state);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + random.nextDouble();
		double z = pos.getZ() + random.nextDouble();
		if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.REVERSE_PORTAL, x, pos.getY() + 0.8, z, 0.0, 0.04 + random.nextDouble() * 0.06, 0.0);
		if (random.nextInt(6) == 0) level.addParticle(ParticleTypes.PORTAL, x, pos.getY() + 0.5, z, 0.0, -0.3, 0.0);
		if (random.nextInt(40) == 0) level.addParticle(ParticleTypes.END_ROD, x, pos.getY() + 0.7, z, 0.0, 0.08, 0.0);
		if (state.getValue(HEART)) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 1.0,
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.25 + random.nextDouble() * 0.2, 0.0);
		}
	}
}
