package com.vnap.client;

import com.vnap.client.ui.Ui;
import com.vnap.item.LifeItems;
import com.vnap.network.CoreAnvilPayload;
import com.vnap.spire.gear.CoreAnvilMenu;
import com.vnap.spire.gear.GearItem;
import com.vnap.spire.gear.GearPiece;
import com.vnap.spire.gear.GearTier;
import com.vnap.spire.gear.SpireGear;
import com.vnap.spire.gear.SpireGearItems;
import com.vnap.village.market.Coins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Core Anvil's window, a smithing table of the spire: piece + shards + seal → what comes out, read off the slots
 * (CoreAnvilMenu.work/result), with a card under them saying what the work is, what it gives and what it costs, and
 * one button. Only the click goes over the wire.
 */
public final class CoreAnvilScreen extends AbstractContainerScreen<CoreAnvilMenu> {
	private static final Ui.Theme THEME = Ui.Theme.SPIRE;
	private static final int W = 176;
	private static final int H = 198;
	private static final int CARD_Y = 58;
	private static final int CARD_H = 52;
	private static final int BUTTON_X = 108;
	private static final int BUTTON_Y = CARD_Y + 32;
	private static final int BUTTON_W = 56;
	private static final int BUTTON_H = 16;

	public CoreAnvilScreen(CoreAnvilMenu menu, Inventory inventory, Component title) {
		//? if >=26.1 {
		/*super(menu, inventory, title, W, H);
		*///?} else {
		super(menu, inventory, title);
		imageWidth = W;
		imageHeight = H;
		//?}
	}

	private CoreAnvilMenu.Outcome check() {
		return CoreAnvilMenu.check(menu.pockets, menu.piece(), menu.shards(), menu.seal());
	}

	private boolean click(double mouseX, double mouseY) {
		return Ui.click(actions(), mouseX, mouseY);
	}

	/** One button: hone or raise, as the slots hold. */
	private java.util.List<Ui.Action> actions() {
		CoreAnvilMenu.Work work = CoreAnvilMenu.work(menu.piece(), menu.seal());
		Component label = Component.translatable(work == CoreAnvilMenu.Work.RAISE ? "spire_gear.villager_life.anvil.craft" : "spire_gear.villager_life.anvil.hone");
		return java.util.List.of(new Ui.Action(leftPos + BUTTON_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H, label, work != CoreAnvilMenu.Work.NONE,
				check() == CoreAnvilMenu.Outcome.DONE, () -> PacketDistributor.sendToServer(new CoreAnvilPayload(menu.containerId, CoreAnvilMenu.WORK))));
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

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		ItemStack result = CoreAnvilMenu.result(menu.piece(), menu.seal());
		if (!result.isEmpty() && Ui.over(mouseX, mouseY, leftPos + CoreAnvilMenu.RESULT_X, topPos + CoreAnvilMenu.ROW_Y, 16, 16)) {
			McClient.tooltip(graphics, font, result, mouseX, mouseY);
			return;
		}
		// what each empty slot is for
		for (int i = CoreAnvilMenu.PIECE; i <= CoreAnvilMenu.SEAL; i++) {
			Slot slot = menu.slots.get(i);
			if (!slot.hasItem() && menu.getCarried().isEmpty() && Ui.over(mouseX, mouseY, leftPos + slot.x, topPos + slot.y, 16, 16)) {
				McClient.tooltip(graphics, font, Ui.lines(font, Component.translatable("spire_gear.villager_life.anvil.slot." + i), 170), mouseX, mouseY);
				return;
			}
		}
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		Ui.panel(graphics, THEME, x, y, W, H);
		Ui.header(graphics, THEME, font, x + 4, y + 4, W - 8, title);
		int row = y + CoreAnvilMenu.ROW_Y;
		Ui.bigSlot(graphics, THEME, x + CoreAnvilMenu.PIECE_X, row);
		Ui.icon(graphics, THEME, "plus", x + CoreAnvilMenu.PIECE_X + 22, row + 4);
		Ui.slot(graphics, THEME, x + CoreAnvilMenu.SHARDS_X, row);
		Ui.icon(graphics, THEME, "plus", x + CoreAnvilMenu.SHARDS_X + 20, row + 4);
		Ui.slot(graphics, THEME, x + CoreAnvilMenu.SEAL_X, row);
		CoreAnvilMenu.Outcome outcome = check();
		Ui.arrow(graphics, THEME, x + CoreAnvilMenu.SEAL_X + 24, row, outcome == CoreAnvilMenu.Outcome.DONE ? 1.0 : 0.0);
		Ui.bigSlot(graphics, THEME, x + CoreAnvilMenu.RESULT_X, row);
		// the empty slots show faintly what goes in them
		ghost(graphics, menu.piece(), new ItemStack(SpireGearItems.get(GearTier.ROOT, GearPiece.SWORD)), x + CoreAnvilMenu.PIECE_X, row);
		ghost(graphics, menu.shards(), new ItemStack(LifeItems.CORE_SHARD), x + CoreAnvilMenu.SHARDS_X, row);
		GearTier next = CoreAnvilMenu.nextTier(menu.piece());
		ghost(graphics, menu.seal(), new ItemStack(SpireGearItems.seal(next != null ? next : GearTier.ROOT)), x + CoreAnvilMenu.SEAL_X, row);
		ItemStack result = CoreAnvilMenu.result(menu.piece(), menu.seal());
		if (!result.isEmpty()) {
			graphics.renderItem(result, x + CoreAnvilMenu.RESULT_X, row);
			graphics.renderItemDecorations(font, result, x + CoreAnvilMenu.RESULT_X, row);
			if (outcome != CoreAnvilMenu.Outcome.DONE) graphics.fill(x + CoreAnvilMenu.RESULT_X, row, x + CoreAnvilMenu.RESULT_X + 16, row + 16, 0xA0181620);
		}
		Ui.inset(graphics, THEME, x + 8, y + CARD_Y, W - 16, CARD_H);
		for (Slot slot : menu.slots) if (slot.index > CoreAnvilMenu.SEAL) Ui.slot(graphics, THEME, x + slot.x, y + slot.y);
		Ui.draw(graphics, THEME, font, actions(), mouseX, mouseY);
		CoreAnvilMenu.Work work = CoreAnvilMenu.work(menu.piece(), menu.seal());
		if (work == CoreAnvilMenu.Work.HONE && menu.piece().getItem() instanceof GearItem) {
			int n = SpireGear.honed(menu.piece());
			Ui.bar(graphics, THEME, x + 14, y + CARD_Y + 24, 104, SpireGear.gain(n), SpireGear.gain(n + 1L));
		}
	}

