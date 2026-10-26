package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.minecraft.client.DeltaTracker;

/** A frame of the game: Forge's render tick. */
public final class RenderFrameEvent {
	private RenderFrameEvent() {
	}

	public static final class Pre extends Event {
		static {
			Bridge.bind(Pre.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.TickEvent.RenderTickEvent.Pre.class, priority, Pre::new, sink));
		}

		private final net.minecraftforge.event.TickEvent.RenderTickEvent.Pre f;

		Pre(net.minecraftforge.event.TickEvent.RenderTickEvent.Pre f) {
			super(f);
			this.f = f;
		}

		public DeltaTracker getPartialTick() {
			//? if >=1.21.9 {
			/*return f.timer();
			*///?} else
			return f.getTimer();
		}
	}

	public static final class Post extends Event {
		static {
			Bridge.bind(Post.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.TickEvent.RenderTickEvent.Post.class, priority, Post::new, sink));
		}

		private final net.minecraftforge.event.TickEvent.RenderTickEvent.Post f;

		Post(net.minecraftforge.event.TickEvent.RenderTickEvent.Post f) {
			super(f);
			this.f = f;
		}

		public DeltaTracker getPartialTick() {
			//? if >=1.21.9 {
			/*return f.timer();
			*///?} else
			return f.getTimer();
		}
	}
}
