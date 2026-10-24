package com.vnap.block.decor;

import com.vnap.block.DecorBlock;
import com.vnap.block.LifeBlocks;
import com.vnap.item.LifeItems;
import com.vnap.sound.GolemSounds;
import com.vnap.village.market.Shops;
import com.vnap.village.town.Roads;
import com.vnap.village.town.TownPlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The town's working details: the stall's crates of goods by its door, the pot a wife cooks in, the bell over a
 * trade's door and the sign on chains outside it.
 */
public final class TownDecor {
	/** Copies of the good a crate shows, a heap by how much the stall has (16 a copy). */
	private static final float[][] HEAP = {{4, 8.5F, 5, 0.42F, 75}, {11, 8.5F, 5, 0.42F, 60}, {7.5F, 8.5F, 10, 0.42F, 80},
			{12, 8.5F, 11, 0.42F, 70}, {5, 9.5F, 11, 0.42F, 65}, {8, 10, 7, 0.42F, 55}};
	private static final int PER_COPY = 16;
	/** How long a pot simmers once the wife sets to cooking. */
	public static final int SIMMER = 400;
	/** What a sign shows for each workstation (its trade's ware). */
	private static final Map<net.minecraft.resources.ResourceKey<PoiType>, Item> TRADE_ICON = Map.ofEntries(
			Map.entry(PoiTypes.FARMER, Items.WHEAT), Map.entry(PoiTypes.BUTCHER, Items.PORKCHOP), Map.entry(PoiTypes.SHEPHERD, Items.WHITE_WOOL),
			Map.entry(PoiTypes.FISHERMAN, Items.COD), Map.entry(PoiTypes.FLETCHER, Items.ARROW), Map.entry(PoiTypes.LEATHERWORKER, Items.LEATHER),
			Map.entry(PoiTypes.LIBRARIAN, Items.BOOK), Map.entry(PoiTypes.CARTOGRAPHER, Items.MAP), Map.entry(PoiTypes.CLERIC, Items.CANDLE),
			Map.entry(PoiTypes.MASON, Items.BRICKS), Map.entry(PoiTypes.ARMORER, Items.IRON_CHESTPLATE), Map.entry(PoiTypes.TOOLSMITH, Items.IRON_PICKAXE),
			Map.entry(PoiTypes.WEAPONSMITH, Items.IRON_SWORD));

	private TownDecor() {
	}

	static void register(Map<String, DecorMechanic> all) {
		all.put("market_crate", new MarketCrate());
		all.put("cooking_pot", new CookingPot());
		all.put("door_bell", new DoorBell());
		all.put("shop_sign", new ShopSign());
	}

	// ---------------------------------------------------------------- the stall's crates

	/**
	 * A crate by the stall's door showing what the stall has most of in food (the crate beside it the next most): a
	 * heap the size of its stock. A click opens the stall, as the counter does.
	 */
	static final class MarketCrate implements DecorMechanic {
		@Override
		public int slots() {
			return HEAP.length;
		}

		@Override
		public float[][] display() {
			return HEAP;
		}

		@Override
		public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
			return true;
		}

