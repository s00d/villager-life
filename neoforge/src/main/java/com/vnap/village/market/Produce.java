package com.vnap.village.market;

import com.vnap.village.workshop.WorkshopState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a workshop sells at the stall: everything in its chest the stall deals in beyond what the trade keeps (its
 * supplies, food for the village's pantries, wheat for the herds, the farm's seed). A chest that only filled up
 * stopped its trade for good; now the surplus is the worker's earnings.
 */
public final class Produce {
	/** A trip to the stall is worth it from this much in copper. */
	static final int WORTH_A_TRIP = 12;

	/** What a trade's chores use up beyond its bought supplies: the feathers it gathers, the flesh it fetches. */
	private static final Map<String, java.util.Set<Item>> INPUTS = Map.of(
			"farmer", java.util.Set.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.CARROT, Items.POTATO),
			"fletcher", java.util.Set.of(Items.FEATHER),
			"leatherworker", java.util.Set.of(Items.ROTTEN_FLESH),
			"mason", java.util.Set.of(Items.STONE));
	/** Inputs a trade keeps: enough for days of work, the rest is sold. */
	static final int INPUT_STOCK = 32;

	private Produce() {
	}

	/** How many of this item the trade keeps in its chest. */
	static int keep(ServerLevel level, WorkshopState state, ItemStack one) {
		Integer supply = Market.SUPPLIES.getOrDefault(state.profession, Map.of()).get(one.getItem());
		if (supply != null) return supply;
		// a damaged tool waits for the toolsmith's whetstone; dyes colour the shepherd's sheep
		if (one.isDamaged() || one.is(net.neoforged.neoforge.common.Tags.Items.DYES)) return Integer.MAX_VALUE;
		if (INPUTS.getOrDefault(state.profession, java.util.Set.of()).contains(one.getItem())) return INPUT_STOCK;
		// a tool it was given to work with (the farmer's hoe) is kept; the weaponsmith's swords are its goods
		if (one.has(DataComponents.MAX_DAMAGE) && !state.profession.equals("weaponsmith")) return 1;
		if (one.is(ItemTags.SAPLINGS)) {
			Farm.Plot farm = Farm.of(level, state);
			return farm == null ? 0 : 2 * Farm.treeSpots(farm).size();
		}
		if (one.is(Items.WHEAT)) return 16;
		// the leatherworker takes its hides from the butcher's chest
		if (state.profession.equals("butcher") && (one.is(Items.ROTTEN_FLESH) || one.is(Items.RABBIT_HIDE))) return 8;
		if (one.has(DataComponents.FOOD)) return 16;
		return 0;
	}

	/** What the chest has for sale: item to count. */
	public static Map<Item, Integer> forSale(ServerLevel level, WorkshopState state, Container store) {
		Map<Item, Integer> all = new LinkedHashMap<>();
		Map<Item, ItemStack> sample = new LinkedHashMap<>();
		for (int i = 0; i < store.getContainerSize(); i++) {
			ItemStack stack = store.getItem(i);
			if (stack.isEmpty() || stack.isDamaged() || Prices.price(stack) <= 0) continue;
			all.merge(stack.getItem(), stack.getCount(), Integer::sum);
			sample.putIfAbsent(stack.getItem(), stack);
		}
		Map<Item, Integer> sale = new LinkedHashMap<>();
		all.forEach((item, count) -> {
			int keep = keep(level, state, sample.getOrDefault(item, new ItemStack(item)));
			if (count > keep) sale.put(item, count - keep);
		});
		return sale;
	}

	/**
	 * Is a trip with {@code carry} items of it at most worth it, counting only what the stall has room for (a cheap
	 * crop by the armful is not: by the cartload it is)?
	 */
	public static boolean worthSelling(Shops.Shop shop, Map<Item, Integer> sale, int carry) {
		double worth = 0.0;
		int left = carry;
		for (Map.Entry<Item, Integer> entry : sale.entrySet()) {
			int n = Math.min(Math.min(entry.getValue(), left), Math.max(0, room(shop, entry.getKey())));
			worth += Prices.worth(new ItemStack(entry.getKey())) * n;
			left -= n;
		}
		return worth * Market.WHOLESALE >= WORTH_A_TRIP;
	}

	/** What a worker carries to the stall in its arms in one trip. */
	public static final int HAND = 16;
	/** What a hand cart (entity.HandCart) takes in one trip. */
	public static final int CART = 128;

	/**
	 * The worker sells at the stall what it carries ({@code carry} items at most, the rest waits in the chest): it is
	 * paid the wholesale price (into its household's purse), the goods go on the shelf. The stall takes no more of a
	 * good than four times its target (the rest waits in the chest).
	 */
	public static int sell(ServerLevel level, Villager seller, Shops.Shop shop, WorkshopState state, Container store, int carry) {
		return sell(level, seller, shop, forSale(level, state, store), store, carry);
	}

	/** The cart's load at the stall: all of it is for sale; what the stall has no room for rides home in it. */
	public static int sellLoad(ServerLevel level, Villager seller, Shops.Shop shop, Container load) {
		Map<Item, Integer> all = new LinkedHashMap<>();
		for (int i = 0; i < load.getContainerSize(); i++) {
			ItemStack stack = load.getItem(i);
			if (!stack.isEmpty() && Prices.price(stack) > 0) all.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		return sell(level, seller, shop, all, load, Integer.MAX_VALUE);
	}

	/** Moves what the chest has for sale into the cart, {@code room} items at most; how many went in. */
	public static int load(ServerLevel level, WorkshopState state, Container store, Container cart, int room) {
		int moved = 0;
		for (Map.Entry<Item, Integer> entry : forSale(level, state, store).entrySet()) {
			int n = Math.min(entry.getValue(), room - moved);
			if (n <= 0) break;
			for (ItemStack stack : take(store, entry.getKey(), n)) {
				ItemStack rest = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, cart, stack, null);
				// a full cart: what did not fit goes back to the chest
				if (!rest.isEmpty()) Farm.put(store, rest);
				moved += stack.getCount() - rest.getCount();
			}
		}
		return moved;
	}

	/** How many items the chest has for sale. */
	public static int total(Map<Item, Integer> sale) {
		return sale.values().stream().mapToInt(Integer::intValue).sum();
	}

	private static int sell(ServerLevel level, Villager seller, Shops.Shop shop, Map<Item, Integer> sale, Container store, int carry) {
		int paid = 0;
		int left = carry;
		for (Map.Entry<Item, Integer> entry : sale.entrySet()) {
			int n = Math.min(Math.min(entry.getValue(), room(shop, entry.getKey())), left);
			if (n <= 0) continue;
			for (ItemStack stack : take(store, entry.getKey(), n)) {
				int price = (int) Math.floor(Prices.worth(stack) * Market.WHOLESALE * stack.getCount());
				left -= stack.getCount();
				shop.put(stack, stack.getCount(), false);
				Market.pay(level, shop, price);
				paid += Market.earn(level, seller, price);
			}
		}
		if (paid > 0) {
			com.vnap.dialogue.Speech.queue(seller, "Villager Sells at the Stall", Shops.keeper(level, shop), 1L);
		}
		return paid;
	}

	/** The stall takes no more of a good than four times its target. */
	static int room(Shops.Shop shop, Item item) {
		return Math.max(64, 4 * Market.target(item)) - shop.count(item);
	}

	/** The cart's load and the chest's sale together. */
	public static Map<Item, Integer> with(Map<Item, Integer> sale, Container load) {
		Map<Item, Integer> all = new LinkedHashMap<>();
		for (int i = 0; i < load.getContainerSize(); i++) {
			ItemStack stack = load.getItem(i);
			if (!stack.isEmpty()) all.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		sale.forEach((item, n) -> all.merge(item, n, Integer::sum));
		return all;
	}

	/**
	 * Up to {@code count} of {@code item} out of the chest as the stacks they are (an enchanted book, a filled map, a
	 * tool keep what they are; worn ones stay, as forSale does not count them).
	 */
	private static java.util.List<ItemStack> take(Container store, Item item, int count) {
		java.util.List<ItemStack> out = new java.util.ArrayList<>();
		int taken = 0;
		for (int i = 0; i < store.getContainerSize() && taken < count; i++) {
			ItemStack stack = store.getItem(i);
			if (!stack.is(item) || stack.isDamaged()) continue;
			ItemStack part = stack.split(Math.min(count - taken, stack.getCount()));
			taken += part.getCount();
			out.add(part);
		}
		store.setChanged();
		return out;
	}
}
