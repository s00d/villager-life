package com.vnap.neo.neoforge.event.entity.living;

public class LivingShieldBlockEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(LivingShieldBlockEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.living.ShieldBlockEvent.class, priority, LivingShieldBlockEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.living.ShieldBlockEvent f;

	LivingShieldBlockEvent(net.minecraftforge.event.entity.living.ShieldBlockEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.damagesource.DamageSource getDamageSource() {
		return f.getDamageSource();
	}

	public float getBlockedDamage() {
		return f.getBlockedDamage();
	}

	public boolean getBlocked() {
		return !isCanceled();
	}
}
