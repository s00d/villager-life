package com.vnap.fab.neoforge.event.entity.player;

/** A trade made with a villager or a wandering trader (com.vnap.fab.mixin.TradeMixin). */
public class TradeWithVillagerEvent extends com.vnap.fab.bus.api.Event {
	private final net.minecraft.world.entity.player.Player player;
	private final net.minecraft.world.item.trading.MerchantOffer offer;
	private final net.minecraft.world.entity.npc.AbstractVillager villager;

	public TradeWithVillagerEvent(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.trading.MerchantOffer offer, net.minecraft.world.entity.npc.AbstractVillager villager) {
		this.player = player;
		this.offer = offer;
		this.villager = villager;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return player;
	}

	public net.minecraft.world.item.trading.MerchantOffer getMerchantOffer() {
		return offer;
	}

	public net.minecraft.world.entity.npc.AbstractVillager getAbstractVillager() {
		return villager;
	}
}
