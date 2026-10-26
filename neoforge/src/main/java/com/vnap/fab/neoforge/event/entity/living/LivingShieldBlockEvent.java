package com.vnap.fab.neoforge.event.entity.living;

/** A shield about to block damage (com.vnap.fab.mixin.ShieldBlockMixin); a cancel lets it through. */
public class LivingShieldBlockEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.LivingEntity entity;
	private final net.minecraft.world.damagesource.DamageSource damageSource;
	private final float blockedDamage;

	public LivingShieldBlockEvent(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource damageSource, float blockedDamage) {
		this.entity = entity;
		this.damageSource = damageSource;
		this.blockedDamage = blockedDamage;
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return entity;
	}

	public net.minecraft.world.damagesource.DamageSource getDamageSource() {
		return damageSource;
	}

	public float getBlockedDamage() {
		return blockedDamage;
	}

	public boolean getBlocked() {
		return !isCanceled();
	}
}
