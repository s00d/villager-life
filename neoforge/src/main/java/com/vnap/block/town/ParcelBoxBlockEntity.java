package com.vnap.block.town;

import com.vnap.block.LifeBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A player's parcel box by a stall (village/market/Orders): what the caravan brought of their orders, for them alone
 * to take out. Whose it is and their name (for the word to whoever else tries it) are kept with it.
 */
public final class ParcelBoxBlockEntity extends BlockEntity implements Container {
	public static final int SLOTS = 54;
	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
	public @Nullable UUID owner;
	public String ownerName = "";

	public ParcelBoxBlockEntity(BlockPos pos, BlockState state) {
		super(LifeBlocks.PARCEL_BOX_BE, pos, state);
	}

	/** As much of the stack as fits (stacking onto what lies there first); what does not. */
	public ItemStack put(ItemStack stack) {
		ItemStack rest = stack.copy();
		for (int i = 0; i < SLOTS && !rest.isEmpty(); i++) {
			ItemStack there = items.get(i);
			if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, rest) && there.getCount() < there.getMaxStackSize()) {
				int moved = Math.min(rest.getCount(), there.getMaxStackSize() - there.getCount());
				there.grow(moved);
				rest.shrink(moved);
			}
		}
		for (int i = 0; i < SLOTS && !rest.isEmpty(); i++) {
			if (items.get(i).isEmpty()) items.set(i, rest.split(Math.min(rest.getCount(), rest.getMaxStackSize())));
		}
		setChanged();
		return rest;
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
		items.clear();
		com.vnap.Mc.loadItems(tag, items, registries);
		owner = com.vnap.Nbt.hasUUID(tag, "Owner") ? com.vnap.Nbt.getUUID(tag, "Owner") : null;
		ownerName = com.vnap.Nbt.getString(tag, "OwnerName");
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
		com.vnap.Mc.saveItems(tag, items, registries);
		if (owner != null) com.vnap.Nbt.putUUID(tag, "Owner", owner);
		tag.putString("OwnerName", ownerName);
		//? if >=1.21.6
		/*com.vnap.Mc.put(output, tag);*/
	}

	//? if >=1.21.5 {
	/*@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) net.minecraft.world.Containers.dropContents(level, pos, this);
	}
	*///?}

	@Override
	public int getContainerSize() {
		return SLOTS;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) if (!stack.isEmpty()) return false;
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack out = ContainerHelper.removeItem(items, slot, amount);
		if (!out.isEmpty()) setChanged();
		return out;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		setChanged();
	}

	/** Nothing goes in by hand or by hopper: a parcel box is filled by the caravan only. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return false;
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player) && (owner == null || owner.equals(player.getUUID()));
	}

	@Override
	public void clearContent() {
		items.clear();
	}
}
