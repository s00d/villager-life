package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.bus.api.ICancellableEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class RenderGuiLayerEvent {
	private RenderGuiLayerEvent() {
	}

	/** Whether the HUD layer draws this frame (com.vnap.neo.mixin.client.ForgeLayeredDrawMixin asks for each). */
	public static boolean drawn(ResourceLocation name, GuiGraphics graphics, DeltaTracker delta) {
		return !Bridge.fire(new Pre(name, graphics, delta)).vnap$canceled();
	}

	public static final class Pre extends Event implements ICancellableEvent {
		static {
			Bridge.own(Pre.class);
		}

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
