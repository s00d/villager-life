package com.vnap.neo.neoforge.event.entity.living;

public class LivingKnockBackEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(LivingKnockBackEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.living.LivingKnockBackEvent.class, priority, LivingKnockBackEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.living.LivingKnockBackEvent f;

	LivingKnockBackEvent(net.minecraftforge.event.entity.living.LivingKnockBackEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return f.getEntity();
	}

	public float getStrength() {
		return f.getStrength();
	}

	public void setStrength(float strength) {
		f.setStrength(strength);
	}
}
