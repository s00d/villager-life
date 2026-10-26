package com.vnap.fab.neoforge.event.level;

/** An explosion about to break blocks and hurt entities (com.vnap.fab.mixin.ExplosionMixin); the lists can be trimmed. */
public final class ExplosionEvent {
	private ExplosionEvent() {
	}

	public static class Detonate extends com.vnap.fab.bus.api.Event {
		private final net.minecraft.world.level.Level level;
		private final net.minecraft.world.level.Explosion explosion;
		private final java.util.List<net.minecraft.core.BlockPos> affectedBlocks;
		private final java.util.List<net.minecraft.world.entity.Entity> affectedEntities;

		public Detonate(net.minecraft.world.level.Level level, net.minecraft.world.level.Explosion explosion, java.util.List<net.minecraft.core.BlockPos> affectedBlocks, java.util.List<net.minecraft.world.entity.Entity> affectedEntities) {
			this.level = level;
			this.explosion = explosion;
			this.affectedBlocks = affectedBlocks;
			this.affectedEntities = affectedEntities;
		}

		public net.minecraft.world.level.Level getLevel() {
			return level;
		}

		public net.minecraft.world.level.Explosion getExplosion() {
			return explosion;
		}

		public java.util.List<net.minecraft.core.BlockPos> getAffectedBlocks() {
			return affectedBlocks;
		}

		public java.util.List<net.minecraft.world.entity.Entity> getAffectedEntities() {
			return affectedEntities;
		}
	}
}
