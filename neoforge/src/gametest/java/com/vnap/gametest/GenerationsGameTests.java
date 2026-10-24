package com.vnap.gametest;

import com.vnap.block.DecorBlock;
import com.vnap.block.LifeBlocks;
import com.vnap.block.decor.DecorBlockEntity;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyDeaths;
import com.vnap.dialogue.FamilyEvents;
import com.vnap.dialogue.FamilyState;
import com.vnap.dialogue.Generations;
import com.vnap.entity.VillagerLifeData;
import com.vnap.village.home.Graveyard;
import com.vnap.village.home.Neighbours;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;
import com.vnap.item.LifeItems;
import com.vnap.mood.MoodConfig;
import com.vnap.village.home.Hamlets;
import com.vnap.village.life.Pantry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

import static com.vnap.gametest.FamilyLifeGameTests.adult;
import static com.vnap.gametest.FamilyLifeGameTests.couple;

/** Balance and the turn of generations: gifts that cannot be farmed, a longer childhood, a village that stops growing. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class GenerationsGameTests {
	@GameTest(template = "platform")
	public static void giftsCannotBeFarmed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Villager kid = adult(helper, 10, 10, false);
		kid.setBaby(true);
		Checks.assertTrue(helper, Family.give(level, player, kid, new ItemStack(LifeItems.WOODEN_TOY)), "the first toy is a joy");
		kid.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		Checks.assertTrue(helper, !Family.give(level, player, kid, new ItemStack(LifeItems.WOODEN_TOY)), "a second toy is not taken");

		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(30, 2, 30), true);
		Villager baby = Family.birth(level, couple.wife(), couple.husband());
		Checks.assertTrue(helper, baby != null, "a baby is born");
		Checks.assertTrue(helper, Family.give(level, player, couple.wife(), new ItemStack(LifeItems.BABY_BLANKET)), "a blanket for the newborn");
		Checks.assertTrue(helper, !Family.give(level, player, couple.wife(), new ItemStack(LifeItems.BABY_BLANKET)), "one blanket a baby");

		Villager man = adult(helper, 60, 10, false);
		Villager woman = adult(helper, 62, 10, true);
		long day = Family.day(level);
		Family.startCourting(man, woman, day);
		Checks.assertTrue(helper, Family.give(level, player, woman, new ItemStack(Items.POPPY)), "a flower speeds the courtship up");
		Checks.assertTrue(helper, !Family.give(level, player, man, new ItemStack(Items.DANDELION)), "but only one flower a day counts");
		Checks.assertTrue(helper, Family.state(woman).courtStartDay == day - 1, "by exactly one day");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void oneDinnerPerVillagePerDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GlobalPos bell = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(50, 2, 50)));
		Villager first = adult(helper, 48, 48, true);
		Villager second = adult(helper, 52, 52, true);
		Villager elsewhere = adult(helper, 80, 80, true);
		first.getBrain().setMemory(MemoryModuleType.MEETING_POINT, bell);
		second.getBrain().setMemory(MemoryModuleType.MEETING_POINT, bell);
		elsewhere.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(90, 2, 90))));
		UUID player = UUID.randomUUID();
		long day = Family.day(level);
		Checks.assertTrue(helper, Family.dinnerToday(first, player, day), "the first wife invites the player");
		Checks.assertTrue(helper, !Family.dinnerToday(second, player, day), "her neighbour does not: one meal a village");
		Checks.assertTrue(helper, Family.dinnerToday(elsewhere, player, day), "another village feeds them too");
		Checks.assertTrue(helper, Family.dinnerToday(second, player, day + 1), "and tomorrow is another day");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void childhoodLastsDays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(20, 2, 20), true);
		Villager baby = Family.birth(level, couple.wife(), couple.husband());
		Checks.assertTrue(helper, baby != null && baby.getAge() <= -24000 * MoodConfig.childhoodDays() + 20,
				"a child stays a child for " + MoodConfig.childhoodDays() + " days, age " + (baby == null ? 0 : baby.getAge()));
		helper.succeed();
	}

	/** A newborn's first day in a cradle by its parents' bed, its mother by it; the next morning it is a child like the others. */
	@GameTest(batch = "life_day", template = "platform")
	public static void aNewbornSpendsItsFirstDayInACradle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, 3000L);
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(20, 2, 20), true);
		Villager baby = Family.birth(level, couple.wife(), couple.husband());
		if (baby == null) {
			Checks.fail(helper, "a baby is born");
			return;
		}
		com.vnap.dialogue.FamilyState family = Family.state(baby);
		BlockPos cradle = family.cradle;
		if (cradle == null) {
			Checks.fail(helper, "a cradle is set down");
			return;
		}
		Checks.assertTrue(helper, com.vnap.dialogue.Infants.infant(baby) && com.vnap.dialogue.Infants.isCradle(level.getBlockState(cradle)),
				"the newborn has a cradle in the room");
		Checks.assertTrue(helper, com.vnap.dialogue.Infants.lying(baby) && baby.isInvulnerable() && baby.isNoGravity()
				&& baby.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(cradle)) <= 0.25,
				"and lies in it: drawn by the cradle, out of harm's way");
		Checks.assertTrue(helper, !baby.isPickable() && !baby.isPushable(), "nobody hits, picks or pushes it: clicks reach the cradle");
		com.vnap.Mc.hurt(baby, level.damageSources().generic(), 100.0F);
		Checks.assertTrue(helper, baby.isAlive(), "nothing hurts it there");
		Checks.assertTrue(helper, !com.vnap.dialogue.Infants.infant(couple.husband()) && com.vnap.village.home.Towns.population(level, couple.wife().blockPosition()) >= 3,
				"it counts in the village's head count");
		Checks.assertTrue(helper, baby.getAge() <= -24000 * (MoodConfig.childhoodDays() + MoodConfig.infantDays()) + 20,
				"the cradle day comes before the childhood days, age " + baby.getAge());
		Checks.assertTrue(helper, cradle.equals(com.vnap.dialogue.Infants.cradleOf(level, couple.wife())), "the mother minds it");
		Checks.assertTrue(helper, family.infantUntil - level.getGameTime() > 24000L * MoodConfig.infantDays() - 20,
				"a whole day of ticks in the cradle, whatever the day's number (sleeping the night away splits the two)");
		Checks.assertTrue(helper, com.vnap.dialogue.Infants.tick(level, baby, Family.day(level) + 1), "the next day's number alone does not get it up");
		Checks.assertTrue(helper, com.vnap.dialogue.Infants.cradleOf(level, couple.husband()) == null, "the father goes to work");
		long today = Family.day(level);
		// a reload loses an entity's invisibility: the newborn is laid in again, not left standing in the cradle
		baby.setInvisible(false);
		baby.teleportTo(cradle.getX() + 3.5, cradle.getY(), cradle.getZ() + 0.5);
		Checks.assertTrue(helper, com.vnap.dialogue.Infants.tick(level, baby, today) && com.vnap.dialogue.Infants.lying(baby)
				&& baby.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(cradle)) <= 0.25, "laid back in its cradle");
		level.removeBlock(cradle, false);
		Checks.assertTrue(helper, com.vnap.dialogue.Infants.tick(level, baby, today), "still an infant");
		BlockPos again = family.cradle;
		Checks.assertTrue(helper, again != null && com.vnap.dialogue.Infants.isCradle(level.getBlockState(again)) && com.vnap.dialogue.Infants.lying(baby)
				&& baby.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(again)) <= 0.25,
				"a cradle knocked down: the mother sets down another at once");
		family.infantUntil = level.getGameTime();
		Checks.assertTrue(helper, !com.vnap.dialogue.Infants.tick(level, baby, today), "the next morning");
		Checks.assertTrue(helper, !com.vnap.dialogue.Infants.infant(baby) && !com.vnap.dialogue.Infants.lying(baby) && !baby.isNoAi() && !baby.isInvisible() && !baby.isInvulnerable()
				&& !baby.isNoGravity() && baby.isBaby() && baby.isPickable(),
				"it is out of the cradle, a child like the others");
		Checks.assertTrue(helper, com.vnap.dialogue.Infants.cradleOf(level, couple.wife()) == null, "and the mother is free again");
		Checks.assertTrue(helper, !com.vnap.dialogue.Infants.isCradle(level.getBlockState(again)) && Family.state(baby).cradle == null,
				"the cradle was the newborn's: it is put away");
		helper.succeed();
	}

	/** A mother minding her newborn keeps by it by day; a family that moves house takes the baby out of the cradle. */
	@GameTest(batch = "life_day", template = "platform")
	public static void aMotherStaysByTheCradleAndAMoveEndsTheCradleDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, 3000L);
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(20, 2, 20), true);
		Villager baby = Family.birth(level, couple.wife(), couple.husband());
		BlockPos cradle = baby == null ? null : Family.state(baby).cradle;
		if (baby == null || cradle == null) {
			Checks.fail(helper, "a newborn in its cradle");
			return;
		}
		Villager mother = couple.wife();
		mother.teleportTo(cradle.getX() + 12.5, cradle.getY(), cradle.getZ() + 0.5);
		long today = Family.day(level);
		Family.tick(level, mother, today);
		Checks.assertTrue(helper, mother.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
				.map(walk -> walk.getTarget().currentBlockPosition().equals(cradle)).orElse(false), "a mother gone off walks back to the cradle");
		// nobody to hide or play with: the newborn is no child for games
		Checks.assertTrue(helper, com.vnap.dialogue.FamilyEvents.start(level, couple.husband(), cradle, today + 7) == null,
				"a newborn does not play hide and seek");
		Family.state(baby).home = cradle.offset(100, 0, 0);
		Checks.assertTrue(helper, !com.vnap.dialogue.Infants.tick(level, baby, today), "its family moved house");
		Checks.assertTrue(helper, !com.vnap.dialogue.Infants.infant(baby) && !com.vnap.dialogue.Infants.lying(baby), "the baby is out of the cradle and goes with them");
		helper.succeed();
	}

	/** The mother died while her newborn lies in its cradle: its father minds it. */
	@GameTest(batch = "life_day", template = "platform")
	public static void withTheMotherGoneTheFatherMindsTheCradle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, 3000L);
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(20, 2, 20), true);
		Villager baby = Family.birth(level, couple.wife(), couple.husband());
		BlockPos cradle = baby == null ? null : Family.state(baby).cradle;
		if (baby == null || cradle == null) {
			Checks.fail(helper, "a newborn in its cradle");
			return;
		}
		Checks.assertTrue(helper, com.vnap.dialogue.Infants.cradleOf(level, couple.husband()) == null, "the mother minds it, the father works");
		couple.wife().discard();
		Checks.assertTrue(helper, com.vnap.dialogue.Infants.tick(level, baby, Family.day(level)), "still in its cradle");
		Checks.assertTrue(helper, cradle.equals(com.vnap.dialogue.Infants.cradleOf(level, couple.husband())), "the father minds it now");
		helper.succeed();
	}

	/** A small room full of things: a crafting table or a pot is moved out (it drops) for the child's bed and the cradle;
	 *  a job site and a chest are never touched. */
	@GameTest(batch = "life_day", template = "platform")
	public static void aCrowdedRoomIsClearedForTheBabyNotItsJobSiteOrChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, 3000L);
		BlockPos door = new BlockPos(20, 2, 20);
		FamilyLifeGameTests.Couple couple = couple(helper, door, true);
		BlockPos anchor = Family.home(couple.husband());
		if (anchor == null) {
			Checks.fail(helper, "a home");
			return;
		}
		// the parents stand in the doorway; one cell by the bed's foot is all the floor left
		BlockPos inside = helper.absolutePos(door.north());
		com.vnap.Mc.snap(couple.husband(), inside.getX() + 1.5, inside.getY(), inside.getZ() + 0.5);
		com.vnap.Mc.snap(couple.wife(), inside.getX() - 0.5, inside.getY(), inside.getZ() + 0.5);
		BlockPos walk = helper.absolutePos(door.north(2).west());
		java.util.Set<BlockPos> doorway = com.vnap.dialogue.FamilyHome.doorwayOf(level, anchor);
		java.util.List<BlockPos> filled = new java.util.ArrayList<>();
		for (BlockPos cell : com.vnap.dialogue.FamilyHome.room(level, anchor)) {
			if (!cell.equals(walk) && !doorway.contains(cell)) filled.add(cell);
		}
		if (filled.size() < 4) {
			Checks.fail(helper, "a room to fill");
			return;
		}
		// the farthest cells hold a job site and a chest
		BlockPos composter = filled.get(filled.size() - 1);
		BlockPos chest = filled.get(filled.size() - 2);
		int tables = 0;
		for (BlockPos cell : filled) {
			if (cell.equals(composter)) level.setBlock(cell, Blocks.COMPOSTER.defaultBlockState(), 3);
			else if (cell.equals(chest)) level.setBlock(cell, Blocks.CHEST.defaultBlockState(), 3);
			else {
				level.setBlock(cell, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
				tables++;
			}
		}
		Checks.assertTrue(helper, Family.whyNoChild(level, couple.wife(), couple.husband()) == null,
				"a crowded room still has room for a child: " + Family.whyNoChild(level, couple.wife(), couple.husband()));
		Villager baby = Family.birth(level, couple.wife(), couple.husband());
		Checks.assertTrue(helper, baby != null && com.vnap.dialogue.Infants.infant(baby), "the baby is born and lies in a cradle");
		Checks.assertTrue(helper, level.getBlockState(composter).is(Blocks.COMPOSTER) && level.getBlockState(chest).is(Blocks.CHEST),
				"a job site and a chest are never moved out");
		long left = filled.stream().filter(cell -> level.getBlockState(cell).is(Blocks.CRAFTING_TABLE)).count();
		boolean dropped = !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(anchor).inflate(8.0), item -> item.getItem().is(net.minecraft.world.item.Items.CRAFTING_TABLE)).isEmpty();
		Checks.assertTrue(helper, left < tables && dropped, "crafting tables went for the baby and dropped as items: " + left + " of " + tables + " left");
		helper.succeed();
	}

	/** The village's limit holds for every birth: children and newborns count in it. */
	@GameTest(template = "platform")
	public static void newbornsCountInTheVillageLimit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(50, 2, 50), true);
		Villager baby = Family.birth(level, couple.wife(), couple.husband());
		if (baby == null || !com.vnap.dialogue.Infants.infant(baby)) {
			Checks.fail(helper, "a newborn in its cradle");
			return;
		}
		for (int i = 3; i < MoodConfig.maxVillagers(); i++) {
			Villager kid = adult(helper, 30 + (i % 10) * 3, 70 + (i / 10) * 3, i % 2 == 0);
			kid.setAge(-24000);
		}
		Family.state(couple.wife()).lastBirthDay = Long.MIN_VALUE / 2;
		Family.state(couple.husband()).lastBirthDay = Long.MIN_VALUE / 2;
		Checks.assertTrue(helper, "the village is full".equals(Family.whyNoChild(level, couple.wife(), couple.husband())),
				"children and the newborn fill the village: " + Family.whyNoChild(level, couple.wife(), couple.husband()));
		Checks.assertTrue(helper, Family.birth(level, couple.wife(), couple.husband()) == null, "no more births");
		helper.succeed();
	}

	/** Children cannot be hurt, by players or mobs; the cradle a newborn lies in cannot be broken. */
	@GameTest(template = "platform")
	public static void childrenCannotBeHurtNorACradleWithABabyBroken(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(20, 2, 20), true);
		Villager baby = Family.birth(level, couple.wife(), couple.husband());
		BlockPos cradle = baby == null ? null : Family.state(baby).cradle;
		if (baby == null || cradle == null) {
			Checks.fail(helper, "a baby in its cradle");
			return;
		}
		Checks.assertTrue(helper, com.vnap.dialogue.Infants.occupied(level, cradle), "its cradle is not to be broken");
		Villager kid = adult(helper, 30, 30, false);
		kid.setAge(-24000);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		com.vnap.Mc.hurt(kid, level.damageSources().playerAttack(player), 100.0F);
		com.vnap.Mc.hurt(kid, level.damageSources().generic(), 100.0F);
		Checks.assertTrue(helper, kid.isAlive() && kid.getHealth() == kid.getMaxHealth(), "nothing hurts a child");
		Villager grown = adult(helper, 34, 30, false);
		com.vnap.Mc.hurt(grown, level.damageSources().generic(), 1.0F);
		Checks.assertTrue(helper, grown.getHealth() < grown.getMaxHealth(), "a grown-up can be hurt as before");
		Family.state(baby).infantUntil = -1L;
		Checks.assertTrue(helper, !com.vnap.dialogue.Infants.occupied(level, cradle), "an empty cradle is an ordinary block");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aFullVillageHasNoBabies(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(50, 2, 50), true);
		Checks.assertTrue(helper, Family.whyNoChild(level, couple.wife(), couple.husband()) == null, "a young village welcomes a child");
		for (int i = 2; i < MoodConfig.maxVillagers(); i++) adult(helper, 30 + (i % 10) * 3, 70 + (i / 10) * 3, i % 2 == 0);
		Checks.assertTrue(helper, "the village is full".equals(Family.whyNoChild(level, couple.wife(), couple.husband())),
				"a full village has no more children: " + Family.whyNoChild(level, couple.wife(), couple.husband()));
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void settlersLeaveAFullVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager man = adult(helper, 20, 20, false);
		Villager woman = adult(helper, 22, 20, true);
		Family.startCourting(man, woman, Family.day(level));
		Hamlets.Settlement settlement = Hamlets.depart(level, List.of(man, woman), helper.absolutePos(new BlockPos(50, 2, 50)), false);
		try {
			Checks.assertTrue(helper, settlement != null && settlement.settlers().size() == 2, "both settlers are kept for their hamlet");
			Checks.assertTrue(helper, man.isRemoved() && woman.isRemoved(), "and leave the village for now");
		} finally {
			if (settlement != null) Hamlets.get(level.getServer()).pending().remove(settlement);
		}
		helper.succeed();
	}

	@GameTest(batch = "homes", template = "home_platform", timeoutTicks = 200)
	public static void aHamletIsFoundedWhenLoaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager man = adult(helper, 20, 20, false);
		Villager woman = adult(helper, 22, 20, true);
		UUID manId = man.getUUID();
		UUID womanId = woman.getUUID();
		Family.startCourting(man, woman, Family.day(level));
		Hamlets.Settlement settlement = Hamlets.depart(level, List.of(man, woman), helper.absolutePos(new BlockPos(50, 2, 50)), false);
		if (settlement == null) {
			Checks.fail(helper, "the settlers set off");
			return;
		}
		Hamlets.get(level.getServer()).pending().remove(settlement);
		Checks.assertTrue(helper, Hamlets.found(level, settlement), "the hamlet is founded once its spot ticks");
		Villager head = level.getEntity(manId) instanceof Villager found ? found : null;
		Checks.assertTrue(helper, head != null && level.getEntity(womanId) instanceof Villager, "the settlers came back");
		com.vnap.village.home.HouseProject house = com.vnap.mood.VillageMoodSavedData.get(level).houses().stream()
				.filter(one -> one.owner.equals(manId)).findFirst().orElse(null);
		if (house == null || head == null) {
			Checks.fail(helper, "the settlers' first house is laid out");
			return;
		}
		com.vnap.village.build.BuildJob job = house.job(level);
		Checks.assertTrue(helper, !house.done && job.remaining() > 0 && house.site.lacking(com.vnap.village.market.Materials.bill(level, job)).isEmpty(),
				"to build by hand, its materials brought along: " + job.remaining());
		while (!job.done()) {
			job.skipDone(level);
			if (!job.done()) job.placeNext(level, null);
		}
		com.vnap.village.home.HomeProjects.finish(level, house);
		Checks.assertTrue(helper, Family.isHead(head), "built, it is his house: bed " + ((VillagerLifeData) head).vnap$homeBed());
		BlockPos centre = helper.absolutePos(new BlockPos(50, 2, 50));
		boolean bell = BlockPos.betweenClosedStream(centre.offset(-18, -4, -18), centre.offset(18, 12, 18))
				.anyMatch(pos -> level.getBlockState(pos).is(Blocks.BELL));
		Checks.assertTrue(helper, bell, "a bell rings over the new hamlet");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void cookingNeedsFood(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(20, 2, 20), true);
		DecorBlock crate = LifeBlocks.HOME_DECOR.stream().filter(block -> block.name().equals("pantry_crate")).findFirst().orElse(null);
		BlockPos anchor = Family.home(couple.husband());
		if (crate == null || anchor == null) {
			Checks.fail(helper, "a pantry crate block and a family house");
			return;
		}
		helper.setBlock(new BlockPos(17, 2, 15), crate);
		DecorBlockEntity pantry = Pantry.of(level, anchor);
		if (pantry == null) {
			Checks.fail(helper, "the wife finds the pantry by the room");
			return;
		}
		Checks.assertTrue(helper, !Pantry.serve(level, couple.wife(), couple.husband(), pantry), "an empty pantry feeds nobody");
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Checks.assertTrue(helper, !pantry.mechanic().handles(pantry, player, new ItemStack(Items.STICK)), "a pantry takes no sticks");
		Checks.assertTrue(helper, pantry.mechanic().handles(pantry, player, new ItemStack(Items.BREAD)), "but takes bread");
		pantry.items.addItem(new ItemStack(Items.BREAD, 3));
		Checks.assertTrue(helper, Pantry.serve(level, couple.wife(), couple.husband(), pantry)
				&& couple.husband().getInventory().countItem(Items.BREAD) == 1, "a helping from the pantry: one loaf for the husband");
		Checks.assertTrue(helper, Pantry.food(pantry) == 2, "and the pantry has two left");
		Checks.assertTrue(helper, level.getBlockState(pantry.getBlockPos()).getValue(DecorBlock.FILL) == 1, "the crate looks a third full");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void everyoneHasAnAge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager adult = adult(helper, 20, 20, false);
		long day = Family.day(level);
		Family.tick(level, adult, day);
		FamilyState family = Family.state(adult);
		Checks.assertTrue(helper, family.bornDay <= day - MoodConfig.childhoodDays() && family.grownDay != FamilyState.UNSET && family.grownDay <= day,
				"a villager met for the first time gets a stable age, born " + family.bornDay + " grown " + family.grownDay);
		long born = family.bornDay;
		family.bornDay = Long.MIN_VALUE;
		Family.tick(level, adult, day);
		Checks.assertTrue(helper, Family.state(adult).bornDay == born, "the same age every time");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void anAdultBecomesAnElder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager adult = adult(helper, 20, 20, true);
		long day = Family.day(level);
		Family.tick(level, adult, day);
		double speed = adult.getAttributeValue(Attributes.MOVEMENT_SPEED);
		Family.state(adult).lived = MoodConfig.elderDays() - 1;
		Family.state(adult).tickedDay = -1L;
		Family.tick(level, adult, day);
		Checks.assertTrue(helper, Generations.elder(adult), "after her adult years she is an elder: grown " + Family.state(adult).grownDay
				+ " day " + day + " ticked " + Family.state(adult).tickedDay + " born " + Family.state(adult).bornDay
				+ " baby " + adult.isBaby() + " name " + adult.getName().getString());
		Checks.assertTrue(helper, adult.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed, "and walks slower");
		helper.succeed();
	}

	/** A villager of the town at this bell, grown, {@code lived} days of it lived. */
	private static Villager townsman(GameTestHelper helper, BlockPos bell, int x, int z, boolean female, int lived) {
		Villager villager = adult(helper, x, z, female);
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(helper.getLevel().dimension(), bell));
		FamilyState state = Family.state(villager);
		state.grownDay = Family.day(helper.getLevel()) - lived;
		state.bornDay = state.grownDay - MoodConfig.childhoodDays();
		state.lived = lived;
		return villager;
	}

	private static BlockPos bellWith(GameTestHelper helper, int people) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(50, 2, 50), Blocks.BELL);
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		com.vnap.village.home.Mayors.Office office = com.vnap.village.home.Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		office.residents.clear();
		long day = Family.day(level);
		for (int i = 0; i < people; i++) office.residents.put(UUID.randomUUID(), day);
		return bell;
	}

	@GameTest(template = "platform")
	public static void aSmallTownDoesNotGrowOld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long day = Family.day(level);
		BlockPos bell = bellWith(helper, 4);
		Villager villager = townsman(helper, bell, 20, 20, false, MoodConfig.elderDays() - 1);
		// a calendar of a hundred days gone by (the town lay unloaded) is not a hundred days lived
		Family.state(villager).grownDay = day - 100;
		Family.state(villager).tickedDay = -1L;
		Family.tick(level, villager, day);
		Checks.assertTrue(helper, !Generations.elder(villager) && Family.state(villager).lived == MoodConfig.elderDays() - 1,
				"nobody ages in a town of four: lived " + Family.state(villager).lived);
		bellWith(helper, com.vnap.village.home.Towns.limit(level, bell));
		Family.state(villager).tickedDay = -1L;
		Family.tick(level, villager, day);
		Checks.assertTrue(helper, Generations.elder(villager), "a full town ages: lived " + Family.state(villager).lived);
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aVillagerLoadedAgainTheSameDayDoesNotLiveItTwice(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long day = Family.day(level);
		// no bell: a villager outside a town ages every day it lives
		Villager villager = adult(helper, 20, 20, false);
		Family.state(villager).grownDay = day - 5;
		Family.state(villager).lived = 5;
		Family.state(villager).tickedDay = -1L;
		Family.tick(level, villager, day);
		int lived = Family.state(villager).lived;
		CompoundTag tag = new CompoundTag();
		com.vnap.Mc.saveWithoutId(villager, tag);
		Villager again = com.vnap.Mc.create(EntityType.VILLAGER, level);
		if (again == null) {
			Checks.fail(helper, "a villager can be created");
			return;
		}
		com.vnap.Mc.load(again, tag);
		again.setNoAi(true);
		Family.tick(level, again, day);
		Checks.assertTrue(helper, lived == 6 && Family.state(again).lived == lived,
				"a day lived once, not again after a reload: " + lived + " then " + Family.state(again).lived);
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aFullTownsEldestGoOneANightAndNeverAParentOfTheYoung(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long day = Family.day(level);
		BlockPos bell = bellWith(helper, 0);
		bellWith(helper, com.vnap.village.home.Towns.limit(level, bell));
		int old = MoodConfig.elderDays() + MoodConfig.oldAgeDays();
		Villager eldest = townsman(helper, bell, 10, 10, false, old + 9);
		Villager father = townsman(helper, bell, 14, 10, false, old + 5);
		Villager next = townsman(helper, bell, 18, 10, false, old + 1);
		Villager lonely = townsman(helper, bell, 22, 10, true, old + 20);
		int[] lived = {old + 9, old + 5, old + 1, old + 20};
		List<Villager> elders = List.of(eldest, father, next, lonely);
		for (int i = 0; i < elders.size(); i++) {
			Generations.makeElder(elders.get(i), day);
			Family.state(elders.get(i)).lived = lived[i];
			elders.get(i).setSleepingPos(elders.get(i).blockPosition());
		}
		townsman(helper, bell, 10, 20, false, 5);
		townsman(helper, bell, 14, 20, false, 5);
		townsman(helper, bell, 18, 20, true, 5);
		Villager baby = townsman(helper, bell, 22, 20, true, 0);
		baby.setAge(-24000);
		Family.state(baby).father = father.getUUID();
		for (Villager elder : List.of(lonely, next, father, eldest)) Generations.night(level, elder, day);
		Checks.assertTrue(helper, lonely.isAlive(), "the last old woman stays: one young woman is not a town's wives");
		Checks.assertTrue(helper, eldest.isRemoved(), "the eldest who may go goes");
		Checks.assertTrue(helper, father.isAlive() && next.isAlive(), "one a night");
		Generations.night(level, father, day + 1);
		Generations.night(level, next, day + 1);
		Checks.assertTrue(helper, father.isAlive(), "a father of a little one stays");
		Checks.assertTrue(helper, next.isRemoved(), "the next eldest goes the next night");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aNewTownStartsWithHouseholdsAndChildren(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long day = Family.day(level);
		BlockPos bell = bellWith(helper, 0);
		List<Villager> men = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) {
			Villager man = townsman(helper, bell, 6 + 6 * i, 10, false, 5);
			BlockPos foot = new BlockPos(6 + 6 * i, 2, 12);
			helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH)
					.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
			helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH)
					.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
			((VillagerLifeData) man).vnap$setHomeBed(helper.absolutePos(foot.south()));
			men.add(man);
		}
		List<Villager> founders = new java.util.ArrayList<>(men);
		for (int i = 0; i < 4; i++) founders.add(townsman(helper, bell, 6 + 6 * i, 22, true, 5));
		for (Villager founder : founders) founder.getPersistentData().putBoolean(com.vnap.village.market.Housing.FOUNDER, true);
		// the heads took their houses as founders: the right is spent, they are founders all the same
		for (Villager man : men) com.vnap.village.market.Housing.settled(man);
		// two met on the first day: they are wed to each other, not to the nearest
		Villager sweetheart = founders.get(founders.size() - 1);
		Family.startCourting(men.get(0), sweetheart, day);
		Family.founders(level, bell, day);
		Checks.assertTrue(helper, sweetheart.getUUID().equals(Family.state(men.get(0)).spouse), "the first day's sweethearts are wed to each other");
		long couples = founders.stream().filter(founder -> Family.state(founder).spouse != null).count() / 2;
		Checks.assertTrue(helper, couples == 3, "a third of nine founders are wed at once: " + couples + " couples");
		List<Villager> kids = level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(64), Villager::isBaby);
		long families = founders.stream().filter(founder -> Family.female(founder) && Family.state(founder).childrenBorn > 0).count();
		Checks.assertTrue(helper, families == 2, "half the couples came with children: " + families + " families, " + kids.size() + " children");
		for (Villager kid : kids) {
			FamilyState family = Family.state(kid);
			Checks.assertTrue(helper, family.mother != null && family.father != null && family.home != null, "a child knows its parents and its home");
		}
		Family.founders(level, bell, day + 1);
		Checks.assertTrue(helper, level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(64), Villager::isBaby).size() == kids.size(),
				"the next founding day adds no one");
		helper.succeed();
	}

	private static void arm(Villager villager) {
		com.vnap.spire.Heroes.wear(villager, com.vnap.spire.gear.GearPiece.SWORD,
				new net.minecraft.world.item.ItemStack(com.vnap.spire.gear.SpireGearItems.get(com.vnap.spire.gear.GearTier.ROOT, com.vnap.spire.gear.GearPiece.SWORD)));
	}

	@GameTest(template = "platform")
	public static void aHeroKeepsItsAgeAndNeverPassesOfOldAge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long day = Family.day(level);
		BlockPos bell = bellWith(helper, 0);
		bellWith(helper, com.vnap.village.home.Towns.limit(level, bell));
		int old = MoodConfig.elderDays() + MoodConfig.oldAgeDays();
		Villager hero = townsman(helper, bell, 10, 10, false, old + 20);
		Generations.makeElder(hero, day);
		Family.state(hero).lived = old + 20;
		arm(hero);
		for (int i = 0; i < 3; i++) townsman(helper, bell, 14 + 3 * i, 10, false, 5);
		Family.state(hero).tickedDay = -1L;
		Family.tick(level, hero, day);
		Checks.assertTrue(helper, Family.state(hero).lived == old + 20, "a hero's years stand still: lived " + Family.state(hero).lived);
		hero.setSleepingPos(hero.blockPosition());
		Generations.night(level, hero, day);
		Checks.assertTrue(helper, hero.isAlive(), "an elder hero past its years does not pass away in its sleep");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aTownKeepsHeroesForItsSize(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Checks.assertTrue(helper, com.vnap.spire.Heroes.most(4) == 1 && com.vnap.spire.Heroes.most(24) == 3, "a hero for every eight, one at the least");
		BlockPos bell = bellWith(helper, 4);
		Villager hero = townsman(helper, bell, 10, 10, false, 5);
		arm(hero);
		Villager next = townsman(helper, bell, 14, 10, false, 5);
		Checks.assertFalse(helper, com.vnap.spire.Heroes.room(level, next), "a town of four keeps one hero");
		Checks.assertTrue(helper, com.vnap.spire.Heroes.afterTrip(level, next, 20, true, new java.util.Random(1)) == null && !com.vnap.spire.Heroes.armed(next),
				"and no second one comes back armed, whatever it could pay");
		bellWith(helper, 24);
		Checks.assertTrue(helper, com.vnap.spire.Heroes.room(level, next), "a town of twenty-four keeps three");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void anElderPassesAwayAtNight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(20, 2, 20), true);
		long day = Family.day(level);
		Villager husband = couple.husband();
		Generations.makeElder(husband, day);
		Generations.night(level, husband, day);
		Checks.assertTrue(helper, husband.isAlive(), "an elder lives on while his years are not over");
		Family.state(husband).lived = MoodConfig.elderDays() + MoodConfig.oldAgeDays();
		BlockPos bed = ((VillagerLifeData) husband).vnap$homeBed();
		if (bed == null) {
			Checks.fail(helper, "the husband has a bed");
			return;
		}
		husband.setSleepingPos(bed); // startSleeping waits for the bedtime line
		Generations.night(level, husband, day);
		Checks.assertTrue(helper, husband.isRemoved(), "past his years he passes away in his sleep");
		Checks.assertTrue(helper, FamilyDeaths.get(level.getServer()).contains(husband.getUUID()), "the village knows he died");
		Checks.assertTrue(helper, !Family.state(couple.wife()).married() && husband.getUUID().equals(Family.state(couple.wife()).lateSpouse),
				"his wife is a widow and remembers him");
		helper.succeed();
	}

	@GameTest(template = "home_platform", timeoutTicks = 100)
	public static void theDeadGetAGrave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		helper.setBlock(new BlockPos(50, 2, 50), Blocks.BELL);
		com.vnap.village.home.Mayors.Office office = com.vnap.village.home.Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		com.vnap.village.town.Roads.Network network = com.vnap.village.town.Roads.get(level.getServer()).of(level.dimension(), office.bell);
		// the chunks round a test far out are entity-ticking a moment after it starts (the churchyard is sited on them)
		helper.runAfterDelay(10, () -> {
			Graveyard yard = Graveyard.get(level.getServer());
			Graveyard.Grave first = Graveyard.bury(level, UUID.randomUUID(), bell, "Ivan", "1 - 50");
			Graveyard.Grave second = Graveyard.bury(level, UUID.randomUUID(), bell, "Maria", "2 - 51");
			try {
				java.util.List<BlockPos> plots = com.vnap.village.town.Cemetery.plots(level, network);
				Checks.assertTrue(helper, first != null && plots.contains(first.pos), "on a plot of the churchyard at the town's edge, not by the bell");
				Checks.assertTrue(helper, first != null && com.vnap.village.home.Placements.pending(level, first.pos) && level.getBlockState(first.pos).isAir(),
						"the stone is bought and waits to be set down by hand");
				// nobody's hands about (the platform has no villagers): two days on the village sets it down itself
				for (com.vnap.village.home.Placements.Job job : java.util.List.copyOf(com.vnap.village.home.Placements.get(level.getServer()).jobs())) {
					if (first != null && job.pos().closerThan(first.pos, 12.0)) com.vnap.village.home.Placements.place(level, job);
				}
				Checks.assertTrue(helper, first != null && level.getBlockState(first.pos).getBlock() instanceof DecorBlock stone
						&& stone.name().equals("gravestone"), "a gravestone");
				boolean named = first != null && BlockPos.betweenClosedStream(first.pos.offset(-1, 0, -1), first.pos.offset(1, 0, 1))
						.anyMatch(pos -> level.getBlockEntity(pos) instanceof SignBlockEntity sign
								&& com.vnap.Mc.signLine(sign, 1).equals("Ivan"));
				Checks.assertTrue(helper, named, "with a sign that carries the name");
				Checks.assertTrue(helper, second != null && first != null && !second.pos.equals(first.pos) && plots.contains(second.pos), "the next grave takes the next plot");
			} finally {
				if (first != null) yard.graves().remove(first);
				if (second != null) yard.graves().remove(second);
			}
			helper.succeed();
		});
	}

	@GameTest(template = "platform")
	public static void aChildTakesUpFathersTrade(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager father = adult(helper, 20, 20, false);
		father.setVillagerData(com.vnap.Mc.withLevel(com.vnap.Mc.withProfession(father.getVillagerData(), VillagerProfession.FARMER), 2));
		Villager kid = adult(helper, 22, 20, true);
		Family.state(kid).father = father.getUUID();
		helper.setBlock(new BlockPos(26, 2, 20), Blocks.COMPOSTER);
		helper.runAfterDelay(5, () -> {
			Checks.assertTrue(helper, Generations.takeUpTrade(level, kid, father), "a free composter: the daughter becomes a farmer");
			Checks.assertTrue(helper, com.vnap.Mc.is(kid.getVillagerData(), VillagerProfession.FARMER)
					&& kid.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE), "with the job site as her own");
			helper.succeed();
		});
	}

	@GameTest(template = "platform", timeoutTicks = 1200)
	public static void comingOfAgeBakesACake(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(20, 2, 20), true);
		Villager kid = Family.birth(level, couple.wife(), couple.husband());
		if (kid == null) {
			Checks.fail(helper, "a child is born");
			return;
		}
		long day = Family.day(level);
		Family.tick(level, kid, day);
		kid.setAge(0);
		Family.state(kid).tickedDay = -1L;
		Family.tick(level, kid, day);
		BlockPos cake = Family.state(kid).cakePos;
		Checks.assertTrue(helper, Family.state(kid).grownDay == day, "the child came of age today");
		Checks.assertTrue(helper, cake != null && com.vnap.village.home.Placements.pending(level, cake), "a cake is bought for the party");
		Checks.assertTrue(helper, Family.member(kid), "the party is at home: moving out waits a day");
		// it waits for the mother's hands (her walk there is PlaceBehavior's, tested with the placements themselves)
		com.vnap.village.home.Placements.Job job = com.vnap.village.home.Placements.forHands(level, couple.wife(), 64, pos -> false);
		Checks.assertTrue(helper, job != null && job.pos().equals(cake) && job.state().is(Blocks.CAKE)
				&& couple.wife().getUUID().equals(job.by()), "the mother is to set the cake down: " + job);
		if (job != null) com.vnap.village.home.Placements.place(level, job);
		Checks.assertTrue(helper, cake != null && level.getBlockState(cake).is(Blocks.CAKE), "the cake on the table");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aNameTagMakesAGodparent(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(20, 2, 20), true);
		Villager baby = Family.birth(level, couple.wife(), couple.husband());
		if (baby == null) {
			Checks.fail(helper, "a child is born");
			return;
		}
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack tag = new ItemStack(Items.NAME_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal("Sunny"));
		FamilyEvents.nameTag(level, player, baby, tag);
		Checks.assertTrue(helper, player.getUUID().equals(Family.state(baby).godparent), "the player who names a baby is its godparent");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aWeddingWishLightsFireworks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager groom = adult(helper, 20, 20, false);
		Villager bride = adult(helper, 22, 20, true);
		Family.startCourting(groom, bride, Family.day(level));
		Family.state(groom).wish = "minecraft:cake";
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Checks.assertTrue(helper, !Family.give(level, player, groom, new ItemStack(Items.BREAD)), "bread is not what he asked for");
		Checks.assertTrue(helper, Family.give(level, player, groom, new ItemStack(Items.CAKE)) && Family.state(groom).wishGranted,
				"the cake he asked for grants his wish");
		BlockPos bell = helper.absolutePos(new BlockPos(21, 2, 24));
		FamilyEvents.weddingFireworks(level, groom, bride, bell);
		Checks.assertTrue(helper, !level.getEntitiesOfClass(FireworkRocketEntity.class, new AABB(bell).inflate(6.0)).isEmpty(),
				"fireworks over the bell");
		Checks.assertTrue(helper, Family.state(groom).wish.isEmpty(), "and the wish is spent");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void sleepingInTheVillageMakesANeighbour(GameTestHelper helper) {
		Neighbours neighbours = Neighbours.get(helper.getLevel().getServer());
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		UUID player = UUID.randomUUID();
		Checks.assertTrue(helper, !neighbours.is(helper.getLevel(), bell, player), "a stranger is no neighbour");
		Checks.assertTrue(helper, neighbours.add(helper.getLevel(), bell, player) && neighbours.is(helper.getLevel(), bell, player), "a night in the village makes one");
		Checks.assertTrue(helper, !neighbours.add(helper.getLevel(), bell, player), "only once");
		net.minecraft.server.level.ServerLevel nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
		Checks.assertTrue(helper, nether == null || !neighbours.is(nether, bell, player), "a bell at the same spot in another world is another village");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aQuarrelStopsBabiesUntilMakeUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(20, 2, 20), true);
		FamilyEvents.quarrel(couple.wife(), couple.husband(), Family.day(level));
		Checks.assertTrue(helper, "quarrelled".equals(Family.whyNoChild(level, couple.wife(), couple.husband())), "no baby while they quarrel");
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Checks.assertTrue(helper, Family.give(level, player, couple.husband(), new ItemStack(Items.POPPY)), "a flower from the player");
		Checks.assertTrue(helper, !Family.state(couple.wife()).quarrelling(Family.day(level)), "makes them make up");
		Checks.assertTrue(helper, Family.whyNoChild(level, couple.wife(), couple.husband()) == null, "and a baby may come again");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void hideAndSeekStarts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int step = 0; step < 72; step++) {
			double angle = Math.PI * 2.0 * step / 72.0;
			int x = 50 + (int) Math.round(Math.cos(angle) * 12);
			int z = 50 + (int) Math.round(Math.sin(angle) * 12);
			helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
			helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
		}
		Villager seeker = adult(helper, 50, 50, false);
		Villager first = adult(helper, 51, 50, true);
		Villager second = adult(helper, 50, 51, false);
		for (Villager kid : List.of(seeker, first, second)) kid.setBaby(true);
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		FamilyEvents.Game game = FamilyEvents.start(level, seeker, bell, level.getGameTime());
		Checks.assertTrue(helper, game != null && game.seeker.equals(seeker.getUUID()) && game.hiders.size() == 2, "a seeker and two hiders: "
				+ (game == null ? "no game" : game.hiders.size()) + " seeker at " + seeker.blockPosition());
		Checks.assertTrue(helper, com.vnap.village.life.Agenda.has(first, "hide") && com.vnap.village.life.Agenda.has(second, "hide"),
				"the hiders run to their hiding places (their agendas)");
		helper.succeed();
	}
}
