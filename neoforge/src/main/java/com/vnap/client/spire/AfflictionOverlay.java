package com.vnap.client.spire;

import com.vnap.VillagerLife;
import com.vnap.spire.Afflictions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** What an affliction does to the view: ink splashed on the screen (more with each stack), time standing grey. */
public final class AfflictionOverlay {
	/** Where the blots land, as fractions of the screen, and their size: at the edges and corners, the middle and the top's label kept. */
	private static final float[][] BLOTS = {{0.06F, 0.3F, 0.09F}, {0.94F, 0.68F, 0.1F}, {0.1F, 0.86F, 0.08F}, {0.9F, 0.24F, 0.08F}, {0.03F, 0.62F, 0.07F},
			{0.97F, 0.9F, 0.08F}};

	private AfflictionOverlay() {
	}

	public static void register(RegisterGuiLayersEvent event) {
		event.registerBelow(VanillaGuiLayers.HOTBAR, VillagerLife.id("afflictions"), AfflictionOverlay::render);
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft game = Minecraft.getInstance();
		if (game.player == null) return;
		int w = g.guiWidth();
		int h = g.guiHeight();
		MobEffectInstance stopped = game.player.getEffect(Afflictions.holder(Afflictions.STOPPED));
		if (stopped != null) g.fill(0, 0, w, h, 0x60A89880);
		MobEffectInstance inked = game.player.getEffect(Afflictions.holder(Afflictions.INKED));
		if (inked == null) return;
		int blots = Math.min(BLOTS.length, 2 * (inked.getAmplifier() + 1));
		int alpha = Math.min(215, inked.getDuration() * 4);
		for (int i = 0; i < blots; i++) blot(g, BLOTS[i][0] * w, BLOTS[i][1] * h, BLOTS[i][2] * Math.min(w, h), alpha, i);
	}

	/** A round splash, its edge gently wavy. */
	private static void blot(GuiGraphics g, float cx, float cy, float r, int alpha, int seed) {
		int colour = alpha << 24 | 0x14121F;
		for (int dy = (int) -r; dy <= r; dy++) {
			double t = Math.asin(Math.max(-1.0, Math.min(1.0, dy / r)));
			double left = r * (0.92 + 0.08 * Math.sin(t * 3 + seed));
			double right = r * (0.92 + 0.08 * Math.cos(t * 2 + seed * 1.7));
			double across = Math.sqrt(Math.max(0.0, 1.0 - (dy / r) * (dy / r)));
			g.fill((int) (cx - left * across), (int) cy + dy, (int) (cx + right * across), (int) cy + dy + 1, colour);
		}
	}
}
