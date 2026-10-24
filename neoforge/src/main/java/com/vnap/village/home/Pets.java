package com.vnap.village.home;

import com.vnap.Nbt;
import com.vnap.village.town.TownRules;
import com.vnap.block.DecorBlock;
import com.vnap.block.decor.DecorBlockEntity;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyHome;
import com.vnap.dialogue.FamilyState;
import com.vnap.item.LifeItems;
import com.vnap.mixin.CatAccessor;
import com.vnap.mixin.WolfAccessor;
import com.vnap.progress.VillageAdvancements;
import com.vnap.village.life.Pantry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A family's pet: a dog, a cat or a parrot that belongs to the house, not to a player. A family with a child (or by
 * chance) wants one; a stray nearby (or one that wanders in while a player is about) is tamed over three feedings by
 * the children; then it lives by the house: a kennel the head builds by hand and a leash at night for a dog, a bed or a
 * perch inside for a cat or a parrot, a bowl the wife fills from the pantry, petting and training by the children.
 * Everything runs from the family tick, only where villagers tick.
 */
public final class Pets {
	public static final String STRAY_FOR = "vnap_stray_for";
	public static final String STRAY_DAY = "vnap_stray_day";
	public static final String TRUST = "vnap_trust";
	public static final String PET_HEAD = "vnap_pet_head";
	public static final String KENNEL = "dog_kennel";
	public static final int FEEDINGS = 3;
	private static final int STRAY_RADIUS = 24;
	private static final double VISIT_RANGE = STRAY_RADIUS + 16.0;
	private static final int COOLDOWN_DAYS = 5;
	private static final int STRAY_DAYS = 3;
	private static final int HOME_RADIUS = 10;
	private static final List<String> DOGS = List.of("Rex", "Buddy", "Bim", "Sharik", "Max", "Rusty", "Lucky", "Bobik");
	private static final List<String> CATS = List.of("Murka", "Barsik", "Tom", "Kitty", "Mittens", "Vaska", "Luna", "Ginger");
	private static final List<String> PARROTS = List.of("Kesha", "Polly", "Rio", "Kiwi", "Mango", "Pip");
	private static final Map<UUID, Training> TRAINING = new HashMap<>();
	/** Last day each child visited the stray or the pet; not saved. */
	private static final Map<String, Long> VISITED = new HashMap<>();

	public enum Kind { DOG, CAT, PARROT }

	private Pets() {
	}

	public static void clear() {
		TRAINING.clear();
		VISITED.clear();
	}

	/** Visits and lessons of kids and parrots gone for good are not kept: {@code today} is Family.day of the overworld. */
	public static void forgetOld(long today) {
		VISITED.values().removeIf(day -> Math.abs(today - day) > 2);
	}

	// ---- which pet, and the stray -------------------------------------------------------

	public static @Nullable Kind kindOf(Entity animal) {
		return animal instanceof Wolf ? Kind.DOG : animal instanceof Cat ? Kind.CAT : animal instanceof Parrot ? Kind.PARROT : null;
	}

	/** Jungle families keep parrots; otherwise a cat (two in five families) or a dog, the same choice every day. */
	public static Kind wanted(ServerLevel level, Villager head) {
		if (level.getBiome(head.blockPosition()).is(BiomeTags.IS_JUNGLE)) return Kind.PARROT;
		return Math.floorMod(head.getUUID().hashCode(), 5) < 2 ? Kind.CAT : Kind.DOG;
	}

	/** Why this family gets no pet now, or null when it wants one. */
	public static @Nullable String whyNoPet(ServerLevel level, Villager head, long day) {
		FamilyState family = Family.state(head);
		if (!Family.isHead(head) || !family.married()) return "not a married head of a house";
		if (family.pet != null) return "has a pet";
		if (day - family.petLostDay < COOLDOWN_DAYS) return "mourns the last pet";
		if (petsNear(level, com.vnap.village.home.Towns.bellOf(head, head.blockPosition())) >= TownRules.PETS_PER_TOWN) return "the village has enough pets";
		return null;
	}

	public static int petsNear(ServerLevel level, BlockPos bell) {
		return level.getEntitiesOfClass(TamableAnimal.class, new AABB(bell).inflate(TownRules.NEAR_BELL),
				animal -> animal.isAlive() && Nbt.hasUUID(animal.getPersistentData(), PET_HEAD)).size();
	}

	/** Daily for a head: a family with a child (or one in four others) looks for a stray to tame. */
	public static void daily(ServerLevel level, Villager head, FamilyState family, long day) {
		if (family.pet != null && day - family.petSeenDay > 7) {
			// the pet has not been seen for a week: lost somewhere unloaded for good
			family.pet = null;
			family.petLostDay = day;
		}
		if (family.stray != null) {
			Entity stray = level.getEntity(family.stray);
			if (stray == null || !stray.isAlive() || day - Nbt.getLong(stray.getPersistentData(), STRAY_DAY) > STRAY_DAYS) {
				if (stray != null) stray.getPersistentData().remove(STRAY_FOR);
				if (stray instanceof TamableAnimal animal) com.vnap.Mc.clearRestriction(animal);
				family.stray = null;
			}
			return;
		}
		if (whyNoPet(level, head, day) != null) return;
		boolean kids = Family.household(level, head).stream().anyMatch(Villager::isBaby);
		if (!kids && head.getRandom().nextFloat() >= 0.25F) return;
		// the pet's home first: the family buys the kennel (the cat's bed, the parrot's perch), then looks for a stray
		if (!com.vnap.village.market.Market.obtain(level, head, homeItem(wanted(level, head)))) return;
		findStray(level, head, family, day);
	}

	/** What a pet of this kind sleeps in: bought before a stray is taken in. */
	public static net.minecraft.world.item.Item homeItem(Kind kind) {
		return com.vnap.village.market.Market.decor(switch (kind) {
			case DOG -> "dog_kennel";
			case CAT -> "cat_bed";
			case PARROT -> "parrot_perch";
		});
	}

	/** An untamed animal of the wanted kind nearby, or one that wanders in (only while a player is about to see it). */
	public static @Nullable TamableAnimal findStray(ServerLevel level, Villager head, FamilyState family, long day) {
		return findStray(level, head, family, day, wanted(level, head));
	}

