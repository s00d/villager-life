package com.vnap.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class AltarCoreBlock extends BaseEntityBlock {
    //? if <26.3
    public static final MapCodec<AltarCoreBlock> CODEC = simpleCodec(AltarCoreBlock::new);
    /** Forge heat: 0 cold, 1 embers, 2 glowing, 3 white-hot. */
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 3);
    static final int[] LIGHT = {2, 7, 11, 15};

    public AltarCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HEAT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEAT);
    }

    public static void setHeat(Level level, BlockPos pos, int heat) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AltarCoreBlock && state.getValue(HEAT) != heat) {
            level.setBlock(pos, state.setValue(HEAT, Math.max(0, Math.min(3, heat))), Block.UPDATE_ALL);
        }
    }

    // a block has no codec of its own from 26.3
    //? if <26.3 {
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    //?}

    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 16, 15);

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
        return new AltarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, LifeBlocks.ALTAR_BE, AltarBlockEntity::tick);
    }
}
