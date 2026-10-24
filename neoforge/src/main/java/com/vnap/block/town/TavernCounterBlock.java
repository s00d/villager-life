package com.vnap.block.town;

import com.mojang.serialization.MapCodec;
import com.vnap.item.LifeItems;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Coins;
import com.vnap.village.market.Market;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The tavern's bar (TownPlots TAVERN): a player buys a home-cooked meal across it for a few coppers, which go to the
 * town's treasury. A fence-high collision like the stall's counter: villagers walk round it, not over it.
 */
public final class TavernCounterBlock extends HorizontalDirectionalBlock {
	//? if <26.3
	public static final MapCodec<TavernCounterBlock> CODEC = simpleCodec(TavernCounterBlock::new);
	/** Copper a meal costs. */
	public static final int MEAL = 4;
	private static final VoxelShape FENCE = Block.box(0, 0, 0, 16, 24, 16);

	public TavernCounterBlock(Properties properties) {
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

	/** The customer's side faces the one who sets it. */
	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return FENCE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
		if (!Coins.pay(player, MEAL)) {
			com.vnap.Mc.say(player, Component.translatable("vnap.shop.no_money", Coins.text(MEAL)), true);
			return InteractionResult.CONSUME;
		}
		Mayors.Office office = Mayors.get(server.getServer()).officeNear(server.dimension(), pos);
		if (office != null) Market.deposit(server, office, MEAL);
		ItemStack meal = new ItemStack(LifeItems.HOME_MEAL);
		if (!player.getInventory().add(meal)) com.vnap.Mc.toss(player, meal);
		server.playSound(null, pos, com.vnap.sound.GolemSounds.SHOP_BUY, SoundSource.BLOCKS, 0.8F, 1.0F);
		com.vnap.Mc.say(player, Component.translatable("block.villager_life.tavern_counter.meal", Coins.text(MEAL)), true);
		return InteractionResult.CONSUME;
	}
}
