package com.vnap.fab.neoforge.event.entity.player;

/** A bow drawn (com.vnap.fab.mixin.BowMixin). */
public class ArrowNockEvent extends com.vnap.fab.bus.api.Event {
	private final net.minecraft.world.entity.player.Player player;
	private final net.minecraft.world.item.ItemStack bow;
	private final net.minecraft.world.level.Level level;
	private final net.minecraft.world.InteractionHand hand;
	private final boolean hasAmmo;

	public ArrowNockEvent(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack bow, net.minecraft.world.level.Level level, net.minecraft.world.InteractionHand hand, boolean hasAmmo) {
		this.player = player;
		this.bow = bow;
		this.level = level;
		this.hand = hand;
		this.hasAmmo = hasAmmo;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return player;
	}

	public net.minecraft.world.item.ItemStack getBow() {
		return bow;
	}

	public net.minecraft.world.level.Level getLevel() {
		return level;
	}

	public net.minecraft.world.InteractionHand getHand() {
		return hand;
	}

	public boolean hasAmmo() {
		return hasAmmo;
	}
}