	/** The faint picture of what goes in an empty slot. */
	private void ghost(GuiGraphics graphics, ItemStack in, ItemStack shown, int x, int y) {
		if (!in.isEmpty()) return;
		graphics.renderFakeItem(shown, x, y);
		graphics.fill(x, y, x + 16, y + 16, 0xB0282A34);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		int left = 14;
		int width = W - 28;
		int line = CARD_Y + 5;
		ItemStack piece = menu.piece();
		CoreAnvilMenu.Work work = CoreAnvilMenu.work(piece, menu.seal());
		if (work == CoreAnvilMenu.Work.NONE) {
			Ui.wrap(graphics, font, Component.translatable("spire_gear.villager_life.anvil.empty"), left, line - 1, width, THEME.muted(), 5);
			return;
		}
		GearItem gear = SpireGear.gear(piece);
		GearPiece kind = gear != null ? gear.piece() : GearPiece.source(piece.getItem());
		int n = SpireGear.honed(piece);
		CoreAnvilMenu.Outcome outcome = check();
		if (work == CoreAnvilMenu.Work.HONE && gear != null) {
			Ui.clip(graphics, font, Component.translatable("spire_gear.villager_life.anvil.hone_line", n, n + 1L, SpireGear.percent(SpireGear.gain(n + 1L))),
					left, line, width, THEME.title());
			Ui.clip(graphics, font, stat(gear.piece(), SpireGear.headline(gear.tier(), gear.piece(), n), SpireGear.headline(gear.tier(), gear.piece(), n + 1)),
					left, line + 10, width, THEME.good());
		} else {
			GearTier next = CoreAnvilMenu.nextTier(piece);
			if (next == null || kind == null) {
				Ui.wrap(graphics, font, Component.translatable("spire_gear.villager_life.anvil.top"), left, line, width, THEME.muted(), 3);
				return;
			}
			Ui.clip(graphics, font, Component.translatable("spire_gear.villager_life.anvil.next", next.number(), Component.translatable(SpireGear.tierKey(next))),
					left, line, width, THEME.title());
			double after = SpireGear.headline(next, kind, n);
			Ui.clip(graphics, font, gear != null ? stat(kind, SpireGear.headline(gear.tier(), kind, n), after)
					: Component.translatable("spire_gear.villager_life.short." + statKey(kind), "—", SpireGear.number(after)), left, line + 10, width, THEME.good());
			boolean seal = menu.seal().is(SpireGearItems.seal(next));
			Ui.clip(graphics, font, seal ? Component.translatable("spire_gear.villager_life.anvil.seal_ok", Component.translatable(SpireGear.tierKey(next)))
					: Component.translatable("spire_gear.villager_life.anvil.seal_missing", Component.translatable(SpireGear.tierKey(next))),
					left, line + 20, width, seal ? THEME.text() : THEME.bad());
		}
		// the cost: the shards in their slot, the smith's coins from the purse; red what is short
		int needShards = CoreAnvilMenu.shardCost(piece, menu.seal());
		int needCoins = CoreAnvilMenu.coinCost(piece, menu.seal());
		int costY = BUTTON_Y + 4;
		Ui.icon(graphics, THEME, "shard", left, costY - 1);
		String s = needShards + "/" + menu.shards().getCount();
		graphics.drawString(font, s, left + 11, costY, menu.shards().getCount() >= needShards ? THEME.text() : THEME.bad(), false);
		int cx = left + 16 + font.width(s);
		Ui.icon(graphics, THEME, "coin", cx, costY - 1);
		graphics.drawString(font, Coins.text(needCoins), cx + 11, costY, Coins.count(menu.pockets) >= needCoins ? THEME.text() : THEME.bad(), false);
		if (outcome == CoreAnvilMenu.Outcome.TOP && work == CoreAnvilMenu.Work.HONE) {
			Ui.clip(graphics, font, Component.translatable("spire_gear.villager_life.anvil.top"), left, line + 20, width, THEME.muted());
		}
	}

	private static String statKey(GearPiece piece) {
		return piece.melee() ? "damage" : piece == GearPiece.BOW ? "power" : "armour";
	}

	private static Component stat(GearPiece piece, double now, double after) {
		return Component.translatable("spire_gear.villager_life.short." + statKey(piece), SpireGear.number(now), SpireGear.number(after));
	}
}
