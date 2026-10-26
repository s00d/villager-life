package com.vnap.fab.neoforge.event.entity.living;

/** An effect added to a living entity (com.vnap.fab.mixin.MobEffectMixin). */
public final class MobEffectEvent {
	private MobEffectEvent() {
	}

	public static class Added extends com.vnap.fab.bus.api.Event {
		private final net.minecraft.world.entity.LivingEntity entity;
		private final net.minecraft.world.effect.MobEffectInstance effectInstance;
		private final net.minecraft.world.effect.@org.jspecify.annotations.Nullable MobEffectInstance oldEffectInstance;
		private final net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity effectSource;

		public Added(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.effect.MobEffectInstance effectInstance, net.minecraft.world.effect.@org.jspecify.annotations.Nullable MobEffectInstance oldEffectInstance, net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity effectSource) {
			this.entity = entity;
			this.effectInstance = effectInstance;
			this.oldEffectInstance = oldEffectInstance;
			this.effectSource = effectSource;
		}

		public net.minecraft.world.entity.LivingEntity getEntity() {
			return entity;
		}

		public net.minecraft.world.effect.MobEffectInstance getEffectInstance() {
			return effectInstance;
		}

		public net.minecraft.world.effect.@org.jspecify.annotations.Nullable MobEffectInstance getOldEffectInstance() {
			return oldEffectInstance;
		}

		public net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity getEffectSource() {
			return effectSource;
		}
	}
}
