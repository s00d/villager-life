package com.vnap.gametest;

import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Housing;
import com.vnap.village.market.Market;
import com.vnap.village.town.TownRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** A home is bought (Housing): a down payment, a loan never over the cap, the mayor's help for the long homeless, founders by right. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class HousingGameTests {
	private static final BlockPos BELL = new BlockPos(40, 2, 40);

	private HousingGameTests() {
	}

	@GameTest(batch = "housing", template = "home_platform", timeoutTicks = 200)
	public static void aHomelessVillagerBuysAnEmptyHouseOnADownPayment(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		HouseProject house = emptyHouse(helper, bell);
		if (house == null) return;
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		int treasury = Math.max(0, office.treasury);
		Villager buyer = adult(helper, 2);
		Family.state(buyer).coins = 60;
		long day = Family.day(level);
		int price = Housing.price(level, house);
		Housing.daily(level, buyer, bell, day, day);
		Checks.assertTrue(helper, HomeProjects.claimed(buyer) && buyer.getUUID().equals(house.owner), "it buys the empty house: " + Housing.lastWhy);
		int own = 60 - TownRules.RESERVE;
		Checks.assertTrue(helper, Family.state(buyer).coins == TownRules.RESERVE && Market.debt(level, buyer) == Math.min(TownRules.LOAN_CAP, price - own)
				&& office.treasury == treasury + own, "its coins down, the rest a loan under the cap: price " + price + ", owes " + Market.debt(level, buyer));
		cleanUp(level, house, office);
		helper.succeed();
	}

	@GameTest(batch = "housing", template = "home_platform", timeoutTicks = 200)
	public static void aHomelessVillagerWithNothingSavedGetsTheDownPaymentFromTheMayor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		HouseProject house = emptyHouse(helper, bell);
		if (house == null) return;
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Villager buyer = adult(helper, 2);
		long day = Family.day(level);
		Housing.daily(level, buyer, bell, day, day);
		Checks.assertFalse(helper, HomeProjects.claimed(buyer), "with no coins it saves first: " + Housing.lastWhy);
		Housing.daily(level, buyer, bell, day + TownRules.HOMELESS_GRACE, day);
		Checks.assertTrue(helper, HomeProjects.claimed(buyer), "homeless long enough, the mayor pays the down payment: " + Housing.lastWhy);
		Checks.assertTrue(helper, Market.debt(level, buyer) <= TownRules.LOAN_CAP, "and the loan stays under the cap: " + Market.debt(level, buyer));
		cleanUp(level, house, office);
		helper.succeed();
	}

	@GameTest(batch = "housing", template = "home_platform", timeoutTicks = 200)
	public static void onlyAFounderTakesAHouseForNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		HouseProject house = emptyHouse(helper, bell);
		if (house == null) return;
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		BlockPos bed = house.bed(level);
		if (bed == null) {
			Checks.fail(helper, "the cottage has a bed");
			return;
		}
		Villager lodger = helper.spawn(EntityType.VILLAGER, helper.relativePos(bed).above());
		lodger.setNoAi(true);
		lodger.teleportTo(bed.getX() + 0.5, bed.getY() + 1, bed.getZ() + 0.5);
		lodger.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		VillagerHome.tick(level, lodger, Family.day(level));
		Checks.assertFalse(helper, HomeProjects.claimed(lodger), "a villager of the town only lodges by a free bed");
		lodger.getPersistentData().putBoolean(Housing.FOUNDER, true);
		// a chunk scans for beds a few times a tick only
		helper.runAfterDelay(2, () -> {
			VillagerHome.tick(level, lodger, Family.day(level) + 1);
			Checks.assertTrue(helper, HomeProjects.claimed(lodger) && !Housing.founder(lodger), "a founder takes it by right, once: " + VillagerHome.whyNoHome(level, lodger) + " at " + lodger.blockPosition().toShortString() + ", bed " + bed.toShortString() + " " + level.getBlockState(bed));
			cleanUp(level, house, office);
			helper.succeed();
		});
	}

	@GameTest(batch = "housing", template = "platform")
	public static void aLoanNeverGoesOverTheCap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Villager owner = adult(helper, 0);
		Family.state(owner).debt = TownRules.LOAN_CAP - 10;
		Housing.Split home = Housing.split(level, owner, 500, Housing.Purpose.HOME, true);
		Checks.assertTrue(helper, home != null && home.own() == 0 && home.loan() == 10 && home.town() == 490,
				"a home: ten more on the loan, the town pays the rest: " + home);
		Checks.assertTrue(helper, Housing.split(level, owner, 500, Housing.Purpose.GROWTH, true) == null, "growing takes no town money");
		Checks.assertTrue(helper, Housing.split(level, owner, 500, Housing.Purpose.HOME, false) == null, "no home without the down payment or the mayor's help");
		Family.state(owner).debt = 0;
		Family.state(owner).coins = 30;
		Housing.Split small = Housing.split(level, owner, 20, Housing.Purpose.GROWTH, true);
		Checks.assertTrue(helper, small != null && small.own() == 20 && small.loan() == 0, "what it can pay it pays itself: " + small);
		helper.succeed();
	}

	// ---- setting up -------------------------------------------------------------------------

	private static BlockPos bell(GameTestHelper helper) {
		helper.setBlock(BELL, Blocks.BELL);
		return helper.absolutePos(BELL);
	}

	private static Villager adult(GameTestHelper helper, int index) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, BELL.offset(-4 + index, 0, 2));
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), helper.absolutePos(BELL)));
		((VillagerLifeData) villager).vnap$setFemale(false);
		FamilyState family = Family.state(villager);
		family.genderKnown = true;
		family.bornDay = Family.day(level) - 30;
		return villager;
	}

	/** A cottage its builder finished and left: empty, for sale. */
	private static @Nullable HouseProject emptyHouse(GameTestHelper helper, BlockPos bell) {
		ServerLevel level = helper.getLevel();
		Villager builder = adult(helper, 0);
		long day = Family.day(level);
		builder.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, day - 5);
		HouseProject house = HomeProjects.start(level, builder, day, List.of("cottage"));
		if (house == null) {
			Checks.fail(helper, "a cottage is planned");
			return null;
		}
		HomeProjects.buildNow(level, house);
		Family.leaveForNewHouse(level, builder);
		builder.discard();
		Checks.assertTrue(helper, HomeProjects.emptyHouses(level, bell).contains(house), "the cottage stands empty");
		return house;
	}

	private static void cleanUp(ServerLevel level, HouseProject house, Mayors.Office office) {
		VillageMoodSavedData.get(level).houses().remove(house);
		Mayors.get(level.getServer()).offices().remove(office);
	}
}
