package com.vnap.village.market;

import com.vnap.Nbt;
import com.vnap.block.LifeBlocks;
import com.vnap.block.town.ParcelBoxBlockEntity;
import com.vnap.village.town.TownRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A player's orders at a stall: a sample shows what, the player pays for it at once and the next caravan brings it,
 * into the player's own parcel box by the stall (set down by the keeper if there is none). What can be had is what the
 * prices know (Prices: listed, by a tag, made by a recipe; never what nobody sells). An order is of the thing itself,
 * not of the sample's contents: a fresh item, with only a potion's kind or a book's enchantments kept (and paid for).
 */
public final class Orders {
	/** Over the worth: it comes from abroad. */
	static final double MARKUP = 1.5;
	/** The carriage: a tenth of the goods, at least this much. */
	static final int CARRIAGE_MIN = 4;
	/** Open orders a player may have at one stall. */
	public static final int MAX_ORDERS = 9;
	/** Stacks one order may be of (nine stacks: a parcel box holds six of those orders). */
	public static final int MAX_STACKS = 9;

	private Orders() {
	}

	/** One player's order. */
	public static final class Order {
		public final UUID player;
		public final String name;
		public final ItemStack item;
		public int count;
		public final int paid;
		/** The part of {@code paid} the stall keeps (the rest goes abroad with the caravan). */
		public final int carriage;
		public final long day;

		Order(UUID player, String name, ItemStack item, int count, int paid, int carriage, long day) {
			this.player = player;
			this.name = name;
			this.item = item.copyWithCount(1);
			this.count = count;
			this.paid = paid;
			this.carriage = carriage;
			this.day = day;
		}

		CompoundTag save(HolderLookup.Provider registries) {
			CompoundTag tag = new CompoundTag();
			Nbt.putUUID(tag, "Player", player);
			tag.putString("Name", name);
			tag.put("Item", com.vnap.Mc.saveStack(item, registries));
			tag.putInt("Count", count);
			tag.putInt("Paid", paid);
			tag.putInt("Carriage", carriage);
			tag.putLong("Day", day);
			return tag;
		}

		static @Nullable Order load(CompoundTag tag, HolderLookup.Provider registries) {
			ItemStack item = com.vnap.Mc.parseStackOrEmpty(registries, Nbt.getCompound(tag, "Item"));
			if (item.isEmpty() || !Nbt.hasUUID(tag, "Player")) return null;
			return new Order(Nbt.getUUID(tag, "Player"), Nbt.getString(tag, "Name"), item, Nbt.getInt(tag, "Count"), Nbt.getInt(tag, "Paid"), Nbt.getInt(tag, "Carriage"), Nbt.getLong(tag, "Day"));
		}
	}

	/** What an order of this sample is of: the item fresh, only a potion's kind or a book's enchantments kept. */
	public static ItemStack sample(ItemStack shown) {
		if (shown.isEmpty()) return ItemStack.EMPTY;
		ItemStack fresh = new ItemStack(shown.getItem());
		var potion = shown.get(DataComponents.POTION_CONTENTS);
		if (potion != null) fresh.set(DataComponents.POTION_CONTENTS, potion);
		var stored = shown.get(DataComponents.STORED_ENCHANTMENTS);
		if (stored != null) fresh.set(DataComponents.STORED_ENCHANTMENTS, stored);
		return fresh;
	}

	/** What one of it costs ordered at this counter (0: not to be had), the village's mood turning it as a purchase. */
	public static int unit(ServerLevel level, BlockPos counter, ItemStack sample) {
		if (sample.isEmpty() || Coins.isCoin(sample)) return 0;
		double worth = Prices.worth(sample);
		if (worth <= 0.0) return 0;
		return Math.max(1, (int) Math.ceil(worth * MARKUP * (1.0 - Prices.moodFactor(ShopMenu.mood(level, counter)))));
	}

	/** The whole price of {@code count} at {@code unit} each: the goods and their carriage. */
	public static int total(int unit, int count) {
		int goods = unit * count;
		return goods + Math.max(CARRIAGE_MIN, (int) Math.ceil(goods * 0.1));
	}

	public static int most(ItemStack sample) {
		return MAX_STACKS * sample.getMaxStackSize();
	}

	/** Days until the caravan comes to this stall (it brings the orders): 1 at least. */
	public static int days(ServerLevel level, Shops.Shop shop) {
		long day = com.vnap.dialogue.Family.day(level);
		return (int) Math.max(1, TownRules.CARAVAN_DAYS - (day - shop.caravanDay));
	}

	/** The player's open orders here. */
	public static java.util.List<Order> of(Shops.Shop shop, UUID player) {
		return shop.playerOrders.stream().filter(order -> order.player.equals(player)).toList();
	}

