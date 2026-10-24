package com.vnap.client.spire;

import com.vnap.client.McClient;
import com.vnap.network.SpireFloorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * The floor on the screen while a player is up the spire: coming in, a title card in the middle (the floor's number,
 * its theme under it, fading after a few seconds); then a chip at the top in the middle with the guards still standing,
 * or a tick once the way on is open.
 */
public final class SpireHud {
	private static final long CARD_MS = 3500L;
	private static SpireFloorPayload floor = SpireFloorPayload.NONE;
	private static long arrivedAt;

	private SpireHud() {
	}

	public static void update(SpireFloorPayload payload) {
		if (payload.arrived()) arrivedAt = System.currentTimeMillis();
		floor = payload;
	}

	/** The floor the player stands on, none outside the spire's dimension. */
	public static SpireFloorPayload current() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && inSpire(minecraft) ? floor : SpireFloorPayload.NONE;
	}

	private static boolean inSpire(Minecraft minecraft) {
		return minecraft.player != null && com.vnap.Mc.id(minecraft.player.level().dimension()).getPath().equals("spire");
	}

	public static void clear() {
		floor = SpireFloorPayload.NONE;
	}

	public static void onRender(RenderGuiEvent.Post event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (floor.floor() <= 0 || minecraft.player == null || McClient.hudHidden()) return;
		// the floor's word may come before the player is moved into the spire: kept, shown once there
		if (!inSpire(minecraft)) return;
		GuiGraphics g = event.getGuiGraphics();
		Font font = minecraft.font;
		Component theme = Component.translatable("spire.villager_life.theme." + floor.theme());
		long since = System.currentTimeMillis() - arrivedAt;
		if (since < CARD_MS) {
			float alpha = since < 400 ? since / 400.0F : since > CARD_MS - 800 ? (CARD_MS - since) / 800.0F : 1.0F;
			int a = Math.max(8, (int) (alpha * 255)) << 24;
			// an Outer Gate's run counts waves, waystones, bosses: its own words
			boolean outer = floor.theme().startsWith("outer_");
			Component number = Component.translatable(outer ? "outer.villager_life.hud.card." + floor.theme() : "spire.villager_life.hud.floor", floor.floor());
			int cx = g.guiWidth() / 2;
			int cy = g.guiHeight() / 3;
			McClient.push(g);
			McClient.translate(g, cx, cy, 0);
			McClient.scale(g, 2.5F, 2.5F, 1.0F);
			g.drawString(font, number, -font.width(number) / 2, -4, a | 0xE6B85A, true);
			McClient.pop(g);
			g.drawString(font, theme, cx - font.width(theme) / 2, cy + 12, a | 0xE8DCC0, true);
			int line = font.width(theme) / 2 + 12;
			g.fill(cx - line, cy + 24, cx + line, cy + 25, a | 0xB284EC);
			return;
		}
		// then a chip, one line at the top in the middle (the corners are the minimaps'): the floor, its theme, the
		// guards still standing or a tick once the way on is open; under a keeper's bar on its floor
		Component head = floor.theme().startsWith("outer_") ? Component.translatable("outer.villager_life.hud.chip." + floor.theme(), floor.floor(), theme)
				: Component.translatable("spire.villager_life.hud.chip", floor.floor(), theme);
		Component state = floor.theme().equals("outer_isles") ? Component.translatable("outer.villager_life.hud.lives", floor.guards(), floor.total())
				: floor.cleared() ? Component.translatable(floor.theme().startsWith("outer_") ? "outer.villager_life.hud.rest" : "spire.villager_life.hud.chip_open")
				: Component.translatable("spire.villager_life.hud.chip_guards", floor.guards(), floor.total());
		int width = font.width(head) + 10 + font.width(state) + 12;
		int x = g.guiWidth() / 2 - width / 2;
		// under the boss bars while they stand: a keeper's every tenth floor, a trial's every fifth, the Schism's pair two
		int bars = floor.cleared() ? 0 : floor.theme().equals("outer_rush") ? 1
				: floor.theme().equals("outer_arena") ? (floor.floor() % 10 == 0 ? 2 : floor.floor() % 5 == 0 ? 1 : 0)
				: floor.theme().startsWith("outer_") ? 0 : com.vnap.spire.Genome.schism(floor.floor()) ? 2
				: floor.floor() % 10 == 0 || com.vnap.spire.Genome.trial(floor.floor()) ? 1 : 0;
		int y = 3 + bars * 19 + (bars > 0 ? 4 : 0);
		McClient.sprite(g, com.vnap.client.ui.Ui.Theme.SPIRE.sprite("header"), x, y, width, 14);
		g.drawString(font, head, x + 6, y + 3, 0xFFE6B85A, false);
		g.drawString(font, state, x + width - 6 - font.width(state), y + 3, floor.cleared() ? 0xFF9BE08F : 0xFFE8DCC0, false);
	}
}
