package com.vnap.fab.neoforge.event.entity.player;

/** A catch reeled in (com.vnap.fab.mixin.FishingMixin). */
public class ItemFishedEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.player.Player player;
	private final net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> drops;
	private final net.minecraft.world.entity.projectile.FishingHook hook;
	private final int rodDamage;

	public ItemFishedEvent(net.minecraft.world.entity.player.Player player, net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> drops, net.minecraft.world.entity.projectile.FishingHook hook, int rodDamage) {
		this.player = player;
		this.drops = drops;
		this.hook = hook;
		this.rodDamage = rodDamage;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return player;
	}

	public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> getDrops() {
		return drops;
	}

	public net.minecraft.world.entity.projectile.FishingHook getHookEntity() {
		return hook;
	}

	public int getRodDamage() {
		return rodDamage;
	}
}
