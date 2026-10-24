package com.vnap.village.market;

import com.vnap.village.town.TownRules;
import com.vnap.block.DecorBlock;
import com.vnap.block.LifeBlocks;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.entity.VillagerLifeData;
import com.vnap.item.LifeItems;
import com.vnap.village.home.Mayors;
import com.vnap.village.life.Pantry;
import com.vnap.village.workshop.WorkshopState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The village's money (copper; see {@link Coins}) and what it buys, once the village has a stall open:
 * <ul>
 * <li>The mayor's treasury pays every worker a wage after work (a trade and its workshop's tier; the mayor and the
 * keeper are paid too), into the household's purse (on its head). When the treasury runs short the village sells
 * its produce to the caravan ({@code minted}): wages are always paid.</li>
 * <li>A family buys what it puts up in its house (beds, decor, the kennel, candles) and the food its pantry lacks:
 * {@link #obtain} says "buy it first", the villager walks to the counter ({@link #shop}) and puts the thing up once
 * it has it. Food and beds a poor family cannot afford for two days the mayor pays for; anything the stall could not
 * offer for four days the family does without the stall, the old way, so nothing waits for ever.</li>
 * <li>The stall keeps a catalogue at its targets; a caravan comes every week and fills it up for half the price,
 * paid from the till. The till is kept between a floor and a cap with the treasury (takings above the cap go to the
 * village as tax).</li>
 * </ul>
 * Mood does not change with any of it: only the player's prices follow the mood (ShopMenu).
 */
public final class Market {
	public static final String FOOD = "#food";
	/** The caravan sells to the stall at this share of the worth. */
	public static final double WHOLESALE = 0.5;
	/** Wages left uncollected are paid quietly from this time of day (the payday at the bell is earlier). */
	public static final float SETTLE = 0.42F;

	private static @Nullable Map<Item, Integer> catalogue;

	private Market() {
	}

	// ---- the catalogue ---------------------------------------------------------------------

	/** What the stall keeps in stock and how many of each (the caravan fills up to these). */
	public static Map<Item, Integer> catalogue() {
		Map<Item, Integer> built = catalogue;
		if (built != null) return built;
		Map<Item, Integer> out = new LinkedHashMap<>();
		out.put(Items.BREAD, 32);
		out.put(Items.BAKED_POTATO, 16);
		out.put(Items.APPLE, 12);
		out.put(Items.COOKED_COD, 8);
		out.put(Items.COOKED_BEEF, 8);
		out.put(Items.CAKE, 3);
		out.put(Items.WHITE_BED, 4);
		// the town's crossbows' arrows (TurretBehavior buys them for the towers)
		out.put(Items.ARROW, 64);
		out.put(Items.CANDLE, 12);
		out.put(Items.LANTERN, 6);
		out.put(Items.FLOWER_POT, 6);
		out.put(Items.PAINTING, 3);
		for (String name : List.of("hearth", "pantry_crate", "cooking_pot", "cradle", "vanity_table", "flower_vase", "standing_mirror",
				"family_portrait", "aquarium", "clothesline", "dog_kennel", "pet_bowl", "cat_bed", "parrot_perch", "gravestone")) {
			Item item = decor(name);
			if (!item.equals(Items.AIR)) out.put(item, name.equals("pet_bowl") || name.equals("flower_vase") ? 3 : 2);
		}
		out.put(LifeItems.PET_TREAT, 8);
		// workshops' hand carts (Chores.sell)
		out.put(LifeItems.HAND_CART, 2);
		out.put(LifeItems.WOODEN_TOY, 3);
		out.put(LifeItems.BABY_BLANKET, 3);
		out.put(LifeItems.WEDDING_RING, 1);
		out.put(Items.BONE_MEAL, 16);
		// the farmers' beehives and their honey bottles (Chores.hive, honey)
		out.put(Items.BEEHIVE, 2);
		out.put(Items.GLASS_BOTTLE, 16);
		// the town's notice board (the mayor sets one up), and its records (its gatherings play them)
		out.put(com.vnap.block.LifeBlocks.NOTICE_BOARD.asItem(), 1);
		// the nameplate a family hangs by its door (VillagerHome)
		out.put(com.vnap.block.LifeBlocks.HOUSE_SIGN.asItem(), 4);
		// torches for the households' dark rooms (Lighting)
		out.put(Items.TORCH, 16);
		// the town's iron golems (Guards)
		out.put(Items.IRON_BLOCK, 4);
		out.put(Items.CARVED_PUMPKIN, 1);
		// the Builders' Writ, for a player who will not wait for the town to call its spire (SpireWritItem)
		out.put(LifeItems.SPIRE_WRIT, 1);
		for (Item disc : List.of(LifeItems.DISC_FEAST, LifeItems.DISC_TAVERN, LifeItems.DISC_HYMN, LifeItems.DISC_EVENING)) out.put(disc, 2);
		for (Item sapling : List.of(Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING, Items.JUNGLE_SAPLING,
				Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING)) out.put(sapling, 8);
		building().forEach((item, count) -> out.merge(item, count, Math::max));
		catalogue = out;
		return out;
	}

	/**
	 * Building materials: every block of every plan the villagers build (houses, halls, the stall, the farm, rooms,
	 * workshops, the pen, rites) in every biome palette, as many of each as the biggest single plan takes, so any one
	 * build can be served at once. Wooden parts are kept as their logs (the stall makes the parts, {@link #saw}).
	 */
	static Map<Item, Integer> building() {
		List<String> names = new ArrayList<>();
		List<String> houses = new ArrayList<>(com.vnap.village.home.HomeProjects.PLANS);
		houses.addAll(Mayors.HALLS);
		houses.addAll(Shops.STALLS);
		for (String plan : com.vnap.village.home.HomeProjects.PLANS) {
			houses.add("room_" + plan);
			// the house's next levels (a storey's beds, a cellar): HouseLevels
			for (int level = 2; level <= 3; level++) houses.add(plan + "_l" + level);
		}
		List<String> world = new ArrayList<>();
		for (String house : com.vnap.village.home.HouseLevels.WORLD_HOUSES) {
			for (String part : new String[]{"", "_l2", "_l3"}) world.add("house/" + com.vnap.village.home.HouseLevels.WORLD + house + part);
		}
		for (String hall : Mayors.HALLS) {
			houses.add(hall + "_" + Mayors.OFFICE);
			houses.add(hall + "_" + Mayors.HOME);
		}
		for (String profession : com.vnap.village.workshop.Workshops.PROFESSIONS) {
			for (int tier = 1; tier <= 3; tier++) names.add(com.vnap.village.workshop.Workshops.schematicName(profession, tier));
		}
		for (com.vnap.mood.ScriptKind kind : com.vnap.mood.ScriptKind.values()) names.add(com.vnap.mood.VillageRites.schematicName(kind));
		List<com.vnap.village.script.SchematicSpec> specs = new ArrayList<>();
		for (String name : names) specs.add(com.vnap.village.script.SchematicSpec.loadBundled(name));
		// a small house of the world grown its own way (its blocks are its own, no palette)
		for (String name : world) specs.add(com.vnap.village.script.SchematicSpec.loadBundled(name));
		// the town's own buildings (village/town/TownPlots: chapel, bakehouse, tavern), in every palette
		specs.addAll(com.vnap.village.town.TownPlots.kit());
		for (String plan : houses) {
			com.vnap.village.script.SchematicSpec spec = com.vnap.village.script.SchematicSpec.loadBundled("house/" + plan);
			if (spec.blocks.isEmpty()) continue;
			for (String palette : com.vnap.village.home.HousePalette.ALL) specs.add(com.vnap.village.home.HousePalette.apply(spec, palette));
			// the second age's (HouseLevels.restyle)
			for (String palette : com.vnap.village.home.HousePalette.FINES) specs.add(com.vnap.village.home.HousePalette.apply(spec, palette));
		}
		specs.addAll(com.vnap.village.town.Roads.kit());
		specs.addAll(com.vnap.village.town.Roads.plazaKit());
		// the trades' fronts: signs, door bells, the stall's crates (Signage)
		specs.addAll(com.vnap.village.town.Signage.kit());
		// the nooks between the houses: benches, lamps, bushes, saplings (Greenery)
		specs.addAll(com.vnap.village.town.Greenery.kit());
		// a household's comforts: porch, garden, shed, glasshouse, trees, rooms (Comfort)
		specs.addAll(com.vnap.village.town.Comfort.kit());
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (com.vnap.village.script.SchematicSpec spec : specs) {
			Map<Item, Double> one = new LinkedHashMap<>();
			Materials.bill(spec).forEach((item, count) -> {
				Materials.Wood wood = Materials.wood(item);
				if (wood != null) one.merge(wood.log(), count * wood.logs(), Double::sum);
				else one.merge(item, (double) count, Double::sum);
			});
			one.forEach((item, count) -> out.merge(item, (int) Math.ceil(count), Math::max));
		}
		// several builds go up at once in a town (the hall, the stall, homes, roads): its stall keeps enough for them
		out.replaceAll((item, count) -> count * TownRules.BUILD_STOCK);
		return out;
	}

	public static int target(Item item) {
		return catalogue().getOrDefault(item, 0);
	}

	public static Item decor(String name) {
		DecorBlock block = decorBlock(name);
		return block == null ? Items.AIR : block.asItem();
	}

	/** The stall opens: full shelves and a till to start with. */
	public static void openShop(ServerLevel level, Shops.Shop shop, long day) {
		for (Map.Entry<Item, Integer> entry : catalogue().entrySet()) {
			int missing = entry.getValue() - shop.count(entry.getKey());
			if (missing > 0) shop.put(new ItemStack(entry.getKey()), missing, false);
		}
		shop.till = Math.max(shop.till, TownRules.TILL_START);
		shop.stocked = true;
		shop.caravanDay = day;
		Shops.get(level.getServer()).setDirty();
	}

	/** The stall's day: the weekly caravan, and the till kept between its floor and cap with the treasury. */
	public static void dailyShop(ServerLevel level, Shops.Shop shop, long day) {
		if (!shop.stocked || shop.restockDay == day) return;
		shop.restockDay = day;
		if (day - shop.caravanDay >= TownRules.CARAVAN_DAYS) Caravan.arrive(level, shop, day);
		else restock(level, shop);
		balance(level, shop);
	}

	/**
	 * The morning delivery between caravans: what buyers asked for and the stall had not (its orders) comes in full,
	 * and every catalogue good below its target gets a quarter of it back, so a stall is never empty for long. Paid at
	 * the wholesale price out of the till (the treasury adds what it lacks).
	 */
	public static int restock(ServerLevel level, Shops.Shop shop) {
		int cost = 0;
		Map<Item, Integer> deliver = new LinkedHashMap<>(shop.orders);
		shop.orders.clear();
		boolean bakes = com.vnap.village.town.Bakehouse.bakes(level, shop);
		if (bakes) deliver.remove(Items.BREAD);
		for (Map.Entry<Item, Integer> entry : catalogue().entrySet()) {
			// a town with its bakehouse bakes its own bread
			if (bakes && entry.getKey().equals(Items.BREAD)) continue;
			int missing = entry.getValue() - shop.count(entry.getKey()) - deliver.getOrDefault(entry.getKey(), 0);
			if (missing > 0) deliver.merge(entry.getKey(), Math.min(missing, Math.max(4, (entry.getValue() + 3) / 4)), Integer::sum);
		}
		for (Map.Entry<Item, Integer> entry : deliver.entrySet()) {
			ItemStack item = new ItemStack(entry.getKey());
			// a town linked by Waygates sells its surplus first (mega/TownWork): only the rest comes from abroad
			int rest = entry.getValue() - com.vnap.mega.TownWork.fromLinked(level, shop, entry.getKey(), entry.getValue());
			if (rest <= 0) continue;
			cost += (int) Math.ceil(Prices.price(item) * WHOLESALE * rest);
			shop.put(item, rest, false);
		}
		pay(level, shop, cost);
		return cost;
	}

	/** The stall pays someone (a player's delivery to the notice board) out of its till; the treasury adds what the till lacks. */
	public static void payOut(ServerLevel level, Shops.Shop shop, int cost) {
		pay(level, shop, cost);
	}

	/** The stall pays a delivery out of its till; the treasury adds what the till lacks. */
	static void pay(ServerLevel level, Shops.Shop shop, int cost) {
		int fromTill = Math.min(cost, Math.max(0, shop.till));
		shop.till -= fromTill;
		if (cost > fromTill) spendTreasury(level, office(level, shop), cost - fromTill);
		Shops.get(level.getServer()).setDirty();
	}

	public static void balance(ServerLevel level, Shops.Shop shop) {
		Mayors.Office office = office(level, shop);
		if (shop.till > TownRules.TILL_CAP) {
			deposit(office, shop.till - TownRules.TILL_CAP);
			shop.till = TownRules.TILL_CAP;
		} else if (shop.till < TownRules.TILL_FLOOR) {
			spendTreasury(level, office, TownRules.TILL_FLOOR - shop.till);
			shop.till = TownRules.TILL_FLOOR;
		}
		Shops.get(level.getServer()).setDirty();
	}

	// ---- the treasury ----------------------------------------------------------------------

	public static Mayors.Office office(ServerLevel level, Shops.Shop shop) {
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(shop.dimension, shop.bell);
		if (office.treasury < 0) office.treasury = TownRules.TREASURY_START;
		return office;
	}

	/** Pays out of the treasury; what it lacks the village makes up by selling its produce, so it always pays. */
	private static final int MINT_LOGGED = 200;

	public static void spendTreasury(ServerLevel level, Mayors.Office office, int amount) {
		if (office.treasury < 0) office.treasury = TownRules.TREASURY_START;
		if (office.treasury < amount) {
			int made = amount - office.treasury;
			office.minted += made;
			office.treasury = amount;
			// the economy is closed but for this: a big make-up is logged with what asked for it
			if (made >= MINT_LOGGED) com.vnap.VillagerLife.trace("vnap mint: {}c at {} for {}", made, office.bell.toShortString(),
					StackWalker.getInstance().walk(frames -> frames.skip(1).limit(3)
							.map(frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1) + "." + frame.getMethodName())
							.collect(java.util.stream.Collectors.joining(" < "))));
		}
		office.treasury -= amount;
		Mayors.get(level.getServer()).setDirty();
	}

	public static void deposit(ServerLevel level, Mayors.Office office, int amount) {
		deposit(office, amount);
		Mayors.get(level.getServer()).setDirty();
	}

	static void deposit(Mayors.Office office, int amount) {
		office.treasury = Math.max(0, office.treasury) + amount;
	}

	// ---- building --------------------------------------------------------------------------

	/** The treasury grants a new build what its bill costs (a little over: prices move), into the site's money. */
	public static void grant(ServerLevel level, @Nullable BlockPos bell, Site site, Map<Item, Integer> bill) {
		int cost = (int) Math.ceil(Materials.cost(bill) * 1.1);
		if (cost <= 0 || bell == null) return;
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		spendTreasury(level, office, cost);
		site.funds += cost;
		site.wages += cost - Materials.cost(bill);
	}

	/**
	 * A household's own build (its house, a storey, a room, mending, its workshop, its yard), paid by the rules of
	 * {@link Housing#build}: its coins, a loan up to the cap, the town the rest of a home or of upkeep. Without the owner
	 * loaded the treasury grants it. False, and nothing paid, when the household cannot carry a {@link Housing.Purpose#GROWTH}.
	 */
	public static boolean finance(ServerLevel level, @Nullable Villager owner, @Nullable BlockPos bell, Site site, Map<Item, Integer> bill,
								  Housing.Purpose purpose) {
		// out of any town there is no treasury and no stall: the build goes on with what is at hand
		if (bell == null) return true;
		if (owner == null) {
			grant(level, bell, site, bill);
			return true;
		}
		return Housing.build(level, owner, bell, site, bill, purpose, 0);
	}

	/** What the household still owes the treasury. */
	public static int debt(ServerLevel level, Villager villager) {
		return Family.state(purseOf(level, villager)).debt;
	}

	/** Owing what the treasury lends at most, a household borrows no more: no storey, room or better workshop until it paid some back. */
	public static boolean deepInDebt(ServerLevel level, Villager villager) {
		return debt(level, villager) >= TownRules.LOAN_CAP;
	}


	/**
	 * A helper set a block on someone else's build: a copper out of the build's wages (the tenth its owner put in over the
	 * materials), up to a day's labour; when they are spent the neighbours help for nothing and the materials stay paid for.
	 */
	public static void dayLabour(ServerLevel level, Villager helper, Site site) {
		FamilyState family = Family.state(helper);
		long day = Family.day(level);
		if (family.labourDay != day) {
			family.labourDay = day;
			family.labourToday = 0;
		}
		if (site.funds <= 0 || site.wages <= 0 || family.labourToday >= TownRules.DAY_LABOUR) return;
		int paid = earn(level, helper, 1);
		site.funds -= paid;
		site.wages -= paid;
		family.labourToday += paid;
	}

	/**
	 * A block taken down by hand for a new build (a house of the world grown, an old roof, a hall's ground): its materials
	 * go to the site's store, and what the new build sets of the same kind comes out of there before anything is bought.
	 */
	public static void salvage(Site site, net.minecraft.world.level.block.state.BlockState state) {
		Materials.items(state).forEach(site::add);
	}

	/**
	 * A finished build: what is left of the salvage in its store is sold to the stall (wholesale), the money it did not
	 * need pays back its loan, the rest goes to its owner's household (or back to the treasury).
	 */
	public static void settle(ServerLevel level, Site site, @Nullable Villager owner, @Nullable BlockPos bell) {
		Shops.Shop shop = bell == null || site.stock.isEmpty() ? null : Shops.near(level, bell);
		if (shop != null) {
			int sold = 0;
			for (Map.Entry<Item, Integer> entry : site.stock.entrySet()) {
				net.minecraft.world.item.ItemStack one = new net.minecraft.world.item.ItemStack(entry.getKey());
				// the stall buys what it has shelf room for, as from a worker (with no cap a big old house taken down
				// was hundreds of blocks paid for by the till, the till by the treasury minting what it lacked);
				// the rest is the builders' scrap
				int n = Math.min(entry.getValue(), Math.max(0, Produce.room(shop, entry.getKey())));
				int worth = (int) Math.floor(Prices.price(one) * WHOLESALE * n);
				if (worth <= 0) continue;
				shop.put(one, n, false);
				sold += worth;
			}
			site.stock.clear();
			if (sold > 0) {
				pay(level, shop, sold);
				site.funds += sold;
			}
		}
		int left = site.funds;
		site.funds = 0;
		site.wages = 0;
		if (left <= 0) return;
		if (owner != null && bell != null) {
			FamilyState purse = Family.state(purseOf(level, owner));
			int repay = Math.min(purse.debt, left);
			purse.debt -= repay;
			left -= repay;
			if (repay > 0) deposit(Mayors.get(level.getServer()).officeFor(level.dimension(), bell), repay);
			if (left <= 0) return;
		}
		int kept = owner == null ? 0 : earn(level, owner, left);
		if (left > kept && bell != null) deposit(Mayors.get(level.getServer()).officeFor(level.dimension(), bell), left - kept);
	}

	/**
	 * A builder at the counter buys what the bill still lacks on its site, out of the site's money (the treasury
	 * makes up what it lacks: a build is never left half done for want of coins). Wooden parts the stall lacks it
	 * makes from its logs; anything sold out is ordered for the next morning. True when anything was bought.
	 */
	public static boolean supply(ServerLevel level, Villager buyer, Shops.Shop shop, Site site, Map<Item, Integer> bill) {
		boolean bought = false;
		for (Map.Entry<Item, Integer> entry : site.lacking(bill).entrySet()) {
			Item item = entry.getKey();
			int want = entry.getValue();
			if (shop.count(item) < want) saw(shop, item, want - shop.count(item));
			for (Shops.Lot lot : new ArrayList<>(shop.stock)) {
				if (want <= 0) break;
				if (!lot.item.is(item) || lot.count <= 0) continue;
				int n = Math.min(want, lot.count);
				int price = lot.price * n;
				if (site.funds < price) {
					spendTreasury(level, office(level, shop), price - site.funds);
					site.funds = price;
				}
				site.funds -= price;
				shop.till += price;
				shop.take(lot, n);
				site.add(item, n);
				want -= n;
				bought = true;
			}
			// sold out: the caravan's drover brings the rest at once at the catalogue price, as for an event (Market.order),
			// up to TownRules.ABROAD_PER_DAY a day (the rest waits for the morning's delivery: a town is not built out of
			// nothing); a build that waited a day stood a whole day for one block of cobblestone
			int abroad = Math.min(want, shop.abroadLeft(Family.day(level)));
			if (abroad < want) shop.orders.merge(item, want - abroad, Integer::max);
			want = abroad;
			if (want > 0) {
				shop.abroadTaken += want;
				int price = Prices.price(new ItemStack(item)) * want;
				if (site.funds < price) {
					spendTreasury(level, office(level, shop), price - site.funds);
					site.funds = price;
				}
				site.funds -= price;
				site.add(item, want);
				bought = true;
			}
		}
		Shops.get(level.getServer()).setDirty();
		if (bought && Family.once(buyer, "materials", Family.day(level))) {
			Speech.queue(buyer, "Villager Buys Building Materials", Shops.keeper(level, shop), 1L);
		}
		return bought;
	}

	/**
	 * The village buys a whole bill at once, out of the treasury: what the stall has comes off its shelves (into its
	 * till), the rest the caravan brings straight to the site. For what is set down in one go rather than block by
	 * block: a rite, the hall's furnishing, a hamlet's first house. With a site the goods land there.
	 */
	public static void kit(ServerLevel level, BlockPos bell, @Nullable Site site, Map<Item, Integer> bill) {
		Shops.Shop shop = Shops.near(level, bell);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Map<Item, Integer> lacking = site == null ? bill : site.lacking(bill);
		for (Map.Entry<Item, Integer> entry : lacking.entrySet()) {
			int want = entry.getValue();
			int price = Prices.price(new ItemStack(entry.getKey()));
			if (shop != null) {
				for (Shops.Lot lot : new ArrayList<>(shop.stock)) {
					if (want <= 0) break;
					if (!lot.item.is(entry.getKey()) || lot.count <= 0) continue;
					int n = Math.min(want, lot.count);
					shop.take(lot, n);
					shop.till += lot.price * n;
					want -= n;
				}
			}
			spendTreasury(level, office, price * entry.getValue());
			if (site != null) site.add(entry.getKey(), entry.getValue());
		}
		if (shop != null) Shops.get(level.getServer()).setDirty();
	}

	/** The stall makes {@code count} wooden parts from its logs of that tree (as many as its logs allow). */
	static void saw(Shops.Shop shop, Item part, int count) {
		Materials.Wood wood = Materials.wood(part);
		if (wood == null || wood.log().equals(part)) return;
		int logs = (int) Math.min(Math.ceil(count * wood.logs()), shop.count(wood.log()));
		if (logs <= 0) return;
		int made = Math.min(count, (int) Math.floor(logs / wood.logs()));
		if (made <= 0) return;
		int used = (int) Math.ceil(made * wood.logs());
		for (Shops.Lot lot : new ArrayList<>(shop.stock)) {
			if (used <= 0) break;
			if (!lot.item.is(wood.log())) continue;
			int n = Math.min(used, lot.count);
			shop.take(lot, n);
			used -= n;
		}
		shop.put(new ItemStack(part), made, false);
	}

	// ---- purses ----------------------------------------------------------------------------

	/** The household head the money goes to (the villager itself without one loaded). */
	static Villager purseOf(ServerLevel level, Villager villager) {
		FamilyState family = Family.state(villager);
		return family.head != null && !family.head.equals(villager.getUUID()) && level.getEntity(family.head) instanceof Villager head
				&& head.isAlive() ? head : villager;
	}

	/** What this villager can spend: its own coins and its household's. */
	public static int funds(ServerLevel level, Villager villager) {
		Villager purse = purseOf(level, villager);
		return Family.state(villager).coins + (purse.equals(villager) ? 0 : Family.state(purse).coins);
	}

	/** Takes {@code amount} from the villager's coins, then its household's; nothing when they have less. */
	public static boolean charge(ServerLevel level, Villager villager, int amount) {
		if (funds(level, villager) < amount) return false;
		FamilyState own = Family.state(villager);
		int fromOwn = Math.min(own.coins, amount);
		own.coins -= fromOwn;
		if (amount > fromOwn) Family.state(purseOf(level, villager)).coins -= amount - fromOwn;
		return true;
	}

	/** Money into the household's purse, up to its cap (half of it to the treasury while it owes); returns what it took. */
	public static int earn(ServerLevel level, Villager villager, int amount) {
		FamilyState purse = Family.state(purseOf(level, villager));
		// half of it, rounded up, goes back while it owes (with no village to pay it to, it keeps owing)
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
		int repay = bell == null ? 0 : Math.max(0, Math.min(purse.debt, (amount + 1) / 2));
		if (repay > 0 && bell != null) {
			purse.debt -= repay;
			deposit(Mayors.get(level.getServer()).officeFor(level.dimension(), bell), repay);
		}
		int paid = Math.max(0, Math.min(amount - repay, TownRules.PURSE_CAP - purse.coins));
		purse.coins += paid;
		return repay + paid;
	}

	/** The mayor's gift to a household on a birth: from the treasury into its purse (in a village with a stall). */
	public static void birthGift(ServerLevel level, Villager parent, int amount) {
		Shops.Shop shop = Shops.of(level, parent);
		if (shop == null) return;
		int paid = earn(level, parent, amount);
		if (paid > 0) spendTreasury(level, office(level, shop), paid);
	}

	/**
	 * Something the stall orders from outside the village (livestock from the caravan's drover): the household pays
	 * what it has, the treasury the rest, and the money leaves the village.
	 */
	public static void buyAbroad(ServerLevel level, Villager buyer, Shops.Shop shop, int cost) {
		int own = Math.min(cost, funds(level, buyer));
		charge(level, buyer, own);
		if (cost > own) spendTreasury(level, office(level, shop), cost - own);
	}

	// ---- wages -----------------------------------------------------------------------------

	/**
	 * A villager died: its coins and what its household owes go to {@code heir} (the widow), else the coins to the
	 * treasury and the debt is written off (the village lent it, the village loses it).
	 */
	public static void bequeath(ServerLevel level, Villager late, @Nullable Villager heir) {
		FamilyState own = Family.state(late);
		if (heir != null) {
			FamilyState to = Family.state(heir);
			to.coins = Math.min(TownRules.PURSE_CAP, to.coins + own.coins);
			to.debt += own.debt;
		} else if (own.coins > 0) {
			BlockPos bell = com.vnap.village.home.Towns.bellOf(late);
			if (bell != null) deposit(Mayors.get(level.getServer()).officeFor(level.dimension(), bell), own.coins);
		}
		own.coins = 0;
		own.debt = 0;
	}

	/** A town job's pay (winding the chapel's clock): out of the treasury into the worker's household. */
	public static void townWage(ServerLevel level, Villager worker, BlockPos bell, int amount) {
		int paid = earn(level, worker, amount);
		if (paid > 0) spendTreasury(level, Mayors.get(level.getServer()).officeFor(level.dimension(), bell), paid);
	}

	/** A day's wage: a worker by its workshop's tier, the mayor and the keeper their own; 0 without a trade. */
	public static int wage(Villager villager) {
		if (villager.isBaby()) return 0;
		if (Mayors.isMayor(villager)) return TownRules.MAYOR_WAGE;
		if (Shops.isKeeper(villager)) return TownRules.KEEPER_WAGE;
		if (com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE) || com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NITWIT)) return 0;
		WorkshopState workshop = villager instanceof VillagerLifeData data ? data.vnap$workshop() : null;
		return TownRules.WAGE + (workshop == null ? 0 : workshop.tier);
	}

	public static boolean owedWage(ServerLevel level, Villager villager, long day) {
		return Family.state(villager).paidDay < day && wage(villager) > 0 && Shops.of(level, villager) != null;
	}

	/**
	 * Pays today's wage (once): the keeper's from the till, everyone else's from the treasury. With {@code mayor} it
	 * was paid by hand at the bell and the villager says so.
	 */
	public static boolean payWage(ServerLevel level, Villager villager, long day, @Nullable Villager mayor) {
		Shops.Shop shop = Shops.of(level, villager);
		if (shop == null || !owedWage(level, villager, day)) return false;
		int wage = wage(villager);
		Family.state(villager).paidDay = day;
		int paid = earn(level, villager, wage);
		if (paid > 0) {
			if (Shops.isKeeper(villager) && shop.till - paid >= TownRules.TILL_FLOOR) {
				shop.till -= paid;
				Shops.get(level.getServer()).setDirty();
			} else {
				spendTreasury(level, office(level, shop), paid);
			}
		}
		if (mayor != null) {
			Speech.queue(villager, "Villager Gets Paid", mayor, 2L);
			level.playSound(null, villager.blockPosition(), com.vnap.sound.GolemSounds.WAGE, net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 1.0F);
		}
		return true;
	}

	/** Every family tick: a wage nobody came for at the bell is paid quietly after the payday; a worker stocks up. */
	public static void tick(ServerLevel level, Villager villager, long day) {
		float time = Family.dayFraction(level);
		if (time >= SETTLE && time < 0.9F && owedWage(level, villager, day)) payWage(level, villager, day, null);
		if (Family.once(villager, "stock_up", day)) stockUp(level, villager, day);
	}

	/**
	 * What a trade's chores use up, kept in its workshop chest: below half of it the worker means to buy the rest at
	 * the stall (out of its household's purse), and the stall delivers it to the chest. Everything else a chore
	 * makes comes from its own work (crops, wool, fish, feathers).
	 */
	public static final Map<String, Map<Item, Integer>> SUPPLIES = Map.ofEntries(
			Map.entry("fletcher", Map.of(Items.FLINT, 8, Items.STICK, 16)),
			Map.entry("mason", Map.of(Items.COBBLESTONE, 16)),
			Map.entry("armorer", Map.of(Items.IRON_INGOT, 4)),
			Map.entry("toolsmith", Map.of(Items.IRON_INGOT, 4)),
			Map.entry("weaponsmith", Map.of(Items.IRON_INGOT, 4)),
			Map.entry("librarian", Map.of(Items.PAPER, 9, Items.LEATHER, 3)),
			Map.entry("cartographer", Map.of(Items.PAPER, 9)),
			// bottles for the hive's honey, bone meal for the flowers by it (Chores.honey, flowers)
			Map.entry("farmer", Map.of(Items.WHEAT_SEEDS, 8, Items.GLASS_BOTTLE, 4, Items.BONE_MEAL, 2)),
			Map.entry("butcher", Map.of(Items.WHEAT, 8)),
			Map.entry("shepherd", Map.of(Items.WHEAT, 8)),
			Map.entry("leatherworker", Map.of(Items.RABBIT_HIDE, 4)));
	public static final String CHEST = "chest:";

	static void stockUp(ServerLevel level, Villager worker, long day) {
		WorkshopState state = worker instanceof VillagerLifeData data ? data.vnap$workshop() : null;
		if (state == null || state.tier <= 0 || Shops.of(level, worker) == null) return;
		net.minecraft.world.Container chest = com.vnap.village.workshop.ProfessionWork.chest(level, state);
		if (chest == null) return;
		for (Map.Entry<Item, Integer> entry : SUPPLIES.getOrDefault(state.profession, Map.of()).entrySet()) {
			int have = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(entry.getKey())) have += chest.getItem(i).getCount();
			if (have * 2 < entry.getValue()) {
				FamilyState.Want want = want(worker, CHEST + key(entry.getKey()), entry.getValue() - have, day);
				want.count = Math.max(want.count, entry.getValue() - have);
			}
		}
	}

	/** Goods bought for the workshop go straight into its chest; false when it has none or no room. */
	static boolean intoChest(ServerLevel level, Villager worker, ItemStack goods) {
		WorkshopState state = worker instanceof VillagerLifeData data ? data.vnap$workshop() : null;
		net.minecraft.world.Container chest = state == null ? null : com.vnap.village.workshop.ProfessionWork.chest(level, state);
		if (chest == null) return false;
		ItemStack rest = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, chest, goods.copy(), null);
		return rest.isEmpty();
	}

	// ---- what families buy -----------------------------------------------------------------

	/**
	 * May the villager put this up now? Only when it (or its household's head) has one in its pockets, bought at the
	 * stall: then {@link #spend} it when it is set down. Otherwise it goes on the list to buy and the answer is no; a
	 * thing the stall has run out of is ordered for the next morning ({@link #shop}), so the wait is never long.
	 */
	public static boolean obtain(ServerLevel level, Villager villager, Item item) {
		if (has(villager, item) || has(purseOf(level, villager), item)) return true;
		want(villager, key(item), 1, Family.day(level));
		return false;
	}

	/** The bought thing was put up: out of the pockets (its own, else its household head's), off the list. */
	public static void spend(Villager villager, Item item) {
		if (!removeOne(villager, item) && villager.level() instanceof ServerLevel level) removeOne(purseOf(level, villager), item);
		Family.state(villager).wants.removeIf(want -> want.item.equals(key(item)));
	}

	private static boolean removeOne(Villager villager, Item item) {
		var inventory = villager.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (matches(inventory.getItem(i), item)) {
				inventory.removeItem(i, 1);
				return true;
			}
		}
		return false;
	}

	/**
	 * Something a day's event needs at once (the coming-of-age cake, a newborn's bed, a gravestone): taken from the
	 * stall's shelf when it has one and paid by {@code payer}'s household, or by the treasury when it cannot (or for
	 * the village, {@code payer} null). Always true: an event does not wait for money.
	 */
	public static boolean order(ServerLevel level, @Nullable Villager payer, BlockPos where, Item item) {
		Shops.Shop shop = payer != null ? Shops.of(level, payer) : Shops.near(level, where);
		if (shop == null) return true;
		Shops.Lot lot = cheapest(shop, stack -> matches(stack, item));
		// sold out: fetched from abroad at its price, the money leaving the village (nothing comes for nothing)
		int price = lot != null ? lot.price : Math.max(1, Prices.price(new ItemStack(item)));
		if (payer == null) spendTreasury(level, office(level, shop), price);
		else Housing.need(level, payer, office(level, shop), price);
		if (lot == null) return true;
		shop.till += price;
		shop.take(lot, 1);
		Shops.get(level.getServer()).setDirty();
		return true;
	}

	/** The pantry is low and no supplier has food: the wife will buy {@code count} at the stall. */
	public static void wantFood(ServerLevel level, Villager villager, int count) {
		if (Shops.of(level, villager) == null || count <= 0) return;
		FamilyState.Want want = want(villager, FOOD, count, Family.day(level));
		want.count = Math.max(want.count, count);
	}

	public static boolean wantsAnything(Villager villager) {
		return !Family.state(villager).wants.isEmpty();
	}

	private static FamilyState.Want want(Villager villager, String key, int count, long day) {
		List<FamilyState.Want> wants = Family.state(villager).wants;
		for (FamilyState.Want want : wants) if (want.item.equals(key)) return want;
		FamilyState.Want want = new FamilyState.Want(key, count, day);
		wants.add(want);
		return want;
	}

	private static String key(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** Beds are all one to a buyer: any colour will do. */
	public static boolean matches(ItemStack stack, Item item) {
		if (stack.isEmpty()) return false;
		if (item.getDefaultInstance().is(ItemTags.BEDS)) return stack.is(ItemTags.BEDS);
		return stack.is(item);
	}

	static boolean has(Villager villager, Item item) {
		var inventory = villager.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) if (matches(inventory.getItem(i), item)) return true;
		return false;
	}

	private static boolean essential(String key) {
		return key.equals(FOOD) || (key.endsWith("_bed") && !key.endsWith("cat_bed"));
	}

	private static Shops.@Nullable Lot cheapest(Shops.Shop shop, java.util.function.Predicate<ItemStack> kind) {
		return shop.stock.stream().filter(lot -> lot.count > 0 && kind.test(lot.item))
				.min(Comparator.comparingInt((Shops.Lot lot) -> lot.price)).orElse(null);
	}

	/** What happened at the counter: the lines the buyer and the keeper say. */
	public enum Visit { BOUGHT, POOR, SOLD_OUT, NOTHING }

	/**
	 * The villager is at the counter: buys what it wants that the stall has and it can pay for. Food and beds it
	 * waited {@link TownRules#HELP_DAYS} for and still cannot afford the mayor pays for out of the treasury.
	 */
	public static Visit shop(ServerLevel level, Villager buyer, Shops.Shop shop) {
		long day = Family.day(level);
		boolean bought = false;
		boolean poor = false;
		boolean soldOut = false;
		boolean helped = false;
		boolean granary = com.vnap.village.town.TownPlots.has(level, shop.bell, com.vnap.village.town.TownPlots.Kind.GRANARY);
		for (FamilyState.Want want : new ArrayList<>(Family.state(buyer).wants)) {
			boolean food = want.item.equals(FOOD);
			boolean forChest = want.item.startsWith(CHEST);
			Item item = food ? Items.AIR : BuiltInRegistries.ITEM.get(ResourceLocation.parse(forChest ? want.item.substring(CHEST.length()) : want.item));
			while (want.count > 0) {
				Shops.Lot lot = cheapest(shop, stack -> food ? Pantry.FOOD.test(stack) : matches(stack, item));
				if (lot == null) {
					// sold out: it comes with the next morning's delivery
					if (!food) shop.orders.merge(item, want.count, Integer::max);
					else shop.orders.merge(Items.BREAD, want.count, Integer::max);
					soldOut = true;
					break;
				}
				want.offered = day;
				// the granary's quarter off food: the treasury makes it up to the stall
				int pay = food && granary ? lot.price - lot.price / 4 : lot.price;
				if (!charge(level, buyer, pay)) {
					if (!essential(want.item) || day - want.since < TownRules.HELP_DAYS) {
						poor = true;
						break;
					}
					Housing.need(level, buyer, office(level, shop), pay);
					helped = true;
				}
				ItemStack one = lot.item.copyWithCount(1);
				ItemStack rest = forChest ? (intoChest(level, buyer, one) ? ItemStack.EMPTY : one) : buyer.getInventory().addItem(one);
				if (!rest.isEmpty()) {
					// pockets full: the money back, the rest another day
					Family.state(buyer).coins += pay;
					break;
				}
				shop.till += lot.price;
				if (pay < lot.price) spendTreasury(level, office(level, shop), lot.price - pay);
				shop.take(lot, 1);
				want.count--;
				bought = true;
			}
			if (want.count <= 0) Family.state(buyer).wants.remove(want);
		}
		Shops.get(level.getServer()).setDirty();
		Villager keeper = Shops.keeper(level, shop);
		if (helped) {
			Villager mayor = Mayors.mayorNear(level, shop.bell);
			if (mayor != null) Speech.queue(mayor, "Mayor Helps a Family", buyer, 4L);
		}
		if (bought) {
			Speech.queue(buyer, "Villager Buys at the Stall", keeper, 1L);
			if (keeper != null) Speech.queue(keeper, "Shopkeeper Thanks a Customer", buyer, 5L);
			return Visit.BOUGHT;
		}
		if (poor) {
			Speech.queue(buyer, "Villager Can't Afford It", keeper, 1L);
			return Visit.POOR;
		}
		if (soldOut) {
			if (keeper != null) Speech.queue(keeper, "Shopkeeper Sold Out", buyer, 1L);
			return Visit.SOLD_OUT;
		}
		return Visit.NOTHING;
	}

	/** Food the villager carries home from the stall (into the pantry). */
	public static List<ItemStack> takeFood(Villager villager) {
		List<ItemStack> out = new ArrayList<>();
		var inventory = villager.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (Pantry.FOOD.test(stack)) {
				out.add(stack.copy());
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		return out;
	}

	/** Whether the villager carries food for the pantry (a wife back from the stall). */
	public static boolean carriesFood(Villager villager) {
		var inventory = villager.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (Pantry.FOOD.test(stack)) return true;
		}
		return false;
	}

	static @Nullable DecorBlock decorBlock(String name) {
		for (DecorBlock block : LifeBlocks.HOME_DECOR) if (block.name().equals(name)) return block;
		return null;
	}
}
