package com.vnap.village.life;

import com.vnap.village.town.TownRules;
import com.google.common.collect.ImmutableMap;
import com.vnap.block.clock.TowerClockBlockEntity;
import com.vnap.dialogue.Family;
import com.vnap.village.home.Hamlets;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Market;
import com.vnap.village.market.Shops;
import com.vnap.village.script.WorkerControl;
import com.vnap.village.town.Chapel;
import com.vnap.village.town.Roads;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A town job: a villager without a trade keeps the chapel's clock going. Once its spring runs low (less than a day
 * left) one walks into the tower, winds it by its weights and is paid a few coppers from the treasury.
 */
final class ClockBehavior extends Behavior<Villager> {
	private static final int CHECK_EVERY = 400;
	private static final int WORK = 80;
	/** Wound again when it would run down within this many ticks. */
	private static final long LOW = 24000L;
	/** Chapels someone is on the way to wind: one keeper each. */
	private static final Claims CLAIMED = new Claims();

	private final LifeGate gate;
	private final ErrandRunner runner = new ErrandRunner();
	private @Nullable BlockPos claimed;
	private @Nullable Errand errand;
	private boolean finished;
	private long nextCheck;
	/** The day this villager could not get to the weights: someone else winds the clock that day. */
	private long failedDay = Long.MIN_VALUE;

	ClockBehavior(LifeGate gate) {
		super(ImmutableMap.of(), 1200, 2400);
		this.gate = gate;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		long now = level.getGameTime();
		if (now < nextCheck || gate.busy || com.vnap.dialogue.Infants.minding(level, villager)) return false;
		nextCheck = now + CHECK_EVERY + villager.getRandom().nextInt(CHECK_EVERY);
		if (villager.isBaby() || villager.isSleeping() || villager.isNoAi() || !Workshops.workTime(level)
				|| !com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE)
				|| villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY) || com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG)
				|| Life.official(villager) || Hamlets.leaving(villager) || ErrandRunner.goingToAJob(villager)) {
			return false;
		}
		Roads.Network network = Roads.networkOf(level, villager);
		BlockPos winder = network == null ? null : Chapel.winder(network);
		if (network == null || winder == null || CLAIMED.taken(level, winder, villager) || !level.isPositionEntityTicking(winder)
				|| !winder.closerThan(villager.blockPosition(), TownRules.WORK_REACH)) {
			return false;
		}
		List<TowerClockBlockEntity> clocks = Chapel.clocks(level, network);
		if (clocks.isEmpty() || clocks.stream().noneMatch(clock -> clock.runsFor() < LOW) || failedDay == Family.day(level)) return false;
		// only one who can walk there takes it (not one stuck in a pond, holding the job for nothing)
		if (!villager.blockPosition().closerThan(winder, ErrandRunner.NEAR)) {
			net.minecraft.world.level.pathfinder.Path path = villager.getNavigation().createPath(winder, 1);
			if (path == null || !path.canReach()) return false;
		}
		claimed = winder.immutable();
		CLAIMED.take(claimed, villager);
		errand = Errand.at("wind_clock", winder, new ItemStack(Items.CLOCK), WORK, SoundEvents.CHAIN_STEP, null, () -> {
			for (TowerClockBlockEntity clock : Chapel.clocks(level, network)) clock.wind();
			Market.townWage(level, villager, network.bell, TownRules.CLOCK_WAGE);
		});
		return true;
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		gate.busy = true;
		finished = false;
		runner.reset();
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		Errand current = errand;
		ErrandRunner.Result result = current == null ? ErrandRunner.Result.FAILED : runner.tick(level, villager, current);
		if (result == ErrandRunner.Result.FAILED) failedDay = Family.day(level);
		if (result != ErrandRunner.Result.WORKING) finished = true;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !finished && villager.isAlive() && !villager.isSleeping() && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		gate.busy = false;
		BlockPos held = claimed;
		if (held != null) CLAIMED.release(held);
		claimed = null;
		errand = null;
		WorkerControl.hold(villager, ItemStack.EMPTY);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		nextCheck = gameTime + CHECK_EVERY;
	}
}
