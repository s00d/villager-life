package com.vnap.village.town;

import com.vnap.mood.MoodConfig;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The town wall, once the town is full and done building (its layout will not change much more): an octagon round
 * everything the town has, a few blocks out, laid over the ground as it lies, column by column. Stone from the stall,
 * the treasury's money, the town's day labourers (Roads.queue, RoadBehavior), stretch by stretch. Towers at the corners
 * and along the long sides, a gate wherever a road runs out (a crossbow on each side of it, looking out), gaps where
 * the land is not the town's or a building stands. A river is dammed: its columns are filled with stone to the bed
 * (thrown in from the bank, Hands.SINK_REACH) and the wall goes on over them; a lake too wide or deep is left open.
 * Outside it a dry ditch two wide and two deep, so that what walks in comes through a gate: none in front of the
 * gates, by roads or next to water (it would run in). Everything else a builder sets is within its reach from the
 * ground (Hands.REACH): a wall four high with crenels on top, towers five high with a floor and a ring of crenels.
 */
public final class Wall {

	private Wall() {
	}

	/** A full town building nothing more (its plots settled) gets its wall. */
	static boolean due(ServerLevel level, Roads.Network network) {
		if (network.wall > 0) return false;
		if (!Towns.full(level, network.bell)) return false;
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.done && Towns.ours(level, network.bell, house.origin)) return false;
		}
		return true;
	}

	static boolean plan(ServerLevel level, Roads roads, Roads.Network network) {
		return due(level, network) && lay(level, roads, network);
	}

	/** Lays the wall out and queues it; false when the town's middle is not loaded to plan from. */
	public static boolean lay(ServerLevel level, Roads roads, Roads.Network network) {
		Mayors.Office office = Towns.of(level, network.bell);
		BlockPos bell = office == null ? network.bell : office.bell;
		if (!level.isPositionEntityTicking(bell)) return false;
		BoundingBox area = area(level, network, bell);
		List<BlockPos> outline = octagon(area);
		// the whole ring at once or not yet: a wall laid out over unloaded land would have holes for good
		for (BlockPos column : outline) {
			if (!level.isPositionEntityTicking(new BlockPos(column.getX(), bell.getY(), column.getZ()))) return false;
		}
		String palette = HousePalette.forBiome(level.getBiome(bell));
		BlockState base = Roads.palette(Blocks.COBBLESTONE.defaultBlockState(), palette);
		BlockState body = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState crenel = Blocks.STONE_BRICK_WALL.defaultBlockState();
		List<BoundingBox> blocked = Roads.blocked(level, network);
		Set<Integer> towers = towers(outline);
		Set<Integer> gates = new java.util.HashSet<>();
		for (int i = 0; i < outline.size(); i++) {
			BlockPos column = outline.get(i);
			// a gate in the middle of every side, in line with the bell (Millenaire's walls have them so): the town opens
			// to the four winds whatever its roads do
			boolean side = ((column.getX() == area.minX() || column.getX() == area.maxX()) && column.getZ() == bell.getZ())
					|| ((column.getZ() == area.minZ() || column.getZ() == area.maxZ()) && column.getX() == bell.getX());
			if (side) {
				gates.add(Math.floorMod(i - 1, outline.size()));
				gates.add(i);
				gates.add((i + 1) % outline.size());
			}
			RoadFinder.Cell cell = RoadFinder.surface(level, column.getX(), column.getZ(), network.cells::contains);
			if (cell != null && network.cells.contains(cell.pos())) {
				// a road runs out here: a gate three wide
				gates.add(Math.floorMod(i - 1, outline.size()));
				gates.add(i);
				gates.add((i + 1) % outline.size());
			}
		}
		RoadFinder.Cell[] cells = new RoadFinder.Cell[outline.size()];
		for (int i = 0; i < outline.size(); i++) cells[i] = RoadFinder.surface(level, outline.get(i).getX(), outline.get(i).getZ(), network.cells::contains);
		int[] depths = dams(level, cells);
		List<SchematicSpec.PlacedBlock> stretch = new ArrayList<>();
		List<BlockPos> gateColumns = new ArrayList<>();
		int columns = 0;
		Towns.Territory territory = Towns.territory(level, network.bell);
		for (int i = 0; i < outline.size(); i++) {
			BlockPos column = outline.get(i);
			if (!territory.contains(column)) continue;
			RoadFinder.Cell cell = cells[i];
			if (cell == null || (cell.water() && depths[i] == 0) || Land.inside(blocked, cell.pos())) continue;
			BlockPos ground = cell.pos();
			// a dammed column: stone from the bed up, the wall standing on the water's surface
			for (int d = depths[i] - 1; d >= 1; d--) stretch.add(new SchematicSpec.PlacedBlock(ground.below(d), base));
			if (gates.contains(i)) {
				boolean middle = gates.contains(Math.floorMod(i - 1, outline.size())) && gates.contains((i + 1) % outline.size());
				if (cell.water()) stretch.add(new SchematicSpec.PlacedBlock(ground, base));
				gate(network, bell, ground, body, stretch, middle);
				gateColumns.add(ground);
			} else if (towers.contains(i) && !cell.water() && !nearGate(gates, i, outline.size())) {
				tower(level, network, ground, base, body, crenel, blocked, stretch);
			} else {
				for (int y = 0; y < TownRules.WALL_HEIGHT; y++) stretch.add(new SchematicSpec.PlacedBlock(ground.above(y), y <= 1 ? base : body));
				// crenels every other column
				if (i % 2 == 0) stretch.add(new SchematicSpec.PlacedBlock(ground.above(TownRules.WALL_HEIGHT), crenel));
				network.wallCells.add(ground);
			}
			if (++columns % TownRules.WALL_STRETCH == 0) {
				roads.queue(level, network, stretch, "wall");
				stretch = new ArrayList<>();
			}
		}
		if (!stretch.isEmpty()) roads.queue(level, network, stretch, "wall");
		List<SchematicSpec.PlacedBlock> ditch = ditch(level, network, area, blocked, gateColumns);
		for (int from = 0; from < ditch.size(); from += TownRules.WALL_STRETCH * 2) roads.queue(level, network, new ArrayList<>(ditch.subList(from, Math.min(ditch.size(), from + TownRules.WALL_STRETCH * 2))), "ditch");
		network.wall = 1;
		roads.setDirty();
		return true;
	}

	/**
	 * How deep each water column of the ring is where the wall dams it (0: dry land, or water left open): runs of
	 * water along the ring no longer than TownRules.WALL_MAX_SPAN, each column no deeper than TownRules.WALL_MAX_DEPTH.
	 */
	static int[] dams(ServerLevel level, RoadFinder.Cell[] cells) {
		int n = cells.length;
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			if (cells[i] == null || !cells[i].water() || out[i] != 0) continue;
			// the whole run this column is in, round the ring both ways
			int start = i;
			int len = 1;
			while (len < n && wet(cells[Math.floorMod(start - 1, n)])) {
				start = Math.floorMod(start - 1, n);
				len++;
			}
			int end = i;
			while (len < n && wet(cells[(end + 1) % n])) {
				end = (end + 1) % n;
				len++;
			}
			if (len > TownRules.WALL_MAX_SPAN) {
				for (int k = 0, j = start; k < len; k++, j = (j + 1) % n) out[j] = -1;
				continue;
			}
			int[] run = new int[len];
			boolean ok = true;
			for (int k = 0, j = start; k < len; k++, j = (j + 1) % n) {
				run[k] = depth(level, cells[j].pos());
				ok &= run[k] > 0;
			}
			for (int k = 0, j = start; k < len; k++, j = (j + 1) % n) out[j] = ok ? run[k] : -1;
		}
		for (int i = 0; i < n; i++) out[i] = Math.max(0, out[i]);
		return out;
	}

	private static boolean wet(RoadFinder.@org.jspecify.annotations.Nullable Cell cell) {
		return cell != null && cell.water();
	}

	/** Blocks of water from the surface down to the bed, or 0 deeper than TownRules.WALL_MAX_DEPTH. */
	private static int depth(ServerLevel level, BlockPos surface) {
		for (int d = 1; d <= TownRules.WALL_MAX_DEPTH; d++) {
			if (level.getFluidState(surface.below(d)).isEmpty()) return level.getBlockState(surface.below(d)).isAir() ? 0 : d;
		}
		return 0;
	}

	/** The dry ditch outside the wall: two rings dug two deep, clear of the gates, the roads, buildings and water. */
	static List<SchematicSpec.PlacedBlock> ditch(ServerLevel level, Roads.Network network, BoundingBox area, List<BoundingBox> blocked, List<BlockPos> gates) {
		List<SchematicSpec.PlacedBlock> out = new ArrayList<>();
		Towns.Territory territory = Towns.territory(level, network.bell);
		for (int ring = TownRules.DITCH_OUT; ring < TownRules.DITCH_OUT + 2; ring++) {
			for (BlockPos column : octagon(area.inflatedBy(ring, 0, ring))) {
				if (!territory.contains(column)) continue;
				boolean byGate = false;
				for (BlockPos gate : gates) byGate |= Math.max(Math.abs(gate.getX() - column.getX()), Math.abs(gate.getZ() - column.getZ())) <= TownRules.GATE_CLEAR;
				if (byGate) continue;
				RoadFinder.Cell cell = RoadFinder.surface(level, column.getX(), column.getZ(), network.cells::contains);
				if (cell == null || cell.water() || network.cells.contains(cell.pos()) || Land.inside(blocked, cell.pos()) || nearWater(level, cell.pos())) continue;
				for (int d = 0; d < TownRules.DITCH_DEPTH; d++) out.add(new SchematicSpec.PlacedBlock(cell.pos().below(d), Blocks.AIR.defaultBlockState()));
				network.moat.add(cell.pos().below(TownRules.DITCH_DEPTH - 1));
			}
		}
		return out;
	}

	/** Water beside or under what the ditch digs out (it would run in and fill it). */
	private static boolean nearWater(ServerLevel level, BlockPos top) {
		for (int d = 0; d <= TownRules.DITCH_DEPTH; d++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (!level.getFluidState(top.offset(dx, -d, dz)).isEmpty()) return true;
				}
			}
		}
		return false;
	}

	/** One stretch's stone in every palette: the stall keeps it (Market.building). */
	static List<SchematicSpec> kit() {
		List<SchematicSpec> out = new ArrayList<>();
		for (String palette : HousePalette.ALL) {
			List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
			int n = 0;
			for (int i = 0; i < TownRules.WALL_STRETCH * 2 + 18; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Roads.palette(Blocks.COBBLESTONE.defaultBlockState(), palette)));
			for (int i = 0; i < TownRules.WALL_STRETCH * 2 + 36; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.STONE_BRICKS.defaultBlockState()));
			for (int i = 0; i < TownRules.WALL_STRETCH / 2 + 4; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.STONE_BRICK_WALL.defaultBlockState()));
			for (int i = 0; i < 4; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.LANTERN.defaultBlockState()));
			for (int i = 0; i < 4; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), com.vnap.block.LifeBlocks.ARROW_TURRET.defaultBlockState()));
			out.add(new SchematicSpec(n, 1, 1, List.of(), blocks, BlockPos.ZERO));
		}
		return out;
	}

	/** Everything the town has (houses, rooms, roads, the square, the bell) and the margin round it. */
	static BoundingBox area(ServerLevel level, Roads.Network network, BlockPos bell) {
		int minX = bell.getX(), maxX = bell.getX(), minZ = bell.getZ(), maxZ = bell.getZ();
		List<BlockPos> points = new ArrayList<>(network.cells);
		points.addAll(network.features);
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!Towns.ours(level, network.bell, house.origin)) continue;
			BoundingBox box = house.footprint(level);
			points.add(new BlockPos(box.minX(), 0, box.minZ()));
			points.add(new BlockPos(box.maxX(), 0, box.maxZ()));
		}
		for (TownPlan.Lot lot : network.lots) {
			if (lot.free()) continue;
			points.add(new BlockPos(lot.box.minX(), 0, lot.box.minZ()));
			points.add(new BlockPos(lot.box.maxX(), 0, lot.box.maxZ()));
		}
		for (BoundingBox box : Roads.blocked(level, network)) {
			if (!Towns.ours(level, network.bell, box.getCenter())) continue;
			points.add(new BlockPos(box.minX(), 0, box.minZ()));
			points.add(new BlockPos(box.maxX(), 0, box.maxZ()));
		}
		for (BlockPos point : points) {
			minX = Math.min(minX, point.getX());
			maxX = Math.max(maxX, point.getX());
			minZ = Math.min(minZ, point.getZ());
			maxZ = Math.max(maxZ, point.getZ());
		}
		return new BoundingBox(minX - TownRules.WALL_MARGIN, bell.getY(), minZ - TownRules.WALL_MARGIN, maxX + TownRules.WALL_MARGIN, bell.getY(), maxZ + TownRules.WALL_MARGIN);
	}

	/** The ring round the box with its corners cut, one column after the next (four-connected, closed). */
	static List<BlockPos> octagon(BoundingBox box) {
		int w = box.maxX() - box.minX();
		int h = box.maxZ() - box.minZ();
		int cut = Math.max(2, Math.min(w, h) / 5);
		List<BlockPos> corners = List.of(
				new BlockPos(box.minX() + cut, 0, box.minZ()), new BlockPos(box.maxX() - cut, 0, box.minZ()),
				new BlockPos(box.maxX(), 0, box.minZ() + cut), new BlockPos(box.maxX(), 0, box.maxZ() - cut),
				new BlockPos(box.maxX() - cut, 0, box.maxZ()), new BlockPos(box.minX() + cut, 0, box.maxZ()),
				new BlockPos(box.minX(), 0, box.maxZ() - cut), new BlockPos(box.minX(), 0, box.minZ() + cut));
		Set<BlockPos> ring = new LinkedHashSet<>();
		for (int i = 0; i < corners.size(); i++) {
			BlockPos from = corners.get(i);
			BlockPos to = corners.get((i + 1) % corners.size());
			int x = from.getX();
			int z = from.getZ();
			ring.add(new BlockPos(x, 0, z));
			// a diagonal goes as a staircase of single steps: the wall stays closed
			while (x != to.getX() || z != to.getZ()) {
				if (x != to.getX() && (z == to.getZ() || Math.abs(to.getX() - x) >= Math.abs(to.getZ() - z))) x += Integer.signum(to.getX() - x);
				else z += Integer.signum(to.getZ() - z);
				ring.add(new BlockPos(x, 0, z));
			}
		}
		return new ArrayList<>(ring);
	}

	/** A tower is three across: none right by a gate (it would wall up the gate's side and its crossbow). */
	private static boolean nearGate(Set<Integer> gates, int i, int size) {
		for (int d = -2; d <= 2; d++) if (gates.contains(Math.floorMod(i + d, size))) return true;
		return false;
	}

	/** Towers at the octagon's corners and every so often along its sides. */
	static Set<Integer> towers(List<BlockPos> outline) {
		Set<Integer> out = new java.util.HashSet<>();
		int since = 0;
		for (int i = 0; i < outline.size(); i++) {
			BlockPos prev = outline.get(Math.floorMod(i - 1, outline.size()));
			BlockPos next = outline.get((i + 1) % outline.size());
			BlockPos at = outline.get(i);
			boolean turn = (at.getX() - prev.getX()) != (next.getX() - at.getX()) || (at.getZ() - prev.getZ()) != (next.getZ() - at.getZ());
			boolean straightBefore = Math.abs(at.getX() - prev.getX()) + Math.abs(at.getZ() - prev.getZ()) == 1;
			if ((turn && straightBefore && since > 4) || since >= TownRules.TOWER_EVERY) {
				out.add(i);
				since = 0;
			} else {
				since++;
			}
		}
		return out;
	}

	/** A three by three tower on the wall's line: solid to its floor, a ring of crenels above. */
	private static void tower(ServerLevel level, Roads.Network network, BlockPos centre, BlockState base, BlockState body, BlockState crenel,
							  List<BoundingBox> blocked, List<SchematicSpec.PlacedBlock> out) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				RoadFinder.Cell cell = RoadFinder.surface(level, centre.getX() + dx, centre.getZ() + dz, network.cells::contains);
				if (cell == null || cell.water() || Land.inside(blocked, cell.pos()) || Math.abs(cell.pos().getY() - centre.getY()) > 1) continue;
				// all of it on the centre's ground: a tower stands level
				for (int y = Math.min(0, cell.pos().getY() - centre.getY()); y <= TownRules.TOWER_HEIGHT; y++) {
					BlockPos pos = new BlockPos(centre.getX() + dx, centre.getY() + y, centre.getZ() + dz);
					BlockState state = y <= 1 ? base : body;
					out.add(new SchematicSpec.PlacedBlock(pos, state));
				}
				network.wallCells.add(new BlockPos(centre.getX() + dx, centre.getY(), centre.getZ() + dz));
				if ((dx + dz) % 2 != 0) out.add(new SchematicSpec.PlacedBlock(new BlockPos(centre.getX() + dx, centre.getY() + TownRules.TOWER_HEIGHT + 1, centre.getZ() + dz), crenel));
			}
		}
		// a lantern on the tower's floor
		BlockPos light = centre.above(TownRules.TOWER_HEIGHT + 1);
		out.add(new SchematicSpec.PlacedBlock(light, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false)));
	}

	/**
	 * A gate column: open two high (the road goes through), an arch over it, a lantern on the arch's middle and a
	 * crossbow on each of its sides looking out (a builder reaches it from the ground).
	 */
	private static void gate(Roads.Network network, BlockPos bell, BlockPos ground, BlockState body, List<SchematicSpec.PlacedBlock> out, boolean middle) {
		for (int y = 3; y <= TownRules.WALL_HEIGHT; y++) out.add(new SchematicSpec.PlacedBlock(ground.above(y), body));
		BlockPos top = ground.above(TownRules.WALL_HEIGHT + 1);
		if (middle) {
			out.add(new SchematicSpec.PlacedBlock(top, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false)));
		} else {
			Direction outward = Direction.getNearest(ground.getX() - bell.getX(), 0, ground.getZ() - bell.getZ());
			out.add(new SchematicSpec.PlacedBlock(top, com.vnap.block.LifeBlocks.ARROW_TURRET.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, outward)));
			network.turrets.add(top);
		}
		network.wallCells.add(ground);
	}
}
