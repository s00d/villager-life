package com.vnap.fab.neoforge.event.entity.item;

/** A player dropping an item (com.vnap.fab.mixin.ItemTossMixin); a cancel takes the item back. */
public class ItemTossEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.player.Player player;
	private final net.minecraft.world.entity.item.ItemEntity entity;

	public ItemTossEvent(net.minecraft.world.entity.item.ItemEntity entity, net.minecraft.world.entity.player.Player player) {
		this.entity = entity;
		this.player = player;
	}

	public net.minecraft.world.entity.player.Player getPlayer() {
		return player;
	}

	public net.minecraft.world.entity.item.ItemEntity getEntity() {
		return entity;
	}
}
