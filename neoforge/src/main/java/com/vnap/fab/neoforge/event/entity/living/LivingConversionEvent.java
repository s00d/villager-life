package com.vnap.fab.neoforge.event.entity.living;

/** A living entity turned into another (com.vnap.fab.mixin.ConversionMixin). */
public final class LivingConversionEvent {
	private LivingConversionEvent() {
	}

	public static class Post extends com.vnap.fab.bus.api.Event {
		private final net.minecraft.world.entity.LivingEntity entity;
		private final net.minecraft.world.entity.LivingEntity outcome;

		public Post(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.entity.LivingEntity outcome) {
			this.entity = entity;
			this.outcome = outcome;
		}

		public net.minecraft.world.entity.LivingEntity getEntity() {
			return entity;
		}

		public net.minecraft.world.entity.LivingEntity getOutcome() {
			return outcome;
		}
	}
}
