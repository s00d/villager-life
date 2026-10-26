package com.vnap.fab.neoforge.event.entity.living;

/** A living entity dying (Fabric API ALLOW_DEATH); a cancel keeps it alive. */
public class LivingDeathEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.LivingEntity entity;
	private final net.minecraft.world.damagesource.DamageSource source;

	public LivingDeathEvent(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		this.entity = entity;
		this.source = source;
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return entity;
	}

	public net.minecraft.world.damagesource.DamageSource getSource() {
		return source;
	}
}
