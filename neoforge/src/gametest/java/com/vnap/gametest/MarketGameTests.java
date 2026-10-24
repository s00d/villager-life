package com.vnap.gametest;

import com.vnap.village.town.TownRules;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.entity.VillagerLifeData;
import com.vnap.item.LifeItems;
import com.vnap.mood.MoodState;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Caravan;
import com.vnap.village.market.Coins;
import com.vnap.village.market.Market;
import com.vnap.village.market.Prices;
import com.vnap.village.market.ShopMenu;
import com.vnap.village.market.Shops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The village market: coins that change up on their own, prices a player cannot turn round for a profit, the
 * shopkeeper of a big village and its stall, families buying before they put things up (and never waiting for ever),
 * wages the treasury always pays, the caravan, and a market that never moves the village's mood.
 */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class MarketGameTests {
	private static final BlockPos BELL = new BlockPos(50, 2, 50);

	@GameTest(batch = "market", template = "platform")
	public static void everyThingThatCanBeHadHasItsWorth(GameTestHelper helper) {
		java.util.function.ToDoubleFunction<Item> worth = item -> Prices.worth(new ItemStack(item));
		{
			// made things by their recipes, not "a building block at a fifth of a copper"
			Checks.assertTrue(helper, worth.applyAsDouble(net.minecraft.world.item.Items.FURNACE) >= 0.4 && worth.applyAsDouble(net.minecraft.world.item.Items.HOPPER) >= 15,
					"a furnace and a hopper by what makes them: " + worth.applyAsDouble(net.minecraft.world.item.Items.FURNACE) + ", " + worth.applyAsDouble(net.minecraft.world.item.Items.HOPPER));
			Checks.assertTrue(helper, worth.applyAsDouble(net.minecraft.world.item.Items.BEACON) >= 1000, "a beacon is a nether star and more: " + worth.applyAsDouble(net.minecraft.world.item.Items.BEACON));
			Checks.assertTrue(helper, worth.applyAsDouble(net.minecraft.world.item.Items.SHULKER_BOX) >= 80, "a shulker box is two shells: " + worth.applyAsDouble(net.minecraft.world.item.Items.SHULKER_BOX));
			// an ore is worth more than a Fortune III pick takes out of it (an order of ore never pays)
			Checks.assertTrue(helper, worth.applyAsDouble(net.minecraft.world.item.Items.DIAMOND_ORE) >= 2.2 * worth.applyAsDouble(net.minecraft.world.item.Items.DIAMOND),
					"diamond ore: " + worth.applyAsDouble(net.minecraft.world.item.Items.DIAMOND_ORE));
			Checks.assertTrue(helper, worth.applyAsDouble(net.minecraft.world.item.Items.ELYTRA) >= 1000, "elytra are dear: " + worth.applyAsDouble(net.minecraft.world.item.Items.ELYTRA));
			Checks.assertTrue(helper, worth.applyAsDouble(net.minecraft.world.item.Items.MUSIC_DISC_CAT) > 0 && worth.applyAsDouble(net.minecraft.world.item.Items.RED_DYE) > 0,
					"a record and a dye have a worth");
			ItemStack book = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
			Checks.assertTrue(helper, Prices.worth(book) > 0, "an enchanted book is dealt in");
			// nobody makes or sells these
			for (Item never : new Item[]{net.minecraft.world.item.Items.SPAWNER, net.minecraft.world.item.Items.ZOMBIE_SPAWN_EGG, net.minecraft.world.item.Items.BEDROCK,
					net.minecraft.world.item.Items.DRAGON_EGG, net.minecraft.world.item.Items.COMMAND_BLOCK}) {
				Checks.assertTrue(helper, worth.applyAsDouble(never) == 0, "not dealt in: " + never);
			}
		}
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void coinsChangeUpOnTheirOwn(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Coins.give(player, 5000);
		Checks.assertTrue(helper, Coins.count(player.getInventory()) == 5000, "5000 copper given");
		Checks.assertTrue(helper, count(player, LifeItems.GOLD_COIN) == 1 && count(player, LifeItems.SILVER_COIN) == 14
				&& count(player, LifeItems.COPPER_COIN) == 8, "in the fewest coins: 1 gold, 14 silver, 8 copper");
		Checks.assertTrue(helper, Coins.pay(player, 100) && Coins.count(player.getInventory()) == 4900, "a payment takes 100 and gives change");
		Checks.assertFalse(helper, Coins.pay(player, 5000), "nobody pays more than it has");
		Checks.assertTrue(helper, Coins.count(player.getInventory()) == 4900, "and a failed payment takes nothing");
		Player other = helper.makeMockPlayer(GameType.SURVIVAL);
		other.getInventory().add(new ItemStack(LifeItems.COPPER_COIN, 64));
		Coins.consolidate(other);
		Checks.assertTrue(helper, count(other, LifeItems.SILVER_COIN) == 1 && count(other, LifeItems.COPPER_COIN) == 0,
				"a full stack of copper is one silver coin");
		Checks.assertTrue(helper, Prices.worth(new ItemStack(LifeItems.GOLD_COIN)) == 0.0, "the stall does not trade in coins");
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aPlayerNeverSellsBackAtAGain(GameTestHelper helper) {
		for (Map.Entry<net.minecraft.resources.ResourceLocation, Double> entry : Prices.listed().entrySet()) {
			Checks.assertTrue(helper, BuiltInRegistries.ITEM.containsKey(entry.getKey()), "a listed price is for a real item: " + entry.getKey());
		}
		for (Item item : Market.catalogue().keySet()) {
			Checks.assertTrue(helper, Prices.price(new ItemStack(item)) > 0, "the stall's goods have a price: " + item);
		}
		List<ItemStack> samples = new java.util.ArrayList<>();
		for (Item item : Market.catalogue().keySet()) samples.add(new ItemStack(item));
		samples.addAll(List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.WHEAT, 64), new ItemStack(Items.IRON_PICKAXE),
				new ItemStack(Items.GOLDEN_APPLE, 3), new ItemStack(Items.EMERALD, 10)));
		for (ItemStack stack : samples) {
			for (int mood = -100; mood <= 100; mood += 25) {
				int bought = Prices.playerBuys(Prices.price(stack), stack.getCount(), mood);
				int sold = Prices.playerSells(stack, mood);
				Checks.assertTrue(helper, sold < bought, "at mood " + mood + " " + stack + " sells for " + sold + " and costs " + bought);
			}
		}
		Checks.assertTrue(helper, Prices.playerBuys(10, 1, -100) > Prices.playerBuys(10, 1, 100), "friends of the village pay less");
		Checks.assertTrue(helper, Prices.playerSells(new ItemStack(Items.DIAMOND), 100) > Prices.playerSells(new ItemStack(Items.DIAMOND), -100),
				"and are paid more");
		Checks.assertTrue(helper, Prices.worth(new ItemStack(Items.BEDROCK)) == 0.0, "what the list does not know is not traded");
		helper.succeed();
	}

	@GameTest(batch = "market", template = "home_platform")
	public static void aShopkeeperComesToEveryVillageAndBuildsItsShopHouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		long day = Family.day(level);
		Map<BlockPos, List<Float>> mood = mood(level, bell);
		List<Villager> adults = new java.util.ArrayList<>();
		for (int i = 0; i < 2; i++) adults.add(adult(helper, i, "none", 20L + i));
		Villager mayor = adult(helper, 4, "none", 2L);
		((VillagerLifeData) mayor).vnap$setMayor(true);
		Shops.daily(level, adults.get(0), day);
		Shops shops = Shops.get(level.getServer());
		Shops.Shop shop = shops.shopNear(level.dimension(), bell);
		Villager keeper = shop == null ? null : Shops.keeper(level, shop);
		if (shop == null || keeper == null) {
			Checks.fail(helper, "even a small village has its stall and a shopkeeper");
			return;
		}
		Checks.assertTrue(helper, shop.stocked && !adults.contains(keeper) && !keeper.equals(mayor),
				"the keeper came from elsewhere: the village's own people keep their lives");
		Shops.daily(level, adults.get(1), day + 1);
		Checks.assertTrue(helper, level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(bell).inflate(64.0), Shops::isKeeper).size() == 1,
				"a village keeps one stall and one keeper");
		keeper.setVillagerData(com.vnap.Mc.withProfession(keeper.getVillagerData(), VillagerProfession.LIBRARIAN));
		Checks.assertTrue(helper, com.vnap.Mc.is(keeper.getVillagerData(), VillagerProfession.NONE), "the keeper takes no trade");
		Checks.assertFalse(helper, Mayors.candidate(keeper), "the keeper is not elected mayor");
		for (Map.Entry<Item, Integer> entry : Market.catalogue().entrySet()) {
			Checks.assertTrue(helper, shop.count(entry.getKey()) == entry.getValue(), "full shelves: " + entry.getKey());
		}
		HouseProject stall = HomeProjects.start(level, keeper, day, Shops.STALLS);
		if (stall == null) {
			Checks.fail(helper, "the shop house finds a site near the bell");
			return;
		}
		Checks.assertTrue(helper, stall.footprint(level).getCenter().distSqr(bell) >= (double) (TownRules.HOUSE_MIN_DISTANCE - 2) * (TownRules.HOUSE_MIN_DISTANCE - 2),
				"the shop house keeps a house's distance from the bell (the village gathers there)");
		Checks.assertTrue(helper, stall.base() && stall.job(level).site == null, "the shop house is the town's base: built for nothing");
		stall.owner = adults.get(0).getUUID();
		Shops.daily(level, keeper, day + 2);
		Checks.assertTrue(helper, stall.owner.equals(keeper.getUUID()), "a new keeper builds on the shop house the late one began");
		BlockPos counter = shop.counter;
		Checks.assertTrue(helper, !stall.done && counter != null && stall.footprint(level).isInside(counter) && Shops.isCounter(level, counter),
				"the shop house's counter goes in first and the stall trades from it while the walls go up");
		// the unfinished shop house is everyone's first work: a farmer leaves the field for it
		Villager farmer = adults.get(adults.size() - 1);
		farmer.setVillagerData(com.vnap.Mc.withProfession(farmer.getVillagerData(), VillagerProfession.FARMER));
		Checks.assertTrue(helper, HomeProjects.forBuilder(level, farmer) == stall, "a farmer builds the shop house first");
		HomeProjects.buildNow(level, stall);
		Checks.assertTrue(helper, shop.counter != null && Shops.isCounter(level, shop.counter), "the shop house's counter is found");
		Checks.assertTrue(helper, Shops.keeperSpot(level, shop) != null, "the keeper has its place behind the counter");
		// the counter is the shop: no clearing takes it down, and knocked down it is set again at once, for nothing
		BlockPos standing = java.util.Objects.requireNonNull(shop.counter);
		Checks.assertTrue(helper, HomeProjects.cleared(level, stall, standing), "a lot's clearing passes the counter by");
		level.removeBlock(standing, false);
		Shops.daily(level, keeper, day + 3);
		Checks.assertTrue(helper, Shops.isCounter(level, standing) && standing.equals(shop.counter), "a knocked-down counter is set again the next day");
		// the keeper's work is the counter: no town chores, and in market hours no visits
		Checks.assertTrue(helper, com.vnap.village.life.Life.official(keeper), "the keeper is one of the town's officials");
		com.vnap.Mc.setDayTime(level, 3000L);
		com.vnap.village.life.Agenda.go(keeper, "visit_test", bell, com.vnap.village.life.Agenda.VISIT, null, null);
		Checks.assertTrue(helper, com.vnap.village.life.Agenda.top(level, keeper) == null, "a visit does not take the keeper from the counter in market hours");
		Checks.assertTrue(helper, HomeProjects.hasHome(keeper), "the keeper who came lives in the shop house");
		Checks.assertTrue(helper, mood.equals(mood(level, bell)), "the market does not move the village's mood");
		VillageMoodSavedData.get(level).houses().remove(stall);
		keeper.discard();
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "home_platform")
	public static void theStallPassesToTheFamilyElseAShopkeeperComes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Shops.Shop shop = stall(helper);
		Villager keeper = adult(helper, 0, "none", 20L);
		Villager wife = adult(helper, 1, "none", 22L);
		Villager neighbour = adult(helper, 2, "none", 10L);
		((VillagerLifeData) keeper).vnap$setShopkeeper(true);
		shop.keeper = keeper.getUUID();
		Family.state(keeper).spouse = wife.getUUID();
		Family.state(wife).spouse = keeper.getUUID();
		Shops.onGone(level, keeper);
		Checks.assertTrue(helper, wife.getUUID().equals(shop.keeper) && Shops.isKeeper(wife) && !Shops.isKeeper(keeper),
				"the keeper's widow takes the stall over");
		keeper.discard();
		Shops.onGone(level, wife);
		wife.discard();
		Villager arrived = Shops.keeper(level, shop);
		Checks.assertTrue(helper, arrived != null && !arrived.equals(neighbour) && !Shops.isKeeper(neighbour),
				"with nobody of the family left a shopkeeper comes; the neighbour keeps its life");
		Checks.assertTrue(helper, arrived != null && bell.equals(com.vnap.village.home.Towns.bellOf(arrived)), "the newcomer is of the village");
		if (arrived != null) {
			Shops.onGone(level, arrived);
			arrived.discard();
		}
		Checks.assertTrue(helper, shop.keeper == null, "a second newcomer the same day does not come: killing keepers breeds no villagers");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aPlayerCannotEmptyTheStallNorItsTill(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Shops.Shop shop = stall(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Coins.give(player, 10_000);
		Shops.Lot candles = shop.lot(new ItemStack(Items.CANDLE));
		if (candles == null) {
			Checks.fail(helper, "the stall sells candles");
			return;
		}
		int reserve = (Market.target(Items.CANDLE) + 3) / 4;
		Checks.assertFalse(helper, ShopMenu.buy(level, shop, player, candles, candles.count, bell), "the last of its own goods are kept for the villagers");
		Checks.assertTrue(helper, ShopMenu.buy(level, shop, player, candles, candles.count - reserve, bell) && candles.count == reserve,
				"all but the reserve can be bought");
		shop.till = 100 * Coins.SILVER;
		int sold = 0;
		for (int i = 0; i < 40; i++) {
			if (ShopMenu.sell(level, shop, player, new ItemStack(Items.DIAMOND, 4), bell)) sold++;
		}
		Checks.assertTrue(helper, shop.paid(player.getUUID()) <= TownRules.PLAYER_BUDGET && sold < 40, "a day's buying from players has a ceiling: " + shop.paidPlayers);
		Checks.assertTrue(helper, ShopMenu.sellState(shop, player.getUUID(), new ItemStack(Items.DIAMOND, 4), 60) == ShopMenu.SELL_BUDGET, "and says so");
		// the ceiling is each player's: another one still sells
		Player other = helper.makeMockPlayer(GameType.SURVIVAL);
		Checks.assertTrue(helper, ShopMenu.sell(level, shop, other, new ItemStack(Items.DIAMOND, 1), bell), "another player has a day's budget of its own");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aHelperEarnsADayLabourersWage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Villager helperVillager = adult(helper, 0, "none", 30L);
		com.vnap.village.market.Site site = new com.vnap.village.market.Site();
		site.funds = 100;
		site.wages = 30;
		for (int i = 0; i < 40; i++) Market.dayLabour(level, helperVillager, site);
		Checks.assertTrue(helper, Family.state(helperVillager).coins == TownRules.DAY_LABOUR && site.funds == 100 - TownRules.DAY_LABOUR,
				"a copper a block out of the build's money, up to a day's labour");
		// the next day's labour stops where the owner's wages do: the rest of the money buys the materials
		Family.state(helperVillager).labourDay = -1;
		for (int i = 0; i < 40; i++) Market.dayLabour(level, helperVillager, site);
		Checks.assertTrue(helper, site.wages == 0 && site.funds == 70, "no more than the wages put in: " + site.funds);
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "home_platform")
	public static void aBrokenShopIsMendedForNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Villager first = adult(helper, 0, "none", 20L);
		Shops.daily(level, first, Family.day(level));
		Shops.Shop shop = Shops.get(level.getServer()).shopNear(level.dimension(), bell);
		Villager keeper = shop == null ? null : Shops.keeper(level, shop);
		if (shop == null || keeper == null) {
			Checks.fail(helper, "a stall and its keeper");
			return;
		}
		HouseProject stall = HomeProjects.start(level, keeper, Family.day(level), Shops.STALLS);
		if (stall == null) {
			Checks.fail(helper, "the shop house finds a site");
			return;
		}
		HomeProjects.buildNow(level, stall);
		BlockPos counter = shop.counter;
		if (counter == null) {
			Checks.fail(helper, "a counter");
			return;
		}
		// a broken wall is mended by hand for nothing; a broken counter the keeper sets again at once (the shop is the town's base)
		BlockPos wall = null;
		for (com.vnap.village.script.SchematicSpec.PlacedBlock block : stall.schematic(level).blocks) {
			BlockPos at = com.vnap.village.build.BuildJob.at(stall.origin, stall.rotation, block.rel());
			if (block.rel().getY() == 2 && level.getBlockState(at).isCollisionShapeFullBlock(level, at) && !Shops.isCounter(level, at)) {
				wall = at;
				break;
			}
		}
		if (wall == null) {
			Checks.fail(helper, "a wall block of the shop house");
			return;
		}
		level.removeBlock(wall, false);
		level.removeBlock(counter, false);
		Checks.assertFalse(helper, Shops.open(level, shop), "a broken counter shuts the stall");
		Shops.daily(level, keeper, Family.day(level) + 1);
		Checks.assertTrue(helper, Shops.isCounter(level, counter), "the counter is set again the same day");
		Checks.assertTrue(helper, stall.mending && stall.done && stall.job(level).site == null, "the hole is mended at once, for nothing");
		Checks.assertTrue(helper, HomeProjects.forBuilder(level, keeper) == stall, "the keeper drops everything for it");
		stall.job(level).completeNow(level);
		Checks.assertFalse(helper, level.getBlockState(wall).isAir(), "the wall stands again");
		VillageMoodSavedData.get(level).houses().remove(stall);
		keeper.discard();
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void withoutAVillageNothingIsFree(GameTestHelper helper) {
		Villager wife = helper.spawn(EntityType.VILLAGER, BELL.offset(-4, 0, 2));
		wife.setNoAi(true);
		Checks.assertFalse(helper, Market.obtain(helper.getLevel(), wife, Market.decor("cradle")), "no stall to buy at: no cradle");
		Checks.assertTrue(helper, Market.wantsAnything(wife), "it waits on her list");
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aFamilyBuysWhatItPutsUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Villager wife = adult(helper, 0, "none", 30L);
		Item cradle = Market.decor("cradle");
		Checks.assertFalse(helper, Market.obtain(level, wife, cradle), "with a stall the cradle is bought first");
		Checks.assertTrue(helper, Market.wantsAnything(wife), "it is on her list");
		Checks.assertTrue(helper, Market.shop(level, wife, shop) == Market.Visit.POOR, "without money she cannot buy it");
		Family.state(wife).coins = 100;
		int till = shop.till;
		int shelf = shop.count(cradle);
		Checks.assertTrue(helper, Market.shop(level, wife, shop) == Market.Visit.BOUGHT, "with money she buys it");
		int price = Prices.price(new ItemStack(cradle));
		Checks.assertTrue(helper, Family.state(wife).coins == 100 - price && shop.till == till + price && shop.count(cradle) == shelf - 1,
				"her coins went into the till and the cradle off the shelf");
		Checks.assertTrue(helper, Market.obtain(level, wife, cradle), "now she may put it up");
		Market.spend(wife, cradle);
		Checks.assertFalse(helper, wife.getInventory().hasAnyMatching(stack -> stack.is(cradle)), "put up: out of her pockets");
		Checks.assertFalse(helper, Market.wantsAnything(wife), "and off her list");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void theHouseholdPursePaysAndWagesGoToTheHead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Villager head = adult(helper, 0, "none", 30L);
		Villager son = adult(helper, 1, "farmer", 50L);
		Family.state(son).head = head.getUUID();
		Family.state(head).head = head.getUUID();
		long day = Family.day(level);
		Checks.assertTrue(helper, Market.payWage(level, son, day, null), "the son is paid");
		Checks.assertTrue(helper, Family.state(head).coins == TownRules.WAGE && Family.state(son).coins == 0, "into the household's purse");
		Checks.assertFalse(helper, Market.payWage(level, son, day, null), "once a day");
		Checks.assertTrue(helper, Market.funds(level, son) == TownRules.WAGE, "the son can spend the household's money");
		Checks.assertTrue(helper, Market.charge(level, son, 3) && Family.state(head).coins == TownRules.WAGE - 3, "and does");
		Family.state(head).coins = TownRules.PURSE_CAP;
		Mayors.Office office = Market.office(level, shop);
		int treasury = office.treasury;
		Family.state(son).paidDay = day - 1;
		Market.payWage(level, son, day, null);
		Checks.assertTrue(helper, Family.state(head).coins == TownRules.PURSE_CAP && office.treasury == treasury,
				"a full purse takes no more: the wage stays in the treasury");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aFamilyPaysForItsOwnBuildAndRepaysWhatTheTreasuryLent(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Shops.Shop shop = stall(helper);
		Villager head = adult(helper, 0, "farmer", 30L);
		Family.state(head).head = head.getUUID();
		Family.state(head).coins = 40;
		Mayors.Office office = Market.office(level, shop);
		int treasury = office.treasury;
		com.vnap.village.market.Site site = new com.vnap.village.market.Site();
		// a bill of 100 copper (a tenth more for prices that move): the purse gives its 40 but what it keeps for bread, the
		// treasury lends the rest (under the cap)
		Checks.assertTrue(helper, Market.finance(level, head, bell, site, java.util.Map.of(net.minecraft.world.item.Items.COBBLESTONE, 100),
				com.vnap.village.market.Housing.Purpose.GROWTH), "a build the household can carry is paid");
		int cost = site.funds;
		int own = 40 - TownRules.RESERVE;
		Checks.assertTrue(helper, cost > 40 && Family.state(head).coins == TownRules.RESERVE && Market.debt(level, head) == cost - own
				&& office.treasury == treasury - (cost - own), "the household pays what it has, the rest is a loan: " + cost + " " + Market.debt(level, head));
		int owed = Market.debt(level, head);
		long day = Family.day(level);
		Market.payWage(level, head, day, null);
		int wage = Market.wage(head);
		Checks.assertTrue(helper, Market.debt(level, head) == owed - (wage + 1) / 2 && Family.state(head).coins == TownRules.RESERVE + wage - (wage + 1) / 2,
				"half the wage goes back: " + Market.debt(level, head) + " " + Family.state(head).coins);
		Family.state(head).debt = 0;
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void theTreasuryAlwaysPaysTheWages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Mayors.Office office = Market.office(level, shop);
		office.treasury = 0;
		long day = Family.day(level);
		for (int i = 0; i < 6; i++) {
			Villager worker = adult(helper, i, "farmer", 10L + i);
			Checks.assertTrue(helper, Market.payWage(level, worker, day, null) && Family.state(worker).coins == TownRules.WAGE,
					"every worker is paid, the treasury empty or not");
		}
		Checks.assertTrue(helper, office.treasury == 0 && office.minted == 6L * TownRules.WAGE, "what it lacked the village made up");
		Villager keeper = adult(helper, 7, "none", 5L);
		((VillagerLifeData) keeper).vnap$setShopkeeper(true);
		int till = shop.till;
		Market.payWage(level, keeper, day, null);
		Checks.assertTrue(helper, shop.till == till - TownRules.KEEPER_WAGE && Family.state(keeper).coins == TownRules.KEEPER_WAGE,
				"the keeper is paid out of the till");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aPoorFamilyGetsFoodFromTheMayor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Villager wife = adult(helper, 0, "none", 30L);
		long day = Family.day(level);
		Family.state(wife).wants.add(new FamilyState.Want(Market.FOOD, 3, day));
		Checks.assertTrue(helper, Market.shop(level, wife, shop) == Market.Visit.POOR, "today she waits for the wage");
		Family.state(wife).wants.clear();
		Family.state(wife).wants.add(new FamilyState.Want(Market.FOOD, 3, day - TownRules.HELP_DAYS));
		Family.state(wife).wants.add(new FamilyState.Want("villager_life:vanity_table", 1, day - TownRules.HELP_DAYS));
		Mayors.Office office = Market.office(level, shop);
		int treasury = office.treasury;
		Market.shop(level, wife, shop);
		Checks.assertTrue(helper, Market.carriesFood(wife), "food she waited two days for the treasury pays");
		Checks.assertTrue(helper, office.treasury < treasury, "out of the treasury");
		Checks.assertTrue(helper, Family.state(wife).wants.size() == 1 && Family.state(wife).wants.get(0).item.endsWith("vanity_table"),
				"a vanity table she still saves up for");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aSoldOutThingComesWithTheMorningDelivery(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Villager wife = adult(helper, 0, "none", 30L);
		Item mirror = Market.decor("standing_mirror");
		shop.stock.removeIf(lot -> lot.item.is(mirror));
		Family.state(wife).coins = 100;
		Checks.assertFalse(helper, Market.obtain(level, wife, mirror), "she means to buy it");
		Checks.assertTrue(helper, Market.shop(level, wife, shop) == Market.Visit.SOLD_OUT, "sold out");
		Checks.assertTrue(helper, shop.orders.getOrDefault(mirror, 0) >= 1, "so it is ordered");
		Market.restock(level, shop);
		Checks.assertTrue(helper, shop.count(mirror) >= 1 && shop.orders.isEmpty(), "and comes with the morning's delivery");
		Checks.assertTrue(helper, Market.shop(level, wife, shop) == Market.Visit.BOUGHT, "then she buys it");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aBlockGoesUpOnlyFromTheSitesStore(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.village.script.SchematicSpec spec = new com.vnap.village.script.SchematicSpec(1, 1, 1, List.of(),
				List.of(new com.vnap.village.script.SchematicSpec.PlacedBlock(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState())), BlockPos.ZERO);
		BlockPos at = helper.absolutePos(new BlockPos(20, 2, 20));
		com.vnap.village.build.BuildJob job = com.vnap.village.build.BuildJob.all("test", spec, at, net.minecraft.world.level.block.Rotation.NONE,
				com.vnap.village.build.BuildJob.NO_GROUND);
		com.vnap.village.market.Site site = new com.vnap.village.market.Site();
		job.site = site;
		Checks.assertTrue(helper, job.lacksMaterials() && job.placeNext(level, null) == null && level.getBlockState(at).isAir() && !job.done(),
				"no planks on the site: nothing goes up, the block waits");
		site.add(Items.OAK_PLANKS, 1);
		Checks.assertTrue(helper, job.placeNext(level, null) != null && level.getBlockState(at).is(Blocks.OAK_PLANKS) && site.count(Items.OAK_PLANKS) == 0,
				"with a plank brought it goes up and the plank is used");
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void whatABlockCosts(GameTestHelper helper) {
		Checks.assertTrue(helper, com.vnap.village.market.Materials.items(Blocks.OAK_DOOR.defaultBlockState()).equals(Map.of(Items.OAK_DOOR, 1))
				&& com.vnap.village.market.Materials.items(Blocks.OAK_DOOR.defaultBlockState().setValue(
				net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)).isEmpty(),
				"a door is one door, its upper half nothing");
		Checks.assertTrue(helper, com.vnap.village.market.Materials.items(Blocks.POTTED_POPPY.defaultBlockState())
				.equals(Map.of(Items.FLOWER_POT, 1, Items.POPPY, 1)), "a potted poppy is a pot and a poppy");
		Checks.assertTrue(helper, com.vnap.village.market.Materials.items(Blocks.CANDLE.defaultBlockState()
				.setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3)).equals(Map.of(Items.CANDLE, 3)), "three candles");
		Checks.assertTrue(helper, com.vnap.village.market.Materials.items(Blocks.OAK_SLAB.defaultBlockState()
				.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE))
				.equals(Map.of(Items.OAK_SLAB, 2)), "a double slab is two");
		Checks.assertTrue(helper, com.vnap.village.market.Materials.items(Blocks.RED_WALL_BANNER.defaultBlockState()).equals(Map.of(Items.RED_BANNER, 1)),
				"a wall banner is a banner");
		Checks.assertTrue(helper, com.vnap.village.market.Materials.items(Blocks.FARMLAND.defaultBlockState()).equals(Map.of(Items.DIRT, 1)), "farmland is dirt");
		com.vnap.village.market.Materials.Wood stairs = com.vnap.village.market.Materials.wood(Items.SPRUCE_STAIRS);
		Checks.assertTrue(helper, stairs != null && stairs.log().equals(Items.SPRUCE_LOG), "spruce stairs are made of spruce logs");
		com.vnap.village.market.Materials.Wood dark = com.vnap.village.market.Materials.wood(Items.DARK_OAK_PLANKS);
		Checks.assertTrue(helper, dark != null && dark.log().equals(Items.DARK_OAK_LOG), "dark oak planks of dark oak, not oak");
		// every block the villagers build has a price and the stall keeps it (or the log it is made of)
		for (Map.Entry<Item, Integer> entry : Market.catalogue().entrySet()) {
			Checks.assertTrue(helper, Prices.price(new ItemStack(entry.getKey())) > 0, "the stall's goods have a price: " + entry.getKey());
		}
		for (String name : List.of("house/cottage", "house/mayor_house", "house/market_stall", "house/village_farm", "workshop/mason_t3", "festoon", "town/chapel", "town/bakehouse", "town/tavern")) {
			for (Item item : com.vnap.village.market.Materials.bill(com.vnap.village.script.SchematicSpec.loadBundled(name)).keySet()) {
				com.vnap.village.market.Materials.Wood wood = com.vnap.village.market.Materials.wood(item);
				Item kept = wood == null ? item : wood.log();
				Checks.assertTrue(helper, Market.target(kept) > 0, name + ": the stall keeps " + kept);
			}
		}
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aBuilderBuysForTheSiteAndTheStallSawsItsLogs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Villager builder = adult(helper, 0, "none", 30L);
		shop.stock.removeIf(lot -> lot.item.is(Items.OAK_PLANKS));
		int logs = shop.count(Items.OAK_LOG);
		com.vnap.village.market.Site site = new com.vnap.village.market.Site();
		site.funds = 100;
		int till = shop.till;
		Checks.assertTrue(helper, Market.supply(level, builder, shop, site, Map.of(Items.OAK_PLANKS, 8, Items.GLASS_PANE, 4)), "the builder buys");
		Checks.assertTrue(helper, site.count(Items.OAK_PLANKS) == 8 && site.count(Items.GLASS_PANE) == 4, "the planks and panes are on the site");
		Checks.assertTrue(helper, shop.count(Items.OAK_LOG) == logs - 2, "the stall made the planks of two logs");
		Checks.assertTrue(helper, site.funds < 100 && shop.till > till, "paid out of the site's money into the till");
		site.funds = 0;
		Mayors.Office office = Market.office(level, shop);
		int treasury = office.treasury;
		Market.supply(level, builder, shop, site, Map.of(Items.OAK_PLANKS, 16));
		Checks.assertTrue(helper, site.count(Items.OAK_PLANKS) == 16 && office.treasury < treasury, "a site out of money is topped up by the treasury");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aCartloadSoldIsTheThingsItHeldNotCopiesOfTheFirst(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Villager seller = adult(helper, 0, "librarian", 30L);
		// two maps of two places: the same item, each its own
		ItemStack first = new ItemStack(Items.FILLED_MAP);
		first.set(net.minecraft.core.component.DataComponents.MAP_ID, new net.minecraft.world.level.saveddata.maps.MapId(901));
		ItemStack second = new ItemStack(Items.FILLED_MAP);
		second.set(net.minecraft.core.component.DataComponents.MAP_ID, new net.minecraft.world.level.saveddata.maps.MapId(902));
		net.minecraft.world.SimpleContainer load = new net.minecraft.world.SimpleContainer(9);
		load.setItem(0, first.copy());
		for (int i = 1; i <= 3; i++) load.setItem(i, second.copy());
		shop.stock.removeIf(lot -> lot.item.is(Items.FILLED_MAP));
		com.vnap.village.market.Produce.sellLoad(level, seller, shop, load);
		Shops.Lot sold = shop.lot(first);
		Shops.Lot other = shop.lot(second);
		// one sample copied for all made the one map of 901 four
		Checks.assertTrue(helper, sold != null && sold.count == 1 && other != null && other.count == 3,
				"each map is sold as itself: 901 " + (sold == null ? 0 : sold.count) + ", 902 " + (other == null ? 0 : other.count));
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aBuilderAtASoldOutStallGetsItBroughtUpToADailyCap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Villager builder = adult(helper, 0, "none", 30L);
		shop.stock.removeIf(lot -> lot.item.is(Items.GLASS_PANE));
		shop.orders.clear();
		com.vnap.village.market.Site site = new com.vnap.village.market.Site();
		site.funds = 100;
		// sold out: the caravan's drover brings it at once, the build does not wait a day for twelve panes
		Market.supply(level, builder, shop, site, Map.of(Items.GLASS_PANE, 12));
		Checks.assertTrue(helper, site.lacking(Map.of(Items.GLASS_PANE, 12)).isEmpty() && shop.orders.isEmpty(), "the panes are on the site at once");
		// but only so much a day: the rest is ordered for the morning, once however often the builder comes back
		for (int trip = 0; trip < 5; trip++) Market.supply(level, builder, shop, site, Map.of(Items.GLASS_PANE, 12 + TownRules.ABROAD_PER_DAY + 40));
		Checks.assertTrue(helper, shop.orders.getOrDefault(Items.GLASS_PANE, 0) == 52,
				"past the day's cap the shortfall is ordered once, not each trip (" + shop.orders.get(Items.GLASS_PANE) + ")");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aNewHouseIsPaidByItsFamily(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Shops.Shop shop = stall(helper);
		Villager owner = adult(helper, 0, "none", 30L);
		HouseProject house = new HouseProject(bell.asLong(), bell, owner.getUUID(), "cottage", "plains",
				helper.absolutePos(new BlockPos(5, 1, 5)), net.minecraft.world.level.block.Rotation.NONE, Family.day(level));
		HomeProjects.register(level, house);
		int cost = com.vnap.village.market.Materials.cost(com.vnap.village.market.Materials.bill(house.schematic(level)));
		Checks.assertTrue(helper, house.site.funds >= cost && house.site.stock.isEmpty(), "the cottage's bill is on its site: " + house.site.funds + " of " + cost);
		int owed = Market.debt(level, owner);
		Checks.assertTrue(helper, owed == Math.min(TownRules.LOAN_CAP, house.site.funds),
				"a family without coins borrows from the treasury up to the cap, the town pays the rest: " + owed + " of " + house.site.funds);
		house.site.funds = owed + 40;
		HomeProjects.finish(level, house);
		Checks.assertTrue(helper, Market.debt(level, owner) == 0 && Family.state(owner).coins == 40 && house.site.funds == 0,
				"the money it did not need pays the loan back, the rest goes to the owner");
		VillageMoodSavedData.get(level).houses().remove(house);
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void theCaravanTakesTheSurplus(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		shop.put(new ItemStack(Items.OAK_LOG), 1000, false);
		int till = shop.till;
		com.vnap.village.market.Caravan.arrive(level, shop, Family.day(level));
		Checks.assertTrue(helper, shop.count(Items.OAK_LOG) <= 2 * Market.target(Items.OAK_LOG) && shop.till > till,
				"a thousand logs do not stay: the caravan buys what is over twice the stall's stock");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aPetsHomeIsBoughtBeforeTaming(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Villager head = adult(helper, 0, "none", 30L);
		Villager wife = adult(helper, 1, "none", 31L);
		Family.state(head).spouse = wife.getUUID();
		Family.state(head).head = head.getUUID();
		Family.state(wife).spouse = head.getUUID();
		Family.state(head).home = helper.absolutePos(new BlockPos(30, 2, 30));
		((VillagerLifeData) head).vnap$setHomeBed(Family.state(head).home);
		Family.state(head).petLostDay = Long.MIN_VALUE / 2;
		// a child in the house: then the family always looks for a pet (without one only a day in four), no luck in it
		Villager kid = adult(helper, 2, "none", 32L);
		kid.setAge(-24000);
		Family.state(kid).head = head.getUUID();
		com.vnap.village.home.Pets.daily(level, head, Family.state(head), Family.day(level));
		Checks.assertTrue(helper, Family.state(head).stray == null, "no stray without the pet's home");
		Checks.assertTrue(helper, Market.wantsAnything(head), "the kennel (or bed, or perch) is on the list first");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void eventsDoNotWaitForMoney(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Villager father = adult(helper, 0, "none", 30L);
		Mayors.Office office = Market.office(level, shop);
		int treasury = office.treasury;
		int cakes = shop.count(Items.CAKE);
		Checks.assertTrue(helper, Market.order(level, father, father.blockPosition(), Items.CAKE), "the coming-of-age cake is always there");
		Checks.assertTrue(helper, shop.count(Items.CAKE) == cakes - 1 && office.treasury == treasury - Prices.price(new ItemStack(Items.CAKE)),
				"taken off the shelf, paid by the village for a family without money");
		Family.state(father).coins = 50;
		Market.order(level, father, father.blockPosition(), Items.WHITE_BED);
		Checks.assertTrue(helper, Family.state(father).coins == 50 - Prices.price(new ItemStack(Items.WHITE_BED)), "a family with money pays itself");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void theCaravanFillsTheShelvesAndTheTillStaysInBounds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Shops.Lot bread = shop.lot(new ItemStack(Items.BREAD));
		if (bread == null) {
			Checks.fail(helper, "the stall sells bread");
			return;
		}
		bread.count = 0;
		int till = shop.till;
		int cost = Caravan.arrive(level, shop, Family.day(level));
		Checks.assertTrue(helper, shop.count(Items.BREAD) == Market.target(Items.BREAD), "the bread is back");
		Checks.assertTrue(helper, cost == (int) Math.ceil(Market.target(Items.BREAD) * Market.WHOLESALE) && shop.till == till - cost,
				"for half its worth, out of the till");
		Mayors.Office office = Market.office(level, shop);
		int treasury = office.treasury;
		shop.till = TownRules.TILL_CAP + 100;
		Market.balance(level, shop);
		Checks.assertTrue(helper, shop.till == TownRules.TILL_CAP && office.treasury == treasury + 100, "takings over the cap go to the village");
		shop.till = 0;
		Market.balance(level, shop);
		Checks.assertTrue(helper, shop.till == TownRules.TILL_FLOOR, "an empty till is filled up to its floor");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aPlayerBuysAndSellsAndAVillagerBuysWhatHeSold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Shops.Shop shop = stall(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Coins.give(player, 20);
		Shops.Lot bread = shop.lot(new ItemStack(Items.BREAD));
		if (bread == null) {
			Checks.fail(helper, "the stall sells bread");
			return;
		}
		int till = shop.till;
		Checks.assertTrue(helper, ShopMenu.buy(level, shop, player, bread, 4, bell), "the player buys four loaves");
		int paid = 20 - Coins.count(player.getInventory());
		Checks.assertTrue(helper, paid == Prices.playerBuys(bread.price, 4, ShopMenu.mood(level, bell)) && shop.till == till + paid,
				"for the price of the day, into the till");
		Checks.assertTrue(helper, player.getInventory().countItem(Items.BREAD) == 4, "and has them");
		Checks.assertFalse(helper, ShopMenu.buy(level, shop, player, bread, 100, bell), "nobody buys more than the shelf has");
		ItemStack ring = new ItemStack(LifeItems.WEDDING_RING);
		shop.stock.removeIf(lot -> lot.item.is(LifeItems.WEDDING_RING));
		int before = Coins.count(player.getInventory());
		Checks.assertTrue(helper, ShopMenu.sell(level, shop, player, ring, bell), "the player sells a wedding ring");
		Checks.assertTrue(helper, ring.isEmpty() && Coins.count(player.getInventory()) > before, "for coins");
		Shops.Lot sold = shop.lot(new ItemStack(LifeItems.WEDDING_RING));
		Checks.assertTrue(helper, sold != null && sold.fromPlayer && sold.price == Prices.price(new ItemStack(LifeItems.WEDDING_RING)),
				"it is on the shelf at its worth");
		Checks.assertFalse(helper, ShopMenu.sell(level, shop, player, new ItemStack(Items.BEDROCK), bell), "bedrock is not bought");
		shop.till = TownRules.TILL_FLOOR;
		Checks.assertFalse(helper, ShopMenu.sell(level, shop, player, new ItemStack(Items.DIAMOND), bell), "nor anything from a till at its floor");
		Villager groom = adult(helper, 0, "none", 30L);
		Family.state(groom).coins = 200;
		Family.state(groom).wants.add(new FamilyState.Want("villager_life:wedding_ring", 1, Family.day(level)));
		Market.shop(level, groom, shop);
		Checks.assertTrue(helper, groom.getInventory().hasAnyMatching(stack -> stack.is(LifeItems.WEDDING_RING))
				&& shop.lot(new ItemStack(LifeItems.WEDDING_RING)) == null, "a villager buys the player's ring and its lot is gone");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void aKeeperWhoDiedLeavesTheStallToTheNext(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Shops.Shop shop = stall(helper);
		Villager keeper = adult(helper, 0, "none", 30L);
		((VillagerLifeData) keeper).vnap$setShopkeeper(true);
		shop.keeper = keeper.getUUID();
		int goods = shop.stock.stream().mapToInt(lot -> lot.count).sum();
		Shops.onGone(level, keeper);
		Villager next = Shops.keeper(level, shop);
		Checks.assertTrue(helper, next != null && !next.equals(keeper) && !Shops.isKeeper(keeper), "a keeper without family is followed by a newcomer");
		Checks.assertTrue(helper, shop.stock.stream().mapToInt(lot -> lot.count).sum() == goods && shop.stocked, "the goods and the till pass on");
		if (next != null) next.discard();
		close(level, shop);
		helper.succeed();
	}

	// ---- helpers ---------------------------------------------------------------------------

	private static int count(Player player, Item coin) {
		return player.getInventory().countItem(coin);
	}

	private static BlockPos bell(GameTestHelper helper) {
		helper.setBlock(BELL, Blocks.BELL);
		return helper.absolutePos(BELL);
	}

	/** Every trade sells its surplus at the stall and keeps its supplies, the pantries' food and the tanner's hides. */
	@GameTest(batch = "market", template = "home_platform")
	public static void everyTradeSellsItsSurplusAndKeepsWhatItWorksWith(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shops.Shop shop = stall(helper);
		BlockPos far = helper.absolutePos(BELL).offset(200, 0, 0);
		Villager fletcher = adult(helper, 0, "fletcher", 20L);
		com.vnap.village.workshop.WorkshopState bows = new com.vnap.village.workshop.WorkshopState("fletcher", far, far,
				net.minecraft.world.level.block.Rotation.NONE, 3);
		bows.tier = 3;
		net.minecraft.world.SimpleContainer quiver = new net.minecraft.world.SimpleContainer(27);
		quiver.addItem(new ItemStack(Items.ARROW, 40));
		quiver.addItem(new ItemStack(Items.FLINT, 10));
		ItemStack worn = new ItemStack(Items.IRON_AXE);
		worn.setDamageValue(100);
		quiver.addItem(worn);
		quiver.addItem(new ItemStack(Items.FEATHER, 40));
		Map<Item, Integer> sale = com.vnap.village.market.Produce.forSale(level, bows, quiver);
		Checks.assertTrue(helper, Integer.valueOf(40).equals(sale.get(Items.ARROW)) && Integer.valueOf(2).equals(sale.get(Items.FLINT))
				&& !sale.containsKey(Items.IRON_AXE) && Integer.valueOf(8).equals(sale.get(Items.FEATHER)),
				"arrows sold, flint over its supply and feathers over its stock sold, a worn tool kept: " + sale);
		com.vnap.village.workshop.WorkshopState field = new com.vnap.village.workshop.WorkshopState("farmer", far, far,
				net.minecraft.world.level.block.Rotation.NONE, 0);
		net.minecraft.world.SimpleContainer barn = new net.minecraft.world.SimpleContainer(27);
		barn.addItem(new ItemStack(Items.IRON_HOE));
		barn.addItem(new ItemStack(Items.BEETROOT_SEEDS, 20));
		Checks.assertTrue(helper, com.vnap.village.market.Produce.forSale(level, field, barn).isEmpty(),
				"the farmer keeps the hoe it was given and the seed it sows");
		int before = shop.count(Items.ARROW);
		int purse = Family.state(fletcher).coins;
		int paid = com.vnap.village.market.Produce.sell(level, fletcher, shop, bows, quiver, com.vnap.village.market.Produce.CART);
		Checks.assertTrue(helper, paid > 0 && Family.state(fletcher).coins == purse + paid, "the fletcher is paid");
		Checks.assertTrue(helper, shop.count(Items.ARROW) == before + 40 - quiver.countItem(Items.ARROW) && quiver.countItem(Items.FLINT) == 8,
				"the arrows go on the shelf, the supply stays");

		com.vnap.village.workshop.WorkshopState smoker = new com.vnap.village.workshop.WorkshopState("butcher", far, far,
				net.minecraft.world.level.block.Rotation.NONE, 3);
		smoker.tier = 3;
		net.minecraft.world.SimpleContainer meat = new net.minecraft.world.SimpleContainer(27);
		meat.addItem(new ItemStack(Items.COOKED_BEEF, 40));
		meat.addItem(new ItemStack(Items.ROTTEN_FLESH, 10));
		meat.addItem(new ItemStack(Items.WHEAT, 20));
		sale = com.vnap.village.market.Produce.forSale(level, smoker, meat);
		Checks.assertTrue(helper, Integer.valueOf(24).equals(sale.get(Items.COOKED_BEEF)) && Integer.valueOf(12).equals(sale.get(Items.WHEAT))
				&& !Integer.valueOf(10).equals(sale.get(Items.ROTTEN_FLESH)), "food for the pantries, wheat for the herd and hides kept: " + sale);

		for (Item product : List.of(Items.ARROW, Items.BOOK, Items.FILLED_MAP, Items.STONE_BRICKS, Items.LEATHER, Items.WHITE_WOOL,
				Items.COD, Items.COOKED_BEEF, Items.OAK_LOG, Items.IRON_SWORD, Items.FEATHER)) {
			Checks.assertTrue(helper, Prices.price(new ItemStack(product)) > 0, "what a trade makes has a price at the stall: " + product);
		}

		// a birth: the mayor gives the household a little (the cradle comes off it)
		Villager mother = adult(helper, 2, "none", 24L);
		int gifted = Market.office(level, shop).treasury;
		Market.birthGift(level, mother, TownRules.BIRTH_GIFT);
		Checks.assertTrue(helper, Family.state(mother).coins == TownRules.BIRTH_GIFT
				&& Market.office(level, shop).treasury == gifted - TownRules.BIRTH_GIFT, "the mayor's gift for the newborn");

		// livestock from the drover: the household pays what it has, the treasury the rest
		Villager herder = adult(helper, 1, "butcher", 22L);
		Family.state(herder).coins = 10;
		int treasury = Market.office(level, shop).treasury;
		Market.buyAbroad(level, herder, shop, 32);
		Checks.assertTrue(helper, Family.state(herder).coins == 0 && Market.office(level, shop).treasury == treasury - 22,
				"the pair costs the household 10 and the treasury 22");
		close(level, shop);
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform", timeoutTicks = 100)
	public static void aPlayersOrderComesWithTheCaravanIntoTheirParcelBox(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shops.Shop shop = stall(helper);
		BlockPos counter = helper.absolutePos(BELL.offset(3, 0, 0));
		helper.setBlock(BELL.offset(3, 0, 0), net.minecraft.world.level.block.Blocks.BARREL);
		shop.counter = counter;
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		Coins.give(player, 64 * 20);
		int before = Coins.count(player.getInventory());
		ItemStack sample = new ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
		int unit = com.vnap.village.market.Orders.unit(level, counter, sample);
		Checks.assertTrue(helper, unit >= Prices.price(sample), "an order costs more than the stall's own price: " + unit);
		Checks.assertTrue(helper, com.vnap.village.market.Orders.place(level, shop, player, sample, 70, counter), "the order is placed");
		int paid = before - Coins.count(player.getInventory());
		Checks.assertTrue(helper, paid == com.vnap.village.market.Orders.total(unit, 70), "paid at once, with the carriage: " + paid);
		// taken back: all of it returned
		Checks.assertTrue(helper, com.vnap.village.market.Orders.cancel(level, shop, player, 0) && Coins.count(player.getInventory()) == before, "a cancelled order is all returned");
		// a shulker box full of things is ordered empty: an order is of the thing, not of the sample's contents
		ItemStack box = new ItemStack(net.minecraft.world.item.Items.SHULKER_BOX);
		box.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(
				List.of(new ItemStack(net.minecraft.world.item.Items.DIAMOND_BLOCK, 64))));
		Checks.assertTrue(helper, net.minecraft.world.item.component.ItemContainerContents.EMPTY.equals(com.vnap.village.market.Orders.sample(box)
				.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY)), "the sample's contents are not ordered");
		Checks.assertTrue(helper, com.vnap.village.market.Orders.unit(level, counter, new ItemStack(net.minecraft.world.item.Items.SPAWNER)) == 0, "a spawner cannot be ordered");
		com.vnap.village.market.Orders.place(level, shop, player, sample, 70, counter);
		Caravan.arrive(level, shop, Family.day(level));
		BlockPos parcel = shop.parcels.get(player.getUUID());
		if (parcel == null || !(level.getBlockEntity(parcel) instanceof com.vnap.block.town.ParcelBoxBlockEntity parcels)) {
			Checks.fail(helper, "the keeper sets a parcel box down by the counter");
			return;
		}
		int irons = 0;
		for (int i = 0; i < parcels.getContainerSize(); i++) if (parcels.getItem(i).is(net.minecraft.world.item.Items.IRON_INGOT)) irons += parcels.getItem(i).getCount();
		Checks.assertTrue(helper, irons == 70 && shop.playerOrders.isEmpty(), "all of the order in the box: " + irons);
		Checks.assertTrue(helper, parcel.closerThan(counter, 7), "the box stands by the counter: " + parcel.toShortString());
		Player other = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		Checks.assertTrue(helper, !parcels.stillValid(other), "nobody else opens it");
		level.removeBlock(parcel, false);
		close(level, shop);
		helper.succeed();
	}

	/** An open, stocked stall of this test's village (removed again by {@link #close}). */
	private static Shops.Shop stall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shops.Shop shop = new Shops.Shop(level.dimension(), helper.absolutePos(BELL));
		Shops.get(level.getServer()).shops().add(shop);
		Market.openShop(level, shop, Family.day(level));
		return shop;
	}

	private static void close(ServerLevel level, Shops.Shop shop) {
		Shops.get(level.getServer()).shops().remove(shop);
		Mayors.get(level.getServer()).offices().removeIf(office -> office.bell.equals(shop.bell));
	}

	/** A grown-up of the bell's village with a home, a trade (or "none"), born on {@code born}. */
	private static Villager adult(GameTestHelper helper, int index, String profession, long born) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, BELL.offset(-4 + index, 0, 2));
		villager.setNoAi(true);
		VillagerProfession trade = BuiltInRegistries.VILLAGER_PROFESSION.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(profession));
		villager.setVillagerData(com.vnap.Mc.withProfession(villager.getVillagerData(), trade));
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), helper.absolutePos(BELL)));
		((VillagerLifeData) villager).vnap$setFemale(false);
		FamilyState family = Family.state(villager);
		family.genderKnown = true;
		family.bornDay = born;
		family.widowedDay = Long.MIN_VALUE / 2;
		family.home = helper.absolutePos(BELL.offset(index, 0, -6));
		return villager;
	}

	private static Map<BlockPos, List<Float>> mood(ServerLevel level, BlockPos bell) {
		Map<BlockPos, List<Float>> out = new HashMap<>();
		for (MoodState state : VillageMoodSavedData.get(level).all()) {
			if (state.meetingPos.closerThan(bell, 96.0)) out.put(state.meetingPos, List.of(state.mood, state.standing));
		}
		return out;
	}
}
