package com.vnap.neo.neoforge.event.entity.player;

public class ItemTooltipEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(ItemTooltipEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.ItemTooltipEvent.class, priority, ItemTooltipEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.player.ItemTooltipEvent f;

	ItemTooltipEvent(net.minecraftforge.event.entity.player.ItemTooltipEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.item.ItemStack getItemStack() {
		return f.getItemStack();
	}

	public java.util.List<net.minecraft.network.chat.Component> getToolTip() {
		return f.getToolTip();
	}

	public net.minecraft.world.item.TooltipFlag getFlags() {
		return f.getFlags();
	}

	public net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player getEntity() {
		return f.getEntity();
	}

}
