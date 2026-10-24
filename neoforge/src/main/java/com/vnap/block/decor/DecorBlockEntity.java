package com.vnap.block.decor;

import com.vnap.Nbt;
import com.vnap.block.DecorBlock;
import com.vnap.block.LifeBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Items, stock, timers and machine work of one decor block; its {@link DecorMechanic} decides what they mean. */
public final class DecorBlockEntity extends BlockEntity implements MenuProvider {
    public final SimpleContainer items;
    final MachineWork work = new MachineWork();
    final int[] timers = new int[4];
    private @Nullable Item stockItem;
    private int stock;
    int fuel;
    long day = -1;
    private int clock;

    public DecorBlockEntity(BlockPos pos, BlockState state) {
        super(LifeBlocks.DECOR_BE, pos, state);
        // a container's listeners are gone from 26.1: its own setChanged instead
        items = new SimpleContainer(Math.max(1, mechanic().slots())) {
            @Override
            public void setChanged() {
                super.setChanged();
                changed();
            }
        };
    }

    public DecorMechanic mechanic() {
        return getBlockState().getBlock() instanceof DecorBlock block ? block.mechanic() : DecorMechanics.NONE;
    }

    public String name() {
        return getBlockState().getBlock() instanceof DecorBlock block ? block.name() : "";
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DecorBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        DecorMechanic mechanic = be.mechanic();
        Machine machine = mechanic.machine();
        if (machine != null) {
            int before = be.work.progress;
            if (be.work.tick(server, pos, machine, be.items)) {
                be.changed();
            } else if ((before == 0) != (be.work.progress == 0)) {
                be.refreshFill();
            }
            if (be.work.progress > 0) {
                be.setChanged();
            }
        }
        if (++be.clock % 20 == 0) {
            mechanic.tick(server, be);
        }
    }

    // ---------------------------------------------------------------- stock

    public int stock() {
        return stock;
    }

    public @Nullable Item stockItem() {
        return stockItem;
    }

    public boolean canStock(ItemStack stack) {
        DecorMechanic mechanic = mechanic();
        return !stack.isEmpty() && mechanic.stocks(stack) && stock < mechanic.stockCap()
                && (stockItem == null || stack.is(stockItem)) && stack.getComponentsPatch().isEmpty();
    }

    /** Moves as much of {@code stack} as fits into the stock; returns how many went in. */
    public int addStock(ItemStack stack) {
        if (!canStock(stack)) {
            return 0;
        }
        int moved = Math.min(stack.getCount(), mechanic().stockCap() - stock);
        stockItem = stack.getItem();
        stock += moved;
        stack.shrink(moved);
        changed();
        return moved;
    }

    /** Takes up to {@code count} items out of the stock. */
    public ItemStack takeStock(int count) {
        if (stockItem == null || stock <= 0) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(count, stock);
        ItemStack out = new ItemStack(stockItem, taken);
        stock -= taken;
        if (stock == 0) {
            stockItem = null;
        }
        changed();
        return out;
    }

    // ---------------------------------------------------------------- items

    public int filled() {
        int n = 0;
        for (int i = 0; i < items.getContainerSize(); i++) {
            if (!items.getItem(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            com.vnap.Mc.toss(player, stack);
        }
    }

    /** Drops every item and the whole stock where the block stood. */
    public void dropAll(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, items);
        while (stock > 0 && stockItem != null) {
            ItemStack out = takeStock(stockItem.getDefaultMaxStackSize());
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, out);
        }
    }

    // ---------------------------------------------------------------- sync

    /** Saves, sends the new contents to clients and updates the FILL look. */
    public void changed() {
        setChanged();
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            refreshFill();
        }
    }

    void refreshFill() {
        Level level = getLevel();
        BlockState state = getBlockState();
        if (level == null || level.isClientSide() || !state.hasProperty(DecorBlock.FILL)) {
            return;
        }
        int fill = Math.max(0, Math.min(4, mechanic().fill(this)));
        if (state.getValue(DecorBlock.FILL) != fill) {
            level.setBlock(getBlockPos(), state.setValue(DecorBlock.FILL, fill), 3);
        }
    }

    @Override
    //? if >=1.21.6 {
    /*protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        HolderLookup.Provider registries = com.vnap.Mc.registries(level);
    *///?} else {
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    //?}
        com.vnap.Mc.saveItems(tag, items.getItems(), registries);
        if (stockItem != null && stock > 0) {
            tag.putString("stock_item", BuiltInRegistries.ITEM.getKey(stockItem).toString());
            tag.putInt("stock", stock);
        }
        tag.putIntArray("timers", timers.clone());
        tag.putInt("fuel", fuel);
        tag.putLong("day", day);
        work.save(tag);
        //? if >=1.21.6
        /*com.vnap.Mc.put(output, tag);*/
    }

    @Override
    //? if >=1.21.6 {
    /*protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        CompoundTag tag = com.vnap.Mc.tagOf(input);
        HolderLookup.Provider registries = com.vnap.Mc.registries(level);
    *///?} else {
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    //?}
        items.getItems().replaceAll(stack -> ItemStack.EMPTY);
        com.vnap.Mc.loadItems(tag, items.getItems(), registries);
        stock = Nbt.getInt(tag, "stock");
        ResourceLocation id = ResourceLocation.tryParse(Nbt.getString(tag, "stock_item"));
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        stockItem = stock > 0 && !item.equals(Items.AIR) ? item : null;
        int[] saved = Nbt.getIntArray(tag, "timers");
        System.arraycopy(saved, 0, timers, 0, Math.min(saved.length, timers.length));
        fuel = Nbt.getInt(tag, "fuel");
        day = tag.contains("day") ? Nbt.getLong(tag, "day") : -1;
        work.load(tag);
    }

    //? if >=1.21.5 {
    /*@Override
    public void preRemoveSideEffects(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (level != null) dropAll(level, pos);
    }
    *///?}

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---------------------------------------------------------------- menu

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        Machine machine = mechanic().machine();
        Level level = getLevel();
        if (machine == null || level == null) {
            return null;
        }
        return new MachineMenu(id, inventory, machine, items, work, ContainerLevelAccess.create(level, getBlockPos()),
                p -> Container.stillValidBlockEntity(this, p), null);
    }
}
