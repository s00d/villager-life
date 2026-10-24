package com.vnap.village.market;

import com.vnap.Nbt;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.town.TownRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How a household comes by a home and pays for what it builds, in one place. A home is bought, as a player buys one:
 * a finished house of the villagers nobody lives in, a house of the world's village with a free bed, or a lot and the
 * house built on it. The household pays a tenth down out of its own coins, the treasury lends the rest up to
 * {@link TownRules#LOAN_CAP} (half of all it earns pays the loan back, {@link Market#earn}), and the town pays what is
 * over that: no family owes more than it can work off, and none saves for ever. Homeless {@link TownRules#HOMELESS_GRACE}
 * days without the down payment, a villager gets it from the mayor. A bed slept in without a deed (vanilla gives one
 * to anyone near) is lodging, not a home: the lodger still buys one. The villagers the town found when it began live in
 * the world's houses by right.
 */
public final class Housing {
	/** Persistent: a villager the town knew in its first days (it claims a house of the world for nothing, once). */
	public static final String FOUNDER = "vnap_founder";
	private static final String SETTLED = "vnap_founder_settled";

	/** What a household's money is spent on: a home and its upkeep the town makes sure of; growing only on credit it can carry. */
	public enum Purpose {
		HOME, UPKEEP, GROWTH
	}

	/** Who pays what of a price: the household's coins, the treasury's loan, the town's grant. */
	public record Split(int own, int loan, int town) {
		public int total() {
			return own + loan + town;
		}
	}

	/** A home for sale: its bed, the house of the villagers it is in (null for a house of the world), its price. */
	public record Offer(BlockPos bed, @Nullable HouseProject house, @Nullable BoundingBox box, int price) {
	}

	/** Why the last {@link #daily} bought nothing (debug: /vnap home). */
	public static String lastWhy = "";

	private Housing() {
	}

	// ---- money ------------------------------------------------------------------------------

	/** The part of a price paid down: a tenth, never less than {@link TownRules#DOWN_MIN}. */
	public static int down(int price) {
		return Math.min(price, Math.max(TownRules.DOWN_MIN, (int) Math.ceil(price * TownRules.DOWN_SHARE)));
	}

	/**
	 * Splits {@code price} for {@code owner}'s household: its coins (all but {@link TownRules#RESERVE}, all of them for
	 * upkeep), a loan while
	 * its debt stays under the cap, the town the rest. A home needs the down payment out of its own coins unless
	 * {@code helped} (homeless long enough: the treasury pays it); growing never takes the town's money, upkeep always
	 * may. Null when it cannot be paid for.
	 */
	public static @Nullable Split split(ServerLevel level, Villager owner, int price, Purpose purpose, boolean helped) {
		if (price <= 0) return new Split(0, 0, 0);
		int spare = Math.max(0, Market.funds(level, owner) - (purpose == Purpose.UPKEEP ? 0 : TownRules.RESERVE));
		int own = Math.min(price, spare);
		if (purpose == Purpose.HOME && own < down(price) && !helped) return null;
		int room = Math.max(0, TownRules.LOAN_CAP - Market.debt(level, owner));
		int loan = Math.min(price - own, room);
		int town = price - own - loan;
		if (purpose == Purpose.GROWTH && town > 0) return null;
		return new Split(own, loan, town);
	}

	/** The household's part of a split is taken: its coins to the treasury, the loan on its slate. */
	static void charge(ServerLevel level, Villager owner, BlockPos bell, Split split) {
		Market.charge(level, owner, split.own());
		Family.state(Market.purseOf(level, owner)).debt += split.loan();
		if (split.own() > 0) Market.deposit(level, Mayors.get(level.getServer()).officeFor(level.dimension(), bell), split.own());
	}

	/**
	 * Something a household must have and cannot pay for (food after waiting, a bed, a cradle, a gravestone): its coins,
	 * then a loan while it owes under the cap, the treasury the rest. The coins go where the caller sends the price (the
	 * till); the loan and the rest come out of the treasury.
	 */
	public static void need(ServerLevel level, Villager payer, Mayors.Office office, int price) {
		Split split = split(level, payer, price, Purpose.UPKEEP, true);
		if (split == null) return;
		Market.charge(level, payer, split.own());
		Family.state(Market.purseOf(level, payer)).debt += split.loan();
		if (split.loan() + split.town() > 0) Market.spendTreasury(level, office, split.loan() + split.town());
	}

	/**
	 * A household's build (its house, a storey, a room, mending, its workshop, its yard) is paid for: the treasury puts
	 * the bill (a tenth over: prices move, helpers are paid out of it) into the site and the household pays its split
	 * back into the treasury. False, and nothing paid, when the household cannot carry it ({@link Purpose#GROWTH}).
	 */
	public static boolean build(ServerLevel level, Villager owner, BlockPos bell, Site site, Map<Item, Integer> bill, Purpose purpose, int land) {
		int cost = (int) Math.ceil(Materials.cost(bill) * 1.1);
		Split split = split(level, owner, cost + land, purpose, true);
		if (split == null) return false;
		charge(level, owner, bell, split);
		if (cost > 0) {
			Market.spendTreasury(level, Mayors.get(level.getServer()).officeFor(level.dimension(), bell), cost);
			site.funds += cost;
			site.wages += cost - Materials.cost(bill);
		}
		return true;
	}

	// ---- prices -----------------------------------------------------------------------------

	/** A house of the villagers, sold: its materials at {@link TownRules#RESALE}. */
	public static int price(ServerLevel level, HouseProject house) {
		return Math.max(1, (int) Math.ceil(Materials.cost(Materials.bill(house.schematic(level))) * TownRules.RESALE));
	}

	/** A house of the world's village, sold: what it is built of at {@link TownRules#RESALE}. */
	public static int price(ServerLevel level, BoundingBox box) {
		Map<Item, Integer> bill = new HashMap<>();
		for (BlockPos cell : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(cell);
			if (!state.isAir()) Materials.items(state).forEach((item, count) -> bill.merge(item, count, Integer::sum));
		}
		return Math.max(1, (int) Math.ceil(Materials.cost(bill) * TownRules.RESALE));
	}

	// ---- founders ---------------------------------------------------------------------------

	/** Marks a villager the town sees in its first {@link TownRules#FOUNDING_DAYS} days (called once, when it is first registered). */
	public static void firstSeen(Villager villager, long founded, long day) {
		if (day - founded <= TownRules.FOUNDING_DAYS) villager.getPersistentData().putBoolean(FOUNDER, true);
	}

	/** A founder whose right to a house for nothing is not spent yet. */
	public static boolean founder(Villager villager) {
		return foundedIt(villager) && !Nbt.getBoolean(villager.getPersistentData(), SETTLED);
	}

	/** Was there when the town was founded (its house taken or not). */
	public static boolean foundedIt(Villager villager) {
		return Nbt.getBoolean(villager.getPersistentData(), FOUNDER);
	}

	/** A founder took its house: the right is spent. */
	public static void settled(Villager villager) {
		villager.getPersistentData().putBoolean(SETTLED, true);
	}

	// ---- buying a home ----------------------------------------------------------------------

	/** Whether this villager buys its own home: a grown-up of a town, not a member of a household, nor one housed by its post. */
	public static boolean buyer(Villager villager) {
		return !villager.isBaby() && !Family.member(villager) && !Mayors.isMayor(villager) && !Shops.isKeeper(villager)
				&& !com.vnap.village.home.Hamlets.leaving(villager) && Towns.bellOf(villager) != null;
	}

	/** Homes for sale round the bell, cheapest first (nearest of equal price). */
	public static List<Offer> offers(ServerLevel level, Villager buyer, BlockPos bell) {
		List<Offer> found = new ArrayList<>();
		for (HouseProject house : HomeProjects.emptyHouses(level, bell)) {
			BlockPos bed = house.bed(level);
			if (bed != null) found.add(new Offer(bed, house, null, price(level, house)));
		}
		List<BoundingBox> seen = new ArrayList<>();
		level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), bell, (int) Towns.reach(level, bell), PoiManager.Occupancy.ANY)
				.map(net.minecraft.world.entity.ai.village.poi.PoiRecord::getPos)
				.forEach(bed -> {
					if (!level.isLoaded(bed) || !VillagerHome.claimable(level, bed) || !Towns.ours(level, bell, bed)) return;
					BoundingBox box = HomeProjects.piece(level, bed);
					if (box == null || seen.contains(box) || !forSale(level, box, buyer)) return;
					seen.add(box);
					found.add(new Offer(bed.immutable(), null, box, price(level, box)));
				});
		found.sort(Comparator.<Offer>comparingInt(Offer::price).thenComparingDouble(offer -> offer.bed().distSqr(buyer.blockPosition())));
		return found;
	}

	/** A house of the world no household owns: no home sign, no villager's own bed in it, nobody building on it, no player's. */
	private static boolean forSale(ServerLevel level, BoundingBox box, Villager buyer) {
		if (com.vnap.village.town.Roads.bought(level, box.getCenter())) return false;
		if (VillageMoodSavedData.get(level).houses().stream().anyMatch(house -> house.footprint(level).intersects(box))) return false;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign
					&& VillagerHome.isMarker(com.vnap.Mc.signLine(sign, 1))) return false;
		}
		return level.getEntitiesOfClass(Villager.class, AABB.of(box).inflate(TownRules.NEAR_BELL), villager -> !villager.equals(buyer)
				&& villager instanceof VillagerLifeData data && data.vnap$homeBed() != null && box.isInside(data.vnap$homeBed())).isEmpty();
	}

	/**
	 * Once a day for a homeless grown-up: the cheapest home for sale it can pay down on is bought; with none for sale a
	 * house of its own is begun on a lot (HomeProjects.startHome). Saving meanwhile it lodges where it can.
	 */
	public static void daily(ServerLevel level, Villager villager, BlockPos bell, long day, long homelessSince) {
		boolean helped = day - homelessSince >= TownRules.HOMELESS_GRACE;
		List<Offer> offers = offers(level, villager, bell);
		for (Offer offer : offers) {
			Split split = split(level, villager, offer.price(), Purpose.HOME, helped);
			if (split == null) {
				lastWhy = "saving " + Market.funds(level, villager) + "/" + (down(offer.price()) + TownRules.RESERVE) + " for a house at "
						+ offer.bed().toShortString() + " (" + offer.price() + "c)";
				return;
			}
			if (buy(level, villager, bell, offer, split)) return;
		}
		String blocker = HomeProjects.blocker(level, villager, day);
		if (blocker != null) {
			lastWhy = offers.isEmpty() ? "nothing for sale; " + blocker : "could not move in; " + blocker;
			return;
		}
		HouseProject house = HomeProjects.startHome(level, villager, day, helped);
		lastWhy = house != null ? "building on " + house.origin.toShortString() : HomeProjects.lastHomeRefusal;
	}

	/** The household pays and moves in; false (nothing paid) when the house would not take it. */
	static boolean buy(ServerLevel level, Villager buyer, BlockPos bell, Offer offer, Split split) {
		if (offer.house() != null) {
			if (!HomeProjects.moveIn(level, buyer, offer.house())) return false;
			offer.house().owner = buyer.getUUID();
			VillageMoodSavedData.get(level).markDirty();
		} else {
			if (offer.box() != null) evictLodgers(level, offer.box(), buyer);
			if (!VillagerHome.claim(level, buyer, offer.bed())) return false;
		}
		charge(level, buyer, bell, split);
		buyer.getPersistentData().remove(HomeProjects.HOMELESS_SINCE);
		com.vnap.VillagerLife.trace("vnap housing: {} bought {} for {}c ({} own, {} loan, {} town)", VillagerHome.nameOf(buyer),
				offer.bed().toShortString(), split.total(), split.own(), split.loan(), split.town());
		com.vnap.village.town.Chronicle.note(level, bell, "house_bought", VillagerHome.nameOf(buyer));
		return true;
	}

	/** Lodgers in a house sold to another household sleep elsewhere (vanilla finds them a bed, the town a shelter). */
	private static void evictLodgers(ServerLevel level, BoundingBox box, Villager buyer) {
		for (Villager other : level.getEntitiesOfClass(Villager.class, AABB.of(box).inflate(TownRules.NEAR_BELL), v -> !v.equals(buyer)
				&& !buyer.getUUID().equals(Family.state(v).head))) {
			GlobalPos home = other.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
			if (home == null || !home.dimension().equals(level.dimension()) || !box.isInside(home.pos())) continue;
			other.getBrain().eraseMemory(MemoryModuleType.HOME);
			level.getPoiManager().release(home.pos());
		}
	}

	/** Debug (/vnap home): what the household has, owes and is after. */
	public static String describe(ServerLevel level, Villager villager) {
		FamilyState purse = Family.state(Market.purseOf(level, villager));
		return "coins " + Market.funds(level, villager) + ", owes " + purse.debt + "/" + TownRules.LOAN_CAP
				+ (founder(villager) ? ", founder" : "") + (lastWhy.isEmpty() ? "" : ", " + lastWhy);
	}
}
