package com.vnap.client.spire;

import com.vnap.VillagerLife;
import com.vnap.client.McClient;
import com.vnap.network.SpireJournalPayload;
import com.vnap.network.SpireNotePayload;
import com.vnap.spire.FloorGenerator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * The Chronicle of the Spire: every spire the player climbed (by its town), every tablet it read there, a chapter of
 * ten floors a row, a few chapters a page. Floors not yet read in a chapter it has begun, and the chapter after its
 * last, stand as sealed cells, so the way on shows; a click on a read one reads it again.
 */
public final class SpireJournalScreen extends Screen {
	private static final ResourceLocation ATLAS = VillagerLife.id("textures/gui/spire_tablet.png");
	private static final int W = 200;
	private static final int H = 180;
	private static final int CELL = 14;
	private static final int STEP = CELL + 2;
	/** Chapters on a page. */
	private static final int ROWS = 6;
	private static final int GRID_X = 30;
	private static final int GRID_Y = 40;
	private static final int INK = 0xFF3B2A1A;
	private static final int FAINT = 0xFF7A6046;

	private final SpireJournalPayload journal;
	private int climb;
	/** The first chapter on the page shown. */
	private int first;

	public SpireJournalScreen(SpireJournalPayload journal) {
		super(Component.translatable("spire.villager_life.journal"));
		this.journal = journal;
	}

	/** Chapters to show for a climb: those begun, and the next one sealed. */
	private static int chapters(SpireJournalPayload.Climb shown) {
		return Math.min(100, shown.read() / 10 + 1);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		com.vnap.client.ui.Ui.panel(graphics, com.vnap.client.ui.Ui.Theme.SPIRE, x - 10, y - 10, W + 20, H + 20);
		McClient.blit(graphics, ATLAS, x, y, 0, 0, W, H, 256, 256);
		Component title = Component.translatable("spire.villager_life.journal");
		graphics.drawString(font, title, x + W / 2 - font.width(title) / 2, y + 12, INK, false);
		if (journal.climbs().isEmpty()) {
			Component none = Component.translatable("spire.villager_life.journal.empty");
			graphics.drawString(font, none, x + W / 2 - font.width(none) / 2, y + H / 2, FAINT, false);
			return;
		}
		SpireJournalPayload.Climb shown = journal.climbs().get(climb);
		String town = Component.translatable("spire.villager_life.journal.town", shown.town()).getString();
		graphics.drawString(font, town, x + W / 2 - font.width(town) / 2, y + 24, FAINT, false);
		int chapters = chapters(shown);
		@Nullable Component hover = null;
		for (int row = 0; row < ROWS && first + row < chapters; row++) {
			int chapter = first + row;
			int ry = y + GRID_Y + row * STEP;
			String label = roman(chapter + 1);
			graphics.drawString(font, label, x + GRID_X - 4 - font.width(label), ry + 3, 0xFF7A4A20, false);
			if (mouseX >= x + 6 && mouseX < x + GRID_X - 2 && mouseY >= ry && mouseY < ry + CELL) {
				Component theme = Component.translatable("spire.villager_life.theme." + FloorGenerator.chapter(chapter).name());
				hover = Component.translatable("spire.villager_life.journal.chapter", chapter + 1, theme);
			}
			for (int i = 0; i < 10; i++) {
				int floor = chapter * 10 + i + 1;
				int cx = x + GRID_X + i * STEP;
				boolean read = floor <= shown.read();
				boolean keeper = floor % 10 == 0;
				boolean over = mouseX >= cx && mouseY >= ry && mouseX < cx + CELL && mouseY < ry + CELL;
				if (read) {
					graphics.fill(cx, ry, cx + CELL, ry + CELL, 0xFF8A6A44);
					graphics.fill(cx + 1, ry + 1, cx + CELL - 1, ry + CELL - 1, over ? 0xFFFFF2D0 : keeper ? 0xFFE8C48A : 0xFFEADBB8);
					number(graphics, floor, cx, ry, INK);
				} else {
					// sealed: a dotted outline and the number faint
					graphics.fill(cx, ry, cx + CELL, ry + CELL, 0x18000000);
					for (int d = 0; d < CELL; d += 3) {
						graphics.fill(cx + d, ry, cx + d + 1, ry + 1, 0x667A6046);
						graphics.fill(cx + d, ry + CELL - 1, cx + d + 1, ry + CELL, 0x667A6046);
						graphics.fill(cx, ry + d, cx + 1, ry + d + 1, 0x667A6046);
						graphics.fill(cx + CELL - 1, ry + d, cx + CELL, ry + d + 1, 0x667A6046);
					}
					number(graphics, floor, cx, ry, 0x997A6046);
				}
				if (keeper) graphics.fill(cx + CELL - 4, ry + 1, cx + CELL - 1, ry + 4, read ? 0xFFAA2A2C : 0x66AA2A2C);
			}
		}
		// the page's foot: arrows either side, which chapters these are, and which spire of how many
		int footY = y + H - 26;
		if (first > 0) McClient.blit(graphics, ATLAS, x + 14, footY, 212, 24, 12, 12, 256, 256);
		if (first + ROWS < chapters) McClient.blit(graphics, ATLAS, x + W - 26, footY, 200, 24, 12, 12, 256, 256);
		Component page = Component.translatable("spire.villager_life.journal.page", first + 1, Math.min(chapters, first + ROWS), chapters);
		graphics.drawString(font, page, x + W / 2 - font.width(page) / 2, footY + 2, FAINT, false);
		if (journal.climbs().size() > 1) {
			Component other = Component.translatable("spire.villager_life.journal.spires", climb + 1, journal.climbs().size());
			graphics.drawString(font, other, x + W / 2 - font.width(other) / 2, footY + 12, FAINT, false);
		}
		if (hover != null) McClient.tooltip(graphics, font, hover, mouseX, mouseY);
	}

