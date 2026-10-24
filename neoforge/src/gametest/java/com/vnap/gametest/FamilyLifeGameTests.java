package com.vnap.gametest;

import com.vnap.block.DecorBlock;
import com.vnap.block.decor.DecorBlockEntity;
import com.vnap.block.LifeBlocks;
import com.vnap.dialogue.DialogueCatalog;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyDeaths;
import com.vnap.dialogue.FamilyState;
import com.vnap.dialogue.VillagerHome;
import com.vnap.dialogue.VoicePitch;
import com.vnap.entity.VillagerLifeData;
import com.vnap.item.LifeItems;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.FamilyRooms;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.life.Pantry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Families after the review: voices, beds and their POI tickets, signs, weddings, widows, rooms, gifts, home life. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class FamilyLifeGameTests {
	private static final long MORNING = 1000L;

	@GameTest(template = "platform")
	public static void womenVoiceTheLinesTheyHave(GameTestHelper helper) {
		// the middle of the platform, lifted out of any block: a villager stuck in a wall only talks about that
		Villager woman = adult(helper, 45, 45, true);
		for (int lift = 0; lift < 8 && woman.isInWall(); lift++) com.vnap.Mc.snap(woman, woman.getX(), woman.getY() + 1.0, woman.getZ());
		Checks.assertTrue(helper, com.vnap.dialogue.Voices.canVoice(woman, "wbbxpo"), "a woman calms down in her own words");
		Checks.assertTrue(helper, !com.vnap.dialogue.Voices.canVoice(woman, "asqzby"), "but has no man's snore to borrow");
		Checks.assertTrue(helper, Speech.line(woman, "Caught in the Rain", "test:rain:" + woman.getUUID(), false),
				"a title line reaches a woman through her stand-in: " + Speech.lastRefusal());
		helper.succeed();
	}

	/** Every "X at Work" is her one "Woman at Work": another trade's title is no way round its cooldown. */
	@GameTest(template = "platform", timeoutTicks = 600)
	public static void aWomanAtWorkDoesNotRepeatHerLine(GameTestHelper helper) {
		Villager woman = adult(helper, 45, 45, true);
		for (int lift = 0; lift < 8 && woman.isInWall(); lift++) com.vnap.Mc.snap(woman, woman.getX(), woman.getY() + 1.0, woman.getZ());
		Checks.assertTrue(helper, Speech.line(woman, "Armorer at Work", "test:work:a:" + woman.getUUID(), true),
				"she says her work line: " + Speech.lastRefusal());
		helper.runAfterDelay(DialogueCatalog.durationTicks("w_work") + 40L, () -> {
			Checks.assertTrue(helper, !Speech.line(woman, "Farmer at Work", "test:work:b:" + woman.getUUID(), true)
					&& Speech.lastRefusal().equals("cooldown"), "the same line again within its cooldown: " + Speech.lastRefusal());
			helper.succeed();
		});
	}

	@GameTest(template = "platform")
	public static void familyBedsAreBookedAtOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Couple couple = couple(helper, new BlockPos(20, 2, 20), true);
		BlockPos herBed = ((VillagerLifeData) couple.wife).vnap$homeBed();
		Checks.assertTrue(helper, herBed != null && level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), herBed, 0,
				PoiManager.Occupancy.IS_OCCUPIED).findAny().isPresent(), "the wife's new bed is booked, no neighbour can take it");
		Villager child = Family.birth(level, couple.wife, couple.husband);
		BlockPos kidBed = child == null ? null : ((VillagerLifeData) child).vnap$homeBed();
		BlockPos hisBed = ((VillagerLifeData) couple.husband).vnap$homeBed();
		Checks.assertTrue(helper, kidBed != null && hisBed != null && herBed != null && !VillagerHome.sameBed(level, kidBed, hisBed)
				&& !VillagerHome.sameBed(level, kidBed, herBed), "the baby gets a bed of its own");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aCoupleThatMayHaveAChildIsGivenBreadForIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Couple couple = couple(helper, new BlockPos(60, 2, 60), true);
		String why = Family.whyNoChild(level, couple.wife, couple.husband);
		if (why != null) {
			Checks.fail(helper, "the couple may have a child: " + why);
			return;
		}
		long day = Family.day(level);
		for (Villager one : List.of(couple.husband, couple.wife)) {
			one.getInventory().clearContent();
			Family.state(one).tickedDay = -1L;
			Family.tick(level, one, day);
		}
		Checks.assertTrue(helper, couple.husband.canBreed() && couple.wife.canBreed(), "both are fed for a child, nothing bought");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void theNeighboursSignIsLeftAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Couple couple = couple(helper, new BlockPos(40, 2, 20), true);
		layHouse(helper, new BlockPos(46, 2, 20), true);
		Villager neighbour = adult(helper, 46, 16, false);
		VillagerHome.tick(level, neighbour, 1L);
		String name = VillagerHome.nameOf(neighbour);
		Family.rewriteSign(level, couple.husband);
		Checks.assertTrue(helper, name.equals(signLine(helper, new BlockPos(46, 2, 20), 0, 2)), "the neighbour's sign keeps its name");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aWeddingWithoutAHouseIsPutOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager man = adult(helper, 60, 20, false);
		Villager woman = adult(helper, 62, 20, true);
		long day = Family.day(level);
		Family.startCourting(man, woman, day - 5);
		Family.state(man).weddingDay = day;
		Family.state(woman).weddingDay = day;
		Family.tick(level, woman, day);
		Checks.assertTrue(helper, Family.state(woman).weddingDay == -1L && man.getUUID().equals(Family.state(woman).courting)
				&& !Family.state(woman).married(), "no house, no wedding: they court on");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aOneSidedCourtshipEndsWithoutAWedding(GameTestHelper helper) {
		// the husband gave her up while she was out of loaded chunks and married another: she must not marry him too
		ServerLevel level = helper.getLevel();
		Couple couple = couple(helper, new BlockPos(20, 2, 60), true);
		Villager bride = adult(helper, 24, 66, true);
		long day = Family.day(level);
		Family.state(bride).courting = couple.husband.getUUID();
		Family.state(bride).courtStartDay = day - 5;
		Family.state(bride).weddingDay = day;
		Family.tick(level, bride, day);
		Checks.assertTrue(helper, !Family.state(bride).married() && Family.state(bride).courting == null
				&& couple.wife.getUUID().equals(Family.state(couple.husband).spouse), "she lets him go, he stays her neighbour's husband");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aHeadWhoLostTheBedMovesAndTheFamilyFollows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Couple couple = couple(helper, new BlockPos(80, 2, 20), true);
		layHouse(helper, new BlockPos(86, 2, 20), true);
		BlockPos bed = ((VillagerLifeData) couple.husband).vnap$homeBed();
		if (bed == null) {
			Checks.fail(helper, "the husband has a bed");
			return;
		}
		level.setBlock(bed, Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(bed.south(), Blocks.AIR.defaultBlockState(), 3);
		long day = Family.day(level);
		VillagerHome.tick(level, couple.husband, day);
		VillagerHome.noteStructureChange();
		VillagerHome.tick(level, couple.husband, day);
		Checks.assertTrue(helper, Family.isHead(couple.husband) && !Family.member(couple.husband), "the husband claims the other house");
		Family.state(couple.wife).tickedDay = -1L;
		Family.tick(level, couple.wife, day);
		BlockPos newHome = Family.home(couple.husband);
		Checks.assertTrue(helper, newHome != null && newHome.equals(Family.state(couple.wife).home), "the wife follows him there");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aWidowWithoutABedTakesTheHousesBed(GameTestHelper helper) {
		Couple couple = couple(helper, new BlockPos(20, 2, 40), false);
		BlockPos anchor = ((VillagerLifeData) couple.husband).vnap$homeBed();
		com.vnap.Mc.kill(couple.husband);
		Checks.assertTrue(helper, Family.isHead(couple.wife) && anchor != null && anchor.equals(((VillagerLifeData) couple.wife).vnap$homeBed()),
				"the widow sleeps in the house's bed and heads the house");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aSpouseWhoDiedFarAwayStillLeavesAWidow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Couple couple = couple(helper, new BlockPos(40, 2, 40), true);
		FamilyDeaths.get(level.getServer()).add(couple.husband.getUUID());
		Family.state(couple.wife).tickedDay = -1L;
		long day = Family.day(level);
		Family.tick(level, couple.wife, day);
		Checks.assertTrue(helper, !Family.state(couple.wife).married() && Family.state(couple.wife).widowedDay == day,
				"a death recorded elsewhere widows her on her next day");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void pitchStretchesTheLine(GameTestHelper helper) {
		for (int i = 0; i < 8; i++) {
			Villager villager = adult(helper, 60 + i, 40, false);
			float pitch = VoicePitch.of(villager);
			Checks.assertTrue(helper, pitch >= 0.92F && pitch <= 1.08F, "a pitch near 1, was " + pitch);
			Checks.assertTrue(helper, VoicePitch.stretchTicks(100L, pitch) >= (long) Math.ceil(100.0 / pitch), "the line lasts as long as it sounds");
		}
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void giftsAndMatchmaking(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		layHouse(helper, new BlockPos(80, 2, 40), true);
		Villager man = adult(helper, 80, 36, false);
		Villager woman = adult(helper, 84, 36, true);
		VillagerHome.tick(level, man, 1L);
		GlobalPos bell = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(82, 2, 30)));
		for (Villager villager : new Villager[]{man, woman}) {
			villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, bell);
			Family.state(villager).widowedDay = Long.MIN_VALUE / 2;
		}
		Checks.assertTrue(helper, Family.give(level, player, man, new ItemStack(LifeItems.WEDDING_RING)), "a ring finds the man a match");
		Checks.assertTrue(helper, woman.getUUID().equals(Family.state(man).courting) && Family.state(man).weddingDay == Family.day(level),
				"and the wedding is today");
		Villager kid = adult(helper, 86, 36, false);
		kid.setBaby(true);
		Checks.assertTrue(helper, Family.give(level, player, kid, new ItemStack(LifeItems.WOODEN_TOY))
				&& kid.getMainHandItem().is(LifeItems.WOODEN_TOY), "a child takes the toy and keeps it in hand");
		Checks.assertTrue(helper, !Family.give(level, player, kid, new ItemStack(Items.STICK)), "a stick is no present");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void homeDecorIsRegistered(GameTestHelper helper) {
		Checks.assertTrue(helper, LifeBlocks.HOME_DECOR.size() == 19, "nineteen home and town decor blocks (the market counter, the pot, the stall crate, the door bell and the sign too), have " + LifeBlocks.HOME_DECOR.size());
		for (DecorBlock block : LifeBlocks.HOME_DECOR) {
			Checks.assertTrue(helper, BuiltInRegistries.BLOCK.getKey(block).getPath().equals(block.name()), "registered as " + block.name());
			Checks.assertTrue(helper, block.profession().equals("home") || block.profession().equals("town"), block.name() + " is home or town decor");
			Checks.assertTrue(helper, !new ItemStack(block).isEmpty(), block.name() + " has an item");
			Checks.assertTrue(helper, modelLoads(block.name()), block.name() + "'s model turns only in 22.5 degree steps");
		}
		Checks.assertTrue(helper, LifeItems.HOME_MEAL.components().has(net.minecraft.core.component.DataComponents.FOOD), "a home meal is food");
		helper.succeed();
	}

	/** A house the villagers built grows an extension room: the doorway is carved and the youngest child moves in. */
	@GameTest(batch = "homes", template = "home_platform", timeoutTicks = 400)
	public static void aGrowingFamilyGrowsItsHouseALevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		helper.setBlock(new BlockPos(50, 2, 50), Blocks.BELL);
		Villager husband = com.vnap.Mc.create(EntityType.VILLAGER, level);
		if (husband == null) {
			Checks.fail(helper, "a villager");
			return;
		}
		com.vnap.Mc.snap(husband, net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(52, 2, 52))));
		husband.setNoAi(true);
		level.addFreshEntity(husband);
		((VillagerLifeData) husband).vnap$setFemale(false);
		Family.state(husband).genderKnown = true;
		husband.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		husband.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, -5L);
		HouseProject house = HomeProjects.start(level, husband, 0L);
		if (house == null) {
			Checks.fail(helper, "a site for the house");
			return;
		}
		BuildJob job = house.job(level);
		// set at once for the test: no stall to buy the materials at
		job.site = null;
		while (!job.done()) {
			job.skipDone(level);
			job.placeNext(level, null);
		}
		HomeProjects.finish(level, house);
		BlockPos anchor = ((VillagerLifeData) husband).vnap$homeBed();
		Villager wife = com.vnap.Mc.create(EntityType.VILLAGER, level);
		if (wife == null || anchor == null) {
			Checks.fail(helper, "the husband moved in");
			return;
		}
		com.vnap.Mc.snap(wife, net.minecraft.world.phys.Vec3.atBottomCenterOf(anchor.above()));
		wife.setNoAi(true);
		level.addFreshEntity(wife);
		((VillagerLifeData) wife).vnap$setFemale(true);
		Family.state(wife).genderKnown = true;
		Checks.assertTrue(helper, Family.marry(level, husband, wife), "they marry");
		Villager baby = Family.birth(level, wife, husband);
		Checks.assertTrue(helper, baby != null, "a baby is born");
		Checks.assertTrue(helper, FamilyRooms.wantsRoom(level, husband), "a child and no room yet: the family wants one");
		// the newborn's first day in the cradle is over (a house grows round a sleeping baby no sooner)
		if (baby != null) {
			Family.state(baby).infantUntil = 0L;
			com.vnap.dialogue.Infants.tick(level, baby, Family.day(level));
		}
		Checks.assertTrue(helper, FamilyRooms.start(level, husband, Family.day(level)), "the house grows its next level: " + house.plan + " " + com.vnap.village.home.HouseLevels.refusal);
		// the plans have levels now: the house is rebuilt into its bedroom level in place of a room on its side
		HouseProject grown = VillageMoodSavedData.get(level).houses().stream()
				.filter(h -> !h.done && h.owner.equals(husband.getUUID())).findFirst().orElse(null);
		if (grown == null || baby == null) {
			Checks.fail(helper, "a level being built");
			return;
		}
		Checks.assertTrue(helper, grown.plan.endsWith("_l2") && grown.origin.equals(house.origin), "the next level, in the same place: " + grown.plan);
		while (!grown.clearing.isEmpty()) HomeProjects.clearOne(level, grown, grown.clearing.get(0));
		BuildJob level2 = grown.job(level);
		level2.site = null;
		while (!level2.done()) {
			level2.skipDone(level);
			if (!level2.done()) level2.placeNext(level, null);
		}
		HomeProjects.finish(level, grown);
		Checks.assertTrue(helper, anchor.equals(((VillagerLifeData) husband).vnap$homeBed()), "the head keeps his bed");
		Checks.assertTrue(helper, Family.needsBed(level, husband) == null, "everyone of the family has a bed in the grown house");
		Checks.assertTrue(helper, com.vnap.village.home.HouseLevels.beds(level, grown) >= 3, "the bedroom's beds stand");
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		helper.succeed();
	}

	/**
	 * Every house plan: after the wedding the wife's bed and then a child's bed still fit, and no bed ever cuts the
	 * floor in two (in the loft the first free cells by the bed are the only way to the back of the house).
	 */
	@GameTest(batch = "homes", template = "home_platform", timeoutTicks = 200)
	public static void aFamilyFitsInEveryPlan(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		java.util.List<String> failed = new java.util.ArrayList<>();
		int x = 4;
		int z = 60;
		for (String plan : HomeGameTests.PLANS) {
			String name = "house/" + plan + "@plains";
			com.vnap.village.script.SchematicSpec spec = com.vnap.village.workshop.Workshops.schematic(level, name);
			if (x + spec.sizeX > 96) {
				x = 4;
				z = 20;
			}
			BlockPos origin = helper.absolutePos(new BlockPos(x, 1, z));
			BuildJob job = BuildJob.all(name, spec, origin, net.minecraft.world.level.block.Rotation.NONE, 0);
			while (!job.done()) job.placeNext(level, null);
			x += spec.sizeX + 4;
			BlockPos rel = com.vnap.village.home.HouseProject.ownerBed(spec);
			BlockPos bed = rel == null ? null : BuildJob.at(origin, net.minecraft.world.level.block.Rotation.NONE, rel);
			Villager husband = com.vnap.Mc.create(EntityType.VILLAGER, level);
			Villager wife = com.vnap.Mc.create(EntityType.VILLAGER, level);
			if (bed == null || husband == null || wife == null) {
				failed.add(plan + ": no bed");
				continue;
			}
			for (Villager villager : new Villager[]{husband, wife}) {
				com.vnap.Mc.snap(villager, net.minecraft.world.phys.Vec3.atBottomCenterOf(bed.above()));
				villager.setNoAi(true);
				level.addFreshEntity(villager);
				((VillagerLifeData) villager).vnap$setFemale(villager.equals(wife));
				Family.state(villager).genderKnown = true;
			}
			if (!VillagerHome.claim(level, husband, bed) || !Family.marry(level, husband, wife)) {
				failed.add(plan + ": no wedding");
				continue;
			}
			String why = Family.whyNoChild(level, wife, husband);
			if (((VillagerLifeData) wife).vnap$homeBed() == null) failed.add(plan + ": no bed for the wife");
			else if (why != null) failed.add(plan + ": " + why);
			else if (Family.birth(level, wife, husband) == null) failed.add(plan + ": no baby");
		}
		Checks.assertTrue(helper, failed.isEmpty(), "every plan fits a family: " + failed);
		helper.succeed();
	}

	/** With AI a head carries a bed in for his wife and sets it down himself. */
	@GameTest(batch = "family_life", template = "platform", timeoutTicks = 1400, required = false)
	public static void aHusbandBuildsHisWifeABedByHand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.onEachTick(() -> com.vnap.Mc.setDayTime(level, MORNING));
		Couple couple = couple(helper, new BlockPos(60, 2, 60), true);
		BlockPos herBed = ((VillagerLifeData) couple.wife).vnap$homeBed();
		if (herBed != null) {
			level.setBlock(herBed, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(BlockPos.of(herBed.asLong()).relative(Direction.NORTH), Blocks.AIR.defaultBlockState(), 3);
			((VillagerLifeData) couple.wife).vnap$setHomeBed(null);
		}
		couple.husband.setNoAi(false);
		couple.husband.refreshBrain(level);
		// the bed is bought at the stall first; here he has it already
		couple.husband.getInventory().addItem(new ItemStack(Items.WHITE_BED));
		helper.succeedWhen(() -> {
			BlockPos bed = ((VillagerLifeData) couple.wife).vnap$homeBed();
			Checks.assertTrue(helper, bed != null && level.getBlockState(bed).getBlock() instanceof BedBlock, "the wife has a bed again");
		});
	}

	/** Shut in a pen, down a crevice: a villager that cannot walk to its bed at night is set beside it unseen. */
	@GameTest(batch = "life_night", template = "platform", timeoutTicks = 1600)
	public static void aVillagerStuckAtNightGetsHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.onEachTick(() -> com.vnap.Mc.setDayTime(level, 18000L));
		BlockPos foot = new BlockPos(70, 2, 80);
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.north(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		BlockPos box = new BlockPos(56, 2, 80);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			helper.setBlock(box.relative(side), Blocks.STONE);
			helper.setBlock(box.relative(side).above(), Blocks.STONE);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, box.getX(), box.getY(), box.getZ());
		BlockPos bed = helper.absolutePos(foot.north());
		((VillagerLifeData) villager).vnap$setHomeBed(bed);
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
		helper.succeedWhen(() -> Checks.assertTrue(helper, villager.blockPosition().closerThan(bed, 3.0), "the villager is by its bed"));
	}

	/** By day the same: a villager walled in below a flower it walks for is set by its bed, not left there all day. */
	@GameTest(batch = "life_day", template = "platform", timeoutTicks = 1600)
	public static void aVillagerStuckByDayGetsOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.onEachTick(() -> com.vnap.Mc.setDayTime(level, 5000L));
		BlockPos foot = new BlockPos(70, 2, 90);
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.north(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		BlockPos box = new BlockPos(56, 2, 90);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			helper.setBlock(box.relative(side), Blocks.STONE);
			helper.setBlock(box.relative(side).above(), Blocks.STONE);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, box.getX(), box.getY(), box.getZ());
		villager.setNoAi(true);
		BlockPos bed = helper.absolutePos(foot.north());
		((VillagerLifeData) villager).vnap$setHomeBed(bed);
		helper.onEachTick(() -> villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
				new net.minecraft.world.entity.ai.memory.WalkTarget(helper.absolutePos(box.east(8)), 0.6F, 1)));
		helper.succeedWhen(() -> Checks.assertTrue(helper, villager.blockPosition().closerThan(bed, 3.0), "the villager is out, by its bed"));
	}

	/** Powder snow by a mountain door froze two villagers to death in a dev world: they are set on firm ground now. */
	@GameTest(template = "platform", timeoutTicks = 400)
	public static void aVillagerIsPulledOutOfPowderSnow(GameTestHelper helper) {
		BlockPos snow = new BlockPos(30, 2, 80);
		helper.setBlock(snow.below(), Blocks.POWDER_SNOW);
		helper.setBlock(snow, Blocks.POWDER_SNOW);
		Villager villager = helper.spawn(EntityType.VILLAGER, snow.getX(), snow.getY() - 1, snow.getZ());
		helper.succeedWhen(() -> Checks.assertTrue(helper, !villager.isInPowderSnow && villager.isAlive()
				&& !helper.getLevel().getBlockState(villager.blockPosition()).is(Blocks.POWDER_SNOW), "out of the snow"));
	}

	/** With AI a wife cooks breakfast at the hearth from the pantry and brings it to her husband: bread lands in his pockets. */
	@GameTest(batch = "family_life", template = "platform", timeoutTicks = 1800)
	public static void aWifeCooksAndFeedsHerHusband(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.onEachTick(() -> com.vnap.Mc.setDayTime(level, MORNING));
		BlockPos door = new BlockPos(80, 2, 60);
		Couple couple = couple(helper, door, true);
		DecorBlock hearth = LifeBlocks.HOME_DECOR.stream().filter(block -> block.name().equals("hearth")).findFirst().orElse(null);
		DecorBlock crate = LifeBlocks.HOME_DECOR.stream().filter(block -> block.name().equals("pantry_crate")).findFirst().orElse(null);
		if (hearth == null || crate == null) {
			Checks.fail(helper, "a hearth and a pantry crate block");
			return;
		}
		helper.setBlock(door.offset(-2, 0, -2), hearth.defaultBlockState());
		helper.setBlock(door.offset(-3, 0, -5), crate.defaultBlockState());
		BlockPos anchor = Family.home(couple.husband);
		DecorBlockEntity pantry = anchor == null ? null : Pantry.of(level, anchor);
		if (pantry == null) {
			Checks.fail(helper, "the wife finds the pantry by the room");
			return;
		}
		pantry.items.addItem(new ItemStack(Items.BREAD, 3));
		couple.wife.setNoAi(false);
		couple.wife.refreshBrain(level);
		helper.succeedWhen(() -> Checks.assertTrue(helper, couple.husband.getInventory().countItem(Items.BREAD) > 0,
				"the husband got his share of breakfast"));
	}

	// ---- helpers --------------------------------------------------------------------------

	/** Minecraft rejects an element rotation other than -45..45 in 22.5 steps and shows the missing model. */
	static boolean modelLoads(String name) {
		String path = "/assets/villager_life/models/block/decor/" + name + ".json";
		try (java.io.InputStream stream = FamilyLifeGameTests.class.getResourceAsStream(path)) {
			if (stream == null) return false;
			com.google.gson.JsonObject model = com.google.gson.JsonParser.parseReader(
					new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
			for (com.google.gson.JsonElement element : model.getAsJsonArray("elements")) {
				com.google.gson.JsonObject rotation = element.getAsJsonObject().getAsJsonObject("rotation");
				if (rotation == null) continue;
				float angle = rotation.get("angle").getAsFloat();
				if (Math.abs(angle) > 45.0F || Math.abs(angle % 22.5F) > 0.001F) return false;
			}
			return true;
		} catch (java.io.IOException exception) {
			return false;
		}
	}

	record Couple(Villager husband, Villager wife) {
	}

	/** A south door, a bed north of it, maybe a roof; the husband claims it and marries; NoAI beds come at once. */
	static Couple couple(GameTestHelper helper, BlockPos door, boolean roof) {
		ServerLevel level = helper.getLevel();
		layHouse(helper, door, roof);
		Villager husband = adult(helper, door.getX(), door.getZ() - 4, false);
		Villager wife = adult(helper, door.getX() + 2, door.getZ() - 4, true);
		VillagerHome.tick(level, husband, 1L);
		Family.marry(level, husband, wife);
		return new Couple(husband, wife);
	}

	static Villager adult(GameTestHelper helper, int x, int z, boolean female) {
		Villager villager = helper.spawn(EntityType.VILLAGER, x, 2, z);
		villager.setNoAi(true);
		((VillagerLifeData) villager).vnap$setFemale(female);
		FamilyState state = Family.state(villager);
		state.genderKnown = true;
		return villager;
	}

	static void layHouse(GameTestHelper helper, BlockPos door, boolean roof) {
		helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		BlockPos foot = door.north(2);
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.north(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		if (!roof) return;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -6; dz <= 1; dz++) {
				helper.setBlock(door.offset(dx, 3, dz), Blocks.STONE.defaultBlockState());
			}
		}
	}

	private static String signLine(GameTestHelper helper, BlockPos door, int line, int reach) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(door);
		for (BlockPos pos : BlockPos.betweenClosed(abs.offset(-reach, -1, -reach), abs.offset(reach, 2, reach))) {
			if (level.getBlockEntity(pos) instanceof SignBlockEntity sign
					&& VillagerHome.isMarker(com.vnap.Mc.signLine(sign, 1))) {
				return com.vnap.Mc.signLine(sign, line);
			}
		}
		return "";
	}
}
