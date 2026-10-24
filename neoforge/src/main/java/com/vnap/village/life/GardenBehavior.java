package com.vnap.village.life;

import com.vnap.Nbt;
import com.google.common.collect.ImmutableMap;
import com.vnap.dialogue.Family;
import com.vnap.village.home.Hamlets;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Market;
import com.vnap.village.market.Shops;
import com.vnap.village.script.WorkerControl;
import com.vnap.village.town.Greenery;
import com.vnap.village.town.Roads;
import com.vnap.village.town.TownRules;
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
 * A town job: the gardener. A villager without a trade (or a worker after its shift), one a town at a time, sets the plants the town keeps along
 * its ways and in its nooks (Greenery) and puts back the ones picked or trampled: a few a round, by hand, from the
 * wild (no cost), a couple of coppers from the treasury for the morning's work. Nothing to set, nobody goes.
 */
final class GardenBehavior extends Behavior<Villager> {
	private static final int CHECK_EVERY = 600;
	private static final int WORK = 30;
	/** Plants a round. */
	static final int PER_ROUND = 6;
	private static final String PAID = "vnap_garden_paid";
	private static final Claims CLAIMED = new Claims();

	private final LifeGate gate;
	private final ErrandRunner runner = new ErrandRunner();
	private @Nullable BlockPos claimed;
	private @Nullable Errand errand;
	private Roads.@Nullable Network network;
	private List<BlockPos> todo = List.of();
	private int done;
	private int set;
	private boolean finished;
	private long nextCheck;

	GardenBehavior(LifeGate gate) {
		super(ImmutableMap.of(), 1200, 3600);
		this.gate = gate;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		long now = level.getGameTime();
		if (now < nextCheck || gate.busy || com.vnap.dialogue.Infants.minding(level, villager)) return false;
		nextCheck = now + CHECK_EVERY + villager.getRandom().nextInt(CHECK_EVERY);
		// one without a trade in the working day, a worker after its shift (a town of workers gets its green too)
		if (villager.isBaby() || villager.isSleeping() || villager.isNoAi() || !free(level, villager)
				|| com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NITWIT)
				|| villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY) || com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG)
				|| Life.official(villager) || Hamlets.leaving(villager) || ErrandRunner.goingToAJob(villager)) {
			return false;
		}
		Roads.Network town = Roads.networkOf(level, villager);
		if (town == null || town.greens.isEmpty() || CLAIMED.taken(level, town.bell, villager)) return false;
		// past the plants it could not get to today (nearest first, they would be the round again and again)
		List<BlockPos> missing = Greenery.missing(level, town, villager.blockPosition(), PER_ROUND * 4).stream()
				.filter(pos -> pos.closerThan(villager.blockPosition(), TownRules.WORK_REACH) && !ErrandRunner.unreached(level, villager, pos))
				.limit(PER_ROUND).toList();
		if (missing.isEmpty()) return false;
		claimed = town.bell;
		CLAIMED.take(town.bell, villager);
		network = town;
		todo = missing;
		done = 0;
		set = 0;
		errand = next(level);
		return errand != null;
	}

	/** One without a trade in the working day; a worker in its idle hour after the gathering (the idle activity). */
	static boolean free(ServerLevel level, Villager villager) {
		long time = Family.timeOfDay(level);
		return com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE)
				? com.vnap.village.workshop.Workshops.workTime(level) : time >= 11000L && time < 12000L;
	}

	private @Nullable Errand next(ServerLevel level) {
		Roads.Network town = network;
		if (town == null || done >= todo.size()) return null;
		BlockPos pos = todo.get(done++);
		return Errand.at("garden", pos, new ItemStack(Items.WOODEN_HOE), WORK, SoundEvents.GRASS_PLACE, null, () -> {
			if (Greenery.set(level, town, pos)) set++;
		});
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
		if (result == ErrandRunner.Result.WORKING) return;
		// a plant it cannot get to waits for another day's round (or another gardener)
		errand = next(level);
		runner.reset();
		if (errand == null) finished = true;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !finished && villager.isAlive() && !villager.isSleeping() && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		gate.busy = false;
		Roads.Network town = network;
		long day = Family.day(level);
		// once a day for the villager, not per behaviour: one runs in the working day's set, one in the idle hour's
		net.minecraft.nbt.CompoundTag data = villager.getPersistentData();
		if (town != null && set > 0 && Nbt.getLong(data, PAID) != day + 1) {
			data.putLong(PAID, day + 1);
			Market.townWage(level, villager, town.bell, TownRules.GARDEN_WAGE);
		}
		BlockPos held = claimed;
		if (held != null) CLAIMED.release(held);
		claimed = null;
		errand = null;
		network = null;
		todo = List.of();
		WorkerControl.hold(villager, ItemStack.EMPTY);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		nextCheck = gameTime + CHECK_EVERY;
	}
}
