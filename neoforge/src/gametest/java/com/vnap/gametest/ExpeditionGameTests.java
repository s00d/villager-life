package com.vnap.gametest;

import com.vnap.dialogue.Family;
import com.vnap.spire.Expeditions;
import com.vnap.spire.Spires;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Market;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Random;
import java.util.UUID;

/** Villagers up the spire (Expeditions): gone from the world, never from the town; back the same, paid within bounds. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class ExpeditionGameTests {
	private static final BlockPos BELL = new BlockPos(4, 2, 4);

	private ExpeditionGameTests() {
	}

	@GameTest(template = "platform", timeoutTicks = 200)
	public static void aVillagerUpTheSpireStaysOneOfItsTownAndComesBackTheSame(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BELL, Blocks.BELL);
		BlockPos bell = helper.absolutePos(BELL);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		BlockPos front = helper.absolutePos(new BlockPos(8, 2, 8));
		Spires.Spire spire = Spires.get(level.getServer()).add(level.dimension(), bell, 5L, front, front, new BoundingBox(0, 0, 0, 0, 0, 0));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		UUID id = villager.getUUID();
		String name = com.vnap.dialogue.VillagerHome.nameOf(villager);
		Family.state(villager).coins = 0;
		com.vnap.spire.SpireOutfits.wear(villager, 4);
		Expeditions.depart(level, villager, spire);
		Checks.assertTrue(helper, villager.isRemoved() && Expeditions.away(level.getServer(), id), "it stepped into the gate and is kept");
		Expeditions.tick(level.getServer());
		Checks.assertTrue(helper, office.residents.containsKey(id), "still one of the town while away");
		Checks.assertTrue(helper, Expeditions.bringBack(level.getServer(), id) && !Expeditions.away(level.getServer(), id), "it comes back");
		Villager back = level.getEntity(id) instanceof Villager v ? v : null;
		Checks.assertTrue(helper, back != null && name.equals(com.vnap.dialogue.VillagerHome.nameOf(back)), "the same villager, by its name and id");
		Checks.assertTrue(helper, back != null && com.vnap.spire.SpireOutfits.of(back) != 0, "still in the spire's clothes it went up in");
		int coins = back == null ? 0 : Market.funds(level, back);
		Checks.assertTrue(helper, coins >= 4 && coins <= 2 * 64, "paid within bounds: " + coins);
		if (back != null) back.discard();
		Spires.get(level.getServer()).all().remove(spire);
		Mayors.get(level.getServer()).offices().remove(office);
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void everyFloorHasClothesToFind(GameTestHelper helper) {
		Random random = new Random(1);
		for (int floor = 1; floor < 300; floor += 7) {
			int outfit = com.vnap.spire.SpireOutfits.ofFloor(floor, random);
			Checks.assertTrue(helper, outfit >= 1 && outfit <= com.vnap.spire.SpireOutfits.NAMES.size(), "a set of clothes there is: " + outfit);
		}
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aTripPaysBetweenAWageAndTwoSilverAndClimbsNoHigherThanThePlayers(GameTestHelper helper) {
		Spires.Spire spire = Spires.get(helper.getLevel().getServer()).add(helper.getLevel().dimension(), BlockPos.ZERO, 1L, BlockPos.ZERO, BlockPos.ZERO,
				new BoundingBox(0, 0, 0, 0, 0, 0));
		Random random = new Random(3);
		for (int i = 0; i < 2000; i++) {
			spire.best = 1 + random.nextInt(150);
			spire.expeditionFloor = random.nextInt(200);
			Expeditions.Result result = Expeditions.result(spire, 1 + random.nextInt(3), random);
			Checks.assertTrue(helper, result.coins() >= 8 && result.coins() <= 128, "pay in bounds: " + result);
			Checks.assertTrue(helper, result.floor() <= Math.max(10, spire.best), "no higher than its players: " + result + " best " + spire.best);
			// a hero is made over many trips, not one: the cheapest piece of the chapter it reached costs more than a trip pays
			com.vnap.spire.gear.GearTier tier = com.vnap.spire.Heroes.tier(result.floor());
			Checks.assertTrue(helper, result.coins() < com.vnap.spire.Heroes.price(tier, com.vnap.spire.gear.GearPiece.BOOTS),
					"no hero from one trip: " + result + " against " + com.vnap.spire.Heroes.price(tier, com.vnap.spire.gear.GearPiece.BOOTS));
		}
		Spires.get(helper.getLevel().getServer()).all().remove(spire);
		helper.succeed();
	}
}
