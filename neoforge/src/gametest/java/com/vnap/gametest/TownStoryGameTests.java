package com.vnap.gametest;

import com.vnap.dialogue.Family;
import com.vnap.dialogue.Temperament;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.Mayors;
import com.vnap.village.town.Chronicle;
import com.vnap.village.town.Roads;
import com.vnap.village.town.TownRules;
import com.vnap.village.town.TownStory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
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

/** The town's storyteller and chronicle, and its people's own natures. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class TownStoryGameTests {
	private static final BlockPos BELL = new BlockPos(50, 2, 50);

	private TownStoryGameTests() {
	}

	private static Roads.Network town(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BELL, Blocks.BELL);
		BlockPos bell = helper.absolutePos(BELL);
		close(level, bell);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		return Roads.get(level.getServer()).of(level.dimension(), office.bell);
	}

	private static void close(ServerLevel level, BlockPos bell) {
		Mayors.get(level.getServer()).offices().removeIf(office -> office.bell.closerThan(bell, TownRules.SPAN));
		Roads.get(level.getServer()).networks().removeIf(network -> network.bell.closerThan(bell, TownRules.SPAN));
		VillageMoodSavedData.get(level).houses().removeIf(house -> house.bell.closerThan(bell, TownRules.SPAN));
	}

	private static Villager villager(GameTestHelper helper, Roads.Network network, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(helper.getLevel().dimension(), network.bell));
		((VillagerLifeData) villager).vnap$setFemale(false);
		Family.state(villager).genderKnown = true;
		return villager;
	}

	@GameTest(batch = "story", template = "home_platform", timeoutTicks = 200)
	public static void aSickVillagerGetsWellWithThePlayersHoneyAndTheChronicleSaysSo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			Villager sick = villager(helper, network, BELL.offset(3, 0, 3));
			TownStory.describe(level, network.bell, TownStory.Kind.SICK);
			TownStory.tick(level, network);
			Checks.assertTrue(helper, sick.getPersistentData().contains("vnap_sick_until"), "someone fell ill");
			Checks.assertTrue(helper, network.chronicle.stream().anyMatch(entry -> entry.key().equals("story.sick")), "the chronicle has it");
			TownStory.tick(level, network);
			Checks.assertTrue(helper, sick.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and drags its feet");
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack honey = new ItemStack(Items.HONEY_BOTTLE);
			Family.give(level, player, sick, honey);
			Checks.assertTrue(helper, honey.isEmpty() && !sick.getPersistentData().contains("vnap_sick_until"), "the player's honey cures it");
			Checks.assertTrue(helper, network.chronicle.stream().anyMatch(entry -> entry.key().equals("story.cured_by")), "and the chronicle thanks the player");
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}

	@GameTest(batch = "story", template = "home_platform", timeoutTicks = 200)
	public static void aChildGetsLostAndWandersOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		helper.runAfterDelay(20, () -> {
			try {
				Villager child = villager(helper, network, BELL.offset(2, 0, 2));
				child.setAge(-24000);
				TownStory.describe(level, network.bell, TownStory.Kind.LOST_CHILD);
				TownStory.tick(level, network);
				Checks.assertTrue(helper, network.chronicle.stream().anyMatch(entry -> entry.key().equals("story.lost")), "a child gets lost");
				// (a player coming near finds it: a mock player is not in the level's players, so that is checked live)
				TownStory.tick(level, network);
				Checks.assertTrue(helper, com.vnap.village.life.Agenda.has(child, "lost"), "it means to wander off to where it hides");
				helper.succeed();
			} finally {
				close(level, network.bell);
			}
		});
	}

	@GameTest(batch = "story", template = "home_platform", timeoutTicks = 100)
	public static void aVillagersNatureIsItsOwnAndItsRoutineScenesComeSomeDays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			Villager a = villager(helper, network, BELL.offset(2, 0, 2));
			float clock = Temperament.clock(level, a);
			Checks.assertTrue(helper, Math.abs(clock - Temperament.clock(level, a)) < 1.0E-6F, "the same clock every time");
			int days = 0;
			for (long day = 0; day < 200; day++) if (Temperament.scene(a, "morning", day, 0.5F)) days++;
			Checks.assertTrue(helper, days > 60 && days < 140, "a scene about half the days, " + days);
			boolean first = Temperament.scene(a, "morning", 7, 0.5F);
			boolean again = Temperament.scene(a, "morning", 7, 0.5F);
			Checks.assertTrue(helper, first == again, "the same all day");
			Chronicle.note(level, network.bell, "flag");
			Checks.assertTrue(helper, network.chronicle.size() == 1, "a line in the chronicle");
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}
}
