package com.vnap.neo.neoforge.event.entity.living;

public final class MobEffectEvent {
	private MobEffectEvent() {
	}

	public static class Added extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(MobEffectEvent.Added.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.living.MobEffectEvent.Added.class, priority, Added::new, sink));
		}

		private final net.minecraftforge.event.entity.living.MobEffectEvent.Added f;

		Added(net.minecraftforge.event.entity.living.MobEffectEvent.Added f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.LivingEntity getEntity() {
			return f.getEntity();
		}

		public net.minecraft.world.effect.MobEffectInstance getEffectInstance() {
			return java.util.Objects.requireNonNull(f.getEffectInstance());
		}

		public net.minecraft.world.effect.@org.jspecify.annotations.Nullable MobEffectInstance getOldEffectInstance() {
			return f.getOldEffectInstance();
		}

		public net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity getEffectSource() {
			return f.getEffectSource();
		}
	}
}
