package com.vnap.neo.neoforge.event.entity.living;

public class LivingDeathEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(LivingDeathEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.living.LivingDeathEvent.class, priority, LivingDeathEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.living.LivingDeathEvent f;

	LivingDeathEvent(net.minecraftforge.event.entity.living.LivingDeathEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.damagesource.DamageSource getSource() {
		return f.getSource();
	}
}
