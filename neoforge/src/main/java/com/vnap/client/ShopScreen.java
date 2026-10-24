package com.vnap.client;

import com.vnap.block.DecorBlock;
import com.vnap.client.ui.Ui;
import com.vnap.item.LifeItems;
import com.vnap.network.ShopActionPayload;
import com.vnap.network.ShopListingPayload;
import com.vnap.village.market.Coins;
import com.vnap.village.market.ShopMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The stall's window: tabs over the shelf (all, food, home, building, from players, other), the shelf with a scroll
 * bar (a click buys one, shift-click a stack), the sell slot with what the stall pays, the player's coins, the
 * inventory below. Big counts read 1.2k. The village's look (client/ui/Ui).
 */
public final class ShopScreen extends AbstractContainerScreen<ShopMenu> {
	private static final Ui.Theme THEME = Ui.Theme.VILLAGE;
	private static final int W = 256;
	private static final int H = 204;
	private static final int SHELF_X = 8;
	private static final int SHELF_Y = 22;
	private static final int COLUMNS = 9;
	private static final int ROWS = 5;
	private static final int BAR_X = SHELF_X + COLUMNS * 18 + 3;
	private static final int SIDE_X = 192;
	private static final int SIDE_W = 56;
	private static final int BUTTON_Y = 72;
	private static final int TAB_W = 26;
	private static final int TAB_H = 22;
	private static final String[] TABS = {"all", "food", "home", "building", "players", "other", "order"};
	private static final ItemStack[] TAB_ICONS = {new ItemStack(Items.CHEST), new ItemStack(Items.BREAD), new ItemStack(Items.RED_BED),
			new ItemStack(Items.BRICKS), new ItemStack(Items.EMERALD), new ItemStack(Items.PAPER), new ItemStack(Items.WRITABLE_BOOK)};
	/** The order tab (village/market/Orders): the slot holds a sample, the shelf turns into the order's sheet. */
	private static final int ORDER_TAB = 6;
	private static final int[] STEPS = {-64, -8, -1, 1, 8, 64};
	/** Rows of the player's orders shown at once (the wheel scrolls them). */
	private static final int ORDER_ROWS = 4;
	private int orderCount = 1;
	/** The order tab's two sheets: a new order, or the player's orders already placed. */
	private boolean mine;
	private int mineScroll;
	private int tab;
	private int scroll;
	private boolean dragging;

	public ShopScreen(ShopMenu menu, Inventory inventory, Component title) {
		//? if >=26.1 {
		/*super(menu, inventory, title, W, H);
		*///?} else {
		super(menu, inventory, title);
		imageWidth = W;
		imageHeight = H;
		//?}
		inventoryLabelX = ShopMenu.INVENTORY_X;
		inventoryLabelY = ShopMenu.INVENTORY_Y - 10;
	}

	private boolean ordering() {
		return tab == ORDER_TAB;
	}

	private int orderTotal() {
		int goods = menu.ordering.unit() * orderCount;
		return goods + Math.max(4, (int) Math.ceil(goods * 0.1));
	}

