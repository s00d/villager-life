package com.vnap.fab.neoforge.event.entity.living;

import com.vnap.fab.Bridge;
import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.ICancellableEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Damage about to be taken, after the checks for invulnerability, before anything else (com.vnap.fab.mixin.LivingEntityDamageMixin). */
public final class LivingIncomingDamageEvent extends Event implements ICancellableEvent {

	private final LivingEntity entity;
	private final DamageSource source;
	private final float originalAmount;
	private float amount;

	public LivingIncomingDamageEvent(LivingEntity entity, DamageSource source, float amount) {
		this.entity = entity;
		this.source = source;
		this.originalAmount = amount;
		this.amount = amount;
	}

	public LivingEntity getEntity() {
		return entity;
	}

	public DamageSource getSource() {
		return source;
	}

	public float getAmount() {
		return amount;
	}

	public float getOriginalAmount() {
		return originalAmount;
	}

	public void setAmount(float amount) {
		this.amount = amount;
	}
}
