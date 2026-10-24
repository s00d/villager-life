package com.vnap.client;

import com.vnap.VillagerLife;
import com.vnap.network.NoticeBoardActionPayload;
import com.vnap.network.NoticeBoardPayload;
import com.vnap.village.market.Coins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The town's notice board: an oak-framed cork board with a paper sheet pinned to it and four tabs, the town at a
 * glance, its builds, what its stall waits for, and the town's plan from above, where a player picks a free lot and
 * buys it. The village's look (client/ui/Ui).
 */
public final class NoticeBoardScreen extends Screen {
	private static final int W = 410;
	private static final int H = 214;
	private static final float LABEL = 0.9F;
	/** The flag's tab: its icon only. */
	private static final int FLAG_TAB_W = 24;
	private static final int TAB_H = 18;
	private static final int INK = 0xFF3A2A1A;
	private static final int FADED = 0xFF7A6448;
	private static final int[] LOT_FILL = {0x5560B040, 0x55806040, 0x804070D0, 0x80D08030};
	private static final int[] LOT_EDGE = {0xFF3E8A2A, 0xFF6A5030, 0xFF2A50B0, 0xFFB06010};
	private static final String[] TABS = {"town", "errands", "builds", "wanted", "lots", "chronicle", "flag"};
	private static final ItemStack[] ICONS = {new ItemStack(Items.BELL), new ItemStack(Items.WRITABLE_BOOK), new ItemStack(Items.BRICKS), new ItemStack(Items.EMERALD),
			new ItemStack(Items.FILLED_MAP), new ItemStack(Items.WRITTEN_BOOK), new ItemStack(Items.RED_BANNER)};
	private static final int TOWN = 0;
	private static final int ERRANDS = 1;
	private static final int BUILDS = 2;
	private static final int WANTED = 3;
	private static final int LOTS = 4;
	private static final int CHRONICLE = 5;
	private static final int FLAG = 6;
	private static final int PER_ROW = 9;
	/** The tab last read: the board opens there again. */
	private static int lastTab;

	private final NoticeBoardPayload board;
	private int tab;
	private NoticeBoardPayload.@Nullable Lot selected;
	private int errand = -1;
	/** The flag being drawn: its ground colour, each layer's pattern (0: none) and colour. */
	private net.minecraft.world.item.DyeColor flagBase = net.minecraft.world.item.DyeColor.WHITE;
	private final int[] flagPatterns = new int[com.vnap.village.town.TownFlag.LAYERS];
	private final net.minecraft.world.item.DyeColor[] flagColours = new net.minecraft.world.item.DyeColor[com.vnap.village.town.TownFlag.LAYERS];

	public NoticeBoardScreen(NoticeBoardPayload board) {
		super(Component.translatable("block.villager_life.notice_board"));
		this.board = board;
		this.tab = lastTab;
		for (int i = 0; i < flagColours.length; i++) flagColours[i] = net.minecraft.world.item.DyeColor.RED;
		if (board.flag() >= 0) {
			flagBase = com.vnap.village.town.TownFlag.base(board.flag());
			for (int i = 0; i < flagPatterns.length; i++) {
				flagPatterns[i] = com.vnap.village.town.TownFlag.pattern(board.flag(), i);
				flagColours[i] = com.vnap.village.town.TownFlag.colour(board.flag(), i);
			}
		}
	}

	private int flagCode() {
		return com.vnap.village.town.TownFlag.code(flagBase, flagPatterns, flagColours);
	}

	private int tabX(int i) {
		int x = left() + 14;
		for (int j = 0; j < i; j++) x += tabW(j) + 2;
		return x;
	}

	/** A tab as wide as its icon and its word (the chronicle's and the flag's: their icons only). */
	private int tabW(int i) {
		if (i >= CHRONICLE) return FLAG_TAB_W;
		return 24 + (int) Math.ceil(LABEL * font.width(Component.translatable("board.villager_life.tab." + TABS[i])));
	}

	private int left() {
		return (width - W) / 2;
	}

	private int top() {
		return (height - H) / 2;
	}

