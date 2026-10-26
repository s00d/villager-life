package com.vnap.fab.neoforge.event.entity.living;

import com.vnap.fab.Bridge;
import com.vnap.fab.bus.api.Event;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class LivingDamageEvent {
	private LivingDamageEvent() {
	}

	/** Damage taken, the health already lowered (the end of actuallyHurt: com.vnap.fab.mixin.LivingEntityDamageMixin). */
	public static final class Post extends Event {

		private final LivingEntity entity;
		private final DamageSource source;
		private final float originalDamage;
		private final float newDamage;

		public Post(LivingEntity entity, DamageSource source, float originalDamage, float newDamage) {
			this.entity = entity;
			this.source = source;
			this.originalDamage = originalDamage;
			this.newDamage = newDamage;
		}

		public LivingEntity getEntity() {
			return entity;
		}

		public DamageSource getSource() {
			return source;
		}

		public float getOriginalDamage() {
			return originalDamage;
		}

		public float getNewDamage() {
			return newDamage;
		}
	}
}