	/** A player orders {@code count} of the sample, paying now; false (and a word why) when it cannot be. */
	public static boolean place(ServerLevel level, Shops.Shop shop, Player player, ItemStack shown, int count, BlockPos counter) {
		ItemStack sample = sample(shown);
		int unit = unit(level, counter, sample);
		if (unit <= 0) {
			com.vnap.Mc.say(player, Component.translatable("vnap.shop.order.cannot"), true);
			return false;
		}
		if (count <= 0 || count > most(sample)) return false;
		if (of(shop, player.getUUID()).size() >= MAX_ORDERS) {
			com.vnap.Mc.say(player, Component.translatable("vnap.shop.order.too_many", MAX_ORDERS), true);
			return false;
		}
		int cost = total(unit, count);
		if (!Coins.pay(player, cost)) {
			com.vnap.Mc.say(player, Component.translatable("vnap.shop.no_money", Coins.text(cost)), true);
			return false;
		}
		// the money goes abroad with the caravan: the stall keeps only its carriage
		int carriage = cost - unit * count;
		shop.till += carriage;
		shop.playerOrders.add(new Order(player.getUUID(), player.getName().getString(), sample, count, cost, carriage, com.vnap.dialogue.Family.day(level)));
		Shops.get(level.getServer()).setDirty();
		level.playSound(null, counter, com.vnap.sound.GolemSounds.SHOP_BUY, SoundSource.BLOCKS, 0.8F, 0.8F);
		com.vnap.village.town.Chronicle.note(level, shop.bell, "order_placed", player.getName().getString(), String.valueOf(count),
				"@" + sample.getItem().getDescriptionId(), String.valueOf(days(level, shop)));
		com.vnap.Mc.say(player, Component.translatable("vnap.shop.order.placed", count, sample.getHoverName(), days(level, shop)), false);
		return true;
	}

	/** The player takes back an order not yet come: all of the money back. */
	public static boolean cancel(ServerLevel level, Shops.Shop shop, Player player, int index) {
		java.util.List<Order> mine = of(shop, player.getUUID());
		if (index < 0 || index >= mine.size()) return false;
		Order order = mine.get(index);
		shop.playerOrders.remove(order);
		shop.till -= Math.min(shop.till, order.carriage);
		Coins.give(player, order.paid);
		Shops.get(level.getServer()).setDirty();
		com.vnap.Mc.say(player, Component.translatable("vnap.shop.order.cancelled", order.item.getHoverName(), Coins.text(order.paid)), true);
		return true;
	}

	/**
	 * The caravan is in: each order into its player's parcel box (set down by the stall first if there is none);
	 * what does not fit waits for the next caravan, as does all of it when the stall's ground is not loaded.
	 */
	static void deliver(ServerLevel level, Shops.Shop shop) {
		BlockPos counter = shop.counter;
		if (counter == null || shop.playerOrders.isEmpty()) return;
		for (Order order : java.util.List.copyOf(shop.playerOrders)) {
			ParcelBoxBlockEntity box = box(level, shop, order.player, order.name, counter);
			if (box == null) continue;
			int before = order.count;
			while (order.count > 0) {
				int batch = Math.min(order.count, order.item.getMaxStackSize());
				ItemStack rest = box.put(order.item.copyWithCount(batch));
				order.count -= batch - rest.getCount();
				if (!rest.isEmpty()) break;
			}
			int came = before - order.count;
			if (order.count <= 0) shop.playerOrders.remove(order);
			if (came <= 0) continue;
			com.vnap.village.town.Chronicle.note(level, shop.bell, "order_arrived", order.name, String.valueOf(came), "@" + order.item.getItem().getDescriptionId());
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(order.player);
			if (player != null) {
				com.vnap.Mc.say(player, Component.translatable(order.count > 0 ? "vnap.shop.order.part" : "vnap.shop.order.arrived", came, order.item.getHoverName(),
						shop.name), false);
			}
		}
		Shops.get(level.getServer()).setDirty();
	}

	/** The player's parcel box by the stall, set down now if there is none (null: no room, or the ground not loaded). */
	static @Nullable ParcelBoxBlockEntity box(ServerLevel level, Shops.Shop shop, UUID player, String name, BlockPos counter) {
		BlockPos known = shop.parcels.get(player);
		if (known != null && level.isLoaded(known) && level.getBlockEntity(known) instanceof ParcelBoxBlockEntity box && player.equals(box.owner)) return box;
		if (!level.isLoaded(counter)) return null;
		BlockPos spot = spot(level, counter);
		if (spot == null) return null;
		Direction face = Direction.getNearest(counter.getX() - spot.getX(), 0, counter.getZ() - spot.getZ());
		if (face.getAxis().isVertical()) face = Direction.NORTH;
		level.setBlock(spot, LifeBlocks.PARCEL_BOX.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, face), 3);
		if (!(level.getBlockEntity(spot) instanceof ParcelBoxBlockEntity box)) return null;
		box.owner = player;
		box.ownerName = name;
		box.setChanged();
		shop.parcels.put(player, spot);
		level.playSound(null, spot, net.minecraft.sounds.SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
		var keeper = Shops.keeper(level, shop);
		if (keeper != null) com.vnap.Mc.swing(keeper, net.minecraft.world.InteractionHand.MAIN_HAND);
		return box;
	}

	/** Free floor by the counter (not a road's cell, open above): nearest first. */
	private static @Nullable BlockPos spot(ServerLevel level, BlockPos counter) {
		com.vnap.village.town.Roads.Network network = com.vnap.village.town.Roads.get(level.getServer()).near(level.dimension(), counter);
		for (int r = 2; r <= 6; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					for (int dy = -1; dy <= 1; dy++) {
						BlockPos at = counter.offset(dx, dy, dz);
						if (!level.isLoaded(at) || !level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) continue;
						var below = level.getBlockState(at.below());
						if (!below.isFaceSturdy(level, at.below(), Direction.UP) || below.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)) continue;
						if (network != null && (network.cells.contains(at.below()) || network.cells.contains(at))) continue;
						return at;
					}
				}
			}
		}
		return null;
	}
}
