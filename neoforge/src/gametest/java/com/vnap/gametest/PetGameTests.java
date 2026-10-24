package com.vnap.gametest;

import com.vnap.Nbt;
import com.vnap.block.DecorBlock;
import com.vnap.block.LifeBlocks;
import com.vnap.block.decor.DecorBlockEntity;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.item.LifeItems;
import com.vnap.village.home.Pets;
import com.vnap.village.life.Pantry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.vnap.gametest.FamilyLifeGameTests.couple;

/** Family pets: a stray tamed by the children, a home by the house, the bowl, pats and lessons, the kennel. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class PetGameTests {
	@GameTest(template = "platform")
	public static void aFamilyWantsAPet(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(40, 2, 40), true);
		long day = Family.day(level);
		Checks.assertTrue(helper, Pets.whyNoPet(level, couple.husband(), day) == null, "a married family may want a pet");
		Checks.assertTrue(helper, Pets.whyNoPet(level, couple.wife(), day) != null, "the head looks for it, not the wife");
		for (int i = 0; i < 4; i++) {
			Wolf other = helper.spawn(EntityType.WOLF, 30 + i * 2, 3, 30);
			other.setNoAi(true);
			Nbt.putUUID(other.getPersistentData(), Pets.PET_HEAD, java.util.UUID.randomUUID());
		}
		Checks.assertTrue(helper, "the village has enough pets".equals(Pets.whyNoPet(level, couple.husband(), day)), "four pets are enough");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aStrayIsFoundNearby(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(40, 2, 40), true);
		Pets.Kind kind = Pets.wanted(level, couple.husband());
		EntityType<? extends TamableAnimal> type = kind == Pets.Kind.DOG ? EntityType.WOLF : kind == Pets.Kind.CAT ? EntityType.CAT : EntityType.PARROT;
		TamableAnimal wild = helper.spawn(type, 50, 3, 50);
		wild.setNoAi(true);
		TamableAnimal stray = Pets.findStray(level, couple.husband(), Family.state(couple.husband()), Family.day(level));
		Checks.assertTrue(helper, stray != null && stray.getUUID().equals(wild.getUUID()), "the wild " + kind + " nearby becomes the family's stray");
		Checks.assertTrue(helper, couple.husband().getUUID().equals(Nbt.getUUID(wild.getPersistentData(), Pets.STRAY_FOR)), "marked for this family");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void threeFeedingsTameAStray(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(40, 2, 40), true);
		Villager kid = baby(helper, couple);
		Wolf stray = stray(helper, couple, 44, 44);
		boolean first = Pets.feedStray(level, kid, stray);
		boolean second = Pets.feedStray(level, kid, stray);
		Checks.assertTrue(helper, !first && !second, "two feedings are not enough");
		Checks.assertTrue(helper, Pets.feedStray(level, kid, stray), "the third makes it the family's");
		FamilyState family = Family.state(couple.husband());
		Checks.assertTrue(helper, stray.isTame() && stray.getOwner() == null, "tame, without a player owner");
		Checks.assertTrue(helper, stray.getCollarColor() == Pets.colour(couple.husband()), "with the family's collar");
		Checks.assertTrue(helper, stray.hasCustomName() && com.vnap.Mc.hasRestriction(stray), "a name and a home");
		Checks.assertTrue(helper, stray.getUUID().equals(family.pet) && family.stray == null, "the family has its pet");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aTreatFromThePlayerHelps(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(40, 2, 40), true);
		Wolf stray = stray(helper, couple, 44, 44);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Checks.assertTrue(helper, !Pets.give(level, player, stray, new ItemStack(Items.STICK)), "a stick is no treat");
		for (int i = 0; i < Pets.FEEDINGS; i++) Pets.give(level, player, stray, new ItemStack(LifeItems.PET_TREAT));
		Checks.assertTrue(helper, stray.isTame() && stray.getUUID().equals(Family.state(couple.husband()).pet), "treats from the player tame it for the family");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aPetGoesHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(40, 2, 40), true);
		Wolf dog = adopted(helper, couple, 44, 44);
		FamilyState family = Family.state(couple.husband());
		BlockPos home = family.home;
		if (home == null) {
			Checks.fail(helper, "a family home");
			return;
		}
		dog.setNoAi(false);
		dog.teleportTo(home.getX() + 25.5, home.getY(), home.getZ() + 0.5);
		// a path is only made for a mob standing on the ground: let it land first
		helper.runAfterDelay(10, () -> {
			Pets.tick(level, couple.husband(), family, Family.day(level));
			Checks.assertTrue(helper, dog.getNavigation().getPath() != null, "a dog 25 blocks out walks home");
			dog.teleportTo(home.getX() + 70.5, home.getY(), home.getZ() + 0.5);
			Pets.tick(level, couple.husband(), family, Family.day(level));
			Checks.assertTrue(helper, dog.blockPosition().closerThan(home, 6.0), "one 70 blocks out, with nobody looking, is brought back");
			helper.succeed();
		});
	}

	@GameTest(template = "platform")
	public static void feedingFillsTheBowl(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(20, 2, 20), true);
		helper.setBlock(new BlockPos(17, 2, 15), decor("pantry_crate"));
		helper.setBlock(new BlockPos(23, 2, 15), decor("pet_bowl"));
		FamilyState family = Family.state(couple.husband());
		BlockPos home = family.home;
		DecorBlockEntity pantry = home == null ? null : Pantry.of(level, home);
		if (pantry == null) {
			Checks.fail(helper, "the pantry by the room");
			return;
		}
		pantry.items.addItem(new ItemStack(Items.BREAD, 2));
		Wolf dog = adopted(helper, couple, 23, 16);
		Checks.assertTrue(helper, Pets.fillBowl(level, couple.wife(), family), "the wife fills the bowl");
		DecorBlockEntity bowl = Pets.bowl(level, family);
		Checks.assertTrue(helper, bowl != null && level.getBlockState(bowl.getBlockPos()).getValue(DecorBlock.FILL) > 0, "the bowl looks full");
		Checks.assertTrue(helper, Pantry.food(pantry) == 1, "from the pantry");
		Pets.eatFromBowl(level, family, dog);
		Checks.assertTrue(helper, bowl != null && Pantry.food(bowl) == 0, "the dog eats it all");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void pattingAndTraining(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(40, 2, 40), true);
		Villager kid = baby(helper, couple);
		Cat cat = helper.spawn(EntityType.CAT, 44, 3, 44);
		cat.setNoAi(true);
		Pets.pet(level, kid, cat);
		Checks.assertTrue(helper, cat.isLying(), "a patted cat lies down");
		Wolf dog = adopted(helper, couple, 46, 44);
		dog.setNoAi(false);
		Pets.pet(level, kid, dog);
		Checks.assertTrue(helper, dog.isInterested(), "a patted dog tilts its head");
		Checks.assertTrue(helper, Pets.startTraining(level, kid, dog) && dog.isOrderedToSit(), "sit!");
		Pets.Training lesson = Pets.training(dog.getUUID());
		if (lesson == null) {
			Checks.fail(helper, "a lesson");
			return;
		}
		lesson.until = level.getGameTime();
		Pets.tickTraining(level.getServer());
		Checks.assertTrue(helper, lesson.stage == 1 && lesson.stick != null && level.getEntity(lesson.stick) instanceof ItemEntity, "fetch! a stick flies");
		Checks.assertTrue(helper, !dog.isOrderedToSit(), "and the dog runs for it");
		helper.succeed();
	}

	@GameTest(template = "platform", skyAccess = true)
	public static void aKennelIsBuiltByHand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(40, 2, 40), true);
		Wolf dog = adopted(helper, couple, 44, 44);
		FamilyState family = Family.state(couple.husband());
		BlockPos home = family.home;
		BlockPos spot = home == null ? null : Pets.kennelSpot(level, couple.husband(), home);
		Checks.assertTrue(helper, Pets.wantsKennel(level, couple.husband()) && spot != null, "the head finds a spot in the yard");
		Checks.assertTrue(helper, spot != null && home != null && !spot.closerThan(home, 3.5) && level.canSeeSky(spot), "outside, under the sky");
		Pets.buildKennelNow(level, couple.husband());
		BlockPos rest = Pets.kennelRest(level, family);
		BlockPos post = Pets.kennelPost(level, family);
		if (rest == null || post == null) {
			Checks.fail(helper, "a kennel stands");
			return;
		}
		Checks.assertTrue(helper, !Pets.wantsKennel(level, couple.husband()), "one kennel block");
		Checks.assertTrue(helper, level.getBlockState(post).is(BlockTags.FENCES), "with a post for the rope");
		Checks.assertTrue(helper, Pets.bowl(level, family) != null, "and the dog's bowl");
		dog.teleportTo(rest.getX() + 0.5, rest.getY(), rest.getZ() + 0.5);
		Pets.bedtime(level, dog, family, true);
		Checks.assertTrue(helper, dog.isLeashed() && dog.isOrderedToSit(), "at night the dog is tied up at its kennel");
		Pets.bedtime(level, dog, family, false);
		Checks.assertTrue(helper, !dog.isLeashed(), "and let go in the morning");
		helper.succeed();
	}

	@GameTest(template = "platform", skyAccess = true, timeoutTicks = 600)
	public static void theDogWalksToItsKennelAtNight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(40, 2, 40), true);
		Wolf dog = adopted(helper, couple, 44, 44);
		Pets.buildKennelNow(level, couple.husband());
		FamilyState family = Family.state(couple.husband());
		BlockPos rest = Pets.kennelRest(level, family);
		if (rest == null) {
			Checks.fail(helper, "a kennel");
			return;
		}
		dog.setNoAi(false);
		dog.teleportTo(rest.getX() + 12.5, rest.getY(), rest.getZ() + 0.5);
		helper.runAfterDelay(10, () -> Pets.bedtime(level, dog, family, true));
		helper.succeedWhen(() -> {
			Pets.bedtime(level, dog, family, true);
			Checks.assertTrue(helper, dog.isLeashed(), "the dog walked to its kennel by itself and is tied up, now at "
					+ helper.relativePos(dog.blockPosition()) + " rest " + helper.relativePos(rest) + " target " + Pets.target(dog)
					+ " sit " + dog.isOrderedToSit() + " path " + dog.getNavigation().getPath() + " goals "
					+ dog.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning()).map(g -> g.getGoal().getClass().getSimpleName()).toList()
					+ " around " + java.util.stream.Stream.of(net.minecraft.core.Direction.values()).map(d -> d + "=" + level.getBlockState(rest.relative(d)).getBlock())
					.toList() + " at rest " + level.getBlockState(rest).getBlock());
		});
	}

	@GameTest(template = "platform")
	public static void aPetDiesAndIsMourned(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(40, 2, 40), true);
		Wolf dog = adopted(helper, couple, 44, 44);
		com.vnap.Mc.kill(dog);
		FamilyState family = Family.state(couple.husband());
		Checks.assertTrue(helper, family.pet == null && family.petLostDay == Family.day(level), "the family lost its pet");
		Checks.assertTrue(helper, "mourns the last pet".equals(Pets.whyNoPet(level, couple.husband(), Family.day(level))), "and wants no new one for a while");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aParrotLearnsWords(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(40, 2, 40), true);
		net.minecraft.world.entity.animal.Parrot parrot = helper.spawn(EntityType.PARROT, 44, 3, 44);
		parrot.setNoAi(true);
		Nbt.putUUID(parrot.getPersistentData(), Pets.STRAY_FOR, couple.husband().getUUID());
		Pets.adopt(level, couple.husband(), parrot);
		int word = Pets.teach(level, couple.wife(), parrot);
		Checks.assertTrue(helper, word >= 1 && word <= Pets.WORDS && Pets.knownWords(parrot) == 0, "one lesson is not enough");
		for (int i = 1; i < Pets.LESSONS; i++) {
			Checks.assertTrue(helper, Pets.teach(level, couple.husband(), parrot) == word, "the family keeps teaching the same word");
		}
		Checks.assertTrue(helper, (Pets.knownWords(parrot) & (1 << (word - 1))) != 0, "after " + Pets.LESSONS + " lessons the parrot knows it");
		Checks.assertTrue(helper, com.vnap.dialogue.DialogueCatalog.byTitle("Parrot Says " + word, "parrot") != null
				&& com.vnap.dialogue.DialogueCatalog.byTitle("Woman Teaches the Parrot " + word, "woman") != null
				&& com.vnap.dialogue.DialogueCatalog.byTitle("Man Teaches the Parrot " + word, "villager") != null
				&& com.vnap.dialogue.DialogueCatalog.byTitle("Kid Teaches the Parrot " + word, "child") != null, "the lesson and the word are voiced");
		// the teachers' own lesson may still hold the floor (one voice around): they leave the parrot to it
		couple.wife().discard();
		couple.husband().discard();
		Checks.assertTrue(helper, com.vnap.dialogue.Speech.line(parrot, "Parrot Says " + word, "test:parrot", false),
				"the parrot speaks in its own voice: " + com.vnap.dialogue.Speech.lastRefusal());
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void petThingsAreRegistered(GameTestHelper helper) {
		for (String name : java.util.List.of("pet_bowl", "cat_bed", "parrot_perch", "aquarium", "dog_kennel")) {
			Checks.assertTrue(helper, decor(name) != null && FamilyLifeGameTests.modelLoads(name), name + " is home decor with a model");
		}
		Checks.assertTrue(helper, PetGameTests.class.getResource("/assets/villager_life/textures/block/decor/aquarium_water.png.mcmeta") != null,
				"the aquarium's fish swim (an animated texture)");
		Checks.assertTrue(helper, !new ItemStack(LifeItems.PET_TREAT).isEmpty(), "a pet treat item");
		helper.succeed();
	}

	// ---- helpers ------------------------------------------------------------------------

	private static Villager baby(GameTestHelper helper, FamilyLifeGameTests.Couple couple) {
		Villager kid = Family.birth(helper.getLevel(), couple.wife(), couple.husband());
		if (kid == null) throw new IllegalStateException("no baby born");
		return kid;
	}

	/** A stray the family is taming is tied to the house: kept near it, one far out brought back; the wife goes to feed it. */
	@GameTest(template = "platform")
	public static void aStrayBeingTamedKeepsNearTheHouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FamilyLifeGameTests.Couple couple = couple(helper, new BlockPos(10, 2, 10), true);
		Wolf far = stray(helper, couple, 90, 90);
		FamilyState house = Family.state(couple.husband());
		Pets.tick(level, couple.husband(), house, Family.day(level));
		BlockPos home = house.home;
		Checks.assertTrue(helper, home != null && com.vnap.Mc.hasRestriction(far) && com.vnap.Mc.restrictCenter(far).equals(home), "its strolls keep to the house");
		Checks.assertTrue(helper, home != null && far.blockPosition().closerThan(home, 40.0), "far out with nobody to see, it is set by the house: " + far.blockPosition());
		Pets.kidAmbient(level, couple.wife(), 0.2F, Family.day(level));
		Checks.assertTrue(helper, com.vnap.village.life.Agenda.has(couple.wife(), "pet_visit"), "the wife goes to feed it (her agenda)");
		helper.succeed();
	}

	private static Wolf stray(GameTestHelper helper, FamilyLifeGameTests.Couple couple, int x, int z) {
		Wolf wolf = helper.spawn(EntityType.WOLF, x, 3, z);
		wolf.setNoAi(true);
		Nbt.putUUID(wolf.getPersistentData(), Pets.STRAY_FOR, couple.husband().getUUID());
		wolf.getPersistentData().putLong(Pets.STRAY_DAY, Family.day(helper.getLevel()));
		Family.state(couple.husband()).stray = wolf.getUUID();
		return wolf;
	}

	private static Wolf adopted(GameTestHelper helper, FamilyLifeGameTests.Couple couple, int x, int z) {
		Wolf wolf = stray(helper, couple, x, z);
		Pets.adopt(helper.getLevel(), couple.husband(), wolf);
		return wolf;
	}

	private static DecorBlock decor(String name) {
		return LifeBlocks.HOME_DECOR.stream().filter(block -> block.name().equals(name)).findFirst()
				.orElseThrow(() -> new IllegalStateException("no " + name));
	}
}
