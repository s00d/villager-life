package com.vnap.fab.neoforge.event.village;

import com.vnap.fab.bus.api.Event;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;

/** A profession's trades by level, once at the first server start (com.vnap.fab.Hooks writes them back to vanilla's table). */
public final class VillagerTradesEvent extends Event {
	private final Int2ObjectMap<List<VillagerTrades.ItemListing>> trades;
	//? if >=1.21.5 {
	/*private final net.minecraft.resources.ResourceKey<VillagerProfession> type;

	public VillagerTradesEvent(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades, net.minecraft.resources.ResourceKey<VillagerProfession> type) {
	*///?} else {
	private final VillagerProfession type;

	public VillagerTradesEvent(Int2ObjectMap<List<VillagerTrades.ItemListing>> trades, VillagerProfession type) {
	//?}
		this.trades = trades;
		this.type = type;
	}

	public Int2ObjectMap<List<VillagerTrades.ItemListing>> getTrades() {
		return trades;
	}

	//? if >=1.21.5 {
	/*public net.minecraft.resources.ResourceKey<VillagerProfession> getType() {
		return type;
	}
	*///?} else {
	public VillagerProfession getType() {
		return type;
	}
	//?}
}
