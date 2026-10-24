package com.vnap.gametest;

import com.vnap.village.town.TownRules;
import com.vnap.block.LifeBlocks;
import com.vnap.block.turret.ArrowTurretBlockEntity;
import com.vnap.village.home.Mayors;
import com.vnap.village.town.Roads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The wall's crossbows: they shoot monsters in sight, never through a friend, and a villager keeps them loaded. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class TurretGameTests {
	private TurretGameTests() {
	}

	private static ArrowTurretBlockEntity turret(GameTestHelper helper, BlockPos at, int arrows) {
		helper.setBlock(at, LifeBlocks.ARROW_TURRET.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof ArrowTurretBlockEntity crossbow)) throw new IllegalStateException("no crossbow");
		if (arrows > 0) crossbow.load(new ItemStack(Items.ARROW, arrows));
		return crossbow;
	}

	@GameTest(template = "platform", timeoutTicks = 200)
	public static void aCrossbowShootsAMonsterInSight(GameTestHelper helper) {
		ArrowTurretBlockEntity crossbow = turret(helper, new BlockPos(50, 2, 50), 16);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 50, 2, 40);
		zombie.setNoAi(true);
		helper.succeedWhen(() -> {
			Checks.assertTrue(helper, zombie.getHealth() < zombie.getMaxHealth() || !zombie.isAlive(), "the zombie is hit");
			Checks.assertTrue(helper, crossbow.arrows() < 16, "arrows were spent: " + crossbow.arrows());
		});
	}

	@GameTest(template = "platform", timeoutTicks = 120)
	public static void aCrossbowNeverShootsThroughAVillager(GameTestHelper helper) {
		ArrowTurretBlockEntity crossbow = turret(helper, new BlockPos(50, 2, 50), 16);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 50, 2, 40);
		zombie.setNoAi(true);
		Villager villager = helper.spawn(EntityType.VILLAGER, 50, 2, 45);
		villager.setNoAi(true);
		helper.runAfterDelay(100, () -> {
			Checks.assertTrue(helper, crossbow.arrows() == 16, "no shot with a villager in the way: " + crossbow.arrows());
			Checks.assertTrue(helper, villager.getHealth() == villager.getMaxHealth(), "the villager is unhurt");
			helper.succeed();
		});
	}

	@GameTest(batch = "life_day", template = "platform", timeoutTicks = 1600)
	public static void aVillagerWithoutATradeLoadsTheCrossbows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.onEachTick(() -> {
			if (com.vnap.Mc.dayTime(level) % 24000L > 8500L) com.vnap.Mc.setDayTime(level, 2500L);
		});
		com.vnap.Mc.setDayTime(level, 2500L);
		helper.setBlock(new BlockPos(50, 2, 50), net.minecraft.world.level.block.Blocks.BELL);
		BlockPos bell = helper.absolutePos(new BlockPos(50, 2, 50));
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Roads roads = Roads.get(level.getServer());
		roads.networks().removeIf(old -> old.bell.closerThan(office.bell, TownRules.SPAN));
		Roads.Network network = roads.of(level.dimension(), office.bell);
		ArrowTurretBlockEntity crossbow = turret(helper, new BlockPos(56, 2, 50), 0);
		network.turrets.add(crossbow.getBlockPos());
		// the armoury has its arrows already: no stall here
		network.armoury.add(Items.ARROW, 64);
		Villager carrier = helper.spawn(EntityType.VILLAGER, 46, 2, 50);
		carrier.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		helper.succeedWhen(() -> {
			Checks.assertTrue(helper, crossbow.arrows() >= 16, "the crossbow is loaded: " + crossbow.arrows());
			roads.networks().remove(network);
			Mayors.get(level.getServer()).offices().remove(office);
		});
	}
}
