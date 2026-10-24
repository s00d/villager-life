package com.vnap.block;

import com.mojang.serialization.MapCodec;
import com.vnap.VillagerLife;
import com.vnap.block.decor.DecorBlockEntity;
import com.vnap.block.decor.DecorMechanic;
import com.vnap.block.decor.DecorMechanics;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <1.21.2
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Profession workshop decor and machines: a north-facing model turned by FACING, with the outline turned the
 * same way. FILL picks how full it looks (0 is the plain decor look); the {@link DecorMechanic} does the work.
 */
public final class DecorBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, EntityBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 4);

    private final String name;
    private final String profession;
    private final double[] box;
    private final boolean smoke;
    private final boolean machine;
    private final VoxelShape[] shapes = new VoxelShape[4];
    private @Nullable DecorMechanic mechanic;

    public DecorBlock(Properties properties, String name, String profession, double[] box, boolean smoke, boolean machine) {
        super(properties);
        this.name = name;
        this.profession = profession;
        this.box = box.clone();
        this.smoke = smoke;
        this.machine = machine;
        double x0 = box[0], y0 = box[1], z0 = box[2], x1 = box[3], y1 = box[4], z1 = box[5];
        shapes[Direction.NORTH.get2DDataValue()] = Block.box(x0, y0, z0, x1, y1, z1);
        shapes[Direction.EAST.get2DDataValue()] = Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
        shapes[Direction.SOUTH.get2DDataValue()] = Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
        shapes[Direction.WEST.get2DDataValue()] = Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false).setValue(FILL, 0));
    }

    public String name() {
        return name;
    }

    public String profession() {
        return profession;
    }

    public boolean isMachine() {
        return machine;
    }

    public DecorMechanic mechanic() {
        DecorMechanic known = mechanic;
        if (known == null) {
            known = DecorMechanics.get(name);
            mechanic = known;
        }
        return known;
    }

    // a block has no codec of its own from 26.3
    //? if <26.3 {
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new DecorBlock(p, name, profession, box, smoke, machine));
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, FILL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER);
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, water);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(FACING).get2DDataValue()];
    }

    /** The stall's counter stands as high as a fence for walking: villagers go round it, not over it. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return name().equals(com.vnap.village.market.Shops.COUNTER) ? COUNTER_FENCE : super.getCollisionShape(state, level, pos, context);
    }

    private static final VoxelShape COUNTER_FENCE = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 24, 16);

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    //? if >=1.21.2 {
    /*@Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks,
                                     BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighbor, net.minecraft.util.RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }
    *///?} else {
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
    //?}

    // ---------------------------------------------------------------- mechanics

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DecorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        DecorMechanic work = mechanic();
        if (level.isClientSide() || !LifeBlocks.DECOR_BE.equals(type) || (!work.ticks() && work.machine() == null)) {
            return null;
        }
        return (l, p, s, be) -> DecorBlockEntity.tick(l, p, s, (DecorBlockEntity) be);
    }

    @Override
    //? if >=1.21.2 {
    /*protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
    *///?} else
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DecorBlockEntity be) || !mechanic().handles(be, player, stack)) {
            return com.vnap.Mc.passToBlock();
        }
        if (level instanceof ServerLevel server) {
            mechanic().use(server, be, player, hand, stack);
        }
        return com.vnap.Mc.itemSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DecorBlockEntity be) || !mechanic().handles(be, player, ItemStack.EMPTY)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            mechanic().use(server, be, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        return com.vnap.Mc.success(level.isClientSide());
    }

    // from 1.21.5 the block entity drops what it holds (DecorBlockEntity.preRemoveSideEffects)
    //? if <1.21.5 {
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DecorBlockEntity be) {
            be.dropAll(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
    //?}

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    //? if >=1.21.11 {
    /*protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, net.minecraft.core.Direction direction) {
    *///?} else
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DecorBlockEntity be ? Math.max(0, mechanic().signal(be)) : 0;
    }

    @Override
    //? if >=26.1 {
    /*public float getEnchantPowerBonus(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
    *///?} else
    public float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) {
        return name.equals("book_stack") && state.getValue(FILL) > 0 ? 1.0F : 0.0F;
    }

    // ---------------------------------------------------------------- looks

    /** Whose decor or machine it is (the item's tooltip; from 1.21.5 VillagerLifeClient adds it, blocks have no tooltip of their own). */
    public Component tooltip() {
        Component kind = profession.equals("home") || profession.equals("town")
                ? Component.translatable("tooltip." + VillagerLife.MOD_ID + "." + profession + "_decor")
                : Component.translatable("tooltip." + VillagerLife.MOD_ID + (machine ? ".machine" : ".decor"),
                        Component.translatable("entity.minecraft.villager." + profession));
        return kind.copy().withStyle(ChatFormatting.DARK_GRAY);
    }

    //? if <1.21.5 {
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(tooltip());
    }
    //?}

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (name.equals("cooking_pot") && state.getValue(FILL) > 0) {
            // steam off the simmering pot
            level.addParticle(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.35 + random.nextDouble() * 0.3, pos.getY() + 0.75,
                    pos.getZ() + 0.35 + random.nextDouble() * 0.3, 0.0, 0.04, 0.0);
            return;
        }
        if (!smoke || state.getValue(WATERLOGGED) || state.getValue(FILL) == 0 || random.nextInt(3) != 0) {
            return;
        }
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.15;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.15;
        level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + box[4] / 16.0, z, 0.0, 0.03, 0.0);
    }
}