	/** A floor's number in its cell, smaller when it has three figures. */
	private void number(GuiGraphics graphics, int floor, int cx, int ry, int colour) {
		String text = Integer.toString(floor);
		float scale = text.length() > 2 ? 0.7F : 1.0F;
		McClient.push(graphics);
		McClient.translate(graphics, cx + CELL / 2.0F - font.width(text) * scale / 2.0F, ry + (CELL - 8 * scale) / 2.0F + 0.5F, 0.0F);
		McClient.scale(graphics, scale, scale, 1.0F);
		graphics.drawString(font, text, 0, 0, colour, false);
		McClient.pop(graphics);
	}

	private static String roman(int n) {
		String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
		String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
		return n >= 100 ? Integer.toString(n) : tens[n / 10] + ones[n % 10];
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
		if (journal.climbs().isEmpty()) return false;
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		SpireJournalPayload.Climb shown = journal.climbs().get(climb);
		int chapters = chapters(shown);
		int footY = y + H - 26;
		if (inside(mouseX, mouseY, x + 14, footY, 12) && first > 0) {
			first = Math.max(0, first - ROWS);
			return true;
		}
		if (inside(mouseX, mouseY, x + W - 26, footY, 12) && first + ROWS < chapters) {
			first += ROWS;
			return true;
		}
		if (journal.climbs().size() > 1 && mouseY >= footY + 10 && mouseY < footY + 22) {
			climb = (climb + 1) % journal.climbs().size();
			first = 0;
			return true;
		}
		for (int row = 0; row < ROWS; row++) {
			for (int i = 0; i < 10; i++) {
				int floor = (first + row) * 10 + i + 1;
				int cx = x + GRID_X + i * STEP;
				int ry = y + GRID_Y + row * STEP;
				if (floor > shown.read() || !inside(mouseX, mouseY, cx, ry, CELL)) continue;
				int kind = floor <= com.vnap.spire.SpireNotes.STORY ? SpireNotePayload.STORY : SpireNotePayload.LAYER;
				long seed = FloorGenerator.seed(shown.seed(), floor);
				SpireNotePayload note = new SpireNotePayload(kind, floor, seed, FloorGenerator.theme(seed, floor).name(), "");
				McClient.setScreen(new SpireNoteScreen(SpireText.of(note), this));
				return true;
			}
		}
		return false;
	}

	private static boolean inside(double mx, double my, int x, int y, int size) {
		return mx >= x && my >= y && mx < x + size && my < y + size;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (journal.climbs().isEmpty()) return false;
		int chapters = chapters(journal.climbs().get(climb));
		first = Math.max(0, Math.min(Math.max(0, chapters - ROWS), first - (int) Math.signum(scrollY) * ROWS));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
