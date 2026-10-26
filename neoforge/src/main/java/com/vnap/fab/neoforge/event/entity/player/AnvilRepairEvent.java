package com.vnap.fab.neoforge.event.entity.player;

/** An item taken from an anvil (com.vnap.fab.mixin.AnvilMixin). */
public class AnvilRepairEvent extends com.vnap.fab.bus.api.Event {
	private final net.minecraft.world.entity.player.Player player;
	private final net.minecraft.world.item.ItemStack left;
	private final net.minecraft.world.item.ItemStack right;
	private final net.minecraft.world.item.ItemStack output;

	public AnvilRepairEvent(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack left, net.minecraft.world.item.ItemStack right, net.minecraft.world.item.ItemStack output) {
		this.player = player;
		this.left = left;
		this.right = right;
		this.output = output;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return player;
	}

	public net.minecraft.world.item.ItemStack getLeft() {
		return left;
	}

	public net.minecraft.world.item.ItemStack getRight() {
		return right;
	}

	public net.minecraft.world.item.ItemStack getOutput() {
		return output;
	}
}
