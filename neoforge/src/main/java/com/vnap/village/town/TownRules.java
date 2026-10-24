package com.vnap.village.town;

import com.vnap.village.market.Coins;

/**
 * Every number a town lives by, in one place: its size and reach, the days things wait, wages and prices, the
 * plan's streets and lots, the roads and the wall. The mechanics read them from here; nothing else defines one.
 */
public final class TownRules {
	private TownRules() {
	}

	// ---- The town: its ground, who belongs to it -------------------------------------------------------------

	/**
	 * Bells this close are one village with one mayor and one hall: a big village has more than one bell (players
	 * hang them), while another village or a hamlet (150-300 blocks out) is always further.
	 */
	public static final double SPAN = 64.0;
	/** The reach of a young village: houses, a free bed, an empty house are looked for this far from the bell. */
	public static final double BASE_REACH = 48.0;
	/** The furthest a grown town reaches. */
	public static final double MAX_REACH = 80.0;
	/** Round the bell (or a villager's own house), what a villager walks to on foot in one go and what is looked for. */
	public static final int NEAR_BELL = 48;
	/** How far a villager goes for a town job of its own (a crossbow to load, the oven, the clock, a road). */
	public static final double WORK_REACH = 64.0;
	/** Two single villagers this far apart may still court. */
	public static final double COURT_RANGE = 64.0;
	/** A village of fewer grown-ups (a young hamlet) has no mayor yet. */
	public static final int MAYOR_MIN_ADULTS = 4;
	/** A hamlet stands at least this far from any town (two grown towns' reach). */
	public static final int HAMLET_FAR = (int) (TownRules.MAX_REACH * 2) + 4;
	/** Families' pets in one town at most. */
	public static final int PETS_PER_TOWN = 4;

	// ---- Houses ----------------------------------------------------------------------------------------------

	/** A house off the plan (no lot fits) stands this far from the bell at least, and its ring search reaches the town's
	 * edge less HOUSE_MAX_DISTANCE's margin to NEAR_BELL. */
	public static final int HOUSE_MIN_DISTANCE = 12;
	public static final int HOUSE_MAX_DISTANCE = 40;
	/** A villager without a trade helps build a neighbour's house this far away. */
	public static final int HELP_RADIUS = 32;
	/** Helpers at most on one house besides its owner. */
	public static final int HOUSE_HELPERS = 0;
	/** Helpers on the town's base (the shop house, the hall) going up or mended besides its official: one drops what it does for it (HomeProjects.forBuilder). */
	public static final int BASE_HELPERS = 0;
	/** People a town has before it lays out a churchyard (its first death does it sooner). */
	public static final int CEMETERY_PEOPLE = 6;
	/** Days a grave stands before it gives way (a full churchyard reuses one this old already). */
	public static final int GRAVE_DAYS = 40;
	public static final int GRAVE_REUSE_DAYS = 10;

	// ---- Days and waits (Family.day: game days) --------------------------------------------------------------

	/** Homeless this long, a villager starts a house of its own. */
	public static final int HOMELESS_DAYS = 2;
	/** A mayor without a home (and no hall going up) this long steps down. */
	public static final int MAYOR_HOMELESS_DAYS = 3;
	/** A resident nobody saw for this long while its village was ticking is gone (carried off, killed out of sight). */
	public static final int GONE_DAYS = 5;
	/** A door no road reached waits this long before the next try (the ground may change, chunks load). */
	public static final int RETRY_DAYS = 3;
	/** A mending stuck on cells nobody reaches waits this long. */
	public static final int MEND_RETRY_DAYS = 2;
	/** Days a thing waits for hands before it is set down anyway. */
	public static final int PLACE_WAIT_DAYS = 2;
	/** Days without ground for a stall before the keeper trades from a kiosk by the bell. */
	public static final int KIOSK_DAYS = 2;
	/** Food or a bed a family cannot afford this long, the treasury pays. */
	public static final int HELP_DAYS = 2;
	/** The caravan comes every this many days: deliveries, buying up surplus, the fair. */
	public static final int CARAVAN_DAYS = 7;
	/** A town this full of its limit grows older: below it nobody ages (a small town is not to die out before its births). */
	public static final double AGE_FROM = 0.5;
	/** And this full, its eldest pass away: never more than one a night, so a full town makes room for its young slowly. */
	public static final double PASS_FROM = 0.8;
	/** A town keeps a hero for every this many of its people (spire/Heroes). */
	public static final int PEOPLE_PER_HERO = 8;
	/** Town buildings keep this far off the line of a main street out of the square. */
	public static final int MAIN_STREET_CLEAR = 3;
	/** What the fountain and the heroes' statue cost a town with its spire (the square's third step). */
	public static final int FOUNTAIN_PURSE = 256;
	/** A service every this many days, in the evening, after the day's work. */
	public static final int SERVICE_EVERY = 7;
	/** Villagers post new errands on the notice board every this many days. */
	public static final int REQUEST_EVERY = 3;

