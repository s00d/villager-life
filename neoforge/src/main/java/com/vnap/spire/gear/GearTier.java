package com.vnap.spire.gear;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The ten tiers of spire gear, one a chapter of the spire (docs/tower/DESIGN.md §7): a tier is opened by its chapter's
 * seal, sets the base of every piece and has one property of its own (GearEffects).
 */
public enum GearTier {
	ROOT(0), ARCHIVE(1), CLOCKWORK(2), GARDEN(3), DEEP(4), ICE(5), SPORE(6), HIVE(7), STAR(8), SUMMIT(9);

	private static final int COUNT = 10;
	private final int step;

	GearTier(int step) {
		this.step = step;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** 0 for Root … 9 for Summit: how many tiers above the first. */
	public int step() {
		return step;
	}

	/** 1 for Root … 10 for Summit. */
	public int number() {
		return step + 1;
	}

	public @Nullable GearTier next() {
		return step + 1 < COUNT ? ofChapter(step + 1) : null;
	}

	/** The tier of a chapter (0 for floors 1–10); past the tenth the chapters come round again. */
	public static GearTier ofChapter(int chapter) {
		int wanted = Math.floorMod(chapter, COUNT);
		for (GearTier tier : values()) {
			if (tier.step == wanted) return tier;
		}
		return ROOT;
	}

	/** The chapter a floor belongs to: floors 1–10 are Root's, 11–20 Archive's … */
	public static GearTier ofFloor(int floor) {
		return ofChapter((floor - 1) / 10);
	}
}
