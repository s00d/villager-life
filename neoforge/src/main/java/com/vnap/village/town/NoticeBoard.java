package com.vnap.village.town;

import com.vnap.Nbt;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.VillagerHome;
import com.vnap.mood.MoodConfig;
import com.vnap.mood.MoodState;
import com.vnap.mood.VillageMoodApi;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.network.NoticeBoardPayload;
import com.vnap.sound.GolemSounds;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Coins;
import com.vnap.village.market.Market;
import com.vnap.village.market.Prices;
import com.vnap.village.market.Shops;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The town's notice board (NoticeBoardBlock, set on the square by the town, or by a player): read with an empty hand
 * (NoticeBoardScreen): the town at a glance, its builds and how far along, what its stall waits for. A player who
 * brings a wanted thing is paid its full price (a sale over the counter fetches less than half); coins in hand go
 * to the treasury. Both are a help the village is glad of (its mood).
 */
public final class NoticeBoard {
	/** Wanted things shown at most, the biggest orders first. */
	static final int WANTED = 18;
	/** Copper of gifts a day that still lifts a village's mood (a point per 16), per player. */
	static final int GIFT_MOOD_CAP = 5 * 16;
	/** A player's last use of a board (game time): reading one walks every house and lays out the whole map. */
	private static final Map<UUID, Long> LAST_USE = new HashMap<>();
	private static final long USE_PAUSE = 10;
	private static final int LOST_KEPT = 54;

	private NoticeBoard() {
	}

	/** What the board shows, read at {@code pos}; null away from any town. */
	public static @Nullable NoticeBoardPayload read(ServerLevel level, BlockPos pos) {
		return read(level, pos, null);
	}

