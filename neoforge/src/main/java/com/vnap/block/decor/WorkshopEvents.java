package com.vnap.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;


/** Game events behind the workshop: whetted blades, the tackle crate, the fletching table and the town's doors and benches. */
public final class WorkshopEvents {
    /** Extra catch chance with a tackle crate within {@link #CRATE_RADIUS}. */
    public static final float CRATE_CHANCE = 0.15F;
    static final int CRATE_RADIUS = 8;

    private WorkshopEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Sharpening::onDamage);
        NeoForge.EVENT_BUS.addListener(Sharpening::onTooltip);
        NeoForge.EVENT_BUS.addListener(WorkshopEvents::onFished);
        NeoForge.EVENT_BUS.addListener(WorkshopEvents::onUseBlock);
        NeoForge.EVENT_BUS.addListener(WorkshopEvents::onTownBlock);
    }

    // ---------------------------------------------------------------- fishing

    private static void onFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        if (!(com.vnap.Mc.levelOf(player) instanceof ServerLevel level) || event.getDrops().isEmpty()) {
            return;
        }
        if (level.getRandom().nextFloat() < CRATE_CHANCE && tackleCrateNear(level, player.blockPosition())) {
            DecorBlockEntity.give(player, event.getDrops().get(0).copy());
        }
    }

    static boolean tackleCrateNear(ServerLevel level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-CRATE_RADIUS, -4, -CRATE_RADIUS), pos.offset(CRATE_RADIUS, 4, CRATE_RADIUS))) {
            if (level.getBlockEntity(p) instanceof DecorBlockEntity be && be.name().equals("tackle_crate")) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- the town's doors and benches

    /** A player opens a door under a door bell (it rings, the keeper turns), or sits on a bench with an empty hand. */
    private static void onTownBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(event.getPos());
        Player player = event.getEntity();
        if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock && !player.isSecondaryUseActive()) {
            BlockPos lower = state.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    ? event.getPos() : event.getPos().below();
            if (!state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) TownDecor.doorOpened(level, player, lower);
        } else if (com.vnap.entity.Seat.sit(level, player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    // ---------------------------------------------------------------- fletching table

    private static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        Level level = event.getLevel();
        if (!level.getBlockState(pos).is(Blocks.FLETCHING_TABLE) || (player.isSecondaryUseActive() && !player.getMainHandItem().isEmpty())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(com.vnap.Mc.success(level.isClientSide()));
        if (player instanceof ServerPlayer server) {
            openFletching(server, pos);
        }
    }

    public static void openFletching(ServerPlayer player, BlockPos pos) {
        Machine machine = Machines.FLETCHING;
        ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), pos);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> {
            MachineWork work = new MachineWork();
            return new MachineMenu(id, inventory, machine, new SimpleContainer(machine.inputs() + 1), work, access,
                    q -> q.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0
                            && q.level().getBlockState(pos).is(Blocks.FLETCHING_TABLE), work);
        }, Blocks.FLETCHING_TABLE.getName()), buf -> buf.writeUtf(machine.id()));
    }
}
