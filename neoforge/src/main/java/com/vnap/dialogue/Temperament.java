package com.vnap.dialogue;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

import java.util.UUID;

/**
 * A villager's own nature, fixed for life by its UUID, so a village is not a clockwork of the same scenes at the same
 * moment every day: an early riser or a late one (its day runs up to RANGE of a day ahead or behind), the sociable who
 * go to the tavern, the quiet who keep away, the pious who look into the chapel, the homebodies who seldom go out.
 * And a day's luck: the routine's work is done every day, its scene (a greeting, a call, a word) only on some.
 */
public final class Temperament {
	/** How far a villager's own clock runs from the sun's. */
	static final float RANGE = 0.03F;
	/** Ticks over which the villagers' day begins one by one (their daily affairs), not all on one tick. */
	static final int DAWN_SPREAD = 2400;

	public enum Trait { SOCIABLE, QUIET, PIOUS, HOMEBODY }

	private Temperament() {
	}

	private static long seed(UUID id, long salt) {
		long h = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits() ^ salt * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		return h ^ h >>> 29;
	}

	/** A number in [0, 1) for this villager and {@code salt}, the same every time. */
	static double roll(UUID id, long salt) {
		return (seed(id, salt) >>> 11) * 0x1.0p-53;
	}

	/** The villager's main trait (a quarter of the village each), or null for the plain ones (a third). */
	public static boolean is(Villager villager, Trait trait) {
		double r = roll(villager.getUUID(), 17L);
		int i = (int) (r * 6);
		return i < Trait.values().length && Trait.values()[i] == trait;
	}

	/** The time of day as this villager lives it (0..1): its own clock, a little ahead or behind the sun. */
	public static float clock(ServerLevel level, Villager villager) {
		float shift = (float) ((roll(villager.getUUID(), 3L) * 2.0 - 1.0) * RANGE);
		float clock = Family.dayFraction(level) + shift;
		return clock - (float) Math.floor(clock);
	}

	/** Whether the scene {@code what} plays today for this villager (its chance a day; the same all day long). */
	public static boolean scene(Villager villager, String what, long day, float chance) {
		return roll(villager.getUUID(), what.hashCode() * 31L + day) < chance;
	}

	/** Ticks into the day before the villager's daily affairs begin (spread over the first part of the morning). */
	public static long dawn(Villager villager) {
		return (long) (roll(villager.getUUID(), 5L) * DAWN_SPREAD);
	}

	/** How much more (or less) this villager likes going to a place of {@code kind}: chapel, tavern, or anywhere. */
	public static float liking(Villager villager, String kind) {
		if (is(villager, Trait.HOMEBODY)) return 0.3F;
		return switch (kind) {
			case "chapel" -> is(villager, Trait.PIOUS) ? 3.0F : 1.0F;
			case "tavern" -> is(villager, Trait.SOCIABLE) ? 3.0F : is(villager, Trait.QUIET) ? 0.3F : 1.0F;
			default -> 1.0F;
		};
	}
}
