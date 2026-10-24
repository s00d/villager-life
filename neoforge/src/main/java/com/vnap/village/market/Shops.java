package com.vnap.village.market;

import com.vnap.Nbt;
import com.vnap.village.town.TownRules;
import com.vnap.block.DecorBlock;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyDeaths;
import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A big village keeps a stall: a villager without a trade is chosen shopkeeper, builds the stall by hand near the bell
 * (a work place; it keeps its own home) and stands at the counter by day. The stall's goods and till belong to the
 * village, not the keeper: a new keeper takes them over. While a village has no stall open, everything works the
 * old way (families put things up without buying them).
 */
public final class Shops extends SavedData {
	private static final String NAME = "villager_life_shops";
	public static final List<String> STALLS = List.of("shop_house");
	public static final String COUNTER = "market_counter";
	/** The keeper minds the counter from this far at most; further off the stall is shut. */
	public static final double AT_COUNTER = 10.0;
	static final int NO_ROOM_SAID = 3;

	private final List<Shop> shops = new ArrayList<>();

	/** One village's stall: its keeper, the counter, the till and the goods. */
	public static final class Shop {
		public final ResourceKey<Level> dimension;
		public BlockPos bell;
		public @Nullable UUID keeper;
		public String name = "";
		public long electedDay;
		public long seenDay;
		public long lastElection = Long.MIN_VALUE;
		/** The stall's counter once it stands; null while it is being built. */
		public @Nullable BlockPos counter;
		/** Copper in the till. */
		public int till;
		/** The goods: false until the first delivery (the stall opens stocked and with money in the till). */
		public boolean stocked;
		public final List<Lot> stock = new ArrayList<>();
		public long caravanDay = Long.MIN_VALUE / 2;
		/** The day the caravan last came: the fair (Fair). Not the shop's opening day, which only starts the caravan's week. */
		public long fairDay = Long.MIN_VALUE / 2;
		public long noRoomDay = Long.MIN_VALUE / 2;
		public long noRoomSince = Long.MIN_VALUE / 2;
		/** Day of the last morning delivery (Market.dailyShop runs once a day whoever calls it). */
		public long restockDay = Long.MIN_VALUE / 2;
		/** Building materials brought from abroad today for builders at a sold-out stall (Market.supply); not saved. */
		int abroadTaken;
		long abroadDay = Long.MIN_VALUE / 2;

		int abroadLeft(long day) {
			if (abroadDay != day) {
				abroadDay = day;
				abroadTaken = 0;
			}
			return Math.max(0, TownRules.ABROAD_PER_DAY - abroadTaken);
		}
		/** What the stall paid players today (TownRules.PLAYER_BUDGET a day). */
		public long playerDay = Long.MIN_VALUE / 2;
		/** What the stall paid each player today (PLAYER_BUDGET is each player's, not one for all of them). */
		public final java.util.Map<java.util.UUID, Integer> paidPlayers = new java.util.HashMap<>();

		public int paid(java.util.UUID player) {
			return paidPlayers.getOrDefault(player, 0);
		}
		/** What players ordered here and paid for: it comes with the next caravan (Orders). */
		public final List<Orders.Order> playerOrders = new ArrayList<>();
		/** Each player's parcel box by this stall, once the keeper has set one down (Orders.deliver). */
		public final java.util.Map<UUID, BlockPos> parcels = new java.util.HashMap<>();
		/** Goods the stall ran out of that a buyer asked for: they come with the next morning's delivery. */
		public final java.util.Map<net.minecraft.world.item.Item, Integer> orders = new java.util.LinkedHashMap<>();

		public Shop(ResourceKey<Level> dimension, BlockPos bell) {
			this.dimension = dimension;
			this.bell = bell.immutable();
		}

		/** The lot of this very item (with its components), or null. */
		public @Nullable Lot lot(ItemStack item) {
			for (Lot lot : stock) if (ItemStack.isSameItemSameComponents(lot.item, item)) return lot;
			return null;
		}

		public int count(net.minecraft.world.item.Item item) {
			int total = 0;
			for (Lot lot : stock) if (lot.item.is(item)) total += lot.count;
			return total;
		}

