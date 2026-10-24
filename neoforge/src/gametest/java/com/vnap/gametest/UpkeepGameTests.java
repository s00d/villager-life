package com.vnap.gametest;

import com.vnap.dialogue.Family;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Coins;
import com.vnap.village.town.RoadFinder;
import com.vnap.village.town.Roads;
import com.vnap.village.town.TownPlan;
import com.vnap.village.town.TownRules;
import com.vnap.village.town.Upkeep;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** The mayor's work orders: the town's pits filled, its roads cleared, a player's lot left alone. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class UpkeepGameTests {
	private static final BlockPos BELL = new BlockPos(50, 2, 50);

	private UpkeepGameTests() {
	}

	/** A bell, its office with a mayor and a plan laid out over a few days. */
	private static Roads.Network town(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BELL, Blocks.BELL);
		BlockPos bell = helper.absolutePos(BELL);
		close(level, bell);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		office.mayor = UUID.randomUUID();
		Roads roads = Roads.get(level.getServer());
		Roads.Network network = roads.of(level.dimension(), office.bell);
		long day = Family.day(level);
		for (int i = 0; i < 6; i++) roads.plan(level, network, day + i, 0);
		// the plan's own orders out of the way: each test orders its own
		network.segments.removeIf(segment -> segment.label.equals("pits") || segment.label.equals("debris") || segment.label.equals("levelling"));
		return network;
	}

	private static void close(ServerLevel level, BlockPos bell) {
		Mayors.get(level.getServer()).offices().removeIf(office -> office.bell.closerThan(bell, TownRules.SPAN));
		Roads.get(level.getServer()).networks().removeIf(network -> network.bell.closerThan(bell, TownRules.SPAN));
		VillageMoodSavedData.get(level).houses().removeIf(house -> house.bell.closerThan(bell, TownRules.SPAN));
	}

	/** A cut {@code width} by {@code length} three deep from the grass at {@code corner} (east and south), on dirt. */
	private static void cut(ServerLevel level, BlockPos corner, int width, int length) {
		for (int dx = 0; dx < length; dx++) {
			for (int dz = 0; dz < width; dz++) {
				BlockPos top = corner.offset(dx, 0, dz);
				for (int d = 0; d < 3; d++) level.setBlockAndUpdate(top.below(d), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(top.below(3), Blocks.DIRT.defaultBlockState());
			}
		}
	}

	private static Roads.@Nullable Segment order(Roads.Network network, String label) {
		return network.segments.stream().filter(segment -> segment.label.equals(label)).findFirst().orElse(null);
	}

	private static void lay(ServerLevel level, Roads.Segment segment) {
		BuildJob job = segment.job();
		job.site = null;
		while (!job.done()) {
			job.skipDone(level);
			if (!job.done()) job.placeNext(level, null);
		}
	}

	/** The street out of the square going east: its cell {@code at} blocks from the bell. */
	private static @Nullable BlockPos eastStreet(Roads.Network network, int at) {
		for (TownPlan.Street street : network.streets) {
			for (RoadFinder.Cell cell : street.cells) {
				if (cell.pos().getX() - network.bell.getX() == at && Math.abs(cell.pos().getZ() - network.bell.getZ()) <= 2) return cell.pos();
			}
		}
		return null;
	}

	@GameTest(batch = "upkeep", template = "home_platform", timeoutTicks = 200)
	public static void aRavineCutByAStreetIsFilledToTheGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			BlockPos street = eastStreet(network, 14);
			Checks.assertTrue(helper, street != null, "a street east of the bell: " + network.streets.stream().map(one -> one.cells.isEmpty() ? "empty"
					: one.cells.get(one.cells.size() - 1).pos().subtract(network.bell).toShortString()).toList() + " plots " + network.plots.keySet());
			if (street == null) return;
			// three wide and seven long, two blocks off the street, on land the town has not given to anything (a plot
			// laid out there is rightly left alone by the pits)
			java.util.Set<Long> built = new java.util.HashSet<>();
			com.vnap.village.town.Land.around(level, network.bell).map(network.bell, 64).forEach((key, layer) -> {
				if (layer == com.vnap.village.town.Land.Layer.BUILDING) built.add(key);
			});
			BlockPos corner = null;
			for (int along = -3; along <= 20 && corner == null; along++) {
				BlockPos at = street.offset(along, 0, 3);
				boolean free = true;
				for (int dx = 0; dx < 7 && free; dx++) for (int dz = 0; dz < 3 && free; dz++) free = !built.contains(BlockPos.asLong(at.getX() + dx, 0, at.getZ() + dz));
				if (free) corner = at;
			}
			Checks.assertTrue(helper, corner != null, "free land by the street");
			if (corner == null) return;
			cut(level, corner, 3, 7);
			Checks.assertTrue(helper, Upkeep.pits(level, Roads.get(level.getServer()), network) > 0, "the mayor orders the pits filled");
			Roads.Segment fill = order(network, "pits");
			Checks.assertTrue(helper, fill != null, "the order is a stretch for the town's builders");
			if (fill == null) return;
			lay(level, fill);
			for (int dx = 0; dx < 7; dx++) {
				for (int dz = 0; dz < 3; dz++) {
					for (int d = 0; d < 3; d++) {
						BlockPos cell = corner.offset(dx, -d, dz);
						Checks.assertTrue(helper, level.getBlockState(cell).is(d == 0 ? Blocks.GRASS_BLOCK : Blocks.DIRT),
								(d == 0 ? "turf on top at " : "filled up to the ground at ") + cell.toShortString());
					}
					Checks.assertTrue(helper, level.getBlockState(corner.offset(dx, 1, dz)).isAir(), "and no higher");
				}
			}
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}

	/** A pond and a hump on the town's free land: the mayor has the square levelled to its ground, filled and dug. */
	@GameTest(batch = "upkeep", template = "home_platform", timeoutTicks = 200)
	public static void aPondAndAHumpOnFreeLandAreLevelled(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BELL, Blocks.BELL);
		BlockPos bell = helper.absolutePos(BELL);
		close(level, bell);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		office.mayor = UUID.randomUUID();
		Roads roads = Roads.get(level.getServer());
		Roads.Network network = roads.of(level.dimension(), office.bell);
		try {
			BlockPos grass = helper.absolutePos(new BlockPos(20, 1, 20));
			// a pond four across and two deep on a dirt bed, and a hump of earth two high beside it
			for (int dx = 0; dx < 4; dx++) {
				for (int dz = 0; dz < 4; dz++) {
					level.setBlockAndUpdate(grass.offset(dx, -2, dz), Blocks.DIRT.defaultBlockState());
					level.setBlockAndUpdate(grass.offset(dx, -1, dz), Blocks.WATER.defaultBlockState());
					level.setBlockAndUpdate(grass.offset(dx, 0, dz), Blocks.WATER.defaultBlockState());
					level.setBlockAndUpdate(grass.offset(dx + 6, 1, dz), Blocks.DIRT.defaultBlockState());
					level.setBlockAndUpdate(grass.offset(dx + 6, 2, dz), Blocks.GRASS_BLOCK.defaultBlockState());
				}
			}
			// a square a day: the hump's and the pond's
			Checks.assertTrue(helper, Upkeep.levelling(level, roads, network) > 0, "the mayor orders the ground levelled");
			Roads.Segment work = null;
			StringBuilder days = new StringBuilder(Upkeep.lastLevelling);
			for (int day = 0; day < 3; day++) {
				Roads.Segment order = order(network, "levelling");
				if (order == null) break;
				work = order;
				lay(level, order);
				network.segments.remove(order);
				Upkeep.levelling(level, roads, network);
				days.append(" | ").append(Upkeep.lastLevelling);
			}
			Checks.assertTrue(helper, work != null, "the order is a stretch for the town's builder");
			if (work == null) return;
			for (int dx = 0; dx < 4; dx++) {
				for (int dz = 0; dz < 4; dz++) {
					Checks.assertTrue(helper, level.getBlockState(grass.offset(dx, 0, dz)).is(Blocks.GRASS_BLOCK), "the pond filled to the grass, not "
							+ level.getBlockState(grass.offset(dx, 0, dz)) + " at " + grass.offset(dx, 0, dz).toShortString() + "; the order: "
							+ days);
					Checks.assertTrue(helper, level.getFluidState(grass.offset(dx, -1, dz)).isEmpty(), "no water left under it");
					Checks.assertTrue(helper, level.getBlockState(grass.offset(dx + 6, 1, dz)).isAir(), "the hump dug down to the grass");
				}
			}
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}

	@GameTest(batch = "upkeep", template = "home_platform", timeoutTicks = 200)
	public static void gravelAndThingsOnARoadAreClearedAndWhatWasSetStays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			BlockPos one = eastStreet(network, 10);
			BlockPos two = eastStreet(network, 12);
			BlockPos three = eastStreet(network, 14);
			Checks.assertTrue(helper, one != null && two != null && three != null, "a street east of the bell");
			if (one == null || two == null || three == null) return;
			// a stretch of the street laid
			for (BlockPos cell : new BlockPos[]{one, two, three}) {
				level.setBlockAndUpdate(cell, Blocks.COBBLESTONE.defaultBlockState());
				network.cells.add(cell);
			}
			level.setBlockAndUpdate(one.above(), Blocks.GRAVEL.defaultBlockState());
			level.setBlockAndUpdate(two.above(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
			ItemEntity apple = new ItemEntity(level, three.getX() + 0.5, three.getY() + 1.0, three.getZ() + 0.5, new ItemStack(Items.APPLE, 3));
			level.addFreshEntity(apple);
			Checks.assertTrue(helper, Upkeep.debris(level, Roads.get(level.getServer()), network) > 0, "the mayor orders the road cleared");
			Roads.Segment clear = order(network, "debris");
			Checks.assertTrue(helper, clear != null, "the order is a stretch for the town's builders");
			if (clear == null) return;
			lay(level, clear);
			Checks.assertTrue(helper, level.getBlockState(one.above()).isAir(), "the gravel is gone");
			Checks.assertTrue(helper, level.getBlockState(two.above()).is(Blocks.OAK_LEAVES), "leaves set by hand stay");
			Checks.assertTrue(helper, level.getBlockState(one).is(Blocks.COBBLESTONE), "the road stays");
			Checks.assertTrue(helper, Upkeep.litter(level, network) > 0, "the apples are seen");
			Checks.assertTrue(helper, level.getEntitiesOfClass(ItemEntity.class, new AABB(three).inflate(2.0), ItemEntity::isAlive).isEmpty(), "nothing lies on the road");
			Checks.assertTrue(helper, network.lost.stream().anyMatch(stack -> stack.is(Items.APPLE)), "the apples go to the lost and found");
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}

	@GameTest(batch = "upkeep", template = "home_platform", timeoutTicks = 200)
	public static void aPitOnAPlayersLotIsLeftAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			BlockPos bell = network.bell;
			TownPlan.Lot lot = network.lots.stream().min(java.util.Comparator.comparingDouble(l -> l.box.getCenter().distSqr(bell))).orElse(null);
			Checks.assertTrue(helper, lot != null, "a lot");
			if (lot == null) return;
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			Coins.give(player, lot.price());
			Checks.assertTrue(helper, TownPlan.buy(level, network, lot, player), "bought");
			BlockPos corner = lot.box.getCenter().atY(helper.absolutePos(BELL).getY() - 1).offset(-1, 0, -1);
			cut(level, corner, 3, 3);
			Upkeep.pits(level, Roads.get(level.getServer()), network);
			Roads.Segment fill = order(network, "pits");
			if (fill != null) lay(level, fill);
			for (int dx = 0; dx < 3; dx++) {
				for (int dz = 0; dz < 3; dz++) Checks.assertTrue(helper, level.getBlockState(corner.offset(dx, 0, dz)).isAir(), "the player's pit is the player's");
			}
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}
}
