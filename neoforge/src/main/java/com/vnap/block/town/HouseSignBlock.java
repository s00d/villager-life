package com.vnap.block.town;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A family's nameplate: a small wooden board hung on the wall by the door (or standing on a post), the family's
 * names on it (HouseSignRenderer). Its text is a sign's (HouseSignBlockEntity), so the home marker on its second
 * line works as on any sign; a player cannot edit it. FACING is the way its writing looks out.
 */
public final class HouseSignBlock extends BaseEntityBlock {
	//? if <26.3
	public static final MapCodec<HouseSignBlock> CODEC = simpleCodec(HouseSignBlock::new);
	public static final BooleanProperty POST = BooleanProperty.create("post");
	private static final VoxelShape POST_SHAPE = Block.box(7, 0, 7, 9, 9, 9);

	public HouseSignBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(POST, false));
	}

	// a block has no codec of its own from 26.3
	//? if <26.3 {
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	//?}

	/** Its writing looking {@code out}: on the wall behind it, or on a post of its own. */
	public BlockState facing(Direction out, boolean post) {
		return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, out).setValue(POST, post);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HorizontalDirectionalBlock.FACING, POST);
	}

	/** On the side of a block it hangs there, looking away from it; on the ground it stands on a post, looking at the one who sets it. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		return face.getAxis().isHorizontal() ? facing(face, false) : facing(context.getHorizontalDirection().getOpposite(), true);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return rotate(state, mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Direction out = state.getValue(HorizontalDirectionalBlock.FACING);
		if (state.getValue(POST)) {
			return Shapes.or(POST_SHAPE, out.getAxis() == Direction.Axis.Z ? Block.box(1, 8, 7, 15, 15, 9) : Block.box(7, 8, 1, 9, 15, 15));
		}
		return switch (out) {
			case SOUTH -> Block.box(1, 4, 0, 15, 12, 2);
			case WEST -> Block.box(14, 4, 1, 16, 12, 15);
			case EAST -> Block.box(0, 4, 1, 2, 12, 15);
			default -> Block.box(1, 4, 14, 15, 12, 16);
		};
	}

	/** Nobody bumps into a board on a wall (it hangs beside a door); a post stands like a fence post. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(POST) ? POST_SHAPE : Shapes.empty();
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HouseSignBlockEntity(pos, state);
	}
}
