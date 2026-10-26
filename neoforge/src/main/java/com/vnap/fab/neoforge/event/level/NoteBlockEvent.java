package com.vnap.fab.neoforge.event.level;

/** A note block about to sound (com.vnap.fab.mixin.NoteBlockMixin). */
public final class NoteBlockEvent {
	private NoteBlockEvent() {
	}

	public static class Play extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
		private final net.minecraft.world.level.LevelAccessor level;
		private final net.minecraft.core.BlockPos pos;
		private final net.minecraft.world.level.block.state.BlockState state;

		public Play(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
			this.level = level;
			this.pos = pos;
			this.state = state;
		}

		public net.minecraft.world.level.LevelAccessor getLevel() {
			return level;
		}

		public net.minecraft.core.BlockPos getPos() {
			return pos;
		}

		public net.minecraft.world.level.block.state.BlockState getState() {
			return state;
		}
	}
}
