package com.vnap.gametest;

import com.vnap.dialogue.Family;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.Graveyard;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Placements;
import com.vnap.village.town.Cemetery;
import com.vnap.village.town.Roads;
import com.vnap.village.town.TownRules;
import com.vnap.village.town.Yards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.WallBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** The town's grounds: its churchyard out at the edge, graves that give way in time, the yards round its houses. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class TownGroundsGameTests {
	private static final BlockPos BELL = new BlockPos(50, 2, 50);

	private TownGroundsGameTests() {
	}

	private static Roads.Network town(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BELL, Blocks.BELL);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), helper.absolutePos(BELL));
		return Roads.get(level.getServer()).of(level.dimension(), office.bell);
	}

	private static void lay(ServerLevel level, Roads.Network network, String label) {
		for (Roads.Segment segment : List.copyOf(network.segments)) {
			if (!segment.label.equals(label)) continue;
			BuildJob job = segment.job();
			job.site = null;
			while (!job.done()) {
				job.skipDone(level);
				if (!job.done()) job.placeNext(level, null);
			}
			Roads.get(level.getServer()).finish(level, segment);
		}
	}

	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 200)
	public static void aChurchyardIsLaidAtTheTownsEdgeAndTakesTheGraves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		helper.runAfterDelay(10, () -> {
			Roads roads = Roads.get(level.getServer());
			Checks.assertTrue(helper, Cemetery.plan(level, roads, network), "a churchyard is sited");
			BlockPos front = network.cemetery;
			if (front == null) return;
			double out = Math.sqrt(front.distSqr(network.bell));
			Checks.assertTrue(helper, out >= TownRules.SQUARE_LARGE + 12, "out at the edge, not by the square: " + (int) out);
			Checks.assertTrue(helper, network.segments.stream().anyMatch(segment -> segment.label.equals("cemetery")), "laid by the town's builders");
			lay(level, network, "cemetery");
			List<BlockPos> plots = Cemetery.plots(level, network);
			Checks.assertTrue(helper, plots.size() == 12, "twelve plots, " + plots.size());
			int fences = 0;
			for (BlockPos plot : plots) for (BlockPos near : BlockPos.betweenClosed(plot.offset(-6, -1, -10), plot.offset(6, 1, 10))) {
				if (level.getBlockState(near).getBlock() instanceof FenceBlock) fences++;
			}
			Checks.assertTrue(helper, fences > 0, "fenced round");
			Graveyard.Grave grave = Graveyard.bury(level, UUID.randomUUID(), network.bell, "Anna", "1 - 9");
			Checks.assertTrue(helper, grave != null && plots.contains(grave.pos), "the dead are buried on a plot of the churchyard");
			Checks.assertTrue(helper, Placements.pending(level, grave == null ? BlockPos.ZERO : grave.pos), "the stone is set by hand");
			Graveyard.get(level.getServer()).graves().removeIf(one -> one.bell.closerThan(network.bell, TownRules.SPAN));
			helper.succeed();
		});
	}

	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 200)
	public static void oldGravesGiveWayAndAFullChurchyardReusesTheOldest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		helper.runAfterDelay(10, () -> {
			Roads roads = Roads.get(level.getServer());
			Cemetery.plan(level, roads, network);
			lay(level, network, "cemetery");
			Graveyard yard = Graveyard.get(level.getServer());
			int plots = Cemetery.plots(level, network).size();
			for (int i = 0; i < plots; i++) {
				Graveyard.Grave grave = Graveyard.bury(level, UUID.randomUUID(), network.bell, "N" + i, "");
				if (grave == null) {
					Checks.fail(helper, "plot " + i + " for a grave");
					return;
				}
				grave.funeral = true;
				grave.day = Family.day(level) - TownRules.GRAVE_REUSE_DAYS - plots + i;
			}
			Graveyard.Grave oldest = yard.graves().stream().filter(one -> one.name.equals("N0")).findFirst().orElse(null);
			BlockPos oldestPos = oldest == null ? BlockPos.ZERO : oldest.pos;
			Graveyard.Grave next = Graveyard.bury(level, UUID.randomUUID(), network.bell, "Late", "");
			Checks.assertTrue(helper, next != null && next.pos.equals(oldestPos), "a full churchyard gives the oldest plot to the newly dead");
			Checks.assertTrue(helper, yard.graves().stream().noneMatch(one -> one.name.equals("N0")), "and the old grave is gone");
			// graves past their days are taken away (nobody near here: at once)
			for (Graveyard.Grave grave : yard.graves()) {
				if (grave.bell.closerThan(network.bell, TownRules.SPAN)) {
					grave.funeral = true;
					grave.day = Family.day(level) - TownRules.GRAVE_DAYS - 1;
				}
			}
			Graveyard.ageNow(level);
			Checks.assertTrue(helper, yard.graves().stream().noneMatch(one -> one.bell.closerThan(network.bell, TownRules.SPAN)), "old graves give way in time");
			helper.succeed();
		});
	}

	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 200)
	public static void aYardIsLaidOnlyWhenTheFamilyCanPayAndThereIsRoom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		Villager owner = helper.spawn(EntityType.VILLAGER, BELL.offset(2, 0, 2));
		owner.setNoAi(true);
		owner.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), network.bell));
		helper.runAfterDelay(10, () -> {
			HouseProject house = HomeProjects.start(level, owner, Family.day(level));
			if (house == null) {
				Checks.fail(helper, "a house");
				return;
			}
			HomeProjects.buildNow(level, house);
			Roads roads = Roads.get(level.getServer());
			network.segments.removeIf(segment -> !segment.label.equals("yard"));
			long day = Family.day(level);
			Family.state(owner).coins = 0;
			Yards.plan(level, roads, network, day);
			Checks.assertTrue(helper, network.segments.stream().noneMatch(segment -> segment.label.equals("yard")), "no yard on credit");
			Family.state(owner).coins = 4000;
			Yards.plan(level, roads, network, day + 1);
			Checks.assertTrue(helper, network.segments.stream().noneMatch(segment -> segment.label.equals("yard")), "not looked at again the next day");
			// a door that is no door of the house's (a neighbour's, a hillside house's upper one) by where the ring goes
			net.minecraft.world.level.levelgen.structure.BoundingBox footprint = house.footprint(level);
			BlockPos other = new BlockPos(footprint.maxX() + 1, house.origin.getY(), (footprint.minZ() + footprint.maxZ()) / 2);
			level.setBlock(other, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST), 2);
			level.setBlock(other.above(), Blocks.SPRUCE_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST)
					.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
			Yards.plan(level, roads, network, day + 10);
			Checks.assertTrue(helper, network.segments.stream().anyMatch(segment -> segment.label.equals("yard")), "paid for, the yard is laid by hand");
			Checks.assertTrue(helper, Family.state(owner).coins < 4000, "out of the family's purse");
			lay(level, network, "yard");
			int ring = 0;
			net.minecraft.world.level.levelgen.structure.BoundingBox box = house.footprint(level);
			for (BlockPos pos : BlockPos.betweenClosed(box.minX() - 3, box.minY() - 1, box.minZ() - 3, box.maxX() + 3, box.minY() + 3, box.maxZ() + 3)) {
				var state = level.getBlockState(pos);
				if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof LeavesBlock) ring++;
			}
			Checks.assertTrue(helper, ring > 8, "a fence, hedge or wall round it, " + ring);
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					var state = level.getBlockState(new BlockPos(footprint.maxX() + 3, house.origin.getY() + dy, other.getZ() + dz));
					Checks.assertFalse(helper, state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof LeavesBlock,
							"the ring leaves a way out of every door about it, not only the house's");
				}
			}
			helper.succeed();
		});
	}

	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 200)
	public static void aTownWithItsSpireGetsAFountainAndItsHeroesStatue(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		Roads roads = Roads.get(level.getServer());
		network.segments.clear();
		// ground enough for the large square and its fountain (the template is smaller)
		for (BlockPos pos : BlockPos.betweenClosed(network.bell.offset(-9, -3, -9), network.bell.offset(9, -1, 9))) {
			level.setBlockAndUpdate(pos, pos.getY() == network.bell.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState());
		}
		for (BlockPos pos : BlockPos.betweenClosed(network.bell.offset(-9, 0, -9), network.bell.offset(9, 4, 9))) {
			if (!pos.equals(network.bell)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		}
		for (int due = 1; due <= 3; due++) {
			com.vnap.village.town.Plaza.plan(level, roads, network, due);
			lay(level, network, "square");
			lay(level, network, com.vnap.village.town.Plaza.STATUE);
		}
		helper.runAfterDelay(40, () -> {
			BlockPos bell = network.bell;
			int water = 0;
			int chiselled = 0;
			for (BlockPos pos : BlockPos.betweenClosed(bell.offset(-8, -2, -8), bell.offset(8, 5, 8))) {
				if (level.getBlockState(pos).is(Blocks.WATER)) water++;
				if (level.getBlockState(pos).is(Blocks.CHISELED_STONE_BRICKS)) chiselled++;
			}
			Checks.assertTrue(helper, water == 9, "the fountain's basin and the well, and no water run out over the square: " + water + " "
					+ java.util.stream.StreamSupport.stream(BlockPos.betweenClosed(bell.offset(-8, -2, -8), bell.offset(8, 5, 8)).spliterator(), false)
							.filter(pos -> level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource()).map(pos -> pos.subtract(bell).toShortString()).toList()
					+ ", square " + network.plaza + ", ground "
					+ helper.getLevel().getBlockState(bell.below()) + " / " + helper.getLevel().getBlockState(bell.offset(-4, -1, 0)) + " / " + helper.getLevel().getBlockState(bell.offset(-4, 0, 0)));
			Checks.assertTrue(helper, chiselled >= 2, "the fountain's pillar and the statue's plinth: " + chiselled + ", stands "
					+ level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, new net.minecraft.world.phys.AABB(bell).inflate(10)).size()
					+ ", andesite " + java.util.stream.StreamSupport.stream(BlockPos.betweenClosed(bell.offset(-8, -2, -8), bell.offset(8, 5, 8)).spliterator(), false)
							.filter(pos -> level.getBlockState(pos).is(Blocks.POLISHED_ANDESITE) && pos.getY() >= bell.getY()).count()
					+ ", fountain " + java.util.stream.StreamSupport.stream(BlockPos.betweenClosed(bell.offset(-4, -1, 0), bell.offset(-2, 3, 0)).spliterator(), false)
							.map(pos -> pos.subtract(bell).toShortString() + "=" + level.getBlockState(pos).getBlock().getDescriptionId().replace("block.minecraft.", "")).toList());
			List<net.minecraft.world.entity.decoration.ArmorStand> statues = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
					new net.minecraft.world.phys.AABB(bell).inflate(10), stand -> com.vnap.Mc.tags(stand).contains("vnap_statue"));
			Checks.assertTrue(helper, statues.size() == 1 && statues.get(0).getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() instanceof com.vnap.spire.gear.GearItem,
					"a hero in the Builders' armour stands on the plinth: " + statues.size());
			statues.forEach(net.minecraft.world.entity.Entity::discard);
			helper.succeed();
		});
	}

	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 200)
	public static void aHomeGrowsItsComfortsStepByStep(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		Villager owner = helper.spawn(EntityType.VILLAGER, BELL.offset(2, 0, 2));
		owner.setNoAi(true);
		owner.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), network.bell));
		helper.runAfterDelay(10, () -> {
			HouseProject house = HomeProjects.start(level, owner, Family.day(level));
			if (house == null) {
				Checks.fail(helper, "a house");
				return;
			}
			HomeProjects.buildNow(level, house);
			Roads roads = Roads.get(level.getServer());
			network.segments.clear();
			List<String> said = new java.util.ArrayList<>();
			for (int i = 0; i < 6; i++) said.add(com.vnap.village.town.Comfort.debugNear(level, roads, network, house.origin));
			for (String label : List.of("comfort_porch", "comfort_garden", "comfort_shed", "comfort_rooms", "comfort_greenhouse", "comfort_orchard")) lay(level, network, label);
			net.minecraft.world.level.levelgen.structure.BoundingBox box = house.footprint(level);
			int glass = 0;
			int blossom = 0;
			int lanterns = 0;
			for (BlockPos pos : BlockPos.betweenClosed(box.minX() - 6, box.minY() - 1, box.minZ() - 6, box.maxX() + 6, box.maxY() + 2, box.maxZ() + 6)) {
				var state = level.getBlockState(pos);
				if (state.is(Blocks.GLASS)) glass++;
				if (state.is(Blocks.FLOWERING_AZALEA_LEAVES)) blossom++;
				if (state.is(Blocks.LANTERN)) lanterns++;
			}
			Checks.assertTrue(helper, glass >= 3, "a glasshouse by a side wall: " + glass + " " + said);
			Checks.assertTrue(helper, blossom >= 9, "a flowering tree in the yard: " + blossom + " " + said);
			Checks.assertTrue(helper, lanterns >= 2, "lanterns under the porch and in the room: " + lanterns + " " + said);
			helper.succeed();
		});
	}

	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 100)
	public static void aBuriedBellIsHungAnewAndTheTownGoesWithIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		BlockPos from = network.bell;
		Villager villager = helper.spawn(EntityType.VILLAGER, BELL.offset(2, 0, 2));
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), from));
		BlockPos to = helper.absolutePos(BELL.offset(10, 0, 0));
		level.setBlockAndUpdate(to, Blocks.BELL.defaultBlockState());
		com.vnap.village.town.BellMove.move(level, Roads.get(level.getServer()), network, to);
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), to);
		Checks.assertTrue(helper, network.bell.equals(to) && office != null && office.bell.equals(to), "the town and its office go to the new bell");
		Checks.assertTrue(helper, villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).map(GlobalPos::pos).filter(to::equals).isPresent(),
				"its villagers meet at the new bell");
		Checks.assertFalse(helper, level.getBlockState(from).is(Blocks.BELL), "the buried one is taken down");
		helper.succeed();
	}

	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 400)
	public static void aWaitingBedTakesTheCellOfItsHeadToo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos foot = helper.absolutePos(BELL.offset(-6, 0, 6));
		Placements.add(level, foot, Blocks.WHITE_BED.defaultBlockState()
				.setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST), null);
		try {
			// a second bed whose foot went there would find no room to go in, and another be bought the next day
			Checks.assertTrue(helper, Placements.pending(level, foot) && Placements.pending(level, foot.east()), "both cells of the bed are taken");
			Checks.assertTrue(helper, !Placements.pending(level, foot.west()), "the cell behind it is not");
		} finally {
			Placements.get(level.getServer()).jobs().removeIf(job -> job.pos().equals(foot));
		}
		helper.succeed();
	}

	// every test of a run shares one light engine: with all of them on one server its queue takes hundreds of ticks
	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 1600)
	public static void theMayorSetsABedForEveryHomelessVillager(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		Villager owner = helper.spawn(EntityType.VILLAGER, BELL.offset(2, 0, 2));
		owner.setNoAi(true);
		owner.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), network.bell));
		helper.runAfterDelay(10, () -> {
			HouseProject house = HomeProjects.start(level, owner, Family.day(level));
			if (house == null) {
				Checks.fail(helper, "a house");
				return;
			}
			HomeProjects.buildNow(level, house);
			net.minecraft.world.level.levelgen.structure.BoundingBox box = house.footprint(level);
			List<Villager> homeless = new java.util.ArrayList<>();
			for (int i = 0; i < 2; i++) {
				Villager one = helper.spawn(EntityType.VILLAGER, BELL.offset(-3 - i, 0, -3));
				one.setNoAi(true);
				one.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), network.bell));
				one.getBrain().eraseMemory(MemoryModuleType.HOME);
				homeless.add(one);
			}
			LightWait.after(helper, level, house.footprint(level), () -> {
				com.vnap.village.town.Shelter.beds(level, network);
				Checks.assertTrue(helper, com.vnap.village.home.Placements.pendingBeds(level, network.bell, 64) == 0, "no mayor, no beds");
				Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), network.bell);
				if (office == null) {
					Checks.fail(helper, "an office");
					return;
				}
				office.mayor = owner.getUUID();
				// the house stands empty (its owner moved on): beds for the homeless go only into a house nobody lives
				// in. Right before the mayor looks: while the light was worked out the owner's family tick took its bed back
				level.getPoiManager().getInRange(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME), box.getCenter(), 24,
								net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.IS_OCCUPIED)
						.map(net.minecraft.world.entity.ai.village.poi.PoiRecord::getPos).filter(box::isInside).toList()
						.forEach(bed -> level.getPoiManager().release(bed));
				owner.getBrain().eraseMemory(MemoryModuleType.HOME);
				String before = "free " + HomeProjects.freeBeds(level, network.bell, homeless) + ", pending " + com.vnap.village.home.Placements.pendingBeds(level, network.bell, 64)
						+ ", rooms " + com.vnap.village.town.Shelter.why(level, homeless.get(0));
				com.vnap.village.town.Shelter.beds(level, network);
				String after = ", ordered " + com.vnap.village.home.Placements.pendingBeds(level, network.bell, 64);
				com.vnap.village.home.Placements.Job job;
				while ((job = com.vnap.village.home.Placements.forHands(level, owner, 200, pos -> false)) != null) {
					com.vnap.village.home.Placements.place(level, job);
				}
				long free = level.getPoiManager().getInRange(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME), network.bell, 64,
						net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE).count();
				Checks.assertTrue(helper, free >= homeless.size(), "a free bed for each of the homeless, " + free + " (" + before + after + ")");
				Checks.assertTrue(helper, homeless.stream().allMatch(one -> HomeProjects.freeBed(level, one, network.bell) != null), "each can go for one");
				com.vnap.village.town.Shelter.beds(level, network);
				Checks.assertTrue(helper, com.vnap.village.home.Placements.pendingBeds(level, network.bell, 64) == 0, "no more once there are enough: "
						+ com.vnap.village.home.Placements.get(level.getServer()).jobs().stream().filter(one -> one.pos().closerThan(network.bell, 64))
								.map(one -> one.pos().subtract(network.bell).toShortString() + " by " + (owner.getUUID().equals(one.by()) ? "mayor" : one.by())).toList()
						+ "; free " + free + ", reach " + com.vnap.village.home.Towns.reach(level, network.bell) + ", homeless here "
						+ com.vnap.village.home.Towns.people(level, network.bell, one -> !one.isBaby() && network.bell.equals(com.vnap.village.home.Towns.bellOf(one))
								&& !one.getBrain().hasMemoryValue(MemoryModuleType.HOME) && !HomeProjects.claimed(one)).stream()
								.map(one -> one.blockPosition().subtract(network.bell).toShortString()).toList());
				helper.succeed();
			});
		});
	}

	// every test of a run shares one light engine: with all of them on one server its queue takes hundreds of ticks
	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 1600)
	public static void theHomelessSpendTheNightUnderARoof(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		Villager owner = helper.spawn(EntityType.VILLAGER, BELL.offset(2, 0, 2));
		owner.setNoAi(true);
		owner.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), network.bell));
		helper.runAfterDelay(10, () -> {
			HouseProject house = HomeProjects.start(level, owner, Family.day(level));
			if (house == null) {
				Checks.fail(helper, "a house");
				return;
			}
			HomeProjects.buildNow(level, house);
			// by the bell: the house (its plan by the owner's UUID) may stand anywhere else round it, on this spot too
			Villager homeless = helper.spawn(EntityType.VILLAGER, BELL.offset(1, 0, 1));
			homeless.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), network.bell));
			homeless.getBrain().eraseMemory(MemoryModuleType.HOME);
			// set down, then held still: wandering while the light is worked out, it went where the room was out of reach or
			// took a bed of the house
			whenOnGround(helper, homeless, () -> homeless.setNoAi(true));
			LightWait.after(helper, level, house.footprint(level), () -> whenOnGround(helper, homeless, () -> {
				com.vnap.Mc.setDayTime(level, 15000);
				com.vnap.village.town.Shelter.night(level, homeless);
				Checks.assertTrue(helper, com.vnap.village.life.Agenda.has(homeless, com.vnap.village.town.Shelter.WHY), "a homeless villager goes into a closed room for the night (" + house.schematicName() + " " + house.rotation + "): " + com.vnap.village.town.Shelter.why(level, homeless));
				com.vnap.Mc.setDayTime(level, 6000);
				helper.succeed();
			}));
		});
	}

	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 200)
	public static void aTownWithNoGolemSetsOneUpByHand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		Villager mayor = helper.spawn(EntityType.VILLAGER, BELL.offset(2, 0, 2));
		mayor.setNoAi(true);
		mayor.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), network.bell));
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), network.bell);
		if (office == null) {
			Checks.fail(helper, "an office");
			return;
		}
		// the town plans only round a bell in a ticking chunk: with many tests starting at once that takes a few ticks
		whenTicking(helper, level, network.bell, () -> {
			// the office's mayor is set by hand (no flag on the villager): the mayors' own check would drop it meanwhile
			office.mayor = mayor.getUUID();
			Roads roads = Roads.get(level.getServer());
			com.vnap.village.town.Guards.plan(level, roads, network);
			BlockPos base = network.golem;
			if (base == null) {
				Checks.fail(helper, "a place for the golem: " + com.vnap.village.town.Guards.REFUSED);
				return;
			}
			setDown(level, mayor);
			Checks.assertTrue(helper, level.getBlockState(base.above()).is(Blocks.IRON_BLOCK), "the iron is set down by hand");
			com.vnap.village.town.Guards.plan(level, roads, network);
			Checks.assertTrue(helper, com.vnap.village.home.Placements.pending(level, base.above(2)), "then the pumpkin");
			setDown(level, mayor);
			List<net.minecraft.world.entity.animal.IronGolem> golems = level.getEntitiesOfClass(net.minecraft.world.entity.animal.IronGolem.class,
					new net.minecraft.world.phys.AABB(base).inflate(3));
			Checks.assertTrue(helper, golems.size() == 1 && !golems.get(0).isPlayerCreated(), "an iron golem of the village stands up: " + golems.size());
			Checks.assertTrue(helper, network.golem == null, "the town is done with it");
			com.vnap.village.town.Guards.plan(level, roads, network);
			Checks.assertTrue(helper, network.golem == null, "one is enough for a small town");
			golems.forEach(net.minecraft.world.entity.Entity::discard);
			helper.succeed();
		});
	}

	// two waits for the light engine: with every test on one server (CI) its queue takes hundreds of ticks
	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 1600)
	public static void aDarkHollowOpenToTheGroundIsLitAndASealedOneIsNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		// an awning by the bell: a stone roof on posts, dark under its middle (zombies were born under such by day)
		BlockPos awning = BELL.offset(8, 0, 8);
		for (int dx = 0; dx < 7; dx++) {
			for (int dz = 0; dz < 7; dz++) {
				helper.setBlock(awning.offset(dx, -1, dz), Blocks.STONE);
				helper.setBlock(awning.offset(dx, 2, dz), Blocks.STONE);
			}
		}
		// a hollow shut all round: nothing comes out of it, nothing is lit in it
		BlockPos sealed = BELL.offset(-14, 0, -14);
		for (int dx = 0; dx < 5; dx++) {
			for (int dy = -1; dy < 4; dy++) {
				for (int dz = 0; dz < 5; dz++) {
					boolean shell = dx == 0 || dx == 4 || dz == 0 || dz == 4 || dy == -1 || dy == 3;
					helper.setBlock(sealed.offset(dx, dy, dz), shell ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
		net.minecraft.world.level.levelgen.structure.BoundingBox box = new net.minecraft.world.level.levelgen.structure.BoundingBox(
				helper.absolutePos(sealed).getX(), helper.absolutePos(sealed).getY() - 1, helper.absolutePos(sealed).getZ(),
				helper.absolutePos(awning).getX() + 7, helper.absolutePos(awning).getY() + 3, helper.absolutePos(awning).getZ() + 7);
		whenTicking(helper, level, network.bell, () -> LightWait.after(helper, level, box, () -> {
			com.vnap.village.town.Lighting.hollows(level, network);
			// only what this test made: the town's land runs over the tests next to it
			List<Placements.Job> torches = new java.util.ArrayList<>();
			for (Placements.Job job : List.copyOf(Placements.get(level.getServer()).jobs())) {
				if (!job.state().is(Blocks.TORCH) && !job.state().is(Blocks.WALL_TORCH)) continue;
				if (box.isInside(job.pos())) torches.add(job);
				else Placements.cancel(level, job.pos());
			}
			BlockPos under = helper.absolutePos(awning);
			BlockPos shut = helper.absolutePos(sealed);
			Checks.assertTrue(helper, torches.stream().anyMatch(job -> job.pos().getX() >= under.getX() && job.pos().getZ() >= under.getZ()),
					"a torch for the dark under the awning: " + torches);
			Checks.assertTrue(helper, torches.stream().noneMatch(job -> job.pos().getX() < shut.getX() + 5 && job.pos().getZ() < shut.getZ() + 5),
					"none in the sealed hollow: " + torches);
			torches.forEach(job -> Placements.place(level, job));
			LightWait.after(helper, level, box, () -> {
				com.vnap.village.town.Lighting.hollows(level, network);
				for (Placements.Job job : List.copyOf(Placements.get(level.getServer()).jobs())) {
					if (!job.state().is(Blocks.TORCH) && !job.state().is(Blocks.WALL_TORCH)) continue;
					Checks.assertFalse(helper, box.isInside(job.pos()), "lit once is lit: " + job.pos());
					Placements.cancel(level, job.pos());
				}
				helper.succeed();
			});
		}));
	}

	private static void setDown(ServerLevel level, Villager by) {
		for (com.vnap.village.home.Placements.Job job; (job = com.vnap.village.home.Placements.forHands(level, by, 64, pos -> false)) != null; ) {
			com.vnap.village.home.Placements.place(level, job);
		}
	}

	@GameTest(template = "platform")
	public static void bedsAcrossAnOpenSideDoNotCloseTheRoom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// an open forge: a roof, walls on three sides, the fourth (z 9) open to the sky beyond the eaves
		for (int x = 9; x <= 13; x++) {
			for (int z = 7; z <= 13; z++) {
				helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
				boolean wall = (x == 9 || x == 13 || z == 13) && z >= 9;
				for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
			}
		}
		// a bed and a slab laid across the open side
		helper.setBlock(new BlockPos(10, 2, 9), Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST));
		helper.setBlock(new BlockPos(11, 2, 9), Blocks.WHITE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST)
				.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
		helper.setBlock(new BlockPos(12, 2, 9), Blocks.STONE_SLAB);
		java.util.Set<BlockPos> room = com.vnap.dialogue.FamilyHome.roomAt(level, helper.absolutePos(new BlockPos(11, 2, 11)));
		Checks.assertTrue(helper, !room.isEmpty() && !com.vnap.dialogue.FamilyHome.closed(level, room),
				"a zombie steps over a bed: beds across the open side leave the room open");
		helper.succeed();
	}

	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 400)
	public static void aMonsterOutsideAClosedRoomDoesNotPanicItsSleeper(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 9; x <= 13; x++) {
			for (int z = 9; z <= 13; z++) {
				helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
				boolean wall = x == 9 || x == 13 || z == 9 || z == 13;
				for (int y = 2; y <= 4; y++) helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
			}
		}
		// a window: a gap in the wall, seen through but not walked through
		helper.setBlock(new BlockPos(13, 3, 11), Blocks.AIR);
		helper.setBlock(new BlockPos(11, 2, 12), Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH));
		helper.setBlock(new BlockPos(11, 2, 11), Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH)
				.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 11));
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(11, 2, 11))));
		net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(15, 2, 11));
		zombie.setNoAi(true);
		Sensor sensor = new Sensor();
		long before = com.vnap.Mc.dayTime(level);
		com.vnap.Mc.setDayTime(level, 6000);
		Checks.assertFalse(helper, sensor.threat(villager, zombie), "a zombie outside the closed room is no threat");
		com.vnap.Mc.setDayTime(level, before);
		zombie.teleportTo(zombie.getX() - 4, zombie.getY(), zombie.getZ());
		Checks.assertTrue(helper, sensor.threat(villager, zombie), "a zombie in the room is");
		zombie.teleportTo(zombie.getX() + 4, zombie.getY(), zombie.getZ());
		// a way in without a door: the zombie outside is a threat again
		helper.setBlock(new BlockPos(13, 2, 11), Blocks.AIR);
		long time = com.vnap.Mc.dayTime(level);
		com.vnap.Mc.setDayTime(level, 6000);
		Checks.assertTrue(helper, sensor.threat(villager, zombie), "by day, with a way in, the zombie outside is a threat");
		com.vnap.Mc.setDayTime(level, 15000);
		Checks.assertFalse(helper, sensor.threat(villager, zombie), "at night the room is still better than out of the door");
		com.vnap.Mc.setDayTime(level, time);
		zombie.discard();
		helper.succeed();
	}

	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 20)
	public static void anOpenTrapdoorIsNoWayThrough(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		BlockPos open = new BlockPos(6, 2, 6);
		BlockPos closed = new BlockPos(8, 2, 6);
		BlockPos hatch = new BlockPos(10, 3, 6);
		helper.setBlock(open, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true));
		helper.setBlock(closed, Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
		helper.setBlock(hatch.below(), Blocks.LADDER.defaultBlockState());
		helper.setBlock(hatch, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true));
		// a taiga flower bed's ring of open trapdoors led six villagers in for a zombie: an open one is walked round
		Checks.assertTrue(helper, net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(villager, helper.absolutePos(open))
				== net.minecraft.world.level.pathfinder.PathType.FENCE, "an open trapdoor stands in the way");
		Checks.assertTrue(helper, net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(villager, helper.absolutePos(closed))
				!= net.minecraft.world.level.pathfinder.PathType.FENCE, "a shut one is walked over");
		Checks.assertTrue(helper, net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(villager, helper.absolutePos(hatch))
				!= net.minecraft.world.level.pathfinder.PathType.FENCE, "an open hatch over a ladder is the way down");
		helper.succeed();
	}

	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 100)
	public static void aVillagerShutsTheDoorItLeftBehind(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(11, 2, 11);
		helper.setBlock(door, Blocks.SPRUCE_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true));
		helper.setBlock(door.above(), Blocks.SPRUCE_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true)
				.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		// five blocks off: vanilla forgot a door more than three away, and it stayed open all night
		Villager villager = helper.spawn(EntityType.VILLAGER, door.east(5));
		villager.setNoAi(true);
		Villager standing = helper.spawn(EntityType.VILLAGER, door);
		standing.setNoAi(true);
		java.util.Set<GlobalPos> doors = new java.util.HashSet<>(java.util.Set.of(GlobalPos.of(level.dimension(), helper.absolutePos(door))));
		com.vnap.village.life.Doors.close(level, villager, null, null, doors, java.util.Optional.empty());
		helper.assertBlockProperty(door, net.minecraft.world.level.block.DoorBlock.OPEN, true);
		Checks.assertTrue(helper, doors.size() == 1, "a door somebody stands in is kept, to be shut once clear");
		standing.discard();
		com.vnap.village.life.Doors.close(level, villager, null, null, doors, java.util.Optional.empty());
		helper.assertBlockProperty(door, net.minecraft.world.level.block.DoorBlock.OPEN, false);
		Checks.assertTrue(helper, doors.isEmpty(), "the shut door is forgotten");
		helper.succeed();
	}

	@GameTest(batch = "grounds", template = "home_platform", timeoutTicks = 200)
	public static void aPanicCallsAGolemInASpreadTown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		List<Villager> villagers = new java.util.ArrayList<>();
		for (int dx : new int[]{-9, 0, 9}) {
			Villager villager = helper.spawn(EntityType.VILLAGER, BELL.offset(dx, 0, 6));
			villager.setNoAi(true);
			villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), network.bell));
			villagers.add(villager);
		}
		helper.runAfterDelay(5, () -> {
			// eighteen blocks between the outer two: vanilla (ten) would call no golem
			villagers.get(0).spawnGolemIfNeeded(level, level.getGameTime(), 3);
			List<net.minecraft.world.entity.animal.IronGolem> golems = level.getEntitiesOfClass(net.minecraft.world.entity.animal.IronGolem.class,
					new net.minecraft.world.phys.AABB(network.bell).inflate(32));
			Checks.assertTrue(helper, golems.size() == 1, "the town's villagers call a golem: " + golems.size());
			villagers.get(2).spawnGolemIfNeeded(level, level.getGameTime(), 3);
			Checks.assertTrue(helper, level.getEntitiesOfClass(net.minecraft.world.entity.animal.IronGolem.class, new net.minecraft.world.phys.AABB(network.bell).inflate(32)).size() == 1,
					"not twice: the golem is seen");
			golems.forEach(net.minecraft.world.entity.Entity::discard);
			helper.succeed();
		});
	}

	private static void whenOnGround(GameTestHelper helper, Villager villager, Runnable then) {
		helper.runAfterDelay(1, () -> {
			if (villager.onGround()) then.run();
			else whenOnGround(helper, villager, then);
		});
	}

	private static void whenTicking(GameTestHelper helper, ServerLevel level, BlockPos pos, Runnable then) {
		helper.runAfterDelay(1, () -> {
			if (level.isPositionEntityTicking(pos)) then.run();
			else whenTicking(helper, level, pos, then);
		});
	}

	private static final class Sensor extends net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor {
		boolean threat(Villager villager, net.minecraft.world.entity.LivingEntity monster) {
			//? if >=1.21.2 {
			/*return isMatchingEntity((net.minecraft.server.level.ServerLevel) villager.level(), villager, monster);
			*///?} else
			return isMatchingEntity(villager, monster);
		}
	}
}