		/** Takes {@code count} (it is there) out of the lot; an empty lot of the player's goods goes. */
		public void take(Lot lot, int count) {
			lot.count -= count;
			if (lot.count <= 0 && (lot.fromPlayer || Market.target(lot.item.getItem()) == 0)) stock.remove(lot);
		}

		/** Adds goods to the stall at their worth. */
		public void put(ItemStack item, int count, boolean fromPlayer) {
			Lot lot = lot(item);
			if (lot == null) {
				lot = new Lot(item.copyWithCount(1), 0, Prices.price(item), fromPlayer);
				stock.add(lot);
			}
			lot.count += count;
		}
	}

	/** So many of one item at the stall, at {@code price} copper each (what a villager pays). */
	public static final class Lot {
		public final ItemStack item;
		public int count;
		public final int price;
		/** Sold by a player: it goes once sold out; the stall's own goods are kept in its catalogue. */
		public final boolean fromPlayer;

		public Lot(ItemStack item, int count, int price, boolean fromPlayer) {
			this.item = item;
			this.count = count;
			this.price = price;
			this.fromPlayer = fromPlayer;
		}
	}

	public static Shops get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, Shops::new, Shops::load, Shops::save);
	}

	public List<Shop> shops() {
		return shops;
	}

	public static boolean isKeeper(Villager villager) {
		return villager instanceof VillagerLifeData data && data.vnap$shopkeeper();
	}

	/** The town's bell was hung anew (BellMove): its stall goes with it. */
	public static void moveBell(ServerLevel level, BlockPos from, BlockPos to) {
		Shops all = get(level.getServer());
		Shop shop = all.shopNear(level.dimension(), from);
		if (shop == null || !shop.bell.equals(from)) return;
		shop.bell = to;
		all.setDirty();
	}

	public @Nullable Shop shopNear(ResourceKey<Level> dimension, BlockPos pos) {
		Shop best = null;
		for (Shop shop : shops) {
			if (shop.dimension.equals(dimension) && shop.bell.closerThan(pos, TownRules.SPAN)
					&& (best == null || shop.bell.distSqr(pos) < best.bell.distSqr(pos))) best = shop;
		}
		return best;
	}

	/** The stall of the village around {@code pos} that is stocked (the economy is on there), or null. */
	public static @Nullable Shop near(ServerLevel level, BlockPos pos) {
		Shop shop = get(level.getServer()).shopNear(level.dimension(), pos);
		return shop != null && shop.stocked ? shop : null;
	}

	/** The village's stall for this villager (by its bell), stocked, or null: then it lives the old way. */
	public static @Nullable Shop of(ServerLevel level, Villager villager) {
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
		return bell == null ? null : near(level, bell);
	}

	public @Nullable Shop shopOf(UUID keeper) {
		for (Shop shop : shops) if (keeper.equals(shop.keeper)) return shop;
		return null;
	}

	public @Nullable Shop atCounter(ResourceKey<Level> dimension, BlockPos counter) {
		for (Shop shop : shops) if (shop.dimension.equals(dimension) && counter.equals(shop.counter)) return shop;
		return null;
	}

	public static @Nullable Villager keeper(ServerLevel level, Shop shop) {
		return shop.keeper != null && level.getEntity(shop.keeper) instanceof Villager keeper && keeper.isAlive() && isKeeper(keeper)
				? keeper : null;
	}

	/** Open for trade: built and stocked, its counter standing, the keeper by it, in the hours of the market. */
	public static boolean open(ServerLevel level, Shop shop) {
		BlockPos counter = shop.counter;
		if (!shop.stocked || counter == null || !level.isLoaded(counter) || !isCounter(level, counter)) return false;
		if (!marketHours(level)) return false;
		Villager keeper = keeper(level, shop);
		if (keeper == null || keeper.isSleeping()) return false;
		// while the keeper builds the shop house the counter still serves (else no build in a new village gets its
		// materials, nor the shop house itself its last ones: everything waits on the shop that waits on itself)
		HouseProject stall = stallOf(level, shop);
		return keeper.blockPosition().closerThan(counter, AT_COUNTER)
				|| (stall != null && !stall.done && keeper.blockPosition().closerThan(counter, com.vnap.village.town.TownRules.NEAR_BELL));
	}

	/** The stall's hours: from early morning to midday. */
	public static boolean marketHours(net.minecraft.world.level.Level level) {
		float time = Family.dayFraction(level);
		return time >= 0.04F && time < 0.5F;
	}

	public static boolean isCounter(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getBlock() instanceof DecorBlock decor && decor.name().equals(COUNTER);
	}

	// ---- the keeper ------------------------------------------------------------------------

	/** Once a day for every awake adult in a ticking chunk (Family.daily, after the mayor's). */
	public static void daily(ServerLevel level, Villager villager, long day) {
		if (villager.isBaby() || com.vnap.Mc.tags(villager).contains(Speech.TEST_TAG)) return;
		Shops shops = get(level.getServer());
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
		if (isKeeper(villager)) {
			shops.keep(level, villager, bell, day);
			return;
		}
		if (bell == null) return;
		Shop shop = shops.shopNear(level.dimension(), bell);
		if (shop != null) {
			shops.vacateIfGone(level, shop, day);
			Market.dailyShop(level, shop, day);
		}
		if (shop == null) {
			// every village has its stall from the start: everything the villagers build or put up is bought there
			shop = new Shop(level.dimension(), bell);
			shops.shops.add(shop);
			Market.openShop(level, shop, day);
			shops.setDirty();
		}
		if (shop.keeper == null && shop.lastElection != day) shops.choose(level, shop, day, null);
	}

	/** A grown-up of the late keeper's family who may take the stall over: not the mayor, not leaving, no keeper yet. */
	public static boolean candidate(Villager villager) {
		return villager.isAlive() && !villager.isBaby() && !isKeeper(villager) && !Mayors.isMayor(villager)
				&& !com.vnap.village.home.Hamlets.leaving(villager);
	}

	/**
	 * The stall passes down: to the late keeper's spouse, else its eldest grown child in the village. Null when nobody
	 * of the family is here: then a shopkeeper comes from elsewhere ({@link #choose}).
	 */
	public static @Nullable Villager heir(ServerLevel level, Shop shop, Villager late) {
		UUID spouse = Family.state(late).spouse;
		return com.vnap.village.home.Towns.kin(level, shop.bell, late, Shops::candidate).stream()
				.min(Comparator.comparingInt((Villager villager) -> villager.getUUID().equals(spouse) ? 0 : 1)
						.thenComparingLong(villager -> Family.state(villager).bornDay).thenComparing(Villager::getUUID))
				.orElse(null);
	}

	/**
	 * A stall without a keeper and nobody of the keeper's family to take it on: a shopkeeper comes to the village (a
	 * new villager by the bell, it lives in the shop house). The village's own people keep their lives. Null only
	 * when this dimension is not the stall's or the bell has no ground about it.
	 */
	public @Nullable Villager choose(ServerLevel level, Shop shop, long day, @Nullable UUID except) {
		shop.lastElection = day;
		setDirty();
		if (!shop.dimension.equals(level.dimension()) || !level.isLoaded(shop.bell)) return null;
		Villager chosen = arrive(level, shop.bell);
		if (chosen == null) return null;
		take(level, shop, chosen, day);
		Speech.queue(chosen, "Shopkeeper Comes to the Village", null, 4L);
		return chosen;
	}

	/** A shopkeeper from elsewhere: a grown villager without a trade by the bell, of this village from now on. */
	static @Nullable Villager arrive(ServerLevel level, BlockPos bell) {
		BlockPos spot = null;
		for (BlockPos at : com.vnap.village.town.Rings.around(bell, 1, 4, 1, 8)) {
			if (!level.isLoaded(at)) continue;
			// right by the bell, where the village gathers, at its height or below: never above it (the roof of a
			// canopy over the bell was "ground" too, and a keeper stood on it for days with no way down)
			BlockPos ground = com.vnap.village.town.Ground.near(level, at.getX(), at.getZ(), bell.getY(), 0, 4);
			if (ground != null && level.getFluidState(ground.above()).isEmpty()) {
				spot = ground.above();
				break;
			}
		}
		if (spot == null) return null;
		Villager keeper = com.vnap.Mc.create(net.minecraft.world.entity.EntityType.VILLAGER, level);
		if (keeper == null) return null;
		com.vnap.Mc.snap(keeper, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0F, 0.0F);
		keeper.setPersistenceRequired();
		keeper.getBrain().setMemory(MemoryModuleType.MEETING_POINT, net.minecraft.core.GlobalPos.of(level.dimension(), bell));
		level.addFreshEntity(keeper);
		return keeper;
	}

	private void take(ServerLevel level, Shop shop, Villager keeper, long day) {
		shop.keeper = keeper.getUUID();
		shop.name = VillagerHome.nameOf(keeper);
		shop.electedDay = day;
		shop.seenDay = day;
		setDirty();
		if (keeper instanceof VillagerLifeData data) data.vnap$setShopkeeper(true);
		Mayors.leaveJobSites(keeper);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, keeper.getBoundingBox().inflate(TownRules.NEAR_BELL))) {
			com.vnap.Mc.say(player, Component.translatable("vnap.shop.chosen", shop.name), true);
		}
	}

	/** The keeper's own day: still its village's keeper, its stall standing (planned, built or mended). */
	private void keep(ServerLevel level, Villager keeper, @Nullable BlockPos bell, long day) {
		Shop shop = shopOf(keeper.getUUID());
		if (shop == null || bell == null || !shop.dimension.equals(level.dimension()) || !shop.bell.closerThan(bell, TownRules.SPAN)) {
			// moved away, or the record is gone: the village chooses again
			step(level, keeper, day);
			return;
		}
		shop.seenDay = day;
		setDirty();
		Market.dailyShop(level, shop, day);
		stall(level, shop, keeper, day);
	}

	/** The stall stands, is going up, or is planned now; a counter knocked down sends the keeper back to work on it. */
	private void stall(ServerLevel level, Shop shop, Villager keeper, long day) {
		HouseProject stall = stallOf(level, shop);
		if (stall == null) {
			stall = HomeProjects.start(level, keeper, day, STALLS);
			if (stall == null) {
				if (shop.noRoomSince == Long.MIN_VALUE / 2) shop.noRoomSince = day;
				if (day - shop.noRoomDay >= NO_ROOM_SAID) {
					shop.noRoomDay = day;
					Speech.queue(keeper, "Shopkeeper Has No Room for the Stall", null, 2L);
				}
				// a village with no ground for a stall still trades: a lone counter by the bell does
				if (shop.counter == null && day - shop.noRoomSince >= TownRules.KIOSK_DAYS) kiosk(level, shop, keeper);
				return;
			}
			Speech.queue(keeper, "Shopkeeper Lays Out the Stall", null, 2L);
			counterFirst(level, shop, stall);
			return;
		}
		// the counter stands first and always: knocked down, the keeper sets it again at once (the stall is the town's base, free)
		counterFirst(level, shop, stall);
		// the shop house is the stall's: a new keeper builds on what the late one began
		if (!stall.owner.equals(keeper.getUUID())) {
			stall.owner = keeper.getUUID();
			VillageMoodSavedData.get(level).markDirty();
		}
		if (!stall.done) return;
		// a player broke the counter or any of the shop house: the keeper mends what is missing, out of its own goods
		com.vnap.village.home.HomeProjects.inspect(level, stall, keeper, day);
		// the keeper without a home of its own lives in the shop house
		if (!com.vnap.village.home.HomeProjects.claimed(keeper)) com.vnap.village.home.HomeProjects.moveIn(level, keeper, stall);
	}

	/** A counter on its own by the bell, facing it, where the keeper trades until a stall can go up. */
	static boolean kiosk(ServerLevel level, Shop shop, Villager keeper) {
		var block = Market.decorBlock(COUNTER);
		if (block == null) return false;
		for (BlockPos column : com.vnap.village.town.Rings.around(shop.bell, 3, 10, 1, 16)) {
			if (!level.isLoaded(column)) continue;
			BlockPos ground = com.vnap.village.town.Ground.near(level, column.getX(), column.getZ(), shop.bell.getY());
			if (ground == null) continue;
			BlockPos at = ground.above();
			net.minecraft.core.Direction front = net.minecraft.core.Direction.getNearest(shop.bell.getX() - at.getX(), 0, shop.bell.getZ() - at.getZ());
			BlockPos behind = at.relative(front.getOpposite());
			if (!level.getBlockState(at).canBeReplaced() || !level.getBlockState(behind).canBeReplaced()
					|| !level.getBlockState(behind.above()).canBeReplaced() || !level.getFluidState(at).isEmpty()
					|| !level.getBlockState(behind.below()).isFaceSturdy(level, behind.below(), net.minecraft.core.Direction.UP)) continue;
			// the keeper carries the counter it brought there and sets it down (PlaceBehavior): open once it stands
			com.vnap.village.home.Placements.add(level, at, block.defaultBlockState().setValue(DecorBlock.FACING, front), keeper.getUUID());
			shop.counter = at.immutable();
			get(level.getServer()).setDirty();
			Speech.queue(keeper, "Shopkeeper Sets Up a Kiosk", null, 2L);
			return true;
		}
		return false;
	}

	/**
	 * The shop house's counter goes in first, where its plan has it, and the stall trades from it at once; the walls
	 * go up round it. Waiting for the whole house left a new village without a shop for days (its builds waiting on
	 * materials, the keeper building its house with nothing to buy). The shop house is the town's base: free (HouseProject.base).
	 */
	static void counterFirst(ServerLevel level, Shop shop, HouseProject stall) {
		if (shop.counter != null && level.isLoaded(shop.counter) && isCounter(level, shop.counter) && stall.footprint(level).isInside(shop.counter)) return;
		var block = Market.decorBlock(COUNTER);
		if (block == null) return;
		com.vnap.village.build.BuildJob job = stall.job(level);
		for (com.vnap.village.script.SchematicSpec.PlacedBlock placed : stall.schematic(level).blocks) {
			if (!placed.state().is(block)) continue;
			BlockPos at = com.vnap.village.build.BuildJob.at(stall.origin, stall.rotation, placed.rel());
			if (!level.isLoaded(at)) return;
			if (!isCounter(level, at)) {
				var there = level.getBlockState(at);
				// a stall set into a slope: its counter cell is the hill's own earth, which the build would dig out anyway
				if (!(there.canBeReplaced() || com.vnap.village.build.BuildJob.naturalGround(there))) return;
				level.setBlock(at, job.stateOf(level, at, placed), net.minecraft.world.level.block.Block.UPDATE_ALL);
			}
			BlockPos kiosk = shop.counter;
			shop.counter = at.immutable();
			// the kiosk by the bell has done its part
			if (kiosk != null && !kiosk.equals(at) && level.isLoaded(kiosk) && isCounter(level, kiosk)) level.removeBlock(kiosk, false);
			com.vnap.mood.VillageMoodSavedData.get(level).markDirty();
			get(level.getServer()).setDirty();
			return;
		}
	}

	/** Where the keeper stands to trade: behind the stall's counter (its focal mark), or behind the kiosk. */
	public static @Nullable BlockPos keeperSpot(ServerLevel level, Shop shop) {
		HouseProject stall = stallOf(level, shop);
		if (stall != null && stall.done) return com.vnap.village.build.BuildJob.at(stall.origin, stall.rotation, stall.schematic(level).focal);
		BlockPos counter = shop.counter;
		if (counter == null || !level.isLoaded(counter) || !isCounter(level, counter)) return null;
		return counter.relative(level.getBlockState(counter).getValue(DecorBlock.FACING).getOpposite());
	}

	/** The village's stall project (the one nearest the bell), or null. */
	public static @Nullable HouseProject stallOf(ServerLevel level, Shop shop) {
		HouseProject best = null;
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (STALLS.contains(house.plan) && house.bell.closerThan(shop.bell, TownRules.SPAN)
					&& (best == null || house.origin.distSqr(shop.bell) < best.origin.distSqr(shop.bell))) best = house;
		}
		return best;
	}

	/** The last block of the stall is in (HomeProjects.finish): the counter is found and the stall opens. */
	public static void onStallDone(ServerLevel level, HouseProject stall) {
		Shops shops = get(level.getServer());
		Shop shop = shops.shopNear(level.dimension(), stall.bell);
		if (shop == null) return;
		var box = stall.footprint(level);
		BlockPos kiosk = shop.counter;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (isCounter(level, pos)) {
				shop.counter = pos.immutable();
				break;
			}
		}
		// the kiosk by the bell is taken down once the stall stands
		if (kiosk != null && !kiosk.equals(shop.counter) && !box.isInside(kiosk) && isCounter(level, kiosk)) level.removeBlock(kiosk, false);
		boolean first = !shop.stocked;
		if (first) Market.openShop(level, shop, Family.day(level));
		shops.setDirty();
		Villager keeper = keeper(level, shop);
		if (keeper != null && first) Speech.queue(keeper, "Shopkeeper Opens the Stall", null, 2L);
	}

	private void vacateIfGone(ServerLevel level, Shop shop, long day) {
		UUID id = shop.keeper;
		if (id == null) return;
		boolean dead = FamilyDeaths.get(level.getServer()).contains(id);
		boolean unflagged = level.getEntity(id) instanceof Villager loaded && (!loaded.isAlive() || !isKeeper(loaded));
		boolean missing = level.getEntity(id) == null && day - shop.seenDay > TownRules.GONE_DAYS;
		if (dead || unflagged || missing) vacate(shop);
	}

	private void vacate(Shop shop) {
		shop.keeper = null;
		shop.name = "";
		setDirty();
	}

	/** The keeper gives up the stall alive (moved away, by command): the village chooses another at once. */
	public static void step(ServerLevel level, Villager keeper, long day) {
		if (keeper instanceof VillagerLifeData data) data.vnap$setShopkeeper(false);
		Shops shops = get(level.getServer());
		Shop shop = shops.shopOf(keeper.getUUID());
		if (shop == null) return;
		shops.vacate(shop);
		shops.choose(level, shop, day, keeper.getUUID());
	}

	/**
	 * The keeper died or left for a hamlet: the stall (its goods and till) passes to the spouse or the eldest grown
	 * child at once; with nobody of the family here a shopkeeper comes to the village. The stall is never left
	 * without a keeper while the village lives.
	 */
	public static void onGone(ServerLevel level, Villager keeper) {
		if (!isKeeper(keeper)) return;
		if (keeper instanceof VillagerLifeData data) data.vnap$setShopkeeper(false);
		Shops shops = get(level.getServer());
		Shop shop = shops.shopOf(keeper.getUUID());
		if (shop == null) return;
		shops.vacate(shop);
		long day = Family.day(level);
		Villager heir = heir(level, shop, keeper);
		if (heir != null) {
			shops.take(level, shop, heir, day);
			Speech.queue(heir, "Shopkeeper Takes the Stall", null, 6L);
		} else if (shop.lastElection != day) {
			// a newcomer at most once a day: killing keepers does not breed villagers (the next day's look brings one)
			shops.choose(level, shop, day, keeper.getUUID());
		}
	}

	// ---- save ------------------------------------------------------------------------------

	private static Shops load(CompoundTag tag, HolderLookup.Provider registries) {
		Shops shops = new Shops();
		for (Tag entry : Nbt.getList(tag, "Shops", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag) entry;
			ResourceLocation dimension = ResourceLocation.tryParse(Nbt.getString(one, "Dim"));
			if (dimension == null) continue;
			Shop shop = new Shop(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(Nbt.getLong(one, "Bell")));
			shop.keeper = Nbt.hasUUID(one, "Keeper") ? Nbt.getUUID(one, "Keeper") : null;
			shop.name = Nbt.getString(one, "Name");
			shop.electedDay = Nbt.getLong(one, "Elected");
			shop.seenDay = Nbt.getLong(one, "Seen");
			shop.lastElection = one.contains("LastElection") ? Nbt.getLong(one, "LastElection") : Long.MIN_VALUE;
			shop.counter = one.contains("Counter") ? BlockPos.of(Nbt.getLong(one, "Counter")) : null;
			shop.till = Nbt.getInt(one, "Till");
			shop.stocked = Nbt.getBoolean(one, "Stocked");
			shop.caravanDay = Nbt.getLong(one, "Caravan");
			shop.fairDay = one.contains("Fair") ? Nbt.getLong(one, "Fair") : Long.MIN_VALUE / 2;
			shop.noRoomDay = Nbt.getLong(one, "NoRoom");
			shop.noRoomSince = one.contains("NoRoomSince") ? Nbt.getLong(one, "NoRoomSince") : Long.MIN_VALUE / 2;
			shop.restockDay = Nbt.getLong(one, "Restock");
			CompoundTag orders = Nbt.getCompound(one, "Orders");
			for (String key : com.vnap.Mc.keys(orders)) {
				var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(key));
				if (!item.equals(net.minecraft.world.item.Items.AIR)) shop.orders.put(item, Nbt.getInt(orders, key));
			}
			for (Tag orderEntry : Nbt.getList(one, "PlayerOrders", Tag.TAG_COMPOUND)) {
				Orders.Order order = Orders.Order.load((CompoundTag) orderEntry, registries);
				if (order != null) shop.playerOrders.add(order);
			}
			for (Tag parcel : Nbt.getList(one, "Parcels", Tag.TAG_COMPOUND)) {
				CompoundTag p = (CompoundTag) parcel;
				if (Nbt.hasUUID(p, "Player")) shop.parcels.put(Nbt.getUUID(p, "Player"), BlockPos.of(Nbt.getLong(p, "Pos")));
			}
			for (Tag lotEntry : Nbt.getList(one, "Stock", Tag.TAG_COMPOUND)) {
				CompoundTag lot = (CompoundTag) lotEntry;
				ItemStack item = com.vnap.Mc.parseStackOrEmpty(registries, Nbt.getCompound(lot, "Item"));
				if (!item.isEmpty()) shop.stock.add(new Lot(item, Nbt.getInt(lot, "Count"), Nbt.getInt(lot, "Price"), Nbt.getBoolean(lot, "Player")));
			}
			shops.shops.add(shop);
		}
		return shops;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Shop shop : shops) {
			CompoundTag one = new CompoundTag();
			one.putString("Dim", com.vnap.Mc.id(shop.dimension).toString());
			one.putLong("Bell", shop.bell.asLong());
			if (shop.keeper != null) Nbt.putUUID(one, "Keeper", shop.keeper);
			one.putString("Name", shop.name);
			one.putLong("Elected", shop.electedDay);
			one.putLong("Seen", shop.seenDay);
			one.putLong("LastElection", shop.lastElection);
			if (shop.counter != null) one.putLong("Counter", shop.counter.asLong());
			one.putInt("Till", shop.till);
			one.putBoolean("Stocked", shop.stocked);
			one.putLong("Caravan", shop.caravanDay);
			one.putLong("Fair", shop.fairDay);
			one.putLong("NoRoom", shop.noRoomDay);
			one.putLong("NoRoomSince", shop.noRoomSince);
			one.putLong("Restock", shop.restockDay);
			CompoundTag orders = new CompoundTag();
			shop.orders.forEach((item, count) -> orders.putInt(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(), count));
			one.put("Orders", orders);
			ListTag playerOrders = new ListTag();
			for (Orders.Order order : shop.playerOrders) playerOrders.add(order.save(registries));
			one.put("PlayerOrders", playerOrders);
			ListTag parcels = new ListTag();
			shop.parcels.forEach((player, pos) -> {
				CompoundTag p = new CompoundTag();
				Nbt.putUUID(p, "Player", player);
				p.putLong("Pos", pos.asLong());
				parcels.add(p);
			});
			one.put("Parcels", parcels);
			ListTag stock = new ListTag();
			for (Lot lot : shop.stock) {
				CompoundTag one2 = new CompoundTag();
				one2.put("Item", com.vnap.Mc.saveStack(lot.item, registries));
				one2.putInt("Count", lot.count);
				one2.putInt("Price", lot.price);
				one2.putBoolean("Player", lot.fromPlayer);
				stock.add(one2);
			}
			one.put("Stock", stock);
			list.add(one);
		}
		tag.put("Shops", list);
		return tag;
	}
}
