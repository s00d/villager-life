package com.vnap.neo.neoforge.event.entity.item;

public class ItemTossEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(ItemTossEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.item.ItemTossEvent.class, priority, ItemTossEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.item.ItemTossEvent f;

	ItemTossEvent(net.minecraftforge.event.entity.item.ItemTossEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.player.Player getPlayer() {
		return f.getPlayer();
	}

	public net.minecraft.world.entity.item.ItemEntity getEntity() {
		return f.getEntity();
	}
}
