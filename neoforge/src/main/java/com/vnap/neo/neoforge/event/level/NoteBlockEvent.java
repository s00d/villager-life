package com.vnap.neo.neoforge.event.level;

public final class NoteBlockEvent {
	private NoteBlockEvent() {
	}

	public static class Play extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
		static {
			com.vnap.neo.Bridge.bind(NoteBlockEvent.Play.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.level.NoteBlockEvent.Play.class, priority, Play::new, sink));
		}

		private final net.minecraftforge.event.level.NoteBlockEvent.Play f;

		Play(net.minecraftforge.event.level.NoteBlockEvent.Play f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.level.LevelAccessor getLevel() {
			return f.getLevel();
		}

		public net.minecraft.core.BlockPos getPos() {
			return f.getPos();
		}

		public net.minecraft.world.level.block.state.BlockState getState() {
			return f.getState();
		}
	}
}
