package com.vnap.client.ui;

import com.vnap.VillagerLife;
import com.vnap.client.McClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The mod's window kit, shared by every screen: frames, slots, buttons, tabs, bars, arrows and icons are vanilla GUI
 * sprites under textures/gui/sprites/<theme>/ (tools/art/paint_ui.py; each scaled as its .mcmeta says, so one small
 * picture makes a window of any size and a resource pack restyles a theme), the colours of the theme's text, and the
 * text helpers the windows all need. A screen picks its theme once; the layout stays in the screen.
 */
public final class Ui {
	private Ui() {
	}

	/** A look: the folder of its sprites and the colours its text is written in (on the title band, titles, body, ...). */
	public record Theme(String folder, int onHeader, int title, int text, int muted, int good, int bad, int accent, int onButton, int onButtonOff) {
		/** The village's windows: an oak frame round parchment. */
		public static final Theme VILLAGE = new Theme("village", 0xFFFFF0D0, 0xFF4A2E14, 0xFF3A2A18, 0xFF7A6650, 0xFF3F7A2A, 0xFFA83228, 0xFF8A5A10,
				0xFFFFF4DC, 0xFFD8D0C4);
		/** The spire's: slate in brass, the Core's vein. */
		public static final Theme SPIRE = new Theme("spire", 0xFFE6B85A, 0xFFE6B85A, 0xFFE8DCC0, 0xFFA8A08C, 0xFF9BE08F, 0xFFEF7A6A, 0xFFC9A6FF,
				0xFF3A2A10, 0xFF8A8478);

		public ResourceLocation sprite(String name) {
			return VillagerLife.id(folder + "/" + name);
		}
	}

	public static void panel(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
		McClient.sprite(g, theme.sprite("panel"), x, y, w, h);
	}

	/** A sunken card. */
	public static void inset(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
		McClient.sprite(g, theme.sprite("inset"), x, y, w, h);
	}

	/** A title band with its title in the middle. */
	public static void header(GuiGraphics g, Theme theme, Font font, int x, int y, int w, Component title) {
		McClient.sprite(g, theme.sprite("header"), x, y, w, 14);
		centred(g, font, title, x + w / 2, y + 3, theme.onHeader(), 0xFF000000);
	}

	/** A slot's frame for the item drawn at (x, y). */
	public static void slot(GuiGraphics g, Theme theme, int x, int y) {
		McClient.sprite(g, theme.sprite("slot"), x - 1, y - 1, 18, 18);
	}

	/** The main slot: a ring round the item at (x, y). */
	public static void bigSlot(GuiGraphics g, Theme theme, int x, int y) {
		McClient.sprite(g, theme.sprite("slot_big"), x - 5, y - 5, 26, 26);
	}

	public static void icon(GuiGraphics g, Theme theme, String name, int x, int y) {
		McClient.sprite(g, theme.sprite("icon/" + name), x, y, 9, 9);
	}

	/** How much smaller a button's word is drawn when it does not fit at full size. */
	private static final float SMALL = 0.75F;

