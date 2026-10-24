package com.vnap.village.town;

import com.vnap.Nbt;
import com.vnap.dialogue.Family;
import com.vnap.mood.MoodConfig;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.Graveyard;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Market;
import com.vnap.village.market.Materials;
import com.vnap.village.market.Site;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.WorkshopState;
import com.vnap.village.workshop.Workshops;
import com.vnap.entity.VillagerLifeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The town's roads: every house door and workshop gate is joined to the network (or to a village path, or to the
 * bell) by a road the villagers lay by hand, block by block, with materials from the stall that the treasury pays
 * for (a town job: day labourers earn a copper a block). A trodden path first; stone once the town is half full.
 * Lamps stand along the way. Planned once a day per town and only over entity-ticking chunks; a road lies on the
 * town's own land (Towns.ours) and nothing else is ever built over it.
 */
public final class Roads extends SavedData {
	private static final String NAME = "villager_life_roads";

	private final List<Network> networks = new ArrayList<>();
	private final Map<Integer, Set<UUID>> builders = new HashMap<>();
	private int nextId;

	/** One town's roads. */
	public static final class Network {
		public final ResourceKey<Level> dimension;
		public BlockPos bell;
		/** Every road cell (the surface block), in the order they were laid. */
		public final Set<BlockPos> cells = new LinkedHashSet<>();
		public final Set<BlockPos> lamps = new HashSet<>();
		/** Nodes joined and when a node was last tried: node (surface by the door) -> day. */
		final Map<Long, Long> tried = new HashMap<>();
		final Set<Long> joined = new HashSet<>();
		public final List<Segment> segments = new ArrayList<>();
		public int tier = 1;
		/** The square round the bell the town has (Plaza: 0 none, 1 paved, 2 with a well and benches) and its things. */
		public int plaza;
		public final Set<BlockPos> features = new HashSet<>();
		/** The wall (Wall: 0 none, 1 laid out) and the ground under its columns (kept free of houses). */
		public int wall;
		public final Set<BlockPos> wallCells = new HashSet<>();
		/** The crossbows on its towers and the store their arrows are bought into (the treasury's money; TurretBehavior). */
		public final Set<BlockPos> turrets = new HashSet<>();
		/** The ditch's bottom cells outside the wall (Wall.ditch): no road across them. */
		public final Set<BlockPos> moat = new HashSet<>();
		public final Site armoury = new Site();
		/** The town's own buildings (TownPlots): the ground each keeps, and the last day one found no room. */
		public final java.util.EnumMap<TownPlots.Kind, TownPlots.Plot> plots = new java.util.EnumMap<>(TownPlots.Kind.class);
		final java.util.EnumMap<TownPlots.Kind, Long> plotTried = new java.util.EnumMap<>(TownPlots.Kind.class);
		/** The plan (TownPlan): its streets out of the square and the lots cut along them. */
		public final List<TownPlan.Street> streets = new ArrayList<>();
		public final List<TownPlan.Lot> lots = new ArrayList<>();
		int nextLot;
		/** The workshops planned in the town, by their job block: their ground, kept while their worker is away. */
		public final Map<Long, BoundingBox> workshops = new HashMap<>();
		/** The houses of the village the world generated round the bell (WorldHouses): the town's buildings too. */
		public final List<BoundingBox> worldHouses = new ArrayList<>();
		/** Its land surveyed (TownSurvey: squares by their offset from the bell) and the people it holds (-1: not seen enough). */
		public final Set<Long> goodLand = new HashSet<>();
		public final Set<Long> badLand = new HashSet<>();
		int landCells;
		public int capacity = -1;
		/** Its life's last days (TownLife: the service, the tavern evening, the errands) and the watch (Watch, WatchBehavior). */
		public long servedDay = Long.MIN_VALUE / 2;
		public long eveningDay = Long.MIN_VALUE / 2;
		public long lunchDay = Long.MIN_VALUE / 2;
		public long requestDay = Long.MIN_VALUE / 2;
		public long musicAt = Long.MIN_VALUE / 2;
		public long alarmAt = Long.MIN_VALUE / 2;
		public long watchNight = Long.MIN_VALUE / 2;
		public final Set<UUID> watchers = new HashSet<>();
		/** A player's things taken off the town's lots, waiting for room in the lost and found (NoticeBoard). */
		public final List<net.minecraft.world.item.ItemStack> lost = new ArrayList<>();
		/** The town's flag (TownFlag code, -1: none yet) and the banners it hangs (TownFlag.plan). */
		public int flag = -1;
		public final Set<BlockPos> banners = new HashSet<>();
		/** The last day a traveller came to the tavern (Travellers). */
		public long travellerDay = Long.MIN_VALUE / 2;
		/** What happened in the town (Chronicle), and its day's event (TownStory). */
		public final List<Chronicle.Entry> chronicle = new ArrayList<>();
		public final TownStory.State story = new TownStory.State();
		/** The town's churchyard (Cemetery): the middle of its way in and the way it faces (toward the bell), or null. */
		public @Nullable BlockPos cemetery;
		public Direction cemeteryFace = Direction.NORTH;
		/** Houses (by their footprint's corner) whose yard was laid or tried and the day of the last try (Yards). */
		public final Map<Long, Long> yards = new HashMap<>();
		/** Houses (as in yards) and how far their comfort has come: the next step in the low byte, the day it was last tried above (Comfort). */
		public final Map<Long, Long> comfort = new HashMap<>();
		/** Jukeboxes the villagers set out for a gathering (Music), taken away after. */
		public final Set<BlockPos> jukeboxes = new HashSet<>();
		/** Houses of the world's village a player bought with a deed (HouseDeedItem), and whose: no villager moves in. */
		public final Map<BoundingBox, java.util.UUID> boughtHouses = new HashMap<>();
		/** Where the town's new bell is to hang, its old one buried in a cave (BellMove), or null. */
		public @Nullable BlockPos bellMove;
		/** Where the town's next iron golem is being set up by hand (Guards), or null. */
		public @Nullable BlockPos golem;
		/** Plants the town keeps along its ways and in its nooks, by the cell they grow in (Greenery): the gardener tends them. */
		public final Map<Long, net.minecraft.world.level.block.Block> greens = new HashMap<>();
		/** The nooks between the houses laid out so far, by their middle (Greenery). */
		public final Set<BlockPos> nooks = new HashSet<>();
		/** The day the town last looked for a free gap between its houses (in memory: after a restart it looks again). */
		long nookDay = Long.MIN_VALUE / 2;
		long day = Long.MIN_VALUE;

		Network(ResourceKey<Level> dimension, BlockPos bell) {
			this.dimension = dimension;
			this.bell = bell.immutable();
		}
	}

	/** A stretch of road waiting to be laid: a little plan of its own, built like a house. */
	public static final class Segment {
		public final int id;
		final List<SchematicSpec.PlacedBlock> blocks;
		public final BlockPos origin;
		public final SchematicSpec spec;
		public final Site site;
		private @Nullable BuildJob job;
		private @Nullable CompoundTag savedJob;
		public long pausedDay = Long.MIN_VALUE;
		/** What the stretch is of (road, paving, square, wall, ditch, chapel, bakehouse, tavern): the notice board says. */
		public String label = "road";

