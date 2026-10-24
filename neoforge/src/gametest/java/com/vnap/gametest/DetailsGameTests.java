package com.vnap.gametest;

import com.vnap.dialogue.Family;
import com.vnap.entity.HandCart;
import com.vnap.entity.VillagerLifeData;
import com.vnap.item.LifeItems;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.Mayors;
import com.vnap.village.life.Chores;
import com.vnap.village.market.Market;
import com.vnap.village.market.Produce;
import com.vnap.village.market.Shops;
import com.vnap.village.workshop.ProfessionWork;
import com.vnap.village.workshop.WorkshopState;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The village's working details: the hand cart, the stall's crates, the signs and the bell, the beehive, the pot. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class DetailsGameTests {
	private static final long DAY = 1L;
	private static final BlockPos SITE = new BlockPos(50, 2, 50);

	/** A trade's workshop at tier 3 with its job block, its worker (no AI) of the bell's village. */
	private record Work(Villager villager, WorkshopState state, Container chest) {
		@org.jspecify.annotations.Nullable String perform(ServerLevel level) {
			return Chores.perform(level, villager, state, DAY);
		}
	}

	private static Work work(GameTestHelper helper, String profession, Block jobBlock, BlockPos bell) {
		ServerLevel level = helper.getLevel();
		BlockPos site = helper.absolutePos(SITE);
		BlockPos origin = site.subtract(Workshops.layout(profession).focal());
		String name = Workshops.schematicName(profession, 3);
		BuildJob job = BuildJob.all(name, Workshops.schematic(level, name), origin, Rotation.NONE, 0);
		while (!job.done()) {
			job.skipDone(level);
			job.placeNext(level, null);
		}
		level.setBlock(site, jobBlock.defaultBlockState(), 3);
		Villager villager = com.vnap.Mc.create(EntityType.VILLAGER, level);
		if (villager == null) throw new IllegalStateException("no villager");
		com.vnap.Mc.snap(villager, net.minecraft.world.phys.Vec3.atBottomCenterOf(site.offset(0, 0, -3)));
		villager.setNoAi(true);
		level.addFreshEntity(villager);
		VillagerProfession trade = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.withDefaultNamespace(profession));
		villager.setVillagerData(com.vnap.Mc.withLevel(com.vnap.Mc.withProfession(villager.getVillagerData(), trade), 5));
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		WorkshopState state = new WorkshopState(profession, site, origin, Rotation.NONE, 3);
		state.tier = 3;
		((VillagerLifeData) villager).vnap$setWorkshop(state);
		Container chest = ProfessionWork.chest(level, state);
		if (chest == null) throw new IllegalStateException("no chest in " + name);
		return new Work(villager, state, chest);
	}

	/** An open, stocked stall at the bell: a counter and its keeper standing at it. */
	private static Shops.Shop stall(GameTestHelper helper, BlockPos bell, BlockPos counter) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, 3000L);
		level.setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
		level.setBlock(counter, Market.decor(Shops.COUNTER) instanceof net.minecraft.world.item.BlockItem block
				? block.getBlock().defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
		Shops.Shop shop = new Shops.Shop(level.dimension(), bell);
		Shops.get(level.getServer()).shops().add(shop);
		Market.openShop(level, shop, Family.day(level));
		shop.counter = counter;
		Villager keeper = com.vnap.Mc.create(EntityType.VILLAGER, level);
		if (keeper == null) throw new IllegalStateException("no keeper");
		com.vnap.Mc.snap(keeper, net.minecraft.world.phys.Vec3.atBottomCenterOf(counter.south()));
		keeper.setNoAi(true);
		level.addFreshEntity(keeper);
		((VillagerLifeData) keeper).vnap$setShopkeeper(true);
		shop.keeper = keeper.getUUID();
		return shop;
	}

	private static void close(ServerLevel level, Shops.Shop shop) {
		Shops.get(level.getServer()).shops().remove(shop);
		Mayors.get(level.getServer()).offices().removeIf(office -> office.bell.equals(shop.bell));
	}

	/**
	 * In its arms a worker takes an armful to the stall; with more waiting it buys a hand cart, which stands by its
	 * chest, is loaded there, rolls behind it to the stall and back, and keeps what the stall had no room for.
	 */
	@GameTest(template = "workshop_platform", timeoutTicks = 400)
	public static void aFarmerCartsItsLogsToTheStall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = helper.absolutePos(SITE.offset(-10, 0, -4));
		Shops.Shop shop = stall(helper, bell, helper.absolutePos(SITE.offset(-10, 0, 0)));
		if (!Shops.open(level, shop)) {
			Checks.fail(helper, "the test's stall is open");
			return;
		}
		Work farm = work(helper, "farmer", Blocks.COMPOSTER, bell);

		// by hand: an armful a trip
		WorkshopState bows = new WorkshopState("fletcher", farm.state.anchor, farm.state.origin, Rotation.NONE, 3);
		net.minecraft.world.SimpleContainer quiver = new net.minecraft.world.SimpleContainer(27);
		quiver.addItem(new ItemStack(Items.ARROW, 40));
		Checks.assertTrue(helper, Produce.sell(level, farm.villager, shop, bows, quiver, Produce.HAND) > 0 && quiver.countItem(Items.ARROW) == 40 - Produce.HAND,
				"an armful of arrows goes to the stall, the rest waits");

		farm.chest.clearContent();
		farm.chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		farm.chest.setItem(1, new ItemStack(Items.OAK_LOG, 64));
		farm.chest.setItem(2, new ItemStack(Items.OAK_LOG, 20));
		farm.perform(level);
		Checks.assertTrue(helper, HandCart.of(level, farm.state.anchor) == null
				&& Family.state(farm.villager).wants.stream().anyMatch(want -> want.item.contains("hand_cart")),
				"logs by the armful are not worth the walk: the farmer means to buy a cart");
		Checks.assertTrue(helper, farm.chest.countItem(Items.OAK_LOG) == 148, "nothing was carried off by hand");

		farm.villager.getInventory().addItem(new ItemStack(LifeItems.HAND_CART));
		Checks.assertTrue(helper, "cart".equals(farm.perform(level)), "bought, the cart is set down first");
		HandCart cart = HandCart.of(level, farm.state.anchor);
		if (cart == null) {
			Checks.fail(helper, "the cart stands by the farm");
			return;
		}
		Checks.assertTrue(helper, farm.villager.getInventory().countItem(LifeItems.HAND_CART) == 0, "out of the farmer's pockets");
		Checks.assertTrue(helper, cart.blockPosition().equals(cart.park()) && cart.park().closerThan(Workshops.chestPos(farm.state), 4.0),
				"it stands by the chest");

		int purse = Family.state(farm.villager).coins;
		int shelf = shop.count(Items.OAK_LOG);
		Checks.assertTrue(helper, "sell".equals(farm.perform(level)), "the cart's trip");
		int sold = shop.count(Items.OAK_LOG) - shelf;
		Checks.assertTrue(helper, sold > Produce.HAND && sold <= Produce.CART, "a cartload, not an armful, reached the shelf: " + sold);
		Checks.assertTrue(helper, farm.chest.countItem(Items.OAK_LOG) == 148 - Produce.CART, "the cart took no more than it holds");
		Checks.assertTrue(helper, cart.count() == Produce.CART - sold, "what the stall had no room for rides home in the cart: " + cart.count() + " sold " + sold + " chest " + farm.chest.countItem(Items.OAK_LOG));
		Checks.assertTrue(helper, Family.state(farm.villager).coins > purse, "the farmer is paid");
		Checks.assertTrue(helper, !cart.pulled() && cart.blockPosition().equals(cart.park()), "back on its stand, let go");

		level.removeBlock(farm.state.anchor, false);
		helper.runAfterDelay(210, () -> {
			Checks.assertTrue(helper, !cart.isAlive(), "the farm is gone: the cart breaks up");
			close(level, shop);
			helper.succeed();
		});
	}

	private static net.minecraft.world.level.block.state.BlockState decor(String name, net.minecraft.core.Direction facing) {
		return ((net.minecraft.world.item.BlockItem) Market.decor(name)).getBlock().defaultBlockState().setValue(com.vnap.block.DecorBlock.FACING, facing);
	}

	private static com.vnap.block.decor.DecorBlockEntity decorAt(GameTestHelper helper, BlockPos pos) {
		if (helper.getLevel().getBlockEntity(pos) instanceof com.vnap.block.decor.DecorBlockEntity be) return be;
		throw new IllegalStateException("no decor at " + pos);
	}

	/** The stall's crates show what it has most of in food, two crates two goods, a heap by the stock. */
	@GameTest(template = "workshop_platform")
	public static void theStallsCratesShowItsGoods(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = helper.absolutePos(SITE.offset(-10, 0, -4));
		Shops.Shop shop = stall(helper, bell, helper.absolutePos(SITE.offset(-10, 0, 0)));
		shop.stock.removeIf(lot -> lot.item.has(net.minecraft.core.component.DataComponents.FOOD));
		shop.put(new ItemStack(Items.CARROT), 90, false);
		shop.put(new ItemStack(Items.BREAD), 20, false);
		BlockPos even = helper.absolutePos(SITE.offset(-8, 0, 2));
		BlockPos odd = even.east();
		if (((even.getX() + even.getZ()) & 1) != 0) {
			BlockPos swap = even;
			even = odd;
			odd = swap;
		}
		level.setBlock(even, decor("market_crate", net.minecraft.core.Direction.SOUTH), 3);
		level.setBlock(odd, decor("market_crate", net.minecraft.core.Direction.SOUTH), 3);
		com.vnap.block.decor.DecorBlockEntity first = decorAt(helper, even);
		com.vnap.block.decor.DecorBlockEntity second = decorAt(helper, odd);
		first.mechanic().tick(level, first);
		second.mechanic().tick(level, second);
		Checks.assertTrue(helper, first.items.getItem(0).is(Items.CARROT) && first.filled() == 6, "the most of it, carrots, heaped up: " + first.filled());
		Checks.assertTrue(helper, second.items.getItem(0).is(Items.BREAD) && second.filled() == 2, "the next crate the bread, a smaller heap: " + second.filled());
		shop.stock.removeIf(lot -> lot.item.is(Items.CARROT));
		first.mechanic().tick(level, first);
		Checks.assertTrue(helper, first.items.getItem(0).is(Items.BREAD), "sold out, the crate shows the next good");
		close(level, shop);
		helper.succeed();
	}

	/** The pot simmers while the wife cooks (its lid up), then goes quiet; the bell over a door rings when a player opens it. */
	@GameTest(template = "workshop_platform", timeoutTicks = 600)
	public static void thePotSimmersAndTheDoorBellRings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos potPos = helper.absolutePos(new BlockPos(3, 2, 3));
		level.setBlock(potPos, decor("cooking_pot", net.minecraft.core.Direction.NORTH), 3);
		com.vnap.block.decor.DecorBlockEntity pot = decorAt(helper, potPos);
		com.vnap.block.decor.TownDecor.simmer(pot);
		Checks.assertTrue(helper, com.vnap.block.decor.TownDecor.simmering(level.getBlockState(potPos)), "the pot simmers");
		BlockPos door = helper.absolutePos(new BlockPos(6, 2, 6));
		level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState(), 3);
		level.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF,
				net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
		level.setBlock(door.above(2), Blocks.STONE.defaultBlockState(), 3);
		BlockPos bellPos = door.above(2).south();
		level.setBlock(bellPos, decor("door_bell", net.minecraft.core.Direction.SOUTH), 3);
		net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		Checks.assertFalse(helper, com.vnap.block.decor.TownDecor.doorOpened(level, player, helper.absolutePos(new BlockPos(1, 2, 1))), "no bell over another door");
		Checks.assertTrue(helper, com.vnap.block.decor.TownDecor.doorOpened(level, player, door), "the bell over the door rings");
		Checks.assertTrue(helper, level.getBlockState(bellPos).getValue(com.vnap.block.DecorBlock.FILL) == 1, "it swings");
		helper.runAfterDelay(TownDecor_SIMMER + 60, () -> {
			Checks.assertFalse(helper, com.vnap.block.decor.TownDecor.simmering(level.getBlockState(potPos)), "the meal is done, the pot quiet");
			Checks.assertTrue(helper, level.getBlockState(bellPos).getValue(com.vnap.block.DecorBlock.FILL) == 0, "the bell still again");
			helper.succeed();
		});
	}

	private static final int TownDecor_SIMMER = com.vnap.block.decor.TownDecor.SIMMER;

	/** A sign finds its own trade: a workstation by it shows the trade's ware, a player's own item stays. */
	@GameTest(template = "workshop_platform")
	public static void aSignShowsTheTradeItHangsBy(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos signPos = helper.absolutePos(new BlockPos(4, 4, 4));
		level.setBlock(signPos, decor("shop_sign", net.minecraft.core.Direction.SOUTH), 3);
		level.setBlock(helper.absolutePos(new BlockPos(6, 2, 4)), Blocks.LECTERN.defaultBlockState(), 3);
		com.vnap.block.decor.DecorBlockEntity sign = decorAt(helper, signPos);
		Checks.assertTrue(helper, Items.BOOK.equals(com.vnap.block.decor.TownDecor.icon(level, signPos)), "a lectern by it: the librarian's book");
		sign.mechanic().tick(level, sign);
		Checks.assertTrue(helper, sign.items.getItem(0).is(Items.BOOK), "the sign shows it");
		net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		sign.mechanic().use(level, sign, player, net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.CAKE));
		for (int i = 0; i < 200; i++) sign.mechanic().tick(level, sign);
		Checks.assertTrue(helper, sign.items.getItem(0).is(Items.CAKE), "a player's own picture stays");
		helper.succeed();
	}

	/** A workshop with a door gets its front: a sign beside the door over head height, a bell inside over the door. */
	@GameTest(template = "workshop_platform")
	public static void aWorkshopWithADoorGetsItsSign(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// a hut 5x5, walls three high, a door on its south side
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				for (int y = 2; y <= 5; y++) {
					boolean wall = x == 0 || x == 4 || z == 0 || z == 4 || y == 5;
					helper.setBlock(new BlockPos(x + 3, y, z + 3), wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		BlockPos door = new BlockPos(5, 2, 7);
		helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH));
		helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH)
				.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.LECTERN);
		helper.setBlock(new BlockPos(12, 2, 12), Blocks.BELL);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), helper.absolutePos(new BlockPos(12, 2, 12)));
		com.vnap.village.town.Roads roads = com.vnap.village.town.Roads.get(level.getServer());
		roads.networks().removeIf(old -> old.bell.closerThan(office.bell, com.vnap.village.town.TownRules.SPAN));
		com.vnap.village.town.Roads.Network network = roads.of(level.dimension(), office.bell);
		BlockPos a = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos b = helper.absolutePos(new BlockPos(7, 5, 7));
		network.workshops.put(helper.absolutePos(new BlockPos(5, 2, 5)).asLong(), net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(a, b));
		com.vnap.village.town.Signage.plan(level, roads, network);
		com.vnap.village.town.Roads.Segment front = network.segments.stream().filter(seg -> com.vnap.village.town.Signage.LABEL.equals(seg.label)).findFirst().orElse(null);
		if (front == null) {
			Checks.fail(helper, "the workshop's front is queued: " + com.vnap.village.town.Signage.debugNear(level, roads, network, a));
			return;
		}
		BlockPos doorAbs = helper.absolutePos(door);
		boolean sign = false;
		boolean bell = false;
		for (com.vnap.village.script.SchematicSpec.PlacedBlock block : front.spec.blocks) {
			BlockPos at = block.rel().offset(front.origin);
			String name = block.state().getBlock() instanceof com.vnap.block.DecorBlock d ? d.name() : "";
			if (name.equals("shop_sign")) sign = at.getY() == doorAbs.getY() + 2 && at.getZ() == doorAbs.getZ() + 1 && Math.abs(at.getX() - doorAbs.getX()) == 1;
			if (name.equals("door_bell")) bell = at.equals(doorAbs.above(2).north());
		}
		Checks.assertTrue(helper, sign, "the sign beside the door, over head height, out from the wall");
		Checks.assertTrue(helper, bell, "the bell inside over the door");
		com.vnap.village.town.Signage.plan(level, roads, network);
		Checks.assertTrue(helper, network.segments.stream().filter(seg -> com.vnap.village.town.Signage.LABEL.equals(seg.label)).count() == 1, "one front, not two");
		roads.networks().remove(network);
		Mayors.get(level.getServer()).offices().remove(office);
		helper.succeed();
	}

	/**
	 * A grown farm keeps bees: the farmer buys a hive and sets it by a tree, a pair of bees from the drover, flowers
	 * by bone meal; a full hive is bottled calmly (bottles from the chest), the honey for sale.
	 */
	@GameTest(template = "workshop_platform")
	public static void aFarmersHiveIsStockedAndItsHoneyBottled(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = helper.absolutePos(SITE.offset(-10, 0, -4));
		Shops.Shop shop = stall(helper, bell, helper.absolutePos(SITE.offset(-10, 0, 0)));
		Work farm = work(helper, "farmer", Blocks.COMPOSTER, bell);
		farm.chest.clearContent();
		farm.villager.getInventory().addItem(new ItemStack(Items.BEEHIVE));
		java.util.List<String> done = new java.util.ArrayList<>();
		for (int i = 0; i < 12 && !done.contains("bees"); i++) done.add(String.valueOf(farm.perform(level)));
		BlockPos hive = Chores.hive(level, farm.state);
		if (hive == null) {
			Checks.fail(helper, "a hive by the farm's trees: " + done);
			return;
		}
		Checks.assertTrue(helper, farm.villager.getInventory().countItem(Items.BEEHIVE) == 0, "the hive came out of the farmer's pockets");
		Checks.assertTrue(helper, done.contains("bees") && level.getBlockEntity(hive) instanceof net.minecraft.world.level.block.entity.BeehiveBlockEntity combs
				&& combs.getOccupantCount() == 2, "a pair of bees in it: " + done);
		level.setBlock(hive, level.getBlockState(hive).setValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL, 5), 3);
		Checks.assertTrue(helper, !"honey".equals(farm.perform(level)) || farm.chest.countItem(Items.HONEY_BOTTLE) == 0,
				"no bottle in the chest, no honey");
		farm.chest.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 2));
		Checks.assertTrue(helper, "honey".equals(farm.perform(level)), "a full hive is bottled first thing");
		Checks.assertTrue(helper, farm.chest.countItem(Items.HONEY_BOTTLE) == 1 && farm.chest.countItem(Items.GLASS_BOTTLE) == 1
				&& net.minecraft.world.level.block.entity.BeehiveBlockEntity.getHoneyLevel(level.getBlockState(hive)) == 0, "a bottle of honey, the hive empty");
		Checks.assertTrue(helper, level.getEntitiesOfClass(net.minecraft.world.entity.animal.Bee.class, new net.minecraft.world.phys.AABB(hive).inflate(16.0),
				net.minecraft.world.entity.animal.Bee::isAngry).isEmpty(), "the bees stay calm");
		level.setBlock(hive.offset(3, -1, 0), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
		farm.chest.setItem(1, new ItemStack(Items.BONE_MEAL, 2));
		Checks.assertTrue(helper, com.vnap.village.market.Prices.price(new ItemStack(Items.HONEY_BOTTLE)) > 0
				&& com.vnap.village.market.Prices.price(new ItemStack(Items.BEEHIVE)) > 0 && Market.target(Items.BEEHIVE) > 0,
				"the stall sells hives and buys honey");
		close(level, shop);
		helper.succeed();
	}

	/** A player sits on a bench (a lower step, free above) with an empty hand, not on a step with a block over it. */
	@GameTest(template = "platform")
	public static void aPlayerSitsOnABench(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = helper.absolutePos(new BlockPos(3, 2, 3));
		level.setBlock(bench, Blocks.OAK_STAIRS.defaultBlockState(), 3);
		BlockPos covered = bench.east(2);
		level.setBlock(covered, Blocks.OAK_STAIRS.defaultBlockState(), 3);
		level.setBlock(covered.above(), Blocks.STONE.defaultBlockState(), 3);
		net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		com.vnap.Mc.snap(player, net.minecraft.world.phys.Vec3.atBottomCenterOf(bench.south()));
		Checks.assertFalse(helper, com.vnap.entity.Seat.sit(level, player, covered), "no seat under a block");
		Checks.assertTrue(helper, com.vnap.entity.Seat.sit(level, player, bench) && player.getVehicle() instanceof com.vnap.entity.Seat, "sat down on the bench");
		player.stopRiding();
		helper.runAfterDelay(3, () -> {
			Checks.assertTrue(helper, level.getEntitiesOfClass(com.vnap.entity.Seat.class, new net.minecraft.world.phys.AABB(bench).inflate(2.0)).isEmpty(), "got up, the seat is gone");
			helper.succeed();
		});
	}

	/**
	 * Where things stand in a home: against a wall, never a way through, never in the doorway, never in front of the
	 * chest; corners first; and no more than the room's share of things, the beds still reachable.
	 */
	@GameTest(template = "platform")
	public static void aHomeHasItsPlacesForThings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// a room 5x5 inside walls, roofed, a door in the south wall, the bed against the north wall, a chest in a corner
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 6; z++) {
				for (int y = 2; y <= 5; y++) {
					boolean wall = x == 0 || x == 6 || z == 0 || z == 6 || y == 5;
					helper.setBlock(new BlockPos(x + 2, y, z + 2), wall ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		BlockPos door = new BlockPos(5, 2, 8);
		helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH));
		helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH)
				.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		BlockPos head = new BlockPos(5, 2, 3);
		helper.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH)
				.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
		helper.setBlock(head.south(), Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH)
				.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
		BlockPos chest = new BlockPos(3, 2, 3);
		helper.setBlock(chest, Blocks.CHEST);
		BlockPos anchor = helper.absolutePos(head);
		java.util.Set<BlockPos> room = com.vnap.dialogue.FamilyHome.room(level, anchor);
		java.util.List<BlockPos> spots = com.vnap.dialogue.FamilyHome.decorSpots(level, anchor);
		Checks.assertTrue(helper, !spots.isEmpty(), "a home has places for things");
		BlockPos inside = helper.absolutePos(door.north());
		for (BlockPos spot : spots) {
			Checks.assertFalse(helper, spot.distManhattan(inside) <= 1 || spot.equals(helper.absolutePos(door.north(2))), "never in the doorway: " + spot);
			Checks.assertFalse(helper, spot.distManhattan(helper.absolutePos(chest)) == 1, "never in front of the chest: " + spot);
			boolean through = (room.contains(spot.north()) && room.contains(spot.south())) || (room.contains(spot.east()) && room.contains(spot.west()));
			Checks.assertFalse(helper, through, "never a way through: " + spot);
		}
		BlockPos first = spots.get(0);
		int walls = 0;
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(first.relative(side)).is(Blocks.STONE_BRICKS)) walls++;
		}
		Checks.assertTrue(helper, walls >= 2, "a corner first: " + first);
		int placed = 0;
		for (int i = 0; i < 20; i++) {
			java.util.List<BlockPos> left = com.vnap.dialogue.FamilyHome.decorSpots(level, anchor);
			if (left.isEmpty()) break;
			level.setBlock(left.get(0), decor("flower_vase", net.minecraft.core.Direction.NORTH), 3);
			placed++;
		}
		Checks.assertTrue(helper, placed >= 1 && placed + 1 <= Math.max(2, room.size() / 5), "no more than the room's share of things: " + placed + " in " + room.size());
		Checks.assertTrue(helper, com.vnap.dialogue.FamilyHome.keepsOpen(level, anchor, com.vnap.dialogue.FamilyHome.room(level, anchor)), "the bed is still reached from the door");
		helper.succeed();
	}
}