	/** The board's buttons, by the tab open: buy a lot, take or give back an errand, raise the flag. */
	private List<com.vnap.client.ui.Ui.Action> actions() {
		int bottom = top() + H - 36;
		NoticeBoardPayload.Lot lot = selected;
		NoticeBoardPayload.Errand chosen = chosen();
		int coins = minecraft != null && minecraft.player != null ? Coins.count(minecraft.player.getInventory()) : 0;
		return List.of(
				new com.vnap.client.ui.Ui.Action(left() + W - 86, bottom, 64, 18, Component.translatable("board.villager_life.buy"),
						tab == LOTS && lot != null && lot.state() == NoticeBoardPayload.Lot.FREE, lot != null && coins >= lot.price(),
						() -> PacketDistributor.sendToServer(new NoticeBoardActionPayload(board.board(), NoticeBoardActionPayload.BUY_LOT,
								java.util.Objects.requireNonNull(lot).id()))),
				new com.vnap.client.ui.Ui.Action(left() + W - 160, bottom, 70, 18, Component.translatable("board.villager_life.take"),
						tab == ERRANDS && chosen != null && chosen.state() == NoticeBoardPayload.Errand.OPEN, true,
						() -> PacketDistributor.sendToServer(new NoticeBoardActionPayload(board.board(), NoticeBoardActionPayload.TAKE_ERRAND, errand))),
				new com.vnap.client.ui.Ui.Action(left() + W - 116, bottom, 94, 18, Component.translatable("board.villager_life.raise_flag"),
						tab == FLAG, true,
						() -> PacketDistributor.sendToServer(new NoticeBoardActionPayload(board.board(), NoticeBoardActionPayload.SET_FLAG, flagCode()))),
				new com.vnap.client.ui.Ui.Action(left() + W - 98, bottom, 76, 18, Component.translatable("board.villager_life.cancel"),
						tab == ERRANDS && board.errands().stream().anyMatch(one -> one.state() == NoticeBoardPayload.Errand.MINE), true,
						() -> PacketDistributor.sendToServer(new NoticeBoardActionPayload(board.board(), NoticeBoardActionPayload.CANCEL_ERRAND, 0))));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		renderTransparentBackground(g);
		int x = left();
		int y = top();
		// the village's window (Ui): the tabs behind the sheet, the sheet, the open tab over it
		com.vnap.client.ui.Ui.panel(g, com.vnap.client.ui.Ui.Theme.VILLAGE, x, y, W, H);
		for (int i = 0; i < TABS.length; i++) if (i != tab) tab(g, i, mouseX, mouseY);
		int sx = x + 12;
		int sy = y + 30;
		int sw = W - 24;
		int sh = H - 42;
		com.vnap.client.ui.Ui.inset(g, com.vnap.client.ui.Ui.Theme.VILLAGE, sx, sy, sw, sh);
		tab(g, tab, mouseX, mouseY);
		List<Component> tooltip = new ArrayList<>();
		switch (tab) {
			case TOWN -> town(g, sx + 10, sy + 10, sw - 20);
			case ERRANDS -> errands(g, sx + 10, sy + 10, sw - 20, mouseX, mouseY);
			case BUILDS -> builds(g, sx + 10, sy + 10, sw - 20, sh - 16);
			case WANTED -> wanted(g, sx + 10, sy + 10, sw - 20, sh - 16, mouseX, mouseY, tooltip);
			case FLAG -> flag(g, sx + 10, sy + 10, sw - 20);
			case CHRONICLE -> chronicle(g, sx + 10, sy + 10, sw - 20, sh - 16);
			default -> lots(g, sx + 6, sy + 8, sw - 12, mouseX, mouseY, tooltip);
		}
		super.render(g, mouseX, mouseY, partialTick);
		com.vnap.client.ui.Ui.draw(g, com.vnap.client.ui.Ui.Theme.VILLAGE, font, actions(), mouseX, mouseY);
		if (!tooltip.isEmpty()) McClient.tooltip(g, font, tooltip, mouseX, mouseY);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	}

	private static final int YOU = 0xFFD02020;

