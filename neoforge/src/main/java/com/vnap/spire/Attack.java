package com.vnap.spire;

/**
 * What a guard can do, as numbers apart from the guard (DESIGN §5): how long it takes, the tick it lands on, how
 * long before it again, how far it reaches, how hard it hits (a share of the guard's damage), what it costs of the
 * threat budget and the chapter it opens in. A strong attack lands on tick 15..25 and shows itself till then.
 */
public enum Attack {
	//      duration impact cooldown range power cost chapter
	STRIKE(12, 6, 20, 2.6, 1.0, 2, 0),
	SLAM(32, 20, 90, 3.5, 1.6, 5, 0),
	DASH(34, 16, 100, 9.0, 1.3, 4, 0),
	BOLT(20, 10, 40, 14.0, 0.8, 3, 0),
	LEAP(36, 18, 90, 8.0, 1.4, 5, 1),
	MEND(30, 12, 120, 10.0, 0.0, 4, 1),
	HASTE(30, 12, 200, 10.0, 0.0, 3, 1),
	VOLLEY(36, 18, 110, 14.0, 0.7, 6, 2),
	SUMMON(40, 22, 300, 16.0, 0.0, 7, 2),
	/** Only a fighter's, never sooner than every six seconds. */
	BLINK(24, 15, 120, 12.0, 0.0, 6, 3),
	/** Only a keeper's: rings of shock across the floor round it. */
	QUAKE(44, 25, 140, 6.0, 1.5, 0, 0);

	public final int duration;
	public final int impact;
	public final int cooldown;
	public final double range;
	public final double power;
	public final int cost;
	public final int chapter;

	Attack(int duration, int impact, int cooldown, double range, double power, int cost, int chapter) {
		this.duration = duration;
		this.impact = impact;
		this.cooldown = cooldown;
		this.range = range;
		this.power = power;
		this.cost = cost;
		this.chapter = chapter;
	}

	/** Shown before it lands (particles and a sound from its first tick). */
	public boolean strong() {
		return impact >= 15;
	}

	/** Hurts whoever it reaches (mending, haste, a summons and a blink do not). */
	public boolean damaging() {
		return power > 0;
	}
}
