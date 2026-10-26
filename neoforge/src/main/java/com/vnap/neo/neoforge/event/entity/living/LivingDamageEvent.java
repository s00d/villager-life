package com.vnap.neo.neoforge.event.entity.living;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class LivingDamageEvent {
	private LivingDamageEvent() {
	}

	/** Damage taken, the health already lowered (the end of actuallyHurt: com.vnap.neo.mixin.LivingEntityDamageMixin). */
	public static final class Post extends Event {
		static {
			Bridge.own(Post.class);
		}

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
