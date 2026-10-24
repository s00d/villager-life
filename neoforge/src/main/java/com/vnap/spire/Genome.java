package com.vnap.spire;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A guard out of its genome (DESIGN §5): the floor's seed gives every guard a long, and the same long on the same
 * floor is the same guard. First its make — how it moves, its body, its role, its attacks and defence, its size and
 * colours — by rules that keep impossible ones out (a flyer never leaps, a shooter is never warded, only a fighter
 * blinks, a helper hits with one thing at most); then what it costs of the floor's threat (too dear: the extras go
 * first); last the numbers, which grow slowly with the floor (SpireGuards). New attacks open chapter by chapter.
 */
public record Genome(long seed, int floor, Movement movement, Body body, Role role, List<Attack> attacks, Defence defence,
		float size, int palette, int shade, int keeper) {

	public enum Movement {
		WALK, FLY, SWIM
	}

	/** The silhouettes: a model each (geo/spire/<model>.geo.json); from the Gatekeeper on, the keepers' own. */
	public enum Body {
		SENTINEL, HOUND, VINE, MOTH, RAY, WISP, FISH, GATEKEEPER, ARCHIVIST, WARDEN, GARDENER, SERPENT, COLOSSUS, SPORE_QUEEN, HIVE_MASTER,
		STARGAZER, MIND, MASON, SURVEYOR, MASON_ALONE, SURVEYOR_ALONE, ROOT_WYRM, WYRM_SEGMENT, BINDER, CHRONARCH, HEARTWOOD, FROST_MIRROR,
		MYCEL_HYDRA, HYDRA_HEAD, HONEY_GOLEM, GRAVITOR, PLANET, REFLECTION;

		public String model() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public enum Role {
		//      health damage cost
		FIGHTER(20, 4, 6), SHOOTER(14, 3, 6), TANK(30, 4, 8), LEAPER(18, 4, 7), SWARM(6, 2, 2), HELPER(12, 2, 4),
		SUMMONER(16, 3, 8), KEEPER(150, 6, 0);

		public final double health;
		public final double damage;
		public final int cost;

		Role(double health, double damage, int cost) {
			this.health = health;
			this.damage = damage;
			this.cost = cost;
		}
	}

	public enum Defence {
		/** WARD: a moment untouchable after each hit; REGEN: slowly whole again. */
		NONE(0), ARMOR(3), SHIELD(4), WARD(4), REGEN(4);

		public final int cost;

		Defence(int cost) {
			this.cost = cost;
		}
	}

	/** The themes in chapter order: a guard's colours are its floor's theme's. */
	public static final List<String> THEMES = List.of("roots", "archive", "workshop", "gardens", "galleries", "ice", "spores", "hive",
			"observatory", "summit");
	/** Three body colours and an eye colour per theme. */
	private static final int[][] PALETTES = {
			{0x7FA36B, 0x9C9A8E, 0x9B7FC8, 0xC9A8FF},
			{0xD9C39A, 0x8A6A48, 0x4E6A9C, 0xFFD27A},
			{0xC9773F, 0x5FA89A, 0xD3AC55, 0xFFB347},
			{0xE7A3C3, 0x7DBE6A, 0xF0E6C8, 0xFFF2A8},
			{0x5FB8A8, 0x3F6E8A, 0xD8E8E4, 0x9FF6FF},
			{0xBFE3F2, 0xEEF4F8, 0x6FA8D6, 0xB8F0FF},
			{0xC2504A, 0x8C7A8E, 0x6F9A4A, 0xB6FF8A},
			{0xE5A93A, 0xF2D27A, 0xD9D2BC, 0xFFE27A},
			{0x6A6A80, 0x8E66C8, 0xC9A64A, 0xE3C8FF},
			{0xEDE9E2, 0xE0BE52, 0x8EC2E8, 0xFFFFFF}};
	/** The keepers of the chapters (DESIGN §1), by chapter; after the hundredth they come round again. */
	public static final List<String> KEEPERS = List.of("gatekeeper", "archivist", "clockwork_warden", "gardener", "deep_serpent", "ice_colossus",
			"spore_queen", "hive_master", "stargazer", "mind");
	/**
	 * The pair of the Schism (DESIGN §1, chapter V), on floor 45 of every hundred: the Mason and the Surveyor, and
	 * what each becomes when the other falls and it takes up the other's tools. Keeper numbers 10..13 (after the ten).
	 */
	public static final List<String> DUO = List.of("mason", "surveyor", "mason_alone", "surveyor_alone");
	public static final int MASON = 10;
	public static final int SURVEYOR = 11;
	public static final int MASON_ALONE = 12;
	public static final int SURVEYOR_ALONE = 13;
	private static final Body[] DUO_BODIES = {Body.MASON, Body.SURVEYOR, Body.MASON_ALONE, Body.SURVEYOR_ALONE};
	/** Each of the pair's share of a keeper's health and damage: alone, it is more than either was. */
	private static final double[] DUO_HEALTH = {0.6, 0.55, 0.85, 0.8};
	private static final double[] DUO_DAMAGE = {0.65, 0.6, 1.0, 0.95};
	private static final List<List<Attack>> DUO_ATTACKS = List.of(
			List.of(Attack.STRIKE, Attack.SLAM, Attack.LEAP),
			List.of(Attack.BOLT, Attack.VOLLEY),
			List.of(Attack.STRIKE, Attack.SLAM, Attack.LEAP, Attack.QUAKE, Attack.VOLLEY),
			List.of(Attack.STRIKE, Attack.BOLT, Attack.SLAM, Attack.LEAP, Attack.QUAKE));
	private static final float[] DUO_SIZE = {2.0F, 1.9F, 2.4F, 2.3F};
	private static final int[] DUO_SHADES = {1, 2, 1, 2};

	/**
	 * The trials of the half-chapters, on floors 5, 15 … 95 (docs/tower/BOSSES.md), by chapter; the fifth chapter's
	 * is the Schism's pair. Keeper numbers TRIAL + chapter.
	 */
	public static final List<String> TRIALS = List.of("root_wyrm", "binder", "chronarch", "heartwood", "schism", "frost_mirror", "mycel_hydra",
			"honey_golem", "gravitor", "reflection");
	public static final int TRIAL = 14;
	private static final Body[] TRIAL_BODIES = {Body.ROOT_WYRM, Body.BINDER, Body.CHRONARCH, Body.HEARTWOOD, Body.MASON, Body.FROST_MIRROR,
			Body.MYCEL_HYDRA, Body.HONEY_GOLEM, Body.GRAVITOR, Body.REFLECTION};
	private static final List<List<Attack>> TRIAL_ATTACKS = List.of(
			List.of(Attack.STRIKE, Attack.DASH),
			List.of(Attack.BOLT, Attack.VOLLEY),
			List.of(Attack.STRIKE, Attack.SLAM, Attack.DASH),
			List.of(Attack.STRIKE, Attack.SLAM),
			List.of(Attack.STRIKE),
			List.of(Attack.BOLT, Attack.VOLLEY),
			List.of(Attack.SLAM),
			List.of(Attack.STRIKE, Attack.SLAM, Attack.QUAKE),
			List.of(Attack.BOLT, Attack.VOLLEY),
			List.of(Attack.STRIKE, Attack.DASH));
	private static final float[] TRIAL_SIZE = {1.8F, 1.7F, 1.9F, 1.9F, 2.0F, 1.8F, 1.6F, 2.0F, 1.7F, 1.25F};
	private static final int[] TRIAL_SHADES = {1, 1, 0, 1, 0, 2, 0, 0, 1, 2};

	/** Whether a floor has a trial (every fifth but the Schism's). */
	public static boolean trial(int floor) {
		return Math.floorMod(floor, 10) == 5 && !schism(floor);
	}

	/** The trial of a floor's half-chapter. */
	public static Genome trial(long seed, int floor, String theme) {
		int chapter = Math.floorMod(chapter(floor), TRIALS.size());
		int palette = Math.max(0, THEMES.indexOf(theme));
		return new Genome(seed, floor, Movement.WALK, TRIAL_BODIES[chapter], Role.KEEPER, TRIAL_ATTACKS.get(chapter), Defence.ARMOR, TRIAL_SIZE[chapter], palette,
				TRIAL_SHADES[chapter], TRIAL + chapter);
	}

	/** A keeper of any kind back from its number (saved guards): the pair, a trial, or a chapter's keeper. */
	public static Genome byKeeper(long seed, int floor, String theme, int keeper) {
		if (keeper >= TRIAL) return trial(seed, floor, theme);
		if (keeper >= MASON) return duo(seed, floor, theme, keeper);
		return decode(seed, floor, theme, false, true);
	}

	/** One of a boss's parts (a worm's segment, a hydra's head, a planet): its body, small, with the boss's colours. */
	public static Genome part(Genome boss, long seed, Body body, Role role, List<Attack> attacks, float size) {
		return new Genome(seed, boss.floor, Movement.WALK, body, role, attacks, Defence.NONE, size, boss.palette, boss.shade, -1);
	}

	/** Whether a floor is the Schism's: the pair in its last room instead of a guard of the chapter. */
	public static boolean schism(int floor) {
		return Math.floorMod(floor, 100) == 45;
	}

	/** One of the pair (MASON..SURVEYOR_ALONE) on its floor. */
	public static Genome duo(long seed, int floor, String theme, int which) {
		int i = Math.max(0, Math.min(DUO.size() - 1, which - MASON));
		int palette = Math.max(0, THEMES.indexOf(theme));
		return new Genome(seed, floor, Movement.WALK, DUO_BODIES[i], Role.KEEPER, DUO_ATTACKS.get(i), Defence.ARMOR, DUO_SIZE[i], palette, DUO_SHADES[i],
				MASON + i);
	}

	/** The name key of a keeper number (the ten, then the pair). */
	public static String keeperName(int keeper) {
		if (keeper >= TRIAL) return TRIALS.get(Math.min(TRIALS.size() - 1, keeper - TRIAL));
		return keeper >= MASON ? DUO.get(Math.min(DUO.size() - 1, keeper - MASON)) : KEEPERS.get(Math.floorMod(keeper, KEEPERS.size()));
	}

	private static final Body[] KEEPER_BODIES = {Body.GATEKEEPER, Body.ARCHIVIST, Body.WARDEN, Body.GARDENER, Body.SERPENT, Body.COLOSSUS,
			Body.SPORE_QUEEN, Body.HIVE_MASTER, Body.STARGAZER, Body.MIND};
	/** Which of its theme's three body colours each keeper wears (the bee gold, the serpent sea-green, the queen red). */
	private static final int[] KEEPER_SHADES = {1, 1, 0, 1, 0, 2, 0, 0, 1, 2};
	/** Each keeper's own attacks: the first two are all it uses before its first change; its arts are its own (KeeperArts). */
	private static final List<List<Attack>> KEEPER_ATTACKS = List.of(
			List.of(Attack.STRIKE, Attack.SLAM, Attack.DASH, Attack.QUAKE),
			List.of(Attack.BOLT, Attack.VOLLEY, Attack.SUMMON),
			List.of(Attack.STRIKE, Attack.DASH, Attack.SLAM, Attack.QUAKE),
			List.of(Attack.STRIKE, Attack.VOLLEY, Attack.SUMMON),
			List.of(Attack.STRIKE, Attack.DASH, Attack.BOLT),
			List.of(Attack.STRIKE, Attack.SLAM, Attack.QUAKE),
			List.of(Attack.BOLT, Attack.SUMMON, Attack.MEND),
			List.of(Attack.STRIKE, Attack.DASH, Attack.VOLLEY, Attack.SUMMON),
			List.of(Attack.BOLT, Attack.VOLLEY, Attack.SUMMON),
			List.of(Attack.BOLT, Attack.SLAM, Attack.VOLLEY, Attack.SUMMON, Attack.QUAKE));

	/** A boss of the Nameless (outer/Rush): its keeper number, past the trials'; its arts are drawn from its seed (KeeperArts.Patchwork). */
	public static final int NAMELESS = 24;

	/**
	 * A Nameless boss: any keeper's or trial's body, in a chapter's colours, two to four of all the blows, a size of
	 * its own — one of hundreds made of the same few pieces (Nimbatus's bosses).
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "seeded: the same seed draws the same boss")
	public static Genome nameless(long seed, int floor) {
		Random random = new Random(FloorGenerator.mix(seed, 31));
		// the ten keepers' bodies stand alone (a trial's wyrm, hydra or mirror needs its parts and its room)
		Body body = KEEPER_BODIES[random.nextInt(KEEPER_BODIES.length)];
		List<Attack> pool = new ArrayList<>(List.of(Attack.STRIKE, Attack.SLAM, Attack.DASH, Attack.BOLT, Attack.VOLLEY, Attack.QUAKE, Attack.LEAP, Attack.SUMMON));
		java.util.Collections.shuffle(pool, random);
		List<Attack> attacks = List.copyOf(pool.subList(0, 2 + random.nextInt(3)));
		Defence defence = random.nextInt(3) == 0 ? Defence.SHIELD : Defence.ARMOR;
		float size = 1.4F + random.nextFloat();
		return new Genome(seed, floor, Movement.WALK, body, Role.KEEPER, attacks, defence, size, random.nextInt(THEMES.size()), random.nextInt(3), NAMELESS);
	}

	/** Which hundred of floors this is: 0 for the first hundred, 1 for 101..200 (the Spire's rounds). */
	public static int round(int floor) {
		return Math.max(0, (floor - 1) / 100);
	}

	/** A keeper of chapter {@code which} on this floor (a companion of the floor's own, past the hundredth). */
	public static Genome companion(long seed, int floor, String theme, int which) {
		int palette = Math.max(0, THEMES.indexOf(theme));
		float size = 2.0F + 0.04F * Math.min(chapter(floor), 10);
		Defence defence = which == 0 ? Defence.SHIELD : Defence.ARMOR;
		return new Genome(seed, floor, Movement.WALK, KEEPER_BODIES[which], Role.KEEPER, KEEPER_ATTACKS.get(which), defence, size * 0.9F, palette,
				KEEPER_SHADES[which], which);
	}

	public static int chapter(int floor) {
		return Math.max(0, (floor - 1) / 10);
	}

	/** What one guard of this floor may cost at most. */
	public static double budget(int floor) {
		return (40 + 6 * Math.pow(Math.max(1, floor), 0.85)) / 2.0;
	}

	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "seeded: the same seed draws the same guard")
	public static Genome decode(long seed, int floor, String theme, boolean water, boolean keeper) {
		Random random = new Random(FloorGenerator.mix(seed, 11));
		int chapter = chapter(floor);
		int palette = Math.max(0, THEMES.indexOf(theme));
		int shade = random.nextInt(3);
		if (keeper) return keeper(seed, floor, palette);
		Bestiary kind = Bestiary.of(theme);
		Movement movement = water ? Movement.SWIM : random.nextInt(12) < kind.flyers ? Movement.FLY : Movement.WALK;
		Role role = role(movement, chapter, random, kind);
		Body body = body(movement, role, random);
		List<Attack> attacks = new ArrayList<>();
		switch (role) {
			case FIGHTER -> {
				attacks.add(Attack.STRIKE);
				if (random.nextBoolean()) attacks.add(Attack.DASH);
				if (chapter >= Attack.BLINK.chapter && movement != Movement.SWIM && random.nextInt(10) < 3) attacks.add(Attack.BLINK);
			}
			case SHOOTER -> {
				attacks.add(Attack.BOLT);
				if (chapter >= Attack.VOLLEY.chapter && movement != Movement.SWIM && random.nextBoolean()) attacks.add(Attack.VOLLEY);
			}
			case TANK -> {
				attacks.add(Attack.STRIKE);
				attacks.add(Attack.SLAM);
			}
			case LEAPER -> {
				attacks.add(Attack.LEAP);
				attacks.add(Attack.STRIKE);
			}
			case HELPER -> {
				attacks.add(random.nextBoolean() ? Attack.MEND : Attack.HASTE);
				if (random.nextBoolean()) attacks.add(movement == Movement.FLY ? Attack.BOLT : Attack.STRIKE);
			}
			case SUMMONER -> {
				attacks.add(Attack.SUMMON);
				attacks.add(Attack.BOLT);
			}
			default -> attacks.add(Attack.STRIKE);
		}
		Defence defence = defence(role, chapter, random);
		float size = switch (role) {
			case SWARM -> 0.55F + random.nextFloat() * 0.15F;
			case TANK -> 1.15F + random.nextFloat() * 0.2F;
			default -> 0.85F + random.nextFloat() * 0.3F;
		};
		if (movement != Movement.WALK) size = Math.min(1.0F, size);
		Genome genome = new Genome(seed, floor, movement, body, role, List.copyOf(attacks), defence, size, palette, shade, -1);
		// too dear for its floor: the extras go, the size, the defence; what a role is (its first attack) stays
		while (genome.threat() > budget(floor)) {
			if (attacks.size() > 1 && !(role == Role.TANK && attacks.size() == 2)) attacks.remove(attacks.size() - 1);
			else if (size > 1.0F) size = 1.0F;
			else if (defence != Defence.NONE && defence != Defence.SHIELD) defence = Defence.NONE;
			else break;
			genome = new Genome(seed, floor, movement, body, role, List.copyOf(attacks), defence, size, palette, shade, -1);
		}
		return genome;
	}

	/** A small one a summoner calls up: a swarm walker of its floor. */
	public static Genome summoned(long seed, int floor, String theme) {
		int palette = Math.max(0, THEMES.indexOf(theme));
		return new Genome(seed, floor, Movement.WALK, Body.HOUND, Role.SWARM, List.of(Attack.STRIKE), Defence.NONE, 0.6F, palette,
				(int) Math.floorMod(seed, 3L), -1);
	}

	private static Genome keeper(long seed, int floor, int palette) {
		int chapter = chapter(floor);
		int which = Math.floorMod(chapter, KEEPERS.size());
		float size = 2.0F + 0.04F * Math.min(chapter, 10);
		Defence defence = which == 0 ? Defence.SHIELD : Defence.ARMOR;
		return new Genome(seed, floor, Movement.WALK, KEEPER_BODIES[which], Role.KEEPER, KEEPER_ATTACKS.get(which), defence, size, palette,
				KEEPER_SHADES[which], which);
	}

	/**
	 * Who lives on a theme's floors (the bestiary): how many in twelve fly, and the roles drawn, a role named twice
	 * twice as often. The roots are stone sentinels and hounds, the archive paper moths and shooters, the workshop
	 * tanks and leapers, the gardens vines and menders, the hive a swarm on the wing... A role the chapter has not
	 * opened yet, or the way it moves cannot have, is drawn again from what is left.
	 */
	enum Bestiary {
		ROOTS(2, Role.FIGHTER, Role.FIGHTER, Role.TANK, Role.TANK, Role.SWARM, Role.SHOOTER),
		ARCHIVE(6, Role.SHOOTER, Role.SHOOTER, Role.SHOOTER, Role.HELPER, Role.SUMMONER, Role.FIGHTER),
		WORKSHOP(3, Role.TANK, Role.TANK, Role.LEAPER, Role.LEAPER, Role.FIGHTER, Role.SHOOTER),
		GARDENS(3, Role.HELPER, Role.HELPER, Role.SHOOTER, Role.SHOOTER, Role.SWARM, Role.LEAPER),
		GALLERIES(4, Role.SHOOTER, Role.SHOOTER, Role.FIGHTER, Role.FIGHTER, Role.LEAPER),
		ICE(2, Role.TANK, Role.TANK, Role.LEAPER, Role.LEAPER, Role.FIGHTER),
		SPORES(4, Role.SUMMONER, Role.SUMMONER, Role.HELPER, Role.HELPER, Role.SWARM, Role.SWARM, Role.SHOOTER),
		HIVE(8, Role.SWARM, Role.SWARM, Role.SWARM, Role.FIGHTER, Role.FIGHTER, Role.SHOOTER),
		OBSERVATORY(6, Role.SHOOTER, Role.SHOOTER, Role.FIGHTER, Role.FIGHTER, Role.HELPER),
		SUMMIT(4, Role.FIGHTER, Role.SHOOTER, Role.TANK, Role.LEAPER, Role.SWARM, Role.HELPER, Role.SUMMONER);

		final int flyers;
		final com.google.common.collect.ImmutableList<Role> roles;

		Bestiary(int flyers, Role... roles) {
			this.flyers = flyers;
			this.roles = com.google.common.collect.ImmutableList.copyOf(roles);
		}

		static Bestiary of(String theme) {
			int i = THEMES.indexOf(theme);
			return values()[i < 0 ? 0 : i];
		}
	}

	private static Role role(Movement movement, int chapter, Random random, Bestiary kind) {
		List<Role> drawn = new ArrayList<>();
		for (Role role : kind.roles) {
			boolean open = switch (role) {
				case LEAPER, HELPER -> chapter >= 1;
				case SUMMONER -> chapter >= 2;
				default -> true;
			};
			boolean moves = switch (movement) {
				case SWIM -> role == Role.FIGHTER || role == Role.SHOOTER;
				case FLY -> role == Role.FIGHTER || role == Role.SHOOTER || role == Role.SWARM || (role == Role.HELPER && chapter >= 1);
				default -> true;
			};
			if (open && moves) drawn.add(role);
		}
		return drawn.isEmpty() ? role(movement, chapter, random) : drawn.get(random.nextInt(drawn.size()));
	}

	private static Role role(Movement movement, int chapter, Random random) {
		List<Role> roles = new ArrayList<>();
		switch (movement) {
			case SWIM -> roles.addAll(List.of(Role.FIGHTER, Role.SHOOTER));
			case FLY -> {
				roles.addAll(List.of(Role.FIGHTER, Role.SHOOTER, Role.SWARM));
				if (chapter >= 1) roles.add(Role.HELPER);
			}
			default -> {
				roles.addAll(List.of(Role.FIGHTER, Role.FIGHTER, Role.SHOOTER, Role.TANK, Role.SWARM));
				if (chapter >= 1) roles.addAll(List.of(Role.LEAPER, Role.HELPER));
				if (chapter >= 2) roles.add(Role.SUMMONER);
			}
		}
		return roles.get(random.nextInt(roles.size()));
	}

	private static Body body(Movement movement, Role role, Random random) {
		boolean coin = random.nextBoolean();
		return switch (movement) {
			case SWIM -> coin ? Body.FISH : Body.RAY;
			case FLY -> switch (role) {
				case SWARM -> Body.WISP;
				case HELPER -> coin ? Body.WISP : Body.MOTH;
				case SHOOTER -> coin ? Body.RAY : Body.MOTH;
				default -> coin ? Body.MOTH : Body.RAY;
			};
			default -> switch (role) {
				case TANK -> Body.SENTINEL;
				case LEAPER, SWARM -> Body.HOUND;
				case HELPER -> Body.VINE;
				case SHOOTER, SUMMONER -> coin ? Body.VINE : Body.SENTINEL;
				default -> coin ? Body.SENTINEL : Body.HOUND;
			};
		};
	}

	private static Defence defence(Role role, int chapter, Random random) {
		if (role == Role.TANK) return Defence.SHIELD;
		int roll = random.nextInt(10);
		boolean front = role == Role.FIGHTER || role == Role.LEAPER;
		if (front && chapter >= 1 && roll < 2) return Defence.WARD;
		if (chapter >= 4 && roll == 2) return Defence.REGEN;
		return roll < 6 ? Defence.NONE : Defence.ARMOR;
	}

	public boolean isKeeper() {
		return keeper >= 0;
	}

	public double threat() {
		double cost = role.cost + defence.cost + Math.max(0, size - 1.0F) * 6;
		for (Attack attack : attacks) cost += attack.cost;
		return cost;
	}

	public double health() {
		if (isKeeper()) {
			double share = keeper >= TRIAL ? 0.7 : keeper >= MASON ? DUO_HEALTH[Math.min(DUO.size() - 1, keeper - MASON)] : 1.0;
			return share * (role.health + 12 * Math.min(chapter(floor), 10)) * SpireGuards.keeperHealth(floor);
		}
		return role.health * Math.max(0.6, size) * SpireGuards.health(floor);
	}

	public double damage() {
		if (keeper >= TRIAL) return 0.85 * role.damage * SpireGuards.keeperDamage(floor);
		if (keeper >= MASON) return DUO_DAMAGE[Math.min(DUO.size() - 1, keeper - MASON)] * role.damage * SpireGuards.keeperDamage(floor);
		return role.damage * (isKeeper() ? SpireGuards.keeperDamage(floor) : SpireGuards.damage(floor));
	}

	public double speed() {
		if (movement == Movement.SWIM) return 0.2;
		if (isKeeper()) return 0.24;
		if (body == Body.HOUND) return 0.32;
		return role == Role.TANK ? 0.2 : 0.26;
	}

	/** Why this make may not be (a rule of DESIGN §5 broken), null when it may. */
	public @Nullable String problem() {
		int damaging = 0;
		for (Attack attack : attacks) {
			if (attack.damaging()) damaging++;
			if (!isKeeper() && attack.chapter > chapter(floor)) return attack + " before chapter " + attack.chapter;
		}
		if (attacks.isEmpty()) return "no attack";
		if (movement != Movement.WALK && attacks.contains(Attack.LEAP)) return "a " + movement + " guard leaps";
		if (movement == Movement.FLY && (role == Role.TANK || role == Role.LEAPER)) return "a flying " + role;
		if (role == Role.SHOOTER && defence == Defence.WARD) return "a warded shooter";
		if (attacks.contains(Attack.BLINK) && role != Role.FIGHTER) return "a " + role + " blinks";
		if (role == Role.HELPER && damaging > 1) return "a helper with " + damaging + " attacks";
		if (!isKeeper() && attacks.contains(Attack.QUAKE)) return "a quake not a keeper's";
		if (isKeeper() != (role == Role.KEEPER)) return "a keeper's role";
		if (!isKeeper() && threat() > budget(floor)) return "threat " + threat() + " over " + budget(floor);
		return null;
	}

	/** The flag an elite guard adds to its look (SpireGuard.elite). */
	public static final int ELITE = 1 << 18;
	/** A boss's echo or decoy: drawn see-through (an echo) — the client reads it off the look. */
	public static final int ECHO = 1 << 19;
	/** Two bits of a boss's form (the Heartwood's blades, shield, whips). */
	public static final int FORM_SHIFT = 20;

	/** Packed for the client: body, palette, shade, shield, keeper. */
	@SuppressWarnings("EnumOrdinal") // one synced int; Look reads the body back by the same index
	public int look() {
		return body.ordinal() | (palette << 6) | (shade << 10) | (defence == Defence.SHIELD ? 1 << 12 : 0) | ((keeper + 1) << 13);
	}

	public record Look(Body body, int colour, int glow, boolean shield, int keeper, boolean elite, boolean echo, int form) {
		public static Look of(int look) {
			Body[] bodies = Body.values();
			Body body = bodies[Math.min(bodies.length - 1, look & 63)];
			int[] palette = PALETTES[Math.min(PALETTES.length - 1, (look >> 6) & 15)];
			int shade = Math.min(2, (look >> 10) & 3);
			return new Look(body, palette[shade], palette[3], ((look >> 12) & 1) == 1, ((look >> 13) & 31) - 1, (look & ELITE) != 0, (look & ECHO) != 0,
					(look >> FORM_SHIFT) & 3);
		}
	}
}
