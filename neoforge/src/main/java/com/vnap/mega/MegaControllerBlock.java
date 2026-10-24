package com.vnap.mega;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A megastructure's controller: it faces the one who set it down, looks every few seconds whether the structure round
 * it stands as its plan says (Shape), lights up when it does and works (MegaControllerBlockEntity). A click tells how
 * it stands, or which block is wrong and where; a Waygate takes its keystone in by a click with one.
 */
public final class MegaControllerBlock extends BaseEntityBlock {
	public static final BooleanProperty FORMED = BooleanProperty.create("formed");
	public final MegaKind kind;

	public MegaControllerBlock(Properties properties, MegaKind kind) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(FORMED, false));
	}

	// a block has no codec of its own from 26.3
	//? if <26.3 {
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return simpleCodec(properties -> new MegaControllerBlock(properties, kind));
	}
	//?}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HorizontalDirectionalBlock.FACING, FORMED);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
	}

	/** Its owner: who set it down (its town pays them for what it takes of its work: TownWork). */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.@Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (placer instanceof Player player && level.getBlockEntity(pos) instanceof MegaControllerBlockEntity controller) {
			controller.owner = player.getUUID();
			controller.setChanged();
		}
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MegaControllerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, MegaBlocks.CONTROLLER_BE, MegaControllerBlockEntity::tick);
	}

	@Override
	//? if >=1.21.2 {
	/*protected net.minecraft.world.InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
	*///?} else
	protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (kind != MegaKind.GATE || MegaBlocks.colourOf(stack) == null) return com.vnap.Mc.passToBlock();
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MegaControllerBlockEntity controller) controller.key(player, stack);
		return com.vnap.Mc.itemSuccess(level.isClientSide());
	}

	@Override
	protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MegaControllerBlockEntity controller) controller.tell(player);
		return com.vnap.Mc.success(level.isClientSide());
	}

	// from 1.21.5 the block entity does it itself (MegaControllerBlockEntity.preRemoveSideEffects)
	//? if <1.21.5 {
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
		if (!state.is(now.getBlock()) && level.getBlockEntity(pos) instanceof MegaControllerBlockEntity controller) controller.broken();
		super.onRemove(state, level, pos, now, moved);
	}
	//?}
}
