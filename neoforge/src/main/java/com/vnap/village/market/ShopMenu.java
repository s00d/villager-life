package com.vnap.village.market;

import com.vnap.village.town.TownRules;
import com.vnap.block.LifeBlocks;
import com.vnap.dialogue.Speech;
import com.vnap.mood.MoodHooks;
import com.vnap.mood.VillageMoodApi;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.network.ShopListingPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Trading at a stall's counter: the shelf (click a lot to buy one, shift-click for a handful) and a slot to sell a
 * stack into. A player pays the worth turned by the village's mood (friends pay less) and is paid half of it (friends
 * get more), so nothing bought from a player can be sold back to the stall at a gain. What a player sells goes on the
 * shelf at its worth: the player, or a villager who needs it, can buy it back. The stall pays only from its till
 * above the floor it keeps for the caravan, and keeps at most {@link #SHELF} of a lot.
 */
public final class ShopMenu extends AbstractContainerMenu {
	public static final int SELL_X = 213;
	public static final int SELL_Y = 36;
	public static final int INVENTORY_X = 48;
	public static final int INVENTORY_Y = 122;
	public static final int SHELF = 64;
	public static final int MAX_LOTS = 240;
	public static final int SELL_OK = 0;
	public static final int SELL_NOTHING = 1;
	public static final int SELL_UNWANTED = 2;
	public static final int SELL_TILL = 3;
	public static final int SELL_FULL = 4;
	public static final int SELL_BUDGET = 5;

	private final SimpleContainer sell = new SimpleContainer(1) {
		@Override
		public void setChanged() {
			super.setChanged();
			slotsChanged(this);
		}
	};
	private final @Nullable ServerLevel level;
	private final BlockPos counter;
	private final List<Shops.Lot> shown = new ArrayList<>();
	/** Client side: the shelf from the last {@link ShopListingPayload}. */
	public List<ShopListingPayload.Offer> offers = List.of();
	public int sellOffer;
	public int sellState = SELL_NOTHING;
	public String keeper = "";
	/** Client side: the order tab's numbers (Orders). */
	public ShopListingPayload.Ordering ordering = ShopListingPayload.Ordering.NONE;

	/** Client side: the counter's position comes in the open packet. */
	public ShopMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
		this(id, inventory, null, buf.readBlockPos());
	}

	private ShopMenu(int id, Inventory inventory, @Nullable ServerLevel level, BlockPos counter) {
		super(LifeBlocks.SHOP_MENU, id);
		this.level = level;
		this.counter = counter.immutable();
		addSlot(new Slot(sell, 0, SELL_X, SELL_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return !Coins.isCoin(stack);
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
		}
	}

	/**
	 * A player at a counter (or talking to the keeper): the stall's window when it is open, a word why not otherwise.
	 * False when this counter is no village's stall.
	 */
	public static boolean open(ServerLevel level, ServerPlayer player, BlockPos counter) {
		Shops.Shop shop = Shops.get(level.getServer()).atCounter(level.dimension(), counter);
		if (shop == null) return false;
		Villager keeper = Shops.keeper(level, shop);
		if (!Shops.open(level, shop)) {
			com.vnap.Mc.say(player, Component.translatable("vnap.shop.closed"), true);
			return true;
		}
		if (mood(level, counter) <= TownRules.REFUSE_MOOD) {
			if (keeper != null) Speech.queue(keeper, "Shopkeeper Turns the Player Away", player, 1L);
			com.vnap.Mc.say(player, Component.translatable("vnap.shop.refuses"), true);
			return true;
		}
		player.openMenu(new SimpleMenuProvider((id, inventory, who) -> new ShopMenu(id, inventory, level, counter),
				Component.translatable("vnap.shop.title", shop.name)), buf -> buf.writeBlockPos(counter));
		if (player.containerMenu instanceof ShopMenu menu) menu.push(player);
		return true;
	}

	public static float mood(ServerLevel level, BlockPos pos) {
		VillageMoodApi.VillageRef ref = VillageMoodApi.resolve(level, pos);
		var state = ref == null ? null : VillageMoodSavedData.get(level).get(ref.villageId());
		return state == null ? 0.0F : state.mood;
	}

	private Shops.@Nullable Shop shop() {
		ServerLevel server = level;
		return server == null ? null : Shops.get(server.getServer()).atCounter(server.dimension(), counter);
	}

	/** Sends the shelf, as this player sees it now. */
	void push(Player player) {
		ServerLevel server = level;
		Shops.Shop shop = shop();
		if (server == null || shop == null || !(player instanceof ServerPlayer serverPlayer)) return;
		float mood = mood(server, counter);
		newDay(server, shop);
		shown.clear();
		List<ShopListingPayload.Offer> out = new ArrayList<>();
		for (Shops.Lot lot : shop.stock) {
			if (lot.count <= 0 || shown.size() >= MAX_LOTS) continue;
			shown.add(lot);
			out.add(new ShopListingPayload.Offer(lot.item.copyWithCount(lot.count), Prices.playerBuys(lot.price, 1, mood), lot.fromPlayer));
		}
		ItemStack selling = sell.getItem(0);
		int offer = Fair.offer(server, shop, Prices.playerSells(selling, mood));
		sellOffer = offer;
		sellState = sellState(shop, player.getUUID(), selling, offer, Fair.budget(server, shop));
		keeper = shop.name;
		ItemStack sample = Orders.sample(selling);
		List<ShopListingPayload.Placed> placed = new ArrayList<>();
		for (Orders.Order order : Orders.of(shop, player.getUUID())) placed.add(new ShopListingPayload.Placed(order.item.copyWithCount(order.count), order.paid));
		ShopListingPayload.Ordering ordering = new ShopListingPayload.Ordering(Orders.unit(server, counter, sample), sample.isEmpty() ? 0 : Orders.most(sample),
				Orders.days(server, shop), placed);
		PacketDistributor.sendToPlayer(serverPlayer, new ShopListingPayload(containerId, out, offer, sellState, shop.name, ordering));
	}

	public static void newDay(ServerLevel level, Shops.Shop shop) {
		long day = com.vnap.dialogue.Family.day(level);
		if (shop.playerDay != day) {
			shop.playerDay = day;
			shop.paidPlayers.clear();
		}
	}

	public static int sellState(Shops.Shop shop, java.util.UUID player, ItemStack selling, int offer) {
		return sellState(shop, player, selling, offer, TownRules.PLAYER_BUDGET);
	}

	/** {@code sellState} with the day's budget (twice on a fair day, Fair). */
	public static int sellState(Shops.Shop shop, java.util.UUID player, ItemStack selling, int offer, int budget) {
		if (selling.isEmpty()) return SELL_NOTHING;
		if (shop.paid(player) + offer > budget) return SELL_BUDGET;
		if (offer <= 0) return SELL_UNWANTED;
		Shops.Lot lot = shop.lot(selling);
		int shelf = Math.max(SHELF, 2 * Market.target(selling.getItem()));
		if (lot != null && lot.count + selling.getCount() > shelf) return SELL_FULL;
		if (shop.till - offer < TownRules.TILL_FLOOR) return SELL_TILL;
		return SELL_OK;
	}

	@Override
	public void slotsChanged(net.minecraft.world.Container container) {
		super.slotsChanged(container);
		ServerLevel server = level;
		if (server != null) {
			for (ServerPlayer player : server.players()) {
				if (player.containerMenu == this) push(player);
			}
		}
	}

	/** A click in the window (ShopActionPayload): buy one or a stack of a lot, or sell the slot. */
	public boolean act(Player player, int action, int index) {
		ServerLevel server = level;
		Shops.Shop shop = shop();
		if (server == null || shop == null || !Shops.open(server, shop)) return false;
		newDay(server, shop);
		boolean done;
		if (action == com.vnap.network.ShopActionPayload.SELL) {
			done = sell(server, shop, player);
		} else if (action == com.vnap.network.ShopActionPayload.ORDER) {
			done = Orders.place(server, shop, player, sell.getItem(0), index, counter);
		} else if (action == com.vnap.network.ShopActionPayload.CANCEL_ORDER) {
			done = Orders.cancel(server, shop, player, index);
		} else if (index >= 0 && index < MAX_LOTS) {
			done = buy(server, shop, player, index, action == com.vnap.network.ShopActionPayload.BUY_STACK);
		} else {
			return false;
		}
		if (done) {
			Shops.get(server.getServer()).setDirty();
			Villager keeper = Shops.keeper(server, shop);
			if (keeper != null) MoodHooks.onTradeCompleted(server, keeper, player);
			// the others at this counter see the shelf as it is now (their clicks went to lots sold out meanwhile)
			for (ServerPlayer other : server.players()) {
				if (!other.getUUID().equals(player.getUUID()) && other.containerMenu instanceof ShopMenu menu && menu.counter.equals(counter)) menu.push(other);
			}
		}
		push(player);
		return done;
	}

	private boolean buy(ServerLevel server, Shops.Shop shop, Player player, int index, boolean handful) {
		if (index >= shown.size()) return false;
		Shops.Lot lot = shown.get(index);
		int count = handful ? Math.min(lot.count, lot.item.getMaxStackSize()) : 1;
		return buy(server, shop, player, lot, count, counter);
	}

	/** A player buys {@code count} of the lot at the stall by {@code where} (its mood sets the price). */
	public static boolean buy(ServerLevel server, Shops.Shop shop, Player player, Shops.Lot lot, int count, BlockPos where) {
		if (!shop.stock.contains(lot) || count <= 0) return false;
		// a quarter of what the stall keeps of its own goods is for the villagers: a player cannot buy it out
		int reserve = lot.fromPlayer ? 0 : (Market.target(lot.item.getItem()) + 3) / 4;
		if (lot.count - reserve < count) {
			com.vnap.Mc.say(player, Component.translatable("vnap.shop.reserved"), true);
			return false;
		}
		int cost = Prices.playerBuys(lot.price, count, mood(server, where));
		if (!Coins.pay(player, cost)) {
			com.vnap.Mc.say(player, Component.translatable("vnap.shop.no_money", Coins.text(cost)), true);
			return false;
		}
		shop.till += cost;
		ItemStack bought = lot.item.copyWithCount(count);
		shop.take(lot, count);
		if (!player.getInventory().add(bought)) com.vnap.Mc.toss(player, bought);
		server.playSound(null, where, com.vnap.sound.GolemSounds.SHOP_BUY, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
		com.vnap.progress.VillageAdvancements.award(player, "town/shop");
		return true;
	}

	private boolean sell(ServerLevel server, Shops.Shop shop, Player player) {
		ItemStack selling = sell.getItem(0);
		if (!sell(server, shop, player, selling, counter)) return false;
		sell.setItem(0, ItemStack.EMPTY);
		return true;
	}

	/** A player sells the stack to the stall by {@code where}: coins for it, the stack on the shelf (it is emptied). */
	public static boolean sell(ServerLevel server, Shops.Shop shop, Player player, ItemStack selling, BlockPos where) {
		int offer = Fair.offer(server, shop, Prices.playerSells(selling, mood(server, where)));
		if (sellState(shop, player.getUUID(), selling, offer, Fair.budget(server, shop)) != SELL_OK) return false;
		shop.till -= offer;
		shop.paidPlayers.merge(player.getUUID(), offer, Integer::sum);
		shop.put(selling, selling.getCount(), true);
		selling.setCount(0);
		Coins.give(player, offer);
		server.playSound(null, where, com.vnap.sound.GolemSounds.SHOP_SELL, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
		Villager keeper = Shops.keeper(server, shop);
		if (keeper != null) Speech.queue(keeper, "Shopkeeper Buys From the Player", player, 1L);
		com.vnap.progress.VillageAdvancements.award(player, "town/sell");
		return true;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		ItemStack left = sell.removeItemNoUpdate(0);
		if (left.isEmpty()) return;
		if (!player.isAlive() || (player instanceof ServerPlayer server && server.hasDisconnected())) com.vnap.Mc.toss(player, left);
		else com.vnap.Mc.placeBack(player.getInventory(), left);
	}

	@Override
	public boolean stillValid(Player player) {
		ServerLevel server = level;
		if (server == null) return true;
		Shops.Shop shop = shop();
		return shop != null && Shops.open(server, shop) && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(counter)) <= 64.0;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index == 0) {
			if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
		} else if (Coins.isCoin(stack) || !moveItemStackTo(stack, 0, 1, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		slot.onTake(player, stack);
		return copy;
	}
}
