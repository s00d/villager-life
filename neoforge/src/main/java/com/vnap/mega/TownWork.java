package com.vnap.mega;

import com.vnap.village.market.Market;
import com.vnap.village.market.Prices;
import com.vnap.village.market.Shops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What a megastructure is to the town it stands in (its stall in reach): the Quarry's stone, the Sawmill's logs, the
 * Smelter's ingots that find no room in a chest beside them go to the town's stall — the stuff its builders and its
 * workshops buy (Market: roads, walls, houses, the smiths' iron) — at the stall's wholesale price, paid to the one who
 * set the controller down (in their pockets when they are about, else kept for their next click). A megastructure
 * coming alive in a town is a thing the village is glad of, once a kind (its mood, as for any help of a player's).
 */
public final class TownWork {
	private TownWork() {
	}

	/** The stocked stall of the town this controller stands in, or null outside a town. */
	static Shops.@Nullable Shop stall(ServerLevel level, MegaControllerBlockEntity controller) {
		Shops.Shop shop = Shops.near(level, controller.getBlockPos());
		if (shop == null || !com.vnap.village.home.Towns.ours(level, shop.bell, controller.getBlockPos())) return null;
		return shop;
	}

	/** As much of the stack as the town's stall takes (no more of a good than it keeps: Produce's room); what is left. */
	static ItemStack deliver(ServerLevel level, MegaControllerBlockEntity controller, ItemStack stack) {
		Shops.Shop shop = stall(level, controller);
		if (shop == null || stack.isEmpty()) return stack;
		int room = Math.max(64, 4 * Market.target(stack.getItem())) - shop.count(stack.getItem());
		int n = Math.min(room, stack.getCount());
		if (n <= 0 || Prices.price(stack) <= 0) return stack;
		ItemStack sold = stack.split(n);
		int coins = (int) Math.floor(Prices.price(sold) * Market.WHOLESALE * n);
		shop.put(sold, n, false);
		Market.payOut(level, shop, coins);
		pay(level, controller, coins);
		return stack;
	}

	private static void pay(ServerLevel level, MegaControllerBlockEntity controller, int coins) {
		if (coins <= 0) return;
		ServerPlayer owner = controller.owner == null ? null : level.getServer().getPlayerList().getPlayer(controller.owner);
		if (owner != null && owner.blockPosition().closerThan(controller.getBlockPos(), 128)) com.vnap.village.market.Coins.give(owner, coins);
		else controller.owed += coins;
		controller.setChanged();
	}

	/** A click by its owner: what the town owes for what it took. */
	static void collect(ServerLevel level, MegaControllerBlockEntity controller, net.minecraft.world.entity.player.Player player) {
		if (controller.owed <= 0 || controller.owner == null || !controller.owner.equals(player.getUUID())) return;
		com.vnap.village.market.Coins.give(player, controller.owed);
		com.vnap.Mc.say(player, net.minecraft.network.chat.Component.translatable("mega.villager_life.town.paid", com.vnap.village.market.Coins.text(controller.owed)), false);
		controller.owed = 0;
		controller.setChanged();
	}

	/** Alive in a town for the first time: the village is glad of it. */
	static void thanked(ServerLevel level, MegaControllerBlockEntity controller) {
		if (controller.thanked || stall(level, controller) == null) return;
		controller.thanked = true;
		controller.setChanged();
		com.vnap.mood.VillageMoodApi.applyAt(level, controller.getBlockPos(), 5.0F, 2.0F, "megastructure");
	}

	/**
	 * The stalls of the towns this one is linked to by Waygates (a whole, keyed gate in each town, the two of a colour):
	 * a town's morning delivery takes their surplus first (Market.restock), the money staying between the two.
	 */
	public static java.util.List<Shops.Shop> linked(ServerLevel level, Shops.Shop shop) {
		java.util.List<Shops.Shop> out = new java.util.ArrayList<>();
		if (!com.vnap.config.LifeSettings.megastructures()) return out;
		Gates book = Gates.get(level.getServer());
		for (net.minecraft.core.GlobalPos gate : book.all()) {
			if (!gate.dimension().equals(level.dimension()) || !com.vnap.village.home.Towns.ours(level, shop.bell, gate.pos())) continue;
			net.minecraft.core.GlobalPos other = book.other(gate);
			if (other == null) continue;
			Shops.Shop there = Shops.get(level.getServer()).shopNear(other.dimension(), other.pos());
			if (there != null && there.stocked && there != shop && !out.contains(there)) out.add(there);
		}
		return out;
	}

	/**
	 * Up to {@code wanted} of a good out of a linked town's surplus (what its stall holds past its own target): moved
	 * into {@code shop}, paid wholesale out of {@code shop}'s till into the other's; how many came.
	 */
	public static int fromLinked(ServerLevel level, Shops.Shop shop, net.minecraft.world.item.Item item, int wanted) {
		int got = 0;
		for (Shops.Shop there : linked(level, shop)) {
			int spare = there.count(item) - Market.target(item);
			for (Shops.Lot lot : java.util.List.copyOf(there.stock)) {
				if (got >= wanted || spare <= 0) break;
				if (lot.fromPlayer || !lot.item.is(item)) continue;
				int n = Math.min(Math.min(lot.count, spare), wanted - got);
				if (n <= 0) continue;
				int price = (int) Math.ceil(Prices.price(lot.item) * Market.WHOLESALE * n);
				there.take(lot, n);
				there.till += price;
				shop.till -= price;
				shop.put(new ItemStack(item), n, false);
				got += n;
				spare -= n;
			}
		}
		if (got > 0) Shops.get(level.getServer()).setDirty();
		return got;
	}

	/** Where in the town the controller stands, for the click's word (null when in none). */
	static @Nullable BlockPos bell(ServerLevel level, MegaControllerBlockEntity controller) {
		Shops.Shop shop = stall(level, controller);
		return shop == null ? null : shop.bell;
	}
}
