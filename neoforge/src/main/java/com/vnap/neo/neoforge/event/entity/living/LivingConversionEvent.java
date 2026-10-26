package com.vnap.neo.neoforge.event.entity.living;

public final class LivingConversionEvent {
	private LivingConversionEvent() {
	}

	public static class Post extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(LivingConversionEvent.Post.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.living.LivingConversionEvent.Post.class, priority, Post::new, sink));
		}

		private final net.minecraftforge.event.entity.living.LivingConversionEvent.Post f;

		Post(net.minecraftforge.event.entity.living.LivingConversionEvent.Post f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.LivingEntity getEntity() {
			return f.getEntity();
		}

		public net.minecraft.world.entity.LivingEntity getOutcome() {
			return f.getOutcome();
		}
	}
}
