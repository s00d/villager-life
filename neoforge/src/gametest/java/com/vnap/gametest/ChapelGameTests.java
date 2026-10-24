package com.vnap.gametest;

import com.vnap.village.town.TownRules;
import com.vnap.block.LifeBlocks;
import com.vnap.block.clock.TowerClockBlockEntity;
import com.vnap.dialogue.Family;
import com.vnap.mood.MoodConfig;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.Mayors;
import com.vnap.village.town.Chapel;
import com.vnap.village.town.Roads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** The town's chapel: laid out once the town is half full, built like its roads; its tower clock and the keeper who winds it. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class ChapelGameTests {
	private ChapelGameTests() {
	}

	private record Town(Mayors.Office office, Roads roads, Roads.Network network, BlockPos bell) {
		void forget(ServerLevel level) {
			roads.networks().remove(network);
			Mayors.get(level.getServer()).offices().remove(office);
		}
	}

	private static Town town(GameTestHelper helper, int people) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(50, 2, 50), Blocks.BELL);
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Roads roads = Roads.get(level.getServer());
		roads.networks().removeIf(old -> old.bell.closerThan(office.bell, TownRules.SPAN));
		Roads.Network network = roads.of(level.dimension(), office.bell);
		long day = Family.day(level);
		for (int i = 0; i < people; i++) office.residents.put(UUID.randomUUID(), day);
		return new Town(office, roads, network, bell);
	}

	/** By hand would take days: every stretch set at once, in the builders' order. */
	private static void layAll(ServerLevel level, Roads.Network network) {
		for (Roads.Segment segment : List.copyOf(network.segments)) {
			BuildJob job = segment.job();
			job.site = null;
			while (!job.done()) {
				job.skipDone(level);
				if (!job.done()) job.placeNext(level, null);
			}
		}
	}

	@GameTest(batch = "roads", template = "home_platform", timeoutTicks = 200)
	public static void aHalfFullTownBuildsAChapelWithAClockTower(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Town town = town(helper, MoodConfig.maxVillagers() / 4);
		try {
			Roads.Network network = town.network();
			town.roads().plan(level, network, Family.day(level), 0);
			com.vnap.village.town.TownPlots.Plot kept0 = com.vnap.village.town.TownPlots.plot(network, com.vnap.village.town.TownPlots.Kind.CHAPEL);
			Checks.assertTrue(helper, kept0 != null && !kept0.queued && network.segments.stream().noneMatch(s -> s.spec.blocks.size() > 500),
					"a town with its first square keeps the chapel's ground, but does not build it yet");
			BoundingBox kept = com.vnap.village.town.TownPlots.footprint(level, network, com.vnap.village.town.TownPlots.Kind.CHAPEL);
			Checks.assertTrue(helper, kept != null && com.vnap.village.town.Land.around(level, town.bell()).claims().stream().map(com.vnap.village.town.Land.Claim::box).anyMatch(box -> box.equals(kept)), "no house goes up there meanwhile");
			long day = Family.day(level);
			for (int i = 0; i < MoodConfig.maxVillagers() / 2; i++) town.office().residents.put(UUID.randomUUID(), day);
			town.roads().plan(level, network, Family.day(level), 0);
			com.vnap.village.town.TownPlots.Plot laid = com.vnap.village.town.TownPlots.plot(network, com.vnap.village.town.TownPlots.Kind.CHAPEL);
			BlockPos origin = laid == null ? null : laid.origin;
			if (origin == null) {
				Checks.fail(helper, "a half full town lays out its chapel");
				return;
			}
			Checks.assertTrue(helper, !network.segments.isEmpty(), "to be built by the town's hands, stretch by stretch");
			layAll(level, network);
			List<TowerClockBlockEntity> clocks = Chapel.clocks(level, network);
			Checks.assertTrue(helper, clocks.size() == 3, "a dial on three sides of the tower: " + clocks.size());
			BlockPos winder = Chapel.winder(network);
			Checks.assertTrue(helper, winder != null && level.getBlockState(winder).isAir() && level.getBlockState(winder.above(2)).is(Blocks.CHISELED_STONE_BRICKS),
					"the weights hang over the tower's ground floor: " + winder);
			// the tower's door looks toward the bell
			TowerClockBlockEntity front = clocks.stream().min(java.util.Comparator.comparingDouble(clock -> clock.getBlockPos().distSqr(town.bell()))).orElse(clocks.get(0));
			BlockPos ahead = front.getBlockPos().relative(front.getBlockState().getValue(HorizontalDirectionalBlock.FACING), 4);
			Checks.assertTrue(helper, ahead.distSqr(town.bell()) < front.getBlockPos().distSqr(town.bell()), "its front faces the bell");
			BoundingBox footprint = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(origin, origin);
			Checks.assertTrue(helper, com.vnap.village.town.Land.around(level, town.bell()).claims().stream().map(com.vnap.village.town.Land.Claim::box).anyMatch(box -> box.intersects(footprint)), "no house goes up on it");
			town.roads().plan(level, network, Family.day(level) + 1, 0);
			com.vnap.village.town.TownPlots.Plot again = com.vnap.village.town.TownPlots.plot(network, com.vnap.village.town.TownPlots.Kind.CHAPEL);
			Checks.assertTrue(helper, again != null && origin.equals(again.origin), "one chapel a town");
			// with its spire risen, the town restyles its chapel in the fine palette, where it stands
			com.vnap.spire.Spires spires = com.vnap.spire.Spires.get(level.getServer());
			com.vnap.spire.Spires.Spire spire = spires.add(level.dimension(), town.bell(), 1L, town.bell().offset(60, 0, 0), town.bell().offset(55, 0, 0),
					new BoundingBox(town.bell().getX() + 50, town.bell().getY(), town.bell().getZ(), town.bell().getX() + 70, town.bell().getY() + 20, town.bell().getZ() + 20));
			spire.shown = 1;
			try {
				Checks.assertTrue(helper, com.vnap.village.town.TownPlots.restyle(level, town.roads(), network), "a restyle of a town building is queued");
				Checks.assertTrue(helper, again != null && com.vnap.village.home.HousePalette.fine(again.palette) && network.segments.stream().anyMatch(segment -> segment.label.startsWith("restyle_")),
						"in the fine palette");
				layAll(level, network);
				Checks.assertTrue(helper, Chapel.clocks(level, network).size() == 3, "the restyled chapel keeps its clocks");
			} finally {
				spires.all().remove(spire);
			}
		} finally {
			town.forget(level);
		}
		helper.succeed();
	}

	/** A villager fallen into a pond whose rim stands over the water climbs out instead of swimming there for hours. */
	@GameTest(batch = "roads", template = "home_platform", timeoutTicks = 200)
	public static void aNewTownKeepsGroundForAllItsBuildingsOnItsFirstDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Town town = town(helper, 1);
		try {
			town.roads().plan(level, town.network(), Family.day(level), 0);
			for (com.vnap.village.town.TownPlots.Kind kind : com.vnap.village.town.TownPlots.Kind.values()) {
				com.vnap.village.town.TownPlots.Plot plot = com.vnap.village.town.TownPlots.plot(town.network(), kind);
				Checks.assertTrue(helper, plot != null && !plot.queued, "a town of one keeps the " + kind + "'s ground before any house: "
						+ com.vnap.village.town.TownPlots.lastRefusal);
				BoundingBox box = com.vnap.village.town.TownPlots.footprint(level, town.network(), kind);
				Checks.assertTrue(helper, box != null && !com.vnap.village.town.TownPlots.mainStreets(town.network().bell, box),
						"and not across the way of a main street: " + kind);
			}
		} finally {
			town.forget(level);
		}
		helper.succeed();
	}

	@GameTest(batch = "roads", template = "home_platform", timeoutTicks = 200)
	public static void aTownWithNoRoomLeftBuildsOnItsOutskirts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Town town = town(helper, 1);
		try {
			BlockPos bell = town.office().bell;
			// every lot round the bell bought by a player: nothing of the town's may go there
			com.vnap.village.town.TownPlan.Lot lot = new com.vnap.village.town.TownPlan.Lot(9001,
					new BoundingBox(bell.getX() - 38, bell.getY() - 4, bell.getZ() - 38, bell.getX() + 38, bell.getY() + 12, bell.getZ() + 38), net.minecraft.core.Direction.NORTH, 0, 0);
			lot.player = UUID.randomUUID();
			town.network().lots.add(lot);
			double before = com.vnap.village.home.Towns.reach(level, bell);
			if (!com.vnap.village.town.TownPlots.lay(level, town.roads(), town.network(), com.vnap.village.town.TownPlots.Kind.GRANARY)) {
				Checks.fail(helper, "the granary goes up on the outskirts: " + com.vnap.village.town.TownPlots.lastRefusal);
				return;
			}
			BoundingBox box = com.vnap.village.town.TownPlots.footprint(level, town.network(), com.vnap.village.town.TownPlots.Kind.GRANARY);
			Checks.assertTrue(helper, box != null && !box.intersects(lot.box), "not on the bought ground");
			Checks.assertTrue(helper, town.office().outskirts > 0 && com.vnap.village.home.Towns.reach(level, bell) >= before, "the town's land grows to take it in");
			if (box != null) {
				for (BlockPos corner : new BlockPos[]{new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.minY(), box.maxZ()),
						new BlockPos(box.minX(), box.minY(), box.maxZ()), new BlockPos(box.maxX(), box.minY(), box.minZ())}) {
					Checks.assertTrue(helper, com.vnap.village.home.Towns.ours(level, bell, corner), "all of it the town's now: " + corner.toShortString());
				}
			}
		} finally {
			town.forget(level);
		}
		helper.succeed();
	}

	@GameTest(template = "platform", timeoutTicks = 900)
	public static void aVillagerClimbsOutOfAPond(GameTestHelper helper) {
		// a pit two deep in a raised floor: water to its rim's foot, a wall of earth two high around it
		for (int x = 4; x <= 12; x++) {
			for (int z = 4; z <= 12; z++) {
				boolean pond = x >= 7 && x <= 9 && z >= 7 && z <= 9;
				helper.setBlock(new BlockPos(x, 2, z), pond ? Blocks.WATER : Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), pond ? Blocks.AIR : Blocks.DIRT);
			}
		}
		for (int x = 7; x <= 9; x++) for (int z = 7; z <= 9; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
		Villager villager = helper.spawn(EntityType.VILLAGER, 8, 1, 8);
		helper.succeedWhen(() -> Checks.assertTrue(helper, !villager.isInWater() && villager.getY() >= helper.absolutePos(new BlockPos(0, 4, 0)).getY(),
				"out of the pond, on the ground: " + villager.blockPosition()));
	}

	@GameTest(template = "platform")
	public static void aTowerClockRunsWhileWoundAndStopsWhenRunDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(10, 2, 10), LifeBlocks.TOWER_CLOCK.defaultBlockState());
		if (!(level.getBlockEntity(helper.absolutePos(new BlockPos(10, 2, 10))) instanceof TowerClockBlockEntity clock)) {
			Checks.fail(helper, "the clock's works");
			return;
		}
		Checks.assertTrue(helper, TowerClockBlockEntity.hour(0L) == 6 && TowerClockBlockEntity.hour(6000L) == 12 && TowerClockBlockEntity.hour(18000L) == 0,
				"day time 0 is six in the morning");
		helper.runAfterDelay(2, () -> {
			Checks.assertTrue(helper, !clock.running() && clock.shownTime(com.vnap.Mc.dayTime(level) + 5000L) != com.vnap.Mc.dayTime(level) + 5000L,
					"never wound, it stands still");
			clock.wind();
			Checks.assertTrue(helper, clock.running() && clock.runsFor() >= TowerClockBlockEntity.WIND - 1, "wound, it runs two days: " + clock.runsFor());
			Checks.assertTrue(helper, clock.shownTime(12345L) == 12345L, "and shows the world's time");
			clock.wind();
			clock.wind();
			Checks.assertTrue(helper, clock.runsFor() <= 3 * 24000L, "three days at the most");
			helper.succeed();
		});
	}

	@GameTest(batch = "roads", template = "home_platform", timeoutTicks = 1600)
	public static void aVillagerWithoutATradeWindsTheChapelClockForAWage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.onEachTick(() -> {
			if (com.vnap.Mc.dayTime(level) % 24000L > 8500L) com.vnap.Mc.setDayTime(level, 2500L);
		});
		com.vnap.Mc.setDayTime(level, 2500L);
		Town town = town(helper, 0);
		Roads.Network network = town.network();
		if (!com.vnap.village.town.TownPlots.lay(level, town.roads(), network, com.vnap.village.town.TownPlots.Kind.CHAPEL)) {
			town.forget(level);
			Checks.fail(helper, "room for the chapel");
			return;
		}
		layAll(level, network);
		BlockPos winder = Chapel.winder(network);
		if (winder == null) {
			town.forget(level);
			Checks.fail(helper, "the chapel's weights");
			return;
		}
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(50, 2, 52));
		// in the tower already: the walk up from the street is the live world's to show
		com.vnap.Mc.snap(keeper, winder.getX() + 0.5, winder.getY(), winder.getZ() + 0.5);
		keeper.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), town.bell()));
		helper.succeedWhen(() -> {
			List<TowerClockBlockEntity> clocks = Chapel.clocks(level, network);
			Checks.assertTrue(helper, !clocks.isEmpty() && clocks.stream().allMatch(TowerClockBlockEntity::running), "the clock is wound");
			Checks.assertTrue(helper, Family.state(keeper).coins >= 3, "and the keeper paid: " + Family.state(keeper).coins);
			town.forget(level);
		});
	}
}
