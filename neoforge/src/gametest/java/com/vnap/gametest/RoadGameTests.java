package com.vnap.gametest;

import com.vnap.village.town.TownRules;
import com.vnap.dialogue.Family;
import com.vnap.mood.MoodConfig;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.town.Land;
import com.vnap.village.town.RoadFinder;
import com.vnap.village.town.Roads;
import com.vnap.village.town.Wall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** The town's roads: the way round buildings and over water, the network, laying by hand, paving, saving. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class RoadGameTests {
	private RoadGameTests() {
	}

	@GameTest(batch = "roads", template = "home_platform")
	public static void aRoadGoesRoundAWallAndStepsUpOneBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// a wall across the way and a terrace one block up behind it
		for (int z = 35; z <= 65; z++) for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(45, y, z), Blocks.STONE);
		for (int x = 55; x <= 99; x++) for (int z = 0; z <= 99; z++) helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
		BlockPos start = helper.absolutePos(new BlockPos(20, 1, 50));
		BlockPos end = helper.absolutePos(new BlockPos(70, 2, 50));
		BoundingBox wall = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(45, 2, 35)), helper.absolutePos(new BlockPos(45, 4, 65)));
		List<RoadFinder.Cell> route = RoadFinder.route(level, start, pos -> pos.getX() == end.getX() && pos.getZ() == end.getZ(),
				pos -> true, Land.of(List.of(wall)), pos -> false, end);
		Checks.assertFalse(helper, route.isEmpty(), "a way is found");
		for (int i = 0; i < route.size(); i++) {
			BlockPos cell = route.get(i).pos();
			Checks.assertFalse(helper, cell.getX() == wall.minX() && cell.getZ() >= wall.minZ() && cell.getZ() <= wall.maxZ(), "never through the wall: " + cell);
			if (i > 0) {
				BlockPos last = route.get(i - 1).pos();
				Checks.assertTrue(helper, Math.abs(last.getX() - cell.getX()) + Math.abs(last.getZ() - cell.getZ()) == 1, "a step to a neighbour");
				Checks.assertTrue(helper, Math.abs(last.getY() - cell.getY()) <= 1, "at most a block up or down");
			}
		}
		Checks.assertTrue(helper, route.get(route.size() - 1).pos().getY() == end.getY(), "up on the terrace at the end");
		helper.succeed();
	}

	/** Over a stream a bridge: a deck a block over the water, railings either side, a pier of logs to the bed. */
	@GameTest(batch = "roads", template = "home_platform")
	public static void aRoadCrossesAStreamOnABridge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 40; x <= 42; x++) {
			for (int z = 0; z <= 99; z++) {
				helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 0, z), Blocks.WATER);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
			}
		}
		BlockPos start = helper.absolutePos(new BlockPos(30, 1, 50));
		BlockPos end = helper.absolutePos(new BlockPos(52, 1, 50));
		List<RoadFinder.Cell> route = RoadFinder.route(level, start, pos -> pos.equals(end), pos -> true, Land.of(List.of()), pos -> false, end);
		Checks.assertFalse(helper, route.isEmpty(), "a way over the water");
		long bridged = route.stream().filter(RoadFinder.Cell::water).count();
		Checks.assertTrue(helper, bridged == 3, "three cells of bridge, was " + bridged);
		helper.setBlock(new BlockPos(50, 2, 50), Blocks.BELL);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), helper.absolutePos(new BlockPos(50, 2, 50)));
		Roads roads = Roads.get(level.getServer());
		roads.networks().removeIf(old -> old.bell.closerThan(office.bell, TownRules.SPAN));
		Roads.Network network = roads.of(level.dimension(), office.bell);
		try {
			roads.lay(level, network, route);
			layAll(level, network);
			int z = 50;
			Checks.assertTrue(helper, route.stream().filter(RoadFinder.Cell::water).allMatch(cell -> cell.pos().getZ() == helper.absolutePos(new BlockPos(0, 0, z)).getZ()), "straight over");
			for (int x = 40; x <= 42; x++) {
				helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(x, 2, z));
				helper.assertBlockPresent(Blocks.WATER, new BlockPos(x, 1, z));
				helper.assertBlockPresent(Blocks.OAK_FENCE, new BlockPos(x, 3, z - 1));
				helper.assertBlockPresent(Blocks.OAK_FENCE, new BlockPos(x, 3, z + 1));
			}
			helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(41, 0, z - 1));
			helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(41, 1, z + 1));
			Checks.assertTrue(helper, network.cells.contains(helper.absolutePos(new BlockPos(41, 2, z))), "the deck is the road");
		} finally {
			roads.networks().remove(network);
			Mayors.get(level.getServer()).offices().remove(office);
		}
		helper.succeed();
	}

	/** A street over a stream: a deck three wide, railings outside it, lanterns at both ends. */
	@GameTest(batch = "roads", template = "home_platform")
	public static void aStreetCrossesAStreamOnAWideBridge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 40; x <= 43; x++) {
			for (int z = 0; z <= 99; z++) {
				helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 0, z), Blocks.WATER);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
			}
		}
		BlockPos start = helper.absolutePos(new BlockPos(30, 1, 50));
		BlockPos end = helper.absolutePos(new BlockPos(52, 1, 50));
		List<RoadFinder.Cell> route = RoadFinder.route(level, start, pos -> pos.equals(end), pos -> true, Land.of(List.of()), pos -> false, end);
		helper.setBlock(new BlockPos(50, 2, 50), Blocks.BELL);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), helper.absolutePos(new BlockPos(50, 2, 50)));
		Roads roads = Roads.get(level.getServer());
		roads.networks().removeIf(old -> old.bell.closerThan(office.bell, TownRules.SPAN));
		Roads.Network network = roads.of(level.dimension(), office.bell);
		try {
			roads.lay(level, network, route, "street");
			layAll(level, network);
			int z = 50;
			for (int x = 40; x <= 43; x++) {
				for (int w = -1; w <= 1; w++) helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(x, 2, z + w));
				helper.assertBlockPresent(Blocks.OAK_FENCE, new BlockPos(x, 3, z - 2));
				helper.assertBlockPresent(Blocks.OAK_FENCE, new BlockPos(x, 3, z + 2));
				helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, 3, z));
			}
			for (int x : new int[]{40, 43}) {
				helper.assertBlockPresent(Blocks.LANTERN, new BlockPos(x, 4, z - 2));
				helper.assertBlockPresent(Blocks.LANTERN, new BlockPos(x, 4, z + 2));
			}
		} finally {
			roads.networks().remove(network);
			Mayors.get(level.getServer()).offices().remove(office);
		}
		helper.succeed();
	}

	/**
	 * A road over a meadow is stone from the first day: the grass and flowers over it are cut, not left to pop off and lie
	 * about; a block's rise gets a half step; a lamp hangs its lantern from an arm over the way.
	 */
	@GameTest(batch = "roads", template = "home_platform")
	public static void aRoadOverAMeadowIsStoneWithStepsAndNothingLeftLying(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 10; x <= 60; x++) {
			for (int z = 45; z <= 55; z++) {
				boolean up = x > 35;
				helper.setBlock(new BlockPos(x, 1, z), up ? Blocks.DIRT : Blocks.GRASS_BLOCK);
				if (up) helper.setBlock(new BlockPos(x, 2, z), Blocks.GRASS_BLOCK);
				helper.setBlock(new BlockPos(x, up ? 3 : 2, z), (x + z) % 2 == 0 ? Blocks.POPPY : Blocks.SHORT_GRASS);
			}
		}
		// the chunks of a test far out are entity-ticking (RoadFinder's ground) a moment after it starts
		helper.runAfterDelay(20, () -> {
			BlockPos start = helper.absolutePos(new BlockPos(12, 1, 50));
			BlockPos end = helper.absolutePos(new BlockPos(58, 2, 50));
			List<RoadFinder.Cell> route = RoadFinder.route(level, start, pos -> pos.equals(end), pos -> true, Land.of(List.of()), pos -> false, end);
			Checks.assertFalse(helper, route.isEmpty(), "a way over the meadow " + RoadFinder.surface(level, start.getX(), start.getZ(), pos -> false) + " " + RoadFinder.surface(level, end.getX(), end.getZ(), pos -> false) + " " + RoadFinder.surface(level, start.getX() + 24, start.getZ(), pos -> false) + " " + RoadFinder.surface(level, start.getX() + 23, start.getZ(), pos -> false));
			helper.setBlock(new BlockPos(50, 2, 20), Blocks.BELL);
			Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), helper.absolutePos(new BlockPos(50, 2, 20)));
			Roads roads = Roads.get(level.getServer());
			roads.networks().removeIf(old -> old.bell.closerThan(office.bell, TownRules.SPAN));
			Roads.Network network = roads.of(level.dimension(), office.bell);
			try {
				roads.lay(level, network, route, "street");
				layAll(level, network);
				for (RoadFinder.Cell cell : route) {
					Checks.assertTrue(helper, Roads.roadSurface(level.getBlockState(cell.pos())) && !level.getBlockState(cell.pos()).is(Blocks.DIRT_PATH), "stone: " + level.getBlockState(cell.pos()));
					net.minecraft.world.level.block.state.BlockState over = level.getBlockState(cell.pos().above());
					Checks.assertTrue(helper, over.isAir() || over.getBlock() instanceof net.minecraft.world.level.block.SlabBlock, "nothing grows on the road: " + over);
				}
				Checks.assertTrue(helper, route.stream().anyMatch(cell -> level.getBlockState(cell.pos().above()).getBlock() instanceof net.minecraft.world.level.block.SlabBlock), "a half step up the rise");
				BoundingBox around = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(5, 0, 40)), helper.absolutePos(new BlockPos(65, 8, 60)));
				List<net.minecraft.world.entity.item.ItemEntity> lying = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, net.minecraft.world.phys.AABB.of(around));
				Checks.assertTrue(helper, lying.isEmpty(), "nothing lies about: " + lying.stream().map(item -> item.getItem().getItem() + "@" + helper.relativePos(item.blockPosition())).toList());
				boolean hanging = false;
				for (BlockPos pos : BlockPos.betweenClosed(around.minX(), around.minY(), around.minZ(), around.maxX(), around.maxY(), around.maxZ())) {
					net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
					hanging |= state.is(Blocks.LANTERN) && state.getValue(net.minecraft.world.level.block.LanternBlock.HANGING);
				}
				Checks.assertTrue(helper, hanging, "a lamp's lantern hangs over the way");
				// a later road joins it over the step's slab
				Checks.assertTrue(helper, route.stream().allMatch(cell -> RoadFinder.surface(level, cell.pos().getX(), cell.pos().getZ(), network.cells::contains) != null),
						"its cells are still road to the way finder: " + route.stream().filter(cell -> RoadFinder.surface(level, cell.pos().getX(), cell.pos().getZ(), network.cells::contains) == null)
							.map(cell -> helper.relativePos(cell.pos()) + " " + level.getBlockState(cell.pos()) + "/" + level.getBlockState(cell.pos().above()) + "/" + level.getBlockState(cell.pos().above(2))).toList());
			} finally {
				roads.networks().remove(network);
				Mayors.get(level.getServer()).offices().remove(office);
			}
			helper.succeed();
		});
	}

	@GameTest(batch = "roads", template = "home_platform")
	public static void aRoadStaysOnItsTownsLand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(new BlockPos(15, 1, 50));
		BlockPos b = helper.absolutePos(new BlockPos(85, 1, 50));
		Mayors mayors = Mayors.get(level.getServer());
		List<Mayors.Office> offices = List.of(mayors.officeFor(level.dimension(), a), mayors.officeFor(level.dimension(), b));
		try {
			BlockPos goal = helper.absolutePos(new BlockPos(75, 1, 50));
			List<RoadFinder.Cell> route = RoadFinder.route(level, a.east(2), pos -> pos.equals(goal), pos -> Towns.ours(level, a, pos),
					Land.of(List.of()), pos -> false, goal);
			Checks.assertTrue(helper, route.isEmpty(), "A's road does not run into B's land");
		} finally {
			mayors.offices().removeAll(offices);
		}
		helper.succeed();
	}

	@GameTest(batch = "roads", template = "home_platform", timeoutTicks = 4000)
	public static void aHouseIsJoinedToTheBellByHandAndNoHouseGoesOverTheRoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, 2500L);
		helper.setBlock(new BlockPos(50, 2, 50), Blocks.BELL);
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		long day = Family.day(level);
		// every batch stands on the same ground: an office an earlier batch left here (its residents counted) would
		// make this a big town, its plan a square and streets two hands cannot lay in time
		Mayors.get(level.getServer()).offices().removeIf(old -> old.bell.closerThan(bell, TownRules.SPAN));
		Roads.get(level.getServer()).networks().removeIf(old -> old.bell.closerThan(bell, TownRules.SPAN));
		VillageMoodSavedData.get(level).houses().removeIf(old -> old.bell.closerThan(bell, TownRules.SPAN));
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Villager owner = villager(helper, level, bell, 52, 52, true);
		owner.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, day - 5);
		HouseProject house = HomeProjects.start(level, owner, day);
		if (house == null) {
			Checks.fail(helper, "a site for the house");
			return;
		}
		BuildJob job = house.job(level);
		job.site = null;
		while (!job.done()) {
			job.skipDone(level);
			job.placeNext(level, null);
		}
		HomeProjects.finish(level, house);
		// the owner has no brain: left by the bell it may stand on the road's last cell for good
		BlockPos bed = house.bed(level);
		if (bed != null) owner.teleportTo(bed.getX() + 0.5, bed.getY() + 1, bed.getZ() + 0.5);
		Roads roads = Roads.get(level.getServer());
		// an earlier batch's test may have stood on this very ground: its roads are not this town's
		roads.networks().removeIf(old -> old.bell.closerThan(office.bell, TownRules.SPAN));
		Roads.Network network = roads.of(level.dimension(), office.bell);
		int planned = roads.plan(level, network, day, 8);
		Checks.assertTrue(helper, planned >= 1 && !network.segments.isEmpty(), "a road from the door is planned: " + planned + " for " + house.schematicName() + " " + house.rotation + " at " + house.origin.toShortString());
		// the stretch's lamps are bought already (no stall here); a trodden path costs nothing
		for (Roads.Segment segment : network.segments) {
			segment.job().bill(level, Integer.MAX_VALUE).forEach(segment.site::add);
		}
		List<BlockPos> cells = List.copyOf(network.cells);
		// the door's road (a stall keeper arriving meanwhile starts a street of its own, bought at its stall)
		java.util.Set<Integer> first = new java.util.HashSet<>(network.segments.stream().map(s -> s.id).toList());
		Villager hand = villager(helper, level, bell, 48, 48, false);
		Villager other = villager(helper, level, bell, 47, 52, false);
		hand.setNoAi(false);
		other.setNoAi(false);
		hand.refreshBrain(level);
		other.refreshBrain(level);
		hand.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		other.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		helper.succeedWhen(() -> {
			// work hours all the while
			if (com.vnap.Mc.dayTime(level) % 24000L > 8500L) com.vnap.Mc.setDayTime(level, 2500L);
			// a keeper arriving meanwhile begins its shop house, and a house takes hands before a road: not this test's
			VillageMoodSavedData.get(level).houses().removeIf(h -> !h.done && h != house && h.bell.closerThan(bell, TownRules.SPAN));
			// a flower of the meadow the yard was billed as standing may go under the road's clearing: in a town the
			// builder buys one at the stall, here there is none
			for (Roads.Segment segment : network.segments) {
				if (first.contains(segment.id)) segment.site.lacking(segment.job().bill(level, Integer.MAX_VALUE)).forEach(segment.site::add);
			}
			Checks.assertTrue(helper, network.segments.stream().noneMatch(s -> first.contains(s.id)), "still laying: " + network.segments.stream()
					.filter(s -> first.contains(s.id)).map(s -> s.label + " " + s.job().remaining() + " left, next " + s.job().nextPos().toShortString()
							+ " " + s.job().nextState() + (s.job().lacksMaterials() ? " lacks" : "") + " paused " + s.pausedDay).toList()
					+ "; hand " + hand.blockPosition().toShortString() + " " + com.vnap.village.life.LifeBrain.busy(hand) + ", other " + other.blockPosition().toShortString());
			long paths = cells.stream().filter(cell -> level.getBlockState(cell).is(Blocks.COBBLESTONE) || level.getBlockState(cell).is(Blocks.MOSSY_COBBLESTONE)).count();
			Checks.assertTrue(helper, paths >= cells.size() - 1, "the road is laid: " + paths + " of " + cells.size());
			// nothing is planned over the road afterwards
			List<BoundingBox> reserved = com.vnap.village.town.Land.around(level, bell).claims().stream().map(com.vnap.village.town.Land.Claim::box).toList();
			Checks.assertTrue(helper, reserved.stream().anyMatch(box -> box.isInside(cells.get(0))), "the road is kept free of houses");
			Villager next = villager(helper, level, bell, 50, 46, true);
			next.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, day - 5);
			HouseProject second = HomeProjects.start(level, next, day + 1);
			if (second != null) {
				BoundingBox box = second.footprint(level);
				Checks.assertTrue(helper, cells.stream().noneMatch(box::isInside), "the next house leaves the road alone");
			}
			VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
			roads.networks().remove(network);
			Mayors.get(level.getServer()).offices().remove(office);
		});
	}

	@GameTest(batch = "roads", template = "home_platform")
	public static void aHalfFullTownPavesItsPathsAndTheRoadsAreSaved(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(50, 2, 50), Blocks.BELL);
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Roads roads = Roads.get(level.getServer());
		Roads.Network network = roads.of(level.dimension(), office.bell);
		try {
			for (int x = 30; x < 46; x++) {
				BlockPos cell = helper.absolutePos(new BlockPos(x, 1, 50));
				level.setBlock(cell, Blocks.DIRT_PATH.defaultBlockState(), 3);
				network.cells.add(cell);
			}
			for (int i = 0; i < MoodConfig.maxVillagers() / 2 + 1; i++) office.residents.put(UUID.randomUUID(), Family.day(level));
			roads.plan(level, network, Family.day(level), 0);
			Checks.assertTrue(helper, network.tier == 2 && !network.segments.isEmpty(), "the paths are to be paved: tier " + network.tier);
			BlockPos first = helper.absolutePos(new BlockPos(30, 1, 50));
			Checks.assertTrue(helper, network.segments.get(0).spec.blocks.stream().noneMatch(block -> block.state().is(Blocks.DIRT_PATH)),
					"with stone, not a path again");
			network.segments.get(0).job();
			CompoundTag saved = roads.save(new CompoundTag(), level.registryAccess());
			Roads back = Roads.load(saved, level.registryAccess());
			Roads.Network again = back.networks().stream().filter(n -> n.bell.equals(network.bell)).findFirst().orElse(null);
			Checks.assertTrue(helper, again != null && again.cells.equals(network.cells) && again.tier == 2
					&& again.segments.size() == network.segments.size(), "the roads come back as they were");
			if (again != null) {
				Checks.assertTrue(helper, again.segments.get(0).spec.blocks.size() == network.segments.get(0).spec.blocks.size()
						&& again.segments.get(0).origin.equals(network.segments.get(0).origin), "with their stretches");
			}
			Checks.assertTrue(helper, network.cells.contains(first), "the old path is still the network");
		} finally {
			roads.networks().remove(network);
			Mayors.get(level.getServer()).offices().remove(office);
		}
		helper.succeed();
	}

	/** Every plan in every turn: the road finds its front door (a turned door, a porch, a door on the far side). */
	@GameTest(batch = "roads", template = "home_platform", timeoutTicks = 400)
	public static void aRoadReachesEveryPlansDoorInEveryTurn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(50, 2, 50), Blocks.BELL);
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Roads roads = Roads.get(level.getServer());
		roads.networks().removeIf(old -> old.bell.closerThan(office.bell, TownRules.SPAN));
		List<String> failed = new java.util.ArrayList<>();
		BlockPos origin = helper.absolutePos(new BlockPos(25, 1, 30));
		for (String plan : HomeProjects.PLANS) {
			for (net.minecraft.world.level.block.Rotation turn : net.minecraft.world.level.block.Rotation.values()) {
				HouseProject house = new HouseProject(bell.asLong(), bell, UUID.randomUUID(), plan, "plains", origin, turn, 0L);
				BuildJob job = house.job(level);
				job.site = null;
				while (!job.done()) {
					job.skipDone(level);
					if (!job.done()) job.placeNext(level, null);
				}
				house.done = true;
				VillageMoodSavedData.get(level).houses().add(house);
				roads.networks().removeIf(old -> old.bell.closerThan(office.bell, TownRules.SPAN));
				Roads.Network network = roads.of(level.dimension(), office.bell);
				if (roads.plan(level, network, Family.day(level), 4) < 1 && network.cells.isEmpty()) failed.add(plan + " " + turn);
				VillageMoodSavedData.get(level).houses().remove(house);
				roads.networks().remove(network);
				BoundingBox box = house.footprint(level).inflatedBy(1);
				for (BlockPos pos : BlockPos.betweenClosed(box.minX(), origin.getY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
					level.setBlock(pos, (pos.getY() == origin.getY() ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState(), 2);
				}
			}
		}
		Mayors.get(level.getServer()).offices().remove(office);
		Checks.assertTrue(helper, failed.isEmpty(), "no road from the door of " + failed);
		helper.succeed();
	}

	/** A dozen people pave a square round the bell with lamps; half a town widens it with a well, benches and flowers. */
	@GameTest(batch = "roads", template = "home_platform")
	public static void aGrowingTownLaysItsSquareRoundTheBell(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(50, 2, 50), Blocks.BELL);
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		// something standing on the square's ground: left alone
		helper.setBlock(new BlockPos(52, 2, 51), Blocks.CHEST);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Roads roads = Roads.get(level.getServer());
		roads.networks().removeIf(old -> old.bell.closerThan(office.bell, TownRules.SPAN));
		Roads.Network network = roads.of(level.dimension(), office.bell);
		try {
			long day = Family.day(level);
			for (int i = 0; i < MoodConfig.maxVillagers() / 6 + 1; i++) office.residents.put(UUID.randomUUID(), day);
			roads.plan(level, network, day, 0);
			Checks.assertTrue(helper, network.plaza == 1 && !network.segments.isEmpty(), "a dozen people: a square is due, " + network.plaza);
			layAll(level, network);
			Checks.assertTrue(helper, level.getBlockState(bell.offset(1, -1, 1)).is(Blocks.STONE_BRICKS), "paved round the bell");
			Checks.assertTrue(helper, level.getBlockState(helper.absolutePos(new BlockPos(52, 2, 51))).is(Blocks.CHEST)
					&& !level.getBlockState(helper.absolutePos(new BlockPos(52, 1, 51))).is(Blocks.STONE_BRICKS), "the chest and its ground stay");
			Checks.assertTrue(helper, level.getBlockState(bell.offset(4, 2, 4)).is(Blocks.LANTERN), "a lamp at a corner");
			for (int i = 0; i < MoodConfig.maxVillagers() / 2; i++) office.residents.put(UUID.randomUUID(), day);
			roads.plan(level, network, day, 0);
			Checks.assertTrue(helper, network.plaza == 2, "half a town: the big square");
			layAll(level, network);
			int water = 0;
			int seats = 0;
			int pots = 0;
			for (BlockPos pos : BlockPos.betweenClosed(bell.offset(-6, -1, -6), bell.offset(6, 3, 6))) {
				if (level.getBlockState(pos).is(Blocks.WATER)) water++;
				if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.StairBlock) seats++;
				if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock) pots++;
			}
			Checks.assertTrue(helper, water == 1 && seats >= 6 && pots >= 8, "a well, benches and flowers: " + water + " water, " + seats + " seats, " + pots + " pots; features " + network.features.size() + " " + network.features.stream().limit(12).map(BlockPos::toShortString).toList() + " bell " + bell.toShortString());
			Checks.assertTrue(helper, com.vnap.village.town.Land.around(level, bell).claims().stream().map(com.vnap.village.town.Land.Claim::box).anyMatch(box -> box.isInside(bell.offset(4, 0, 0)) || box.isInside(bell.offset(-4, 0, 0))
					|| box.isInside(bell.offset(0, 0, 4)) || box.isInside(bell.offset(0, 0, -4))), "no house goes over the well");
		} finally {
			roads.networks().remove(network);
			Mayors.get(level.getServer()).offices().remove(office);
		}
		helper.succeed();
	}

	/**
	 * A full town walls itself in: a closed ring round its houses, towers, a gate where its road runs out with a
	 * crossbow each side, a stream dammed under it, a dry ditch outside.
	 */
	@GameTest(batch = "roads", template = "home_platform", timeoutTicks = 200)
	public static void aFullTownWallsItselfInWithTowersAndAGate(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(50, 2, 50), Blocks.BELL);
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Roads roads = Roads.get(level.getServer());
		roads.networks().removeIf(old -> old.bell.closerThan(office.bell, TownRules.SPAN));
		Roads.Network network = roads.of(level.dimension(), office.bell);
		// a full town lays out a churchyard at its edge, and the wall goes round that too: on this 100-block platform it
		// would run off the edge, so this town has its churchyard far off already
		network.cemetery = office.bell.offset(0, 0, 1000);
		try {
			// the town: a road out east from the bell, twenty blocks long
			for (int x = 53; x <= 72; x++) {
				BlockPos cell = helper.absolutePos(new BlockPos(x, 1, 50));
				level.setBlock(cell, Blocks.DIRT_PATH.defaultBlockState(), 3);
				network.cells.add(cell);
			}
			// a stream two deep across the line of the north wall (z 46: the bell's box four out)
			for (int x = 58; x <= 60; x++) {
				for (int z = 20; z <= 48; z++) {
					helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
					helper.setBlock(new BlockPos(x, 0, z), Blocks.WATER);
					helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
				}
			}
			long day = Family.day(level);
			for (int i = 0; i < MoodConfig.maxVillagers(); i++) office.residents.put(UUID.randomUUID(), day);
			network.plaza = 2;
			roads.plan(level, network, day, 0);
			Checks.assertTrue(helper, network.wall == 1 && !network.segments.isEmpty(), "a full town lays out its wall: " + network.wall + ", " + com.vnap.village.town.Wall.lastRefusal);
			layAll(level, network);
			// a closed ring: walking round the box the wall stands everywhere but at the gate
			int stone = 0;
			int towers = 0;
			for (BlockPos column : network.wallCells) {
				if (level.getBlockState(column.above(2)).is(Blocks.STONE_BRICKS)) stone++;
				if (level.getBlockState(column.above(5)).is(Blocks.STONE_BRICKS)) towers++;
			}
			Checks.assertTrue(helper, stone >= network.wallCells.size() - 12, "the wall stands round but at its four gates: " + stone + " of " + network.wallCells.size());
			Checks.assertTrue(helper, towers >= 4 * 9, "towers at the corners at least: " + towers / 9);
			Checks.assertTrue(helper, network.turrets.size() >= 8 && network.turrets.stream().allMatch(pos -> level.getBlockState(pos).is(com.vnap.block.LifeBlocks.ARROW_TURRET)
					&& level.getBlockState(pos.below(TownRules.WALL_HEIGHT)).isAir()), "crossbows over the gates only: " + network.turrets.size() + " " + network.turrets.stream().map(pos -> pos.toShortString() + "=" + level.getBlockState(pos).getBlock() + "/" + level.getBlockState(pos.below(TownRules.WALL_HEIGHT)).getBlock()).toList());
			// the stream is dammed: stone to its bed under the wall, the wall over it
			int streamX = helper.absolutePos(new BlockPos(59, 0, 0)).getX();
			BlockPos dam = network.wallCells.stream().filter(c -> c.getX() == streamX && c.getZ() < bell.getZ()).findFirst().orElse(BlockPos.ZERO).below();
			Checks.assertTrue(helper, level.getBlockState(dam).is(Blocks.COBBLESTONE) && level.getBlockState(dam.above()).is(Blocks.COBBLESTONE)
					&& level.getBlockState(dam.above(3)).is(Blocks.STONE_BRICKS), "the stream dammed under the wall: " + level.getBlockState(dam) + " at " + dam);
			// the ditch: dug two deep, but not by the stream nor in front of a gate
			Checks.assertTrue(helper, network.moat.size() > 40 && network.moat.stream().allMatch(cell -> level.getBlockState(cell).isAir() && level.getBlockState(cell.above()).isAir()),
					"a ditch outside the wall: " + network.moat.size());
			Checks.assertTrue(helper, network.moat.stream().noneMatch(cell -> BlockPos.betweenClosedStream(cell.offset(-1, 0, -1), cell.offset(1, 1, 1))
					.anyMatch(near -> !level.getFluidState(near).isEmpty())), "none beside the stream: it would run in");
			Checks.assertTrue(helper, network.moat.stream().noneMatch(cell -> cell.getZ() == bell.getZ()), "none across the gates in line with the bell");
			// the road's way out is open two high under an arch
			// a gate in the middle of the east side, in line with the bell
			BlockPos gate = network.wallCells.stream().filter(column -> column.getZ() == bell.getZ() && column.getX() > bell.getX()).findFirst().orElse(null);
			Checks.assertTrue(helper, gate != null && level.getBlockState(gate.above()).isAir() && level.getBlockState(gate.above(2)).isAir()
					&& level.getBlockState(gate.above(3)).is(Blocks.STONE_BRICKS), "a gate in line with the bell: " + gate);
			Checks.assertTrue(helper, com.vnap.village.town.Land.around(level, bell).claims().stream().map(com.vnap.village.town.Land.Claim::box).anyMatch(box -> network.wallCells.stream().anyMatch(box::isInside)), "no house on the wall");
			roads.plan(level, network, day + 1, 0);
			// (the tavern the full town built gets its sign the next day: Signage)
			Checks.assertTrue(helper, network.segments.stream().noneMatch(seg -> seg.label.equals("wall") || seg.label.equals("ditch")),
					"the wall is laid out once: " + network.segments.stream().map(seg -> seg.label).toList());
		} finally {
			roads.networks().remove(network);
			Mayors.get(level.getServer()).offices().remove(office);
		}
		helper.succeed();
	}

	/** A builder on the bank fills a stream's column to its bed, stone by stone, and does not drown at it. */
	@GameTest(template = "platform", timeoutTicks = 1200)
	public static void aBuilderDamsAStreamFromTheBank(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 7; x <= 9; x++) {
			for (int z = 4; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, -4, z), Blocks.STONE);
				for (int y = -3; y <= 1; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
			}
		}
		List<com.vnap.village.script.SchematicSpec.PlacedBlock> blocks = new java.util.ArrayList<>();
		for (int y = -3; y <= 1; y++) blocks.add(new com.vnap.village.script.SchematicSpec.PlacedBlock(helper.absolutePos(new BlockPos(8, y, 5)), Blocks.COBBLESTONE.defaultBlockState()));
		BuildJob job = BuildJob.all("dam", new com.vnap.village.script.SchematicSpec(1, 1, 1, List.of(), blocks, BlockPos.ZERO), BlockPos.ZERO,
				net.minecraft.world.level.block.Rotation.NONE, BuildJob.ALL_GROUND);
		Villager builder = helper.spawn(EntityType.VILLAGER, 5, 2, 5);
		com.vnap.village.life.Hands hands = new com.vnap.village.life.Hands();
		helper.onEachTick(() -> {
			if (!job.done()) hands.tick(level, builder, job);
			Checks.assertTrue(helper, builder.getHealth() >= builder.getMaxHealth(), "nobody drowns at it");
		});
		helper.succeedWhen(() -> {
			Checks.assertTrue(helper, job.done(), "the column is filled: " + job.remaining() + " left");
			for (int y = -3; y <= 1; y++) helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(8, y, 5));
		});
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

	private static Villager villager(GameTestHelper helper, ServerLevel level, BlockPos bell, int x, int z, boolean noAi) {
		Villager villager = helper.spawn(EntityType.VILLAGER, x, 2, z);
		villager.setNoAi(noAi);
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		return villager;
	}
}
