package com.vnap.gametest;

import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyHome;
import com.vnap.dialogue.FamilyState;
import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.MoodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class FamilyGameTests {
	@GameTest(template = "platform")
	public static void genderFollowsTheOldNameAndIsSaved(GameTestHelper helper) {
		Villager olga = named(helper, 4, 4, "Olga");
		Villager boris = named(helper, 6, 4, "Boris");
		Checks.assertTrue(helper, Family.female(olga) && !Family.female(boris), "a generated name keeps its gender");
		CompoundTag tag = new CompoundTag();
		com.vnap.Mc.saveWithoutId(olga, tag);
		Villager copy = com.vnap.Mc.create(EntityType.VILLAGER, helper.getLevel());
		if (copy == null) {
			Checks.fail(helper, "a villager can be created");
			return;
		}
		com.vnap.Mc.load(copy, tag);
		Checks.assertTrue(helper, Family.female(copy) && Family.state(copy).genderKnown, "the gender is saved with the villager");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void strangersDoNotBreed(GameTestHelper helper) {
		Villager man = adult(helper, 20, 4, false);
		Villager woman = adult(helper, 22, 4, true);
		man.getInventory().addItem(new ItemStack(Items.BREAD, 16));
		woman.getInventory().addItem(new ItemStack(Items.BREAD, 16));
		Checks.assertTrue(helper, !man.canBreed() && !woman.canBreed(), "unmarried villagers do not look for a partner");
		Checks.assertTrue(helper, !Family.mayHaveChild(helper.getLevel(), man, woman), "and cannot have a child together");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void weddingMovesTheWifeInAndRewritesTheSign(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(40, 2, 20);
		layHouse(helper, door, true);
		Villager man = adult(helper, 40, 16, false);
		Villager woman = adult(helper, 44, 16, true);
		VillagerHome.tick(level, man, 1L);
		Checks.assertTrue(helper, Family.isHead(man), "the husband owns the house");
		Checks.assertTrue(helper, Family.marry(level, man, woman), "the wedding succeeds");
		FamilyState his = Family.state(man);
		FamilyState hers = Family.state(woman);
		Checks.assertTrue(helper, woman.getUUID().equals(his.spouse) && man.getUUID().equals(hers.spouse), "they are spouses of each other");
		Checks.assertTrue(helper, Family.member(woman) && Family.isHead(man), "she moves into his house");
		BlockPos herBed = ((VillagerLifeData) woman).vnap$homeBed();
		Checks.assertTrue(helper, herBed != null && level.getBlockState(herBed).getBlock() instanceof BedBlock,
				"a bed for her stands next to his");
		Checks.assertTrue(helper, VillagerHome.nameOf(woman).equals(signLine(helper, door, 2)), "her name is on the sign");
		VillagerHome.tick(level, woman, 2L);
		Checks.assertTrue(helper, signs(helper, door) == 1, "a spouse does not hang a second sign");
		helper.succeed();
	}

	/** A husband with AI sets the bed down by hand later: until then the bride sleeps in her own house, not on her feet. */
	@GameTest(template = "platform")
	public static void aBrideKeepsHerBedUntilHerHusbandSetsOneDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hisDoor = new BlockPos(40, 2, 60);
		BlockPos herDoor = new BlockPos(70, 2, 60);
		layHouse(helper, hisDoor, true);
		layHouse(helper, herDoor, true);
		Villager man = adult(helper, 40, 56, false);
		Villager woman = adult(helper, 70, 56, true);
		VillagerHome.tick(level, man, 1L);
		VillagerHome.tick(level, woman, 1L);
		BlockPos ownBed = ((VillagerLifeData) woman).vnap$homeBed();
		BlockPos anchor = ((VillagerLifeData) man).vnap$homeBed();
		if (ownBed == null || anchor == null) {
			Checks.fail(helper, "both own a house before the wedding");
			return;
		}
		man.setNoAi(false);
		Checks.assertTrue(helper, Family.marry(level, man, woman), "the wedding succeeds");
		Checks.assertTrue(helper, ownBed.equals(((VillagerLifeData) woman).vnap$homeBed())
						&& woman.getBrain().hasMemoryValue(MemoryModuleType.HOME) && signs(helper, herDoor) == 1,
				"no bed by his yet: she keeps her own bed and house");
		Checks.assertTrue(helper, Family.member(woman) && woman.equals(Family.needsBed(level, man)), "she lives with him and waits for a bed");
		BlockPos[] spot = FamilyHome.bedSpot(level, anchor, true);
		if (spot == null) {
			Checks.fail(helper, "room for her bed by his");
			return;
		}
		Checks.assertTrue(helper, Family.buildBed(level, man, woman, spot[0], spot[1]), "he sets her bed down");
		BlockPos newBed = ((VillagerLifeData) woman).vnap$homeBed();
		Checks.assertTrue(helper, newBed != null && newBed.closerThan(anchor, 4.0) && Family.needsBed(level, man) == null, "she sleeps by him now");
		Checks.assertTrue(helper, signs(helper, herDoor) == 0, "and her old house is free for someone else");
		helper.succeed();
	}

	/** A couple with a brain waits for its nursery: the bed the husband sets down, the cradle his wife brings; the baby lies there. */
	@GameTest(template = "platform")
	public static void theNurseryIsMadeReadyBeforeTheBaby(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(30, 2, 20);
		layHouse(helper, door, true);
		Villager man = adult(helper, 30, 16, false);
		Villager woman = adult(helper, 32, 16, true);
		VillagerHome.tick(level, man, 1L);
		Family.marry(level, man, woman);
		BlockPos anchor = Family.home(man);
		if (anchor == null) {
			Checks.fail(helper, "their house");
			return;
		}
		// the wedding gave her a bed by his: the rest is the nursery's
		man.setNoAi(false);
		woman.setNoAi(false);
		Checks.assertTrue(helper, Family.NO_BABY_BED.equals(Family.whyNoChild(level, woman, man)), "first a bed for the baby: " + Family.whyNoChild(level, woman, man));
		BlockPos[] spot = FamilyHome.bedSpot(level, anchor, false);
		if (spot == null) {
			Checks.fail(helper, "room for the baby's bed");
			return;
		}
		Checks.assertTrue(helper, Family.makeNursery(level, man, spot[0], spot[1]), "he sets it down");
		BlockPos nursery = Family.state(man).nursery;
		Checks.assertTrue(helper, nursery != null && FamilyHome.freeBed(level, anchor) == null, "kept for the baby: nobody else's");
		Checks.assertTrue(helper, Family.NO_CRADLE.equals(Family.whyNoChild(level, woman, man)), "then a cradle: " + Family.whyNoChild(level, woman, man));
		BlockPos cradle = com.vnap.dialogue.Infants.placeCradle(level, woman, anchor);
		Checks.assertTrue(helper, cradle != null && Family.whyNoChild(level, woman, man) == null, "ready: " + Family.whyNoChild(level, woman, man));
		man.setNoAi(true);
		woman.setNoAi(true);
		Villager child = Family.birth(level, woman, man);
		if (child == null || nursery == null || cradle == null) {
			Checks.fail(helper, "the baby is born");
			return;
		}
		Checks.assertTrue(helper, cradle.equals(Family.state(child).cradle), "it lies in the cradle she brought");
		Checks.assertTrue(helper, child.getBrain().getMemory(MemoryModuleType.HOME).map(point -> point.pos().equals(FamilyHome.bedHead(level, nursery))).orElse(false)
				&& Family.state(man).nursery == null, "and the bed he made is its own");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aCoupleHasAChildInTheirHouseWithinTheLimits(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(60, 2, 20);
		layHouse(helper, door, true);
		Villager man = adult(helper, 60, 16, false);
		Villager woman = adult(helper, 62, 16, true);
		VillagerHome.tick(level, man, 1L);
		Family.marry(level, man, woman);
		Villager child = Family.birth(level, woman, man);
		if (child == null) {
			Checks.fail(helper, "a married couple with room has a child");
			return;
		}
		FamilyState kid = Family.state(child);
		BlockPos anchor = Family.home(man);
		BlockPos kidBed = ((VillagerLifeData) child).vnap$homeBed();
		Checks.assertTrue(helper, woman.getUUID().equals(kid.mother) && man.getUUID().equals(kid.father), "the child knows its parents");
		Checks.assertTrue(helper, anchor != null && anchor.equals(kid.home) && kidBed != null && kidBed.closerThan(anchor, 6.0),
				"the child sleeps in the family house");
		Checks.assertTrue(helper, Family.state(woman).childrenBorn == 1, "the birth is counted");
		Checks.assertTrue(helper, !signLine(helper, door, 3).isEmpty(), "the sign lists the children");
		Checks.assertTrue(helper, Family.birth(level, woman, man) == null, "no second child the same day");
		Family.state(woman).lastBirthDay = Long.MIN_VALUE / 2;
		Family.state(man).lastBirthDay = Long.MIN_VALUE / 2;
		Family.state(woman).childrenBorn = MoodConfig.maxChildren();
		Checks.assertTrue(helper, Family.birth(level, woman, man) == null, "no child past the limit");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void noRoomNoChild(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(80, 2, 20);
		layHouse(helper, door, false);
		Villager man = adult(helper, 80, 16, false);
		Villager woman = adult(helper, 82, 16, true);
		VillagerHome.tick(level, man, 1L);
		Family.marry(level, man, woman);
		Checks.assertTrue(helper, Family.birth(level, woman, man) == null, "a house without a roof has no room for a child bed");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aChildNeverClaimsAHouse(GameTestHelper helper) {
		BlockPos door = new BlockPos(20, 2, 40);
		layHouse(helper, door, true);
		Villager baby = person(helper, 20, 36);
		baby.setBaby(true);
		VillagerHome.tick(helper.getLevel(), baby, 1L);
		Checks.assertTrue(helper, signs(helper, door) == 0 && ((VillagerLifeData) baby).vnap$homeBed() == null,
				"a child does not hang a sign of its own");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aGrownChildMovesOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(40, 2, 40);
		layHouse(helper, door, true);
		Villager man = adult(helper, 40, 36, false);
		Villager woman = adult(helper, 42, 36, true);
		VillagerHome.tick(level, man, 1L);
		Family.marry(level, man, woman);
		Villager child = Family.birth(level, woman, man);
		if (child == null) {
			Checks.fail(helper, "the couple has a child");
			return;
		}
		child.setAge(0);
		long day = Family.day(level);
		Family.state(child).tickedDay = -1L;
		Family.tick(level, child, day);
		Checks.assertTrue(helper, Family.state(child).home != null, "the coming-of-age party is at home");
		Family.tick(level, child, day + 1);
		Checks.assertTrue(helper, Family.state(child).home == null && ((VillagerLifeData) child).vnap$homeBed() == null,
				"the grown child leaves its bed");
		Checks.assertTrue(helper, signLine(helper, door, 3).isEmpty(), "the sign no longer lists a child");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aBedSomeoneHoldsIsNotTakenByAnother(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(40, 2, 40);
		layHouse(helper, door, true);
		BlockPos head = helper.absolutePos(door.north(3));
		// a bride keeps her old bed (no sign of hers on it) until her husband sets her one: it is still hers
		Villager bride = adult(helper, 44, 36, true);
		FamilyHome.take(level, head);
		bride.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
		Villager neighbour = adult(helper, 40, 36, true);
		VillagerHome.tick(level, neighbour, 1L);
		Checks.assertTrue(helper, ((VillagerLifeData) neighbour).vnap$homeBed() == null, "one bed, one owner: the neighbour does not move in on it");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aBedsOwnerTakesItsTicketBackFromAnother(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(40, 2, 40);
		layHouse(helper, door, true);
		Villager owner = adult(helper, 40, 36, false);
		VillagerHome.tick(level, owner, 1L);
		BlockPos head = ((VillagerLifeData) owner).vnap$homeBed();
		if (head == null) {
			Checks.fail(helper, "the owner has a bed");
			return;
		}
		// vanilla let the owner's ticket go one night (the bed was slept in) and handed it to another
		FamilyHome.release(level, head);
		Villager other = adult(helper, 44, 36, false);
		FamilyHome.take(level, head);
		other.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
		VillagerHome.tick(level, owner, 2L);
		Checks.assertFalse(helper, other.getBrain().hasMemoryValue(MemoryModuleType.HOME), "the other gives the bed back: two remembering one bed lose it by turns");
		Checks.assertTrue(helper, FamilyHome.taken(level, head) && owner.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).filter(head::equals).isPresent(),
				"the owner holds its bed");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aBedMarkedSleptInWithNobodyInItIsFreed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(40, 2, 40);
		layHouse(helper, door, true);
		Villager owner = adult(helper, 40, 36, false);
		VillagerHome.tick(level, owner, 1L);
		BlockPos head = ((VillagerLifeData) owner).vnap$homeBed();
		if (head == null) {
			Checks.fail(helper, "the owner has a bed");
			return;
		}
		// the save kept the mark of a sleeper gone without waking: vanilla takes the owner's HOME over it every night
		level.setBlockAndUpdate(head, level.getBlockState(head).setValue(BedBlock.OCCUPIED, true));
		VillagerHome.tick(level, owner, 2L);
		Checks.assertFalse(helper, level.getBlockState(head).getValue(BedBlock.OCCUPIED), "the empty bed is not left marked taken");
		// one really asleep in it keeps its mark
		level.setBlockAndUpdate(head, level.getBlockState(head).setValue(BedBlock.OCCUPIED, true));
		owner.setSleepingPos(head);
		VillagerHome.tick(level, owner, 3L);
		Checks.assertTrue(helper, level.getBlockState(head).getValue(BedBlock.OCCUPIED), "a sleeper's bed stays taken");
		helper.succeed();
	}

	@GameTest(template = "platform", batch = "calendar")
	public static void aNightSleptAwayIsANewVillageDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long start = Family.day(level);
		long calendar = com.vnap.Mc.dayTime(level) / 24000L;
		// dusk, then the players sleep: the time of day jumps to the next morning, game time stays where it was
		com.vnap.Mc.setDayTime(level, calendar * 24000L + 13000L);
		Checks.assertTrue(helper, Family.day(level) == start, "the evening is the same day");
		com.vnap.Mc.setDayTime(level, (calendar + 1) * 24000L);
		Checks.assertTrue(helper, Family.day(level) == start + 1, "the morning after a night slept away is the next day");
		// /time set back to an earlier morning: another day, never an old one
		com.vnap.Mc.setDayTime(level, 1000L);
		Checks.assertTrue(helper, Family.day(level) == start + 2, "time set back is a new day, not a day gone");
		// the daylight cycle off: the time of day stands, a day of game time is a day
		com.vnap.Mc.setDaylightCycle(level, false);
		try {
			long frozen = Family.day(level);
			level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + 24000L);
			Checks.assertTrue(helper, Family.day(level) == frozen + 1, "with the time of day stopped, game time counts the days");
		} finally {
			com.vnap.Mc.setDaylightCycle(level, true);
		}
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void anElderWhoDiesInBedLeavesItFree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(40, 2, 40);
		layHouse(helper, door, true);
		Villager elder = adult(helper, 40, 36, false);
		VillagerHome.tick(level, elder, 1L);
		BlockPos head = ((VillagerLifeData) elder).vnap$homeBed();
		if (head == null) {
			Checks.fail(helper, "the elder has a bed");
			return;
		}
		level.setBlockAndUpdate(head, level.getBlockState(head).setValue(BedBlock.OCCUPIED, true));
		elder.setSleepingPos(head);
		com.vnap.dialogue.Generations.passAway(level, elder);
		Checks.assertFalse(helper, level.getBlockState(head).getValue(BedBlock.OCCUPIED), "the bed is not left marked taken");
		helper.succeed();
	}

	@GameTest(template = "home_platform", timeoutTicks = 100)
	public static void aHouseWithNoDoorGetsOneSetByHand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// a desert house of the world: sandstone walls, a roof, a way in with no door, a bed
		for (int x = 10; x <= 14; x++) {
			for (int z = 10; z <= 14; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.SANDSTONE);
				boolean wall = x == 10 || x == 14 || z == 10 || z == 14;
				for (int y = 2; y <= 3; y++) if (wall) helper.setBlock(new BlockPos(x, y, z), Blocks.SANDSTONE);
			}
		}
		helper.setBlock(new BlockPos(12, 2, 10), Blocks.AIR);
		helper.setBlock(new BlockPos(12, 3, 10), Blocks.AIR);
		helper.setBlock(new BlockPos(12, 2, 12), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(12, 2, 13), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		BlockPos way = helper.absolutePos(new BlockPos(12, 2, 10));
		BlockPos head = helper.absolutePos(new BlockPos(12, 2, 13));
		Checks.assertTrue(helper, FamilyHome.doorways(level, head).containsKey(way), "the way in with no door is found: " + FamilyHome.doorways(level, head));
		Villager villager = adult(helper, 12, 11, false);
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
		VillagerHome.tick(level, villager, 1L);
		Checks.assertTrue(helper, com.vnap.village.home.Placements.pending(level, way), "a door is to be set there by hand");
		com.vnap.village.home.Placements.tick(level);
		helper.runAfterDelay(2, () -> {
			for (com.vnap.village.home.Placements.Job job : java.util.List.copyOf(com.vnap.village.home.Placements.get(level.getServer()).jobs())) {
				if (job.pos().equals(way)) com.vnap.village.home.Placements.place(level, job);
			}
			Checks.assertTrue(helper, level.getBlockState(way).getBlock() instanceof DoorBlock && level.getBlockState(way.above()).getBlock() instanceof DoorBlock,
					"the door stands, both halves");
			Checks.assertTrue(helper, FamilyHome.doorways(level, head).isEmpty(), "no way in left open");
			helper.succeed();
		});
	}

	@GameTest(template = "platform")
	public static void aWidowKeepsTheHouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(60, 2, 40);
		layHouse(helper, door, true);
		Villager man = adult(helper, 60, 36, false);
		Villager woman = adult(helper, 62, 36, true);
		VillagerHome.tick(level, man, 1L);
		Family.marry(level, man, woman);
		com.vnap.Mc.kill(man);
		FamilyState hers = Family.state(woman);
		Checks.assertTrue(helper, hers.spouse == null && hers.widowedDay == Family.day(level), "she is widowed");
		Checks.assertTrue(helper, Family.isHead(woman), "the house is hers now");
		Checks.assertTrue(helper, VillagerHome.nameOf(woman).equals(signLine(helper, door, 0)), "her name heads the sign");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aSingleHeadsHouseGoesToTheEldestLeftInIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(100, 2, 40);
		layHouse(helper, door, true);
		BlockPos foot = door.north(2).east(2);
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.north(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		Villager father = adult(helper, 100, 36, false);
		Villager daughter = adult(helper, 102, 36, true);
		VillagerHome.tick(level, father, 1L);
		BlockPos home = Family.home(father);
		if (home == null) {
			Checks.fail(helper, "the father heads the house");
			return;
		}
		FamilyState hers = Family.state(daughter);
		hers.head = father.getUUID();
		hers.home = home;
		((VillagerLifeData) daughter).vnap$setHomeBed(helper.absolutePos(foot.north()));
		com.vnap.Mc.kill(father);
		Checks.assertTrue(helper, Family.isHead(daughter), "the house is hers now");
		Checks.assertTrue(helper, VillagerHome.nameOf(daughter).equals(signLine(helper, door, 0)), "her name heads the sign, not the late father's");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void singlesOfOneVillageStartCourting(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(80, 2, 40);
		layHouse(helper, door, true);
		Villager man = adult(helper, 80, 36, false);
		Villager woman = adult(helper, 84, 36, true);
		VillagerHome.tick(level, man, 1L);
		GlobalPos bell = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(82, 2, 30)));
		man.getBrain().setMemory(MemoryModuleType.MEETING_POINT, bell);
		woman.getBrain().setMemory(MemoryModuleType.MEETING_POINT, bell);
		long day = Family.day(level);
		Family.state(man).widowedDay = Long.MIN_VALUE / 2;
		Family.state(woman).widowedDay = Long.MIN_VALUE / 2;
		Family.tick(level, man, day);
		// the tests share their ground: a single woman of a neighbouring test may take his fancy instead
		java.util.UUID courted = Family.state(man).courting;
		Checks.assertTrue(helper, courted != null && level.getEntity(courted) instanceof Villager her && Family.female(her)
						&& man.getUUID().equals(Family.state(her).courting),
				"a single man with a house courts a single woman of his village");
		helper.succeed();
	}

	private static Villager adult(GameTestHelper helper, int x, int z, boolean female) {
		Villager villager = person(helper, x, z);
		((VillagerLifeData) villager).vnap$setFemale(female);
		Family.state(villager).genderKnown = true;
		return villager;
	}

	private static Villager named(GameTestHelper helper, int x, int z, String name) {
		Villager villager = person(helper, x, z);
		villager.setCustomName(Component.literal(name));
		Family.state(villager).genderKnown = false;
		Family.ensureGender(villager);
		return villager;
	}

	private static Villager person(GameTestHelper helper, int x, int z) {
		Villager villager = helper.spawn(EntityType.VILLAGER, x, 3, z);
		villager.setNoAi(true);
		return villager;
	}

	/** A south door, a bed two blocks north of it and, when {@code roof}, a stone roof three blocks up. */
	private static void layHouse(GameTestHelper helper, BlockPos door, boolean roof) {
		helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		BlockPos foot = door.north(2);
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.north(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		if (!roof) return;
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -7; dz <= 1; dz++) {
				helper.setBlock(door.offset(dx, 3, dz), Blocks.STONE.defaultBlockState());
			}
		}
	}

	private static String signLine(GameTestHelper helper, BlockPos door, int line) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(door);
		for (BlockPos pos : BlockPos.betweenClosed(abs.offset(-3, -1, -3), abs.offset(3, 2, 3))) {
			if (level.getBlockEntity(pos) instanceof SignBlockEntity sign
					&& VillagerHome.isMarker(com.vnap.Mc.signLine(sign, 1))) {
				return com.vnap.Mc.signLine(sign, line);
			}
		}
		return "";
	}

	private static int signs(GameTestHelper helper, BlockPos door) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(door);
		int found = 0;
		for (BlockPos pos : BlockPos.betweenClosed(abs.offset(-6, -1, -6), abs.offset(6, 2, 6))) {
			if (level.getBlockEntity(pos) instanceof SignBlockEntity sign
					&& VillagerHome.isMarker(com.vnap.Mc.signLine(sign, 1))) found++;
		}
		return found;
	}

	/** The villages the world makes: about as many women as men, and many different names, not one man's name for all. */
	@GameTest(template = "platform", timeoutTicks = 100)
	public static void villagersAreBornWomenAndMenAlikeWithManyNames(GameTestHelper helper) {
		int women = 0;
		java.util.Set<String> names = new java.util.HashSet<>();
		int count = 200;
		for (int i = 0; i < count; i++) {
			Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(10 + i % 20, 2, 10 + i / 20));
			villager.setNoAi(true);
			Family.ensureGender(villager);
			if (Family.female(villager)) women++;
			names.add(VillagerHome.nameOf(villager));
			villager.discard();
		}
		Checks.assertTrue(helper, women > count * 0.35 && women < count * 0.65, "women and men alike: " + women + " women of " + count);
		Checks.assertTrue(helper, names.size() > 40, "many names: " + names.size());
		helper.succeed();
	}
}
