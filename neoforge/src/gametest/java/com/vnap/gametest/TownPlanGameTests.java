package com.vnap.gametest;

import com.vnap.village.town.TownRules;
import com.vnap.block.LifeBlocks;
import com.vnap.dialogue.Family;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Placements;
import com.vnap.village.market.Coins;
import com.vnap.village.town.Land;
import com.vnap.village.town.NoticeBoard;
import com.vnap.village.town.RoadFinder;
import com.vnap.village.town.Roads;
import com.vnap.village.town.TownPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The town's plan: streets out of the square, lots along them, houses on the lots, a player's lot and leftovers. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class TownPlanGameTests {
	private static final BlockPos BELL = new BlockPos(50, 2, 50);

	private TownPlanGameTests() {
	}

	/** A bell, its office and a fresh plan laid out over a few days. */
	private static Roads.Network town(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BELL, Blocks.BELL);
		BlockPos bell = helper.absolutePos(BELL);
		// every batch is set out on the same ground: an office, network or house an earlier batch left here would be taken
		close(level, bell);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Roads roads = Roads.get(level.getServer());
		Roads.Network network = roads.of(level.dimension(), office.bell);
		long day = Family.day(level);
		for (int i = 0; i < 6; i++) roads.plan(level, network, day + i, 0);
		return network;
	}

	private static void close(ServerLevel level, BlockPos bell) {
		Mayors.get(level.getServer()).offices().removeIf(office -> office.bell.closerThan(bell, TownRules.SPAN));
		Roads.get(level.getServer()).networks().removeIf(network -> network.bell.closerThan(bell, TownRules.SPAN));
		VillageMoodSavedData.get(level).houses().removeIf(house -> house.bell.closerThan(bell, TownRules.SPAN));
	}

	private static Villager homeless(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, BELL.offset(2, 0, 2));
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), helper.absolutePos(BELL)));
		((VillagerLifeData) villager).vnap$setFemale(false);
		Family.state(villager).genderKnown = true;
		return villager;
	}

	@GameTest(batch = "plan", template = "home_platform", timeoutTicks = 200)
	public static void aTownLaysOutItsStreetsAndLotsAhead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			Checks.assertTrue(helper, network.streets.size() >= 4, "streets run out of the square, " + network.streets.size());
			// the town's own buildings keep their ground first (five of them on this small platform)
			Checks.assertTrue(helper, network.lots.size() >= 4, "lots are cut along them, " + network.lots.size());
			Set<BlockPos> streets = new HashSet<>();
			for (TownPlan.Street street : network.streets) for (RoadFinder.Cell cell : street.cells) streets.add(cell.pos());
			for (TownPlan.Lot lot : network.lots) {
				for (TownPlan.Lot other : network.lots) {
					Checks.assertFalse(helper, lot != other && lot.box.intersects(other.box), "lots never overlap");
				}
				for (BlockPos cell : streets) {
					Checks.assertFalse(helper, lot.box.isInside(cell.atY(lot.floor())), "no street runs across a lot");
				}
				// its street is right in front of it
				BlockPos front = lot.box.getCenter().atY(lot.floor()).relative(lot.front, lot.box.getXSpan() / 2 + 2);
				boolean faces = streets.stream().anyMatch(cell -> Math.abs(cell.getX() - front.getX()) + Math.abs(cell.getZ() - front.getZ()) <= 2);
				Checks.assertTrue(helper, faces, "a lot faces its street");
				Checks.assertFalse(helper, lot.box.getCenter().atY(network.bell.getY()).closerThan(network.bell, 8), "no lot on the square");
			}
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}

	@GameTest(batch = "plan", template = "home_platform", timeoutTicks = 200)
	public static void aHomelessHouseholdBuildsOnTheNearestLotFacingItsStreet(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			Villager villager = homeless(helper);
			HouseProject house = HomeProjects.start(level, villager, Family.day(level));
			Checks.assertTrue(helper, house != null, "a house is planned");
			if (house == null) return;
			BoundingBox box = house.footprint(level);
			TownPlan.Lot lot = TownPlan.at(network, box.getCenter());
			Checks.assertTrue(helper, lot != null && lot.taken, "on a lot of the plan, taken now");
			if (lot == null) return;
			Checks.assertTrue(helper, lot.box.isInside(new BlockPos(box.minX(), lot.floor(), box.minZ())) && lot.box.isInside(new BlockPos(box.maxX(), lot.floor(), box.maxZ())),
					"the house stands inside its lot");
			Checks.assertTrue(helper, house.rotation.equals(rotationFacing(lot)), "its front faces the street");
			Checks.assertTrue(helper, network.segments.stream().anyMatch(segment -> segment.label.equals("street")), "its street is laid up to it");
			HouseProject next = HomeProjects.start(level, homeless(helper), Family.day(level));
			Checks.assertTrue(helper, next != null && !next.footprint(level).intersects(box), "the next house takes another lot");
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}

	private static Rotation rotationFacing(TownPlan.Lot lot) {
		for (Rotation rotation : Rotation.values()) {
			if (rotation.rotate(net.minecraft.core.Direction.NORTH) == lot.front) return rotation;
		}
		return Rotation.NONE;
	}

	@GameTest(batch = "plan", template = "home_platform", timeoutTicks = 200)
	public static void aPlayersShedOnALotGoesToTheLostAndFound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			BlockPos bell = helper.absolutePos(BELL);
			TownPlan.Lot lot = network.lots.stream().min(java.util.Comparator.comparingDouble(l -> l.box.getCenter().distSqr(bell))).orElse(null);
			Checks.assertTrue(helper, lot != null, "a lot");
			if (lot == null) return;
			// a player's shed on the nearest lot, two blocks in from its street side where every plan stands (a house is
			// set to the street, and a shallow one leaves the lot's middle bare): planks and a chest with a diamond in it
			int half = (lot.front.getAxis() == net.minecraft.core.Direction.Axis.X ? lot.box.getXSpan() : lot.box.getZSpan()) / 2;
			BlockPos middle = lot.box.getCenter().atY(lot.floor() + 1).relative(lot.front, half - 2);
			BlockPos beside = middle.relative(lot.front.getClockWise());
			level.setBlockAndUpdate(middle, Blocks.OAK_PLANKS.defaultBlockState());
			level.setBlockAndUpdate(beside, Blocks.CHEST.defaultBlockState());
			if (level.getBlockEntity(beside) instanceof Container chest) chest.setItem(0, new ItemStack(Items.DIAMOND));
			// the town's notice board on the square
			BlockPos board = bell.offset(3, 0, 0);
			level.setBlockAndUpdate(board, LifeBlocks.NOTICE_BOARD.defaultBlockState());
			level.setBlockAndUpdate(board.below(), Blocks.STONE.defaultBlockState());
			network.features.add(board);
			HouseProject house = HomeProjects.start(level, homeless(helper), Family.day(level));
			Checks.assertTrue(helper, house != null && house.footprint(level).isInside(middle), "the house goes up on that lot all the same: " + (house == null ? "none " + com.vnap.village.town.TownPlan.describe(level, network) : house.footprint(level) + " lot " + lot.box));
			if (house == null) return;
			Checks.assertTrue(helper, house.clearing.contains(middle) && house.clearing.contains(beside), "the shed is to come down first");
			while (!house.clearing.isEmpty()) HomeProjects.clearOne(level, house, house.clearing.get(0));
			Checks.assertTrue(helper, level.getBlockState(middle).isAir(), "taken down");
			Checks.assertTrue(helper, network.lost.stream().anyMatch(stack -> stack.is(Items.DIAMOND)), "the diamond is kept for its owner");
			// the lost and found: a chest set down beside the board, the things put into it
			for (Placements.Job job : List.copyOf(Placements.get(level.getServer()).jobs())) {
				if (job.pos().closerThan(board, 2)) Placements.place(level, job);
			}
			NoticeBoard.lostAndFound(level, network);
			boolean found = false;
			for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				if (level.getBlockEntity(board.relative(side)) instanceof Container chest) {
					for (int i = 0; i < chest.getContainerSize(); i++) found |= chest.getItem(i).is(Items.DIAMOND);
				}
			}
			Checks.assertTrue(helper, found, "the diamond lies in the lost and found by the board");
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}

	/** A hole dug in a lot after it was cut (a creeper, a player) is filled with earth before the house goes up over it. */
	@GameTest(batch = "plan", template = "home_platform", timeoutTicks = 200)
	public static void aHoleUnderAHouseIsFilledFirst(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			BlockPos bell = helper.absolutePos(BELL);
			TownPlan.Lot lot = network.lots.stream().min(java.util.Comparator.comparingDouble(l -> l.box.getCenter().distSqr(bell))).orElse(null);
			Checks.assertTrue(helper, lot != null, "a lot");
			if (lot == null) return;
			int half = (lot.front.getAxis() == net.minecraft.core.Direction.Axis.X ? lot.box.getXSpan() : lot.box.getZSpan()) / 2;
			BlockPos hole = lot.box.getCenter().atY(lot.floor() - 1).relative(lot.front, half - 2);
			for (int d = 0; d < 3; d++) level.setBlockAndUpdate(hole.below(d), Blocks.AIR.defaultBlockState());
			// natural ground under the pit (the test world's own ground is sandstone from 26.3)
			level.setBlockAndUpdate(hole.below(3), Blocks.DIRT.defaultBlockState());
			HouseProject house = HomeProjects.start(level, homeless(helper), Family.day(level));
			Checks.assertTrue(helper, house != null && house.footprint(level).isInside(hole.atY(house.origin.getY())), "the house goes up over the hole");
			Roads.Segment fill = network.segments.stream().filter(segment -> segment.label.equals("groundwork")).findFirst().orElse(null);
			Checks.assertTrue(helper, fill != null, "ground work is queued");
			if (fill == null) return;
			com.vnap.village.build.BuildJob job = fill.job();
			job.site = null;
			while (!job.done()) {
				job.skipDone(level);
				if (!job.done()) job.placeNext(level, null);
			}
			for (int d = 0; d < 3; d++) Checks.assertTrue(helper, level.getBlockState(hole.below(d)).is(Blocks.DIRT), "filled from the bottom, " + d + " down");
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}

	private static @org.jspecify.annotations.Nullable BlockPos columnHolding(ServerLevel level, int x, int z, int floor,
			java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> what) {
		for (int y = floor + 6; y >= floor - 6; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (what.test(level.getBlockState(pos))) return pos;
		}
		return null;
	}

	@GameTest(batch = "plan", template = "home_platform", timeoutTicks = 200)
	public static void aPlayerBuysALotAndTheTownKeepsOffIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			BlockPos bell = helper.absolutePos(BELL);
			Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
			TownPlan.Lot lot = network.lots.stream().min(java.util.Comparator.comparingDouble(l -> l.box.getCenter().distSqr(bell))).orElse(null);
			Checks.assertTrue(helper, lot != null, "a lot");
			if (lot == null) return;
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			Checks.assertFalse(helper, TownPlan.buy(level, network, lot, player), "no money, no lot");
			Coins.give(player, lot.price());
			Checks.assertTrue(helper, TownPlan.buy(level, network, lot, player), "bought");
			// the price went to the treasury (which pays for the street up to the lot out of it at once)
			Checks.assertTrue(helper, player.getUUID().equals(lot.player) && Coins.count(player.getInventory()) == 0 && office.treasury >= 0, "the treasury has its price");
			Checks.assertTrue(helper, TownPlan.owns(level, lot.box.getCenter(), player.getUUID()), "the player's now");
			// fenced the moment it is bought: the way in on its street side, the owner's nameplate by it, lanterns at the corners
			Checks.assertTrue(helper, lot.marked, "marked out at once");
			BoundingBox box = lot.box;
			int fences = 0;
			int edge = 0;
			int lanterns = 0;
			net.minecraft.core.Direction along = lot.front.getClockWise();
			BlockPos centre = box.getCenter();
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					if (x != box.minX() && x != box.maxX() && z != box.minZ() && z != box.maxZ()) continue;
					edge++;
					BlockPos fence = columnHolding(level, x, z, lot.floor(), state -> state.getBlock() instanceof net.minecraft.world.level.block.FenceBlock);
					if (fence == null) continue;
					fences++;
					if (level.getBlockState(fence.above()).is(Blocks.LANTERN)) lanterns++;
					boolean front = switch (lot.front) {
						case NORTH -> z == box.minZ();
						case SOUTH -> z == box.maxZ();
						case WEST -> x == box.minX();
						default -> x == box.maxX();
					};
					int off = (x - centre.getX()) * along.getStepX() + (z - centre.getZ()) * along.getStepZ();
					Checks.assertFalse(helper, front && Math.abs(off) <= 1, "the way in on the street side is open, " + off);
				}
			}
			Checks.assertTrue(helper, fences >= edge - 8, "a fence all round: " + fences + " of " + edge);
			Checks.assertTrue(helper, lanterns >= 3, "lanterns on its corner posts, " + lanterns);
			BlockPos sign = null;
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), lot.floor() - 6, box.minZ(), box.maxX(), lot.floor() + 6, box.maxZ())) {
				if (level.getBlockState(pos).is(LifeBlocks.HOUSE_SIGN)) sign = pos.immutable();
			}
			Checks.assertTrue(helper, sign != null && level.getBlockEntity(sign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity board
					&& com.vnap.Mc.signLine(board, 0).equals(lot.playerName) && !lot.playerName.isEmpty(), "a nameplate with the owner's name");
			if (sign == null) return;
			Checks.assertTrue(helper, level.getBlockState(sign).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) == lot.front,
					"it looks out to the street");
			Checks.assertTrue(helper, Math.abs((sign.getX() - centre.getX()) * along.getStepX() + (sign.getZ() - centre.getZ()) * along.getStepZ()) == 2,
					"it stands by the way in");
			for (int i = 0; i < 4; i++) {
				HouseProject house = HomeProjects.start(level, homeless(helper), Family.day(level));
				Checks.assertFalse(helper, house != null && house.footprint(level).intersects(lot.box), "no house goes up on the player's lot");
			}
			Checks.assertTrue(helper, Land.around(level, bell).intersects(lot.box, 0), "the land index holds it");
			helper.succeed();
		} finally {
			Placements.get(level.getServer()).jobs().clear();
			close(level, network.bell);
		}
	}
}
