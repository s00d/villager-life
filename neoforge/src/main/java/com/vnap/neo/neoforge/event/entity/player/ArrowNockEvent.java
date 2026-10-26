package com.vnap.neo.neoforge.event.entity.player;

public class ArrowNockEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(ArrowNockEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.ArrowNockEvent.class, priority, ArrowNockEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.player.ArrowNockEvent f;

	ArrowNockEvent(net.minecraftforge.event.entity.player.ArrowNockEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.item.ItemStack getBow() {
		return f.getBow();
	}

	public net.minecraft.world.level.Level getLevel() {
		return f.getLevel();
	}

	public net.minecraft.world.InteractionHand getHand() {
		return f.getHand();
	}

	public boolean hasAmmo() {
		return f.hasAmmo();
	}
}
