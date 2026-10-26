package com.vnap.fab.mixin.client;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.client.event.CustomizeGuiOverlayEvent;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's boss bars with NeoForge's BossEventProgress for each: a cancel leaves the bar to the listener, the increment moves the next. */
@SuppressWarnings("unused")
@Mixin(BossHealthOverlay.class)
public abstract class BossBarMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	@Final
	Map<UUID, LerpingBossEvent> events;

	// from 26.1 the overlay extracts what it draws (a stratum of its own first)
	//? if >=26.1 {
	/*@Shadow
	protected abstract void extractBar(GuiGraphics graphics, int x, int y, BossEvent event);

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void vnap$render(GuiGraphics graphics, CallbackInfo info) {
		info.cancel();
		if (events.isEmpty()) return;
		graphics.nextStratum();
	*///?} else {
	@Shadow
	protected abstract void drawBar(GuiGraphics graphics, int x, int y, BossEvent event);

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void vnap$render(GuiGraphics graphics, CallbackInfo info) {
		info.cancel();
		if (events.isEmpty()) return;
	//?}
		int width = graphics.guiWidth();
		int y = 12;
		for (LerpingBossEvent bar : events.values()) {
			int x = width / 2 - 91;
			//? if >=1.21.2 {
			/*var delta = minecraft.getDeltaTracker();
			*///?} else
			var delta = minecraft.getTimer();
			var event = Bridge.fire(new CustomizeGuiOverlayEvent.BossEventProgress(graphics, delta, bar, x, y, 10 + minecraft.font.lineHeight));
			if (!event.isCanceled()) {
				//? if >=26.1 {
				/*extractBar(graphics, x, y, bar);
				*///?} else
				drawBar(graphics, x, y, bar);
				Component name = bar.getName();
				graphics.drawString(minecraft.font, name, width / 2 - minecraft.font.width(name) / 2, y - 9, -1);
			}
			y += event.getIncrement();
			if (y >= graphics.guiHeight() / 3) break;
		}
	}
}
