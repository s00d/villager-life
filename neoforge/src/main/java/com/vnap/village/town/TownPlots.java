package com.vnap.village.town;

import com.vnap.Nbt;
import com.vnap.mood.MoodConfig;
import com.vnap.village.build.BuildJob;
import com.vnap.village.build.BuildStages;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The town's own buildings (structures village/town/*), one of each a town: the chapel with its clock (Chapel), the
 * bakehouse (Bakehouse), the tavern (Tavern). Each keeps its ground from an early stage of the town (before houses
 * fill its middle) and goes up later, near the bell and facing it, built like the town's roads: the treasury's
 * money, the stall's materials, the town's day labourers (Roads.queue, RoadBehavior), the hillside dug away first and
 * a stone footing under any dip.
 */
public final class TownPlots {
	/**
	 * The town's buildings, in the order a growing town gets them; the people a town needs to keep and to build. All of
	 * them keep their ground from the town's first day, before any house is planned: kept later, a town grown full
	 * behind its wall had no room left for them.
	 */
	public enum Kind {
		CHAPEL("town/chapel", 0, 1, 1, 2, 0),
		BAKEHOUSE("town/bakehouse", 0, 1, 1, 3, 0),
		TAVERN("town/tavern", 0, 1, 2, 3, 0),
		/** The mayor builds it once the treasury can spare it: food sold cheaper to the town's families (Market.shop). */
		GRANARY("town/granary", 0, 1, 1, 3, 2 * 64),
		/** Lessons there instead of by the librarian, and children grow up sooner (Family.school). */
		SCHOOL("town/school", 0, 1, 1, 2, 3 * 64),
		/** A half-full town with four silver to spare: the shop takes half again from its visitors a day (Fair.budget). */
		MARKET("town/market", 0, 1, 1, 2, 4 * 64);

		public final String plan;
		/** The share of the limit a town needs to keep the building's ground, and to build it. */
		private final int keepOf;
		private final int keepIn;
		private final int buildOf;
		private final int buildIn;
		/** Copper the treasury must hold before the mayor spends on it (0: the town needs it, money or not). */
		private final int purse;

		Kind(String plan, int keepOf, int keepIn, int buildOf, int buildIn, int purse) {
			this.purse = purse;
			this.plan = plan;
			this.keepOf = keepOf;
			this.keepIn = keepIn;
			this.buildOf = buildOf;
			this.buildIn = buildIn;
		}

		boolean keep(int people, int max) {
			return people * keepIn >= max * keepOf;
		}

		boolean build(int people, int max) {
			return people * buildIn >= max * buildOf;
		}
	}

	/** Where a building's plan stands, turned how, in which palette; queued once it is to be built, with its stretch. */
	public static final class Plot {
		public final BlockPos origin;
		public final Rotation rotation;
		public String palette;
		public boolean queued;
		int segment = -1;

		public Plot(BlockPos origin, Rotation rotation, String palette) {
			this.origin = origin.immutable();
			this.rotation = rotation;
			this.palette = palette;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("Origin", origin.asLong());
			tag.putString("Rotation", rotation.name());
			tag.putString("Palette", palette);
			tag.putBoolean("Queued", queued);
			tag.putInt("Segment", segment);
			return tag;
		}

		static Plot load(CompoundTag tag) {
			Plot plot = new Plot(BlockPos.of(Nbt.getLong(tag, "Origin")), Rotation.valueOf(Nbt.getString(tag, "Rotation")), Nbt.getString(tag, "Palette"));
			plot.queued = Nbt.getBoolean(tag, "Queued");
			plot.segment = tag.contains("Segment") ? Nbt.getInt(tag, "Segment") : -1;
			return plot;
		}
	}

	private static final int MIN_RING = 10;
	/** How far past the town's edge a building with no room inside may stand. */
	static final int OUTSKIRTS = 28;
	/** The deepest dip under a floor filled with stone. */
	static final int FOUNDATION = 2;
	/** Why the last layout found no room: spots tried per reason (debug, /vnap roads chapel|bakehouse|tavern). */
	public static String lastRefusal = "";