	private void tab(GuiGraphics g, int i, int mouseX, int mouseY) {
		int x = tabX(i);
		int w = tabW(i);
		boolean active = i == tab;
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= top() + 10 && mouseY < top() + 10 + TAB_H;
		int y = top() + (active ? 10 : 13);
		// the open tab joins the sheet (which starts at top + 30) and goes no further into it
		int h = active ? TAB_H + 3 : TAB_H;
		com.vnap.client.ui.Ui.tab(g, com.vnap.client.ui.Ui.Theme.VILLAGE, x, y, w, h + (active ? 1 : 0), active);
		if (!active && hover) g.fill(x + 2, y + 2, x + w - 2, y + h, 0x18FFFFFF);
		McClient.push(g);
		McClient.translate(g, x + 3, y + 2, 0);
		McClient.scale(g, 0.875F, 0.875F, 1.0F);
		g.renderItem(ICONS[i], 0, 0);
		McClient.pop(g);
		if (i >= CHRONICLE) return;
		Component label = Component.translatable("board.villager_life.tab." + TABS[i]);
		// a touch smaller than the page's text: seven tabs share the sheet's width
		McClient.push(g);
		McClient.translate(g, x + 20, y + 6, 0);
		McClient.scale(g, LABEL, LABEL, 1.0F);
		g.drawString(font, label, 0, 0, active ? INK : FADED, false);
		McClient.pop(g);
	}

	//? if >=1.21.9 {
	/*@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		return click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
	}
	*///?} else {
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return click(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
	}
	//?}

	@SuppressWarnings("UnusedVariable") // any button switches a tab
	private boolean click(double mouseX, double mouseY, int button) {
		if (com.vnap.client.ui.Ui.click(actions(), mouseX, mouseY)) return true;
		for (int i = 0; i < TABS.length; i++) {
			int x = tabX(i);
			if (mouseX >= x && mouseX < x + tabW(i) && mouseY >= top() + 10 && mouseY < top() + 10 + TAB_H) {
				tab = i;
				lastTab = i;
				return true;
			}
		}
		if (tab == ERRANDS) {
			int row = rowAt(mouseX, mouseY);
			if (row >= 0) {
				errand = board.errands().get(row).id();
				return true;
			}
		}
		if (tab == FLAG && flagClicked(mouseX, mouseY)) return true;
		if (tab == LOTS) {
			NoticeBoardPayload.Lot lot = lotAt(mouseX, mouseY);
			if (lot != null) {
				selected = lot;
				return true;
			}
		}
		return false;
	}

	// ---- the errands --------------------------------------------------------------------------

	private static final int ROW = 22;
	private static final int ROWS = 6;

	private NoticeBoardPayload.@Nullable Errand chosen() {
		for (NoticeBoardPayload.Errand one : board.errands()) if (one.id() == errand) return one;
		return null;
	}

	private int rowAt(double mouseX, double mouseY) {
		int x = left() + 22;
		int y = top() + 40 + 14;
		for (int i = 0; i < Math.min(ROWS, board.errands().size()); i++) {
			if (mouseX >= x && mouseX < left() + W - 22 && mouseY >= y + i * ROW && mouseY < y + (i + 1) * ROW - 2) return i;
		}
		return -1;
	}

	private void errands(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
		g.drawString(font, Component.translatable("board.villager_life.errands"), x, y, INK, false);
		int ry = y + 14;
		if (board.errands().isEmpty()) {
			paragraph(g, Component.translatable("board.villager_life.no_errands"), x, ry, w, FADED);
			return;
		}
		int hover = rowAt(mouseX, mouseY);
		for (int i = 0; i < Math.min(ROWS, board.errands().size()); i++) {
			NoticeBoardPayload.Errand one = board.errands().get(i);
			int top = ry + i * ROW;
			int fill = one.id() == errand ? 0x40A08050 : i == hover ? 0x20A08050 : one.state() == NoticeBoardPayload.Errand.MINE ? 0x302A50B0 : 0x10000000;
			g.fill(x - 2, top - 2, x + w, top + ROW - 4, fill);
			g.renderItem(one.item(), x, top);
			g.renderItemDecorations(font, one.item(), x, top);
			Component what = Component.translatable("board.villager_life.errand_line", one.name(), one.item().getCount(), one.item().getHoverName());
			g.drawString(font, font.substrByWidth(what, w - 120).getString(), x + 22, top + 1, INK, false);
			Component state = switch (one.state()) {
				case NoticeBoardPayload.Errand.MINE -> Component.translatable("board.villager_life.errand_mine");
				case NoticeBoardPayload.Errand.TAKEN -> Component.translatable("board.villager_life.errand_taken");
				default -> Component.translatable("board.villager_life.errand_days", one.daysLeft());
			};
			g.drawString(font, state, x + 22, top + 10, one.state() == NoticeBoardPayload.Errand.MINE ? 0xFF2A50B0 : FADED, false);
			Component reward = Coins.text(one.reward());
			g.drawString(font, reward, x + w - font.width(reward) - 4, top + 5, 0xFF2A6A1A, false);
		}
		var hint = font.split(Component.translatable("board.villager_life.errand_hint"), w - 170);
		int hy = top() + H - 16 - hint.size() * 10;
		for (var line : hint) {
			g.drawString(font, line, x, hy, FADED, false);
			hy += 10;
		}
	}

