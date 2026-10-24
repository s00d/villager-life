package com.vnap.dialogue;

import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.MoodConfig;
import com.vnap.village.home.Graveyard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Villagers age: a family child comes of age (a cake at home, the mother and father proud, the father's trade if a
 * job site is free), an adult grows old after {@link MoodConfig#elderDays()} (grey hair, a slower walk, stories for
 * the children) and, {@link MoodConfig#oldAgeDays()} later, passes away quietly in their sleep: a grave by the bell,
 * a funeral the next morning. Special characters never age.
 */
public final class Generations {
	private static final ResourceLocation ELDER_SPEED = ResourceLocation.fromNamespaceAndPath("villager_life", "elder");

	private Generations() {
	}

	/** A stable birth day for a villager the family code meets for the first time. */
	static void ensureAge(Villager villager, FamilyState family, long day) {
		if (family.bornDay != Long.MIN_VALUE) return;
		if (villager.isBaby()) {
			family.bornDay = day;
			return;
		}
		// villagers of the world: somewhere in the first half of their adult life, the same guess on every load
		// (across all of it, a new village lost its first elders within 12 days and most of its people in two months)
		long spread = Math.max(1, MoodConfig.elderDays() / 2);
		family.bornDay = day - MoodConfig.childhoodDays() - Math.floorMod(villager.getUUID().getLeastSignificantBits() >>> 9, spread);
		family.grownDay = family.bornDay + MoodConfig.childhoodDays();
	}

	/** Adult years so far (0 for a child). */
	public static long adultDays(FamilyState family, long day) {
		if (family.lived >= 0) return family.lived;
		return family.grownDay == FamilyState.UNSET ? 0 : Math.max(0, Math.min(day - family.grownDay, MoodConfig.elderDays() / 2));
	}

	public static boolean elder(Villager villager) {
		return villager instanceof VillagerLifeData data && data.vnap$elder();
	}

	/** Once a day for an awake villager, after the family's own day. */
	static void daily(ServerLevel level, Villager villager, FamilyState family, long day) {
		if (family.cakePos != null && family.grownDay != FamilyState.UNSET && day > family.grownDay) {
			if (level.getBlockState(family.cakePos).is(Blocks.CAKE)) level.removeBlock(family.cakePos, false);
			com.vnap.village.home.Placements.cancel(level, family.cakePos);
			family.cakePos = null;
		}
		if (villager.isBaby()) return;
		if (family.grownDay == FamilyState.UNSET) {
			family.grownDay = day;
			if (family.mother != null || family.father != null) comeOfAge(level, villager, family, day);
		}
		// a day lived (this runs once a village day, only while the villager is about): the first time, its age so far,
		// as the calendar tells it but no more than half of a grown life (a world's town met late is not all elders)
		if (family.lived < 0) family.lived = (int) Math.max(0, Math.min(day - family.grownDay, MoodConfig.elderDays() / 2));
		// a hero keeps the age it had when it came back armed: an elder hero stays an elder, never passes of old age
		else if (!com.vnap.spire.Heroes.armed(villager) && fullness(level, villager) >= com.vnap.village.town.TownRules.AGE_FROM) family.lived++;
		if (!elder(villager) && adultDays(family, day) >= MoodConfig.elderDays()) growOld(villager);
	}

	/** At night, for a sleeping villager: an elder past their years passes away. */
	public static void night(ServerLevel level, Villager villager, long day) {
		FamilyState family = Family.state(villager);
		if (!MoodConfig.oldAgeDeath() || !elder(villager) || !villager.isSleeping() || !pastYears(family, day) || com.vnap.spire.Heroes.armed(villager)) return;
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
		if (bell != null) {
			if (fullness(level, villager) < com.vnap.village.town.TownRules.PASS_FROM) return;
			GlobalPos town = GlobalPos.of(level.dimension(), bell);
			if (Long.valueOf(day).equals(PASSED.get(town))) return;
			List<Villager> people = com.vnap.village.home.Towns.members(level, bell, other -> true);
			// the eldest of those who may go goes first; the rest wait their turn
			Villager eldest = people.stream().filter(other -> elder(other) && !com.vnap.spire.Heroes.armed(other) && pastYears(Family.state(other), day) && mayGo(other, people))
					.max(java.util.Comparator.<Villager>comparingLong(other -> adultDays(Family.state(other), day)).thenComparing(Villager::getUUID)).orElse(null);
			if (eldest == null || !eldest.getUUID().equals(villager.getUUID())) return;
			PASSED.put(town, day);
		}
		passAway(level, villager);
	}

	private static boolean pastYears(FamilyState family, long day) {
		return adultDays(family, day) >= (long) MoodConfig.elderDays() + MoodConfig.oldAgeDays();
	}

	/**
	 * An elder may go: none of their children is still a child (the young are not left without a parent), and two more
	 * grown, not old, of their sex stay in town (a town does not lose all its husbands or all its wives).
	 */
	static boolean mayGo(Villager elder, List<Villager> people) {
		UUID id = elder.getUUID();
		boolean female = Family.female(elder);
		int same = 0;
		for (Villager other : people) {
			if (other.getUUID().equals(id)) continue;
			FamilyState state = Family.state(other);
			if (other.isBaby() && (id.equals(state.mother) || id.equals(state.father))) return false;
			if (!other.isBaby() && !elder(other) && Family.female(other) == female) same++;
		}
		return same >= 2;
	}

	/** The last village day each town had one pass away of old age. */
	private static final java.util.Map<GlobalPos, Long> PASSED = new java.util.HashMap<>();

	/** How full the villager's town is of its limit (1 when it has none: a wanderer ages as ever). */
	static double fullness(ServerLevel level, Villager villager) {
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
		if (bell == null) return 1.0;
		return com.vnap.village.home.Towns.population(level, bell) / (double) Math.max(1, com.vnap.village.home.Towns.limit(level, bell));
	}

	/** The party: a cake by the bed, proud parents, and the father's trade when the village has a free job site. */
	static void comeOfAge(ServerLevel level, Villager kid, FamilyState family, long day) {
		com.vnap.village.town.Chronicle.note(level, kid.blockPosition(), "grown", VillagerHome.nameOf(kid));
		Villager mother = family.mother != null && level.getEntity(family.mother) instanceof Villager found && found.isAlive() ? found : null;
		Villager father = family.father != null && level.getEntity(family.father) instanceof Villager found && found.isAlive() ? found : null;
		BlockPos anchor = family.home;
		if (anchor != null) {
			Set<BlockPos> room = FamilyHome.room(level, anchor);
			Set<BlockPos> doorway = FamilyHome.doorwayOf(level, anchor);
			for (BlockPos cell : room) {
				if (FamilyHome.placeable(level, room, doorway, cell) && FamilyHome.keepsOpen(level, anchor, room, cell)) {
					Villager payer = father != null ? father : mother;
					com.vnap.village.market.Market.order(level, payer, cell, net.minecraft.world.item.Items.CAKE);
					// the mother brings the cake in (the father, without her), PlaceBehavior
					Villager baker = mother != null ? mother : father;
					com.vnap.village.home.Placements.add(level, cell, Blocks.CAKE.defaultBlockState(), baker == null ? null : baker.getUUID());
					family.cakePos = cell.immutable();
					break;
				}
			}
		}
		Speech.queue(kid, Family.female(kid) ? "Young Woman Comes of Age" : "Young Man Comes of Age",
				mother != null ? mother : father, 2L);
		if (mother != null) Speech.queue(mother, "Woman Proud of Her Child", kid, 6L);
		if (father != null) Speech.queue(father, "Man Proud of His Child", kid, 10L);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, kid.getX(), kid.getY() + 1.5, kid.getZ(), 12, 0.5, 0.5, 0.5, 0.02);
		if (father != null && takeUpTrade(level, kid, father)) {
			Speech.queue(father, "Man Proud of His Apprentice", kid, 14L);
		}
	}

	/** The child takes a free job site of the father's trade near the bell; false when there is none (vanilla picks). */
	public static boolean takeUpTrade(ServerLevel level, Villager kid, Villager father) {
		VillagerProfession trade = com.vnap.Mc.profession(father.getVillagerData());
		if (com.vnap.Mc.is(father.getVillagerData(), VillagerProfession.NONE) || com.vnap.Mc.is(father.getVillagerData(), VillagerProfession.NITWIT)
				|| !com.vnap.Mc.is(kid.getVillagerData(), VillagerProfession.NONE)) return false;
		BlockPos bell = com.vnap.village.home.Towns.bellOf(kid, kid.blockPosition());
		Optional<BlockPos> site = level.getPoiManager().take(trade.acquirableJobSite(), (type, pos) -> true, bell, 48);
		if (site.isEmpty()) return false;
		kid.setVillagerData(com.vnap.Mc.withLevel(com.vnap.Mc.withProfession(kid.getVillagerData(), trade), 1));
		kid.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), site.get()));
		return true;
	}

	/** Debug and tests: a villager is as old as an elder is, today. */
	public static void makeElder(Villager villager, long day) {
		FamilyState family = Family.state(villager);
		family.grownDay = day - MoodConfig.elderDays();
		family.bornDay = family.grownDay - MoodConfig.childhoodDays();
		family.lived = MoodConfig.elderDays();
		growOld(villager);
	}

	static void growOld(Villager villager) {
		if (!(villager instanceof VillagerLifeData data)) return;
		data.vnap$setElder(true);
		AttributeInstance speed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && !speed.hasModifier(ELDER_SPEED)) {
			speed.addPermanentModifier(new AttributeModifier(ELDER_SPEED, -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Quietly in their sleep: no death animation, no drops; the family is widowed and the village buries them. */
	public static void passAway(ServerLevel level, Villager villager) {
		level.sendParticles(ParticleTypes.SOUL, villager.getX(), villager.getY() + 0.6, villager.getZ(), 8, 0.3, 0.2, 0.3, 0.01);
		com.vnap.VillagerLife.LOGGER.info("vnap death: {} by old age at {}", VillagerHome.nameOf(villager), villager.blockPosition().toShortString());
		Family.onDeath(level, villager);
		villager.releasePoi(MemoryModuleType.HOME);
		villager.releasePoi(MemoryModuleType.JOB_SITE);
		villager.releasePoi(MemoryModuleType.MEETING_POINT);
		// out of the bed first: a sleeper taken away leaves its bed marked taken for good, and vanilla then keeps
		// whoever sleeps there next from lying down
		villager.stopSleeping();
		villager.discard();
	}

	/** An elder in the village teaches the children instead of the librarian, with a story. */
	static @Nullable Villager storyteller(ServerLevel level, Villager kid) {
		return level.getEntitiesOfClass(Villager.class, kid.getBoundingBox().inflate(48.0),
						adult -> adult.isAlive() && !adult.isBaby() && !adult.isSleeping() && elder(adult))
				// today's teller, the same for all the children (another tomorrow, when there are several)
				.stream().min(java.util.Comparator.comparingDouble(adult -> Temperament.roll(adult.getUUID(), Family.day(level)))).orElse(null);
	}

	/** A widow or widower visits the late spouse's grave every few days. */
	static void visitGrave(ServerLevel level, Villager villager, FamilyState family, float time, long day) {
		java.util.UUID late = family.lateSpouse;
		if (late == null || family.married() || time < 0.2F || time >= 0.4F || day == family.widowedDay || !Temperament.scene(villager, "grave", day, 0.2F)) return;
		Graveyard.Grave grave = Graveyard.get(level.getServer()).of(late);
		if (grave == null || !grave.pos.closerThan(villager.blockPosition(), 64.0)) return;
		if (!villager.blockPosition().closerThan(grave.pos, 3.0)) {
			com.vnap.village.life.Agenda.go(villager, "grave", grave.pos, com.vnap.village.life.Agenda.VISIT, null, null);
			return;
		}
		if (Family.once(villager, "grave", day)) {
			Speech.queue(villager, Family.female(villager) ? "Woman Visits the Grave" : "Man Visits the Grave",
					null, 1L);
			level.sendParticles(ParticleTypes.HEART, grave.pos.getX() + 0.5, grave.pos.getY() + 1.2, grave.pos.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.0);
		}
	}
}