		Segment(int id, List<SchematicSpec.PlacedBlock> world, Site site) {
			this.id = id;
			this.blocks = world;
			this.site = site;
			int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE, mx = Integer.MIN_VALUE, my = Integer.MIN_VALUE, mz = Integer.MIN_VALUE;
			for (SchematicSpec.PlacedBlock block : world) {
				x = Math.min(x, block.rel().getX());
				y = Math.min(y, block.rel().getY());
				z = Math.min(z, block.rel().getZ());
				mx = Math.max(mx, block.rel().getX());
				my = Math.max(my, block.rel().getY());
				mz = Math.max(mz, block.rel().getZ());
			}
			origin = new BlockPos(x, y, z);
			List<SchematicSpec.PlacedBlock> rel = new ArrayList<>();
			for (SchematicSpec.PlacedBlock block : world) rel.add(new SchematicSpec.PlacedBlock(block.rel().subtract(origin), block.state()));
			spec = new SchematicSpec(mx - x + 1, my - y + 1, mz - z + 1, List.of(), rel, BlockPos.ZERO);
		}

		/** The road's own order (along the way), ground anywhere: a road replaces the natural surface at every height. */
		public BuildJob job() {
			BuildJob current = job;
			if (current == null) {
				CompoundTag saved = savedJob;
				current = saved == null ? null : BuildJob.load(saved, name -> spec);
				if (current == null) current = BuildJob.all("road", spec, origin, Rotation.NONE, BuildJob.ALL_GROUND);
				current.site = site;
				job = current;
			}
			return current;
		}
	}