	// ---- the town ----------------------------------------------------------------------------

	private void town(GuiGraphics g, int x, int y, int w) {
		int ty = y;
		g.drawString(font, Component.translatable("board.villager_life.title"), x, ty, INK, false);
		ty += 14;
		if (!board.mayor().isEmpty()) {
			g.drawString(font, Component.translatable("board.villager_life.mayor", board.mayor()), x, ty, INK, false);
			ty += 11;
		}
		g.drawString(font, Component.translatable("board.villager_life.people", board.people(), board.limit()), x, ty, INK, false);
		com.vnap.client.ui.Ui.bar(g, com.vnap.client.ui.Ui.Theme.VILLAGE, x + 150, ty, 100, board.limit() <= 0 ? 0.0 : (double) board.people() / board.limit(), 0.0);
		ty += 11;
		g.drawString(font, Component.translatable("board.villager_life.treasury", Coins.text(board.treasury())), x, ty, INK, false);
		ty += 11;
		g.drawString(font, Component.translatable("board.villager_life.mood", board.mood()), x, ty, board.mood() < 0 ? 0xFF8A2A1A : INK, false);
		com.vnap.client.ui.Ui.centreBar(g, com.vnap.client.ui.Ui.Theme.VILLAGE, x + 150, ty, 100, board.mood() / 100.0);
		ty += 15;
		if (board.fair()) {
			g.drawString(font, Component.translatable("board.villager_life.fair"), x, ty, 0xFF2A6A1A, false);
			ty += 11;
		} else if (board.caravanIn() >= 0) {
			g.drawString(font, Component.translatable("board.villager_life.caravan", board.caravanIn()), x, ty, FADED, false);
			ty += 11;
		}
		if (!board.shipments().isEmpty()) {
			// what the caravan is bringing players, as many as fit
			g.drawString(font, Component.translatable("board.villager_life.shipments", board.caravanIn()), x, ty, INK, false);
			ty += 11;
			int shown = 0;
			for (NoticeBoardPayload.Shipment shipment : board.shipments()) {
				if (shown++ >= 4) {
					g.drawString(font, Component.translatable("board.villager_life.shipments_more", board.shipments().size() - 4), x + 4, ty, FADED, false);
					ty += 11;
					break;
				}
				Component line = Component.translatable("board.villager_life.shipment", shipment.item().getCount(), shipment.item().getHoverName(), shipment.who());
				g.drawString(font, line, x + 4, ty, FADED, false);
				ty += 10;
			}
			ty += 2;
		}
		if (board.serviceIn() >= 0) {
			Component service = board.serviceIn() == 0 ? Component.translatable("board.villager_life.service_today")
					: Component.translatable("board.villager_life.service", board.serviceIn());
			g.drawString(font, service, x, ty, FADED, false);
			ty += 11;
		}
		int mine = (int) board.plan().lots.stream().filter(lot -> lot.state() == NoticeBoardPayload.Lot.MINE).count();
		if (mine > 0) {
			g.drawString(font, Component.translatable("board.villager_life.your_lots", mine), x, ty, 0xFF2A50B0, false);
			ty += 11;
		}
		if (board.lost() > 0) {
			ty += 4;
			g.drawString(font, Component.translatable("board.villager_life.lost", board.lost()), x, ty, INK, false);
			ty += 11;
			ty = paragraph(g, Component.translatable("board.villager_life.lost_hint"), x, ty, w, FADED);
		}
	}

	private int paragraph(GuiGraphics g, Component text, int x, int y, int w, int colour) {
		for (var line : font.split(text, w)) {
			g.drawString(font, line, x, y, colour, false);
			y += 10;
		}
		return y;
	}