	public static @Nullable NoticeBoardPayload read(ServerLevel level, BlockPos pos, @Nullable Player reader) {
		Mayors.Office office = Towns.of(level, pos);
		if (office == null) return null;
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), office.bell);
		Shops.Shop shop = Shops.near(level, office.bell);
		long day = Family.day(level);
		MoodState mood = VillagerHome.moodAt(level, office.bell);
		List<NoticeBoardPayload.Project> projects = new ArrayList<>();
		if (network != null) {
			Map<String, int[]> byLabel = new java.util.LinkedHashMap<>();
			for (Roads.Segment segment : network.segments) {
				int[] sum = byLabel.computeIfAbsent(segment.label, k -> new int[2]);
				sum[0] += segment.job().remaining();
				sum[1] += segment.spec.blocks.size();
			}
			byLabel.forEach((label, sum) -> projects.add(new NoticeBoardPayload.Project("board.villager_life.build." + label, "", sum[0], sum[1])));
		}
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if ((house.done && !house.mending) || !Towns.ours(level, office.bell, house.origin)) continue;
			String name = level.getEntity(house.owner) instanceof net.minecraft.world.entity.npc.Villager owner ? VillagerHome.nameOf(owner) : "";
			int total = Math.max(1, house.schematic(level).blocks.size());
			String key = Mayors.HALLS.contains(house.plan) ? "hall" : Shops.STALLS.contains(house.plan) ? "stall" : house.mending ? "mending" : "house";
			projects.add(new NoticeBoardPayload.Project("board.villager_life.build." + key, name, Math.min(total, house.job(level).remaining()), total));
		}
		List<NoticeBoardPayload.Wanted> wanted = new ArrayList<>();
		if (shop != null) {
			List<Map.Entry<Item, Integer>> orders = new ArrayList<>(shop.orders.entrySet());
			orders.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
			for (Map.Entry<Item, Integer> order : orders) {
				if (wanted.size() >= WANTED || order.getValue() <= 0) continue;
				ItemStack item = new ItemStack(order.getKey(), order.getValue());
				wanted.add(new NoticeBoardPayload.Wanted(item, Math.max(1, Prices.price(item.copyWithCount(1)))));
			}
		}
		int serviceIn = network != null && TownPlots.built(network, TownPlots.Kind.CHAPEL)
				? (int) Math.floorMod(-day, (long) TownRules.SERVICE_EVERY) : -1;
		int caravanIn = shop == null ? -1 : (int) Math.max(0, TownRules.CARAVAN_DAYS - (day - shop.caravanDay));
		boolean fair = shop != null && com.vnap.village.market.Fair.today(level, shop);
		int lost = 0;
		if (network != null) for (ItemStack stack : network.lost) lost += stack.getCount();
		return new NoticeBoardPayload(pos, office.mayor == null ? "" : office.name, Towns.population(level, office.bell), Towns.limit(level, office.bell),
				Math.max(0, office.treasury), mood == null ? 0 : Math.round(mood.mood), fair, serviceIn, caravanIn, lost, projects, wanted,
				plan(level, office.bell, network, reader), errands(level, office.bell, reader, day), network == null ? -1 : network.flag,
				network == null ? List.of() : network.chronicle.stream().map(entry -> new NoticeBoardPayload.Chronicle(entry.day(), entry.key(), entry.args())).toList(),
				shop == null ? List.of() : shop.playerOrders.stream().map(order -> new NoticeBoardPayload.Shipment(order.item.copyWithCount(order.count), order.name)).toList());
	}

	/** The town's errands as the reader sees them: open, its own, someone else's. */
	static List<NoticeBoardPayload.Errand> errands(ServerLevel level, BlockPos bell, @Nullable Player reader, long day) {
		List<NoticeBoardPayload.Errand> out = new ArrayList<>();
		for (Errands.Errand errand : Errands.of(level, bell)) {
			int state = errand.taker == null ? NoticeBoardPayload.Errand.OPEN
					: reader != null && reader.getUUID().equals(errand.taker) ? NoticeBoardPayload.Errand.MINE : NoticeBoardPayload.Errand.TAKEN;
			out.add(new NoticeBoardPayload.Errand(errand.id, errand.name, errand.shown(), errand.reward, state,
					(int) Math.max(0, Errands.DAYS - (day - errand.posted))));
		}
		return out;
	}

	/** The town's plan from above for the board's map (NoticeBoardScreen): its buildings, roads, streets and lots. */
	static NoticeBoardPayload.PlanMap plan(ServerLevel level, BlockPos bell, Roads.@Nullable Network network, @Nullable Player reader) {
		int radius = (int) Math.ceil(Towns.reach(level, bell));
		int youX = reader == null ? 0 : reader.getBlockX() - bell.getX();
		int youZ = reader == null ? 0 : reader.getBlockZ() - bell.getZ();
		List<Integer> buildings = new ArrayList<>();
		List<Integer> roads = new ArrayList<>();
		List<Integer> streets = new ArrayList<>();
		List<NoticeBoardPayload.Lot> lots = new ArrayList<>();
		if (network != null) {
			for (net.minecraft.world.level.levelgen.structure.BoundingBox box : Roads.blocked(level, network)) {
				if (Math.abs(box.getCenter().getX() - bell.getX()) > radius + 16 || Math.abs(box.getCenter().getZ() - bell.getZ()) > radius + 16) continue;
				buildings.addAll(List.of(box.minX() - bell.getX(), box.minZ() - bell.getZ(), box.maxX() - bell.getX(), box.maxZ() - bell.getZ()));
			}
			for (BlockPos cell : network.cells) roads.add(NoticeBoardPayload.PlanMap.pack(cell.getX() - bell.getX(), cell.getZ() - bell.getZ()));
			for (BlockPos cell : TownPlan.streetCells(network)) {
				if (!network.cells.contains(cell)) streets.add(NoticeBoardPayload.PlanMap.pack(cell.getX() - bell.getX(), cell.getZ() - bell.getZ()));
			}
			for (TownPlan.Lot lot : network.lots) {
				int state = lot.player != null ? reader != null && reader.getUUID().equals(lot.player) ? NoticeBoardPayload.Lot.MINE : NoticeBoardPayload.Lot.THEIRS
						: lot.taken ? NoticeBoardPayload.Lot.HOUSE : NoticeBoardPayload.Lot.FREE;
				lots.add(new NoticeBoardPayload.Lot(lot.id, lot.box.minX() - bell.getX(), lot.box.minZ() - bell.getZ(), lot.box.maxX() - bell.getX(),
						lot.box.maxZ() - bell.getZ(), state, lot.price(), lot.playerName));
			}
		}
		return new NoticeBoardPayload.PlanMap(radius, youX, youZ, buildings.stream().mapToInt(Integer::intValue).toArray(),
				roads.stream().mapToInt(Integer::intValue).toArray(), streets.stream().mapToInt(Integer::intValue).toArray(), lots);
	}

	/** What a player does at the board (NoticeBoardActionPayload), standing by it: buy a lot, take an errand, give one up. */
	public static void act(ServerLevel level, ServerPlayer player, BlockPos board, int action, int id) {
		if (!player.blockPosition().closerThan(board, 8) || !level.getBlockState(board).is(com.vnap.block.LifeBlocks.NOTICE_BOARD)
				|| tooSoon(level, player)) return;
		switch (action) {
			case com.vnap.network.NoticeBoardActionPayload.BUY_LOT -> buy(level, player, board, id);
			case com.vnap.network.NoticeBoardActionPayload.TAKE_ERRAND -> {
				// an errand of this board's town only (the id came from the client)
				Roads.Network town = Roads.get(level.getServer()).near(level.dimension(), board);
				boolean ours = town != null && Errands.of(level, town.bell).stream().anyMatch(errand -> errand.id == id);
				if (ours && Errands.take(level, player, id)) com.vnap.Mc.say(player, Component.translatable("errand.villager_life.taken"), true);
				show(level, player, board);
			}
			case com.vnap.network.NoticeBoardActionPayload.SET_FLAG -> {
				Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), board);
				if (network == null || id < 0) return;
				if (!mayRaiseFlag(level, network, player)) {
					com.vnap.Mc.say(player, Component.translatable("board.villager_life.flag_not_yours"), true);
					return;
				}
				TownFlag.raise(level, network, id);
				com.vnap.Mc.say(player, Component.translatable("board.villager_life.flag_raised"), true);
				com.vnap.progress.VillageAdvancements.award(player, "town/flag");
				show(level, player, board);
			}
			case com.vnap.network.NoticeBoardActionPayload.CANCEL_ERRAND -> {
				if (Errands.cancel(level, player)) com.vnap.Mc.say(player, Component.translatable("errand.villager_life.cancelled"), true);
				show(level, player, board);
			}
			default -> {
			}
		}
	}

	/** A player buys a lot from the board's map: a free lot of the board's town. */
	private static void buy(ServerLevel level, ServerPlayer player, BlockPos board, int id) {
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), board);
		TownPlan.Lot lot = network == null ? null : TownPlan.byId(network, id);
		if (network == null || lot == null) return;
		if (!lot.free()) {
			com.vnap.Mc.say(player, Component.translatable("board.villager_life.lot_taken"), true);
		} else if (TownPlan.buy(level, network, lot, player)) {
			level.playSound(null, board, GolemSounds.DONATION, SoundSource.BLOCKS, 1.0F, 1.0F);
			com.vnap.Mc.say(player, Component.translatable("board.villager_life.lot_bought", Coins.text(lot.price())), false);
		} else {
			com.vnap.Mc.say(player, Component.translatable("board.villager_life.lot_poor", Coins.text(lot.price())), true);
		}
		show(level, player, board);
	}

	/**
	 * Within USE_PAUSE of its last use: a modified client sending board packets every tick made the server read the
	 * town again and again (and repaint every banner for a flag).
	 */
	public static boolean tooSoon(ServerLevel level, ServerPlayer player) {
		long now = level.getGameTime();
		Long last = LAST_USE.get(player.getUUID());
		if (last != null && now - last >= 0 && now - last < USE_PAUSE) return true;
		LAST_USE.put(player.getUUID(), now);
		return false;
	}

	/** The town's flag is for its neighbours (they slept there), lot owners and operators, not any passer-by. */
	static boolean mayRaiseFlag(ServerLevel level, Roads.Network network, ServerPlayer player) {
		if (com.vnap.Mc.op(player)) return true;
		if (com.vnap.village.home.Neighbours.get(level.getServer()).is(level, network.bell, player.getUUID())) return true;
		for (TownPlan.Lot lot : network.lots) if (player.getUUID().equals(lot.player)) return true;
		return false;
	}

	/** A player logged out: its pause goes. */
	public static void forget(UUID player) {
		LAST_USE.remove(player);
	}

	public static void show(ServerLevel level, ServerPlayer player, BlockPos pos) {
		adopt(level, pos);
		NoticeBoardPayload board = read(level, pos, player);
		if (board == null) {
			com.vnap.Mc.say(player, Component.translatable("board.villager_life.no_town"), true);
			return;
		}
		PacketDistributor.sendToPlayer(player, board);
	}

	/** A player brings what the stall is waiting for: up to what is wanted goes on its shelf, paid in full. True when any did. */
	public static boolean deliver(ServerLevel level, Player player, BlockPos pos, ItemStack stack) {
		Mayors.Office office = Towns.of(level, pos);
		Shops.Shop shop = office == null ? null : Shops.near(level, office.bell);
		if (shop == null) return false;
		int wanted = shop.orders.getOrDefault(stack.getItem(), 0);
		int n = Math.min(wanted, stack.getCount());
		if (n <= 0 || !stack.getComponentsPatch().isEmpty()) return false;
		// paid in full, so within the player's day of selling to the town like the stall's (each player its own budget)
		int each = Math.max(1, Prices.price(stack.copyWithCount(1)));
		com.vnap.village.market.ShopMenu.newDay(level, shop);
		int left = com.vnap.village.market.Fair.budget(level, shop) - shop.paid(player.getUUID());
		n = Math.min(n, left / each);
		if (n <= 0) {
			com.vnap.Mc.say(player, Component.translatable("vnap.shop.budget"), true);
			return false;
		}
		int pay = each * n;
		shop.paidPlayers.merge(player.getUUID(), pay, Integer::sum);
		shop.put(stack.copyWithCount(1), n, false);
		if (wanted - n > 0) shop.orders.put(stack.getItem(), wanted - n);
		else shop.orders.remove(stack.getItem());
		stack.shrink(n);
		Market.payOut(level, shop, pay);
		Coins.give(player, pay);
		level.playSound(null, pos, GolemSounds.BOARD_PIN, SoundSource.BLOCKS, 1.0F, 1.0F);
		com.vnap.Mc.say(player, Component.translatable("board.villager_life.delivered", n, Coins.text(pay)), true);
		gift(level, player, pos, pay);
		return true;
	}

	/** Coins in hand go to the town's treasury. True when it took any. */
	public static boolean donate(ServerLevel level, Player player, BlockPos pos, ItemStack stack) {
		Mayors.Office office = Towns.of(level, pos);
		int value = Coins.value(stack);
		if (office == null || value <= 0) return false;
		stack.setCount(0);
		Market.deposit(level, office, value);
		level.playSound(null, pos, GolemSounds.DONATION, SoundSource.BLOCKS, 1.0F, 1.0F);
		com.vnap.Mc.say(player, Component.translatable("board.villager_life.donated", Coins.text(value)), true);
		gift(level, player, pos, value);
		return true;
	}

	/** The village is glad of a player's help: a point of mood for every 16 copper of it, up to five a day. */
	private static void gift(ServerLevel level, Player player, BlockPos pos, int copper) {
		long day = Family.day(level);
		// kept with the player: a server restart is no new day of giving
		// under the persisted tag: a death is no new day either
		net.minecraft.nbt.CompoundTag data = Nbt.getCompound(player.getPersistentData(), Player.PERSISTED_NBT_TAG);
		player.getPersistentData().put(Player.PERSISTED_NBT_TAG, data);
		int before = Nbt.getLong(data, "vnap_gift_day") == day ? Nbt.getInt(data, "vnap_gift_copper") : 0;
		int after = Math.min(GIFT_MOOD_CAP, before + copper);
		data.putLong("vnap_gift_day", day);
		data.putInt("vnap_gift_copper", after);
		int points = after / 16 - before / 16;
		if (points > 0) VillageMoodApi.applyAt(level, pos, points, points, "town_help");
	}

	/** A player's things taken off a lot of the town at {@code bell}: they wait for the lost and found by its board. */
	public static void lose(ServerLevel level, BlockPos bell, List<ItemStack> things) {
		if (things.isEmpty()) return;
		Roads roads = Roads.get(level.getServer());
		Roads.Network network = roads.near(level.dimension(), bell);
		if (network == null) return;
		for (ItemStack stack : things) {
			if (stack.isEmpty()) continue;
			// like with like, up to a stack: lots cleared one after another bring the same stone and dirt over and over
			for (ItemStack kept : network.lost) {
				if (stack.isEmpty()) break;
				if (ItemStack.isSameItemSameComponents(kept, stack) && kept.getCount() < kept.getMaxStackSize()) {
					int n = Math.min(stack.getCount(), kept.getMaxStackSize() - kept.getCount());
					kept.grow(n);
					stack.shrink(n);
				}
			}
			if (!stack.isEmpty()) network.lost.add(stack.copy());
		}
		// a chest's worth waits at most (no board, a full chest): the oldest are given up, the save does not grow for good
		while (network.lost.size() > LOST_KEPT) network.lost.remove(0);
		roads.setDirty();
		lostAndFound(level, network);
	}

	/**
	 * The lost and found: a chest beside the town's notice board (set down by hand the first time something is lost),
	 * the lost things put into it as far as it has room; the rest waits for the next day.
	 */
	public static void lostAndFound(ServerLevel level, Roads.Network network) {
		if (network.lost.isEmpty()) return;
		BlockPos board = board(level, network);
		if (board == null) return;
		BlockPos chest = null;
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(board.relative(side)).getBlock() instanceof net.minecraft.world.level.block.ChestBlock) chest = board.relative(side);
		}
		if (chest == null) {
			net.minecraft.core.Direction facing = level.getBlockState(board).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
			for (net.minecraft.core.Direction side : new net.minecraft.core.Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
				BlockPos spot = board.relative(side);
				if (com.vnap.village.home.Placements.pending(level, spot)) return;
				if (!level.getBlockState(spot).canBeReplaced() || !level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), net.minecraft.core.Direction.UP)) continue;
				com.vnap.village.home.Placements.add(level, spot, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()
						.setValue(net.minecraft.world.level.block.ChestBlock.FACING, facing), null);
				network.features.add(spot);
				return;
			}
			return;
		}
		if (!(level.getBlockEntity(chest) instanceof net.minecraft.world.Container container)) return;
		java.util.Iterator<ItemStack> things = network.lost.iterator();
		while (things.hasNext()) {
			ItemStack left = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, container, things.next(), null);
			if (!left.isEmpty()) break;
			things.remove();
		}
		Roads.get(level.getServer()).setDirty();
	}

	/** A board a player set up (or reads) in a town is that town's board too: its lost and found stands by it. */
	public static void adopt(ServerLevel level, BlockPos pos) {
		Roads roads = Roads.get(level.getServer());
		Roads.Network network = roads.near(level.dimension(), pos);
		if (network == null || !Towns.ours(level, network.bell, pos)) return;
		if (network.features.add(pos.immutable())) roads.setDirty();
	}

	/** The town's notice board (on the square), or null when it has none in loaded ground. */
	public static @Nullable BlockPos board(ServerLevel level, Roads.Network network) {
		for (BlockPos thing : network.features) {
			if (level.isLoaded(thing) && level.getBlockState(thing).is(com.vnap.block.LifeBlocks.NOTICE_BOARD)) return thing;
		}
		return null;
	}

}
