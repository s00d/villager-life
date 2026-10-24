package com.vnap.gametest;

import com.vnap.Nbt;
import com.vnap.village.town.TownRules;
import com.vnap.block.LifeBlocks;
import com.vnap.block.town.MailboxBlock;
import com.vnap.dialogue.Family;
import com.vnap.item.LifeItems;
import com.vnap.mood.MoodConfig;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.network.NoticeBoardPayload;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Coins;
import com.vnap.village.market.Market;
import com.vnap.village.market.Prices;
import com.vnap.village.market.ShopMenu;
import com.vnap.village.market.Shops;
import com.vnap.village.post.Letters;
import com.vnap.village.post.Post;
import com.vnap.village.town.Bakehouse;
import com.vnap.village.town.NoticeBoard;
import com.vnap.village.town.Roads;
import com.vnap.village.town.TownPlots;
import com.vnap.village.town.Watch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The town's life: its buildings kept and built as it grows, the bakehouse's bread, the notice board, the post, a
 * house bought with a deed, the night watch, the fair's better prices.
 */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class TownLifeGameTests {
	private static final BlockPos BELL = new BlockPos(50, 2, 50);

	private TownLifeGameTests() {
	}

	private static BlockPos bell(GameTestHelper helper) {
		helper.setBlock(BELL, Blocks.BELL);
		return helper.absolutePos(BELL);
	}

	private static Shops.Shop stall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shops.Shop shop = new Shops.Shop(level.dimension(), helper.absolutePos(BELL));
		Shops.get(level.getServer()).shops().add(shop);
		Market.openShop(level, shop, Family.day(level));
		return shop;
	}

	private static void close(ServerLevel level, BlockPos bell) {
		Shops.get(level.getServer()).shops().removeIf(shop -> shop.bell.equals(bell));
		Mayors.get(level.getServer()).offices().removeIf(office -> office.bell.closerThan(bell, TownRules.SPAN));
		Roads.get(level.getServer()).networks().removeIf(network -> network.bell.closerThan(bell, TownRules.SPAN));
	}

	private static Roads.Network network(ServerLevel level, BlockPos bell) {
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Roads roads = Roads.get(level.getServer());
		roads.networks().removeIf(old -> old.bell.closerThan(office.bell, TownRules.SPAN));
		return roads.of(level.dimension(), office.bell);
	}

	@GameTest(batch = "roads", template = "home_platform", timeoutTicks = 200)
	public static void aTownKeepsItsBuildingsGroundEarlyAndBuildsThemAsItGrows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Roads.Network network = network(level, bell);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Roads roads = Roads.get(level.getServer());
		int max = MoodConfig.maxVillagers();
		try {
			long day = Family.day(level);
			for (int i = 0; i < max / 6 + 1; i++) office.residents.put(UUID.randomUUID(), day);
			roads.plan(level, network, day, 0);
			TownPlots.Plot bakehouse = TownPlots.plot(network, TownPlots.Kind.BAKEHOUSE);
			Checks.assertTrue(helper, TownPlots.plot(network, TownPlots.Kind.CHAPEL) != null && bakehouse != null && !bakehouse.queued,
					"a sixth full: the chapel's and the bakehouse's ground kept, nothing built yet");
			TownPlots.Plot early = TownPlots.plot(network, TownPlots.Kind.TAVERN);
			Checks.assertTrue(helper, early != null && !early.queued, "the tavern's ground kept from the first day as well, not built");
			for (int i = 0; i < max / 3; i++) office.residents.put(UUID.randomUUID(), day);
			roads.plan(level, network, day + 1, 0);
			bakehouse = TownPlots.plot(network, TownPlots.Kind.BAKEHOUSE);
			Checks.assertTrue(helper, bakehouse != null && bakehouse.queued, "a third full: the bakehouse goes up");
			for (int i = 0; i < max / 3; i++) office.residents.put(UUID.randomUUID(), day);
			roads.plan(level, network, day + 2, 0);
			TownPlots.Plot tavern = TownPlots.plot(network, TownPlots.Kind.TAVERN);
			Checks.assertTrue(helper, tavern != null && tavern.queued, "two thirds full: the tavern goes up as well");
			Checks.assertTrue(helper, network.segments.stream().anyMatch(segment -> segment.label.equals("tavern")), "on the notice board as the tavern");
			// built: its stretch laid, the bakehouse bakes for the town
			for (Roads.Segment segment : List.copyOf(network.segments)) {
				BuildJob job = segment.job();
				job.site = null;
				while (!job.done()) {
					job.skipDone(level);
					if (!job.done()) job.placeNext(level, null);
				}
				roads.finish(level, segment);
			}
			Checks.assertTrue(helper, TownPlots.built(network, TownPlots.Kind.BAKEHOUSE) && Bakehouse.baker(network) != null, "the bakehouse stands");
		} finally {
			close(level, bell);
		}
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void theBakehouseBakesTheStallsWheatAndTheTownStopsBuyingBread(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		try {
			Shops.Shop shop = stall(helper);
			shop.stock.removeIf(lot -> lot.item.is(Items.BREAD) || lot.item.is(Items.WHEAT));
			shop.put(new ItemStack(Items.WHEAT), 30, false);
			int loaves = Bakehouse.batch(shop);
			Checks.assertTrue(helper, loaves == Bakehouse.BATCH, "a batch of " + Bakehouse.BATCH + " from 30 wheat: " + loaves);
			Mayors.Office office = Market.office(level, shop);
			int treasury = office.treasury;
			Checks.assertTrue(helper, Bakehouse.takeWheat(level, shop, loaves) && shop.count(Items.WHEAT) == 30 - loaves * Bakehouse.WHEAT
					&& office.treasury < treasury, "the wheat off the shelf, the treasury paying");
			Bakehouse.sellBread(level, shop, loaves);
			Checks.assertTrue(helper, shop.count(Items.BREAD) == loaves, "the bread on the shelf");
			// a town whose bakehouse stands gets no bread from outside
			Roads.Network network = network(level, bell);
			TownPlots.Plot plot = new TownPlots.Plot(bell.offset(10, -1, 10), Rotation.NONE, "plains");
			plot.queued = true;
			network.plots.put(TownPlots.Kind.BAKEHOUSE, plot);
			shop.stock.removeIf(lot -> lot.item.is(Items.BREAD));
			shop.orders.put(Items.BREAD, 10);
			Market.restock(level, shop);
			Checks.assertTrue(helper, shop.count(Items.BREAD) == 0, "no bread delivered: " + shop.count(Items.BREAD));
		} finally {
			close(level, bell);
		}
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void theNoticeBoardPaysInFullForWhatIsWantedAndTakesDonations(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		try {
			Shops.Shop shop = stall(helper);
			Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
			shop.orders.clear();
			shop.orders.put(Items.IRON_INGOT, 5);
			NoticeBoardPayload board = NoticeBoard.read(level, bell);
			Checks.assertTrue(helper, board != null && board.wanted().size() == 1 && board.wanted().get(0).item().getCount() == 5, "the board lists the stall's wants");
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack iron = new ItemStack(Items.IRON_INGOT, 8);
			Checks.assertTrue(helper, NoticeBoard.deliver(level, player, bell, iron), "the iron is taken");
			int price = Prices.price(new ItemStack(Items.IRON_INGOT));
			Checks.assertTrue(helper, iron.getCount() == 3 && Coins.count(player.getInventory()) == 5 * price && !shop.orders.containsKey(Items.IRON_INGOT),
					"five taken, paid in full (" + Coins.count(player.getInventory()) + " of " + 5 * price + "), the order filled");
			Mayors.Office office = Market.office(level, shop);
			int treasury = office.treasury;
			ItemStack coins = new ItemStack(LifeItems.SILVER_COIN, 1);
			Checks.assertTrue(helper, NoticeBoard.donate(level, player, bell, coins) && coins.isEmpty() && office.treasury == treasury + Coins.SILVER,
					"a silver coin to the treasury");
			Checks.assertTrue(helper, Nbt.getInt(Nbt.getCompound(player.getPersistentData(), net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG), "vnap_gift_copper") >= Coins.SILVER,
					"the day's giving is kept with the player (a restart is no new day of it): " + player.getPersistentData());
		} finally {
			close(level, bell);
		}
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aLetterWaitsInTheMailboxForItsOwner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.setBlock(new BlockPos(10, 2, 10), LifeBlocks.MAILBOX.defaultBlockState());
		BlockPos box = helper.absolutePos(new BlockPos(10, 2, 10));
		Post.own(level, player, box);
		Letters.to(level, player.getUUID(), "Anna", "birth", "Tim", "Bob");
		Checks.assertTrue(helper, level.getBlockState(box).getValue(MailboxBlock.FLAG), "the flag is up");
		Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
		Checks.assertFalse(helper, Post.collect(level, stranger, box), "not someone else's to take");
		Checks.assertTrue(helper, Post.collect(level, player, box) && !level.getBlockState(box).getValue(MailboxBlock.FLAG), "taken out, the flag down");
		boolean letter = false;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			letter |= stack.is(Items.WRITTEN_BOOK) && stack.has(DataComponents.WRITTEN_BOOK_CONTENT);
		}
		Checks.assertTrue(helper, letter, "a letter in hand");
		helper.succeed();
	}

	@GameTest(batch = "homes", template = "home_platform", timeoutTicks = 100)
	public static void aTownMapIsCentredOnTheBellAndDrawnAtOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		com.vnap.Mc.snap(player, Vec3.atCenterOf(bell.above()));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LifeItems.TOWN_MAP));
		//? if >=1.21.2 {
		/*ItemStack map = LifeItems.TOWN_MAP.use(level, player, InteractionHand.MAIN_HAND) instanceof net.minecraft.world.InteractionResult.Success used
				&& used.heldItemTransformedTo() != null ? used.heldItemTransformedTo() : ItemStack.EMPTY;
		*///?} else
		ItemStack map = LifeItems.TOWN_MAP.use(level, player, InteractionHand.MAIN_HAND).getObject();
		var data = net.minecraft.world.item.MapItem.getSavedData(map, level);
		Checks.assertTrue(helper, data != null && data.centerX == bell.getX() && data.centerZ == bell.getZ(), "centred on the bell, not on the map grid");
		Checks.assertTrue(helper, data != null && data.colors[64 * 128 + 64] != 0, "drawn at once round the bell");
		helper.succeed();
	}

	@GameTest(batch = "homes", template = "home_platform", timeoutTicks = 200)
	public static void aDeedBuysAnEmptyHouseForThePlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		HouseProject house = new HouseProject(bell.asLong(), bell, UUID.randomUUID(), "cottage", "plains", helper.absolutePos(new BlockPos(30, 1, 30)),
				Rotation.NONE, Family.day(level));
		try {
			BuildJob job = house.job(level);
			job.site = null;
			while (!job.done()) {
				job.skipDone(level);
				if (!job.done()) job.placeNext(level, null);
			}
			house.done = true;
			VillageMoodSavedData.get(level).houses().add(house);
			Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
			Checks.assertTrue(helper, HomeProjects.emptyHouses(level, bell).contains(house), "an empty house of the village");
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack deed = new ItemStack(LifeItems.HOUSE_DEED);
			player.setItemInHand(InteractionHand.MAIN_HAND, deed);
			Coins.give(player, 64 * Coins.SILVER);
			BlockPos inside = house.footprint(level).getCenter();
			var result = deed.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(inside), Direction.UP, inside, false)));
			Checks.assertTrue(helper, player.getUUID().equals(house.player), "the house is the player's: " + result + " coins " + Coins.count(player.getInventory())
					+ " inside " + house.footprint(level).isInside(inside) + " empty " + HomeProjects.emptyHouses(level, bell).contains(house));
			Checks.assertTrue(helper, Coins.count(player.getInventory()) < 64 * Coins.SILVER, "paid for");
			Checks.assertFalse(helper, HomeProjects.emptyHouses(level, bell).contains(house), "no longer for a villager to move into");
		} finally {
			VillageMoodSavedData.get(level).houses().remove(house);
			close(level, bell);
		}
		helper.succeed();
	}

	@GameTest(batch = "watch", template = "home_platform", timeoutTicks = 100)
	public static void aWalledTownKeepsANightWatchOfTwo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		long time = com.vnap.Mc.dayTime(level);
		try {
			Roads.Network network = network(level, bell);
			network.wall = 1;
			network.turrets.add(bell.offset(20, 5, 0));
			Villager[] people = new Villager[4];
			for (int i = 0; i < people.length; i++) {
				people[i] = helper.spawn(EntityType.VILLAGER, BELL.offset(-3 + 2 * i, 0, 3));
				people[i].getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
			}
			com.vnap.Mc.setDayTime(level, Family.day(level) * 24000L + 9000L);
			int day = 0;
			for (Villager villager : people) day += Watch.onDuty(level, villager) ? 1 : 0;
			Checks.assertTrue(helper, day == 0, "nobody keeps watch by day");
			com.vnap.Mc.setDayTime(level, Family.day(level) * 24000L + 15000L);
			int night = 0;
			for (Villager villager : people) night += Watch.onDuty(level, villager) ? 1 : 0;
			Checks.assertTrue(helper, night == 2, "two keep watch at night: " + night);
		} finally {
			com.vnap.Mc.setDayTime(level, time);
			close(level, bell);
		}
		helper.succeed();
	}

	@GameTest(batch = "market", template = "platform")
	public static void onAFairDayTheStallPaysMore(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		try {
			Shops.Shop shop = stall(helper);
			shop.till = 64 * Coins.SILVER;
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			shop.fairDay = Family.day(level) - 3;
			ShopMenu.sell(level, shop, player, new ItemStack(Items.IRON_INGOT, 16), bell);
			int plain = Coins.count(player.getInventory());
			shop.fairDay = Family.day(level);
			ShopMenu.sell(level, shop, player, new ItemStack(Items.IRON_INGOT, 16), bell);
			int fair = Coins.count(player.getInventory()) - plain;
			Checks.assertTrue(helper, plain > 0 && fair > plain, "more on the fair day: " + plain + " then " + fair);
			Checks.assertTrue(helper, fair < Prices.price(new ItemStack(Items.IRON_INGOT)) * 16 * 0.8F, "still under what it buys at");
		} finally {
			close(level, bell);
		}
		helper.succeed();
	}
}
