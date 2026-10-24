package com.vnap.block.town;

import com.mojang.serialization.MapCodec;
import com.vnap.village.market.Coins;
import com.vnap.village.town.NoticeBoard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <1.21.2
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The town's notice board (NoticeBoard): an empty hand reads it, a wanted thing in hand is brought to the stall for
 * its full price, coins in hand go to the treasury.
 */
public final class NoticeBoardBlock extends HorizontalDirectionalBlock {
	//? if <26.3
	public static final MapCodec<NoticeBoardBlock> CODEC = simpleCodec(NoticeBoardBlock::new);
	private static final VoxelShape NORTH_SOUTH = Shapes.or(Block.box(1, 0, 6, 3, 16, 10), Block.box(13, 0, 6, 15, 16, 10), Block.box(0, 6, 7, 16, 16, 9));
	private static final VoxelShape EAST_WEST = Shapes.or(Block.box(6, 0, 1, 10, 16, 3), Block.box(6, 0, 13, 10, 16, 15), Block.box(7, 6, 0, 9, 16, 16));

	public NoticeBoardBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	// a block has no codec of its own from 26.3
	//? if <26.3 {
	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}
	//?}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** Set up in a town, it is the town's board. */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.@Nullable LivingEntity placer, ItemStack stack) {
		if (level instanceof ServerLevel server) NoticeBoard.adopt(server, pos);
	}

	/** The notices face the one who sets it. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH : EAST_WEST;
	}

	@Override
	//? if >=1.21.2 {
	/*protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
	*///?} else
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
											  BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) return com.vnap.Mc.itemSuccess(true);
		boolean done = Coins.isCoin(stack) ? NoticeBoard.donate(server, player, pos, stack) : NoticeBoard.deliver(server, player, pos, stack);
		if (done) return com.vnap.Mc.itemSuccess(false);
		return com.vnap.Mc.passToBlock();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && !NoticeBoard.tooSoon(server, serverPlayer)) {
			NoticeBoard.show(server, serverPlayer, pos);
		}
		return com.vnap.Mc.success(level.isClientSide());
	}
}
