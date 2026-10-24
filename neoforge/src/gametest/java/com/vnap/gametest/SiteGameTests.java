package com.vnap.gametest;

import com.vnap.dialogue.Family;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.life.Hands;
import com.vnap.village.market.Market;
import com.vnap.village.market.Prices;
import com.vnap.village.market.Shops;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A building site's money and goods, the stall it buys from, and a builder getting its blocks set wherever they are. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class SiteGameTests {
	private static final BlockPos BELL = new BlockPos(50, 1, 50);
	private static final BlockPos COUNTER = new BlockPos(50, 1, 56);

	private SiteGameTests() {
	}

	/**
	 * While its keeper builds the shop house the counter still serves (everything a new village builds waited on a shop
	 * that waited on itself); off too far, or with the shop house done and the keeper away, it is shut as before.
	 */
	@GameTest(batch = "site", template = "home_platform")
	public static void theStallServesWhileItsKeeperBuildsTheShopHouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shops.Shop shop = stall(helper);
		Villager keeper = Shops.keeper(level, shop);
		if (keeper == null) {
			Checks.fail(helper, "a keeper");
			return;
		}
		BlockPos bell = helper.absolutePos(BELL);
		com.vnap.Mc.snap(keeper, net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(COUNTER.offset(20, 0, 0))));
		Checks.assertFalse(helper, Shops.open(level, shop), "away from its counter, no shop house going up: shut");
		HouseProject stall = new HouseProject(bell.asLong(), bell, keeper.getUUID(), Shops.STALLS.get(0), "plains",
				helper.absolutePos(COUNTER.offset(16, 0, 0)), Rotation.NONE, Family.day(level));
		VillageMoodSavedData.get(level).houses().add(stall);
		Checks.assertTrue(helper, Shops.open(level, shop), "building its shop house: the counter serves");
		com.vnap.Mc.snap(keeper, net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(COUNTER.offset(80, 0, 0))));
		Checks.assertFalse(helper, Shops.open(level, shop), "off beyond the village: shut");
		com.vnap.Mc.snap(keeper, net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(COUNTER.offset(20, 0, 0))));
		stall.done = true;
		Checks.assertFalse(helper, Shops.open(level, shop), "the shop house done: it serves at its own counter, from there");
		VillageMoodSavedData.get(level).houses().remove(stall);
		close(level, shop);
		helper.succeed();
	}

	/**
	 * What comes down for a new build (an old roof, a house on a hall's ground) goes to the site's store, and what the
	 * build had no use for is sold to the stall when it is done: its worth goes to the owner, not into thin air.
	 */
	@GameTest(batch = "site", template = "home_platform")
	public static void whatIsTakenDownIsUsedAgainAndTheRestSold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shops.Shop shop = stall(helper);
		BlockPos bell = helper.absolutePos(BELL);
		Villager owner = helper.spawn(EntityType.VILLAGER, BELL.offset(-4, 0, 2));
		owner.setNoAi(true);
		owner.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		HouseProject house = new HouseProject(bell.asLong(), bell, owner.getUUID(), "cottage", "plains",
				helper.absolutePos(new BlockPos(20, 1, 20)), Rotation.NONE, Family.day(level));
		BlockPos old = helper.absolutePos(new BlockPos(22, 3, 22));
		level.setBlock(old, Blocks.OAK_LOG.defaultBlockState(), 3);
		house.clearing.add(old);
		HomeProjects.clearOne(level, house, old);
		Checks.assertTrue(helper, level.getBlockState(old).isAir() && house.site.count(Items.OAK_LOG) == 1, "the log taken down is in the site's store: " + house.site.stock);

		house.site.add(Items.OAK_LOG, 63);
		house.site.add(Items.COBBLESTONE, 10);
		int worth = 0;
		for (Map.Entry<Item, Integer> entry : Map.of(Items.OAK_LOG, 64, Items.COBBLESTONE, 10).entrySet()) {
			worth += (int) Math.floor(Prices.price(new ItemStack(entry.getKey())) * Market.WHOLESALE * entry.getValue());
		}
		int logs = shop.count(Items.OAK_LOG);
		int before = Family.state(owner).coins;
		Market.settle(level, house.site, owner, bell);
		Checks.assertTrue(helper, worth > 0 && Family.state(owner).coins - before == worth, "the rest sold at wholesale to the owner: " + (Family.state(owner).coins - before) + " of " + worth);
		Checks.assertTrue(helper, shop.count(Items.OAK_LOG) == logs + 64 && house.site.stock.isEmpty() && house.site.funds == 0, "the logs on the stall's shelf, the store empty");
		close(level, shop);
		helper.succeed();
	}

	/**
	 * The last block of a build with nowhere to stand by its column (over a pond) and its builder far off: the builder
	 * goes over and sets it from the bank, the build is not left waiting a day for it (Hands: FAR, LOOSE).
	 */
	@GameTest(batch = "site", template = "home_platform", timeoutTicks = 600)
	public static void theLastBlockIsSetFromWhereverTheBuilderGetsToIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 41; x <= 49; x++) for (int z = 41; z <= 49; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
		BlockPos target = helper.absolutePos(new BlockPos(45, 4, 45));
		SchematicSpec one = new SchematicSpec(1, 1, 1, List.of(), List.of(new SchematicSpec.PlacedBlock(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState())), BlockPos.ZERO);
		BuildJob job = BuildJob.all("pond_post", one, target, Rotation.NONE, 0);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 45));
		builder.setNoAi(true);
		Hands hands = new Hands();
		List<Hands.Result> results = new ArrayList<>();
		helper.onEachTick(() -> {
			if (job.done()) return;
			builder.setOnGround(true);
			Hands.Result result = hands.tick(level, builder, job);
			results.add(result);
			// its legs: a block a tick across the platform toward the walk target, up to the water's edge
			WalkTarget walk = builder.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
			if (walk != null) {
				BlockPos at = builder.blockPosition();
				BlockPos to = walk.getTarget().currentBlockPosition();
				BlockPos next = at.offset(Integer.signum(to.getX() - at.getX()), 0, Integer.signum(to.getZ() - at.getZ()));
				boolean ground = level.getBlockState(next).isAir() && level.getFluidState(next.below()).isEmpty() && !level.getBlockState(next.below()).isAir();
				if (ground) builder.teleportTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5);
				else builder.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			}
		});
		helper.succeedWhen(() -> {
			Checks.assertFalse(helper, results.contains(Hands.Result.STUCK), "never given up as out of reach");
			Checks.assertTrue(helper, job.done() && level.getBlockState(target).is(Blocks.OAK_PLANKS), "the post over the pond stands; the builder at "
					+ builder.blockPosition().subtract(target).toShortString() + " after " + results.stream().distinct().toList());
		});
	}

	@GameTest(batch = "sites", template = "platform", timeoutTicks = 20)
	public static void aBuilderStandsNoLowerThanTheGroundUnderItsBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// a wall block on a floor, and a pit dug right beside it: the floor is the builder's ground, not the pit's bottom
		helper.setBlock(new BlockPos(20, 1, 20), Blocks.STONE);
		helper.setBlock(new BlockPos(20, 2, 20), Blocks.STONE);
		BlockPos target = helper.absolutePos(new BlockPos(20, 3, 20));
		Checks.assertTrue(helper, Hands.groundUnder(level, target) == target.getY() - 3, "just under the floor block below it, was "
				+ (Hands.groundUnder(level, target) - target.getY()));
		BlockPos air = helper.absolutePos(new BlockPos(30, 2, 30));
		Checks.assertTrue(helper, Hands.groundUnder(level, air) < air.getY() - 1, "over open ground it is the ground below");
		helper.succeed();
	}

	private static Shops.Shop stall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, 3000L);
		BlockPos bell = helper.absolutePos(BELL);
		BlockPos counter = helper.absolutePos(COUNTER);
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
}
