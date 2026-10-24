package com.vnap.block.decor;

import com.vnap.block.LifeBlocks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/** The shared menu of every machine: inputs in a row, an arrow, the output, then the player's inventory. */
public final class MachineMenu extends AbstractContainerMenu {
    public static final int INPUT_X = 26;
    public static final int ROW_Y = 35;
    public static final int OUTPUT_X = 124;
    public static final int ARROW_X = 88;

    private final Machine machine;
    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final Predicate<Player> valid;
    private final @Nullable MachineWork running;

    /** Client side: the machine id comes in the open packet. */
    public MachineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, machine(buf.readUtf()), null, new SimpleContainerData(3), ContainerLevelAccess.NULL, p -> true, null);
    }

    /**
     * @param running work ticked by the menu itself (the fletching table has no block entity), or null when a
     *                block entity ticks it
     */
    public MachineMenu(int id, Inventory inventory, Machine machine, @Nullable Container container, ContainerData data,
                       ContainerLevelAccess access, Predicate<Player> valid, @Nullable MachineWork running) {
        super(LifeBlocks.MACHINE_MENU, id);
        this.machine = machine;
        this.container = container == null ? new SimpleContainer(machine.inputs() + 1) : container;
        this.data = data;
        this.access = access;
        this.valid = valid;
        this.running = running;
        int inputs = machine.inputs();
        for (int i = 0; i < inputs; i++) {
            int slot = i;
            addSlot(new Slot(this.container, i, INPUT_X + i * 18, ROW_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return machine.accepts(slot, stack);
                }
            });
        }
        addSlot(new Slot(this.container, inputs, OUTPUT_X, ROW_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
    }

    private static Machine machine(String id) {
        Machine machine = Machines.get(id);
        if (machine == null) {
            throw new IllegalArgumentException("Unknown machine " + id);
        }
        return machine;
    }

    public Machine machine() {
        return machine;
    }

    public int progress() {
        return data.get(0);
    }

    public int total() {
        return data.get(1);
    }

    public int fuel() {
        return data.get(2);
    }

    @Override
    public void broadcastChanges() {
        if (running != null) {
            access.execute((level, pos) -> {
                if (level instanceof ServerLevel server) {
                    running.tick(server, pos, machine, container);
                }
            });
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (running == null) {
            return;
        }
        boolean gone = !player.isAlive() || (player instanceof ServerPlayer server && server.hasDisconnected());
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.removeItemNoUpdate(i);
            if (gone) {
                com.vnap.Mc.toss(player, stack);
            } else {
                com.vnap.Mc.placeBack(player.getInventory(), stack);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return valid.test(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int machineSlots = machine.inputs() + 1;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = 0; i < machine.inputs() && !moved; i++) {
                if (machine.accepts(i, stack)) {
                    moved = moveItemStackTo(stack, i, i + 1, false);
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return copy;
    }
}
