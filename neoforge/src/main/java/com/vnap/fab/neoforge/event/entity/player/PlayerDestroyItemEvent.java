package com.vnap.fab.neoforge.event.entity.player;

/** An item a player broke by using it up (com.vnap.fab.mixin.ItemBreakMixin). */
public class PlayerDestroyItemEvent extends com.vnap.fab.bus.api.Event {
	private final net.minecraft.world.entity.player.Player player;
	private final net.minecraft.world.item.ItemStack original;

	public PlayerDestroyItemEvent(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack original) {
		this.player = player;
		this.original = original;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return player;
	}

	public net.minecraft.world.item.ItemStack getOriginal() {
		return original;
	}
}
