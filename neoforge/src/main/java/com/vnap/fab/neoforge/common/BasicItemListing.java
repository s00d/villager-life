package com.vnap.fab.neoforge.common;

import java.util.Optional;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/** A plain trade: a price (one or two stacks) for a stack (Forge has no such listing of its own). */
public class BasicItemListing implements VillagerTrades.ItemListing {
	protected final ItemStack price;
	protected final ItemStack price2;
	protected final ItemStack forSale;
	protected final int maxTrades;
	protected final int xp;
	protected final float priceMult;

	public BasicItemListing(ItemStack price, ItemStack price2, ItemStack forSale, int maxTrades, int xp, float priceMult) {
		this.price = price;
		this.price2 = price2;
		this.forSale = forSale;
		this.maxTrades = maxTrades;
		this.xp = xp;
		this.priceMult = priceMult;
	}

	public BasicItemListing(int emeralds, ItemStack forSale, int maxTrades, int xp) {
		this(new ItemStack(Items.EMERALD, emeralds), ItemStack.EMPTY, forSale, maxTrades, xp, 1);
	}

	@Override
	//? if >=1.21.9 {
	/*public MerchantOffer getOffer(net.minecraft.server.level.ServerLevel level, Entity trader, RandomSource random) {
	*///?} else
	public MerchantOffer getOffer(Entity trader, RandomSource random) {
		ItemCost cost = new ItemCost(price.getItem(), price.getCount());
		Optional<ItemCost> second = price2.isEmpty() ? Optional.empty()
				: Optional.of(new ItemCost(price2.getItem(), price2.getCount()));
		return new MerchantOffer(cost, second, forSale, maxTrades, xp, priceMult);
	}
}
