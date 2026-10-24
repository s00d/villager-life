package com.vnap.client.spire;

import com.vnap.VillagerLife;
import com.vnap.client.McClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A spire tablet read: a sheet of old parchment on its stone slab, its title, who wrote it, the text page by page,
 * the Builders' seal. It stays until closed (Esc or a click on the seal); arrows turn its pages.
 */
public final class SpireNoteScreen extends Screen {
	private static final ResourceLocation ATLAS = VillagerLife.id("textures/gui/spire_tablet.png");
	private static final int W = 200;
	private static final int H = 180;
	private static final int MARGIN = 14;
	private static final int INK = 0xFF3B2A1A;
	private static final int FAINT = 0xFF7A6046;
	private static final int TITLE = 0xFF5B2A1E;

	private final SpireText.Note note;
	private final @Nullable Screen back;
	private final List<List<FormattedCharSequence>> pages = new ArrayList<>();
	private int page;

	public SpireNoteScreen(SpireText.Note note, @Nullable Screen back) {
		super(Component.literal(note.title()));
		this.note = note;
		this.back = back;
	}

	@Override
	protected void init() {
		pages.clear();
		// the first page under the heading, title and byline; the rest from the top; the bottom kept for arrows and seal
		int first = (H - 30 - top()) / 10;
		int rest = (H - 30 - MARGIN) / 10;
		List<FormattedCharSequence> current = new ArrayList<>();
		for (String paragraph : note.paragraphs()) {
			List<FormattedCharSequence> lines = font.split(Component.literal(paragraph), W - MARGIN * 2);
			for (FormattedCharSequence line : lines) {
				if (current.size() >= (pages.isEmpty() ? first : rest)) {
					pages.add(current);
					current = new ArrayList<>();
				}
				current.add(line);
			}
			if (!current.isEmpty() && current.size() < (pages.isEmpty() ? first : rest)) current.add(FormattedCharSequence.EMPTY);
		}
		if (!current.isEmpty()) pages.add(current);
		page = Math.min(page, Math.max(0, pages.size() - 1));
	}

	/** Where the text starts on the first page: under the heading, the title's lines and the byline. */
	private int top() {
		int top = MARGIN;
		if (!note.heading().isEmpty()) top += 12;
		if (!note.title().isEmpty()) top += 10 * font.split(Component.literal(note.title()).withStyle(net.minecraft.ChatFormatting.BOLD), W - MARGIN * 2).size();
		if (!note.author().isEmpty() || !note.date().isEmpty()) top += 10;
		return top + 4;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		// the sheet on the spire's slab in brass
		com.vnap.client.ui.Ui.panel(graphics, com.vnap.client.ui.Ui.Theme.SPIRE, x - 10, y - 10, W + 20, H + 20);
		McClient.blit(graphics, ATLAS, x, y, 0, 0, W, H, 256, 256);
		int cx = x + W / 2;
		int ty = y + MARGIN;
		if (page == 0 && !note.heading().isEmpty()) {
			graphics.drawString(font, note.heading(), cx - font.width(note.heading()) / 2, ty, FAINT, false);
			ty += 12;
		}
		if (page == 0 && !note.title().isEmpty()) {
			for (FormattedCharSequence line : font.split(Component.literal(note.title()).withStyle(net.minecraft.ChatFormatting.BOLD), W - MARGIN * 2)) {
				graphics.drawString(font, line, cx - font.width(line) / 2, ty, TITLE, false);
				ty += 10;
			}
		}
		String byline = note.author().isEmpty() ? note.date() : note.date().isEmpty() ? note.author() : note.author() + ", " + note.date();
		if (page == 0 && !byline.isEmpty()) {
			graphics.drawString(font, byline, cx - font.width(byline) / 2, ty, FAINT, false);
			ty += 10;
		}
		if (page == 0) ty += 4;
		if (!pages.isEmpty()) {
			for (FormattedCharSequence line : pages.get(page)) {
				graphics.drawString(font, line, x + MARGIN, ty, INK, false);
				ty += 10;
			}
		}
		McClient.blit(graphics, ATLAS, x + W - 30, y + H - 30, 200, 0, 24, 24, 256, 256);
		if (pages.size() > 1) {
			if (page + 1 < pages.size()) McClient.blit(graphics, ATLAS, x + W / 2 + 6, y + H - 22, 200, 24, 12, 12, 256, 256);
			if (page > 0) McClient.blit(graphics, ATLAS, x + W / 2 - 18, y + H - 22, 212, 24, 12, 12, 256, 256);
			String count = (page + 1) + "/" + pages.size();
			graphics.drawString(font, count, x + MARGIN, y + H - 20, FAINT, false);
		}
	}

	//? if >=1.21.9 {
	/*@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		return click(event.x(), event.y()) || super.mouseClicked(event, doubleClick);
	}
	*///?} else {
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return click(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
	}
	//?}

	private boolean click(double mouseX, double mouseY) {
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		if (inside(mouseX, mouseY, x + W / 2 + 6, y + H - 22, 12) && page + 1 < pages.size()) {
			page++;
			return true;
		}
		if (inside(mouseX, mouseY, x + W / 2 - 18, y + H - 22, 12) && page > 0) {
			page--;
			return true;
		}
		if (inside(mouseX, mouseY, x + W - 30, y + H - 30, 24)) {
			onClose();
			return true;
		}
		return false;
	}

	//? if >=1.21.9 {
	/*@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		return key(event.key()) || super.keyPressed(event);
	}
	*///?} else {
	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		return key(key) || super.keyPressed(key, scan, modifiers);
	}
	//?}

	private boolean key(int key) {
		if (key == 262 && page + 1 < pages.size()) {
			page++;
			return true;
		}
		if (key == 263 && page > 0) {
			page--;
			return true;
		}
		return false;
	}

	private static boolean inside(double mx, double my, int x, int y, int size) {
		return mx >= x && my >= y && mx < x + size && my < y + size;
	}

	@Override
	public void onClose() {
		McClient.setScreen(back);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