	private List<Ui.Action> actions() {
		List<Ui.Action> out = new ArrayList<>();
		if (ordering()) {
			int coins = minecraft != null && minecraft.player != null ? Coins.count(minecraft.player.getInventory()) : 0;
			boolean can = !mine && menu.ordering.unit() > 0 && orderCount <= menu.ordering.most() && coins >= orderTotal();
			out.add(new Ui.Action(leftPos + SIDE_X, topPos + BUTTON_Y, SIDE_W, 18, Component.translatable("vnap.shop.order.button"), true, can,
					() -> send(ShopActionPayload.ORDER, orderCount)));
			int ax = leftPos + SHELF_X + 1;
			int ay = topPos + SHELF_Y + 2;
			int placedCount = menu.ordering.placed().size();
			// the sheet shown is the lit one of the two
			out.add(new Ui.Action(ax, ay, 80, 14, Component.translatable("vnap.shop.order.new"), true, !mine, () -> mine = false));
			out.add(new Ui.Action(ax + 82, ay, 80, 14, Component.translatable("vnap.shop.order.mine", placedCount), true, mine || placedCount > 0, () -> mine = true));
			if (mine) {
				// a "take back" on each row of the player's orders
				for (int row = 0; row < ORDER_ROWS; row++) {
					int index = mineScroll + row;
					if (index >= placedCount) break;
					out.add(new Ui.Action(ax + 112, topPos + SHELF_Y + 20 + row * 18, 50, 16, Component.translatable("vnap.shop.order.take_back"), true, true,
							() -> send(ShopActionPayload.CANCEL_ORDER, index)));
				}
				return out;
			}
			// how many: -8 -1 [count] +1 +8, the count in the middle
			// -64 -8 - [count] + +8 +64: a one is just its sign
			int[] at = {0, 21, 42, 100, 121, 142};
			for (int i = 0; i < STEPS.length; i++) {
				int step = STEPS[i];
				String word = (step > 0 ? "+" : "−") + (Math.abs(step) == 1 ? "" : String.valueOf(Math.abs(step)));
				out.add(new Ui.Action(ax + at[i], topPos + SHELF_Y + 40, 20, 16, Component.literal(word), true, menu.ordering.unit() > 0,
						() -> orderCount = Math.max(1, Math.min(Math.max(1, menu.ordering.most()), orderCount + step))));
			}
			return out;
		}
		out.add(new Ui.Action(leftPos + SIDE_X, topPos + BUTTON_Y, SIDE_W, 18, Component.translatable("vnap.shop.sell"), true,
				menu.sellState == ShopMenu.SELL_OK, () -> send(ShopActionPayload.SELL, 0)));
		return out;
	}

	private void send(int action, int index) {
		PacketDistributor.sendToServer(new ShopActionPayload(menu.containerId, action, index));
	}


