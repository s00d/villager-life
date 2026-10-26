package com.vnap.fab.neoforge.event.entity.player;

/** An item's tooltip (Fabric API ItemTooltipCallback). */
public class ItemTooltipEvent extends com.vnap.fab.bus.api.Event {
	private final net.minecraft.world.item.ItemStack stack;
	private final java.util.List<net.minecraft.network.chat.Component> toolTip;
	private final net.minecraft.world.item.TooltipFlag flags;
	private final net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player player;

	public ItemTooltipEvent(net.minecraft.world.item.ItemStack stack, java.util.List<net.minecraft.network.chat.Component> toolTip, net.minecraft.world.item.TooltipFlag flags, net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player player) {
		this.stack = stack;
		this.toolTip = toolTip;
		this.flags = flags;
		this.player = player;
	}

	public net.minecraft.world.item.ItemStack getItemStack() {
		return stack;
	}

	public java.util.List<net.minecraft.network.chat.Component> getToolTip() {
		return toolTip;
	}

	public net.minecraft.world.item.TooltipFlag getFlags() {
		return flags;
	}

	public net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player getEntity() {
		return player;
	}
}
