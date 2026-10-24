package com.vnap.village.town;

import com.vnap.Nbt;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The town's plan, laid out ahead of its houses (as Millénaire's villages and the GDMC settlements are): streets run
 * out from the square like spokes, bending round hills, water and what stands there (RoadFinder over the ground), and
 * lots are cut along both sides of them, each kept for one house facing its street. A homeless household gets the
 * nearest free lot and its street is laid up to it; the ground of a lot is looked at once, when it is cut (level
 * enough, the town's own, nothing built on it), so a house never hunts for room again. What stands on a lot when its
 * house is begun (a player's shed, a tree) is taken down by hand first, and a player's things go to the lost and found
 * by the notice board. A player may buy a free lot (the notice board) and build there: no villager touches it.
 * Planned over entity-ticking chunks only, a few steps a day, as the town grows.
 */
public final class TownPlan {
	/** Why lots were not cut (debug, /vnap roads lots): unloaded or not the town's, the square, built over, the ground. */
	static final int[] REFUSED = new int[4];
	static final List<String> HITS = new ArrayList<>();

	private TownPlan() {
	}

	/** One street: its cells from the square outward and the heading it grows on. */
	public static final class Street {
		public final List<RoadFinder.Cell> cells = new ArrayList<>();
		double heading;
		/** It ran into another street: it ends there for good. */
		boolean closed;
		/** The day it last found no way on (it waits TownRules.RETRY_DAYS). */
		long failed = Long.MIN_VALUE / 2;
		/** Cells along it lots have been cut for already, and side streets sent off. */
		int cut;
		int branched;

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putLongArray("Cells", cells.stream().mapToLong(cell -> cell.pos().asLong()).toArray());
			tag.putLongArray("Water", cells.stream().filter(RoadFinder.Cell::water).mapToLong(cell -> cell.pos().asLong()).toArray());
			tag.putDouble("Heading", heading);
			tag.putBoolean("Closed", closed);
			tag.putLong("Failed", failed);
			tag.putInt("Cut", cut);
			tag.putInt("Branched", branched);
			return tag;
		}

		static Street load(CompoundTag tag) {
			Street street = new Street();
			java.util.Set<Long> water = new java.util.HashSet<>();
			for (long cell : Nbt.getLongArray(tag, "Water")) water.add(cell);
			for (long cell : Nbt.getLongArray(tag, "Cells")) street.cells.add(new RoadFinder.Cell(BlockPos.of(cell), water.contains(cell)));
			street.heading = Nbt.getDouble(tag, "Heading");
			street.closed = Nbt.getBoolean(tag, "Closed");
			street.failed = tag.contains("Failed") ? Nbt.getLong(tag, "Failed") : Long.MIN_VALUE / 2;
			street.cut = Nbt.getInt(tag, "Cut");
			street.branched = Nbt.getInt(tag, "Branched");
			return street;
		}
	}

	/** A lot: its ground (the floor's y to the sky), the side its street runs along, who has it. */
	public static final class Lot {
		public final int id;
		public final BoundingBox box;
		/** From the lot toward its street: the way its house faces. */
		public final Direction front;
		/** The street cell it was cut at: the street is laid this far for it. */
		final int street;
		final int at;
		/** A house of the villagers stands or goes up on it. */
		public boolean taken;
		/** A player bought it: theirs to build on, nobody else's. */
		public @Nullable UUID player;
		public String playerName = "";
		/** Its corner posts and sign were set (a player's lot is marked out once). */
		public boolean marked;

		public Lot(int id, BoundingBox box, Direction front, int street, int at) {
			this.id = id;
			this.box = box;
			this.front = front;
			this.street = street;
			this.at = at;
		}

		public boolean free() {
			return !taken && player == null;
		}

		public int floor() {
			return box.minY() + TownRules.LOT_RISE;
		}

		public int price() {
			return box.getXSpan() * box.getZSpan() * TownRules.LOT_PRICE_PER_COLUMN;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putInt("Id", id);
			tag.putIntArray("Box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
			tag.putString("Front", front.getName());
			tag.putInt("Street", street);
			tag.putInt("At", at);
			tag.putBoolean("Taken", taken);
			if (player != null) {
				Nbt.putUUID(tag, "Player", player);
				tag.putString("PlayerName", playerName);
			}
			tag.putBoolean("Marked", marked);
			return tag;
		}

		static @Nullable Lot load(CompoundTag tag) {
			int[] b = Nbt.getIntArray(tag, "Box");
			Direction front = Direction.byName(Nbt.getString(tag, "Front"));
			if (b.length != 6 || front == null) return null;
			Lot lot = new Lot(Nbt.getInt(tag, "Id"), new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), front, Nbt.getInt(tag, "Street"), Nbt.getInt(tag, "At"));
			lot.taken = Nbt.getBoolean(tag, "Taken");
			if (Nbt.hasUUID(tag, "Player")) {
				lot.player = Nbt.getUUID(tag, "Player");
				lot.playerName = Nbt.getString(tag, "PlayerName");
			}
			lot.marked = Nbt.getBoolean(tag, "Marked");
			return lot;
		}
	}

	static ListTag saveStreets(List<Street> streets) {
		ListTag list = new ListTag();
		for (Street street : streets) list.add(street.save());
		return list;
	}

	static ListTag saveLots(List<Lot> lots) {
		ListTag list = new ListTag();
		for (Lot lot : lots) list.add(lot.save());
		return list;
	}

	static void load(Roads.Network network, CompoundTag one) {
		for (Tag entry : Nbt.getList(one, "Streets", Tag.TAG_COMPOUND)) network.streets.add(Street.load((CompoundTag) entry));
		for (Tag entry : Nbt.getList(one, "Lots", Tag.TAG_COMPOUND)) {
			Lot lot = Lot.load((CompoundTag) entry);
			if (lot != null) network.lots.add(lot);
		}
		network.nextLot = Nbt.getInt(one, "NextLot");
	}

	// ---- planning -------------------------------------------------------------------------

	/** Once a day (Roads.plan): what stands on the lots looked at, the streets grown a few steps, new lots cut. */
	static void plan(ServerLevel level, Roads roads, Roads.Network network) {
		Mayors.Office office = Towns.of(level, network.bell);
		BlockPos bell = office == null ? network.bell : office.bell;
		if (!level.isPositionEntityTicking(bell)) return;
		WorldHouses.scan(level, network, bell);
		TownSurvey.survey(level, network, bell);
		settle(level, network);
		if (network.streets.isEmpty()) begin(level, network, bell);
		long day = com.vnap.dialogue.Family.day(level);
		// the town's ground looked at once: the streets grown and the lots cut today are added to it as they come
		Land land = Land.around(level, bell);
		for (Street street : List.copyOf(network.streets)) {
			for (int i = 0; i < TownRules.STREET_STEPS_PER_DAY && !street.closed && day - street.failed >= TownRules.RETRY_DAYS; i++) {
				if (!grow(level, network, bell, street, land)) break;
			}
			branch(level, network, bell, street);
			cut(level, network, bell, street, land);
		}
		roads.setDirty();
	}

	/**
	 * Which lots have a house (a house of the villagers stands with its middle on it), and lots lost to a building
	 * that went up on them meanwhile (a workshop by its job block, the town's own buildings) given up.
	 */
	static void settle(ServerLevel level, Roads.Network network) {
		List<HouseProject> houses = VillageMoodSavedData.get(level).houses();
		Land built = Land.around(level, network.bell).without(claim -> claim.layer() != Land.Layer.BUILDING);
		network.lots.removeIf(lot -> {
			BoundingBox inner = lot.box.inflatedBy(-1);
			lot.taken = houses.stream().anyMatch(house -> lot.box.isInside(house.footprint(level).getCenter()));
			if (lot.taken || lot.player != null) return false;
			return built.intersects(inner, 0);
		});
	}

	/** The streets' first cells: a few blocks out from the bell, evenly round it, turned by the bell's place. */
	private static void begin(ServerLevel level, Roads.Network network, BlockPos bell) {
		for (int i = 0; i < TownRules.STREETS; i++) {
			double heading = Math.PI * 2.0 * i / TownRules.STREETS;
			BlockPos column = Rings.at(bell, heading, 3);
			RoadFinder.Cell first = RoadFinder.surface(level, column.getX(), column.getZ(), network.cells::contains);
			if (first == null || first.water()) continue;
			Street street = new Street();
			street.heading = heading;
			street.cells.add(first);
			network.streets.add(street);
		}
	}

	/**
	 * One step of a street toward a point ahead: the heading tried first, then a little to either side (a street
	 * bends round a hill or a pond); never through a building or a lot, never off the town's land. False when it
	 * found no way today (or reached the town's edge).
	 */
	static boolean grow(ServerLevel level, Roads.Network network, BlockPos bell, Street street, Land town) {
		BlockPos end = street.cells.get(street.cells.size() - 1).pos();
		// at the town's edge a street waits: the town reaches further as it grows
		double reach = Towns.reach(level, bell);
		if (Math.sqrt(horizontal(end, bell)) >= reach - TownRules.LOT / 2.0) return false;
		Land land = town.without(claim -> claim.layer() == Land.Layer.WAY);
		java.util.Set<BlockPos> own = new java.util.HashSet<>();
		for (RoadFinder.Cell cell : street.cells) own.add(cell.pos());
		java.util.Set<BlockPos> others = streetCells(network);
		others.removeAll(own);
		for (double[] bend : new double[][]{{0.0, TownRules.STREET_STEP}, {0.45, TownRules.STREET_STEP}, {-0.45, TownRules.STREET_STEP}, {0.9, TownRules.STREET_STEP}, {-0.9, TownRules.STREET_STEP}, {Math.PI / 2, TownRules.STREET_STEP / 2.0}, {-Math.PI / 2, TownRules.STREET_STEP / 2.0}}) {
			double heading = street.heading + bend[0];
			BlockPos ahead = Rings.at(end, heading, bend[1]);
			// a street that runs into another one joins it and ends there (a crossing, not two streets side by side)
			BlockPos start = street.cells.get(0).pos();
			java.util.function.Predicate<BlockPos> goal = pos -> horizontal(pos, ahead) < 6.25 || (others.contains(pos) && horizontal(pos, start) >= 36);
			Towns.Territory territory = Towns.territory(level, bell);
			java.util.function.Predicate<BlockPos> ours = pos -> territory.contains(pos) && !own.contains(pos)
					&& !pos.closerThan(bell, Math.min(street.cells.size() + 2.0, TownRules.SQUARE_LARGE + 1.0));
			// a street goes out from the square, by short steps: a long way round something is no street
			int longest = (int) (bend[1] * 2 + 4);
			List<RoadFinder.Cell> route = RoadFinder.route(level, end, goal, ours, land, network.cells::contains, ahead, longest);
			if (route.size() < 2 || route.size() > longest
					|| horizontal(route.get(route.size() - 1).pos(), bell) < horizontal(end, bell) - 1.0) continue;
			// the bend was for what stood in the way: the street keeps its heading after it
			street.cells.addAll(route.subList(1, route.size()));
			for (RoadFinder.Cell cell : route.subList(1, route.size())) town.add(Land.Layer.WAY, Land.column(cell.pos(), 2));
			if (others.contains(route.get(route.size() - 1).pos()) && horizontal(route.get(route.size() - 1).pos(), start) >= 36) street.closed = true;
			return !street.closed;
		}
		street.failed = com.vnap.dialogue.Family.day(level);
		return false;
	}

	/**
	 * Side streets: every TownRules.BRANCH_EVERY cells out of the square a street sends one off to either side (a heading at a
	 * right angle to it), unless another street runs close by already; they grow, branch and get lots like any.
	 */
	static void branch(ServerLevel level, Roads.Network network, BlockPos bell, Street street) {
		java.util.Set<BlockPos> all = streetCells(network);
		Towns.Territory territory = Towns.territory(level, bell);
		for (int i = Math.max(street.branched, TownRules.BRANCH_EVERY); i < street.cells.size() - 4; i += TownRules.BRANCH_EVERY) {
			RoadFinder.Cell at = street.cells.get(i);
			street.branched = i + 1;
			if (at.water()) continue;
			for (double side : new double[]{Math.PI / 2, -Math.PI / 2}) {
				double heading = street.heading + side;
				BlockPos probe = Rings.at(at.pos(), heading, TownRules.STREETS_APART);
				boolean crowded = all.stream().anyMatch(cell -> cell.closerThan(probe, TownRules.STREETS_APART - 4));
				if (crowded || !territory.contains(probe)) continue;
				Street off = new Street();
				off.heading = heading;
				off.cells.add(at);
				network.streets.add(off);
			}
		}
	}

	/** Lots along the street's new length: every TownRules.LOT cells, on both sides, where the ground suits. */
	static void cut(ServerLevel level, Roads.Network network, BlockPos bell, Street street, Land land) {
		int index = network.streets.indexOf(street);
		Towns.Territory territory = Towns.territory(level, bell);
		// the last cells wait: the street may still bend there
		int last = street.closed ? street.cells.size() - 3 : street.cells.size() - TownRules.LOT / 2;
		for (int i = Math.max(street.cut, TownRules.LOT / 2 + 2); i < last; i += 3) {
			RoadFinder.Cell cell = street.cells.get(i);
			if (cell.water() || cell.pos().closerThan(bell, TownRules.SQUARE_LARGE + 3)) continue;
			BlockPos before = street.cells.get(Math.max(0, i - 2)).pos();
			BlockPos after = street.cells.get(Math.min(street.cells.size() - 1, i + 2)).pos();
			Direction along = Direction.getNearest(after.getX() - before.getX(), 0, after.getZ() - before.getZ());
			if (along.getAxis().isVertical()) continue;
			int placed = 0;
			for (Direction side : new Direction[]{along.getClockWise(), along.getCounterClockWise()}) {
				for (int size : new int[]{TownRules.LOT, TownRules.SMALL_LOT}) {
					Lot lot = lot(level, network, bell, cell.pos(), along, side, index, i, size, land, territory);
					if (lot == null) continue;
					network.lots.add(lot);
					land.add(Land.Layer.LOT, lot.box);
					placed = placed == 0 ? size : Math.min(placed, size);
					break;
				}
			}
			street.cut = i + 1;
			if (placed > 0) i += placed - 3;
		}
	}

	/** A lot beside the street cell on {@code side}, or null where the ground does not suit or something is there. */
	private static @Nullable Lot lot(ServerLevel level, Roads.Network network, BlockPos bell, BlockPos at, Direction along, Direction side,
									 int street, int index, int size, Land land, Towns.Territory territory) {
		// a block of verge between the street and the lot (a door's step), then the lot itself
		BlockPos near = at.relative(side, 2).relative(along, -(size / 2));
		BlockPos far = at.relative(side, 1 + size).relative(along, size / 2);
		int y = at.getY();
		BoundingBox box = new BoundingBox(Math.min(near.getX(), far.getX()), y - TownRules.LOT_RISE, Math.min(near.getZ(), far.getZ()),
				Math.max(near.getX(), far.getX()), y + 24, Math.max(near.getZ(), far.getZ()));
		for (BlockPos corner : corners(box)) {
			if (!level.isPositionEntityTicking(corner) || !territory.contains(corner)) {
				REFUSED[0]++;
				return null;
			}
		}
		// the square grows to TownRules.SQUARE_LARGE: no lot on its ground
		BoundingBox square = new BoundingBox(bell.getX() - TownRules.SQUARE_LARGE - 1, box.minY(), bell.getZ() - TownRules.SQUARE_LARGE - 1,
				bell.getX() + TownRules.SQUARE_LARGE + 1, box.maxY(), bell.getZ() + TownRules.SQUARE_LARGE + 1);
		if (square.intersects(box)) {
			REFUSED[1]++;
			return null;
		}
		Land.Claim hit = land.hit(box, 1);
		if (hit != null) {
			if (HITS.size() < 6) HITS.add(hit.layer() + " " + hit.box().getCenter().toShortString() + " span " + hit.box().getXSpan() + "x" + hit.box().getZSpan());
			REFUSED[2]++;
			return null;
		}
		// the lot's own floor: on a slope a lot is a terrace a few blocks over or under its street (its door's way climbs)
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		java.util.List<Integer> grounds = new ArrayList<>();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				Integer ground = Ground.natural(level, x, z, y);
				if (ground == null) {
					REFUSED[3]++;
					return null;
				}
				grounds.add(ground);
				low = Math.min(low, ground);
				high = Math.max(high, ground);
			}
		}
		int floor = floor(grounds, low, high);
		if (floor == Integer.MIN_VALUE || Math.abs(floor - y) > TownRules.LOT_TERRACE) {
			REFUSED[3]++;
			return null;
		}
		BoundingBox kept = new BoundingBox(box.minX(), floor - TownRules.LOT_RISE, box.minZ(), box.maxX(), floor + 24, box.maxZ());
		return new Lot(network.nextLot++, kept, side.getOpposite(), street, index);
	}

	/**
	 * The lot's floor: on the lowest ground, or a block over it with the pits a layer deep filled (when that saves digging
	 * and the pits are few); never where the ground rises more than LOT_RISE over it (a hill is no lot). MIN_VALUE: none.
	 */
	static int floor(java.util.List<Integer> grounds, int low, int high) {
		if (high <= low + TownRules.LOT_DIP + TownRules.LOT_RISE) return low + TownRules.LOT_DIP;
		int raised = low + TownRules.LOT_DIP + TownRules.LOT_FILL;
		if (high > raised + TownRules.LOT_RISE) return Integer.MIN_VALUE;
		long pits = grounds.stream().filter(ground -> ground < raised - TownRules.LOT_DIP).count();
		return pits * 3 <= grounds.size() ? raised : Integer.MIN_VALUE;
	}

	private static BlockPos[] corners(BoundingBox box) {
		int y = box.minY() + TownRules.LOT_RISE;
		return new BlockPos[]{new BlockPos(box.minX(), y, box.minZ()), new BlockPos(box.maxX(), y, box.maxZ()),
				new BlockPos(box.minX(), y, box.maxZ()), new BlockPos(box.maxX(), y, box.minZ())};
	}

	static java.util.Set<BlockPos> streetCells(Roads.Network network) {
		java.util.Set<BlockPos> out = new java.util.HashSet<>();
		for (Street street : network.streets) for (RoadFinder.Cell cell : street.cells) out.add(cell.pos());
		return out;
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	// ---- houses on lots ------------------------------------------------------------------

	/**
	 * A house for this villager on the nearest free lot its plan fits (turned to face the street), with what stands
	 * there to be cleared first; the lot is taken and its street laid up to it. Null when no lot has room for any of
	 * the plans (the caller looks round the bell as before).
	 */
	public static @Nullable HouseProject house(ServerLevel level, Villager villager, BlockPos bell, List<String> plans, long day) {
		Roads roads = Roads.get(level.getServer());
		Roads.Network network = roads.near(level.dimension(), bell);
		if (network == null) return null;
		List<Lot> free = new ArrayList<>();
		for (Lot lot : network.lots) if (lot.free() && level.isPositionEntityTicking(lot.box.getCenter().atY(lot.floor()))) free.add(lot);
		free.sort(Comparator.comparingDouble(lot -> lot.box.getCenter().distSqr(bell)));
		Land land = Land.around(level, bell);
		int[] why = new int[3];
		for (Lot lot : free.subList(0, Math.min(6, free.size()))) {
			Land others = land.without(claim -> claim.box().equals(lot.box));
			for (String plan : plans) {
				String palette = HousePalette.forBiome(level.getBiome(lot.box.getCenter()));
				SchematicSpec spec = Workshops.schematic(level, HousePalette.schematicName(plan, palette));
				if (spec.blocks.isEmpty()) continue;
				Rotation rotation = TownPlots.facing(lot.front);
				BlockPos origin = place(spec, rotation, lot);
				BoundingBox box = HouseProject.footprint(spec, origin, rotation);
				if (!inside(lot.box, box)) {
					why[0]++;
					continue;
				}
				Land.Claim hit = others.hit(box, 1);
				if (hit != null) {
					why[1]++;
					lastHouse = "lot #" + lot.id + " " + plan + " hits " + hit.layer() + " " + hit.box();
					continue;
				}
				List<BlockPos> clearing = clearing(level, spec, origin, rotation, box);
				if (clearing == null) {
					why[2]++;
					continue;
				}
				HouseProject house = new HouseProject(bell.asLong(), bell, villager.getUUID(), plan, palette, origin, rotation, day);
				house.clearing.addAll(clearing);
				house.clearFloor = origin.getY();
				house.junk = true;
				lot.taken = true;
				groundwork(level, roads, network, box, origin.getY(), palette);
				layTo(level, roads, network, lot);
				roads.setDirty();
				return house;
			}
		}
		lastHouse = free.size() + " free lots: too small " + why[0] + ", in the way " + why[1] + ", cannot clear " + why[2] + "; " + lastHouse;
		return null;
	}

	/** Why the last house found no lot (debug, /vnap roads lots). */
	static String lastHouse = "";

	/** The deepest hole under a house's floor that is filled with earth. */
	static final int PIT_DEPTH = 4;

	/**
	 * The holes under the house filled with earth before it goes up, by the town's builders: a house never stands over
	 * a hole. A lot is cut where its dips are a layer deep (floor); deeper holes are what was dug since.
	 */
	static void groundwork(ServerLevel level, Roads roads, Roads.Network network, BoundingBox box, int floor, String palette) {
		List<SchematicSpec.PlacedBlock> fill = new ArrayList<>();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				// the lot's own dip is a layer, but its ground may have been dug since it was cut (a player, a creeper):
				// any hole up to PIT_DEPTH is filled from the bottom, so no house stands over air
				Integer ground = Ground.natural(level, x, z, floor);
				if (ground == null || ground >= floor - 1 || ground < floor - 1 - PIT_DEPTH) continue;
				for (int y = ground + 1; y <= floor - 1; y++) {
					BlockPos pit = new BlockPos(x, y, z);
					if (level.getBlockState(pit).canBeReplaced()) fill.add(new SchematicSpec.PlacedBlock(pit, Blocks.DIRT.defaultBlockState()));
				}
			}
		}
		if (!fill.isEmpty()) roads.queue(level, network, fill, "groundwork");
	}

	/** The plan's origin on the lot: its front on the lot's street side, in the middle of the lot's width, on its floor. */
	static BlockPos place(SchematicSpec spec, Rotation rotation, Lot lot) {
		BoundingBox rel = HouseProject.footprint(spec, BlockPos.ZERO, rotation);
		int width = rel.getXSpan();
		int depth = rel.getZSpan();
		int minX = (lot.box.minX() + lot.box.maxX() + 1 - width) / 2;
		int minZ = (lot.box.minZ() + lot.box.maxZ() + 1 - depth) / 2;
		switch (lot.front) {
			case NORTH -> minZ = lot.box.minZ();
			case SOUTH -> minZ = lot.box.maxZ() - depth + 1;
			case WEST -> minX = lot.box.minX();
			case EAST -> minX = lot.box.maxX() - width + 1;
			default -> {
			}
		}
		return new BlockPos(minX - rel.minX(), lot.floor(), minZ - rel.minZ());
	}

	private static boolean inside(BoundingBox outer, BoundingBox inner) {
		return inner.minX() >= outer.minX() && inner.maxX() <= outer.maxX() && inner.minZ() >= outer.minZ() && inner.maxZ() <= outer.maxZ();
	}

	/**
	 * What has to come down before the house goes up: everything in its box above its floor that is not the plan's
	 * own block there (trees, the hill, a player's things), and built blocks on the floor itself. Null where it cannot
	 * go (water in it, a bed or a job block someone may hold, unloaded ground, far too much to dig).
	 */
	static @Nullable List<BlockPos> clearing(ServerLevel level, SchematicSpec spec, BlockPos origin, Rotation rotation, BoundingBox box) {
		java.util.Map<BlockPos, BlockState> plan = new java.util.HashMap<>();
		for (SchematicSpec.PlacedBlock block : spec.blocks) plan.put(BuildJob.at(origin, rotation, block.rel()), block.state());
		List<BlockPos> out = new ArrayList<>();
		for (int y = box.maxY(); y >= origin.getY(); y--) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.isLoaded(pos)) return null;
					BlockState there = level.getBlockState(pos);
					if (there.isAir() || (there.canBeReplaced() && there.getFluidState().isEmpty())) continue;
					if (!there.getFluidState().isEmpty() || there.getDestroySpeed(level, pos) < 0) return null;
					if (level.getPoiManager().getType(pos).isPresent()) return null;
					BlockState wanted = plan.get(pos);
					if (wanted != null && there.is(wanted.getBlock())) continue;
					if (y == origin.getY() && BuildJob.naturalGround(there)) continue;
					out.add(pos);
					if (out.size() > TownRules.LOT_MAX_CLEARING) return null;
				}
			}
		}
		return out;
	}

	/** The lot's street from the square to it is laid (what of it is not road yet), lamps and all. */
	static void layTo(ServerLevel level, Roads roads, Roads.Network network, Lot lot) {
		if (lot.street < 0 || lot.street >= network.streets.size()) return;
		Street street = network.streets.get(lot.street);
		int upTo = Math.min(street.cells.size(), lot.at + lot.box.getXSpan() / 2 + 1);
		List<RoadFinder.Cell> way = new ArrayList<>(street.cells.subList(0, upTo));
		boolean laid = true;
		for (RoadFinder.Cell cell : way) laid &= network.cells.contains(cell.water() ? cell.pos().above() : cell.pos());
		if (!laid && way.size() >= 2) roads.lay(level, network, way, "street");
		// a side street: its street is laid up to where it leaves it
		layTo(level, roads, network, street.cells.get(0).pos());
	}

	/** The street through this cell is laid from the square up to it (a road from a door met it there). */
	static void layTo(ServerLevel level, Roads roads, Roads.Network network, BlockPos cell) {
		if (network.cells.contains(cell)) return;
		for (Street street : network.streets) {
			for (int i = 0; i < street.cells.size(); i++) {
				// a side street begins on its street: that one is the way there
				if (i == 0 || !street.cells.get(i).pos().equals(cell)) continue;
				List<RoadFinder.Cell> way = new ArrayList<>(street.cells.subList(0, i + 1));
				roads.lay(level, network, way, "street");
				layTo(level, roads, network, street.cells.get(0).pos());
				return;
			}
		}
	}

	/** The lot this column is on, or null. */
	public static @Nullable Lot at(Roads.Network network, BlockPos pos) {
		for (Lot lot : network.lots) {
			if (pos.getX() >= lot.box.minX() && pos.getX() <= lot.box.maxX() && pos.getZ() >= lot.box.minZ() && pos.getZ() <= lot.box.maxZ()) return lot;
		}
		return null;
	}

	public static @Nullable Lot byId(Roads.Network network, int id) {
		for (Lot lot : network.lots) if (lot.id == id) return lot;
		return null;
	}

	/** A player's lot bought while its ground was not loaded: marked out as soon as it is. */
	static void mark(ServerLevel level, Roads.Network network) {
		for (Lot lot : network.lots) {
			if (lot.player != null && !lot.marked && level.isPositionEntityTicking(lot.box.getCenter().atY(lot.floor()))) markOut(level, lot);
		}
	}

	/**
	 * A player's lot fenced at once, so it is plain where it is and how big: a fence of the palette's wood round its
	 * edge, lanterns on the corner posts, a way in three wide in the middle of its street side, and by it the
	 * nameplate with the owner's name (the lot's price pays for it). A column without natural ground, or with
	 * something standing on it, is left open.
	 */
	public static void markOut(ServerLevel level, Lot lot) {
		BoundingBox box = lot.box;
		String palette = HousePalette.forBiome(level.getBiome(box.getCenter().atY(lot.floor())));
		BlockState fence = Roads.palette(Blocks.OAK_FENCE.defaultBlockState(), palette);
		Direction along = lot.front.getClockWise();
		// the street side's middle column
		BlockPos centre = box.getCenter();
		BlockPos gate = switch (lot.front) {
			case NORTH -> new BlockPos(centre.getX(), 0, box.minZ());
			case SOUTH -> new BlockPos(centre.getX(), 0, box.maxZ());
			case WEST -> new BlockPos(box.minX(), 0, centre.getZ());
			default -> new BlockPos(box.maxX(), 0, centre.getZ());
		};
		BlockPos signAt = gate.relative(along, -(GATE_HALF + 1));
		List<BlockPos> lanterns = new ArrayList<>();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				boolean edgeX = x == box.minX() || x == box.maxX();
				boolean edgeZ = z == box.minZ() || z == box.maxZ();
				if (!edgeX && !edgeZ) continue;
				BlockPos column = new BlockPos(x, 0, z);
				int off = (x - gate.getX()) * along.getStepX() + (z - gate.getZ()) * along.getStepZ();
				boolean front = (x - gate.getX()) * lot.front.getStepX() + (z - gate.getZ()) * lot.front.getStepZ() == 0;
				if (front && Math.abs(off) <= GATE_HALF) continue;
				Integer ground = Ground.natural(level, x, z, lot.floor());
				if (ground == null) continue;
				BlockPos pos = new BlockPos(x, ground + 1, z);
				BlockState there = level.getBlockState(pos);
				if (!there.isAir() && !(there.canBeReplaced() && there.getFluidState().isEmpty())) continue;
				if (column.equals(signAt)) {
					level.setBlock(pos, com.vnap.block.LifeBlocks.HOUSE_SIGN.facing(lot.front, true), 3);
					if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
						com.vnap.Mc.writeSign(sign, false, net.minecraft.network.chat.Component.literal(lot.playerName), net.minecraft.network.chat.Component.empty(),
								net.minecraft.network.chat.Component.translatable("sign.villager_life.lot", lot.id, box.getXSpan(), box.getZSpan()));
					}
					continue;
				}
				level.setBlock(pos, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(fence, level, pos), 3);
				if (edgeX && edgeZ) lanterns.add(pos.above());
			}
		}
		for (BlockPos lantern : lanterns) {
			if (level.getBlockState(lantern).canBeReplaced()) level.setBlock(lantern, Blocks.LANTERN.defaultBlockState(), 3);
		}
		lot.marked = true;
	}

	/** The way in: this many columns either side of the street side's middle stay open. */
	static final int GATE_HALF = 1;

	/** A player buys the lot: the treasury gets its price, the lot is theirs (marked out by hand), its street is laid. */
	public static boolean buy(ServerLevel level, Roads.Network network, Lot lot, net.minecraft.world.entity.player.Player player) {
		if (!lot.free()) return false;
		Mayors.Office office = Towns.of(level, network.bell);
		if (office == null || !com.vnap.village.market.Coins.pay(player, lot.price())) return false;
		com.vnap.village.market.Market.deposit(level, office, lot.price());
		lot.player = player.getUUID();
		lot.playerName = com.vnap.Mc.name(player);
		Roads roads = Roads.get(level.getServer());
		layTo(level, roads, network, lot);
		com.vnap.village.home.Neighbours.get(level.getServer()).add(level, network.bell, player.getUUID());
		// marked out at once (the player stands by the board, the lot is near): seen right away where it is and how big
		if (level.isPositionEntityTicking(lot.box.getCenter().atY(lot.floor()))) markOut(level, lot);
		roads.setDirty();
		com.vnap.progress.VillageAdvancements.award(player, "town/lot");
		return true;
	}

	/** Whether this player owns the lot under {@code pos}. */
	public static boolean owns(ServerLevel level, BlockPos pos, UUID player) {
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), pos);
		Lot lot = network == null ? null : at(network, pos);
		return lot != null && player.equals(lot.player);
	}

	/** Debug (/vnap roads lots reset): the plan's streets not laid yet and its free lots thrown away, laid out again. */
	public static void replan(ServerLevel level, Roads roads, Roads.Network network) {
		network.lots.removeIf(Lot::free);
		network.streets.clear();
		for (int i = 0; i < 8; i++) planNow(level, roads, network);
	}

	/** Debug (/vnap roads lots): the plan grown now as on a day. */
	public static void planNow(ServerLevel level, Roads roads, Roads.Network network) {
		// every street's whole length looked at again (lots already cut stay: a new one never overlaps them)
		for (Street street : network.streets) {
			street.cut = 0;
			street.failed = Long.MIN_VALUE / 2;
		}
		java.util.Arrays.fill(REFUSED, 0);
		HITS.clear();
		plan(level, roads, network);
		mark(level, network);
	}

	/** Debug (/vnap roads lots): streets and lots. */
	public static String describe(ServerLevel level, Roads.Network network) {
		int free = 0, taken = 0, players = 0, open = 0, cells = 0;
		for (Lot lot : network.lots) {
			if (lot.player != null) players++;
			else if (lot.taken) taken++;
			else free++;
		}
		for (Street street : network.streets) {
			if (!street.closed) open++;
			cells += street.cells.size();
		}
		return "world houses " + network.worldHouses.size() + ", lost " + network.lost.size() + " stacks, board " + NoticeBoard.board(level, network) + "; last house: " + lastHouse + "; streets " + network.streets.size() + " (" + open + " growing, " + cells + " cells), lots " + network.lots.size()
				+ ": free " + free + ", houses " + taken + ", players' " + players + "; refused: not ours " + REFUSED[0] + ", square " + REFUSED[1]
				+ ", built " + REFUSED[2] + " " + HITS + ", ground " + REFUSED[3] + "; " + network.streets.stream().map(st -> (int) Math.toDegrees(st.heading) + "° " + st.cells.size() + (st.closed ? " joined" : "")
				+ " to " + st.cells.get(st.cells.size() - 1).pos().toShortString()).toList()
				+ "; lots " + network.lots.stream().map(lot -> "#" + lot.id + (lot.player != null ? " player" : lot.taken ? " house" : " free") + " "
				+ lot.box.minX() + "," + lot.floor() + "," + lot.box.minZ() + ".." + lot.box.maxX() + "," + lot.box.maxZ()).toList();
	}
}
