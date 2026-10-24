package com.vnap.dialogue;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import org.jspecify.annotations.Nullable;

/** What villagers say round their town's heroes (spire/Heroes): to them, of them, and when one falls. */
public final class Heroes {
	private Heroes() {
	}

	/** The nearest hero about {@code villager} (not itself), or null. */
	static @Nullable Villager near(ServerLevel level, Villager villager, double reach) {
		Villager found = null;
		for (Villager other : level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(reach),
				other -> !other.getUUID().equals(villager.getUUID()) && com.vnap.spire.Heroes.armed(other))) {
			if (found == null || other.distanceToSqr(villager) < found.distanceToSqr(villager)) found = other;
		}
		return found;
	}

	/** A hero struck a monster down: it says so, a grown-up about thanks it, a child about cheers. */
	public static void felled(ServerLevel level, Villager hero) {
		Speech.queue(hero, "Hero Drives Off a Monster", null, 10L);
		Villager adult = null;
		Villager kid = null;
		for (Villager other : level.getEntitiesOfClass(Villager.class, hero.getBoundingBox().inflate(16.0),
				other -> !other.getUUID().equals(hero.getUUID()) && !com.vnap.spire.Heroes.armed(other))) {
			if (other.isBaby()) {
				if (kid == null || other.distanceToSqr(hero) < kid.distanceToSqr(hero)) kid = other;
			} else if (adult == null || other.distanceToSqr(hero) < adult.distanceToSqr(hero)) {
				adult = other;
			}
		}
		if (adult != null) Speech.queue(adult, "Thanks the Hero", hero, 40L);
		if (kid != null) Speech.queue(kid, "Kid Admires the Hero", hero, 70L);
	}

	/** A hero fell: the nearest grown-up who saw it grieves aloud. */
	static void mourn(ServerLevel level, Villager hero) {
		for (Villager other : level.getEntitiesOfClass(Villager.class, hero.getBoundingBox().inflate(24.0),
				other -> !other.getUUID().equals(hero.getUUID()) && !other.isBaby() && other.isAlive())) {
			Speech.queue(other, "A Hero Has Fallen", null, 30L);
			return;
		}
	}
}