	public static @Nullable TamableAnimal findStray(ServerLevel level, Villager head, FamilyState family, long day, Kind kind) {
		BlockPos home = family.home;
		if (home == null) return null;
		TamableAnimal stray = level.getEntitiesOfClass(TamableAnimal.class, new AABB(home).inflate(32.0),
						animal -> animal.isAlive() && !animal.isTame() && kind == kindOf(animal) && !animal.isBaby()
								&& !Nbt.hasUUID(animal.getPersistentData(), STRAY_FOR))
				.stream().findFirst().orElse(null);
		if (stray == null) {
			if (level.getNearestPlayer(home.getX(), home.getY(), home.getZ(), 64.0, false) == null) return null;
			stray = spawnStray(level, home, kind, head);
		}
		if (stray == null) return null;
		Nbt.putUUID(stray.getPersistentData(), STRAY_FOR, head.getUUID());
		stray.getPersistentData().putLong(STRAY_DAY, day);
		stray.setPersistenceRequired();
		family.stray = stray.getUUID();
		tieStray(level, family, stray);
		Family.household(level, head).stream().filter(Villager::isBaby).findFirst()
				.ifPresent(kid -> Speech.queue(kid, "Kid Wants a Pet", null, 2L));
		return stray;
	}

	private static @Nullable TamableAnimal spawnStray(ServerLevel level, BlockPos home, Kind kind, Villager head) {
		EntityType<? extends TamableAnimal> type = kind == Kind.DOG ? EntityType.WOLF : kind == Kind.CAT ? EntityType.CAT : EntityType.PARROT;
		Map<BlockPos, Boolean> room = new HashMap<>();
		for (BlockPos cell : FamilyHome.room(level, home)) room.put(cell, true);
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = head.getRandom().nextDouble() * Math.PI * 2.0;
			int distance = 16 + head.getRandom().nextInt(9);
			BlockPos at = com.vnap.village.town.Rings.at(home, angle, distance);
			if (!level.isLoaded(at)) continue;
			BlockPos ground = com.vnap.village.town.Ground.top(level, at.getX(), at.getZ());
			if (ground == null || room.containsKey(ground.above()) || !level.getFluidState(ground.above()).isEmpty()) continue;
			TamableAnimal animal = com.vnap.Mc.create(type, level);
			if (animal == null) return null;
			com.vnap.Mc.snap(animal, ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5, head.getRandom().nextFloat() * 360.0F, 0.0F);
			animal.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null);
			if (level.addFreshEntity(animal)) return animal;
		}
		return null;
	}

	// ---- taming -------------------------------------------------------------------------

	/** The right food for a stray of this kind: what a child carries to it. */
	public static ItemStack food(Kind kind) {
		return new ItemStack(kind == Kind.DOG ? Items.BONE : kind == Kind.CAT ? Items.COD : Items.WHEAT_SEEDS);
	}

	/** One feeding of the stray by {@code feeder}; the third makes it the family's. True when it was adopted now. */
	public static boolean feedStray(ServerLevel level, Villager feeder, TamableAnimal stray) {
		Kind kind = kindOf(stray);
		if (kind == null || !Nbt.hasUUID(stray.getPersistentData(), STRAY_FOR)) return false;
		int trust = Nbt.getInt(stray.getPersistentData(), TRUST) + 1;
		stray.getPersistentData().putInt(TRUST, trust);
		com.vnap.Mc.swing(feeder, net.minecraft.world.InteractionHand.MAIN_HAND);
		level.sendParticles(com.vnap.Mc.itemParticle(food(kind)), stray.getX(), stray.getY() + 0.6, stray.getZ(),
				6, 0.2, 0.2, 0.2, 0.05);
		level.sendParticles(ParticleTypes.HEART, stray.getX(), stray.getY() + 1.0, stray.getZ(), trust, 0.3, 0.2, 0.3, 0.0);
		sound(level, stray, kind == Kind.DOG ? com.vnap.Mc.wolfWhine() : kind == Kind.CAT ? com.vnap.Mc.catPurreow() : SoundEvents.PARROT_AMBIENT);
		stray.getLookControl().setLookAt(feeder);
		if (trust < FEEDINGS) {
			Speech.queue(feeder, feeder.isBaby() ? "Kid Tames a Stray" : "Woman Tames a Stray", stray, 1L);
			return false;
		}
		UUID headId = Nbt.getUUID(stray.getPersistentData(), STRAY_FOR);
		if (!(level.getEntity(headId) instanceof Villager head)) return false;
		adopt(level, head, stray);
		return true;
	}

	/** The stray belongs to the house now: tame without a player owner, the family's collar, a name, a home. */
	public static void adopt(ServerLevel level, Villager head, TamableAnimal animal) {
		FamilyState family = Family.state(head);
		Kind kind = kindOf(animal);
		animal.setTame(true, false);
		animal.setPersistenceRequired();
		DyeColor colour = colour(head);
		if (animal instanceof Wolf wolf) ((WolfAccessor) wolf).vnap$setCollarColor(colour);
		if (animal instanceof Cat cat) ((CatAccessor) cat).vnap$setCollarColor(colour);
		List<String> names = kind == Kind.DOG ? DOGS : kind == Kind.CAT ? CATS : PARROTS;
		animal.setCustomName(Component.literal(names.get(Math.floorMod(animal.getUUID().hashCode(), names.size()))));
		animal.getPersistentData().remove(STRAY_FOR);
		animal.getPersistentData().remove(TRUST);
		Nbt.putUUID(animal.getPersistentData(), PET_HEAD, head.getUUID());
		attachGoal(animal);
		family.pet = animal.getUUID();
		family.stray = null;
		family.petSeenDay = Family.day(level);
		if (family.home != null) com.vnap.Mc.restrictTo(animal, family.home, HOME_RADIUS);
		for (Villager member : Family.household(level, head)) {
			if (member.isBaby() && !com.vnap.dialogue.Infants.infant(member)) {
				Speech.queue(member, "Kid Names the Pet", animal, 4L);
				break;
			}
		}
		Speech.queue(head, "Man Welcomes the Pet", animal, 10L);
		VillageAdvancements.awardNear(level, animal.blockPosition(), 32.0, "family/pet");
		level.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + 1.0, animal.getZ(), 8, 0.4, 0.3, 0.4, 0.0);
	}

	public static DyeColor colour(Villager head) {
		return DyeColor.byId(Math.floorMod(head.getUUID().hashCode() >>> 5, 16));
	}

	// ---- the pet's day ------------------------------------------------------------------

	/** Every family tick for a head: the stray and the pet are looked after. */
	public static void tick(ServerLevel level, Villager head, FamilyState family, long day) {
		if (family.stray != null && level.getEntity(family.stray) instanceof TamableAnimal stray && stray.isAlive()) tieStray(level, family, stray);
		if (family.pet == null || !(level.getEntity(family.pet) instanceof TamableAnimal pet) || !pet.isAlive()) return;
		family.petSeenDay = day;
		BlockPos home = family.home;
		if (home == null) return;
		float time = Family.dayFraction(level);
		boolean night = time >= 0.54F || level.isRaining();
		BlockPos rest = restSpot(level, family, pet);
		// vanilla strolls stay inside the restriction: pointing it at the resting place walks the pet there by itself
		boolean resting = (pet instanceof Wolf && night && kennelFacing(level, family) != null) || (pet instanceof Cat && !night && !rest.equals(home))
				|| (pet instanceof Parrot && !rest.equals(home));
		BlockPos centre = resting ? rest : home;
		int radius = resting ? 2 : HOME_RADIUS;
		if (!com.vnap.Mc.hasRestriction(pet) || !com.vnap.Mc.restrictCenter(pet).equals(centre) || com.vnap.Mc.restrictRadius(pet) != radius) com.vnap.Mc.restrictTo(pet, centre, radius);
		double far = pet.blockPosition().distSqr(home);
		if (far > 48 * 48 && !pet.isLeashed()) {
			if (com.vnap.dialogue.VillagerHome.unseen(level, pet)) pet.teleportTo(rest.getX() + 0.5, rest.getY(), rest.getZ() + 0.5);
			return;
		}
		eatFromBowl(level, family, pet);
		if (pet instanceof Wolf dog) {
			if (bedtime(level, dog, family, night)) return;
			if (!TRAINING.containsKey(dog.getUUID()) && far > 16 * 16) {
				goTo(dog, home);
			} else if (!TRAINING.containsKey(dog.getUUID()) && dog.getRandom().nextFloat() < 0.3F) {
				Family.household(level, head).stream().filter(Villager::isBaby).filter(kid -> kid.closerThan(dog, 16.0)).findFirst()
						.ifPresent(kid -> dog.getNavigation().moveTo(kid, 1.0));
			}
			return;
		}
		if (pet instanceof Cat cat && !night) {
			if (!cat.blockPosition().closerThan(rest, 1.5)) {
				cat.setLying(false);
				goTo(cat, rest);
			} else {
				cat.setLying(true);
			}
		}
		if (pet instanceof Parrot parrot) {
			parrotDay(level, head, parrot, day);
			if (!parrot.blockPosition().closerThan(rest, 1.5)) {
				parrot.setOrderedToSit(false);
				goTo(parrot, rest);
			} else {
				parrot.setOrderedToSit(true);
			}
		}
	}

	/**
	 * A dog with a kennel goes in at night and in the rain and is tied to the post; in the morning it is let go.
	 * True while the dog belongs at the kennel.
	 */
	public static boolean bedtime(ServerLevel level, Wolf dog, FamilyState family, boolean night) {
		BlockPos rest = kennelRest(level, family);
		BlockPos post = kennelPost(level, family);
		if (!night || rest == null || post == null) {
			if (dog.isLeashed() && dog.getLeashHolder() instanceof LeashFenceKnotEntity) {
				dog.dropLeash(true, false);
				dog.setOrderedToSit(false);
			}
			return false;
		}
		// a path counts as done a block short of its end: the rope does not mind
		if (!dog.blockPosition().closerThan(rest, 2.5)) {
			dog.setOrderedToSit(false);
			goTo(dog, rest);
		} else if (!dog.isLeashed() && level.getBlockState(post).is(net.minecraft.tags.BlockTags.FENCES)) {
			dog.setLeashedTo(LeashFenceKnotEntity.getOrCreateKnot(level, post), true);
			dog.setOrderedToSit(true);
		}
		return true;
	}

	private static final String TARGET = "vnap_pet_target";

	/** Where the family tick sends the pet; {@link PetGoal} walks it there. */
	public static @Nullable BlockPos target(TamableAnimal pet) {
		return pet.getPersistentData().contains(TARGET) ? BlockPos.of(Nbt.getLong(pet.getPersistentData(), TARGET)) : null;
	}

	static void clearTarget(TamableAnimal pet) {
		pet.getPersistentData().remove(TARGET);
	}

	/** Sends the pet to {@code pos}: the first leg now, the rest by its {@link PetGoal}. */
	public static void goTo(TamableAnimal pet, BlockPos pos) {
		pet.getPersistentData().putLong(TARGET, pos.asLong());
		walkToward(pet, pos);
	}

	/** A family pet walks by its own goal; added when it joins the world and when it is adopted. */
	public static void attachGoal(TamableAnimal pet) {
		if (!Nbt.hasUUID(pet.getPersistentData(), PET_HEAD)) return;
		pet.goalSelector.removeAllGoals(goal -> goal instanceof net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal
				|| goal instanceof PetGoal || goal instanceof PetGoal.Sit);
		pet.goalSelector.addGoal(2, new PetGoal.Sit(pet));
		pet.goalSelector.addGoal(3, new PetGoal(pet));
		pet.setInSittingPose(pet.isOrderedToSit());
	}

	/** A path only reaches a mob's follow range: a far pet walks home in legs of 14 blocks. */
	public static void walkToward(TamableAnimal pet, BlockPos target) {
		Vec3 from = pet.position();
		Vec3 to = Vec3.atBottomCenterOf(target);
		Vec3 leg = to.subtract(from);
		if (leg.horizontalDistance() > 14.0) to = from.add(leg.scale(14.0 / leg.horizontalDistance()));
		pet.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
	}

	/** Where the pet rests: inside the kennel, on the cat bed, on the perch; the head's bed otherwise. */
	public static BlockPos restSpot(ServerLevel level, FamilyState family, TamableAnimal pet) {
		BlockPos home = family.home == null ? pet.blockPosition() : family.home;
		BlockPos kennelRest = pet instanceof Wolf ? kennelRest(level, family) : null;
		if (kennelRest != null) return kennelRest;
		String decor = pet instanceof Cat ? "cat_bed" : pet instanceof Parrot ? "parrot_perch" : "";
		BlockPos found = decorNear(level, home, decor);
		if (found != null) return pet instanceof Parrot ? found.above() : found;
		return home;
	}

	public static @Nullable BlockPos decorNear(ServerLevel level, BlockPos home, String name) {
		if (name.isEmpty()) return null;
		for (BlockPos pos : BlockPos.betweenClosed(home.offset(-8, -2, -8), home.offset(8, 3, 8))) {
			if (level.getBlockState(pos).getBlock() instanceof DecorBlock decor && decor.name().equals(name)) return pos.immutable();
		}
		return null;
	}

	/** The pet's bowl: by the kennel for a dog, in the house otherwise. */
	public static @Nullable DecorBlockEntity bowl(ServerLevel level, FamilyState family) {
		BlockPos near = family.kennel != null ? family.kennel : family.home;
		if (near == null) return null;
		BlockPos pos = decorNear(level, near, "pet_bowl");
		if (pos == null && family.home != null) pos = decorNear(level, family.home, "pet_bowl");
		return pos != null && level.getBlockEntity(pos) instanceof DecorBlockEntity bowl ? bowl : null;
	}

	/** Morning chore of the wife: one helping from the pantry into the bowl. False when either is missing or empty. */
	public static boolean fillBowl(ServerLevel level, Villager wife, FamilyState family) {
		DecorBlockEntity bowl = bowl(level, family);
		BlockPos home = family.home;
		if (bowl == null || home == null || Pantry.food(bowl) > 0) return false;
		DecorBlockEntity pantry = Pantry.of(level, home);
		if (!Pantry.take(pantry, 1)) {
			Speech.queue(wife, "Woman Pantry Empty", null, 1L);
			return false;
		}
		Pantry.store(bowl, new ItemStack(Items.BREAD));
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, bowl.getBlockPos().getX() + 0.5, bowl.getBlockPos().getY() + 0.5,
				bowl.getBlockPos().getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.0);
		Speech.queue(wife, "Woman Feeds the Pet", null, 1L);
		return true;
	}

	/** A pet with food in its bowl walks over and eats it. */
	public static void eatFromBowl(ServerLevel level, FamilyState family, TamableAnimal pet) {
		DecorBlockEntity bowl = bowl(level, family);
		if (bowl == null || Pantry.food(bowl) == 0 || pet.isLeashed()) return;
		BlockPos at = bowl.getBlockPos();
		if (!pet.blockPosition().closerThan(at, 2.0)) {
			pet.setOrderedToSit(false);
			goTo(pet, at);
			return;
		}
		ItemStack meal = bowl.items.getItem(0).copyWithCount(1);
		Pantry.take(bowl, 1);
		level.sendParticles(com.vnap.Mc.itemParticle(meal), at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5,
				8, 0.2, 0.1, 0.2, 0.05);
		sound(level, pet, SoundEvents.GENERIC_EAT);
		pet.heal(4.0F);
	}

	// ---- the parrot learns words --------------------------------------------------------

	public static final int WORDS = 10;
	public static final int LESSONS = 3;
	private static final String KNOWN = "vnap_words";
	private static final String HEARD = "vnap_heard";

	/** Bit i set: the parrot says word i+1 ("Parrot Says i+1"). */
	public static int knownWords(Parrot parrot) {
		return Nbt.getInt(parrot.getPersistentData(), KNOWN);
	}

	/**
	 * One lesson: {@code teacher} says a word to the parrot; after {@link #LESSONS} lessons of the same word the parrot
	 * says it back. Returns the word (1..10), or 0 when it knows them all.
	 */
	public static int teach(ServerLevel level, Villager teacher, Parrot parrot) {
		int known = knownWords(parrot);
		int[] heard = java.util.Arrays.copyOf(Nbt.getIntArray(parrot.getPersistentData(), HEARD), WORDS);
		int word = -1;
		for (int i = 0; i < WORDS && word < 0; i++) {
			if ((known & (1 << i)) == 0 && heard[i] > 0) word = i;
		}
		if (word < 0) {
			List<Integer> unknown = new ArrayList<>();
			for (int i = 0; i < WORDS; i++) if ((known & (1 << i)) == 0) unknown.add(i);
			if (unknown.isEmpty()) return 0;
			word = unknown.get(parrot.getRandom().nextInt(unknown.size()));
		}
		String who = teacher.isBaby() ? "Kid" : Family.female(teacher) ? "Woman" : "Man";
		teacher.getLookControl().setLookAt(parrot);
		Speech.queue(teacher, who + " Teaches the Parrot " + (word + 1), parrot, 1L);
		heard[word]++;
		parrot.getPersistentData().putIntArray(HEARD, heard);
		if (heard[word] >= LESSONS) {
			parrot.getPersistentData().putInt(KNOWN, known | (1 << word));
			Speech.queue(parrot, "Parrot Says " + (word + 1), teacher, 60L);
			level.sendParticles(ParticleTypes.NOTE, parrot.getX(), parrot.getY() + 0.8, parrot.getZ(), 3, 0.3, 0.2, 0.3, 1.0);
		}
		return word + 1;
	}

	/** Every family tick with a player about: one lesson a day from whoever is near, and now and then a learned word. */
	static void parrotDay(ServerLevel level, Villager head, Parrot parrot, long day) {
		if (level.getNearestPlayer(parrot, 16.0) == null) return;
		String key = parrot.getUUID() + ":lesson";
		Long last = VISITED.get(key);
		if (last == null || last != day) {
			Villager teacher = Family.household(level, head).stream()
					.filter(member -> member.isAlive() && !member.isSleeping() && !com.vnap.dialogue.Infants.infant(member) && member.closerThan(parrot, 8.0))
					.findFirst().orElse(null);
			if (teacher != null) {
				VISITED.put(key, day);
				teach(level, teacher, parrot);
				return;
			}
		}
		int known = knownWords(parrot);
		if (known != 0 && parrot.getRandom().nextFloat() < 0.15F) {
			List<Integer> words = new ArrayList<>();
			for (int i = 0; i < WORDS; i++) if ((known & (1 << i)) != 0) words.add(i + 1);
			Speech.queue(parrot, "Parrot Says " + words.get(parrot.getRandom().nextInt(words.size())), null, 1L);
		}
	}

	// ---- petting and training -----------------------------------------------------------

	/** A pat: hearts, a happy sound, the cat lies down, the dog tilts its head. */
	public static void pet(ServerLevel level, Villager by, TamableAnimal pet) {
		com.vnap.Mc.swing(by, net.minecraft.world.InteractionHand.MAIN_HAND);
		by.getLookControl().setLookAt(pet);
		level.sendParticles(ParticleTypes.HEART, pet.getX(), pet.getY() + 0.9, pet.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		if (pet instanceof Cat cat) {
			cat.setLying(true);
			sound(level, pet, com.vnap.Mc.catPurr());
		} else if (pet instanceof Wolf dog) {
			dog.setIsInterested(true);
			sound(level, pet, com.vnap.Mc.wolfPant());
		} else {
			sound(level, pet, SoundEvents.PARROT_AMBIENT);
		}
		Speech.queue(by, by.isBaby() ? "Kid Pets the Animal" : "Woman Pets the Animal", pet, 1L);
	}

	/** A child's lesson for the dog: sit, fetch a thrown stick, a treat. */
	public static final class Training {
		public final UUID kid;
		public int stage;
		public long until;
		public @Nullable UUID stick;

		Training(UUID kid, long until) {
			this.kid = kid;
			this.until = until;
		}
	}

	public static @Nullable Training training(UUID dog) {
		return TRAINING.get(dog);
	}

	public static boolean startTraining(ServerLevel level, Villager kid, Wolf dog) {
		if (TRAINING.containsKey(dog.getUUID()) || dog.isLeashed()) return false;
		TRAINING.put(dog.getUUID(), new Training(kid.getUUID(), level.getGameTime() + 60));
		dog.getNavigation().stop();
		dog.setOrderedToSit(true);
		kid.getLookControl().setLookAt(dog);
		Speech.queue(kid, "Kid Says Sit", dog, 1L);
		return true;
	}

	/** Every few ticks for the dogs in a lesson. */
	public static void tickTraining(MinecraftServer server) {
		if (TRAINING.isEmpty()) return;
		for (ServerLevel level : server.getAllLevels()) {
			for (Map.Entry<UUID, Training> entry : List.copyOf(TRAINING.entrySet())) {
				if (!(level.getEntity(entry.getKey()) instanceof Wolf dog)) continue;
				Training lesson = entry.getValue();
				if (!(level.getEntity(lesson.kid) instanceof Villager kid) || !kid.isAlive() || !dog.isAlive()
						|| level.getGameTime() > lesson.until + 600) {
					finish(level, dog, lesson);
					continue;
				}
				step(level, dog, kid, lesson);
			}
		}
	}

	private static void step(ServerLevel level, Wolf dog, Villager kid, Training lesson) {
		long now = level.getGameTime();
		if (lesson.stage == 0 && now >= lesson.until) {
			dog.setOrderedToSit(false);
			Vec3 look = Vec3.directionFromRotation(0.0F, kid.getYRot() + (kid.getRandom().nextFloat() - 0.5F) * 90.0F);
			ItemEntity stick = new ItemEntity(level, kid.getX(), kid.getEyeY(), kid.getZ(), new ItemStack(Items.STICK));
			stick.setDeltaMovement(look.x * 0.5, 0.35, look.z * 0.5);
			stick.setNeverPickUp();
			stick.lifespan = 1200;
			level.addFreshEntity(stick);
			com.vnap.Mc.swing(kid, net.minecraft.world.InteractionHand.MAIN_HAND);
			lesson.stick = stick.getUUID();
			lesson.stage = 1;
			Speech.queue(kid, "Kid Says Fetch", dog, 1L);
			return;
		}
		if (lesson.stage == 1) {
			Entity stick = lesson.stick == null ? null : level.getEntity(lesson.stick);
			if (stick == null) {
				lesson.stage = 2;
				return;
			}
			if (dog.distanceToSqr(stick) > 2.0) {
				dog.getNavigation().moveTo(stick, 1.2);
				return;
			}
			stick.discard();
			lesson.stage = 2;
			return;
		}
		if (lesson.stage == 2) {
			if (dog.distanceToSqr(kid) > 4.0) {
				dog.getNavigation().moveTo(kid, 1.2);
				return;
			}
			dog.setIsInterested(true);
			level.sendParticles(ParticleTypes.HEART, dog.getX(), dog.getY() + 0.9, dog.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
			sound(level, dog, com.vnap.Mc.wolfPant());
			Speech.queue(kid, "Kid Says Good Dog", dog, 1L);
			finish(level, dog, lesson);
		}
	}

	private static void finish(ServerLevel level, Wolf dog, Training lesson) {
		TRAINING.remove(dog.getUUID());
		if (lesson.stick != null && level.getEntity(lesson.stick) instanceof ItemEntity stick) stick.discard();
		dog.setOrderedToSit(false);
	}

	/**
	 * Every family tick for a child (or a wife of a house without children), in the day with a player about: tame the
	 * stray, pat or train the pet, watch the fish.
	 */
	public static void kidAmbient(ServerLevel level, Villager kid, float time, long day) {
		if (time < 0.1F || time >= 0.5F) return;
		FamilyState family = Family.state(kid);
		if (family.head == null || !(level.getEntity(family.head) instanceof Villager head)) return;
		FamilyState house = Family.state(head);
		if (house.stray != null && level.getEntity(house.stray) instanceof TamableAnimal stray && stray.isAlive()) {
			visit(level, kid, stray, day, true);
			return;
		}
		if (house.pet != null && level.getEntity(house.pet) instanceof TamableAnimal pet && pet.isAlive() && !pet.isLeashed()) {
			visit(level, kid, pet, day, false);
			return;
		}
		BlockPos aquarium = kid.isBaby() && house.home != null && time >= 0.3F ? decorNear(level, house.home, "aquarium") : null;
		if (aquarium != null && Family.once(kid, "fish", day)) {
			com.vnap.village.life.Agenda.go(kid, "fish", aquarium, com.vnap.village.life.Agenda.VISIT, null, "Kid Watches the Fish");
		}
	}

	/**
	 * A stray the family is taming keeps near the house from the first day (left to itself it wandered off a hundred
	 * blocks and the children never got to feed it): its strolls stay within reach of the house; one already far out
	 * walks back, and one far out with no player to see it is set by the house.
	 */
	static void tieStray(ServerLevel level, FamilyState family, TamableAnimal stray) {
		BlockPos home = family.home;
		if (home == null) return;
		if (!com.vnap.Mc.hasRestriction(stray) || !com.vnap.Mc.restrictCenter(stray).equals(home)) com.vnap.Mc.restrictTo(stray, home, STRAY_RADIUS);
		double far = stray.blockPosition().distSqr(home);
		if (far <= (STRAY_RADIUS + 4) * (STRAY_RADIUS + 4) || stray.isLeashed()) return;
		if (far > 48 * 48 && com.vnap.dialogue.VillagerHome.unseen(level, stray)) {
			BlockPos ground = com.vnap.village.town.Ground.top(level, home.getX() + 4, home.getZ() + 4);
			if (ground != null) stray.teleportTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5);
			return;
		}
		walkToward(stray, home);
	}

	/** Once a day: walk over, then feed the stray or pat (or train) the pet. */
	private static void visit(ServerLevel level, Villager kid, TamableAnimal animal, long day, boolean stray) {
		// the stray keeps near the house (tieStray); one not back yet is not followed out into the fields
		BlockPos home = Family.home(kid);
		if (home != null && !animal.blockPosition().closerThan(home, VISIT_RANGE)) return;
		String key = kid.getUUID() + (stray ? ":stray" : ":pet");
		Long last = VISITED.get(key);
		if (last != null && last == day) return;
		VISITED.put(key, day);
		if (kid.closerThan(animal, 2.5)) {
			tend(level, kid, animal, stray);
			return;
		}
		// walked over on its agenda; the animal may have moved off meanwhile, then the visit is for another day
		com.vnap.village.life.Agenda.job(kid, "pet_visit", animal.blockPosition(), com.vnap.village.life.Agenda.VISIT, ItemStack.EMPTY,
				(l, v) -> {
					if (animal.isAlive() && v.closerThan(animal, 4.0)) tend(l, v, animal, stray);
				});
	}

	private static void tend(ServerLevel level, Villager kid, TamableAnimal animal, boolean stray) {
		if (stray) feedStray(level, kid, animal);
		else if (animal instanceof Wolf dog && kid.getRandom().nextBoolean()) startTraining(level, kid, dog);
		else pet(level, kid, animal);
	}

	// ---- the kennel ---------------------------------------------------------------------

	/** The kennel's open side, or null when the family has none (or it was broken). */
	public static @Nullable Direction kennelFacing(ServerLevel level, FamilyState family) {
		BlockPos at = family.kennel;
		if (at == null) return null;
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
		return state.getBlock() instanceof DecorBlock decor && decor.name().equals(KENNEL) ? state.getValue(DecorBlock.FACING) : null;
	}

	/** Where the dog lies at night: in front of the kennel's opening. */
	public static @Nullable BlockPos kennelRest(ServerLevel level, FamilyState family) {
		Direction facing = kennelFacing(level, family);
		return facing == null || family.kennel == null ? null : family.kennel.relative(facing);
	}

	/** The post beside the kennel the dog's rope is tied to. */
	public static @Nullable BlockPos kennelPost(ServerLevel level, FamilyState family) {
		Direction facing = kennelFacing(level, family);
		return facing == null || family.kennel == null ? null : family.kennel.relative(facing.getClockWise());
	}

	/** A family dog without a (standing) kennel gets one. */
	public static boolean wantsKennel(ServerLevel level, Villager head) {
		FamilyState family = Family.state(head);
		return family.home != null && family.pet != null && level.getEntity(family.pet) instanceof Wolf && kennelFacing(level, family) == null;
	}

	/** The kennel block facing the house, the post with the rope on one side, the bowl on the other. */
	public static void placeKennel(ServerLevel level, Villager head, BlockPos spot) {
		FamilyState family = Family.state(head);
		BlockPos home = family.home == null ? head.blockPosition() : family.home;
		Direction facing = Direction.getNearest(home.getX() - spot.getX(), 0, home.getZ() - spot.getZ());
		if (facing.getAxis().isVertical()) facing = Direction.NORTH;
		DecorBlock kennel = decorBlock(KENNEL);
		DecorBlock bowl = decorBlock("pet_bowl");
		// the spot was free when the head set out with the planks; something may stand there by now
		if (kennel == null || bowl == null || !level.getBlockState(spot).canBeReplaced()) return;
		level.setBlock(spot, kennel.defaultBlockState().setValue(DecorBlock.FACING, facing), net.minecraft.world.level.block.Block.UPDATE_ALL);
		BlockPos post = spot.relative(facing.getClockWise());
		if (level.getBlockState(post).canBeReplaced()) {
			level.setBlock(post, net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
		BlockPos dish = spot.relative(facing.getCounterClockWise());
		if (level.getBlockState(dish).canBeReplaced()) {
			level.setBlock(dish, bowl.defaultBlockState().setValue(DecorBlock.FACING, facing), net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
		family.kennel = spot.immutable();
		Speech.queue(head, "Man Finishes the Kennel", null, 2L);
	}

	/**
	 * The family moved house (the mayor's hall): the pet comes along, the spouse's too when the head has none (one
	 * pet a family: a second one goes to a neighbour's family). The kennel, the cat bed, the perch and the bowls at
	 * the houses left behind are the head's to take up by hand (FamilyBuildBehavior); it knocks a new kennel together
	 * in the new yard, the wife sets the bed or the perch down again (HomeChoreBehavior).
	 */
	public static void moveHouse(ServerLevel level, Villager head, @Nullable Villager spouse, List<BlockPos> left) {
		FamilyState family = Family.state(head);
		List<BlockPos> kennels = new ArrayList<>();
		if (family.kennel != null) kennels.add(family.kennel);
		if (spouse != null) {
			FamilyState hers = Family.state(spouse);
			if (hers.kennel != null) kennels.add(hers.kennel);
			UUID herPet = hers.pet;
			if (herPet != null && level.getEntity(herPet) instanceof TamableAnimal pet) {
				// one pet a family: when the head has its own, hers goes to a neighbour's family
				if (family.pet == null) entrust(level, head, pet, false);
				else rehome(level, pet, spouse.blockPosition(), head);
			}
			hers.pet = null;
			hers.kennel = null;
		}
		for (BlockPos kennel : kennels) leaveBehind(family, kennel);
		family.kennel = null;
		for (BlockPos home : left) {
			java.util.Set<BlockPos> room = FamilyHome.room(level, home);
			for (String name : List.of("cat_bed", "parrot_perch", "pet_bowl")) {
				BlockPos found = decorNear(level, home, name);
				// only what stands in that house: a neighbour's cat bed may be just across the wall
				if (found != null && room.contains(found)) leaveBehind(family, found);
			}
		}
		if (family.pet != null && family.home != null && level.getEntity(family.pet) instanceof TamableAnimal pet && pet.isAlive()) {
			if (pet.isLeashed()) pet.dropLeash(true, false);
			com.vnap.Mc.restrictTo(pet, family.home, HOME_RADIUS);
			goTo(pet, family.home);
		}
	}

	/**
	 * The pet's head died or left the village: the pet stays. It goes to whoever heads the house now ({@code
	 * successor}: the widow, the heir) unless that one has a pet, else to the nearest family of the village without
	 * one; with none it is let go wild, to be tamed again by some family later. Its kennel is taken up.
	 */
	public static void ownerGone(ServerLevel level, Villager owner, @Nullable Villager successor) {
		FamilyState family = Family.state(owner);
		UUID id = family.pet;
		BlockPos kennel = family.kennel;
		family.pet = null;
		family.kennel = null;
		if (id == null || !(level.getEntity(id) instanceof TamableAnimal pet) || !pet.isAlive()) return;
		Villager taker = successor != null && successor.isAlive() && !successor.equals(owner) && Family.isHead(successor)
				&& Family.state(successor).pet == null ? successor : null;
		if (taker != null) entrust(level, taker, pet, true);
		else taker = rehome(level, pet, pet.blockPosition(), owner);
		// whoever takes the pet in fetches its kennel to its own yard; a pet gone wild leaves it standing
		if (taker != null && kennel != null) leaveBehind(Family.state(taker), kennel);
	}

	/** A family pet whose head died while the pet was unloaded: sorted out when it comes back (Family's load event). */
	public static void orphanCheck(ServerLevel level, TamableAnimal pet) {
		if (!Nbt.hasUUID(pet.getPersistentData(), PET_HEAD)) return;
		UUID head = Nbt.getUUID(pet.getPersistentData(), PET_HEAD);
		if (com.vnap.dialogue.FamilyDeaths.get(level.getServer()).contains(head)) rehome(level, pet, pet.blockPosition(), null);
	}

	/** The nearest head of a house without a pet takes it in (returned); with none around the pet goes wild (null). */
	static @Nullable Villager rehome(ServerLevel level, TamableAnimal pet, BlockPos near, @Nullable Villager except) {
		Villager taker = level.getEntitiesOfClass(Villager.class, new AABB(near).inflate(48.0),
						villager -> villager.isAlive() && !villager.isBaby() && !villager.equals(except) && Family.isHead(villager)
								&& Family.state(villager).pet == null && Family.state(villager).stray == null)
				.stream().min(java.util.Comparator.comparingDouble(villager -> villager.distanceToSqr(pet))).orElse(null);
		if (taker != null) {
			entrust(level, taker, pet, true);
			return taker;
		}
		pet.getPersistentData().remove(PET_HEAD);
		com.vnap.Mc.clearRestriction(pet);
		if (pet.isLeashed()) pet.dropLeash(true, false);
		pet.setOrderedToSit(false);
		pet.setInSittingPose(false);
		pet.setTame(false, true);
		return null;
	}

	/** {@code head}'s family keeps the pet from now on: its collar, its house. */
	private static void entrust(ServerLevel level, Villager head, TamableAnimal pet, boolean welcome) {
		FamilyState family = Family.state(head);
		family.pet = pet.getUUID();
		family.petSeenDay = Family.day(level);
		Nbt.putUUID(pet.getPersistentData(), PET_HEAD, head.getUUID());
		DyeColor colour = colour(head);
		if (pet instanceof Wolf wolf) ((WolfAccessor) wolf).vnap$setCollarColor(colour);
		if (pet instanceof Cat cat) ((CatAccessor) cat).vnap$setCollarColor(colour);
		if (pet.isLeashed()) pet.dropLeash(true, false);
		attachGoal(pet);
		if (family.home != null) {
			com.vnap.Mc.restrictTo(pet, family.home, HOME_RADIUS);
			goTo(pet, family.home);
		}
		if (welcome) Speech.queue(head, "Man Welcomes the Pet", pet, 10L);
	}

	private static void leaveBehind(FamilyState family, BlockPos thing) {
		if (!family.takeUp.contains(thing)) family.takeUp.add(thing.immutable());
	}

	/** The next old pet thing for this head to take up, dropping ones that are gone already; null when none. */
	public static @Nullable BlockPos nextTakeUp(ServerLevel level, Villager head) {
		FamilyState family = Family.state(head);
		while (!family.takeUp.isEmpty()) {
			BlockPos pos = family.takeUp.get(0);
			if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof DecorBlock decor
					&& List.of(KENNEL, "cat_bed", "parrot_perch", "pet_bowl").contains(decor.name())) return pos;
			family.takeUp.remove(0);
		}
		return null;
	}

	/** The head worked on an old pet thing: it comes apart (a kennel with its post and bowl). */
	public static void takeUp(ServerLevel level, Villager head, BlockPos pos) {
		Family.state(head).takeUp.remove(pos);
		var state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof DecorBlock decor)) return;
		level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
		if (decor.name().equals(KENNEL)) takeUpKennel(level, pos);
		else level.removeBlock(pos, false);
	}

	private static void takeUpKennel(ServerLevel level, BlockPos kennel) {
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(kennel);
		if (!(state.getBlock() instanceof DecorBlock decor) || !decor.name().equals(KENNEL)) return;
		Direction facing = state.getValue(DecorBlock.FACING);
		BlockPos post = kennel.relative(facing.getClockWise());
		BlockPos dish = kennel.relative(facing.getCounterClockWise());
		if (level.getBlockState(post).is(net.minecraft.world.level.block.Blocks.OAK_FENCE)) level.removeBlock(post, false);
		if (level.getBlockState(dish).getBlock() instanceof DecorBlock bowl && bowl.name().equals("pet_bowl")) level.removeBlock(dish, false);
		level.removeBlock(kennel, false);
	}

	/** Debug and tests: the kennel stands at once. */
	public static void buildKennelNow(ServerLevel level, Villager head) {
		FamilyState family = Family.state(head);
		BlockPos spot = family.home == null ? null : kennelSpot(level, head, family.home);
		if (spot != null) placeKennel(level, head, spot);
	}

	/**
	 * Open yard 4 to 9 blocks from the house: under the sky, on firm ground, nothing built within two blocks (so it
	 * never leans on a wall or stands in the house), no door or bed within three, outside every house and workshop.
	 */
	public static @Nullable BlockPos kennelSpot(ServerLevel level, Villager head, BlockPos home) {
		// the family's own lot is its yard; every building, way and other lot round it is not
		com.vnap.village.town.Land others = com.vnap.village.town.Land.around(level, head.blockPosition()).without(claim -> claim.layer() == com.vnap.village.town.Land.Layer.LOT
				&& claim.box().isInside(new BlockPos(home.getX(), claim.box().minY(), home.getZ())));
		java.util.Set<BlockPos> room = FamilyHome.room(level, home);
		for (BlockPos at : com.vnap.village.town.Rings.around(home, 4, 9, 1, 16)) {
			if (!level.isLoaded(at)) continue;
			BlockPos ground = com.vnap.village.town.Ground.top(level, at.getX(), at.getZ());
			if (ground == null || Math.abs(ground.getY() + 1 - home.getY()) > 2) continue;
			BlockPos spot = ground.above();
			if (yard(level, spot, room, others)) return spot;
		}
		return null;
	}

	private static boolean yard(ServerLevel level, BlockPos spot, java.util.Set<BlockPos> room, com.vnap.village.town.Land others) {
		if (others.blocked(spot, 2)) return false;
		for (BlockPos pos : BlockPos.betweenClosed(spot.offset(-2, 0, -2), spot.offset(2, 1, 2))) {
			if (room.contains(pos) || !level.getBlockState(pos).canBeReplaced() || !level.getFluidState(pos).isEmpty()) return false;
		}
		for (BlockPos pos : BlockPos.betweenClosed(spot.offset(-1, -1, -1), spot.offset(1, -1, 1))) {
			if (!level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) return false;
		}
		for (BlockPos pos : BlockPos.betweenClosed(spot.offset(-3, -1, -3), spot.offset(3, 2, 3))) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if (state.is(net.minecraft.tags.BlockTags.DOORS) || state.is(net.minecraft.tags.BlockTags.BEDS)) return false;
		}
		return level.canSeeSky(spot);
	}

	private static @Nullable DecorBlock decorBlock(String name) {
		for (DecorBlock block : com.vnap.block.LifeBlocks.HOME_DECOR) {
			if (block.name().equals(name)) return block;
		}
		return null;
	}

	// ---- the player and the end ---------------------------------------------------------

	/** A treat or the right food from the player: trust for a stray, a happy pet. True when it was taken. */
	public static boolean give(ServerLevel level, Player player, TamableAnimal animal, ItemStack stack) {
		Kind kind = kindOf(animal);
		if (kind == null) return false;
		boolean treat = stack.is(LifeItems.PET_TREAT);
		if (Nbt.hasUUID(animal.getPersistentData(), STRAY_FOR) && (treat || stack.is(food(kind).getItem()))) {
			int trust = Nbt.getInt(animal.getPersistentData(), TRUST) + 1;
			animal.getPersistentData().putInt(TRUST, trust);
			level.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + 1.0, animal.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
			if (trust >= FEEDINGS && level.getEntity(Nbt.getUUID(animal.getPersistentData(), STRAY_FOR)) instanceof Villager head) {
				adopt(level, head, animal);
			}
			consume(player, stack);
			return true;
		}
		if (Nbt.hasUUID(animal.getPersistentData(), PET_HEAD) && treat) {
			level.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + 1.0, animal.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
			sound(level, animal, kind == Kind.CAT ? com.vnap.Mc.catPurr() : kind == Kind.DOG ? com.vnap.Mc.wolfPant() : SoundEvents.PARROT_AMBIENT);
			if (animal instanceof Wolf dog) dog.setIsInterested(true);
			if (animal instanceof Cat cat) cat.setLying(true);
			if (level.getEntity(Nbt.getUUID(animal.getPersistentData(), PET_HEAD)) instanceof Villager head && player instanceof ServerPlayer server
					&& Family.once(head, "treat:" + player.getUUID(), Family.day(level))) {
				head.getGossips().add(server.getUUID(), net.minecraft.world.entity.ai.gossip.GossipType.MINOR_POSITIVE, 5);
			}
			consume(player, stack);
			return true;
		}
		return false;
	}

	private static void consume(Player player, ItemStack stack) {
		if (!player.getAbilities().instabuild) stack.shrink(1);
	}

	/** A family pet died: the family grieves and may want another after a while. */
	public static void onDeath(ServerLevel level, TamableAnimal animal) {
		if (!Nbt.hasUUID(animal.getPersistentData(), PET_HEAD)) return;
		if (!(level.getEntity(Nbt.getUUID(animal.getPersistentData(), PET_HEAD)) instanceof Villager head)) return;
		FamilyState family = Family.state(head);
		if (!animal.getUUID().equals(family.pet)) return;
		family.pet = null;
		family.petLostDay = Family.day(level);
		for (Villager member : Family.household(level, head)) {
			String title = member.isBaby() ? "Kid Misses the Pet" : Family.female(member) ? "Woman Misses the Pet" : null;
			if (title != null) Speech.queue(member, title, null, 20L + member.getRandom().nextInt(40));
		}
	}

	private static void sound(ServerLevel level, Entity at, SoundEvent sound) {
		level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.NEUTRAL, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
	}
}