	/** A button with its label; true when the mouse is over an active one. */
	public static boolean button(GuiGraphics g, Theme theme, Font font, int x, int y, int w, int h, Component label, boolean active, int mouseX,
								 int mouseY) {
		boolean hover = active && over(mouseX, mouseY, x, y, w, h);
		McClient.sprite(g, theme.sprite(!active ? "button_disabled" : hover ? "button_hover" : "button"), x, y, w, h);
		int colour = active ? theme.onButton() : theme.onButtonOff();
		int full = font.width(label);
		if (full > w - 8 && full * SMALL <= w - 4) {
			// too wide for the button at full size, it fits a quarter smaller: drawn so, not cut to "…"
			McClient.push(g);
			McClient.translate(g, x + w / 2.0F, y + h / 2.0F, 0.0F);
			McClient.scale(g, SMALL, SMALL, 1.0F);
			g.drawString(font, label, -full / 2, -4, colour, false);
			McClient.pop(g);
			return hover;
		}
		Component shown = Component.literal(clipped(font, label, w - 8));
		g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2, colour, false);
		return hover;
	}

	/** A button of a window, laid out each frame: where, what it says, whether it is there and works, what it does. */
	public record Action(int x, int y, int w, int h, Component label, boolean shown, boolean active, Runnable does) {
	}

	/** Draws the shown ones. */
	public static void draw(GuiGraphics g, Theme theme, Font font, List<Action> actions, int mouseX, int mouseY) {
		for (Action a : actions) if (a.shown()) button(g, theme, font, a.x(), a.y(), a.w(), a.h(), a.label(), a.active(), mouseX, mouseY);
	}

	/** Runs the active one under the mouse (with its click); false when there is none. */
	public static boolean click(List<Action> actions, double mouseX, double mouseY) {
		for (Action a : actions) {
			if (a.shown() && a.active() && over(mouseX, mouseY, a.x(), a.y(), a.w(), a.h())) {
				press();
				a.does().run();
				return true;
			}
		}
		return false;
	}

	/** A tab over the panel: the open one joins it. */
	public static void tab(GuiGraphics g, Theme theme, int x, int y, int w, int h, boolean open) {
		McClient.sprite(g, theme.sprite(open ? "tab_open" : "tab"), x, y, w, h);
	}

	/** A bar {@code fraction} full, and a paler stretch to {@code next} (what the next step adds); next below fraction shows none. */
	public static void bar(GuiGraphics g, Theme theme, int x, int y, int w, double fraction, double next) {
		McClient.sprite(g, theme.sprite("bar"), x, y, w, 7);
		int now = (int) Math.round((w - 2) * clamp(fraction));
		int then = (int) Math.round((w - 2) * clamp(next));
		if (now > 0) McClient.sprite(g, theme.sprite("bar_fill"), x + 1, y + 1, now, 5);
		if (then > now) g.fill(x + 1 + now, y + 1, x + 1 + then, y + 6, 0x70FFFFFF);
	}

	/** A bar that fills from its middle: to the right for {@code value} above 0, to the left below (-1..1). */
	public static void centreBar(GuiGraphics g, Theme theme, int x, int y, int w, double value) {
		McClient.sprite(g, theme.sprite("bar"), x, y, w, 7);
		int mid = x + w / 2;
		int reach = (int) Math.round((w / 2 - 1) * Math.max(-1.0, Math.min(1.0, value)));
		if (reach > 0) McClient.sprite(g, theme.sprite("bar_fill"), mid, y + 1, reach, 5);
		if (reach < 0) g.fill(mid + reach, y + 1, mid, y + 6, 0xFFB04030);
		g.fill(mid, y, mid + 1, y + 7, 0xFF000000 | theme.text());
	}

	/** The process arrow, {@code fraction} of the way. */
	public static void arrow(GuiGraphics g, Theme theme, int x, int y, double fraction) {
		McClient.sprite(g, theme.sprite("arrow"), x, y, 24, 16);
		int w = (int) Math.round(24 * clamp(fraction));
		if (w > 0) McClient.blitSprite(g, theme.sprite("arrow_fill"), 24, 16, 0, 0, x, y, w, 16);
	}

	/** A flame burning down: {@code fraction} of it left. */
	public static void flame(GuiGraphics g, Theme theme, int x, int y, double fraction) {
		McClient.sprite(g, theme.sprite("flame"), x, y, 14, 14);
		int h = (int) Math.round(14 * clamp(fraction));
		if (h > 0) McClient.blitSprite(g, theme.sprite("flame_fill"), 14, 14, 0, 14 - h, x, y + 14 - h, 14, h);
	}

	/** A scroll track and its knob at {@code fraction} of the way down. */
	public static void scroll(GuiGraphics g, Theme theme, int x, int y, int h, double fraction, boolean active) {
		McClient.sprite(g, theme.sprite("scroll"), x, y, 12, h);
		if (active) McClient.sprite(g, theme.sprite("scroll_knob"), x + 1, y + 1 + (int) Math.round((h - 17) * clamp(fraction)), 10, 15);
	}

	private static double clamp(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}

	/**
	 * An item in a slot with its count small in the corner (the vanilla count of a shelf's 253 runs into the next
	 * slot): over 999 it reads 1.2k.
	 */
	public static void item(GuiGraphics g, Font font, net.minecraft.world.item.ItemStack stack, int x, int y) {
		g.renderItem(stack, x, y);
		g.renderItemDecorations(font, stack, x, y, "");
		if (stack.getCount() <= 1) return;
		int n = stack.getCount();
		String count = n < 1000 ? String.valueOf(n) : n < 10_000 ? String.format(java.util.Locale.ROOT, "%.1fk", n / 1000.0) : n / 1000 + "k";
		McClient.push(g);
		McClient.translate(g, x + 17, y + 11, 200.0F);
		McClient.scale(g, 0.75F, 0.75F, 1.0F);
		g.drawString(font, count, -font.width(count), 0, 0xFFFFFFFF, true);
		McClient.pop(g);
	}

	// ---- text -------------------------------------------------------------------------------

	public static void centred(GuiGraphics g, Font font, Component text, int x, int y, int colour) {
		g.drawString(font, text, x - font.width(text) / 2, y, colour, false);
	}

	/** Centred with a one-pixel shadow of {@code shadow} under it (a title on a band). */
	public static void centred(GuiGraphics g, Font font, Component text, int x, int y, int colour, int shadow) {
		if (shadow != colour) g.drawString(font, text, x - font.width(text) / 2 + 1, y + 1, (shadow & 0x00FFFFFF) | 0x50000000, false);
		g.drawString(font, text, x - font.width(text) / 2, y, colour, false);
	}

	/** One line, cut with an ellipsis to the width. */
	public static void clip(GuiGraphics g, Font font, Component text, int x, int y, int width, int colour) {
		g.drawString(font, clipped(font, text, width), x, y, colour, false);
	}

	public static String clipped(Font font, Component text, int width) {
		String plain = text.getString();
		if (font.width(plain) <= width) return plain;
		return font.plainSubstrByWidth(plain, Math.max(0, width - font.width("…"))) + "…";
	}

	/** Up to {@code most} lines of a long text one under another; how many it took. */
	public static int wrap(GuiGraphics g, Font font, Component text, int x, int y, int width, int colour, int most) {
		List<FormattedCharSequence> lines = font.split(text, width);
		int shown = Math.min(most, lines.size());
		for (int i = 0; i < shown; i++) g.drawString(font, lines.get(i), x, y + i * 10, colour, false);
		return shown;
	}

	/** A long text as tooltip lines of {@code width}. */
	public static List<Component> lines(Font font, Component text, int width) {
		return font.split(text, width).stream().map(line -> (Component) Component.literal(plain(line))).toList();
	}

	public static String plain(FormattedCharSequence line) {
		StringBuilder out = new StringBuilder();
		line.accept((index, style, codePoint) -> {
			out.appendCodePoint(codePoint);
			return true;
		});
		return out.toString();
	}

	public static boolean over(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	/** The click of a button. */
	public static void press() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}
