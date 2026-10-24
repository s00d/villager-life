package com.vnap.village.market;

import com.vnap.item.LifeItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Village money a player carries: a full stack of one coin is one coin of the next kind (64 copper = 1 silver,
 * 64 silver = 1 gold), so a full stack turns up a step on its own ({@link #consolidate}: picked up, paid, every few
 * seconds in the inventory) and any wealth fits in a few slots. Every payment takes all the coins and gives the
 * change back in the biggest. Villagers keep their money as a number (FamilyState.coins), not as items.
 */
public final class Coins {
	public static final int SILVER = 64;
	public static final int GOLD = SILVER * SILVER;

	private Coins() {
	}

	/** Copper a coin stack is worth; 0 for anything else. */
	public static int value(ItemStack stack) {
		return worth(stack.getItem()) * stack.getCount();
	}

	private static int worth(Item item) {
		if (item.equals(LifeItems.COPPER_COIN)) return 1;
		if (item.equals(LifeItems.SILVER_COIN)) return SILVER;
		if (item.equals(LifeItems.GOLD_COIN)) return GOLD;
		return 0;
	}

	public static boolean isCoin(ItemStack stack) {
		return worth(stack.getItem()) > 0;
	}

	public static int count(Inventory inventory) {
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) total += value(inventory.getItem(i));
		return total;
	}

	/** Pays {@code amount}: false (nothing taken) when the player has less; the change comes back in big coins. */
	public static boolean pay(Player player, int amount) {
		Inventory inventory = player.getInventory();
		int total = count(inventory);
		if (amount < 0 || total < amount) return false;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (isCoin(inventory.getItem(i))) inventory.setItem(i, ItemStack.EMPTY);
		}
		give(player, total - amount);
		return true;
	}

	/** Gives {@code amount} in the fewest coins; what does not fit is dropped at the player's feet. */
	public static void give(Player player, int amount) {
		for (ItemStack stack : stacks(amount)) {
			if (!player.getInventory().add(stack)) com.vnap.Mc.toss(player, stack);
		}
	}

	/** Changes all of a player's coins into the fewest when a stack of copper or silver is full. */
	public static void consolidate(Player player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if ((stack.is(LifeItems.COPPER_COIN) || stack.is(LifeItems.SILVER_COIN)) && stack.getCount() >= SILVER) {
				pay(player, 0);
				return;
			}
		}
	}

	public static List<ItemStack> stacks(int amount) {
		List<ItemStack> out = new ArrayList<>();
		add(out, LifeItems.GOLD_COIN, amount / GOLD);
		add(out, LifeItems.SILVER_COIN, amount % GOLD / SILVER);
		add(out, LifeItems.COPPER_COIN, amount % SILVER);
		return out;
	}

	private static void add(List<ItemStack> out, Item coin, int count) {
		while (count > 0) {
			int now = Math.min(count, coin.getDefaultMaxStackSize());
			out.add(new ItemStack(coin, now));
			count -= now;
		}
	}

	/** "1g 2s 3c" (gold, silver, copper), in the player's language. */
	public static net.minecraft.network.chat.MutableComponent text(int amount) {
		net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.empty();
		boolean any = false;
		if (amount >= GOLD) {
			out.append(net.minecraft.network.chat.Component.translatable("vnap.coin.gold", amount / GOLD));
			any = true;
		}
		if (amount % GOLD >= SILVER) {
			if (any) out.append(" ");
			out.append(net.minecraft.network.chat.Component.translatable("vnap.coin.silver", amount % GOLD / SILVER));
			any = true;
		}
		if (amount % SILVER > 0 || !any) {
			if (any) out.append(" ");
			out.append(net.minecraft.network.chat.Component.translatable("vnap.coin.copper", amount % SILVER));
		}
		return out;
	}
}
