package com.vnap.neo.neoforge.event.level;

public final class ExplosionEvent {
	private ExplosionEvent() {
	}

	public static class Detonate extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(ExplosionEvent.Detonate.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.level.ExplosionEvent.Detonate.class, priority, Detonate::new, sink));
		}

		private final net.minecraftforge.event.level.ExplosionEvent.Detonate f;

		Detonate(net.minecraftforge.event.level.ExplosionEvent.Detonate f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.level.Level getLevel() {
			return f.getLevel();
		}

		public net.minecraft.world.level.Explosion getExplosion() {
			return f.getExplosion();
		}

		public java.util.List<net.minecraft.core.BlockPos> getAffectedBlocks() {
			return f.getAffectedBlocks();
		}

		public java.util.List<net.minecraft.world.entity.Entity> getAffectedEntities() {
			return f.getAffectedEntities();
		}
	}
}
