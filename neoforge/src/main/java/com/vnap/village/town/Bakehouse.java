package com.vnap.village.town;

import com.vnap.village.market.Market;
import com.vnap.village.market.Shops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The town's bakehouse (structure village/town/bakehouse, a TownPlots building): a brick oven with a fire in it. A
 * baker (BakeBehavior) buys the stall's wheat with the treasury's money, bakes it into bread and sells the bread to
 * the stall wholesale; a town with its bakehouse built gets no bread from outside (Market.restock, Caravan.arrive).
 */
public final class Bakehouse {
	/** Where the baker stands at the oven's mouth. */
	static final BlockPos BAKER = new BlockPos(3, 1, 5);
	/** Wheat to a loaf (vanilla's recipe), loaves to a batch at most. */
	public static final int WHEAT = 3;
	public static final int BATCH = 8;

	private Bakehouse() {
	}

	public static @Nullable BlockPos baker(Roads.Network network) {
		return TownPlots.built(network, TownPlots.Kind.BAKEHOUSE) ? TownPlots.at(network, TownPlots.Kind.BAKEHOUSE, BAKER) : null;
	}

	/** Whether this stall's town bakes its own bread. */
	public static boolean bakes(ServerLevel level, Shops.Shop shop) {
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), shop.bell);
		return network != null && TownPlots.built(network, TownPlots.Kind.BAKEHOUSE);
	}

	/** Loaves a batch makes now: the stall's bread short of its target and its wheat to bake it with (0: no batch). */
	public static int batch(Shops.Shop shop) {
		int wanted = Market.target(Items.BREAD) - shop.count(Items.BREAD);
		return Math.max(0, Math.min(BATCH, Math.min(wanted, shop.count(Items.WHEAT) / WHEAT)));
	}

	/** The baker takes the batch's wheat off the stall's shelf, the treasury paying for it; false when it is gone. */
	public static boolean takeWheat(ServerLevel level, Shops.Shop shop, int loaves) {
		int wheat = loaves * WHEAT;
		if (shop.count(Items.WHEAT) < wheat) return false;
		int left = wheat;
		int cost = 0;
		for (Shops.Lot lot : new java.util.ArrayList<>(shop.stock)) {
			if (left <= 0) break;
			if (!lot.item.is(Items.WHEAT) || lot.count <= 0) continue;
			int n = Math.min(left, lot.count);
			cost += lot.price * n;
			shop.take(lot, n);
			left -= n;
		}
		shop.till += cost;
		Market.spendTreasury(level, Market.office(level, shop), cost);
		Shops.get(level.getServer()).setDirty();
		return true;
	}

	/** The bread goes onto the stall's shelf; the stall pays the treasury its wholesale worth. */
	public static void sellBread(ServerLevel level, Shops.Shop shop, int loaves) {
		ItemStack bread = new ItemStack(Items.BREAD);
		shop.put(bread, loaves, false);
		int worth = (int) Math.floor(com.vnap.village.market.Prices.price(bread) * Market.WHOLESALE * loaves);
		int fromTill = Math.min(worth, Math.max(0, shop.till));
		shop.till -= fromTill;
		Market.deposit(level, Market.office(level, shop), fromTill);
		Shops.get(level.getServer()).setDirty();
	}
}
