package com.vnap.gametest;

import com.vnap.village.build.BuildJob;
import com.vnap.village.home.Mayors;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.town.Greenery;
import com.vnap.village.town.Roads;
import com.vnap.village.town.TownPlan;
import com.vnap.village.town.TownRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The town's green between the houses: verges and nooks on land nobody needs, the paths as they were. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class GreeneryGameTests {
	/**
	 * A road, a hut, a lot of the plan and a long shed: the verges get plants nobody bumps into, never on the lot, the
	 * road or by the buildings; the gap between hut, lot and shed gets one nook whose solid pieces stand inside it, so
	 * the gap stays one piece to walk through; the gardener sets the plants and puts back a picked one.
	 */
	@GameTest(template = "workshop_platform")
	public static void theGapsBetweenHousesGrowGreenNotTheLots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(50, 2, 34), Blocks.BELL);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), helper.absolutePos(new BlockPos(50, 2, 34)));
		Roads roads = Roads.get(level.getServer());
		roads.networks().removeIf(old -> old.bell.closerThan(office.bell, TownRules.SPAN));
		Roads.Network network = roads.of(level.dimension(), office.bell);
		try {
			for (int x = 20; x <= 80; x++) {
				helper.setBlock(new BlockPos(x, 1, 40), Blocks.DIRT_PATH);
				network.cells.add(helper.absolutePos(new BlockPos(x, 1, 40)));
			}
			BoundingBox hut = box(helper, 30, 2, 44, 36, 5, 50);
			BoundingBox shed = box(helper, 30, 2, 52, 60, 4, 58);
			network.workshops.put(1L, hut);
			network.workshops.put(2L, shed);
			BoundingBox lotBox = box(helper, 44, 2, 42, 54, 8, 50);
			network.lots.add(new TownPlan.Lot(1, lotBox, Direction.NORTH, 0, 0));

			Greenery.plan(level, roads, network);
			Checks.assertFalse(helper, network.greens.isEmpty(), "the verges are planned");
			for (Map.Entry<Long, Block> entry : network.greens.entrySet()) {
				BlockPos cell = BlockPos.of(entry.getKey());
				Checks.assertTrue(helper, entry.getValue().defaultBlockState().getCollisionShape(level, cell).isEmpty(), "nobody bumps into " + entry.getValue());
				Checks.assertFalse(helper, column(lotBox, cell, 0) || column(hut, cell, 1) || column(shed, cell, 1), "never on the lot nor by a building: " + cell);
				Checks.assertFalse(helper, network.cells.contains(cell.below()) || network.cells.contains(cell), "never on the road: " + cell);
			}
			Checks.assertTrue(helper, network.nooks.size() == 1, "the gap gets its nook: " + network.nooks);
			Roads.Segment nook = network.segments.stream().filter(seg -> Greenery.LABEL.equals(seg.label)).findFirst().orElse(null);
			if (nook == null) {
				Checks.fail(helper, "the nook is queued for the builders");
				return;
			}
			Set<BlockPos> solid = new HashSet<>();
			for (SchematicSpec.PlacedBlock block : nook.spec.blocks) {
				BlockPos at = block.rel().offset(nook.origin);
				if (!block.state().getCollisionShape(level, at).isEmpty()) solid.add(at.atY(0));
				Checks.assertFalse(helper, column(lotBox, at, 1) || column(hut, at, 1) || column(shed, at, 1), "the nook keeps off the lot and the buildings: " + at);
				for (BlockPos road : network.cells) Checks.assertTrue(helper, Math.abs(road.getX() - at.getX()) + Math.abs(road.getZ() - at.getZ()) >= 3,
						"nothing solid of it by the road: " + at);
			}
			// the gap between them is still one piece to walk through
			Set<BlockPos> gap = new HashSet<>();
			BlockPos a = helper.absolutePos(new BlockPos(37, 0, 41));
			BlockPos b = helper.absolutePos(new BlockPos(43, 0, 51));
			for (int x = a.getX(); x <= b.getX(); x++) for (int z = a.getZ(); z <= b.getZ(); z++) if (!solid.contains(new BlockPos(x, 0, z))) gap.add(new BlockPos(x, 0, z));
			Checks.assertTrue(helper, connected(gap), "the gap is not cut in two");
			layAll(level, network);

			// the gardener: every plant set, a picked one put back
			int set = 0;
			for (BlockPos pos : Greenery.missing(level, network, office.bell, 1000)) if (Greenery.set(level, network, pos)) set++;
			Checks.assertTrue(helper, set > 0 && Greenery.missing(level, network, office.bell, 1000).isEmpty(), "the gardener set them all: " + set);
			BlockPos picked = BlockPos.of(network.greens.keySet().iterator().next());
			level.destroyBlock(picked, false);
			Checks.assertTrue(helper, Greenery.missing(level, network, office.bell, 10).contains(picked) && Greenery.set(level, network, picked), "a picked one is put back");
			// something built on a verge cell is the player's: struck off, not fought over
			BlockPos built = BlockPos.of(network.greens.keySet().iterator().next());
			level.setBlock(built, Blocks.COBBLESTONE.defaultBlockState(), 3);
			Checks.assertFalse(helper, Greenery.set(level, network, built) || network.greens.containsKey(built.asLong()), "a cell taken by a block is struck off");
			helper.succeed();
		} finally {
			roads.networks().remove(network);
			Mayors.get(level.getServer()).offices().remove(office);
		}
	}

	private static BoundingBox box(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1) {
		return BoundingBox.fromCorners(helper.absolutePos(new BlockPos(x0, y0, z0)), helper.absolutePos(new BlockPos(x1, y1, z1)));
	}

	private static boolean column(BoundingBox box, BlockPos pos, int margin) {
		return pos.getX() >= box.minX() - margin && pos.getX() <= box.maxX() + margin && pos.getZ() >= box.minZ() - margin && pos.getZ() <= box.maxZ() + margin;
	}

	private static boolean connected(Set<BlockPos> cells) {
		if (cells.isEmpty()) return true;
		BlockPos start = cells.iterator().next();
		Set<BlockPos> reached = new HashSet<>(List.of(start));
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
		while (!queue.isEmpty()) {
			BlockPos cell = queue.poll();
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = cell.relative(side);
				if (cells.contains(next) && reached.add(next)) queue.add(next);
			}
		}
		return reached.size() == cells.size();
	}

	private static void layAll(ServerLevel level, Roads.Network network) {
		for (Roads.Segment segment : List.copyOf(network.segments)) {
			BuildJob job = segment.job();
			job.site = null;
			while (!job.done()) {
				job.skipDone(level);
				if (!job.done()) job.placeNext(level, null);
			}
			Roads.get(level.getServer()).finish(level, segment);
		}
	}
}