	// ---- Money (copper; Coins: 64 copper a silver) -----------------------------------------------------------

	/** A day's wage: a worker (plus its workshop's tier), the night watch, the baker. */
	public static final int WAGE = 4;
	/** Copper the treasury pays for a winding. */
	public static final int CLOCK_WAGE = 3;
	/** The town's gardener's pay for a morning's planting (Greenery). */
	public static final int GARDEN_WAGE = 2;
	public static final int MAYOR_WAGE = 8;
	public static final int KEEPER_WAGE = 6;
	/** Copper a day a helper may earn on other people's builds, a copper a block. */
	public static final int DAY_LABOUR = 24;
	/** A household saves this much at most: above it the wage stays in the treasury (and the family spends). */
	public static final int PURSE_CAP = 16 * Coins.SILVER;
	/** The stall's till: what it opens with, what it keeps at least and at most (the rest is tax). */
	public static final int TILL_START = 6 * Coins.SILVER;
	public static final int TILL_FLOOR = 2 * Coins.SILVER;
	public static final int TILL_CAP = 24 * Coins.SILVER;
	/** What a new town's treasury starts with. */
	public static final int TREASURY_START = 4 * Coins.SILVER;
	/** The mayor's gift to a household when a baby is born; the cradle comes out of it. */
	public static final int BIRTH_GIFT = 24;
	/** A stall pays players this much a day at most: a player cannot empty the village's money into its pockets. */
	public static final int PLAYER_BUDGET = 4 * Coins.SILVER;
	/** The mood below which the stall will not trade with the player. */
	public static final float REFUSE_MOOD = -60.0F;
	/** Copper per column of a lot (a player or a villager buys it). */
	public static final int LOT_PRICE_PER_COLUMN = 1;

	// ---- homes are bought (Housing) ----
	/** The share of a home's price paid down from the household's own coins; at least DOWN_MIN. */
	public static final double DOWN_SHARE = 0.1;
	public static final int DOWN_MIN = 16;
	/** Coins a household keeps for bread when it pays for a home. */
	public static final int RESERVE = 8;
	/** The most a household may owe the treasury (homes, storeys, workshops): over it the town pays the rest of a home. */
	public static final int LOAN_CAP = 4 * Coins.SILVER;
	/** Homeless this long without the down payment saved, a villager gets it from the mayor: nobody stays homeless for good. */
	public static final int HOMELESS_GRACE = 3;
	/** A house for sale goes for this share of what its materials cost (the deed a player buys too). */
	public static final double RESALE = 0.8;
	/** Villagers the town knew in its first days live in the world's houses by right (they were there first). */
	public static final int FOUNDING_DAYS = 1;

	// ---- The plan: streets, lots, the square -----------------------------------------------------------------

	/** A lot's width along its street and its depth: the widest house (13) with a block of yard each side. */
	public static final int LOT = 15;
	/** Where a full lot does not fit, a small one: the small houses (7 to 9 wide) with a block of yard. */
	public static final int SMALL_LOT = 11;
	/** Streets out of the square: one to each side of the world (lots are square to the world, and so are streets). */
	public static final int STREETS = 4;
	/** A street grows toward a point this far ahead each step, a few steps a day. */
	public static final int STREET_STEP = 12;
	public static final int STREET_STEPS_PER_DAY = 3;
	/** A side street leaves a street every this many cells (both ways, where there is room). */
	public static final int BRANCH_EVERY = 3 * TownRules.LOT;
	/** Two streets side by side keep this apart: a lot and its verge on each. */
	public static final int STREETS_APART = 2 * (TownRules.LOT + 2);
	/** The ground of a lot may lie a block under the street and three over it (dug away). */
	public static final int LOT_DIP = 1;
	public static final int LOT_RISE = 3;
	/** A lot may have pits this many layers deep under its floor: they are filled (TownPlan.groundwork), deeper is no lot. */
	public static final int LOT_FILL = 1;
	/** A lot's floor may lie this far over or under its street. */
	public static final int LOT_TERRACE = 3;
	/** A lot whose clearing would take more blocks than this is a hillside, not a lot. */
	public static final int LOT_MAX_CLEARING = 600;
	public static final int SQUARE_SMALL = 4;
	public static final int SQUARE_LARGE = 6;

	// ---- Roads -----------------------------------------------------------------------------------------------

