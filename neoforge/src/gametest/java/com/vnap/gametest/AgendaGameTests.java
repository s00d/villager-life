package com.vnap.gametest;

import com.vnap.village.life.Agenda;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A villager's agenda: it walks to the top thing on it, stays its while, says its word; a more pressing thing comes first. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class AgendaGameTests {
	private AgendaGameTests() {
	}

	@GameTest(batch = "agenda", template = "home_platform", timeoutTicks = 900)
	public static void aVillagerGoesToTheTopOfItsAgendaAndTheMorePressingFirst(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(20, 2, 20));
		BlockPos visit = helper.absolutePos(new BlockPos(30, 2, 20));
		BlockPos feast = helper.absolutePos(new BlockPos(20, 2, 32));
		long now = helper.getLevel().getGameTime();
		// the feast lasts a while, the visit is just getting there
		Agenda.add(villager, new Agenda.Item("visit", visit, Agenda.VISIT, 1, 0.6F, null, null, null));
		Agenda.add(villager, new Agenda.Item("feast", feast, Agenda.EVENT, 1, 0.6F, level -> level.getGameTime() < now + 300, null, null));
		// asked again, the same thing stays on (no restart of the walk)
		Agenda.add(villager, new Agenda.Item("feast", feast, Agenda.EVENT, 1, 0.6F, level -> true, null, null));
		Agenda.Item top = Agenda.top(helper.getLevel(), villager);
		Checks.assertTrue(helper, top != null && top.why().equals("feast"), "the feast first");
		boolean[] feasted = {false};
		helper.onEachTick(() -> feasted[0] |= villager.blockPosition().closerThan(feast, 2.0));
		helper.succeedWhen(() -> {
			Checks.assertTrue(helper, feasted[0], "at the feast first");
			Checks.assertTrue(helper, !Agenda.has(villager, "feast") && !Agenda.has(villager, "visit") && villager.blockPosition().closerThan(visit, 3.0),
					"then the visit, both done: at " + helper.relativePos(villager.blockPosition()));
		});
	}
}
