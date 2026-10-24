package com.vnap.gametest;

import com.vnap.village.town.TownRules;
import com.vnap.dialogue.Family;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.Hamlets;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
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

import java.util.ArrayList;
import java.util.List;

/** A village as a town: who lives there (loaded or not), how far it reaches, neighbours, empty houses, hamlets. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class TownGameTests {
	private TownGameTests() {
	}

	@GameTest(template = "platform")
	public static void aTownKeepsCountOfPeopleOutOfSight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		try {
			long day = Family.day(level);
			List<Villager> people = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				Villager villager = resident(helper, level, bell, 45 + i * 2, 45);
				Towns.seen(level, villager, day);
				people.add(villager);
			}
			Checks.assertTrue(helper, Towns.population(level, bell) == 5, "five people: " + Towns.population(level, bell));
			// three walk out of the loaded land (the test removes them the way unloading does)
			for (int i = 0; i < 3; i++) people.get(i).discard();
			Checks.assertTrue(helper, Towns.population(level, bell) == 5, "those out of sight still live here: " + Towns.population(level, bell));
			Towns.forget(level, people.get(0).getUUID());
			Checks.assertTrue(helper, Towns.population(level, bell) == 4, "one who died or left is not: " + Towns.population(level, bell));
			// days pass while the village ticks and the two out of sight never come back
			Towns.seen(level, people.get(3), day + TownRules.GONE_DAYS + 1);
			Towns.seen(level, people.get(4), day + TownRules.GONE_DAYS + 1);
			Checks.assertTrue(helper, Towns.population(level, bell) == 2, "the missing are struck off after a while: " + Towns.population(level, bell));
		} finally {
			Mayors.get(level.getServer()).offices().remove(office);
		}
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aNeighbouringVillageIsNotCountedAndTheLandIsSplit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// 70 blocks apart: two villages (TownRules.SPAN is 64)
		BlockPos a = helper.absolutePos(new BlockPos(15, 2, 50));
		BlockPos b = helper.absolutePos(new BlockPos(85, 2, 50));
		Mayors mayors = Mayors.get(level.getServer());
		Mayors.Office officeA = mayors.officeFor(level.dimension(), a);
		Mayors.Office officeB = mayors.officeFor(level.dimension(), b);
		try {
			long day = Family.day(level);
			for (int i = 0; i < 3; i++) Towns.seen(level, resident(helper, level, a, 12 + i * 2, 45), day);
			// B's people stand nearer A than the old count's 48 blocks
			for (int i = 0; i < 4; i++) Towns.seen(level, resident(helper, level, b, 55 + i * 2, 45), day);
			Checks.assertTrue(helper, officeA != officeB, "two offices");
			Checks.assertTrue(helper, Towns.population(level, a) == 3, "A counts its own three: " + Towns.population(level, a));
			Checks.assertTrue(helper, Towns.population(level, b) == 4, "B its four: " + Towns.population(level, b));
			Checks.assertTrue(helper, Towns.reach(level, a) <= 33.0, "A reaches half way to B at most: " + Towns.reach(level, a));
			Checks.assertTrue(helper, Towns.ours(level, a, helper.absolutePos(new BlockPos(25, 2, 50))), "near A is A's");
			Checks.assertFalse(helper, Towns.ours(level, a, helper.absolutePos(new BlockPos(55, 2, 50))), "past half way is B's");
			Checks.assertTrue(helper, Towns.ours(level, b, helper.absolutePos(new BlockPos(55, 2, 50))), "and B has it");
		} finally {
			mayors.offices().remove(officeA);
			mayors.offices().remove(officeB);
		}
		helper.succeed();
	}

	/** Towns in a row, the middle one loaded and the others not: the land is split by the register alone. */
	@GameTest(template = "platform")
	public static void threeTownsInARowShareTheLandWithoutLoadingIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos b = helper.absolutePos(new BlockPos(50, 2, 50));
		BlockPos a = b.offset(-2000, 0, 0);
		BlockPos c = b.offset(2000, 0, 0);
		BlockPos aNear = b.offset(-70, 0, 0);
		BlockPos cNear = b.offset(140, 0, 0);
		Mayors mayors = Mayors.get(level.getServer());
		List<Mayors.Office> offices = List.of(mayors.officeFor(level.dimension(), aNear), mayors.officeFor(level.dimension(), b),
				mayors.officeFor(level.dimension(), cNear));
		try {
			boolean aLoaded = level.hasChunk(a.getX() >> 4, a.getZ() >> 4);
			boolean cLoaded = level.hasChunk(c.getX() >> 4, c.getZ() >> 4);
			Checks.assertTrue(helper, offices.get(0) != offices.get(1) && offices.get(1) != offices.get(2), "three towns, none merged");
			Checks.assertTrue(helper, Towns.reach(level, b) <= 33.0, "the middle town reaches half way to the nearer: " + Towns.reach(level, b));
			Checks.assertFalse(helper, Towns.ours(level, b, b.offset(-40, 0, 0)), "past half way to A is A's");
			Checks.assertTrue(helper, Towns.ours(level, b, b.offset(30, 0, 0)), "C is further: this is still B's");
			Checks.assertTrue(helper, Towns.ours(level, cNear, b.offset(100, 0, 0)), "and past half way to C is C's");
			Towns.population(level, a);
			Towns.reach(level, c);
			Checks.assertTrue(helper, level.hasChunk(a.getX() >> 4, a.getZ() >> 4) == aLoaded
					&& level.hasChunk(c.getX() >> 4, c.getZ() >> 4) == cLoaded, "working the land out loads no chunk");
		} finally {
			mayors.offices().removeAll(offices);
		}
		helper.succeed();
	}

	@GameTest(batch = "homes", template = "home_platform", timeoutTicks = 100)
	public static void aHomelessVillagerMovesIntoAnEmptyHouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(50, 2, 50), Blocks.BELL);
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		long day = Family.day(level);
		Villager owner = homeless(helper, level, bell, day);
		HouseProject house = HomeProjects.start(level, owner, day);
		if (house == null) {
			Checks.fail(helper, "a site for the house");
			return;
		}
		BuildJob job = house.job(level);
		job.site = null;
		while (!job.done()) {
			job.skipDone(level);
			job.placeNext(level, null);
		}
		HomeProjects.finish(level, house);
		BlockPos bed = house.bed(level);
		Checks.assertTrue(helper, bed != null && bed.equals(((VillagerLifeData) owner).vnap$homeBed()), "the owner lives in it");
		// the owner dies alone: the house stands empty
		com.vnap.Mc.kill(owner);
		helper.runAfterDelay(2, () -> {
			Villager newcomer = homeless(helper, level, bell, day);
			HomeProjects.consider(level, newcomer, day + 1);
			BlockPos home = ((VillagerLifeData) newcomer).vnap$homeBed();
			Checks.assertTrue(helper, bed != null && bed.equals(home), "the homeless one moves into the empty house, not a new one: " + home);
			Checks.assertTrue(helper, VillageMoodSavedData.get(level).houses().stream().noneMatch(h -> !h.done && h.village == bell.asLong()),
					"no new house is started");
			VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
			Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
			if (office != null) Mayors.get(level.getServer()).offices().remove(office);
			helper.succeed();
		});
	}

	@GameTest(template = "platform")
	public static void aHamletStandsWellAwayFromEveryTown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		Mayors mayors = Mayors.get(level.getServer());
		// another town 200 blocks east: the hamlet goes elsewhere
		List<Mayors.Office> offices = List.of(mayors.officeFor(level.dimension(), bell), mayors.officeFor(level.dimension(), bell.offset(200, 0, 0)));
		Villager settler = resident(helper, level, bell, 60, 50);
		Hamlets.Settlement settlement = Hamlets.depart(level, List.of(settler), bell);
		try {
			Checks.assertTrue(helper, settlement != null, "the settler sets off");
			if (settlement == null) return;
			for (Mayors.Office office : offices) {
				Checks.assertTrue(helper, !office.bell.closerThan(settlement.pos(), TownRules.MAX_REACH * 2),
						"the hamlet keeps two towns' reach from " + office.bell.toShortString() + ": " + settlement.pos().toShortString());
			}
		} finally {
			if (settlement != null) Hamlets.get(level.getServer()).pending().remove(settlement);
			mayors.offices().removeAll(offices);
		}
		helper.succeed();
	}

	private static Villager resident(GameTestHelper helper, ServerLevel level, BlockPos bell, int x, int z) {
		Villager villager = helper.spawn(EntityType.VILLAGER, x, 2, z);
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		return villager;
	}

	private static Villager homeless(GameTestHelper helper, ServerLevel level, BlockPos bell, long day) {
		Villager villager = helper.spawn(EntityType.VILLAGER, 52, 2, 52);
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		villager.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, day - 5);
		return villager;
	}
}
