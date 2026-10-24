package com.vnap.client;

import com.mojang.math.Axis;
import com.vnap.network.ErrandPayload;
import com.vnap.village.market.Coins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * The player's errand on the screen, small, at the right edge under the mood bar's height: the thing and whom it is
 * for, the reward, and an arrow turning toward the villager with the distance (where it was last seen while out of
 * sight). Gone once the errand is done or given up (the notice board).
 */
public final class ErrandHud {
	private static ErrandPayload errand = ErrandPayload.NONE;

	private ErrandHud() {
	}

	public static void update(ErrandPayload payload) {
		errand = payload;
	}

	public static void clear() {
		errand = ErrandPayload.NONE;
	}

	public static void onRender(RenderGuiEvent.Post event) {
		//? if <1.21.2 {
		if (com.vnap.client.dev.Recorder.clean) return;
		//?}
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (!errand.active() || player == null || McClient.hudHidden()) return;
		GuiGraphics g = event.getGuiGraphics();
		Font font = minecraft.font;
		Component line = Component.translatable("errand.villager_life.hud", errand.item().getCount(), errand.item().getHoverName(), errand.name());
		int width = Math.max(font.width(line), 60) + 24 + 30;
		int x = g.guiWidth() - width - 6;
		int y = 40;
		g.fill(x, y, x + width, y + 30, 0xA0201812);
		g.renderOutline(x, y, width, 30, 0xC0A08050);
		g.renderItem(errand.item(), x + 4, y + 7);
		g.drawString(font, line, x + 24, y + 5, 0xFFF0E6D0, false);
		g.drawString(font, Component.translatable("errand.villager_life.reward", Coins.text(errand.reward())), x + 24, y + 17, 0xFFC8B890, false);
		if (!errand.here()) return;
		double dx = errand.target().getX() + 0.5 - player.getX();
		double dz = errand.target().getZ() + 0.5 - player.getZ();
		int distance = (int) Math.sqrt(dx * dx + dz * dz);
		// the angle from where the player looks: 0 straight ahead
		float toward = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
		float turn = toward - player.getYRot();
		int ax = x + width - 14;
		int ay = y + 12;
		McClient.push(g);
		McClient.translate(g, ax, ay, 0);
		McClient.rotate(g, turn);
		for (int i = 0; i < 6; i++) g.fill(-i, -6 + i * 2, i + 1, -4 + i * 2, 0xFFE0B040);
		g.fill(-1, 4, 2, 7, 0xFFE0B040);
		McClient.pop(g);
		// in blocks, its own word: "m" would read as copper coins next to the reward
		String metres = net.minecraft.network.chat.Component.translatable("errand.villager_life.blocks", distance).getString();
		g.drawString(font, metres, ax - font.width(metres) / 2, y + 21, 0xFFC8B890, false);
	}
}
