package com.vnap.gametest;

import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.entity.VillagerLifeData;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Coins;
import com.vnap.village.town.Errands;
import com.vnap.village.town.Roads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Errands on the notice board: a household's unmet want posted, taken by a player, brought and paid for. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class ErrandGameTests {
	private static final BlockPos BELL = new BlockPos(50, 2, 50);

	private ErrandGameTests() {
	}

	private record Town(Roads.Network network, Mayors.Office office, BlockPos bell) {
	}

	private static Town town(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BELL, Blocks.BELL);
		BlockPos bell = helper.absolutePos(BELL);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		Roads roads = Roads.get(level.getServer());
		roads.networks().removeIf(old -> old.bell.closerThan(office.bell, com.vnap.village.town.TownRules.SPAN));
		return new Town(roads.of(level.dimension(), office.bell), office, bell);
	}

	private static void close(ServerLevel level, Town town) {
		Errands.get(level.getServer()).all().removeIf(errand -> errand.bell.closerThan(town.bell(), 64));
		Mayors.get(level.getServer()).offices().remove(town.office());
		Roads.get(level.getServer()).networks().remove(town.network());
	}

	private static Villager villager(GameTestHelper helper, int x) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, BELL.offset(x, 0, 3));
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), helper.absolutePos(BELL)));
		((VillagerLifeData) villager).vnap$setFemale(false);
		Family.state(villager).genderKnown = true;
		return villager;
	}

	@GameTest(batch = "errands", template = "home_platform", timeoutTicks = 100)
	public static void anUnmetWantGoesOnTheBoardAndAPlayerBringsIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Town town = town(helper);
		try {
			long day = Family.day(level);
			Villager villager = villager(helper, 2);
			Family.state(villager).wants.add(new FamilyState.Want("minecraft:white_bed", 1, day - 1));
			Roads.get(level.getServer()).plan(level, town.network(), day, 0);
			List<Errands.Errand> errands = Errands.of(level, town.bell());
			Checks.assertTrue(helper, errands.size() == 1 && errands.get(0).villager.equals(villager.getUUID()), "the want is on the board: " + errands.size());
			Errands.Errand errand = errands.get(0);
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			Checks.assertTrue(helper, Errands.take(level, player, errand.id), "taken");
			Checks.assertTrue(helper, Errands.active(level.getServer(), player.getUUID()) == errand, "it is the player's errand now");
			ItemStack bed = new ItemStack(Items.RED_BED);
			player.setItemInHand(InteractionHand.MAIN_HAND, bed);
			Checks.assertTrue(helper, Family.give(level, player, villager, player.getMainHandItem()), "handed over (any bed will do)");
			Checks.assertTrue(helper, bed.isEmpty() && player.getInventory().countItem(Items.RED_BED) == 0, "the bed is the villager's");
			Checks.assertTrue(helper, villager.getInventory().countItem(Items.RED_BED) == 1, "in its pockets now, for its house");
			Checks.assertTrue(helper, Family.state(villager).wants.isEmpty(), "the want is met");
			Checks.assertTrue(helper, Coins.count(player.getInventory()) == errand.reward, "the player is paid " + errand.reward);
			Checks.assertTrue(helper, Errands.of(level, town.bell()).isEmpty(), "the errand is done");
			helper.succeed();
		} finally {
			close(level, town);
		}
	}

	@GameTest(batch = "errands", template = "home_platform", timeoutTicks = 100)
	public static void aWorkshopsOrderIsNoErrandAndBreaksNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Town town = town(helper);
		try {
			long day = Family.day(level);
			Villager villager = villager(helper, 2);
			// a workshop's order for its chest, unmet since yesterday: it read as an item id and took the server down
			Family.state(villager).wants.add(new FamilyState.Want(com.vnap.village.market.Market.CHEST + "minecraft:flint", 8, day - 1));
			Family.state(villager).wants.add(new FamilyState.Want("nosuchmod:gone", 1, day - 1));
			Roads.get(level.getServer()).plan(level, town.network(), day, 0);
			Checks.assertTrue(helper, Errands.of(level, town.bell()).isEmpty(), "neither goes on the board: " + Errands.of(level, town.bell()).size());
			helper.succeed();
		} finally {
			close(level, town);
		}
	}

	@GameTest(batch = "errands", template = "home_platform", timeoutTicks = 100)
	public static void oneErrandAtATimeAndAGivenUpOneGoesBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Town town = town(helper);
		try {
			long day = Family.day(level);
			Villager a = villager(helper, 2);
			Villager b = villager(helper, 4);
			Family.state(a).wants.add(new FamilyState.Want("minecraft:candle", 2, day - 1));
			Family.state(b).wants.add(new FamilyState.Want(com.vnap.village.market.Market.FOOD, 3, day - 1));
			Roads.get(level.getServer()).plan(level, town.network(), day, 0);
			List<Errands.Errand> errands = Errands.of(level, town.bell());
			Checks.assertTrue(helper, errands.size() == 2, "two errands up");
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			Errands.take(level, player, errands.get(0).id);
			Errands.take(level, player, errands.get(1).id);
			Checks.assertTrue(helper, errands.get(0).taker == null && player.getUUID().equals(errands.get(1).taker), "taking another puts the first back");
			Player other = helper.makeMockPlayer(GameType.SURVIVAL);
			Checks.assertFalse(helper, Errands.take(level, other, errands.get(1).id), "someone else's errand is not for the taking");
			// food: any food will do, all of it at once
			Errands.Errand food = errands.get(1).kind.equals(Errands.FOOD) ? errands.get(1) : errands.get(0);
			Errands.take(level, player, food.id);
			Villager eater = food.villager.equals(a.getUUID()) ? a : b;
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF, 2));
			Checks.assertTrue(helper, Family.give(level, player, eater, player.getMainHandItem()) && player.getMainHandItem().getCount() == 2, "too few: nothing taken");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKED_BEEF, 5));
			Checks.assertTrue(helper, Family.give(level, player, eater, player.getMainHandItem()) && player.getMainHandItem().getCount() == 2, "three taken");
			Errands.take(level, player, errands.get(0).id);
			Checks.assertTrue(helper, Errands.cancel(level, player) && Errands.active(level.getServer(), player.getUUID()) == null, "given up");
			helper.succeed();
		} finally {
			close(level, town);
		}
	}

	@GameTest(batch = "errands", template = "home_platform", timeoutTicks = 100)
	public static void aWeddingWishIsAnErrandAndOldOnesComeDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Town town = town(helper);
		try {
			long day = Family.day(level);
			Villager groom = villager(helper, 2);
			Villager bride = villager(helper, 4);
			((VillagerLifeData) bride).vnap$setFemale(true);
			Family.state(groom).courting = bride.getUUID();
			Family.state(bride).courting = groom.getUUID();
			Family.state(groom).wish = "minecraft:cake";
			Roads.get(level.getServer()).plan(level, town.network(), day, 0);
			Errands.Errand wish = Errands.of(level, town.bell()).stream().filter(e -> e.kind.equals(Errands.WISH)).findFirst().orElse(null);
			Checks.assertTrue(helper, wish != null && wish.villager.equals(groom.getUUID()), "the groom's wish is on the board");
			if (wish == null) return;
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			Errands.take(level, player, wish.id);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CAKE));
			Checks.assertTrue(helper, Family.give(level, player, groom, player.getMainHandItem()) && Family.state(groom).wishGranted, "granted through the errand");
			// an errand nobody brought for days comes down
			Family.state(groom).wants.add(new FamilyState.Want("minecraft:lantern", 1, day - 1));
			Roads.get(level.getServer()).plan(level, town.network(), day + 1, 0);
			Checks.assertFalse(helper, Errands.of(level, town.bell()).isEmpty(), "the lantern is wanted");
			Roads.get(level.getServer()).plan(level, town.network(), day + 1 + 5, 0);
			Checks.assertTrue(helper, Errands.of(level, town.bell()).stream().noneMatch(e -> e.want.equals("minecraft:lantern") && e.posted <= day + 1),
					"after five days it is taken down (and put up anew if still wanted)");
			helper.succeed();
		} finally {
			close(level, town);
		}
	}
}