		@Override
		public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
			Shops.Shop shop = Shops.near(level, be.getBlockPos());
			BlockPos counter = shop == null ? null : shop.counter;
			if (player instanceof ServerPlayer server && (counter == null || !com.vnap.village.market.ShopMenu.open(level, server, counter))) {
				com.vnap.Mc.say(server, net.minecraft.network.chat.Component.translatable("vnap.shop.no_stall"), true);
			}
		}

		@Override
		public boolean ticks() {
			return true;
		}

		@Override
		public void tick(ServerLevel level, DecorBlockEntity be) {
			Shops.Shop shop = Shops.near(level, be.getBlockPos());
			ItemStack shown = ItemStack.EMPTY;
			int copies = 0;
			if (shop != null && shop.counter != null && shop.counter.closerThan(be.getBlockPos(), 16.0)) {
				List<Shops.Lot> food = shop.stock.stream().filter(lot -> lot.count > 0 && lot.item.has(DataComponents.FOOD))
						.sorted(Comparator.comparingInt((Shops.Lot lot) -> -lot.count)).toList();
				// two crates side by side show different goods
				int pick = (be.getBlockPos().getX() + be.getBlockPos().getZ()) & 1;
				if (food.size() > pick) {
					Shops.Lot lot = food.get(pick);
					shown = lot.item.copyWithCount(1);
					copies = Math.min(HEAP.length, (lot.count + PER_COPY - 1) / PER_COPY);
				}
			}
			boolean same = true;
			for (int i = 0; i < HEAP.length; i++) {
				ItemStack want = i < copies ? shown : ItemStack.EMPTY;
				if (!ItemStack.isSameItem(be.items.getItem(i), want) || be.items.getItem(i).isEmpty() != want.isEmpty()) same = false;
			}
			if (same) return;
			for (int i = 0; i < HEAP.length; i++) be.items.setItem(i, i < copies ? shown.copy() : ItemStack.EMPTY);
		}
	}

	// ---------------------------------------------------------------- the pot

	/** The wife's pot by the hearth: it simmers (steam, bubbling, the lid up) while she cooks the day's meal. */
	static final class CookingPot implements DecorMechanic {
		@Override
		public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
			return false;
		}

		@Override
		public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
		}

		@Override
		public boolean ticks() {
			return true;
		}

		@Override
		public void tick(ServerLevel level, DecorBlockEntity be) {
			if (be.timers[0] <= 0) return;
			be.timers[0] = Math.max(0, be.timers[0] - 20);
			if (level.getRandom().nextInt(2) == 0) level.playSound(null, be.getBlockPos(), GolemSounds.POT_BUBBLE, SoundSource.BLOCKS, 0.5F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			if (be.timers[0] == 0) be.refreshFill();
		}

		@Override
		public int fill(DecorBlockEntity be) {
			return be.timers[0] > 0 ? 1 : 0;
		}
	}

	/** The wife sets the pot on: it simmers a while. */
	public static void simmer(DecorBlockEntity pot) {
		pot.timers[0] = SIMMER;
		pot.refreshFill();
		pot.setChanged();
	}

	public static boolean simmering(BlockState state) {
		return state.getBlock() instanceof DecorBlock decor && decor.name().equals("cooking_pot") && state.getValue(DecorBlock.FILL) > 0;
	}

	// ---------------------------------------------------------------- the bell over the door

	/** A little bell over a trade's door: it swings and rings when a player opens the door under it. */
	static final class DoorBell implements DecorMechanic {
		@Override
		public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
			return held.isEmpty();
		}

		@Override
		public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
			ring(level, be);
		}

		@Override
		public boolean ticks() {
			return true;
		}

		@Override
		public void tick(ServerLevel level, DecorBlockEntity be) {
			if (be.timers[0] > 0 && --be.timers[0] == 0) be.refreshFill();
		}

		@Override
		public int fill(DecorBlockEntity be) {
			return be.timers[0] > 0 ? 1 : 0;
		}
	}

	static void ring(ServerLevel level, DecorBlockEntity bell) {
		bell.timers[0] = 2;
		bell.refreshFill();
		level.playSound(null, bell.getBlockPos(), GolemSounds.DOOR_BELL, SoundSource.BLOCKS, 0.8F, 0.95F + level.getRandom().nextFloat() * 0.1F);
	}

	/**
	 * A player opened a door: a bell over it rings, and the trade's keeper (the stall's shopkeeper, else whoever is
	 * nearest inside) turns to the door; the shopkeeper greets the customer. True when a bell rang.
	 */
	public static boolean doorOpened(ServerLevel level, Player player, BlockPos door) {
		DecorBlockEntity bell = null;
		for (BlockPos at : BlockPos.betweenClosed(door.offset(-1, 0, -1), door.offset(1, 3, 1))) {
			if (level.getBlockEntity(at) instanceof DecorBlockEntity be && be.name().equals("door_bell")) {
				bell = be;
				break;
			}
		}
		if (bell == null) return false;
		ring(level, bell);
		Shops.Shop shop = Shops.near(level, door);
		Villager keeper = shop == null || shop.counter == null || !shop.counter.closerThan(door, 12.0) ? null : Shops.keeper(level, shop);
		if (keeper != null && keeper.closerThan(player, 16.0)) {
			keeper.getLookControl().setLookAt(player);
			com.vnap.dialogue.Speech.queue(keeper, "Shopkeeper Greets a Customer", player, 1L);
			return true;
		}
		level.getEntitiesOfClass(Villager.class, new AABB(door).inflate(8.0), v -> v.isAlive() && !v.isSleeping())
				.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(door))))
				.ifPresent(v -> v.getLookControl().setLookAt(player));
		return true;
	}

	// ---------------------------------------------------------------- the sign

	/**
	 * A sign on chains outside a trade's door. It finds its own trade (the stall's counter, the tavern's, the
	 * bakehouse, a workstation near), shows its ware on both faces and sways (DecorRenderer), more in rain and
	 * storm, creaking then. A player may hang any item on it.
	 */
	static final class ShopSign implements DecorMechanic {
		@Override
		public int slots() {
			return 1;
		}

		@Override
		public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
			return !held.isEmpty();
		}

		@Override
		public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
			be.items.setItem(0, held.copyWithCount(1));
			be.timers[1] = 1;
		}

		@Override
		public boolean ticks() {
			return true;
		}

		@Override
		public void tick(ServerLevel level, DecorBlockEntity be) {
			// a player's own icon stays (timers[1]); the town's is looked for again now and then
			// (one with no trade found looks again after a while, timers[2], not on every tick: a scan and a POI query)
			boolean empty = be.items.getItem(0).isEmpty();
			if (be.timers[2] > 0) be.timers[2]--;
			if (be.timers[1] == 0 && (empty ? be.timers[2] == 0 : level.getRandom().nextInt(60) == 0)) {
				Item icon = icon(level, be.getBlockPos());
				if (icon != null && !be.items.getItem(0).is(icon)) be.items.setItem(0, new ItemStack(icon));
				if (icon == null) be.timers[2] = 15;
			}
			if (level.isThundering() ? level.getRandom().nextInt(4) == 0 : level.isRaining() && level.getRandom().nextInt(12) == 0) {
				level.playSound(null, be.getBlockPos(), GolemSounds.SIGN_CREAK, SoundSource.BLOCKS, 0.4F, 0.8F + level.getRandom().nextFloat() * 0.3F);
			}
		}
	}

	/** What a sign at {@code pos} shows: the trade whose door it hangs by. */
	public static @Nullable Item icon(ServerLevel level, BlockPos pos) {
		for (BlockPos at : BlockPos.betweenClosed(pos.offset(-6, -3, -6), pos.offset(6, 2, 6))) {
			BlockState state = level.getBlockState(at);
			if (Shops.isCounter(level, at)) return LifeItems.COPPER_COIN;
			if (state.is(LifeBlocks.TAVERN_COUNTER)) return Items.HONEY_BOTTLE;
		}
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), pos);
		if (network != null) {
			BoundingBox bakehouse = TownPlots.footprint(level, network, TownPlots.Kind.BAKEHOUSE);
			if (bakehouse != null && bakehouse.inflatedBy(3).isInside(pos)) return Items.BREAD;
		}
		Optional<BlockPos> site = level.getPoiManager().findClosest(type -> TRADE_ICON.keySet().stream().anyMatch(type::is), pos, 8, PoiManager.Occupancy.ANY);
		if (site.isEmpty()) return null;
		Optional<Holder<PoiType>> type = level.getPoiManager().getType(site.get());
		return type.flatMap(holder -> TRADE_ICON.entrySet().stream().filter(entry -> holder.is(entry.getKey())).map(Map.Entry::getValue).findFirst()).orElse(null);
	}
}
