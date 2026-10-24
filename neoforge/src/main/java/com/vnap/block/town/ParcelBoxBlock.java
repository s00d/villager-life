package com.vnap.block.town;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A player's parcel box (ParcelBoxBlockEntity): set down by a stall's keeper when the caravan brings what the player
 * ordered there. Its owner opens it; anyone else is told whose it is and cannot break it.
 */
public final class ParcelBoxBlock extends BaseEntityBlock {
	//? if <26.3
	public static final MapCodec<ParcelBoxBlock> CODEC = simpleCodec(ParcelBoxBlock::new);

	public ParcelBoxBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
	}

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
		return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
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
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof ParcelBoxBlockEntity box)) return com.vnap.Mc.success(level.isClientSide());
		if (box.owner != null && !box.owner.equals(player.getUUID())) {
			com.vnap.Mc.say(player, Component.translatable("block.villager_life.parcel_box.not_yours", box.ownerName), true);
			return com.vnap.Mc.success(false);
		}
		player.openMenu(new SimpleMenuProvider((id, inventory, who) -> new net.minecraft.world.inventory.ChestMenu(com.vnap.block.LifeBlocks.PARCEL_MENU, id, inventory, box, 6),
				Component.translatable("block.villager_life.parcel_box.title", box.ownerName)));
		return com.vnap.Mc.success(false);
	}

	/** Only its owner (or a creative player) takes it apart. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof ParcelBoxBlockEntity box && box.owner != null && !box.owner.equals(player.getUUID()) && !player.isCreative()) return 0.0F;
		return super.getDestroyProgress(state, player, level, pos);
	}

	//? if <1.21.5 {
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ParcelBoxBlockEntity box) {
			net.minecraft.world.Containers.dropContents(level, pos, box);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
	//?}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ParcelBoxBlockEntity(pos, state);
	}
}
