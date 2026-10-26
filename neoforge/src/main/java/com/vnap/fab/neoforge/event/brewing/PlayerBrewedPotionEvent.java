package com.vnap.fab.neoforge.event.brewing;

/** A potion taken out of a brewing stand (com.vnap.fab.mixin.BrewedMixin). */
public class PlayerBrewedPotionEvent extends com.vnap.fab.bus.api.Event {
	private final net.minecraft.world.entity.player.Player player;
	private final net.minecraft.world.item.ItemStack stack;

	public PlayerBrewedPotionEvent(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack stack) {
		this.player = player;
		this.stack = stack;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return player;
	}

	public net.minecraft.world.item.ItemStack getStack() {
		return stack;
	}
}