	// ---- the builds ---------------------------------------------------------------------------

	private void builds(GuiGraphics g, int x, int y, int w, int h) {
		g.drawString(font, Component.translatable("board.villager_life.builds"), x, y, INK, false);
		int ty = y + 14;
		if (board.projects().isEmpty()) {
			g.drawString(font, Component.translatable("board.villager_life.no_builds"), x, ty, FADED, false);
			return;
		}
		int column = (w - 10) / 2;
		int row = 0;
		int perColumn = Math.max(1, (h - 14) / 20);
		for (NoticeBoardPayload.Project project : board.projects()) {
			if (row >= perColumn * 2) break;
			int cx = x + (row / perColumn) * (column + 10);
			int cy = ty + (row % perColumn) * 20;
			Component name = project.name().isEmpty() ? Component.translatable(project.label())
					: Component.translatable(project.label()).append(": " + project.name());
			g.drawString(font, font.substrByWidth(name, column).getString(), cx, cy, INK, false);
			com.vnap.client.ui.Ui.bar(g, com.vnap.client.ui.Ui.Theme.VILLAGE, cx, cy + 10, column - 2,
					project.total() <= 0 ? 0.0 : (double) (project.total() - project.left()) / project.total(), 0.0);
			row++;
		}
	}

	// ---- the wanted things --------------------------------------------------------------------