	/** Indices into the menu's offers of the lots this tab shows. */
	private List<Integer> shown() {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < menu.offers.size(); i++) if (tab == 0 || category(menu.offers.get(i)) == tab) out.add(i);
		return out;
	}

	static int category(ShopListingPayload.Offer offer) {
		if (offer.fromPlayer()) return 4;
		ItemStack item = offer.item();
		if (item.has(DataComponents.FOOD)) return 1;
		boolean homeDecor = item.getItem() instanceof BlockItem block && block.getBlock() instanceof DecorBlock decor && decor.profession().equals("home");
		if (homeDecor || item.is(ItemTags.BEDS) || item.is(ItemTags.CANDLES) || item.is(Items.LANTERN) || item.is(Items.FLOWER_POT)
				|| item.is(Items.PAINTING) || item.is(Items.CAKE) || item.is(LifeItems.WOODEN_TOY) || item.is(LifeItems.BABY_BLANKET)
				|| item.is(LifeItems.WEDDING_RING) || item.is(LifeItems.PET_TREAT)) return 2;
		if (item.getItem() instanceof BlockItem) return 3;
		return 5;
	}

	private int rows() {
		return (shown().size() + COLUMNS - 1) / COLUMNS;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows() - ROWS)));
		super.render(graphics, mouseX, mouseY, partialTick);
		int hovered = ordering() ? -1 : lotAt(mouseX, mouseY);
		int tabHovered = tabAt(mouseX, mouseY);
		if (hovered >= 0 && menu.getCarried().isEmpty()) {
			ShopListingPayload.Offer offer = menu.offers.get(hovered);
			List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(offer.item()));
			lines.add(Component.translatable("vnap.shop.price", Coins.text(offer.price())).withStyle(ChatFormatting.GOLD));
			lines.add(Component.translatable("vnap.shop.in_stock", offer.item().getCount()).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("vnap.shop.how_to_buy").withStyle(ChatFormatting.DARK_GRAY));
			McClient.tooltip(graphics, font, lines, offer.item().getTooltipImage(), mouseX, mouseY);
		} else if (tabHovered >= 0) {
			McClient.tooltip(graphics, font, Component.translatable("vnap.shop.tab." + TABS[tabHovered]), mouseX, mouseY);
		} else {
			renderTooltip(graphics, mouseX, mouseY);
		}
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		for (int i = 0; i < TABS.length; i++) if (i != tab) tab(graphics, i, false);
		Ui.panel(graphics, THEME, x, y, W, H);
		tab(graphics, tab, true);
		Ui.header(graphics, THEME, font, x + 4, y + 4, W - 8, title);
		if (ordering()) {
			Ui.inset(graphics, THEME, x + SHELF_X, y + SHELF_Y, COLUMNS * 18 + 2, ROWS * 18);
		} else {
			for (int row = 0; row < ROWS; row++) {
				for (int col = 0; col < COLUMNS; col++) Ui.slot(graphics, THEME, x + SHELF_X + col * 18 + 1, y + SHELF_Y + row * 18 + 1);
			}
		}
		Ui.inset(graphics, THEME, x + SIDE_X - 2, y + SHELF_Y, SIDE_W + 4, ROWS * 18);
		for (Slot slot : menu.slots) {
			if (slot.index == 0) Ui.bigSlot(graphics, THEME, x + slot.x, y + slot.y);
			else Ui.slot(graphics, THEME, x + slot.x, y + slot.y);
		}
		if (ordering()) {
			var placed = menu.ordering.placed();
			if (mine) {
				for (int row = 0; row < ORDER_ROWS && mineScroll + row < placed.size(); row++) {
					graphics.renderItem(placed.get(mineScroll + row).item(), x + SHELF_X + 4, y + SHELF_Y + 20 + row * 18);
				}
			} else if (!menu.getSlot(0).getItem().isEmpty()) {
				graphics.renderItem(menu.getSlot(0).getItem(), x + SHELF_X + 4, y + SHELF_Y + 19);
			}
			Ui.draw(graphics, THEME, font, actions(), mouseX, mouseY);
			return;
		}
		List<Integer> shown = shown();
		int first = scroll * COLUMNS;
		for (int i = first; i < Math.min(shown.size(), first + COLUMNS * ROWS); i++) {
			int cell = i - first;
			int ix = x + SHELF_X + cell % COLUMNS * 18 + 1;
			int iy = y + SHELF_Y + cell / COLUMNS * 18 + 1;
			ItemStack item = menu.offers.get(shown.get(i)).item();
			Ui.item(graphics, font, item, ix, iy);
		}
		int hovered = lotAt(mouseX, mouseY);
		int cell = hovered < 0 ? -1 : shown.indexOf(hovered) - first;
		if (cell >= 0) {
			int hx = x + SHELF_X + cell % COLUMNS * 18 + 1;
			int hy = y + SHELF_Y + cell / COLUMNS * 18 + 1;
			graphics.fill(hx, hy, hx + 16, hy + 16, 0x80FFFFFF);
		}
		int rows = rows();
		Ui.scroll(graphics, THEME, x + BAR_X, y + SHELF_Y, ROWS * 18, rows > ROWS ? (double) scroll / (rows - ROWS) : 0.0, rows > ROWS);
		Ui.draw(graphics, THEME, font, actions(), mouseX, mouseY);
	}

	private void tab(GuiGraphics graphics, int i, boolean open) {
		int tx = leftPos + 6 + i * (TAB_W + 2);
		int ty = topPos - TAB_H + 4;
		Ui.tab(graphics, THEME, tx, ty, TAB_W, TAB_H + (open ? 2 : 0), open);
		graphics.renderItem(TAB_ICONS[i], tx + 5, ty + 4);
	}

	/** 1234 -> "1.2k", 25000 -> "25k". */
	static String count(int n) {
		if (n < 1000) return String.valueOf(n);
		if (n < 10_000) return String.format(java.util.Locale.ROOT, "%.1fk", n / 1000.0);
		return n / 1000 + "k";
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, THEME.muted(), false);
		if (ordering()) {
			orderLabels(graphics);
		} else {
			sellLabels(graphics);
		}
		int coins = minecraft != null && minecraft.player != null ? Coins.count(minecraft.player.getInventory()) : 0;
		// the purse inside the side's backing (it ends at SHELF_Y + ROWS * 18)
		Ui.centred(graphics, font, Component.translatable("vnap.shop.purse"), SIDE_X + SIDE_W / 2, 92, THEME.muted());
		Component sum = Coins.text(coins);
		int width = 11 + font.width(sum);
		Ui.icon(graphics, THEME, "coin", SIDE_X + (SIDE_W - width) / 2, 101);
		graphics.drawString(font, sum, SIDE_X + (SIDE_W - width) / 2 + 11, 102, THEME.accent(), false);
	}

	/** The order's sheet over the shelf: a new order (what, for how much, how many, all of it, when) or the player's orders. */
	private void orderLabels(GuiGraphics graphics) {
		Ui.centred(graphics, font, Component.translatable("vnap.shop.order.sample"), SIDE_X + SIDE_W / 2, SHELF_Y + 4, THEME.title());
		int x = SHELF_X + 4;
		int width = COLUMNS * 18 - 6;
		var ordering = menu.ordering;
		if (mine) {
			var placed = ordering.placed();
			for (int row = 0; row < ORDER_ROWS && mineScroll + row < placed.size(); row++) {
				var order = placed.get(mineScroll + row);
				int ry = SHELF_Y + 20 + row * 18;
				Ui.clip(graphics, font, Component.literal(order.item().getCount() + " × ").append(order.item().getHoverName()), x + 20, ry, 86, THEME.text());
				graphics.drawString(font, Coins.text(order.paid()), x + 20, ry + 9, THEME.accent(), false);
			}
			if (!placed.isEmpty()) graphics.drawString(font, Component.translatable("vnap.shop.order.days", ordering.days()), x, SHELF_Y + 20 + ORDER_ROWS * 18 - 2, THEME.muted(), false);
			return;
		}
		ItemStack sample = menu.getSlot(0).getItem();
		if (sample.isEmpty()) {
			Ui.wrap(graphics, font, Component.translatable("vnap.shop.order.hint"), x, SHELF_Y + 20, width, THEME.muted(), 4);
			return;
		}
		Ui.clip(graphics, font, sample.getHoverName(), x + 20, SHELF_Y + 19, width - 20, THEME.text());
		if (ordering.unit() <= 0) {
			Ui.wrap(graphics, font, Component.translatable("vnap.shop.order.cannot"), x + 20, SHELF_Y + 28, width - 20, THEME.muted(), 2);
			return;
		}
		orderCount = Math.max(1, Math.min(ordering.most(), orderCount));
		graphics.drawString(font, Component.translatable("vnap.shop.order.unit", Coins.text(ordering.unit())), x + 20, SHELF_Y + 28, THEME.accent(), false);
		// the count between its buttons, as big as it gets
		Ui.centred(graphics, font, Component.literal(String.valueOf(orderCount)), SHELF_X + 1 + 80, SHELF_Y + 44, THEME.title());
		graphics.drawString(font, Component.translatable("vnap.shop.order.total", Coins.text(orderTotal())), x, SHELF_Y + 62, THEME.good(), false);
		graphics.drawString(font, Component.translatable("vnap.shop.order.days", ordering.days()), x, SHELF_Y + 73, THEME.muted(), false);
	}

	private void sellLabels(GuiGraphics graphics) {
		Ui.centred(graphics, font, Component.translatable("vnap.shop.sell_slot"), SIDE_X + SIDE_W / 2, SHELF_Y + 4, THEME.title());
		Component state = switch (menu.sellState) {
			case ShopMenu.SELL_OK -> Component.translatable("vnap.shop.offer", Coins.text(menu.sellOffer));
			case ShopMenu.SELL_UNWANTED -> Component.translatable("vnap.shop.unwanted");
			case ShopMenu.SELL_TILL -> Component.translatable("vnap.shop.till");
			case ShopMenu.SELL_FULL -> Component.translatable("vnap.shop.full");
			case ShopMenu.SELL_BUDGET -> Component.translatable("vnap.shop.budget");
			default -> Component.empty();
		};
		Ui.wrap(graphics, font, state, SIDE_X, 54, SIDE_W, menu.sellState == ShopMenu.SELL_OK ? THEME.good() : THEME.muted(), 2);
	}



	/** The offer index (into the menu's full list) under the mouse, or -1. */
	private int lotAt(double mouseX, double mouseY) {
		int col = (int) Math.floor((mouseX - leftPos - SHELF_X) / 18.0);
		int row = (int) Math.floor((mouseY - topPos - SHELF_Y) / 18.0);
		if (col < 0 || col >= COLUMNS || row < 0 || row >= ROWS) return -1;
		int index = scroll * COLUMNS + row * COLUMNS + col;
		List<Integer> shown = shown();
		return index < shown.size() ? shown.get(index) : -1;
	}

	private int tabAt(double mouseX, double mouseY) {
		for (int i = 0; i < TABS.length; i++) {
			int tx = leftPos + 6 + i * (TAB_W + 2);
			int ty = topPos - TAB_H + 4;
			if (mouseX >= tx && mouseX < tx + TAB_W && mouseY >= ty && mouseY < topPos + 4) return i;
		}
		return -1;
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

	private boolean click(double mouseX, double mouseY, int button) {
		int clickedTab = tabAt(mouseX, mouseY);
		if (clickedTab >= 0) {
			tab = clickedTab;
			scroll = 0;
			return true;
		}
		if (button == 0 && Ui.click(actions(), mouseX, mouseY)) return true;
		if (ordering()) return false;
		if (button == 0 && mouseX >= leftPos + BAR_X && mouseX < leftPos + BAR_X + 12 && mouseY >= topPos + SHELF_Y
				&& mouseY < topPos + SHELF_Y + ROWS * 18) {
			dragging = true;
			drag(mouseY);
			return true;
		}
		int lot = lotAt(mouseX, mouseY);
		if (lot >= 0 && button == 0 && menu.getCarried().isEmpty()) {
			send(Screen.hasShiftDown() ? ShopActionPayload.BUY_STACK : ShopActionPayload.BUY, lot);
			return true;
		}
		return false;
	}

	//? if >=1.21.9 {
	/*@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
		if (dragging) {
			drag(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}
	*///?} else {
	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (dragging) {
			drag(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}
	//?}

	private void drag(double mouseY) {
		int rows = rows();
		if (rows <= ROWS) return;
		double at = (mouseY - topPos - SHELF_Y) / (ROWS * 18.0);
		scroll = Math.max(0, Math.min(rows - ROWS, (int) Math.round(at * (rows - ROWS))));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (ordering()) {
			if (mine) {
				mineScroll = Math.max(0, Math.min(Math.max(0, menu.ordering.placed().size() - ORDER_ROWS), mineScroll - (int) Math.signum(scrollY)));
			} else {
				orderCount = Math.max(1, Math.min(Math.max(1, menu.ordering.most()), orderCount + (int) Math.signum(scrollY) * (Screen.hasShiftDown() ? 64 : 1)));
			}
			return true;
		}
		scroll = Math.max(0, Math.min(Math.max(0, rows() - ROWS), scroll - (int) Math.signum(scrollY)));
		return true;
	}
}
