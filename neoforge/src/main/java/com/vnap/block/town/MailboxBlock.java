package com.vnap.block.town;

import com.mojang.serialization.MapCodec;
import com.vnap.village.post.Post;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A player's mailbox (Post): the villagers' letters wait in it, its flag up while there are some; the owner takes them out. */
public final class MailboxBlock extends HorizontalDirectionalBlock {
	//? if <26.3
	public static final MapCodec<MailboxBlock> CODEC = simpleCodec(MailboxBlock::new);
	public static final BooleanProperty FLAG = BooleanProperty.create("flag");
	private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 15, 12);

	public MailboxBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FLAG, false));
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
		builder.add(FACING, FLAG);
	}

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
		return SHAPE;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel server && placer instanceof Player player) {
			Post.own(server, player, pos);
			com.vnap.Mc.say(player, Component.translatable("post.villager_life.yours"), true);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
		if (!Post.owns(server, player, pos)) {
			com.vnap.Mc.say(player, Component.translatable("post.villager_life.not_yours"), true);
		} else if (!Post.collect(server, player, pos)) {
			com.vnap.Mc.say(player, Component.translatable("post.villager_life.empty"), true);
		} else {
			com.vnap.progress.VillageAdvancements.award(player, "town/letter");
		}
		return InteractionResult.CONSUME;
	}
}