	private void wanted(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, List<Component> tooltip) {
		g.drawString(font, Component.translatable("board.villager_life.wanted"), x, y, INK, false);
		int wy = y + 14;
		if (board.wanted().isEmpty()) {
			paragraph(g, Component.translatable("board.villager_life.nothing_wanted"), x, wy, w, FADED);
		}
		for (int i = 0; i < board.wanted().size(); i++) {
			NoticeBoardPayload.Wanted want = board.wanted().get(i);
			int ix = x + (i % PER_ROW) * 28;
			int iy = wy + (i / PER_ROW) * 28;
			g.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0x20000000);
			g.renderItem(want.item(), ix, iy);
			String count = want.item().getCount() > 999 ? "999+" : Integer.toString(want.item().getCount());
			McClient.push(g);
			McClient.translate(g, 0, 0, 200);
			g.drawString(font, count, ix + 17 - font.width(count), iy + 9, 0xFFFFFFFF, true);
			McClient.pop(g);
			String price = Coins.text(want.price()).getString();
			McClient.push(g);
			McClient.scale(g, 0.5F, 0.5F, 1.0F);
			g.drawString(font, price, (ix + 1) * 2, (iy + 18) * 2, FADED, false);
			McClient.pop(g);
			if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
				tooltip.add(want.item().getHoverName());
				tooltip.add(Component.translatable("board.villager_life.want_line", want.item().getCount(), Coins.text(want.price())));
			}
		}
		var how = font.split(Component.translatable("board.villager_life.how"), w);
		int hy = y + h - how.size() * 10;
		for (var line : how) {
			g.drawString(font, line, x, hy, FADED, false);
			hy += 10;
		}
	}

	// ---- the chronicle ------------------------------------------------------------------------

	private void chronicle(GuiGraphics g, int x, int y, int w, int h) {
		g.drawString(font, Component.translatable("board.villager_life.chronicle"), x, y, INK, false);
		int ty = y + 14;
		if (board.chronicle().isEmpty()) {
			paragraph(g, Component.translatable("board.villager_life.no_chronicle"), x, ty, w, FADED);
			return;
		}
		// the newest first, as many as the sheet holds
		for (int i = board.chronicle().size() - 1; i >= 0 && ty < y + h - 10; i--) {
			NoticeBoardPayload.Chronicle line = board.chronicle().get(i);
			String day = Component.translatable("board.villager_life.chronicle_day", line.day() + 1).getString();
			g.drawString(font, day, x, ty, FADED, false);
			// "@key" is a translation (an item's name), in the reader's own language
			Object[] args = line.args().stream().map(arg -> arg.startsWith("@") ? (Object) Component.translatable(arg.substring(1)) : arg).toArray();
			Component text = Component.translatable("chronicle.villager_life." + line.key(), args);
			for (var part : font.split(text, w - 56)) {
				if (ty >= y + h - 10) break;
				g.drawString(font, part, x + 56, ty, INK, false);
				ty += 10;
			}
			ty += 2;
		}
	}

	// ---- the flag -----------------------------------------------------------------------------

	private static final int SWATCH = 10;
	private static final int FLAG_ROW = 28;

	/** Where the drawing's controls start: right of the banner's picture. */
	private int flagX() {
		return left() + 22 + 92;
	}

	private int flagY() {
		return top() + 40 + 14;
	}

	/** Row {@code row} (-1: the ground colour, else a layer): the top of its colour swatches. */
	private int swatchY(int row) {
		return row < 0 ? flagY() : flagY() + 18 + row * FLAG_ROW + 11;
	}

	private void flag(GuiGraphics g, int x, int y, int w) {
		g.drawString(font, Component.translatable("board.villager_life.flag"), x, y, INK, false);
		if (minecraft != null && minecraft.level != null) {
			g.fill(x + 2, y + 18, x + 78, y + 118, 0x20000000);
			McClient.push(g);
			McClient.translate(g, x + 8, y + 22, 0);
			McClient.scale(g, 4.0F, 4.0F, 1.0F);
			g.renderItem(com.vnap.village.town.TownFlag.item(minecraft.level.registryAccess(), flagCode()), 0, 0);
			McClient.pop(g);
		}
		int fx = flagX();
		g.drawString(font, Component.translatable("board.villager_life.flag_base"), fx, swatchY(-1) - 11, FADED, false);
		swatches(g, fx, swatchY(-1), flagBase);
		for (int i = 0; i < flagPatterns.length; i++) {
			int ry = swatchY(i) - 11;
			arrow(g, fx, ry, "<");
			Component name = Component.translatable("board.villager_life.flag.pattern." + flagPatterns[i]);
			g.drawString(font, font.substrByWidth(name, 150).getString(), fx + 14, ry + 1, flagPatterns[i] == 0 ? FADED : INK, false);
			arrow(g, fx + 168, ry, ">");
			if (flagPatterns[i] != 0) swatches(g, fx, swatchY(i), flagColours[i]);
		}
		paragraph(g, Component.translatable(board.flag() >= 0 ? "board.villager_life.flag_hint_up" : "board.villager_life.flag_hint"),
				x, top() + H - 52, w - 130, FADED);
	}

	private void arrow(GuiGraphics g, int x, int y, String text) {
		g.fill(x, y, x + 10, y + 10, 0x30000000);
		g.drawString(font, text, x + 2, y + 1, INK, false);
	}

	private void swatches(GuiGraphics g, int x, int y, net.minecraft.world.item.DyeColor chosen) {
		for (net.minecraft.world.item.DyeColor colour : net.minecraft.world.item.DyeColor.values()) {
			int sx = x + colour.getId() * (SWATCH + 2);
			g.fill(sx - 1, y - 1, sx + SWATCH + 1, y + SWATCH + 1, colour == chosen ? 0xFF000000 : 0x40000000);
			g.fill(sx, y, sx + SWATCH, y + SWATCH, colour.getTextureDiffuseColor() | 0xFF000000);
		}
	}

	private net.minecraft.world.item.@Nullable DyeColor swatchAt(double mouseX, double mouseY, int y) {
		if (mouseY < y || mouseY >= y + SWATCH) return null;
		int i = (int) Math.floor((mouseX - flagX()) / (SWATCH + 2));
		return i < 0 || i >= 16 || mouseX - flagX() - i * (SWATCH + 2) >= SWATCH ? null : net.minecraft.world.item.DyeColor.byId(i);
	}

	private boolean flagClicked(double mouseX, double mouseY) {
		net.minecraft.world.item.@Nullable DyeColor base = swatchAt(mouseX, mouseY, swatchY(-1));
		if (base != null) {
			flagBase = base;
			return true;
		}
		int patterns = com.vnap.village.town.TownFlag.PATTERNS.size() + 1;
		for (int i = 0; i < flagPatterns.length; i++) {
			int ry = swatchY(i) - 11;
			if (mouseY >= ry && mouseY < ry + 10) {
				if (mouseX >= flagX() && mouseX < flagX() + 10) {
					flagPatterns[i] = Math.floorMod(flagPatterns[i] - 1, patterns);
					return true;
				}
				if (mouseX >= flagX() + 168 && mouseX < flagX() + 178) {
					flagPatterns[i] = (flagPatterns[i] + 1) % patterns;
					return true;
				}
			}
			net.minecraft.world.item.@Nullable DyeColor colour = flagPatterns[i] == 0 ? null : swatchAt(mouseX, mouseY, swatchY(i));
			if (colour != null) {
				flagColours[i] = colour;
				return true;
			}
		}
		return false;
	}

	// ---- the plan -----------------------------------------------------------------------------

	/** Where the map is drawn and its scale (screen pixels per block); its side is the sheet's height. */
	private int mapX() {
		return left() + 18;
	}

	private int mapY() {
		return top() + 38;
	}

	private int mapSide() {
		return H - 60;
	}

	private float scale() {
		return mapSide() / (float) (board.plan().radius * 2 + 1);
	}

	private int sx(int dx) {
		return mapX() + Math.round((dx + board.plan().radius) * scale());
	}

	private int sy(int dz) {
		return mapY() + Math.round((dz + board.plan().radius) * scale());
	}

	private NoticeBoardPayload.@Nullable Lot lotAt(double mouseX, double mouseY) {
		for (NoticeBoardPayload.Lot lot : board.plan().lots) {
			if (mouseX >= sx(lot.minX()) && mouseX < sx(lot.maxX() + 1) && mouseY >= sy(lot.minZ()) && mouseY < sy(lot.maxZ() + 1)) return lot;
		}
		return null;
	}

	private void lots(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY, List<Component> tooltip) {
		NoticeBoardPayload.PlanMap plan = board.plan();
		int side = mapSide();
		int mx = mapX();
		int my = mapY();
		g.fill(mx - 1, my - 1, mx + side + 1, my + side + 1, 0xFF6B4A2B);
		g.fill(mx, my, mx + side, my + side, 0xFFD9CBA0);
		g.enableScissor(mx, my, mx + side, my + side);
		NoticeBoardPayload.Lot hover = lotAt(mouseX, mouseY);
		for (NoticeBoardPayload.Lot lot : plan.lots) {
			int x0 = sx(lot.minX());
			int y0 = sy(lot.minZ());
			int x1 = sx(lot.maxX() + 1);
			int y1 = sy(lot.maxZ() + 1);
			g.fill(x0, y0, x1, y1, LOT_FILL[lot.state()]);
			int edge = lot.equals(hover) || lot.equals(selected) ? 0xFF000000 : LOT_EDGE[lot.state()];
			g.renderOutline(x0, y0, x1 - x0, y1 - y0, edge);
		}
		for (int i = 0; i + 3 < plan.buildings.length; i += 4) {
			g.fill(sx(plan.buildings[i]), sy(plan.buildings[i + 1]), sx(plan.buildings[i + 2] + 1), sy(plan.buildings[i + 3] + 1), 0xFF5A4030);
		}
		for (int cell : plan.streets) cell(g, cell, 0xFFB09870);
		for (int cell : plan.roads) cell(g, cell, 0xFF7A5A38);
		g.fill(sx(0) - 1, sy(0) - 1, sx(1) + 1, sy(1) + 1, 0xFFE0B020);
		// red, ringed in white: blue is a lot of your own
		g.fill(sx(plan.youX) - 2, sy(plan.youZ) - 2, sx(plan.youX) + 3, sy(plan.youZ) + 3, 0xFFFFFFFF);
		g.fill(sx(plan.youX) - 1, sy(plan.youZ) - 1, sx(plan.youX) + 2, sy(plan.youZ) + 2, YOU);
		g.disableScissor();

		int px = mx + side + 8;
		int pw = x + w - px;
		int py = y + 2;
		g.drawString(font, font.substrByWidth(Component.translatable("board.villager_life.plan"), pw).getString(), px, py, INK, false);
		py += 13;
		String[] legend = {"free", "house", "mine", "theirs"};
		for (int i = 0; i < legend.length; i++) {
			g.fill(px, py + 1, px + 7, py + 8, LOT_FILL[i] | 0xFF000000);
			g.renderOutline(px, py + 1, 7, 7, LOT_EDGE[i]);
			g.drawString(font, font.substrByWidth(Component.translatable("board.villager_life.legend." + legend[i]), pw - 10).getString(), px + 10, py, FADED, false);
			py += 10;
		}
		g.fill(px, py + 3, px + 7, py + 5, 0xFF7A5A38);
		g.drawString(font, font.substrByWidth(Component.translatable("board.villager_life.legend.road"), pw - 10).getString(), px + 10, py, FADED, false);
		py += 10;
		g.fill(px + 1, py + 2, px + 6, py + 7, 0xFFE0B020);
		g.drawString(font, font.substrByWidth(Component.translatable("board.villager_life.legend.bell"), pw - 10).getString(), px + 10, py, FADED, false);
		py += 10;
		g.fill(px + 1, py + 2, px + 6, py + 7, 0xFFFFFFFF);
		g.fill(px + 2, py + 3, px + 5, py + 6, YOU);
		g.drawString(font, font.substrByWidth(Component.translatable("board.villager_life.legend.you"), pw - 10).getString(), px + 10, py, FADED, false);
		py += 15;
		NoticeBoardPayload.Lot lot = selected;
		if (lot == null) {
			py = paragraph(g, Component.translatable("board.villager_life.pick_lot"), px, py, pw, INK);
			paragraph(g, Component.translatable("board.villager_life.lots_hint"), px, py + 4, pw, FADED);
		} else {
			g.drawString(font, Component.translatable("board.villager_life.lot", lot.id(), lot.maxX() - lot.minX() + 1, lot.maxZ() - lot.minZ() + 1), px, py, INK, false);
			py += 11;
			py = paragraph(g, where(lot), px, py, pw, FADED) + 2;
			String state = switch (lot.state()) {
				case NoticeBoardPayload.Lot.FREE -> "free";
				case NoticeBoardPayload.Lot.HOUSE -> "house";
				case NoticeBoardPayload.Lot.MINE -> "mine";
				default -> "theirs";
			};
			g.drawString(font, Component.translatable("board.villager_life.lot_state." + state), px, py, LOT_EDGE[lot.state()], false);
			py += 11;
			if (lot.state() == NoticeBoardPayload.Lot.FREE) {
				g.drawString(font, Component.translatable("board.villager_life.lot_price", Coins.text(lot.price())), px, py, INK, false);
				py += 11;
				int coins = minecraft == null || minecraft.player == null ? 0 : Coins.count(minecraft.player.getInventory());
				g.drawString(font, Component.translatable("board.villager_life.your_coins", Coins.text(coins)), px, py, coins >= lot.price() ? FADED : 0xFF8A2A1A, false);
			} else if (!lot.owner().isEmpty()) {
				g.drawString(font, Component.translatable("board.villager_life.lot_owner", lot.owner()), px, py, INK, false);
			}
		}
		if (hover != null) {
			tooltip.add(Component.translatable("board.villager_life.lot", hover.id(), hover.maxX() - hover.minX() + 1, hover.maxZ() - hover.minZ() + 1));
			tooltip.add(where(hover));
			if (hover.state() == NoticeBoardPayload.Lot.FREE) tooltip.add(Component.translatable("board.villager_life.lot_price", Coins.text(hover.price())));
			else if (!hover.owner().isEmpty()) tooltip.add(Component.translatable("board.villager_life.lot_owner", hover.owner()));
		}
	}

	/** Its area, and where its middle is from the bell (the map's columns are the bell's). */
	private static Component where(NoticeBoardPayload.Lot lot) {
		int dx = (lot.minX() + lot.maxX()) / 2;
		int dz = (lot.minZ() + lot.maxZ()) / 2;
		String side = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? "east" : "west") : (dz >= 0 ? "south" : "north");
		int area = (lot.maxX() - lot.minX() + 1) * (lot.maxZ() - lot.minZ() + 1);
		return Component.translatable("board.villager_life.lot_where", area, Math.round(Math.sqrt((double) dx * dx + (double) dz * dz)),
				Component.translatable("board.villager_life.dir." + side));
	}

	private void cell(GuiGraphics g, int packed, int colour) {
		int dx = NoticeBoardPayload.PlanMap.dx(packed);
		int dz = NoticeBoardPayload.PlanMap.dz(packed);
		int x0 = sx(dx);
		int y0 = sy(dz);
		g.fill(x0, y0, Math.max(x0 + 1, sx(dx + 1)), Math.max(y0 + 1, sy(dz + 1)), colour);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
