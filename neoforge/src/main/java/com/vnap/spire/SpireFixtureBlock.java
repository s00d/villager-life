package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Something of the Builders' hung on a wall inside the spire, facing away from it: a sconce with a crystal of the Core
 * burning in it, or a tapestry with their sign. Their models face north, the wall to the south.
 */
public final class SpireFixtureBlock extends Block {
	public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public enum Kind {
		SCONCE, TAPESTRY
	}

	private final Kind kind;

	public SpireFixtureBlock(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape north = kind == Kind.SCONCE ? Block.box(5, 3, 9, 11, 14, 16) : Block.box(2, 0, 15, 14, 16, 16);
		return switch (state.getValue(FACING)) {
			case SOUTH -> kind == Kind.SCONCE ? Block.box(5, 3, 0, 11, 14, 7) : Block.box(2, 0, 0, 14, 16, 1);
			case EAST -> kind == Kind.SCONCE ? Block.box(0, 3, 5, 7, 14, 11) : Block.box(0, 0, 2, 1, 16, 14);
			case WEST -> kind == Kind.SCONCE ? Block.box(9, 3, 5, 16, 14, 11) : Block.box(15, 0, 2, 16, 16, 14);
			default -> north;
		};
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return rotate(state, mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (kind != Kind.SCONCE || random.nextInt(3) != 0) return;
		Direction facing = state.getValue(FACING);
		double x = pos.getX() + 0.5 - facing.getStepX() * 0.1;
		double z = pos.getZ() + 0.5 - facing.getStepZ() * 0.1;
		level.addParticle(random.nextInt(4) == 0 ? ParticleTypes.END_ROD : ParticleTypes.WITCH, x, pos.getY() + 0.85, z, 0.0, 0.01, 0.0);
	}
}
