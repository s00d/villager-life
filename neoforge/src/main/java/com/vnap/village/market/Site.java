package com.vnap.village.market;

import com.vnap.Nbt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A building site's store: the materials the builders brought (bought at the stall) and the money left to buy the
 * rest. The village's treasury grants what the bill costs when the work is planned; a block is set only when its
 * items are here (BuildJob), so nothing goes up out of thin air.
 */
public final class Site {
	public final Map<Item, Integer> stock = new LinkedHashMap<>();
	public int funds;
	/** Of the funds, what the helpers' day labour may take (the bill's tenth over the materials): the rest buys the materials. */
	public int wages;

	public int count(Item item) {
		return stock.getOrDefault(item, 0);
	}

	public boolean has(Map<Item, Integer> items) {
		for (Map.Entry<Item, Integer> entry : items.entrySet()) if (count(entry.getKey()) < entry.getValue()) return false;
		return true;
	}

	/** Takes all of them, or nothing when one is short. */
	public boolean take(Map<Item, Integer> items) {
		if (!has(items)) return false;
		items.forEach((item, count) -> {
			int left = count(item) - count;
			if (left <= 0) stock.remove(item);
			else stock.put(item, left);
		});
		return true;
	}

	public void add(Item item, int count) {
		if (count > 0) stock.merge(item, count, Integer::sum);
	}

	/** What of {@code bill} is not here yet. */
	public Map<Item, Integer> lacking(Map<Item, Integer> bill) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		bill.forEach((item, count) -> {
			int short_ = count - count(item);
			if (short_ > 0) out.put(item, short_);
		});
		return out;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Funds", funds);
		tag.putInt("Wages", wages);
		CompoundTag items = new CompoundTag();
		stock.forEach((item, count) -> items.putInt(BuiltInRegistries.ITEM.getKey(item).toString(), count));
		tag.put("Stock", items);
		return tag;
	}

	public static Site load(CompoundTag tag) {
		Site site = new Site();
		site.funds = Nbt.getInt(tag, "Funds");
		site.wages = Nbt.getInt(tag, "Wages");
		CompoundTag items = Nbt.getCompound(tag, "Stock");
		for (String key : com.vnap.Mc.keys(items)) {
			Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
			if (!item.equals(Items.AIR)) site.stock.put(item, Nbt.getInt(items, key));
		}
		return site;
	}
}
