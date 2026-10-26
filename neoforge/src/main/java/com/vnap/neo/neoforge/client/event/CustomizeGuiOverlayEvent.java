package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.bus.api.ICancellableEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;

public final class CustomizeGuiOverlayEvent {
	private CustomizeGuiOverlayEvent() {
	}

	public static final class BossEventProgress extends Event implements ICancellableEvent {
		static {
			Bridge.bind(BossEventProgress.class, (priority, sink) -> Bridge.game(net.minecraftforge.client.event.CustomizeGuiOverlayEvent.BossEventProgress.class, priority,
					BossEventProgress::new, sink));
		}

		private final net.minecraftforge.client.event.CustomizeGuiOverlayEvent.BossEventProgress f;

		BossEventProgress(net.minecraftforge.client.event.CustomizeGuiOverlayEvent.BossEventProgress f) {
			super(f);
			this.f = f;
		}

		public GuiGraphics getGuiGraphics() {
			return f.getGuiGraphics();
		}

		public DeltaTracker getPartialTick() {
			return Minecraft.getInstance().getTimer();
		}

		public LerpingBossEvent getBossEvent() {
			return f.getBossEvent();
		}

		public int getX() {
			return f.getX();
		}

		public int getY() {
			return f.getY();
		}

		public int getIncrement() {
			return f.getIncrement();
		}

		public void setIncrement(int increment) {
			f.setIncrement(increment);
		}
	}
}
