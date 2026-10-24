package com.vnap.gametest;

import com.vnap.Nbt;
import com.vnap.village.town.TownRules;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.DialogueCatalog;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.MoodState;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.Hamlets;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The elected mayor: chosen among the village's grown-ups without a trade, one a village whatever is loaded, replaced
 * when it dies, leaves or stays homeless; it builds its hall, marries and has children like anyone, speaks its own
 * lines and never moves the village's mood.
 */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class MayorGameTests {
	private static final BlockPos BELL = new BlockPos(50, 2, 50);
	private static final long DAY = 100L;

	@GameTest(batch = "mayors", template = "home_platform")
	public static void theEldestWithoutATradeIsElected(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Map<BlockPos, List<Float>> mood = mood(level, bell);
		Villager farmer = adult(helper, 0, "farmer", 10L);
		Villager nitwit = adult(helper, 1, "nitwit", 5L);
		Villager young = adult(helper, 2, "none", 60L);
		Villager eldest = adult(helper, 3, "none", 40L);
		Villager child = adult(helper, 5, "none", 0L);
		child.setAge(-24000);
		Mayors.daily(level, young, DAY);
		Checks.assertTrue(helper, Mayors.isMayor(eldest), "the eldest grown-up without a trade is elected");
		for (Villager other : List.of(farmer, nitwit, young, child)) {
			Checks.assertFalse(helper, Mayors.isMayor(other), VillagerHome.nameOf(other) + " is not: a trade, a nitwit, the younger or a child");
		}
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
		Checks.assertTrue(helper, office != null && eldest.getUUID().equals(office.mayor), "the office holds the mayor");
		eldest.setVillagerData(com.vnap.Mc.withProfession(eldest.getVillagerData(), VillagerProfession.FARMER));
		Checks.assertTrue(helper, com.vnap.Mc.is(eldest.getVillagerData(), VillagerProfession.NONE), "the mayor takes no trade");
		Checks.assertTrue(helper, mood.equals(mood(level, bell)), "an election does not move the village's mood");
		helper.succeed();
	}

	@GameTest(batch = "mayors", template = "home_platform")
	public static void allAtWorkTheEldestWorkerIsElected(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		List<Villager> workers = new ArrayList<>();
		for (int i = 0; i < 4; i++) workers.add(adult(helper, i, "farmer", 10L + i));
		Mayors.daily(level, workers.get(1), DAY);
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
		Checks.assertTrue(helper, office != null && office.lastElection == DAY && Mayors.isMayor(workers.get(0)),
				"a village where all have a trade elects its eldest worker");
		Checks.assertTrue(helper, com.vnap.Mc.is(workers.get(0).getVillagerData(), VillagerProfession.NONE), "who leaves the trade for the office");

		BlockPos far = helper.absolutePos(new BlockPos(10, 2, 90));
		helper.setBlock(new BlockPos(10, 2, 90), Blocks.BELL);
		List<Villager> hamlet = new ArrayList<>();
		for (int i = 0; i < TownRules.MAYOR_MIN_ADULTS - 1; i++) {
			Villager settler = adult(helper, 20 + i, "none", 5L);
			settler.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), far));
			hamlet.add(settler);
		}
		Mayors.daily(level, hamlet.get(0), DAY);
		Checks.assertTrue(helper, hamlet.stream().noneMatch(Mayors::isMayor), "a hamlet of " + hamlet.size() + " grown-ups has no mayor yet");
		helper.succeed();
	}

	@GameTest(batch = "mayors", template = "home_platform")
	public static void oneMayorAVillageWhateverIsLoaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Mayors mayors = Mayors.get(level.getServer());
		// a mayor elected earlier stands in an unloaded chunk: the village waits for it
		Mayors.Office office = mayors.officeFor(level.dimension(), bell);
		UUID away = UUID.randomUUID();
		office.mayor = away;
		office.name = "Away";
		office.seenDay = DAY;
		List<Villager> people = new ArrayList<>();
		for (int i = 0; i < 5; i++) people.add(adult(helper, i, "none", 10L + i));
		Mayors.daily(level, people.get(1), DAY + 1);
		Checks.assertTrue(helper, people.stream().noneMatch(Mayors::isMayor) && away.equals(office.mayor),
				"a mayor out of loaded chunks keeps the office");
		Mayors.daily(level, people.get(1), DAY + TownRules.GONE_DAYS + 1);
		Checks.assertTrue(helper, Mayors.isMayor(people.get(0)), "one gone for days is replaced");

		// a second bell within the village: the same office
		helper.setBlock(BELL.east(10), Blocks.BELL);
		Villager other = adult(helper, 6, "none", 1L);
		other.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), helper.absolutePos(BELL.east(10))));
		Mayors.daily(level, other, DAY + TownRules.GONE_DAYS + 2);
		Checks.assertFalse(helper, Mayors.isMayor(other), "a second bell in the village elects nobody");

		// the old mayor comes back loaded with its flag: the village keeps the one it has
		Villager back = adult(helper, 7, "none", 0L);
		((VillagerLifeData) back).vnap$setMayor(true);
		Mayors.daily(level, back, DAY + TownRules.GONE_DAYS + 2);
		Checks.assertFalse(helper, Mayors.isMayor(back), "a mayor whose office was filled meanwhile steps down");
		long flagged = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(bell).inflate(48.0), Mayors::isMayor).size();
		Checks.assertTrue(helper, flagged == 1, "one mayor in the village, was " + flagged);
		helper.succeed();
	}

	@GameTest(batch = "mayors", template = "home_platform", timeoutTicks = 200)
	public static void aNewMayorWhenTheOldOneDiesLeavesOrStaysHomeless(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		List<Villager> people = new ArrayList<>();
		for (int i = 0; i < 7; i++) people.add(adult(helper, i, "none", 10L + i));
		Mayors.daily(level, people.get(6), DAY);
		Villager first = people.get(0);
		Checks.assertTrue(helper, Mayors.isMayor(first), "the eldest is elected");
		com.vnap.Mc.kill(first);
		Checks.assertTrue(helper, Mayors.isMayor(people.get(1)), "a mayor's death: the next is elected at once");

		Villager second = people.get(1);
		Hamlets.Settlement settlement = Hamlets.depart(level, List.of(second), bell.offset(3000, 0, 0), false);
		Checks.assertTrue(helper, settlement != null && !Nbt.getBoolean(settlement.settlers().get(0), "VillagerLifeMayor"),
				"a mayor leaving for a hamlet arrives there no mayor");
		if (settlement != null) Hamlets.get(level.getServer()).pending().remove(settlement);
		Villager third = people.get(2);
		Checks.assertTrue(helper, Mayors.isMayor(third), "the village elects another");

		// homeless and no ground anywhere for a hall (a glass ceiling over the whole platform): out after three days
		for (int x = 0; x < 100; x++) {
			for (int z = 0; z < 100; z++) helper.setBlock(new BlockPos(x, 5, z), Blocks.GLASS);
		}
		// and no lot of the plan left either (a lot's leftovers would be cleared for it)
		com.vnap.village.town.Roads.Network network = com.vnap.village.town.Roads.get(level.getServer()).near(level.dimension(), bell);
		if (network != null) {
			network.lots.clear();
			network.streets.clear();
		}
		Mayors.daily(level, third, DAY + 1);
		Mayors.daily(level, third, DAY + 3);
		Checks.assertTrue(helper, Mayors.isMayor(third), "two homeless days are not enough");
		Mayors.daily(level, third, DAY + 4);
		Checks.assertFalse(helper, Mayors.isMayor(third), "three are");
		Checks.assertTrue(helper, Mayors.isMayor(people.get(3)), "and the next eldest takes office");
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		helper.succeed();
	}

	/** The office passes down: to the eldest grown child, else to the spouse (a baby is skipped); then the village elects. */
	@GameTest(batch = "mayors", template = "home_platform")
	public static void theOfficePassesToTheChildThenTheSpouseThenTheVote(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Villager mayor = adult(helper, 0, "none", 10L);
		Villager wife = adult(helper, 1, "farmer", 12L);
		((VillagerLifeData) wife).vnap$setFemale(true);
		Villager son = adult(helper, 2, "farmer", 40L);
		Villager daughter = adult(helper, 3, "none", 45L);
		Villager stranger = adult(helper, 4, "none", 11L);
		Villager baby = adult(helper, 5, "none", 90L);
		baby.setAge(-24000);
		Family.state(mayor).spouse = wife.getUUID();
		Family.state(wife).spouse = mayor.getUUID();
		for (Villager kid : List.of(son, daughter, baby)) {
			Family.state(kid).father = mayor.getUUID();
			Family.state(kid).mother = wife.getUUID();
		}
		Mayors.daily(level, stranger, DAY);
		Checks.assertTrue(helper, Mayors.isMayor(mayor), "the eldest without a trade is elected");
		com.vnap.Mc.kill(mayor);
		Checks.assertTrue(helper, Mayors.isMayor(son) && !Mayors.isMayor(stranger), "the eldest grown child inherits the office, a trade or not");
		Checks.assertTrue(helper, com.vnap.Mc.is(son.getVillagerData(), VillagerProfession.NONE), "and leaves its trade for it");
		Mayors.resign(level, son, DAY + 1, true);
		Checks.assertTrue(helper, com.vnap.Mc.is(son.getVillagerData(), VillagerProfession.FARMER), "out of office it goes back to its trade");
		Checks.assertTrue(helper, Mayors.isMayor(stranger), "the son has no family of his own: the village elects");
		Mayors.resign(level, stranger, DAY + 1, false);
		Checks.assertTrue(helper, Mayors.isMayor(daughter), "the next eldest without a trade, the daughter, is elected");
		helper.succeed();
	}

	@GameTest(batch = "mayors", template = "home_platform")
	public static void aChildlessMayorsWidowTakesTheOffice(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Villager mayor = adult(helper, 0, "none", 10L);
		Villager wife = adult(helper, 1, "farmer", 30L);
		((VillagerLifeData) wife).vnap$setFemale(true);
		Villager baby = adult(helper, 2, "none", 90L);
		baby.setAge(-24000);
		Family.state(baby).father = mayor.getUUID();
		adult(helper, 3, "none", 11L);
		adult(helper, 4, "none", 12L);
		adult(helper, 5, "farmer", 13L);
		Family.state(mayor).spouse = wife.getUUID();
		Family.state(wife).spouse = mayor.getUUID();
		Mayors.daily(level, wife, DAY);
		Checks.assertTrue(helper, Mayors.isMayor(mayor), "elected");
		com.vnap.Mc.kill(mayor);
		Checks.assertTrue(helper, Mayors.isMayor(wife) && !Mayors.isMayor(baby), "no grown child: the widow takes the office");
		Mayors.resign(level, wife, DAY + 1, false);
		Checks.assertFalse(helper, Mayors.isMayor(baby), "stepping down alive the office is not passed down: the village votes");
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), helper.absolutePos(BELL));
		Checks.assertTrue(helper, office != null && office.heir == null && office.mayor != null && !office.mayor.equals(wife.getUUID()),
				"a neighbour is elected");
		helper.succeed();
	}

	/** A widower with only young children: the office waits for the eldest to grow up; no election meanwhile. */
	@GameTest(batch = "mayors", template = "home_platform")
	public static void theOfficeWaitsForAYoungHeir(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Villager mayor = adult(helper, 0, "none", 10L);
		Villager heir = adult(helper, 1, "none", 80L);
		Villager younger = adult(helper, 2, "none", 85L);
		for (Villager kid : List.of(heir, younger)) {
			kid.setAge(-24000);
			Family.state(kid).father = mayor.getUUID();
		}
		List<Villager> neighbours = new ArrayList<>();
		for (int i = 3; i < 7; i++) neighbours.add(adult(helper, i, "none", 20L + i));
		Mayors.daily(level, neighbours.get(0), DAY);
		Checks.assertTrue(helper, Mayors.isMayor(mayor), "elected");
		com.vnap.Mc.kill(mayor);
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
		Checks.assertTrue(helper, office != null && office.mayor == null && heir.getUUID().equals(office.heir),
				"the office waits for the eldest young child");
		Checks.assertTrue(helper, neighbours.stream().noneMatch(Mayors::isMayor), "and nobody is elected meanwhile");
		Mayors.daily(level, heir, DAY + 1);
		Mayors.daily(level, neighbours.get(1), DAY + 2);
		Checks.assertTrue(helper, neighbours.stream().noneMatch(Mayors::isMayor), "not the next day either");
		heir.setAge(0);
		Mayors.daily(level, heir, DAY + 3);
		Checks.assertTrue(helper, Mayors.isMayor(heir) && office != null && office.heir == null, "grown up, the heir takes the office");

		// a second village: the young heir dies before it grows up, the village elects
		Mayors.Office other = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Mayors.resign(level, heir, DAY + 4, false);
		Checks.assertTrue(helper, other.mayor != null && !heir.getUUID().equals(other.mayor), "out of office: a neighbour is elected");
		helper.succeed();
	}

	@GameTest(batch = "mayors", template = "home_platform")
	public static void aDeadYoungHeirLeavesTheOfficeToTheVote(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Villager mayor = adult(helper, 0, "none", 10L);
		Villager heir = adult(helper, 1, "none", 80L);
		heir.setAge(-24000);
		Family.state(heir).father = mayor.getUUID();
		List<Villager> neighbours = new ArrayList<>();
		for (int i = 3; i < 7; i++) neighbours.add(adult(helper, i, "none", 20L + i));
		Mayors.daily(level, neighbours.get(0), DAY);
		com.vnap.Mc.kill(mayor);
		com.vnap.Mc.kill(heir);
		Mayors.daily(level, neighbours.get(1), DAY + 1);
		Checks.assertTrue(helper, Mayors.isMayor(neighbours.get(0)), "the heir died young: the village elects");
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
		Checks.assertTrue(helper, office != null && office.heir == null, "no heir is waited for");
		helper.succeed();
	}

	@GameTest(batch = "mayors", template = "home_platform", timeoutTicks = 200)
	public static void theMayorBuildsItsHallAndBringsItsFamily(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Map<BlockPos, List<Float>> mood = mood(level, bell);
		BlockPos door = new BlockPos(40, 2, 86);
		layHouse(helper, door);
		Villager wife = adult(helper, 0, "farmer", 30L);
		((VillagerLifeData) wife).vnap$setFemale(true);
		com.vnap.Mc.snap(wife, net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(door.north(4))));
		wife.getPersistentData().putBoolean(com.vnap.village.market.Housing.FOUNDER, true);
		VillagerHome.tick(level, wife, DAY);
		Checks.assertTrue(helper, Family.isHead(wife), "she owns a house");
		Villager mayor = adult(helper, 1, "none", 10L);
		for (int i = 2; i < 5; i++) adult(helper, i, "farmer", 20L);
		Checks.assertTrue(helper, Family.marry(level, mayor, wife) && Family.member(mayor), "he married into her house");
		BlockPos oldBed = ((VillagerLifeData) wife).vnap$homeBed();
		Villager kid = adult(helper, 6, "none", 95L);
		kid.setAge(-24000);
		Family.state(kid).father = mayor.getUUID();
		Family.state(kid).mother = wife.getUUID();
		Family.state(kid).head = wife.getUUID();
		Family.state(kid).home = oldBed;
		net.minecraft.world.entity.animal.Wolf dog = helper.spawn(EntityType.WOLF, door.south(3));
		com.vnap.village.home.Pets.adopt(level, wife, dog);
		com.vnap.village.home.Pets.buildKennelNow(level, wife);
		BlockPos oldKennel = Family.state(wife).kennel;
		Checks.assertTrue(helper, oldKennel != null, "her dog has a kennel in her yard");
		Mayors.daily(level, mayor, DAY);
		Checks.assertTrue(helper, Mayors.isMayor(mayor), "a married man is elected like anyone");
		Mayors.daily(level, mayor, DAY + 1);
		HouseProject hall = null;
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.done && house.owner.equals(mayor.getUUID())) hall = house;
		}
		Checks.assertTrue(helper, hall != null && Mayors.HALLS.contains(hall.plan), "the mayor starts its hall");
		if (hall == null) return;
		HomeProjects.buildNow(level, hall);
		Checks.assertTrue(helper, Mayors.hall(level, mayor) != null && Family.isHead(mayor), "it moves into its hall and heads it");
		FamilyState hers = Family.state(wife);
		Checks.assertTrue(helper, mayor.getUUID().equals(hers.head) && Family.member(wife), "the wife comes along");
		BlockPos anchor = ((VillagerLifeData) mayor).vnap$homeBed();
		BlockPos herBed = ((VillagerLifeData) wife).vnap$homeBed();
		BlockPos kidBed = ((VillagerLifeData) kid).vnap$homeBed();
		Checks.assertTrue(helper, anchor != null && herBed != null && herBed.closerThan(anchor, 9.0) && !herBed.equals(oldBed),
				"she sleeps in the hall at once: his bed " + anchor + ", hers " + herBed);
		Checks.assertTrue(helper, kidBed != null && anchor != null && kidBed.closerThan(anchor, 9.0) && mayor.getUUID().equals(Family.state(kid).head),
				"so does their child, bed " + kidBed);
		Checks.assertTrue(helper, oldBed != null && VillagerHome.claimable(level, oldBed), "her old house is free for someone else");
		Checks.assertTrue(helper, dog.getUUID().equals(Family.state(mayor).pet) && Family.state(wife).pet == null
				&& mayor.getUUID().equals(Nbt.getUUID(dog.getPersistentData(), com.vnap.village.home.Pets.PET_HEAD)), "her dog comes along");
		Checks.assertTrue(helper, oldKennel != null && !level.getBlockState(oldKennel).isAir() && oldKennel.equals(com.vnap.village.home.Pets.nextTakeUp(level, mayor)),
				"the old kennel still stands: the mayor goes to take it up by hand");
		if (oldKennel != null) com.vnap.village.home.Pets.takeUp(level, mayor, oldKennel);
		Checks.assertTrue(helper, oldKennel != null && level.getBlockState(oldKennel).isAir() && com.vnap.village.home.Pets.nextTakeUp(level, mayor) == null
				&& com.vnap.village.home.Pets.wantsKennel(level, mayor), "taken up, the mayor sets a new one down by the hall");
		Checks.assertTrue(helper, com.vnap.Mc.restrictCenter(dog).equals(anchor), "the dog keeps to the hall now");
		Villager child = Family.birth(level, mayor, wife);
		Checks.assertTrue(helper, child != null && mayor.getUUID().equals(Family.state(child).father), "the mayor has a child, was " + Family.whyNoChild(level, mayor, wife));
		if (child == null) return;
		Checks.assertFalse(helper, Mayors.isMayor(child) || Mayors.candidate(child), "a child is never mayor");
		Mayors.daily(level, mayor, DAY + 3);
		long halls = VillageMoodSavedData.get(level).houses().stream().filter(h -> h.owner.equals(mayor.getUUID())).count();
		Checks.assertTrue(helper, halls == 1, "one hall is enough, was " + halls);
		Checks.assertTrue(helper, mood.equals(mood(level, bell)), "the hall, the wedding and the child do not move the mood");
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		helper.succeed();
	}

	/**
	 * The mayor dies alone in its hall with its dog: the dog goes to a family without a pet (the new mayor has one);
	 * the new mayor moves into the empty hall with its own dog, no second hall is built, its old house is free.
	 */
	@GameTest(batch = "mayors", template = "home_platform", timeoutTicks = 200)
	public static void aNewMayorMovesIntoTheEmptyHallAndThePetsFindFamilies(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Villager late = adult(helper, 0, "none", 5L);
		Villager neighbour = adult(helper, 1, "farmer", 20L);
		Villager next = adult(helper, 2, "none", 10L);
		adult(helper, 3, "farmer", 30L);
		adult(helper, 4, "farmer", 31L);
		BlockPos hisDoor = new BlockPos(40, 2, 86);
		BlockPos nextDoor = new BlockPos(60, 2, 86);
		layHouse(helper, hisDoor);
		layHouse(helper, nextDoor);
		com.vnap.Mc.snap(neighbour, net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(hisDoor.north(4))));
		com.vnap.Mc.snap(next, net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(nextDoor.north(4))));
		neighbour.getPersistentData().putBoolean(com.vnap.village.market.Housing.FOUNDER, true);
		next.getPersistentData().putBoolean(com.vnap.village.market.Housing.FOUNDER, true);
		VillagerHome.tick(level, neighbour, DAY);
		VillagerHome.tick(level, next, DAY);
		BlockPos nextOldBed = ((VillagerLifeData) next).vnap$homeBed();
		Checks.assertTrue(helper, Family.isHead(neighbour) && Family.isHead(next) && nextOldBed != null, "two families with houses");
		net.minecraft.world.entity.animal.Wolf ownDog = helper.spawn(EntityType.WOLF, nextDoor.south(3));
		com.vnap.village.home.Pets.adopt(level, next, ownDog);

		Mayors.daily(level, late, DAY);
		Checks.assertTrue(helper, Mayors.isMayor(late), "elected");
		Mayors.daily(level, late, DAY + 1);
		HouseProject hall = null;
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.done && house.owner.equals(late.getUUID())) hall = house;
		}
		if (hall == null) {
			Checks.fail(helper, "the hall is started");
			return;
		}
		HomeProjects.buildNow(level, hall);
		Checks.assertTrue(helper, Mayors.hall(level, late) != null, "the mayor lives in its hall");
		net.minecraft.world.entity.animal.Wolf hallDog = helper.spawn(EntityType.WOLF, BELL.offset(0, 0, 6));
		com.vnap.village.home.Pets.adopt(level, late, hallDog);
		com.vnap.village.home.Pets.buildKennelNow(level, late);
		BlockPos kennel = Family.state(late).kennel;

		com.vnap.Mc.kill(late);
		Checks.assertTrue(helper, hallDog.getUUID().equals(Family.state(neighbour).pet)
						&& neighbour.getUUID().equals(Nbt.getUUID(hallDog.getPersistentData(), com.vnap.village.home.Pets.PET_HEAD)),
				"the late mayor's dog goes to the family without a pet");
		Checks.assertTrue(helper, ownDog.getUUID().equals(Family.state(next).pet), "the new mayor keeps its own dog, not two");
		Checks.assertTrue(helper, kennel != null && kennel.equals(com.vnap.village.home.Pets.nextTakeUp(level, neighbour)),
				"the family that took the dog in goes to fetch its kennel");
		Checks.assertTrue(helper, Mayors.isMayor(next), "the next is elected (no family left)");

		Mayors.daily(level, next, DAY + 2);
		Checks.assertTrue(helper, Mayors.hall(level, next) != null && Family.isHead(next), "the new mayor moves into the empty hall");
		long halls = VillageMoodSavedData.get(level).houses().stream().filter(h -> Mayors.HALLS.contains(h.plan) && h.village == bell.asLong()).count();
		Checks.assertTrue(helper, halls == 1, "no second hall, was " + halls);
		BlockPos anchor = ((VillagerLifeData) next).vnap$homeBed();
		Checks.assertTrue(helper, anchor != null && com.vnap.Mc.restrictCenter(ownDog).equals(anchor), "its dog comes along");
		Checks.assertTrue(helper, nextOldBed != null && VillagerHome.claimable(level, nextOldBed), "its old house is free");
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		helper.succeed();
	}

	/**
	 * With no free ground for a hall, an empty house the villagers built is taken down for it: only its own blocks
	 * and furnishings (a player's block stays), and never a house someone lives in.
	 */
	@GameTest(batch = "mayors", template = "home_platform", timeoutTicks = 200)
	public static void anEmptyVillagerHouseMakesRoomForTheHall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Villager owner = adult(helper, 0, "farmer", 10L);
		owner.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, DAY - 5);
		HouseProject house = HomeProjects.start(level, owner, DAY, List.of("cottage"));
		if (house == null) {
			Checks.fail(helper, "a cottage is planned");
			return;
		}
		HomeProjects.buildNow(level, house);
		Checks.assertTrue(helper, HomeProjects.emptyHouses(level, bell).isEmpty(), "a house someone lives in is not taken down");
		Family.leaveForNewHouse(level, owner);
		BlockPos bed = house.bed(level);
		Checks.assertTrue(helper, bed != null && VillagerHome.claimable(level, bed), "the owner left: the house stands empty");
		BlockPos inside = com.vnap.village.build.BuildJob.at(house.origin, house.rotation, new BlockPos(4, 1, 5));
		level.setBlockAndUpdate(inside, Blocks.DIAMOND_BLOCK.defaultBlockState());
		Checks.assertTrue(helper, HomeProjects.emptyHouses(level, bell).isEmpty(), "a player's block in it keeps the house standing");
		level.setBlockAndUpdate(inside, Blocks.AIR.defaultBlockState());
		Checks.assertTrue(helper, HomeProjects.emptyHouses(level, bell).equals(List.of(house)), "the empty cottage is the one to take down");
		var box = house.footprint(level);
		BlockPos centre = new BlockPos((box.minX() + box.maxX()) / 2, house.origin.getY(), (box.minZ() + box.maxZ()) / 2);
		Villager mayor = adult(helper, 1, "none", 5L);
		HouseProject hall = HomeProjects.planAt(level, mayor, DAY, Mayors.HALLS, centre, house.rotation, box);
		Checks.assertTrue(helper, hall != null && Mayors.HALLS.contains(hall.plan), "a hall would stand where the cottage stands");
		if (hall == null) return;
		HomeProjects.clearForHall(level, hall, house);
		HomeProjects.register(level, hall);
		Checks.assertTrue(helper, !hall.clearing.isEmpty() && bed != null && hall.clearing.contains(bed), "the cottage is to be taken apart, its bed too");
		Checks.assertTrue(helper, level.getBlockState(bed).getBlock() instanceof BedBlock, "nothing comes down by itself");
		Checks.assertFalse(helper, VillageMoodSavedData.get(level).houses().contains(house), "the cottage is no project any more");
		HomeProjects.buildNow(level, hall);
		Checks.assertTrue(helper, hall.clearing.isEmpty() && hall.done, "taken down and the hall built (the debug way)");
		Checks.assertTrue(helper, Mayors.HALLS.contains(hall.plan)
				&& com.vnap.village.workshop.Workshops.schematic(level, hall.schematicName()).blocks.size() > 0, "the hall stands there");
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		helper.succeed();
	}

	/** A villager takes an old building apart by hand: walks up, swings, cell after cell from the top, floor to grass. */
	@GameTest(batch = "mayors", template = "home_platform", timeoutTicks = 4000)
	public static void anOldHouseIsTakenApartByHand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		com.vnap.Mc.setDayTime(level, 3000L);
		helper.onEachTick(() -> com.vnap.Mc.setDayTime(level, 3000L));
		BlockPos corner = new BlockPos(30, 1, 20);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(corner.offset(x, 0, z), Blocks.COBBLESTONE);
				for (int y = 1; y <= 2; y++) {
					if ((x == 1 && z == 1) || (x == 1 && z == 0)) continue;
					helper.setBlock(corner.offset(x, y, z), Blocks.OAK_PLANKS);
				}
				helper.setBlock(corner.offset(x, 3, z), Blocks.OAK_SLAB);
			}
		}
		BlockPos lower = helper.absolutePos(corner);
		var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(lower.getX(), lower.getY(), lower.getZ(),
				lower.getX() + 2, lower.getY() + 3, lower.getZ() + 2);
		Villager mayor = adult(helper, 1, "none", 5L);
		HouseProject hall = new HouseProject(bell.asLong(), bell, mayor.getUUID(), Mayors.HALLS.get(1), "plains",
				helper.absolutePos(new BlockPos(70, 1, 70)), net.minecraft.world.level.block.Rotation.NONE, DAY);
		HomeProjects.clearForHall(level, hall, box, lower.getY());
		HomeProjects.register(level, hall);
		int cells = hall.clearing.size();
		Checks.assertTrue(helper, cells == 9 + 14 + 9, "slabs, walls and floor are to go, was " + cells);
		mayor.setNoAi(false);
		mayor.refreshBrain(level);
		mayor.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		helper.succeedWhen(() -> {
			Checks.assertTrue(helper, hall.clearing.isEmpty(), hall.clearing.size() + " of " + cells + " cells left: "
					+ hall.clearing.stream().map(pos -> pos.subtract(lower).toShortString()).toList() + ", the villager at "
					+ mayor.blockPosition().subtract(lower).toShortString());
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				Checks.assertTrue(helper, pos.getY() == box.minY() ? level.getBlockState(pos).is(Blocks.GRASS_BLOCK) : level.getBlockState(pos).isAir(),
						"cleared at " + pos);
			}
			VillageMoodSavedData.get(level).houses().remove(hall);
		});
	}

	/** A mayor strolled far past the village's edge: it heads back. */
	@GameTest(batch = "mayors", template = "home_platform")
	public static void aMayorFarOutWalksBackToTheVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Villager mayor = helper.spawn(EntityType.VILLAGER, 96, 2, 96);
		mayor.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		((VillagerLifeData) mayor).vnap$setMayor(true);
		Villager near = helper.spawn(EntityType.VILLAGER, 54, 2, 54);
		near.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		((VillagerLifeData) near).vnap$setMayor(true);
		// it was walking further out still
		mayor.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new net.minecraft.world.entity.ai.memory.WalkTarget(
				helper.absolutePos(new BlockPos(99, 2, 99)), 0.6F, 1));
		near.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		Mayors.tether(level, mayor);
		Mayors.tether(level, near);
		Checks.assertTrue(helper, com.vnap.village.life.Agenda.has(mayor, "tether"), "far out, it turns back for the bell (its agenda)");
		Checks.assertFalse(helper, com.vnap.village.life.Agenda.has(near, "tether"), "in the village it goes about its day");
		helper.succeed();
	}

	/** A mayor without a trade keeps booking job sites (vanilla): every family tick it lets them go for those who work there. */
	@GameTest(batch = "mayors", template = "home_platform")
	public static void aMayorLeavesJobSitesToTheWorkers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		helper.setBlock(new BlockPos(56, 2, 50), Blocks.LECTERN);
		BlockPos lectern = helper.absolutePos(new BlockPos(56, 2, 50));
		Villager mayor = adult(helper, 0, "none", 5L);
		((VillagerLifeData) mayor).vnap$setMayor(true);
		helper.runAfterDelay(2, () -> {
			Checks.assertTrue(helper, level.getPoiManager().take(type -> true, (type, pos) -> pos.equals(lectern), lectern, 1).isPresent(),
					"the mayor books the lectern");
			mayor.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), lectern));
			Mayors.tick(level, mayor);
			Checks.assertFalse(helper, mayor.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE), "it forgets the job site");
			Checks.assertTrue(helper, freeTicket(level, lectern), "and the lectern is free for a librarian");
			helper.succeed();
		});
	}

	/** A hall is the office's: the one who started it died; the mayor of the day moves in, and no second one is built. */
	@GameTest(batch = "mayors", template = "home_platform", timeoutTicks = 200)
	public static void theHallGoesToTheMayorOfTheDayAndThereIsOnlyOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Villager first = adult(helper, 0, "none", 5L);
		for (int i = 2; i < 6; i++) adult(helper, i, "farmer", 20L + i);
		Villager second = adult(helper, 1, "none", 10L);
		Mayors.daily(level, second, DAY);
		Checks.assertTrue(helper, Mayors.isMayor(first), "elected");
		HouseProject hall = Mayors.planHall(level, first, bell, DAY);
		if (hall == null) {
			Checks.fail(helper, "the hall is planned");
			return;
		}
		com.vnap.Mc.kill(first);
		Checks.assertTrue(helper, Mayors.isMayor(second), "the next mayor");
		Mayors.daily(level, second, DAY + 1);
		long halls = VillageMoodSavedData.get(level).houses().stream().filter(h -> Mayors.HALLS.contains(h.plan) && h.village == bell.asLong()).count();
		Checks.assertTrue(helper, halls == 1, "the hall going up is not started twice, was " + halls);
		Checks.assertTrue(helper, hall.owner.equals(second.getUUID()), "the new mayor builds on the hall the late one began");
		HomeProjects.buildNow(level, hall);
		Checks.assertTrue(helper, Mayors.hall(level, second) != null && Family.isHead(second), "the mayor of the day moves in, not the late one");

		// the mayor steps down and stays in the hall: the next one keeps its own house, no second hall
		Mayors.resign(level, second, DAY + 2, false);
		Villager third = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(bell).inflate(48.0), Mayors::isMayor)
				.stream().findFirst().orElse(null);
		if (third == null) {
			Checks.fail(helper, "a third mayor is elected");
			return;
		}
		Mayors.daily(level, third, DAY + 3);
		long after = VillageMoodSavedData.get(level).houses().stream().filter(h -> Mayors.HALLS.contains(h.plan) && h.village == bell.asLong()).count();
		Checks.assertTrue(helper, after == 1, "one hall a village, was " + after);
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		helper.succeed();
	}

	/** A grown heir who took the office in the hall it grew up in heads the house now; a guest in a spare bed gives it up. */
	@GameTest(batch = "mayors", template = "home_platform", timeoutTicks = 200)
	public static void aGrownHeirHeadsTheHallAndGuestsMakeRoom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Villager mayor = adult(helper, 0, "none", 5L);
		for (int i = 2; i < 6; i++) adult(helper, i, "farmer", 20L + i);
		Mayors.daily(level, mayor, DAY);
		HouseProject hall = Mayors.planHall(level, mayor, bell, DAY);
		if (hall == null) {
			Checks.fail(helper, "the hall is planned");
			return;
		}
		HomeProjects.buildNow(level, hall);
		Villager heir = adult(helper, 1, "none", 90L);
		heir.setAge(-24000);
		Family.state(heir).father = mayor.getUUID();
		Family.state(heir).head = mayor.getUUID();
		BlockPos anchor = ((VillagerLifeData) mayor).vnap$homeBed();
		Family.state(heir).home = anchor;
		// a homeless stranger grabbed the spare bed meanwhile
		Villager guest = adult(helper, 6, "farmer", 40L);
		VillagerHome.tick(level, mayor, DAY + 1);
		BlockPos heirBed = ((VillagerLifeData) heir).vnap$homeBed();
		Checks.assertTrue(helper, heirBed != null, "the child sleeps in the hall");
		BlockPos spare = null;
		for (BlockPos pos : BlockPos.betweenClosed(hall.footprint(level).minX(), hall.footprint(level).minY(), hall.footprint(level).minZ(),
				hall.footprint(level).maxX(), hall.footprint(level).maxY(), hall.footprint(level).maxZ())) {
			if (level.getBlockState(pos).getBlock() instanceof BedBlock && level.getBlockState(pos).getValue(BedBlock.PART) == BedPart.HEAD
					&& !pos.equals(anchor) && !pos.equals(heirBed)
					&& freeTicket(level, pos)) {
				spare = pos.immutable();
				break;
			}
		}
		if (spare == null) {
			Checks.fail(helper, "a spare bed in the hall");
			return;
		}
		BlockPos taken = spare;
		level.getPoiManager().take(type -> true, (type, pos) -> pos.equals(taken), taken, 1);
		guest.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), taken));
		com.vnap.Mc.kill(mayor);
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
		Checks.assertTrue(helper, office != null && heir.getUUID().equals(office.heir), "the office waits for the child");
		heir.setAge(0);
		Mayors.daily(level, heir, DAY + 2);
		Checks.assertTrue(helper, Mayors.isMayor(heir) && Family.isHead(heir) && Mayors.hall(level, heir) != null, "grown up, it is mayor and heads the hall");
		Villager wife = adult(helper, 7, "farmer", 60L);
		((VillagerLifeData) wife).vnap$setFemale(true);
		Family.state(wife).spouse = heir.getUUID();
		Family.state(heir).spouse = wife.getUUID();
		Family.state(wife).head = heir.getUUID();
		Family.state(wife).home = ((VillagerLifeData) heir).vnap$homeBed();
		VillagerHome.tick(level, heir, DAY + 3);
		BlockPos herBed = ((VillagerLifeData) wife).vnap$homeBed();
		Checks.assertTrue(helper, herBed != null && Mayors.inHall(level, herBed), "his wife gets a bed in the hall, was " + herBed);
		Checks.assertFalse(helper, taken.equals(guest.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null))
				&& taken.equals(herBed), "the guest does not keep a bed the family sleeps in");
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		helper.succeed();
	}

	/** A golem or a cow inside the walls of a house going up is set outside; the family's own dog stays. */
	@GameTest(batch = "mayors", template = "home_platform", timeoutTicks = 100)
	public static void aGolemIsNotWalledIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Villager owner = adult(helper, 0, "farmer", 10L);
		owner.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, DAY - 5);
		HouseProject house = HomeProjects.start(level, owner, DAY, List.of("cottage"));
		if (house == null) {
			Checks.fail(helper, "a cottage is planned");
			return;
		}
		var box = house.footprint(level);
		BlockPos middle = new BlockPos((box.minX() + box.maxX()) / 2, box.minY() + 1, (box.minZ() + box.maxZ()) / 2);
		var golem = com.vnap.Mc.create(EntityType.IRON_GOLEM, level);
		var cow = com.vnap.Mc.create(EntityType.COW, level);
		var dog = com.vnap.Mc.create(EntityType.WOLF, level);
		if (golem == null || cow == null || dog == null) {
			Checks.fail(helper, "mobs can be made");
			return;
		}
		for (var mob : List.of(golem, cow, dog)) {
			com.vnap.Mc.snap(mob, middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5);
			mob.setNoAi(true);
			level.addFreshEntity(mob);
		}
		dog.setTame(true, false);
		var job = house.job(level);
		for (int i = 0; i < 200 && !job.done(); i++) {
			job.skipDone(level);
			job.placeNext(level, null);
		}
		HomeProjects.shooOut(level, house);
		Checks.assertFalse(helper, box.isInside(golem.blockPosition()), "the golem stands outside the house, at " + golem.blockPosition());
		Checks.assertFalse(helper, box.isInside(cow.blockPosition()), "so does the cow");
		Checks.assertTrue(helper, box.isInside(dog.blockPosition()), "a tame dog of the family may stay in");
		Checks.assertTrue(helper, golem.blockPosition().closerThan(middle, 16.0), "set just outside, not somewhere far");
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		helper.succeed();
	}

	/** Bells within one village share one office even 56 blocks apart; two offices from an old save are merged. */
	@GameTest(batch = "mayors", template = "home_platform")
	public static void oneOfficeForABigVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		helper.setBlock(new BlockPos(10, 2, 90), Blocks.BELL);
		BlockPos far = helper.absolutePos(new BlockPos(10, 2, 90));
		List<Villager> people = new ArrayList<>();
		for (int i = 0; i < 5; i++) people.add(adult(helper, i, "none", 10L + i));
		Villager there = adult(helper, 6, "none", 1L);
		there.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), far));
		Mayors.daily(level, people.get(0), DAY);
		Mayors.daily(level, there, DAY);
		long mayors = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(bell).inflate(50.0), Mayors::isMayor).size();
		Checks.assertTrue(helper, mayors == 1 && Mayors.isMayor(there), "one mayor for both bells (the eldest of the whole village), was " + mayors);

		// an old save opened a second office for the far bell (villages were counted by 32 blocks): merged
		Mayors register = Mayors.get(level.getServer());
		Mayors.Office stays = register.officeNear(level.dimension(), far);
		Villager other = people.get(0);
		Mayors.Office second = new Mayors.Office(level.dimension(), bell.offset(1, 0, 0));
		second.mayor = other.getUUID();
		second.electedDay = DAY + 1;
		register.offices().add(second);
		((VillagerLifeData) other).vnap$setMayor(true);
		Mayors.daily(level, other, DAY + 1);
		Checks.assertFalse(helper, register.offices().contains(second), "the second office is closed");
		Checks.assertFalse(helper, Mayors.isMayor(other), "its mayor steps down");
		Checks.assertTrue(helper, stays != null && register.offices().contains(stays) && Mayors.isMayor(there), "the first mayor stays");
		helper.succeed();
	}

	/** The mayor closes a house whose owner died with nobody living there: its sign comes down, the bed is free. */
	@GameTest(batch = "mayors", template = "home_platform", timeoutTicks = 100)
	public static void theMayorClosesTheHouseOfTheDead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Villager owner = adult(helper, 0, "farmer", 30L);
		owner.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, DAY - 5);
		HouseProject house = HomeProjects.start(level, owner, DAY, List.of("cottage"));
		if (house == null) {
			Checks.fail(helper, "a cottage is planned");
			return;
		}
		HomeProjects.buildNow(level, house);
		BlockPos bed = house.bed(level);
		Checks.assertTrue(helper, bed != null && !VillagerHome.claimable(level, bed), "the owner lives there");
		// a death the family code never saw (an old save): only the register knows the owner is dead
		com.vnap.dialogue.FamilyDeaths.get(level.getServer()).add(owner.getUUID());
		owner.discard();
		Villager mayor = adult(helper, 1, "none", 5L);
		for (int i = 2; i < 5; i++) adult(helper, i, "farmer", 20L + i);
		Mayors.daily(level, mayor, DAY);
		Mayors.daily(level, mayor, DAY + 1);
		Checks.assertTrue(helper, bed != null && VillagerHome.claimable(level, bed), "the house is free for someone without a home");
		VillageMoodSavedData.get(level).houses().remove(house);
		helper.succeed();
	}

	/** Both halls get their furnishing sets: the office upstairs, the home downstairs, each set once and all of it in. */
	@GameTest(batch = "mayors", template = "home_platform", timeoutTicks = 200)
	public static void theHallIsFurnishedOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		int x = 4;
		for (String plan : Mayors.HALLS) {
			Villager owner = adult(helper, 0, "none", 5L);
			HouseProject hall = new HouseProject(bell.asLong(), bell, owner.getUUID(), plan, "plains",
					helper.absolutePos(new BlockPos(x, 1, 70)), net.minecraft.world.level.block.Rotation.NONE, DAY);
			x += 20;
			HomeProjects.register(level, hall);
			HomeProjects.buildNow(level, hall);
			for (String part : List.of(Mayors.OFFICE, Mayors.HOME)) {
				var job = Mayors.furnishing(level, hall, part);
				Checks.assertTrue(helper, job != null, plan + " has its " + part + " set to put in");
				int placed = 0;
				while (job != null && !job.done() && placed < 100) {
					if (job.placeNext(level, null) != null) placed++;
				}
				Checks.assertTrue(helper, placed >= 9, plan + " " + part + ": " + placed + " pieces put in");
				Checks.assertTrue(helper, Mayors.furnishing(level, hall, part) == null, plan + " " + part + " is done once and for all");
			}
			BlockPos bed = hall.bed(level);
			Checks.assertTrue(helper, bed != null && level.getBlockState(bed).getBlock() instanceof BedBlock, plan + " keeps its beds clear");
		}
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		helper.succeed();
	}

	@GameTest(batch = "mayors", template = "platform")
	public static void mayorsSpeakTheirOwnLines(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (String title : List.of("Mayor Elected", "Mayor Lays the First Stone", "Mayor Moves Into the Hall", "Mayor Morning Speech",
				"Mayor Inspects the Building", "Mayor Praises a Worker", "Mayor Weds the Couple", "Mayor Funeral Words", "Mayor Resigns",
				"Mayor Takes Up the Office", "Mayor Has No Room for the Hall", "Mayor Clears an Empty House", "Mayor Rebuilds on the Old Ground", "Mayor Furnishes the Office")) {
			if (DialogueCatalog.byTitle(title, "mayor") == null) missing.add("mayor " + title);
			if (DialogueCatalog.byTitle(title, "woman") == null) missing.add("woman " + title);
		}
		if (DialogueCatalog.byTitle("Woman Settles Into the Hall", "woman") == null) missing.add("woman Woman Settles Into the Hall");
		for (String title : List.of("Villager Cheers the New Mayor", "Villager Answers the Mayor")) {
			if (DialogueCatalog.byTitle(title, "villager") == null) missing.add("villager " + title);
			if (DialogueCatalog.byTitle(title, "woman") == null) missing.add("woman " + title);
		}
		Checks.assertTrue(helper, missing.isEmpty(), "missing lines: " + missing);
		Villager man = helper.spawn(EntityType.VILLAGER, 4, 2, 4);
		Villager woman = helper.spawn(EntityType.VILLAGER, 8, 2, 4);
		man.setNoAi(true);
		woman.setNoAi(true);
		((VillagerLifeData) woman).vnap$setFemale(true);
		((VillagerLifeData) man).vnap$setFemale(false);
		((VillagerLifeData) man).vnap$setMayor(true);
		((VillagerLifeData) woman).vnap$setMayor(true);
		DialogueCatalog.DialogueGroup speech = DialogueCatalog.byTitle("Mayor Morning Speech", "mayor");
		DialogueCatalog.DialogueGroup herSpeech = DialogueCatalog.byTitle("Mayor Morning Speech", "woman");
		DialogueCatalog.DialogueGroup baby = DialogueCatalog.byTitle("Man New Baby", "villager");
		DialogueCatalog.DialogueGroup old = DialogueCatalog.byTitle("Sees the Player", "mayor");
		if (speech == null || herSpeech == null || baby == null || old == null) {
			Checks.fail(helper, "the groups exist");
			return;
		}
		Checks.assertTrue(helper, com.vnap.dialogue.Voices.canVoice(man, speech.id()) && com.vnap.dialogue.Voices.canVoice(man, old.id()),
				"a man mayor speaks the mayor's lines");
		Checks.assertTrue(helper, com.vnap.dialogue.Voices.canVoice(man, baby.id()), "and still a father's and a villager's");
		Checks.assertTrue(helper, com.vnap.dialogue.Voices.canVoice(woman, herSpeech.id()) && !com.vnap.dialogue.Voices.canVoice(woman, speech.id()),
				"a woman mayor speaks in her own voice");
		helper.succeed();
	}

	private static boolean freeTicket(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().getInRange(type -> true, pos, 0, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
				.anyMatch(poi -> poi.getPos().equals(pos));
	}

	private static BlockPos bell(GameTestHelper helper) {
		helper.setBlock(BELL, Blocks.BELL);
		return helper.absolutePos(BELL);
	}

	/** A grown-up of the bell's village around it, with a trade (or "none"), born on {@code born}. */
	private static Villager adult(GameTestHelper helper, int index, String profession, long born) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, BELL.offset(-8 + index * 2, 0, 4));
		villager.setNoAi(true);
		VillagerProfession trade = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(
				net.minecraft.resources.ResourceLocation.withDefaultNamespace(profession));
		villager.setVillagerData(com.vnap.Mc.withProfession(villager.getVillagerData(), trade));
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), helper.absolutePos(BELL)));
		((VillagerLifeData) villager).vnap$setFemale(false);
		Family.state(villager).genderKnown = true;
		Family.state(villager).bornDay = born;
		Family.state(villager).widowedDay = Long.MIN_VALUE / 2;
		return villager;
	}

	/** Mood and standing of every village state around the bell. */
	private static Map<BlockPos, List<Float>> mood(ServerLevel level, BlockPos bell) {
		Map<BlockPos, List<Float>> out = new HashMap<>();
		for (MoodState state : VillageMoodSavedData.get(level).all()) {
			if (state.meetingPos.closerThan(bell, 96.0)) out.put(state.meetingPos, List.of(state.mood, state.standing));
		}
		return out;
	}

	/** A south door, a bed two blocks north of it and a stone roof three blocks up. */
	private static void layHouse(GameTestHelper helper, BlockPos door) {
		helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		BlockPos foot = door.north(2);
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.north(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -7; dz <= 1; dz++) {
				helper.setBlock(door.offset(dx, 3, dz), Blocks.STONE.defaultBlockState());
			}
		}
	}
}
