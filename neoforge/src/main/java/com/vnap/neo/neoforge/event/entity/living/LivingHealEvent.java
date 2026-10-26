package com.vnap.neo.neoforge.event.entity.living;

public class LivingHealEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(LivingHealEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.living.LivingHealEvent.class, priority, LivingHealEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.living.LivingHealEvent f;

	LivingHealEvent(net.minecraftforge.event.entity.living.LivingHealEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return f.getEntity();
	}

	public float getAmount() {
		return f.getAmount();
	}

	public void setAmount(float amount) {
		f.setAmount(amount);
	}
}
