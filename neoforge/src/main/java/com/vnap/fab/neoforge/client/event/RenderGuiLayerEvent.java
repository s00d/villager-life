package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.Bridge;
import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.ICancellableEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class RenderGuiLayerEvent {
	private RenderGuiLayerEvent() {
	}

	/** Whether the HUD layer draws this frame (com.vnap.fab.mixin.client.ForgeLayeredDrawMixin asks for each). */
	public static boolean drawn(ResourceLocation name, GuiGraphics graphics, DeltaTracker delta) {
		return !Bridge.fire(new Pre(name, graphics, delta)).vnap$canceled();
	}

	public static final class Pre extends Event implements ICancellableEvent {

		private final ResourceLocation name;
		private final GuiGraphics graphics;
		private final DeltaTracker delta;

		Pre(ResourceLocation name, GuiGraphics graphics, DeltaTracker delta) {
			this.name = name;
			this.graphics = graphics;
			this.delta = delta;
		}

		public ResourceLocation getName() {
			return name;
		}

		public GuiGraphics getGuiGraphics() {
			return graphics;
		}

		public DeltaTracker getPartialTick() {
			return delta;
		}
	}
}
