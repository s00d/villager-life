package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.Bridge;
import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.ICancellableEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** Before and after the HUD's layers draw (com.vnap.fab.mixin.client.GuiRenderMixin): Forge took its own event out. */
public final class RenderGuiEvent {
	private RenderGuiEvent() {
	}

	/** Draws the HUD's layers between the two events, unless a listener of the first cancels them. */
	public static void around(GuiGraphics graphics, DeltaTracker delta, Runnable layers) {
		if (Bridge.fire(new Pre(graphics, delta)).vnap$canceled()) return;
		layers.run();
		Bridge.fire(new Post(graphics, delta));
	}

	public static final class Pre extends Event implements ICancellableEvent {

		private final GuiGraphics graphics;
		private final DeltaTracker delta;

		public Pre(GuiGraphics graphics, DeltaTracker delta) {
			this.graphics = graphics;
			this.delta = delta;
		}

		public GuiGraphics getGuiGraphics() {
			return graphics;
		}

		public DeltaTracker getPartialTick() {
			return delta;
		}
	}

	public static final class Post extends Event {

		private final GuiGraphics graphics;
		private final DeltaTracker delta;

		public Post(GuiGraphics graphics, DeltaTracker delta) {
			this.graphics = graphics;
			this.delta = delta;
		}

		public GuiGraphics getGuiGraphics() {
			return graphics;
		}

		public DeltaTracker getPartialTick() {
			return delta;
		}
	}
}
