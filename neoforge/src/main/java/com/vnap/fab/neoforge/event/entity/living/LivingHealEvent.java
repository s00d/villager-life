package com.vnap.fab.neoforge.event.entity.living;

/** A living entity healing (com.vnap.fab.mixin.HealMixin). */
public class LivingHealEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.LivingEntity entity;
	private float amount;

	public LivingHealEvent(net.minecraft.world.entity.LivingEntity entity, float amount) {
		this.entity = entity;
		this.amount = amount;
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return entity;
	}

	public float getAmount() {
		return amount;
	}

	public void setAmount(float amount) {
		this.amount = amount;
	}
}