	private TownPlots() {
	}

	public static @Nullable Plot plot(Roads.Network network, Kind kind) {
		return network.plots.get(kind);
	}

	/** Whether the town of this bell has the building up. */
	public static boolean has(ServerLevel level, BlockPos bell, Kind kind) {
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), bell);
		return network != null && built(network, kind);
	}

	/** Built: queued and its stretch laid (the stretch is gone from the town's work). */
	public static boolean built(Roads.Network network, Kind kind) {
		Plot plot = plot(network, kind);
		if (plot == null || !plot.queued) return false;
		for (Roads.Segment segment : network.segments) if (segment.id == plot.segment) return false;
		return true;
	}

	/** Every day: each building's ground kept and its building queued when the town has grown to it. */
	static void plan(ServerLevel level, Roads roads, Roads.Network network) {
		long day = com.vnap.dialogue.Family.day(level);
		int people = Towns.population(level, network.bell);
		int max = Towns.limit(level, network.bell);
		// one look at the town's ground for every building today, looked at again only when a plot was kept or given up
		Land land = null;
		for (Kind kind : Kind.values()) {
			Plot plot = plot(network, kind);
			if (plot == null) {
				if (!kind.keep(people, max) || day - network.plotTried.getOrDefault(kind, Long.MIN_VALUE / 2) < TownRules.RETRY_DAYS) continue;
				if (land == null) land = Land.around(level, network.bell);
				if (!reserve(level, network, kind, land)) {
					network.plotTried.put(kind, day);
					continue;
				}
				land = null;
				roads.setDirty();
				plot = plot(network, kind);
			}
			if (plot != null && !plot.queued && kind.build(people, max) && afford(level, network, kind)) {
				if (land == null) land = Land.around(level, network.bell);
				if (!build(level, roads, network, kind, land)) land = null;
			}
		}
	}

	private static boolean afford(ServerLevel level, Roads.Network network, Kind kind) {
		if (kind.purse <= 0) return true;
		Mayors.Office office = Towns.of(level, network.bell);
		return office != null && office.mayor != null && office.treasury >= kind.purse;
	}

	/** Lays the building out and queues it now (debug, whatever the town's size). */
	public static boolean lay(ServerLevel level, Roads roads, Roads.Network network, Kind kind) {
		Plot plot = plot(network, kind);
		if (plot != null && plot.queued) return false;
		if (plot == null && !reserve(level, network, kind, Land.around(level, network.bell))) return false;
		return build(level, roads, network, kind, Land.around(level, network.bell));
	}

	/**
	 * Queues the building on its kept ground: the hillside dug, the dips filled, the building itself. The ground is
	 * looked at again first (a player may have built on it); taken, a new place is looked for later.
	 */
	static boolean build(ServerLevel level, Roads roads, Roads.Network network, Kind kind, Land land) {
		Plot plot = plot(network, kind);
		if (plot == null || !level.isPositionEntityTicking(plot.origin)) return false;
		SchematicSpec spec = HousePalette.apply(Workshops.schematic(level, kind.plan), plot.palette);
		BoundingBox box = HouseProject.footprint(spec, plot.origin, plot.rotation);
		Land taken = land.without(claim -> claim.box().equals(box) || (claim.layer() == Land.Layer.LOT && free(network, claim.box())));
		if (fits(level, network, spec, plot.origin, plot.rotation, box, taken) >= 0) {
			network.plots.remove(kind);
			roads.setDirty();
			return false;
		}
		List<SchematicSpec.PlacedBlock> work = new ArrayList<>(dig(level, plot.origin, box));
		work.addAll(foundations(level, spec, plot.origin, plot.rotation, Roads.palette(Blocks.COBBLESTONE.defaultBlockState(), plot.palette)));
		work.addAll(blocks(level, spec, plot.origin, plot.rotation));
		plot.segment = roads.queue(level, network, work, kind.name().toLowerCase(java.util.Locale.ROOT)).id;
		plot.queued = true;
		roads.setDirty();
		return true;
	}

	/** Finds the building's ground near the bell and keeps it (no house, road or wall goes there); false when there is none. */
	static boolean reserve(ServerLevel level, Roads.Network network, Kind kind, Land land) {
		if (plot(network, kind) != null) return true;
		Mayors.Office office = Towns.of(level, network.bell);
		BlockPos bell = office == null ? network.bell : office.bell;
		if (!level.isPositionEntityTicking(bell)) return false;
		String palette = HousePalette.forBiome(level.getBiome(bell));
		SchematicSpec spec = HousePalette.apply(Workshops.schematic(level, kind.plan), palette);
		if (spec.blocks.isEmpty()) return false;
		// the plan's free lots give way to the town's own building (it takes one or two of them)
		Land taken = land.without(claim -> claim.layer() == Land.Layer.LOT && free(network, claim.box()))
				// the square's ground (it grows to TownRules.SQUARE_LARGE): a building kept early must not take its well's place
				.with(Land.Layer.BUILDING, List.of(new BoundingBox(bell.getX() - TownRules.SQUARE_LARGE - 1, bell.getY() - 8, bell.getZ() - TownRules.SQUARE_LARGE - 1,
						bell.getX() + TownRules.SQUARE_LARGE + 1, bell.getY() + 24, bell.getZ() + TownRules.SQUARE_LARGE + 1)));
		int reach = (int) Towns.reach(level, bell);
		int[] why = new int[5];
		if (reserve(level, network, kind, spec, palette, bell, taken, Rings.around(bell, MIN_RING, reach - 8, 2, 24), 0, why)) return true;
		// no room inside (an old town, a crowded hill): its outskirts, just past its edge (and its wall), the town's land grows to take them in
		int outer = reach + OUTSKIRTS;
		if (office != null && reserve(level, network, kind, spec, palette, bell, taken, Rings.around(bell, reach - 8, outer - 6, 2, 32), outer, why)) {
			TownPlots.Plot plot = network.plots.get(kind);
			if (plot != null) {
				BoundingBox box = HouseProject.footprint(spec, plot.origin, plot.rotation);
				int far = 0;
				for (int x : new int[]{box.minX(), box.maxX()}) for (int z : new int[]{box.minZ(), box.maxZ()}) far = Math.max(far, (int) Math.ceil(Math.hypot(x - bell.getX(), z - bell.getZ())));
				office.outskirts = Math.max(office.outskirts, far + 4);
				Mayors.get(level.getServer()).setDirty();
			}
			return true;
		}
		lastRefusal = kind.name().toLowerCase(java.util.Locale.ROOT) + ": unloaded " + why[0] + ", no level ground " + why[1] + ", built over " + why[2]
				+ ", not the town's " + why[3] + ", terrain " + why[4];
		return false;
	}

	private static boolean reserve(ServerLevel level, Roads.Network network, Kind kind, SchematicSpec spec, String palette, BlockPos bell, Land taken,
								   Iterable<BlockPos> spots, int outer, int[] why) {
		for (BlockPos at : spots) {
			int x = at.getX();
			int z = at.getZ();
			if (!level.isPositionEntityTicking(at)) {
				why[0]++;
				continue;
			}
			BlockPos centre = Ground.near(level, x, z, bell.getY());
			if (centre == null || Math.abs(centre.getY() - (bell.getY() - 1)) > 6) {
				why[1]++;
				continue;
			}
			Rotation front = facing(Direction.getNearest(bell.getX() - x, 0, bell.getZ() - z));
			// facing the bell first, then its sides; never its back to the bell
			for (Rotation rotation : new Rotation[]{front, front.getRotated(Rotation.CLOCKWISE_90), front.getRotated(Rotation.COUNTERCLOCKWISE_90)}) {
				BlockPos origin = centre.subtract(new BlockPos(spec.sizeX / 2, 0, spec.sizeZ / 2).rotate(rotation));
				BoundingBox box = HouseProject.footprint(spec, origin, rotation);
				int refused = fits(level, network, spec, origin, rotation, box, taken, outer);
				if (refused >= 0) {
					why[refused]++;
					continue;
				}
				network.plots.put(kind, new Plot(origin, rotation, palette));
				network.lots.removeIf(lot -> lot.free() && lot.box.intersects(box.inflatedBy(1)));
				return true;
			}
		}
		return false;
	}

	private static int fits(ServerLevel level, Roads.Network network, SchematicSpec spec, BlockPos origin, Rotation rotation,
							BoundingBox box, Land taken) {
		return fits(level, network, spec, origin, rotation, box, taken, 0);
	}

	/**
	 * -1 when it fits, else the reason it does not (2 built over, 3 not the town's land, 4 the ground). With {@code outer},
	 * land past the town's edge counts as its own within that far of the bell and nearer it than any other town's.
	 */
	private static int fits(ServerLevel level, Roads.Network network, SchematicSpec spec, BlockPos origin, Rotation rotation,
							BoundingBox box, Land taken, int outer) {
		if (taken.intersects(box, 1) || mainStreets(network.bell, box)) return 2;
		for (BlockPos corner : new BlockPos[]{new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.minY(), box.maxZ()),
				new BlockPos(box.minX(), box.minY(), box.maxZ()), new BlockPos(box.maxX(), box.minY(), box.minZ())}) {
			if (!level.isPositionEntityTicking(corner)) return 3;
			if (!Towns.ours(level, network.bell, corner) && (outer <= 0 || !outskirt(level, network.bell, corner, outer))) return 3;
		}
		// the hillside and the trees in the way are dug away (dig), a dip under the floor is filled (foundation)
		for (SchematicSpec.PlacedBlock block : spec.blocks) {
			BlockPos pos = BuildJob.at(origin, rotation, block.rel());
			if (!level.isLoaded(pos)) return 4;
			BlockState there = level.getBlockState(pos);
			if (!there.getFluidState().isEmpty() || !(there.isAir() || there.canBeReplaced() || BuildJob.carvable(there))) return 4;
			if (block.rel().getY() == 0 && foundation(level, pos) < 0) return 4;
		}
		return -1;
	}

	/**
	 * The box lies across the way of a main street (TownPlan: out of the square to each side): ground kept for a town
	 * building on its first day stood in the streets' way, and the east one never got out of the square.
	 */
	public static boolean mainStreets(BlockPos bell, BoundingBox box) {
		BoundingBox wide = box.inflatedBy(TownRules.MAIN_STREET_CLEAR);
		for (int i = 0; i < TownRules.STREETS; i++) {
			double heading = Math.PI * 2.0 * i / TownRules.STREETS;
			for (int t = 3; t <= TownRules.MAX_REACH; t++) {
				int x = bell.getX() + (int) Math.round(Math.cos(heading) * t);
				int z = bell.getZ() + (int) Math.round(Math.sin(heading) * t);
				if (x >= wide.minX() && x <= wide.maxX() && z >= wide.minZ() && z <= wide.maxZ()) return true;
			}
		}
		return false;
	}

	private static boolean outskirt(ServerLevel level, BlockPos bell, BlockPos pos, int outer) {
		double mine = Math.hypot(pos.getX() - bell.getX(), pos.getZ() - bell.getZ());
		if (mine > outer) return false;
		for (Mayors.Office other : Mayors.get(level.getServer()).offices()) {
			if (!other.dimension.equals(level.dimension()) || other.bell.equals(bell)) continue;
			// the same line as the town's reach: no nearer than halfway to a neighbour
			if (mine > Math.sqrt(other.bell.distSqr(bell)) / 2.0 - 2.0) return false;
		}
		return true;
	}

	/** Blocks of fill under a floor cell down to firm ground (0: it stands on it), or -1 over water or a drop. */
	static int foundation(ServerLevel level, BlockPos floor) {
		if (BuildJob.naturalGround(level.getBlockState(floor))) return 0;
		for (int d = 1; d <= FOUNDATION; d++) {
			BlockPos below = floor.below(d);
			if (!level.getFluidState(below).isEmpty()) return -1;
			if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return d - 1;
		}
		return -1;
	}

	/** Stone under the floor where the ground dips, bottom up. */
	static List<SchematicSpec.PlacedBlock> foundations(ServerLevel level, SchematicSpec spec, BlockPos origin, Rotation rotation, BlockState stone) {
		List<SchematicSpec.PlacedBlock> out = new ArrayList<>();
		for (int d = FOUNDATION; d >= 1; d--) {
			for (SchematicSpec.PlacedBlock block : spec.blocks) {
				if (block.rel().getY() != 0) continue;
				BlockPos pos = BuildJob.at(origin, rotation, block.rel());
				if (foundation(level, pos) >= d) out.add(new SchematicSpec.PlacedBlock(pos.below(d), stone));
			}
		}
		return out;
	}

	/**
	 * The ground and plants inside the building's box above its floor, top down: dug out before anything goes up
	 * (a hillside, a bush), so it is not left full of earth. Its own blocks are set into it afterwards.
	 */
	static List<SchematicSpec.PlacedBlock> dig(ServerLevel level, BlockPos origin, BoundingBox box) {
		List<SchematicSpec.PlacedBlock> out = new ArrayList<>();
		for (int y = box.maxY(); y >= box.minY(); y--) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState there = level.getBlockState(pos);
					// the floor's own layer keeps its ground (it is built into); a trunk there goes
					if (there.isAir() || there.canBeReplaced() || !BuildJob.carvable(there) || (y == box.minY() && BuildJob.naturalGround(there))) continue;
					out.add(new SchematicSpec.PlacedBlock(pos, Blocks.AIR.defaultBlockState()));
				}
			}
		}
		return out;
	}

	/** The rotation that turns the plan's front (north) toward {@code front}. */
	static Rotation facing(Direction front) {
		for (Rotation rotation : Rotation.values()) {
			if (rotation.rotate(Direction.NORTH) == front) return rotation;
		}
		return Rotation.NONE;
	}

	/**
	 * The town buildings' second age (a town with its risen spire, after its houses' HouseLevels.secondAge): one built
	 * town building a day is restyled in its biome's fine palette where it stands — only the blocks that differ are taken
	 * down and laid anew, by the town's builders, the treasury paying. True when one was queued.
	 */
	public static boolean restyle(ServerLevel level, Roads roads, Roads.Network network) {
		com.vnap.spire.Spires.Spire spire = com.vnap.spire.Spires.get(level.getServer()).ofTown(level.dimension(), network.bell);
		if (spire == null || spire.shown <= 0) return false;
		if (network.segments.stream().anyMatch(segment -> segment.label.startsWith(RESTYLE))) return false;
		for (Kind kind : Kind.values()) {
			Plot plot = plot(network, kind);
			if (plot == null || !plot.queued || HousePalette.fine(plot.palette) || !level.isPositionEntityTicking(plot.origin)) continue;
			if (network.segments.stream().anyMatch(segment -> segment.id == plot.segment)) continue;
			String fine = HousePalette.finer(plot.palette);
			SchematicSpec spec = HousePalette.apply(Workshops.schematic(level, kind.plan), fine);
			List<SchematicSpec.PlacedBlock> work = new ArrayList<>();
			for (SchematicSpec.PlacedBlock block : blocks(level, spec, plot.origin, plot.rotation)) {
				BlockState there = level.getBlockState(block.rel());
				if (there.equals(block.state()) || there.hasBlockEntity() || there.getBlock() instanceof net.minecraft.world.level.block.BedBlock
						|| there.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) continue;
				if (!there.isAir()) work.add(new SchematicSpec.PlacedBlock(block.rel(), Blocks.AIR.defaultBlockState()));
				work.add(block);
			}
			plot.palette = fine;
			roads.setDirty();
			if (work.isEmpty()) continue;
			roads.queue(level, network, work, RESTYLE + kind.name().toLowerCase(java.util.Locale.ROOT));
			Chronicle.note(level, network.bell, "restyle." + kind.name().toLowerCase(java.util.Locale.ROOT));
			return true;
		}
		return false;
	}

	/** The label of a town building's second age. */
	static final String RESTYLE = "restyle_";

	/** The building's blocks in the world, in the order a builder sets them (BuildStages). */
	static List<SchematicSpec.PlacedBlock> blocks(ServerLevel level, SchematicSpec spec, BlockPos origin, Rotation rotation) {
		int[] all = new int[spec.blocks.size()];
		for (int i = 0; i < all.length; i++) all[i] = i;
		List<SchematicSpec.PlacedBlock> out = new ArrayList<>();
		for (int i : BuildStages.sort(spec, 0, all)) {
			SchematicSpec.PlacedBlock block = spec.blocks.get(i);
			if (block.state().isAir()) continue;
			BlockPos at = BuildJob.at(origin, rotation, block.rel());
			out.add(new SchematicSpec.PlacedBlock(at, block.state().rotate(level, at, rotation)));
		}
		return out;
	}

	/** The ground the building stands on (no house, road or wall over it), or null with no plot. */
	public static @Nullable BoundingBox footprint(ServerLevel level, Roads.Network network, Kind kind) {
		Plot plot = plot(network, kind);
		return plot == null ? null : HouseProject.footprint(Workshops.schematic(level, kind.plan), plot.origin, plot.rotation);
	}

	/** A place in the building, by its plan's coordinates; null with no plot. */
	public static @Nullable BlockPos at(Roads.Network network, Kind kind, BlockPos rel) {
		Plot plot = plot(network, kind);
		return plot == null ? null : BuildJob.at(plot.origin, plot.rotation, rel);
	}

	private static boolean free(Roads.Network network, BoundingBox box) {
		for (TownPlan.Lot lot : network.lots) if (lot.box.equals(box)) return lot.free();
		return false;
	}

	/** Every kept building's ground. */
	static List<BoundingBox> footprints(ServerLevel level, Roads.Network network) {
		List<BoundingBox> out = new ArrayList<>();
		for (Kind kind : network.plots.keySet()) {
			BoundingBox box = footprint(level, network, kind);
			if (box != null) out.add(box);
		}
		return out;
	}

	/** The doors of the queued buildings, as the nodes their roads join (Roads.nodes). */
	static List<BlockPos> doors(ServerLevel level, Roads.Network network) {
		List<BlockPos> out = new ArrayList<>();
		for (var entry : network.plots.entrySet()) {
			Plot plot = entry.getValue();
			BoundingBox box = footprint(level, network, entry.getKey());
			if (!plot.queued || box == null) continue;
			BlockPos step = Roads.step(level, Workshops.schematic(level, entry.getKey().plan), plot.origin, plot.rotation, box);
			if (step != null) out.add(step);
		}
		return out;
	}

	/** Every block the town's buildings take, in every palette: the stall keeps them (Market.building). */
	public static List<SchematicSpec> kit() {
		List<SchematicSpec> out = new ArrayList<>();
		for (Kind kind : Kind.values()) {
			SchematicSpec spec = SchematicSpec.loadBundled(kind.plan);
			if (spec.blocks.isEmpty()) continue;
			for (String palette : HousePalette.ALL) out.add(HousePalette.apply(spec, palette));
			// and the second age's (restyle)
			for (String palette : HousePalette.FINES) out.add(HousePalette.apply(spec, palette));
		}
		return out;
	}
}
