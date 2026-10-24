package com.vnap.gametest;

import com.vnap.config.LifeSettings;
import com.vnap.mega.GateFieldBlock;
import com.vnap.mega.Gates;
import com.vnap.mega.MegaBlocks;
import com.vnap.mega.MegaControllerBlock;
import com.vnap.mega.MegaControllerBlockEntity;
import com.vnap.mega.MegaKind;
import com.vnap.mega.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The megastructures: built by their plan they come alive and work; a block taken out stops them; the server can turn them off. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class MegaGameTests {
	private MegaGameTests() {
	}

	/** Builds the plan with its controller at {@code at} facing {@code facing}; the controller's block entity. */
	private static MegaControllerBlockEntity build(GameTestHelper helper, MegaKind kind, BlockPos at, Direction facing) {
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(at);
		Block controller = MegaBlocks.controller(kind);
		for (Shape.Cell cell : kind.shape().cells(where, facing)) {
			if (cell.what() == '@') {
				level.setBlock(cell.pos(), controller.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing), 3);
			} else if (cell.what() == '.') {
				level.setBlock(cell.pos(), Blocks.AIR.defaultBlockState(), 3);
			} else {
				Block block = Shape.block(cell.what());
				if (block != null) level.setBlock(cell.pos(), block.defaultBlockState(), 3);
			}
		}
		if (!(level.getBlockEntity(where) instanceof MegaControllerBlockEntity entity)) throw new IllegalStateException("no controller");
		return entity;
	}

	@GameTest(template = "workshop_platform", timeoutTicks = 200)
	public static void aWholeQuarryDigsIntoItsChestAndStopsWhenBroken(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LifeSettings.megastructures(true);
		BlockPos at = new BlockPos(30, 3, 30);
		MegaControllerBlockEntity quarry = build(helper, MegaKind.QUARRY, at, Direction.NORTH);
		quarry.dig.half = 2;
		// what it will dig: a little stone under its bed
		for (BlockPos stone : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(28, 2, 30)), helper.absolutePos(new BlockPos(32, 2, 34)))) {
			level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
		}
		// in front of the controller, outside its plan
		helper.setBlock(at.north(), Blocks.CHEST);
		if (!(level.getBlockEntity(helper.absolutePos(at.north())) instanceof Container chest)) {
			Checks.fail(helper, "no chest");
			return;
		}
		chest.setItem(0, new ItemStack(Items.COAL, 2));
		quarry.look(level);
		Checks.assertTrue(helper, quarry.formed(), "built by its plan, it stands whole");
		helper.runAfterDelay(60, () -> {
			Checks.assertTrue(helper, quarry.dig.dug > 0, "it digs");
			int cobble = 0;
			for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(Items.COBBLESTONE)) cobble += chest.getItem(slot).getCount();
			Checks.assertTrue(helper, cobble > 0, "what it digs is in the chest beside it");
			// a corner of the crown taken out: it goes dark and stops
			level.setBlock(quarry.getBlockPos().above().relative(Direction.EAST, 2), Blocks.AIR.defaultBlockState(), 3);
			quarry.look(level);
			long dug = quarry.dig.dug;
			Checks.assertTrue(helper, !quarry.formed(), "a block of it gone, it is not whole");
			helper.runAfterDelay(20, () -> {
				Checks.assertTrue(helper, quarry.dig.dug == dug, "and it digs no more");
				helper.succeed();
			});
		});
	}

	@GameTest(template = "workshop_platform", timeoutTicks = 100)
	public static void twoGatesOfAColourAreOneWayAndAThirdBreaksIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LifeSettings.megastructures(true);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		// the book is the server's: earlier runs' gates (unloaded now) would make red three-way
		Gates.get(level.getServer()).forget(DyeColor.RED);
		Gates.get(level.getServer()).forget(DyeColor.BLUE);
		MegaControllerBlockEntity one = build(helper, MegaKind.GATE, new BlockPos(10, 2, 10), Direction.SOUTH);
		MegaControllerBlockEntity two = build(helper, MegaKind.GATE, new BlockPos(30, 2, 10), Direction.SOUTH);
		one.key(player, new ItemStack(MegaBlocks.key(DyeColor.RED)));
		two.key(player, new ItemStack(MegaBlocks.key(DyeColor.RED)));
		one.look(level);
		two.look(level);
		Gates book = Gates.get(level.getServer());
		Checks.assertTrue(helper, one.formed() && two.formed(), "both gates stand whole");
		Checks.assertTrue(helper, two.getBlockPos().equals(java.util.Objects.requireNonNull(book.other(where(one))).pos()), "red to red: one way");
		BlockPos field = helper.absolutePos(new BlockPos(10, 3, 10));
		Checks.assertTrue(helper, level.getBlockState(field).is(MegaBlocks.GATE_FIELD)
				&& level.getBlockState(field).getValue(GateFieldBlock.COLOUR) == DyeColor.RED.getId(), "its field is lit in red");
		MegaControllerBlockEntity three = build(helper, MegaKind.GATE, new BlockPos(50, 2, 10), Direction.SOUTH);
		three.key(player, new ItemStack(MegaBlocks.key(DyeColor.RED)));
		one.look(level);
		Checks.assertTrue(helper, book.other(where(one)) == null && !level.getBlockState(field).is(MegaBlocks.GATE_FIELD),
				"a third red gate: no way at all, the field goes");
		three.key(player, new ItemStack(MegaBlocks.key(DyeColor.BLUE)));
		one.look(level);
		Checks.assertTrue(helper, book.other(where(one)) != null && level.getBlockState(field).is(MegaBlocks.GATE_FIELD), "the third keyed blue: red is one way again");
		for (MegaControllerBlockEntity gate : new MegaControllerBlockEntity[] {one, two, three}) book.set(where(gate), null);
		player.discard();
		helper.succeed();
	}

	// a batch of its own: the switch is the server's, the other tests in a batch run at once
	@GameTest(template = "workshop_platform", timeoutTicks = 100, batch = "mega_off")
	public static void aServerWithoutMegastructuresKeepsThemDark(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MegaControllerBlockEntity gate = build(helper, MegaKind.GATE, new BlockPos(70, 2, 10), Direction.SOUTH);
		LifeSettings.megastructures(false);
		try {
			gate.look(level);
			Checks.assertTrue(helper, !gate.formed() && !level.getBlockState(gate.getBlockPos()).getValue(MegaControllerBlock.FORMED),
					"turned off, a whole gate stays dark");
		} finally {
			LifeSettings.megastructures(true);
		}
		gate.look(level);
		Checks.assertTrue(helper, gate.formed(), "turned on again, it lights");
		helper.succeed();
	}

	private static net.minecraft.core.GlobalPos where(MegaControllerBlockEntity gate) {
		return net.minecraft.core.GlobalPos.of(java.util.Objects.requireNonNull(gate.getLevel()).dimension(), gate.getBlockPos());
	}

	@GameTest(template = "workshop_platform", timeoutTicks = 200)
	public static void theSmelterSmeltsLeftToRightAndOresTwice(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LifeSettings.megastructures(true);
		BlockPos at = new BlockPos(20, 2, 70);
		MegaControllerBlockEntity smelter = build(helper, MegaKind.SMELTER, at, Direction.NORTH);
		// facing north, its left is east (the builder in front sees it on the left)
		helper.setBlock(at.east(2), Blocks.CHEST);
		helper.setBlock(at.west(2), Blocks.CHEST);
		if (!(level.getBlockEntity(helper.absolutePos(at.east(2))) instanceof Container from) || !(level.getBlockEntity(helper.absolutePos(at.west(2))) instanceof Container to)) {
			Checks.fail(helper, "no chests");
			return;
		}
		from.setItem(0, new ItemStack(Items.RAW_IRON, 4));
		from.setItem(1, new ItemStack(Items.COAL, 1));
		smelter.look(level);
		helper.succeedWhen(() -> {
			int iron = 0;
			for (int slot = 0; slot < to.getContainerSize(); slot++) if (to.getItem(slot).is(Items.IRON_INGOT)) iron += to.getItem(slot).getCount();
			Checks.assertTrue(helper, iron == 8, "four raw iron make eight ingots: " + iron);
		});
	}

	@GameTest(template = "workshop_platform", timeoutTicks = 100)
	public static void aBeaconOfCalmKeepsMonstersFromBeingBornNearIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LifeSettings.megastructures(true);
		MegaControllerBlockEntity beacon = build(helper, MegaKind.CALM, new BlockPos(70, 2, 70), Direction.NORTH);
		beacon.look(level);
		BlockPos near = beacon.getBlockPos().offset(40, 0, 0);
		BlockPos far = beacon.getBlockPos().offset(90, 0, 0);
		Checks.assertTrue(helper, com.vnap.mega.MegaWork.calm(level, near) && !com.vnap.mega.MegaWork.calm(level, far), "calm within 64, not beyond");
		level.setBlock(beacon.getBlockPos().above(2).relative(Direction.SOUTH, 2), Blocks.AIR.defaultBlockState(), 3);
		beacon.look(level);
		Checks.assertTrue(helper, !com.vnap.mega.MegaWork.calm(level, near), "its tip taken off, the calm is gone");
		helper.succeed();
	}

	// random ticks: in 800 a sprout grows with three in four, and three of nine once failed it; in 1600, nine in ten
	@GameTest(template = "workshop_platform", timeoutTicks = 1600)
	public static void theDomeMakesItsFarmGrowAndTheWellGathersLore(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LifeSettings.megastructures(true);
		MegaControllerBlockEntity dome = build(helper, MegaKind.DOME, new BlockPos(40, 2, 20), Direction.NORTH);
		BlockPos middle = dome.getBlockPos().relative(Direction.SOUTH, 3);
		List<BlockPos> crops = new java.util.ArrayList<>();
		// wet farmland all round, a sprout on every other cell: each alone grows its fastest (a third of its random ticks)
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				BlockPos soil = middle.offset(x, 0, z);
				level.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 3);
				// wheat grows only in light: the platform is deep under
				level.setBlock(soil.above(3), Blocks.LIGHT.defaultBlockState(), 3);
				if (x % 2 == 0 && z % 2 == 0) {
					level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState(), 3);
					crops.add(soil.above());
				}
			}
		}
		dome.look(level);
		Checks.assertTrue(helper, dome.formed(), "the dome stands whole: " + MegaKind.DOME.shape().wrong(level, dome.getBlockPos(), dome.facing()));
		MegaControllerBlockEntity well = build(helper, MegaKind.WELL, new BlockPos(60, 2, 40), Direction.NORTH);
		well.look(level);
		helper.runAfterDelay(50, () -> net.minecraft.world.entity.ExperienceOrb.award(level, net.minecraft.world.phys.Vec3.atCenterOf(well.getBlockPos().above(2)), 60));
		helper.succeedWhen(() -> {
			int grown = 0;
			for (BlockPos crop : crops) if (level.getBlockState(crop).is(Blocks.WHEAT) && level.getBlockState(crop).getValue(net.minecraft.world.level.block.CropBlock.AGE) > 0) grown++;
			Checks.assertTrue(helper, grown >= 4, "the farm under the dome grows: " + grown + " of 9");
			Checks.assertTrue(helper, well.store >= 60, "the well drew the lore in: " + well.store);
		});
	}

	@GameTest(template = "workshop_platform", timeoutTicks = 400)
	public static void theDrainTakesTheWaterAway(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LifeSettings.megastructures(true);
		MegaControllerBlockEntity drain = build(helper, MegaKind.DRAIN, new BlockPos(80, 4, 20), Direction.NORTH);
		List<BlockPos> water = new java.util.ArrayList<>();
		for (int x = 3; x <= 5; x++) {
			for (int z = -1; z <= 1; z++) {
				BlockPos pos = drain.getBlockPos().offset(x, -2, z);
				level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
				water.add(pos);
			}
		}
		drain.look(level);
		helper.succeedWhen(() -> {
			for (BlockPos pos : water) Checks.assertTrue(helper, level.getFluidState(pos).isEmpty(), "water left at " + pos);
		});
	}

	@GameTest(template = "workshop_platform", timeoutTicks = 300)
	public static void theSawmillFellsATreeButNotALogHouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LifeSettings.megastructures(true);
		BlockPos at = new BlockPos(30, 2, 40);
		MegaControllerBlockEntity mill = build(helper, MegaKind.SAWMILL, at, Direction.NORTH);
		helper.setBlock(at.north(), Blocks.CHEST);
		// a tree: four logs on dirt, leaves round its top; a log house's corner: logs on dirt, no leaves
		BlockPos tree = helper.absolutePos(at.offset(6, 0, 6));
		level.setBlock(tree.below(), Blocks.DIRT.defaultBlockState(), 3);
		for (int y = 0; y < 4; y++) level.setBlock(tree.above(y), Blocks.OAK_LOG.defaultBlockState(), 3);
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) level.setBlock(tree.offset(dx, 3, dz), Blocks.OAK_LEAVES.defaultBlockState(), 3);
		BlockPos house = helper.absolutePos(at.offset(-6, 0, 6));
		level.setBlock(house.below(), Blocks.DIRT.defaultBlockState(), 3);
		for (int y = 0; y < 3; y++) level.setBlock(house.above(y), Blocks.SPRUCE_LOG.defaultBlockState(), 3);
		mill.look(level);
		helper.succeedWhen(() -> {
			Checks.assertTrue(helper, !level.getBlockState(tree.above(2)).is(Blocks.OAK_LOG), "the tree is felled");
			Checks.assertTrue(helper, level.getBlockState(house.above(2)).is(Blocks.SPRUCE_LOG), "the log house stands");
			if (!(level.getBlockEntity(helper.absolutePos(at.north())) instanceof Container chest)) throw new IllegalStateException("no chest");
			int logs = 0;
			for (int slot = 0; slot < chest.getContainerSize(); slot++) if (chest.getItem(slot).is(Items.OAK_LOG)) logs += chest.getItem(slot).getCount();
			Checks.assertTrue(helper, logs == 4, "its logs are in the chest: " + logs);
		});
	}

	@GameTest(template = "workshop_platform", timeoutTicks = 300, batch = "mega_town")
	public static void aQuarryInATownSellsWhatItsChestsCannotHoldToTheStall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LifeSettings.megastructures(true);
		BlockPos bell = helper.absolutePos(new BlockPos(10, 2, 80));
		level.setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
		com.vnap.village.market.Shops.Shop shop = new com.vnap.village.market.Shops.Shop(level.dimension(), bell);
		com.vnap.village.market.Shops.get(level.getServer()).shops().add(shop);
		com.vnap.village.market.Market.openShop(level, shop, com.vnap.dialogue.Family.day(level));
		int before = shop.count(Items.COBBLESTONE);
		BlockPos at = new BlockPos(30, 3, 80);
		MegaControllerBlockEntity quarry = build(helper, MegaKind.QUARRY, at, Direction.NORTH);
		quarry.dig.half = 2;
		for (BlockPos stone : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(28, 2, 80)), helper.absolutePos(new BlockPos(32, 2, 84)))) {
			level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
		}
		// fuel in a chest beside it with no room for what it digs: a full chest of coal
		helper.setBlock(at.north(), Blocks.CHEST);
		if (!(level.getBlockEntity(helper.absolutePos(at.north())) instanceof Container chest)) {
			Checks.fail(helper, "no chest");
			return;
		}
		for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(Items.COAL, 64));
		quarry.look(level);
		helper.succeedWhen(() -> Checks.assertTrue(helper, shop.count(Items.COBBLESTONE) > before,
				"the town's stall took the stone: " + shop.count(Items.COBBLESTONE) + " (was " + before + ")"));
	}

	@GameTest(template = "workshop_platform", timeoutTicks = 100, batch = "mega_town")
	public static void townsLinkedByWaygatesBuyEachOthersSurplusFirst(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LifeSettings.megastructures(true);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Gates.get(level.getServer()).forget(DyeColor.LIME);
		com.vnap.village.market.Shops.Shop here = town(helper, new BlockPos(8, 2, 8));
		com.vnap.village.market.Shops.Shop there = town(helper, new BlockPos(92, 2, 92));
		MegaControllerBlockEntity a = build(helper, MegaKind.GATE, new BlockPos(14, 2, 8), Direction.SOUTH);
		MegaControllerBlockEntity b = build(helper, MegaKind.GATE, new BlockPos(86, 2, 92), Direction.SOUTH);
		a.key(player, new ItemStack(MegaBlocks.key(DyeColor.LIME)));
		b.key(player, new ItemStack(MegaBlocks.key(DyeColor.LIME)));
		a.look(level);
		b.look(level);
		// the other town has bread to spare, this one none
		here.stock.removeIf(lot -> lot.item.is(Items.BREAD));
		there.put(new ItemStack(Items.BREAD), com.vnap.village.market.Market.target(Items.BREAD) + 40, false);
		int before = there.count(Items.BREAD);
		int tillBefore = there.till;
		com.vnap.village.market.Market.restock(level, here);
		Checks.assertTrue(helper, there.count(Items.BREAD) < before && there.till > tillBefore,
				"the linked town sold its spare bread: " + before + " -> " + there.count(Items.BREAD) + ", till " + tillBefore + " -> " + there.till);
		Checks.assertTrue(helper, here.count(Items.BREAD) > 0, "and this town has bread");
		Gates.get(level.getServer()).forget(DyeColor.LIME);
		player.discard();
		helper.succeed();
	}

	private static com.vnap.village.market.Shops.Shop town(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = helper.absolutePos(at);
		level.setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
		com.vnap.village.market.Shops.Shop shop = new com.vnap.village.market.Shops.Shop(level.dimension(), bell);
		com.vnap.village.market.Shops.get(level.getServer()).shops().add(shop);
		com.vnap.village.market.Market.openShop(level, shop, com.vnap.dialogue.Family.day(level));
		return shop;
	}
}
