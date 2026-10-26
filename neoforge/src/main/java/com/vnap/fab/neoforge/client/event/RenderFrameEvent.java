package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.bus.api.Event;
import net.minecraft.client.DeltaTracker;

/** Around a frame of the game (com.vnap.fab.mixin.client.FrameMixin). */
public final class RenderFrameEvent {
	private RenderFrameEvent() {
	}

	public static final class Pre extends Event {
		private final DeltaTracker partialTick;

		public Pre(DeltaTracker partialTick) {
			this.partialTick = partialTick;
		}

		public DeltaTracker getPartialTick() {
			return partialTick;
		}
	}

	public static final class Post extends Event {
		private final DeltaTracker partialTick;

		public Post(DeltaTracker partialTick) {
			this.partialTick = partialTick;
		}

		public DeltaTracker getPartialTick() {
			return partialTick;
		}
	}
}
