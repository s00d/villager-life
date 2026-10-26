package com.vnap.neo.neoforge.event.entity.player;

public class AttackEntityEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(AttackEntityEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.AttackEntityEvent.class, priority, AttackEntityEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.player.AttackEntityEvent f;

	AttackEntityEvent(net.minecraftforge.event.entity.player.AttackEntityEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.entity.Entity getTarget() {
		return f.getTarget();
	}
}
