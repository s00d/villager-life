package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.ICancellableEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;

/** A boss bar about to be drawn (com.vnap.fab.mixin.client.BossBarMixin): cancelled, it is not; the next one goes the increment lower. */
public final class CustomizeGuiOverlayEvent {
	private CustomizeGuiOverlayEvent() {
	}

	public static final class BossEventProgress extends Event implements ICancellableEvent {
		private final GuiGraphics graphics;
		private final DeltaTracker partialTick;
		private final LerpingBossEvent bossEvent;
		private final int x;
		private final int y;
		private int increment;

		public BossEventProgress(GuiGraphics graphics, DeltaTracker partialTick, LerpingBossEvent bossEvent, int x, int y, int increment) {
			this.graphics = graphics;
			this.partialTick = partialTick;
			this.bossEvent = bossEvent;
			this.x = x;
			this.y = y;
			this.increment = increment;
		}

		public GuiGraphics getGuiGraphics() {
			return graphics;
		}

		public DeltaTracker getPartialTick() {
			return partialTick;
		}

		public LerpingBossEvent getBossEvent() {
			return bossEvent;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public int getIncrement() {
			return increment;
		}

		public void setIncrement(int increment) {
			this.increment = increment;
		}
	}
}