	public static Roads get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, Roads::new, Roads::load, Roads::save);
	}

	public List<Network> networks() {
		return networks;
	}

	/** The roads of the town around this bell (its office's bell), made when it has none yet. */
	public Network of(ResourceKey<Level> dimension, BlockPos bell) {
		Network nearest = null;
		for (Network network : networks) {
			if (network.dimension.equals(dimension) && network.bell.closerThan(bell, TownRules.SPAN)
					&& (nearest == null || network.bell.distSqr(bell) < nearest.bell.distSqr(bell))) nearest = network;
		}
		if (nearest != null) return nearest;
		Network made = new Network(dimension, bell);
		networks.add(made);
		setDirty();
		return made;
	}

	public @Nullable Network near(ResourceKey<Level> dimension, BlockPos pos) {
		Network best = null;
		for (Network network : networks) {
			if (network.dimension.equals(dimension) && (best == null || network.bell.distSqr(pos) < best.bell.distSqr(pos))) best = network;
		}
		return best != null && best.bell.closerThan(pos, TownRules.MAX_REACH + 16) ? best : null;
	}

	// ---- planning -----------------------------------------------------------------------

	/** Once a day per town (Mayors.daily, a villager of the town in a ticking chunk). */
	public static void daily(ServerLevel level, Mayors.Office office, long day) {
		if (!level.isPositionEntityTicking(office.bell)) return;
		Roads roads = get(level.getServer());
		Network network = roads.of(level.dimension(), office.bell);
		if (network.day == day) return;
		network.day = day;
		roads.setDirty();
		roads.plan(level, network, day, TownRules.ROUTES_PER_DAY);
	}

	/** Up to {@code routes} new roads for doors not joined yet, and paving once the town is half full. */
	public int plan(ServerLevel level, Network network, long day, int routes) {
		int tier = Towns.population(level, network.bell) * 2 >= Towns.limit(level, network.bell) ? 2 : 1;
		if (tier > network.tier) network.tier = tier;
		// a stretch whose last block went in as its builders' shift ended is done all the same
		for (Segment segment : List.copyOf(network.segments)) {
			if (segment.job().done()) finish(level, segment);
		}
		Set<BlockPos> queued = new HashSet<>();
		for (Segment segment : network.segments) {
			for (SchematicSpec.PlacedBlock block : segment.blocks) queued.add(block.rel());
		}
		// a road torn up (a player, a creeper, a house rebuilt over it) is no road any more: looked at where loaded
		network.cells.removeIf(cell -> level.isLoaded(cell) && !roadSurface(level.getBlockState(cell)) && !queued.contains(cell));
		network.lamps.removeIf(lamp -> level.isLoaded(lamp.above(3)) && !lit(level, lamp, queued));
		// a yard, a lamp, a bench taken away (a player, a house rebuilt over its yard): its land is free again
		network.features.removeIf(thing -> level.isLoaded(thing) && level.getBlockState(thing).isAir() && !queued.contains(thing)
				&& !com.vnap.village.home.Placements.pending(level, thing));
		// paths that were out of the loaded world when the town was paved are paved once they are in it
		pave(level, network, queued, Math.min(network.tier, 2));
		// a bell the world buried in a cave is hung anew on the ground above before anything is built round it
		BellMove.plan(level, this, network);
		if (network.bellMove != null) return 0;
		Plaza.earlyBoard(level, this, network);
		Lighting.town(level, this, network);
		Lighting.hollows(level, network);
		// the mayor's orders for the town's ground: its pits filled, its roads cleared
		Upkeep.plan(level, this, network);
		Guards.plan(level, this, network);
		Shelter.beds(level, network);
		// the square round the bell first: the roads run to it
		Plaza.plan(level, this, network);
		TownFlag.plan(level, network);
		if (Towns.population(level, network.bell) >= TownRules.CEMETERY_PEOPLE) Cemetery.plan(level, this, network);
		Yards.plan(level, this, network, day);
		Comfort.plan(level, this, network, day);
		Signage.plan(level, this, network);
		Greenery.plan(level, this, network);
		TownPlots.plan(level, this, network);
		TownPlan.plan(level, this, network);
		TownPlan.mark(level, network);
		Errands.daily(level, network, day);
		NoticeBoard.lostAndFound(level, network);
		Wall.plan(level, this, network);
		// a town done to its wall wakes the Core under it: a spire rises the next night
		com.vnap.spire.SpireGrowth.plan(level, network);
		com.vnap.spire.Expeditions.daily(level, network);
		com.vnap.village.home.HouseLevels.secondAge(level, network);
		TownPlots.restyle(level, this, network);
		int made = 0;
		for (BlockPos node : nodes(level, network)) {
			if (made >= routes) break;
			long key = node.asLong();
			// joined is checked on the ground every day: a road gone is laid again
			if (reached(network, node)) {
				network.joined.add(key);
				continue;
			}
			network.joined.remove(key);
			Long last = network.tried.get(key);
			if (last != null && day - last < TownRules.RETRY_DAYS) continue;
			network.tried.put(key, day);
			if (connect(level, network, node)) {
				network.joined.add(key);
				made++;
			}
		}
		setDirty();
		return made;
	}

	/** The doors to join: the villagers' houses (the hall and the stall too) and the workshops of the town. */
	public static List<BlockPos> nodes(ServerLevel level, Network network) {
		List<BlockPos> out = new ArrayList<>();
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.done || !Towns.ours(level, network.bell, house.origin)) continue;
			BlockPos step = step(level, house.schematic(level), house.origin, house.rotation, house.footprint(level));
			if (step != null) out.add(step);
		}
		for (Villager villager : Towns.people(level, network.bell, villager -> true)) {
			WorkshopState state = villager instanceof VillagerLifeData data ? data.vnap$workshop() : null;
			if (state == null || state.tier <= 0 || !Towns.ours(level, network.bell, state.origin)) continue;
			SchematicSpec spec = Workshops.schematic(level, Workshops.schematicName(state.profession, state.tier));
			BlockPos step = step(level, spec, state.origin, state.rotation, Workshops.footprint(state));
			if (step != null) out.add(step);
		}
		out.addAll(TownPlots.doors(level, network));
		out.addAll(WorldHouses.doors(level, network));
		BlockPos cemetery = Cemetery.node(network);
		if (cemetery != null && level.isLoaded(cemetery)) out.add(cemetery);
		// the nearest first: the network grows out from the bell
		out.sort(java.util.Comparator.comparingDouble(pos -> pos.distSqr(network.bell)));
		return out;
	}

	/**
	 * The ground just past the building's front door, as the surface cell a road ends at: from the door's outer side
	 * (the one away from the middle of the building) straight on out of its plot, over a porch if it has one; a door
	 * upstairs (a balcony) is too high above that ground and left out. Null when there is none in a ticking chunk.
	 */
	static @Nullable BlockPos step(ServerLevel level, SchematicSpec spec, BlockPos origin, Rotation rotation, BoundingBox footprint) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		BlockPos centre = footprint.getCenter();
		for (SchematicSpec.PlacedBlock block : spec.blocks) {
			BlockState state = block.state();
			boolean door = state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
			if (!door && !(state.getBlock() instanceof FenceGateBlock)) continue;
			BlockPos at = BuildJob.at(origin, rotation, block.rel());
			Direction facing = state.rotate(level, at, rotation).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
			Direction out = horizontal(at.relative(facing), centre) >= horizontal(at.relative(facing.getOpposite()), centre) ? facing : facing.getOpposite();
			BlockPos past = at.relative(out);
			for (int i = 0; i < 12 && inside(footprint, past); i++) past = past.relative(out);
			RoadFinder.Cell cell = RoadFinder.surface(level, past.getX(), past.getZ(), pos -> false);
			if (cell == null || Math.abs(cell.pos().getY() - (at.getY() - 1)) > 1) continue;
			// the door nearest the ground of the way out first (the front door, not a garden gate round the back)
			double distance = at.getY() * 1000.0 + cell.pos().distSqr(at);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = cell.pos();
			}
		}
		return best;
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	private static boolean inside(BoundingBox box, BlockPos pos) {
		return pos.getX() >= box.minX() && pos.getX() <= box.maxX() && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ();
	}


	/** A road at or right by the node already: nothing to lay. */
	private static boolean reached(Network network, BlockPos node) {
		for (BlockPos cell : network.cells) {
			if (Math.abs(cell.getX() - node.getX()) + Math.abs(cell.getZ() - node.getZ()) <= 1 && Math.abs(cell.getY() - node.getY()) <= 1) return true;
		}
		return false;
	}

	/** A road from the node to the network (or a village path, or the bell), queued as a stretch to lay. */
	boolean connect(ServerLevel level, Network network, BlockPos node) {
		BlockPos bell = network.bell;
		Mayors.Office office = Towns.of(level, bell);
		BlockPos centre = office == null ? bell : office.bell;
		// the door's own lot is the way out of it
		Land land = Land.around(level, node).without(claim -> claim.layer() == Land.Layer.LOT && claim.box().isInside(new BlockPos(node.getX(), claim.box().minY(), node.getZ())));
		Set<BlockPos> cells = network.cells;
		java.util.function.Predicate<BlockPos> road = pos -> cells.contains(pos);
		// a planned street will do too: it is laid from the square to where the road meets it
		Set<BlockPos> streets = TownPlan.streetCells(network);
		java.util.function.Predicate<BlockPos> goal = pos -> cells.contains(pos) || streets.contains(pos)
				|| (!pos.closerThan(node, 4.0) && level.getBlockState(pos).is(Blocks.DIRT_PATH))
				|| ring(centre, pos);
		java.util.function.Predicate<BlockPos> ours = Towns.territory(level, bell)::contains;
		List<RoadFinder.Cell> route = RoadFinder.route(level, node, goal, ours, land, road, cells.isEmpty() ? centre : nearest(cells, node, centre));
		if (route.isEmpty()) return false;
		lay(level, network, route);
		TownPlan.layTo(level, this, network, route.get(route.size() - 1).pos());
		return true;
	}

	/** The ring around the bell a first road runs to: the square the town gathers on. */
	private static boolean ring(BlockPos bell, BlockPos pos) {
		int d = Math.max(Math.abs(pos.getX() - bell.getX()), Math.abs(pos.getZ() - bell.getZ()));
		return d >= 2 && d <= 3 && Math.abs(pos.getY() - bell.getY()) <= 3;
	}

	private static BlockPos nearest(Set<BlockPos> cells, BlockPos from, BlockPos fallback) {
		BlockPos best = fallback;
		double distance = from.distSqr(fallback);
		for (BlockPos cell : cells) {
			double d = cell.distSqr(from);
			if (d < distance) {
				distance = d;
				best = cell;
			}
		}
		return best;
	}

	/** The route becomes road cells (bridges over water) and lamps along it, one stretch the treasury pays for. */
	public void lay(ServerLevel level, Network network, List<RoadFinder.Cell> route) {
		lay(level, network, route, "road");
	}

	public void lay(ServerLevel level, Network network, List<RoadFinder.Cell> route, String label) {
		boolean street = label.equals("street");
		Land blocked = Land.around(level, route.get(0).pos());
		String palette = HousePalette.forBiome(level.getBiome(route.get(0).pos()));
		List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
		int span = 0;
		for (int i = 0; i < route.size(); i++) {
			RoadFinder.Cell cell = route.get(i);
			if (cell.water()) {
				bridge(level, network, route, i, span++, palette, street, blocks);
				continue;
			}
			span = 0;
			boolean laid = network.cells.contains(cell.pos()) || roadSurface(level.getBlockState(cell.pos()));
			network.cells.add(cell.pos().immutable());
			// the grass and flowers over the road are cut first (the road is laid in this order): left there, they pop
			// off as the stone goes under them and lie about the road
			boolean step = rises(route, i);
			clear(level, cell.pos(), blocks);
			if (!laid) {
				BlockState surface = surface(network.tier, palette, cell.pos(), false);
				if (!level.getBlockState(cell.pos()).is(surface.getBlock())) blocks.add(new SchematicSpec.PlacedBlock(cell.pos(), surface));
			}
			// a block's rise in the way gets a half step on the lower cell
			if (step) step(level, cell.pos(), palette, network.tier, blocks);
			// a street is three wide: a kerb either side, where the ground is level with it
			if (street) verges(level, network, route, i, palette, blocked, blocks);
		}
		for (int i = 4; i < route.size() - 4; i += 1) {
			if ((i - 4) % TownRules.LAMP_EVERY != 0) continue;
			BlockPos lamp = lampSpot(level, network, route, i, blocked);
			if (lamp == null) continue;
			network.lamps.add(lamp);
			BlockPos at = route.get(i).pos();
			blocks.addAll(lamp(lamp, palette, Direction.getNearest(at.getX() - lamp.getX(), 0, at.getZ() - lamp.getZ())));
		}
		if (blocks.isEmpty()) return;
		queue(level, network, blocks, label);
	}

	Segment queue(ServerLevel level, Network network, List<SchematicSpec.PlacedBlock> blocks) {
		return queue(level, network, blocks, "road");
	}

	/** A stretch a household pays for itself (its yard): its purse, the treasury lending what it lacks (Market.finance). */
	Segment queueFor(ServerLevel level, Network network, List<SchematicSpec.PlacedBlock> blocks, String label, Villager payer) {
		Segment segment = new Segment(nextId++, blocks, new Site());
		segment.label = label;
		if (!Market.finance(level, payer, network.bell, segment.site, Materials.bill(segment.spec), com.vnap.village.market.Housing.Purpose.GROWTH)) {
			nextId--;
			return segment;
		}
		network.segments.add(segment);
		setDirty();
		return segment;
	}

	Segment queue(ServerLevel level, Network network, List<SchematicSpec.PlacedBlock> blocks, String label) {
		Segment segment = new Segment(nextId++, blocks, new Site());
		segment.label = label;
		network.segments.add(segment);
		Market.grant(level, network.bell, segment.site, Materials.bill(segment.spec));
		setDirty();
		return segment;
	}


	/**
	 * One cell of a bridge (Millenaire's and the GDMC settlements' kind): a deck of planks a block over the water as wide
	 * as the way (a street's three), a plank either side with a fence railing, a lantern on the railing at each end, and
	 * every few cells piers of logs from the bed up under the railings. The road's cell is the deck's middle.
	 */
	private static void bridge(ServerLevel level, Network network, List<RoadFinder.Cell> route, int i, int span, String palette, boolean street,
							   List<SchematicSpec.PlacedBlock> blocks) {
		BlockPos water = route.get(i).pos();
		BlockPos deck = water.above();
		network.cells.add(deck.immutable());
		blocks.add(new SchematicSpec.PlacedBlock(deck, bridge(palette)));
		clear(level, deck, blocks);
		BlockPos from = route.get(Math.max(0, i - 1)).pos();
		BlockPos to = route.get(Math.min(route.size() - 1, i + 1)).pos();
		Direction.Axis along = Math.abs(to.getX() - from.getX()) >= Math.abs(to.getZ() - from.getZ()) ? Direction.Axis.X : Direction.Axis.Z;
		Direction left = along == Direction.Axis.X ? Direction.NORTH : Direction.WEST;
		boolean end = span == 0 || i + 1 >= route.size() || !route.get(i + 1).water();
		int width = street ? 1 : 0;
		for (Direction side : new Direction[]{left, left.getOpposite()}) {
			for (int w = 1; w <= width; w++) {
				BlockPos wide = water.relative(side, w);
				if (!wet(level, wide)) continue;
				network.cells.add(wide.above().immutable());
				blocks.add(new SchematicSpec.PlacedBlock(wide.above(), bridge(palette)));
				clear(level, wide.above(), blocks);
			}
			BlockPos edge = water.relative(side, width + 1);
			// over the water only: at the banks the ground is the bridge's side
			if (!wet(level, edge) || network.cells.contains(edge.above())) continue;
			blocks.add(new SchematicSpec.PlacedBlock(edge.above(), bridge(palette)));
			blocks.add(new SchematicSpec.PlacedBlock(edge.above(2), palette(Blocks.OAK_FENCE.defaultBlockState(), palette)));
			network.features.add(edge.above(2));
			if (end) {
				blocks.add(new SchematicSpec.PlacedBlock(edge.above(3), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false)));
				network.features.add(edge.above(3));
			}
			if (span % TownRules.PIER_EVERY != 1) continue;
			int depth = 0;
			while (depth < TownRules.PIER_DEPTH && level.getFluidState(edge.below(depth + 1)).is(net.minecraft.tags.FluidTags.WATER)) depth++;
			if (depth == TownRules.PIER_DEPTH) continue;
			for (int d = depth; d >= 0; d--) blocks.add(new SchematicSpec.PlacedBlock(edge.below(d), palette(Blocks.OAK_LOG.defaultBlockState(), palette)));
		}
	}

	private static boolean wet(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
	}

	/** The height a walker stands on at route step {@code i} (a bridge's deck is a block over its water). */
	private static int floor(List<RoadFinder.Cell> route, int i) {
		RoadFinder.Cell cell = route.get(i);
		return cell.pos().getY() + (cell.water() ? 1 : 0);
	}

	/** The way rises a block right after or before step {@code i} (a land cell). */
	private static boolean rises(List<RoadFinder.Cell> route, int i) {
		int y = floor(route, i);
		return (i > 0 && floor(route, i - 1) == y + 1) || (i + 1 < route.size() && floor(route, i + 1) == y + 1);
	}

	/** A bottom slab on the cell, a half step up the rise (its plant was cut before). */
	private static void step(ServerLevel level, BlockPos cell, String palette, int tier, List<SchematicSpec.PlacedBlock> blocks) {
		BlockState there = level.getBlockState(cell.above());
		if (there.isAir() || there.canBeReplaced() || plant(there)) blocks.add(new SchematicSpec.PlacedBlock(cell.above(), slab(palette, tier)));
	}

	private static boolean plant(BlockState state) {
		return state.getBlock() instanceof net.minecraft.world.level.block.BushBlock || state.getBlock() instanceof net.minecraft.world.level.block.SnowLayerBlock;
	}

	/** Plants and snow over a road cell are cut (no drop: a schematic's air carves), top first. */
	private static void clear(ServerLevel level, BlockPos cell, List<SchematicSpec.PlacedBlock> blocks) {
		for (int up = 2; up >= 1; up--) {
			BlockPos pos = cell.above(up);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) continue;
			if (state.canBeReplaced() || plant(state)) {
				blocks.add(new SchematicSpec.PlacedBlock(pos, Blocks.AIR.defaultBlockState()));
			}
		}
	}

	/** The street's two verge cells at step {@code i}: level natural ground beside it, nobody's, not a bridge. */
	private static void verges(ServerLevel level, Network network, List<RoadFinder.Cell> route, int i, String palette, Land blocked,
							   List<SchematicSpec.PlacedBlock> blocks) {
		BlockPos at = route.get(i).pos();
		BlockPos from = route.get(Math.max(0, i - 1)).pos();
		BlockPos to = route.get(Math.min(route.size() - 1, i + 1)).pos();
		Direction.Axis along = Math.abs(to.getX() - from.getX()) >= Math.abs(to.getZ() - from.getZ()) ? Direction.Axis.X : Direction.Axis.Z;
		Direction left = along == Direction.Axis.X ? Direction.NORTH : Direction.WEST;
		for (Direction side : new Direction[]{left, left.getOpposite()}) {
			BlockPos column = at.relative(side);
			RoadFinder.Cell cell = RoadFinder.surface(level, column.getX(), column.getZ(), network.cells::contains);
			if (cell == null || cell.water() || cell.pos().getY() != at.getY() || network.cells.contains(cell.pos()) || blocked.under(cell.pos())) continue;
			network.cells.add(cell.pos().immutable());
			clear(level, cell.pos(), blocks);
			BlockState surface = surface(network.tier, palette, cell.pos(), true);
			if (!level.getBlockState(cell.pos()).is(surface.getBlock())) blocks.add(new SchematicSpec.PlacedBlock(cell.pos(), surface));
		}
	}

	/** A free cell beside the road (left or right of the way it goes) for a lamp: firm ground, no lamp near. */
	private static @Nullable BlockPos lampSpot(ServerLevel level, Network network, List<RoadFinder.Cell> route, int i, Land blocked) {
		BlockPos at = route.get(i).pos();
		if (route.get(i).water()) return null;
		BlockPos next = route.get(i + 1).pos();
		Direction way = com.vnap.Mc.fromDelta(Integer.signum(next.getX() - at.getX()), 0, Integer.signum(next.getZ() - at.getZ()));
		if (way == null || way.getAxis().isVertical()) return null;
		for (Direction side : new Direction[]{way.getClockWise(), way.getCounterClockWise()}) {
			BlockPos column = at.relative(side);
			RoadFinder.Cell cell = RoadFinder.surface(level, column.getX(), column.getZ(), network.cells::contains);
			// a street's verge takes a lamp (a single road's own cell never does)
			if (cell == null || cell.water()) continue;
			boolean onRoute = false;
			for (RoadFinder.Cell step : route) onRoute |= step.pos().getX() == cell.pos().getX() && step.pos().getZ() == cell.pos().getZ();
			if (onRoute || blocked.under(cell.pos())) continue;
			boolean crowded = false;
			for (BlockPos lamp : network.lamps) crowded |= lamp.closerThan(cell.pos(), TownRules.LAMP_GAP);
			if (crowded || !level.getBlockState(cell.pos()).isFaceSturdy(level, cell.pos(), Direction.UP)) continue;
			return cell.pos().immutable();
		}
		return null;
	}

	/**
	 * A street lamp on {@code ground}: a post of four fence blocks with an arm out over the road ({@code toward}) and a
	 * lantern hanging from it (MineColonies' and the medieval packs' kind), out of the way of a walker's head.
	 */
	static List<SchematicSpec.PlacedBlock> lamp(BlockPos ground, String palette, @Nullable Direction toward) {
		if (toward == null || toward.getAxis().isVertical()) return lamp(ground, palette);
		BlockState fence = palette(Blocks.OAK_FENCE.defaultBlockState(), palette);
		List<SchematicSpec.PlacedBlock> out = new ArrayList<>();
		for (int up = 1; up <= 4; up++) out.add(new SchematicSpec.PlacedBlock(ground.above(up), fence));
		out.add(new SchematicSpec.PlacedBlock(ground.above(4).relative(toward), fence));
		out.add(new SchematicSpec.PlacedBlock(ground.above(3).relative(toward), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)));
		return out;
	}

	/** The lamp on {@code ground} still has its lantern (standing on the post or hanging from the arm), or is to get it. */
	private static boolean lit(ServerLevel level, BlockPos ground, Set<BlockPos> queued) {
		BlockPos top = ground.above(3);
		if (level.getBlockState(top).is(Blocks.LANTERN) || queued.contains(top)) return true;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(top.relative(side)).is(Blocks.LANTERN) || queued.contains(top.relative(side))) return true;
		}
		return false;
	}

	/** A post of two fence blocks with a lantern on top, standing on {@code ground}. */
	static List<SchematicSpec.PlacedBlock> lamp(BlockPos ground, String palette) {
		BlockState fence = palette(Blocks.OAK_FENCE.defaultBlockState(), palette);
		return List.of(new SchematicSpec.PlacedBlock(ground.above(), fence), new SchematicSpec.PlacedBlock(ground.above(2), fence),
				new SchematicSpec.PlacedBlock(ground.above(3), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false)));
	}

	/**
	 * The world village's earthen streets are laid in rough stone like the town's own roads (a town of stone lanes and
	 * dirt tracks looked unfinished); once the town is half full every path is paved in dressed stone, stretch by
	 * stretch along the way they were laid.
	 */
	private void pave(ServerLevel level, Network network, Set<BlockPos> queued, int tier) {
		List<SchematicSpec.PlacedBlock> stretch = new ArrayList<>();
		for (BlockPos cell : network.cells) {
			if (queued.contains(cell) || !level.isLoaded(cell)) continue;
			BlockState state = level.getBlockState(cell);
			if (tier < 2 ? !earthen(state) : !trodden(state)) continue;
			// the square keeps its chequer of rough and dressed stone
			if (Math.max(Math.abs(cell.getX() - network.bell.getX()), Math.abs(cell.getZ() - network.bell.getZ())) <= TownRules.SQUARE_LARGE) continue;
			stretch.add(new SchematicSpec.PlacedBlock(cell, surface(tier, HousePalette.forBiome(level.getBiome(cell)), cell, false)));
			if (stretch.size() >= TownRules.ROAD_STRETCH) {
				queue(level, network, stretch, "paving");
				stretch = new ArrayList<>();
			}
		}
		if (!stretch.isEmpty()) queue(level, network, stretch, "paving");
	}

	/** The road's top block, plain (the square's accents, the kit): rough stone, then dressed stone. */
	static BlockState surface(int tier, String palette) {
		return (tier < 2 ? rough(palette) : dressed(palette))[0].defaultBlockState();
	}

	static BlockState bridge(String palette) {
		return palette(Blocks.OAK_PLANKS.defaultBlockState(), palette);
	}

	/** One block through the house palette (oak becomes the biome's wood). */
	public static BlockState palette(BlockState state, String palette) {
		SchematicSpec one = new SchematicSpec(1, 1, 1, List.of(), List.of(new SchematicSpec.PlacedBlock(BlockPos.ZERO, state)), BlockPos.ZERO);
		SchematicSpec painted = HousePalette.apply(one, palette);
		return painted.blocks.isEmpty() ? state : painted.blocks.get(0).state();
	}

	/**
	 * The road's top block at {@code pos}: stone from the first day (a road of earth and gravel among the grass looked
	 * like rubbish), mostly one stone with a few cells of its kin so it is no flat line, its kerbs a dressed stone. A
	 * new town lays rough stone (cobbles, the desert's sandstone, the savanna's packed mud); half full, it is paved again
	 * in dressed stone (stone bricks with a few cracked and mossy, cut sandstone, mud bricks).
	 */
	static BlockState surface(int tier, String palette, BlockPos pos, boolean verge) {
		if (verge) return kerb(palette).defaultBlockState();
		int r = Math.floorMod(java.util.Objects.hash(pos.getX(), pos.getZ()) * 0x9E3779B1, 100);
		Block[] mix = tier < 2 ? rough(palette) : dressed(palette);
		return (r < 80 ? mix[0] : r < 94 ? mix[1] : mix[2]).defaultBlockState();
	}

	private static Block[] rough(String palette) {
		return switch (palette) {
			case "desert" -> new Block[]{Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE};
			case "savanna" -> new Block[]{Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.PACKED_MUD};
			case "taiga" -> new Block[]{Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE};
			default -> new Block[]{Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE};
		};
	}

	private static Block[] dressed(String palette) {
		return switch (palette) {
			case "desert" -> new Block[]{Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE};
			case "savanna" -> new Block[]{Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.MUD_BRICKS};
			case "taiga" -> new Block[]{Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS};
			default -> new Block[]{Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS};
		};
	}

	static Block kerb(String palette) {
		return switch (palette) {
			case "desert" -> Blocks.CUT_SANDSTONE;
			case "savanna" -> Blocks.MUD_BRICKS;
			case "taiga" -> Blocks.STONE_BRICKS;
			default -> Blocks.POLISHED_ANDESITE;
		};
	}

	/** The half step on a rise, of the road's stone. */
	private static BlockState slab(String palette, int tier) {
		Block slab = switch (palette) {
			case "desert" -> tier < 2 ? Blocks.SANDSTONE_SLAB : Blocks.SMOOTH_SANDSTONE_SLAB;
			case "savanna" -> Blocks.MUD_BRICK_SLAB;
			default -> tier < 2 ? Blocks.COBBLESTONE_SLAB : Blocks.STONE_BRICK_SLAB;
		};
		return slab.defaultBlockState();
	}

	/** A road not paved in dressed stone yet: rough stone, or a world village's path. */
	static boolean trodden(BlockState state) {
		if (earthen(state)) return true;
		for (String palette : HousePalette.ALL) for (Block block : rough(palette)) if (state.is(block)) return true;
		return false;
	}

	/** A world village's path of earth or gravel. */
	static boolean earthen(BlockState state) {
		return state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.GRAVEL);
	}

	/** Whether this is one of the road's own surfaces (it may be walked on and joined). */
	public static boolean roadSurface(BlockState state) {
		if (trodden(state) || state.is(net.minecraft.tags.BlockTags.PLANKS) || state.is(Blocks.ANDESITE)) return true;
		for (String palette : HousePalette.ALL) {
			if (state.is(kerb(palette))) return true;
			for (Block block : dressed(palette)) if (state.is(block)) return true;
		}
		return false;
	}

	/** Every material a stretch of road or a lamp takes, in every palette: the stall keeps them (Market.building). */
	public static List<SchematicSpec> kit() {
		List<SchematicSpec> out = new ArrayList<>();
		for (String palette : HousePalette.ALL) {
			List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
			for (int i = 0; i < TownRules.ROAD_STRETCH; i++) {
				blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(i, 0, 0), rough(palette)[i % 3].defaultBlockState()));
				blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(i, 0, 5), dressed(palette)[i % 3].defaultBlockState()));
				blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(i, 0, 6), kerb(palette).defaultBlockState()));
				if (i % 4 == 0) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(i, 0, 7), slab(palette, 1 + i % 8 / 4)));
			}
			for (int i = 0; i < RoadFinder.MAX_BRIDGE * 3; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(i, 0, 1), bridge(palette)));
			for (int i = 0; i < RoadFinder.MAX_BRIDGE * 2; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(i, 0, 3), palette(Blocks.OAK_FENCE.defaultBlockState(), palette)));
			for (int i = 0; i < TownRules.PIER_DEPTH * 6; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(i, 0, 4), palette(Blocks.OAK_LOG.defaultBlockState(), palette)));
			for (int i = 0; i < 4; i++) for (SchematicSpec.PlacedBlock block : lamp(new BlockPos(i * 3, 0, 2), palette, Direction.SOUTH)) blocks.add(block);
			out.add(new SchematicSpec(Math.max(TownRules.ROAD_STRETCH, TownRules.PIER_DEPTH * 6), 6, 8, List.of(), blocks, BlockPos.ZERO));
		}
		return out;
	}

	/** The square's materials (Plaza.kit) for the stall's catalogue. */
	public static List<SchematicSpec> plazaKit() {
		List<SchematicSpec> out = new ArrayList<>(Plaza.kit());
		out.addAll(Wall.kit());
		out.addAll(Dressing.kit());
		out.addAll(Upkeep.kit());
		return out;
	}

	/** Houses, rooms, workshops, the bell's square and the graves: a road goes round them. */
	static List<BoundingBox> blocked(ServerLevel level, Network network) {
		List<BoundingBox> boxes = new ArrayList<>();
		double reach = Towns.reach(level, network.bell) + 16;
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.origin.closerThan(network.bell, reach)) continue;
			boxes.add(house.footprint(level));
			if (house.hasRoom()) boxes.add(HouseProject.footprint(house.roomSchematic(level), house.origin, house.rotation));
		}
		for (Villager villager : Towns.people(level, network.bell, villager -> true)) {
			WorkshopState state = villager instanceof VillagerLifeData data ? data.vnap$workshop() : null;
			if (state != null && state.maxTier > 0) boxes.add(Workshops.footprint(state));
		}
		boxes.addAll(network.workshops.values());
		boxes.addAll(network.worldHouses);
		boxes.add(BoundingBox.fromCorners(network.bell, network.bell).inflatedBy(1));
		boxes.addAll(TownPlots.footprints(level, network));
		BoundingBox cemetery = Cemetery.footprint(network);
		if (cemetery != null) boxes.add(cemetery);
		for (Graveyard.Grave grave : Graveyard.get(level.getServer()).graves()) {
			if (grave.pos.closerThan(network.bell, reach)) boxes.add(BoundingBox.fromCorners(grave.pos, grave.pos).inflatedBy(1));
		}
		return boxes;
	}

	/**
	 * The one index of the ground given away round {@code pos} (Land): the buildings of the town there (or, outside
	 * any town, the houses, workshops and graves near), every nearby town's ways and planned streets, and its lots.
	 */
	static Land land(ServerLevel level, BlockPos pos) {
		Roads roads = get(level.getServer());
		List<Land.Claim> claims = new ArrayList<>();
		Network home = roads.near(level.dimension(), pos);
		List<BoundingBox> buildings = new ArrayList<>();
		if (home != null) buildings.addAll(blocked(level, home));
		else {
			for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
				if (!house.origin.closerThan(pos, 128)) continue;
				buildings.add(house.footprint(level));
				if (house.hasRoom()) buildings.add(HouseProject.footprint(house.roomSchematic(level), house.origin, house.rotation));
			}
			for (Villager villager : level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(64, 32, 64), Villager::isAlive)) {
				WorkshopState state = villager instanceof VillagerLifeData data ? data.vnap$workshop() : null;
				if (state != null && state.maxTier > 0) buildings.add(Workshops.footprint(state));
			}
			for (Graveyard.Grave grave : Graveyard.get(level.getServer()).graves()) {
				if (grave.pos.closerThan(pos, 128)) buildings.add(BoundingBox.fromCorners(grave.pos, grave.pos).inflatedBy(1));
			}
		}
		for (BoundingBox box : buildings) claims.add(new Land.Claim(Land.Layer.BUILDING, box));
		for (Network network : roads.networks) {
			if (!network.dimension.equals(level.dimension()) || !network.bell.closerThan(pos, TownRules.MAX_REACH * 2 + 32)) continue;
			if (network != home) {
				for (BoundingBox box : TownPlots.footprints(level, network)) claims.add(new Land.Claim(Land.Layer.BUILDING, box));
				for (BoundingBox box : network.workshops.values()) claims.add(new Land.Claim(Land.Layer.BUILDING, box));
			}
			for (BlockPos cell : network.cells) claims.add(new Land.Claim(Land.Layer.WAY, Land.column(cell, 2)));
			for (TownPlan.Street street : network.streets) {
				for (RoadFinder.Cell cell : street.cells) claims.add(new Land.Claim(Land.Layer.WAY, Land.column(cell.pos(), 2)));
			}
			for (BlockPos lamp : network.lamps) claims.add(new Land.Claim(Land.Layer.WAY, Land.column(lamp, 3)));
			for (BlockPos thing : network.features) claims.add(new Land.Claim(Land.Layer.WAY, Land.column(thing, 1)));
			for (BlockPos column : network.wallCells) claims.add(new Land.Claim(Land.Layer.WAY, Land.column(column, TownRules.TOWER_HEIGHT + 1)));
			for (BlockPos cell : network.moat) claims.add(new Land.Claim(Land.Layer.WAY, Land.column(cell, TownRules.DITCH_DEPTH)));
			for (TownPlan.Lot lot : network.lots) claims.add(new Land.Claim(Land.Layer.LOT, lot.box));
		}
		return new Land(claims);
	}

	/** A workshop planned round its job block keeps its ground in the town (null: none there any more). */
	public static void claimWorkshop(ServerLevel level, BlockPos anchor, @Nullable BoundingBox box) {
		Roads roads = get(level.getServer());
		Network network = roads.near(level.dimension(), anchor);
		if (network == null) return;
		BoundingBox was = box == null ? network.workshops.remove(anchor.asLong()) : network.workshops.put(anchor.asLong(), box);
		if (!java.util.Objects.equals(was, box)) roads.setDirty();
	}

	/** Whether a player bought the house of the world's village {@code pos} is in (HouseDeedItem). */
	public static boolean bought(ServerLevel level, BlockPos pos) {
		for (Network network : get(level.getServer()).networks) {
			if (!network.dimension.equals(level.dimension())) continue;
			for (BoundingBox box : network.boughtHouses.keySet()) if (box.isInside(pos)) return true;
		}
		return false;
	}

	/** The roads (and so the town) of this villager's village, or null without a bell. */
	public static @Nullable Network networkOf(ServerLevel level, Villager villager) {
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
		return bell == null ? null : get(level.getServer()).near(level.dimension(), bell);
	}

	// ---- building -----------------------------------------------------------------------

	/** The stretch this villager may lay now: of its own town, its next block in a ticking chunk near, with room for a hand. */
	public @Nullable Segment work(ServerLevel level, Villager villager) {
		Network network = networkOf(level, villager);
		if (network == null) return null;
		long today = Family.day(level);
		for (Segment segment : network.segments) {
			BuildJob job = segment.job();
			if (job.done() || segment.pausedDay == today) continue;
			BlockPos next = job.nextPos();
			if (!level.isPositionEntityTicking(next) || !next.closerThan(villager.blockPosition(), TownRules.WORK_REACH)) continue;
			Set<UUID> hands = builders.computeIfAbsent(segment.id, id -> new HashSet<>());
			hands.removeIf(uuid -> !(level.getEntity(uuid) instanceof Villager other) || !other.isAlive());
			if (hands.size() >= TownRules.ROAD_BUILDERS && !hands.contains(villager.getUUID())) continue;
			return segment;
		}
		return null;
	}

	public void join(Segment segment, Villager villager) {
		builders.computeIfAbsent(segment.id, id -> new HashSet<>()).add(villager.getUUID());
	}

	public void leave(Segment segment, Villager villager) {
		Set<UUID> hands = builders.get(segment.id);
		if (hands != null) hands.remove(villager.getUUID());
	}

	/** The stretch is laid: what money is left goes back to the treasury. */
	public void finish(ServerLevel level, Segment segment) {
		for (Network network : networks) {
			if (network.segments.remove(segment)) {
				Market.settle(level, segment.site, null, network.bell);
				if (segment.label.equals(Plaza.STATUE) && !segment.blocks.isEmpty()) Plaza.unveil(level, segment.blocks.get(0).rel());
				if (java.util.Set.of("chapel", "bakehouse", "tavern", "granary", "school", "market").contains(segment.label)) {
					Chronicle.note(level, network.bell, "built." + segment.label);
				}
				builders.remove(segment.id);
				setDirty();
				return;
			}
		}
	}

	/** Debug (/vnap roads build): every stretch of the town laid now, bought whole by the treasury. */
	public int buildNow(ServerLevel level, Network network) {
		int laid = 0;
		for (Segment segment : List.copyOf(network.segments)) {
			BuildJob job = segment.job();
			Market.kit(level, network.bell, segment.site, job.bill(level, Integer.MAX_VALUE));
			job.completeNow(level);
			laid += job.placed().size();
			finish(level, segment);
		}
		return laid;
	}

	// ---- saving ------------------------------------------------------------------------

	public static Roads load(CompoundTag tag, HolderLookup.Provider registries) {
		Roads roads = new Roads();
		roads.nextId = Nbt.getInt(tag, "NextId");
		for (Tag entry : Nbt.getList(tag, "Networks", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag) entry;
			ResourceLocation dimension = ResourceLocation.tryParse(Nbt.getString(one, "Dimension"));
			if (dimension == null) continue;
			Network network = new Network(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(Nbt.getLong(one, "Bell")));
			for (long cell : Nbt.getLongArray(one, "Cells")) network.cells.add(BlockPos.of(cell));
			for (long lamp : Nbt.getLongArray(one, "Lamps")) network.lamps.add(BlockPos.of(lamp));
			for (long node : Nbt.getLongArray(one, "Joined")) network.joined.add(node);
			CompoundTag tried = Nbt.getCompound(one, "Tried");
			for (String key : com.vnap.Mc.keys(tried)) network.tried.put(Long.parseLong(key), Nbt.getLong(tried, key));
			network.tier = Math.max(1, Nbt.getInt(one, "Tier"));
			network.plaza = Nbt.getInt(one, "Plaza");
			network.wall = Nbt.getInt(one, "Wall");
			network.day = one.contains("Day") ? Nbt.getLong(one, "Day") : Long.MIN_VALUE;
			for (long column : Nbt.getLongArray(one, "WallCells")) network.wallCells.add(BlockPos.of(column));
			for (long turret : Nbt.getLongArray(one, "Turrets")) network.turrets.add(BlockPos.of(turret));
			for (long cell : Nbt.getLongArray(one, "Moat")) network.moat.add(BlockPos.of(cell));
			Site armoury = Site.load(Nbt.getCompound(one, "Armoury"));
			network.armoury.funds = armoury.funds;
			armoury.stock.forEach(network.armoury::add);
			for (long thing : Nbt.getLongArray(one, "Features")) network.features.add(BlockPos.of(thing));
			TownPlan.load(network, one);
			if (one.contains("Served")) network.servedDay = Nbt.getLong(one, "Served");
			for (long cell : Nbt.getLongArray(one, "GoodLand")) network.goodLand.add(cell);
			for (Tag box : Nbt.getList(one, "WorldHouses", Tag.TAG_INT_ARRAY)) {
				int[] b = ((net.minecraft.nbt.IntArrayTag) box).getAsIntArray();
				if (b.length == 6) network.worldHouses.add(new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]));
			}
			for (long cell : Nbt.getLongArray(one, "BadLand")) network.badLand.add(cell);
			network.capacity = one.contains("Capacity") ? Nbt.getInt(one, "Capacity") : -1;
			if (one.contains("Evening")) network.eveningDay = Nbt.getLong(one, "Evening");
			network.flag = one.contains("Flag") ? Nbt.getInt(one, "Flag") : -1;
			for (long banner : Nbt.getLongArray(one, "Banners")) network.banners.add(BlockPos.of(banner));
			if (one.contains("Traveller")) network.travellerDay = Nbt.getLong(one, "Traveller");
			network.chronicle.addAll(Chronicle.load(Nbt.getList(one, "Chronicle", Tag.TAG_COMPOUND)));
			network.story.load(Nbt.getCompound(one, "Story"));
			for (long pos : Nbt.getLongArray(one, "Jukeboxes")) network.jukeboxes.add(BlockPos.of(pos));
			if (one.contains("BellMove")) network.bellMove = BlockPos.of(Nbt.getLong(one, "BellMove"));
			if (one.contains("Golem")) network.golem = BlockPos.of(Nbt.getLong(one, "Golem"));
			if (one.contains("Cemetery")) {
				network.cemetery = BlockPos.of(Nbt.getLong(one, "Cemetery"));
				network.cemeteryFace = Direction.from2DDataValue(Nbt.getInt(one, "CemeteryFace"));
			}
			for (Tag boughtTag : Nbt.getList(one, "Bought", Tag.TAG_COMPOUND)) {
				CompoundTag bought = (CompoundTag) boughtTag;
				int[] b = Nbt.getIntArray(bought, "Box");
				if (b.length == 6 && Nbt.hasUUID(bought, "Player")) network.boughtHouses.put(new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), Nbt.getUUID(bought, "Player"));
			}
			CompoundTag yards = Nbt.getCompound(one, "Yards");
			for (String key : com.vnap.Mc.keys(yards)) network.yards.put(Long.parseLong(key), Nbt.getLong(yards, key));
			CompoundTag comfort = Nbt.getCompound(one, "Comfort");
			for (String key : com.vnap.Mc.keys(comfort)) network.comfort.put(Long.parseLong(key), Nbt.getLong(comfort, key));
			CompoundTag greens = Nbt.getCompound(one, "Greens");
			for (String key : com.vnap.Mc.keys(greens)) {
				net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(Nbt.getString(greens, key));
				if (id != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id)) {
					network.greens.put(Long.parseLong(key), net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id));
				}
			}
			for (long nook : Nbt.getLongArray(one, "Nooks")) network.nooks.add(BlockPos.of(nook));
			if (one.contains("Request")) network.requestDay = Nbt.getLong(one, "Request");
			CompoundTag workshops = Nbt.getCompound(one, "Workshops");
			for (String key : com.vnap.Mc.keys(workshops)) {
				int[] b = Nbt.getIntArray(workshops, key);
				if (b.length == 6) network.workshops.put(Long.parseLong(key), new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]));
			}
			for (Tag stack : Nbt.getList(one, "Lost", Tag.TAG_COMPOUND)) {
				com.vnap.Mc.parseStack(registries, stack).ifPresent(network.lost::add);
			}
			CompoundTag plots = Nbt.getCompound(one, "Plots");
			for (TownPlots.Kind kind : TownPlots.Kind.values()) {
				if (plots.contains(kind.name())) network.plots.put(kind, TownPlots.Plot.load(Nbt.getCompound(plots, kind.name())));
			}
			for (Tag segmentEntry : Nbt.getList(one, "Segments", Tag.TAG_COMPOUND)) {
				CompoundTag s = (CompoundTag) segmentEntry;
				List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
				for (Tag blockEntry : Nbt.getList(s, "Blocks", Tag.TAG_COMPOUND)) {
					CompoundTag b = (CompoundTag) blockEntry;
					blocks.add(new SchematicSpec.PlacedBlock(BlockPos.of(Nbt.getLong(b, "P")),
							NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), Nbt.getCompound(b, "S"))));
				}
				if (blocks.isEmpty()) continue;
				Segment segment = new Segment(Nbt.getInt(s, "Id"), blocks, Site.load(Nbt.getCompound(s, "Site")));
				segment.savedJob = s.contains("Job") ? Nbt.getCompound(s, "Job") : null;
				segment.pausedDay = s.contains("Paused") ? Nbt.getLong(s, "Paused") : Long.MIN_VALUE;
				if (s.contains("Label")) segment.label = Nbt.getString(s, "Label");
				network.segments.add(segment);
			}
			roads.networks.add(network);
		}
		return roads;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putInt("NextId", nextId);
		ListTag list = new ListTag();
		for (Network network : networks) {
			CompoundTag one = new CompoundTag();
			one.putString("Dimension", com.vnap.Mc.id(network.dimension).toString());
			one.putLong("Bell", network.bell.asLong());
			one.putLongArray("Cells", network.cells.stream().mapToLong(BlockPos::asLong).toArray());
			one.putLongArray("Lamps", network.lamps.stream().mapToLong(BlockPos::asLong).toArray());
			one.putLongArray("Joined", network.joined.stream().mapToLong(Long::longValue).toArray());
			CompoundTag tried = new CompoundTag();
			network.tried.forEach((node, day) -> tried.putLong(Long.toString(node), day));
			one.put("Tried", tried);
			one.putInt("Tier", network.tier);
			one.putInt("Plaza", network.plaza);
			one.putInt("Wall", network.wall);
			one.putLong("Day", network.day);
			CompoundTag plots = new CompoundTag();
			network.plots.forEach((kind, plot) -> plots.put(kind.name(), plot.save()));
			one.put("Plots", plots);
			one.putLongArray("WallCells", network.wallCells.stream().mapToLong(BlockPos::asLong).toArray());
			one.putLongArray("Turrets", network.turrets.stream().mapToLong(BlockPos::asLong).toArray());
			one.putLongArray("Moat", network.moat.stream().mapToLong(BlockPos::asLong).toArray());
			one.put("Armoury", network.armoury.save());
			one.putLongArray("Features", network.features.stream().mapToLong(BlockPos::asLong).toArray());
			one.putLong("Served", network.servedDay);
			one.putLongArray("GoodLand", network.goodLand.stream().mapToLong(Long::longValue).toArray());
			ListTag worldHouses = new ListTag();
			for (BoundingBox b : network.worldHouses) worldHouses.add(new net.minecraft.nbt.IntArrayTag(new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()}));
			one.put("WorldHouses", worldHouses);
			one.putLongArray("BadLand", network.badLand.stream().mapToLong(Long::longValue).toArray());
			one.putInt("Capacity", network.capacity);
			one.putLong("Evening", network.eveningDay);
			one.putInt("Flag", network.flag);
			one.putLongArray("Banners", network.banners.stream().mapToLong(BlockPos::asLong).toArray());
			one.putLong("Traveller", network.travellerDay);
			one.put("Chronicle", Chronicle.save(network.chronicle));
			one.put("Story", network.story.save());
			one.putLongArray("Jukeboxes", network.jukeboxes.stream().mapToLong(BlockPos::asLong).toArray());
			BlockPos bellMove = network.bellMove;
			if (bellMove != null) one.putLong("BellMove", bellMove.asLong());
			BlockPos golem = network.golem;
			if (golem != null) one.putLong("Golem", golem.asLong());
			BlockPos cemetery = network.cemetery;
			if (cemetery != null) {
				one.putLong("Cemetery", cemetery.asLong());
				one.putInt("CemeteryFace", network.cemeteryFace.get2DDataValue());
			}
			ListTag boughtList = new ListTag();
			network.boughtHouses.forEach((b, player) -> {
				CompoundTag bought = new CompoundTag();
				bought.putIntArray("Box", new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()});
				Nbt.putUUID(bought, "Player", player);
				boughtList.add(bought);
			});
			one.put("Bought", boughtList);
			CompoundTag yards = new CompoundTag();
			network.yards.forEach((key, day) -> yards.putLong(Long.toString(key), day));
			one.put("Yards", yards);
			CompoundTag comfort = new CompoundTag();
			network.comfort.forEach((key, state) -> comfort.putLong(Long.toString(key), state));
			one.put("Comfort", comfort);
			CompoundTag greens = new CompoundTag();
			network.greens.forEach((key, block) -> greens.putString(Long.toString(key), net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString()));
			one.put("Greens", greens);
			one.putLongArray("Nooks", network.nooks.stream().mapToLong(BlockPos::asLong).toArray());
			one.putLong("Request", network.requestDay);
			one.put("Streets", TownPlan.saveStreets(network.streets));
			one.put("Lots", TownPlan.saveLots(network.lots));
			one.putInt("NextLot", network.nextLot);
			CompoundTag workshops = new CompoundTag();
			network.workshops.forEach((anchor, b) -> workshops.putIntArray(Long.toString(anchor), new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()}));
			one.put("Workshops", workshops);
			ListTag lost = new ListTag();
			for (net.minecraft.world.item.ItemStack stack : network.lost) if (!stack.isEmpty()) lost.add(com.vnap.Mc.saveStack(stack, registries));
			one.put("Lost", lost);
			ListTag segments = new ListTag();
			for (Segment segment : network.segments) {
				CompoundTag s = new CompoundTag();
				s.putInt("Id", segment.id);
				ListTag blocks = new ListTag();
				for (SchematicSpec.PlacedBlock block : segment.blocks) {
					CompoundTag b = new CompoundTag();
					b.putLong("P", block.rel().asLong());
					b.put("S", NbtUtils.writeBlockState(block.state()));
					blocks.add(b);
				}
				s.put("Blocks", blocks);
				s.put("Site", segment.site.save());
				BuildJob job = segment.job;
				if (job != null) s.put("Job", job.save());
				else if (segment.savedJob != null) s.put("Job", segment.savedJob);
				s.putLong("Paused", segment.pausedDay);
				s.putString("Label", segment.label);
				segments.add(s);
			}
			one.put("Segments", segments);
			list.add(one);
		}
		tag.put("Networks", list);
		return tag;
	}
}
