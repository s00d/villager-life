package com.vnap.fab.neoforge.event.entity.living;

/** A living entity knocked back (com.vnap.fab.mixin.KnockBackMixin). */
public class LivingKnockBackEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.LivingEntity entity;
	private float strength;

	public LivingKnockBackEvent(net.minecraft.world.entity.LivingEntity entity, float strength) {
		this.entity = entity;
		this.strength = strength;
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return entity;
	}

	public float getStrength() {
		return strength;
	}

	public void setStrength(float strength) {
		this.strength = strength;
	}
}
