package com.vnap.block;

import com.mojang.serialization.MapCodec;
import com.vnap.mood.VillageMoodApi;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class MonumentPlaqueBlock extends BaseEntityBlock {
    //? if <26.3
    public static final MapCodec<MonumentPlaqueBlock> CODEC = simpleCodec(MonumentPlaqueBlock::new);

    public MonumentPlaqueBlock(Properties properties) {
        super(properties);
    }

    // a block has no codec of its own from 26.3
    //? if <26.3 {
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    //?}

    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 14, 15);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonumentBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, LifeBlocks.MONUMENT_BE, MonumentBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MonumentBlockEntity monument && player instanceof ServerPlayer serverPlayer) {
            for (String line : monument.plaqueLines()) {
                // a line is a lang key, its argument after '|' (a player's name)
                String[] parts = line.split("\\|", 2);
                serverPlayer.sendSystemMessage(parts.length > 1 ? Component.translatable(parts[0], parts[1]) : Component.translatable(line));
            }
            VillageMoodApi.applyAt((ServerLevel) level, pos, 1.0F, 0.0F, "read_plaque");
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