	/** Roads planned per town per day: the network grows over days, as the houses do. */
	public static final int ROUTES_PER_DAY = 3;
	public static final int LAMP_EVERY = 12;
	public static final int LAMP_GAP = 7;
	/** An iron golem for every this many people of the town (at least one), and no more than GOLEMS_MAX (Guards). */
	public static final int PEOPLE_PER_GOLEM = 10;
	public static final int GOLEMS_MAX = 3;
	/** How far round a villager calling for a golem (a panic) the town's villagers count, against vanilla's ten. */
	public static final double GOLEM_CALL = 24;
	/** Builders on one stretch of road at a time: the others keep to their own work. */
	public static final int ROAD_BUILDERS = 1;
	/** The stall's stock of each building material: this many times the most one plan takes (several builds at once). */
	public static final int BUILD_STOCK = 3;
	/** Building materials a sold-out stall gets brought from abroad in a day, for its builders; the rest comes next morning. */
	public static final int ABROAD_PER_DAY = 256;
	/** Cells per stretch when a path is paved: a builder does one stretch in a day or two. */
	public static final int ROAD_STRETCH = 48;
	/** A bridge's piers stand this far apart, under its sides (boats pass under the middle). */
	public static final int PIER_EVERY = 4;
	/** Deeper than this a pier is not driven: the deck carries itself (blocks need no support). */
	public static final int PIER_DEPTH = 8;

	// ---- The wall --------------------------------------------------------------------------------------------

	public static final int WALL_MARGIN = 4;
	public static final int WALL_HEIGHT = 4;
	public static final int TOWER_HEIGHT = 5;
	public static final int TOWER_EVERY = 16;
	/** Columns per stretch. */
	public static final int WALL_STRETCH = 40;
	/** Water the wall dams: this deep at most, this many columns along the wall at most (a stream, not a lake). */
	public static final int WALL_MAX_DEPTH = 10;
	public static final int WALL_MAX_SPAN = 64;
	/** The ditch: this far out from the wall, two wide, two deep; none this near a gate. */
	public static final int DITCH_OUT = 2;
	public static final int DITCH_DEPTH = 2;
	public static final int GATE_CLEAR = 3;

	// ---- travellers (Travellers) ---------------------------------------------------------------

	/** Days at least between travellers at a town's tavern, and the chance one comes on such an evening. */
	public static final int TRAVELLER_EVERY = 3;
	public static final float TRAVELLER_CHANCE = 0.6F;
	/** Days a traveller stays at the tavern before going on its way, if nobody talked it into staying. */
	public static final int TRAVELLER_STAYS = 2;
	/** The least a traveller asks in coins to stay. */
	public static final int TRAVELLER_LEAST_PRICE = 16;

	// ---- the storyteller (TownStory) -------------------------------------------------------------

	/** The chance a day brings an event (after a quiet day), and days before the same kind comes again. */
	public static final float STORY_CHANCE = 0.5F;
	public static final int STORY_COOLDOWN = 10;
	/** People at least for a feast; how long a child stays lost; how long the sick lie ill. */
	public static final int FEAST_PEOPLE = 6;
	public static final long LOST_TICKS = 4800;
	public static final long SICK_TICKS = 24000;
	/** Blocks a storm takes off a roof. */
	public static final int STORM_BLOCKS = 5;

	// ---- the mayor's work orders (Upkeep) --------------------------------------------------------------------

	/** A pit is filled from this many blocks under the ground round it (a block's dip is the land's own) ... */
	public static final int PIT_MIN = 2;
	/** ... down to this deep; a deeper ravine is left as it is (a day's earth would not fill a stretch of it). */
	public static final int PIT_MAX = 8;
	/** The ground round a pit is looked for this far either way along a line. */
	public static final int PIT_BANK = 6;
	/** A pit is the town's to fill this near something of the town (a building, a way, a lot, a road). */
	public static final int PIT_NEAR = 6;
	/** Blocks of earth the mayor orders into the town's pits in a day. */
	public static final int PIT_BLOCKS_PER_DAY = 256;
	/** Levelling (Upkeep.levelling): a square this many columns across (room for a few houses), dug down at most
	 * LEVEL_CUT and filled up at most LEVEL_FILL (water included) to its middle height. */
	public static final int LEVEL_TILE = 24;
	public static final int LEVEL_CUT = 4;
	public static final int LEVEL_FILL = 4;
	/** Blocks of debris (gravel, sand, leaves, snow, weeds) the mayor has cleared off the roads in a day. */
	public static final int DEBRIS_PER_DAY = 64;
	/** Ticks between two looks for things lying on the roads; and how many get picked up in one look. */
	public static final long LITTER_EVERY = 1200;
	public static final int LITTER_PER_LOOK = 4;

	/** Towns kept loaded whole round their players at once (TownLoading): a busy server's ceiling on chunks. */
	public static final int MAX_HELD_TOWNS = 8;

	// ---- the monsters' time ----------------------------------------------------------------------------------

	/** Dusk and dawn (time of day): monsters spawn in the open between them, and in a thunderstorm by day too. */
	public static final long NIGHT_FROM = 12500;
	public static final long NIGHT_TO = 23500;

	/** The monsters' time: night, or a thunderstorm (the one rule of the watch, the panic in a room and the like). */
	public static boolean monstersOut(net.minecraft.server.level.ServerLevel level) {
		long time = com.vnap.dialogue.Family.timeOfDay(level);
		return (time >= NIGHT_FROM && time < NIGHT_TO) || level.isThundering();
	}
}
