package com.vnap.block.turret;

import com.mojang.serialization.MapCodec;
import com.vnap.block.LifeBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <1.21.2
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A wall crossbow on a stone mount: it shoots arrows from its store at monsters it can see within range. A town's
 * towers carry them (Wall); a villager keeps them loaded (TurretBehavior); a player can load one by hand with
 * arrows, a hopper can feed it.
 */
public final class ArrowTurretBlock extends BaseEntityBlock {
	//? if <26.3
	public static final MapCodec<ArrowTurretBlock> CODEC = simpleCodec(ArrowTurretBlock::new);
	private static final VoxelShape SHAPE = box(1, 0, 1, 15, 12, 15);

	public ArrowTurretBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
	}

	// a block has no codec of its own from 26.3
	//? if <26.3 {
	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
	//?}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HorizontalDirectionalBlock.FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection());
	}

	/** Turned with the plan it is part of (a structure, BuildJob): BaseEntityBlock leaves a facing as it was. */
	@Override
	protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
		return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
		return rotate(state, mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ArrowTurretBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, LifeBlocks.TURRET_BE, ArrowTurretBlockEntity::tick);
	}

	/** Arrows in hand load it; an empty hand asks how many it has. */
	@Override
	//? if >=1.21.2 {
	/*protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
	*///?} else
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
											  BlockHitResult hit) {
		if (!stack.is(Items.ARROW)) return com.vnap.Mc.passToBlock();
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ArrowTurretBlockEntity turret) {
			int before = stack.getCount();
			ItemStack left = turret.load(stack.copy());
			if (!player.getAbilities().instabuild) stack.setCount(left.getCount());
			if (left.getCount() == before) return com.vnap.Mc.itemSuccess(false);
		}
		return com.vnap.Mc.itemSuccess(level.isClientSide());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ArrowTurretBlockEntity turret) {
			com.vnap.Mc.say(player, Component.translatable("block.villager_life.arrow_turret.arrows", turret.arrows()), true);
		}
		return com.vnap.Mc.success(level.isClientSide());
	}

	// from 1.21.5 the block entity drops its arrows (ArrowTurretBlockEntity.preRemoveSideEffects)
	//? if <1.21.5 {
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ArrowTurretBlockEntity turret) {
			Containers.dropContents(level, pos, turret);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
	//?}
}
