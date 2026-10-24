package com.vnap.village.life;

import com.vnap.village.town.TownRules;
import com.google.common.collect.ImmutableMap;
import com.vnap.village.home.Hamlets;
import com.vnap.village.home.Placements;
import com.vnap.village.script.WorkerControl;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;


/** A grown-up carries a thing the village set aside for an occasion (Placements) to its place and sets it down. */
final class PlaceBehavior extends Behavior<Villager> {
	private static final int CHECK_EVERY = 100;
	private static final int WORK = 60;
	/** Jobs someone is on the way to: one pair of hands each. */
	private static final Claims CLAIMED = new Claims();

	private final LifeGate gate;
	private final ErrandRunner runner = new ErrandRunner();
	private Placements.@Nullable Job job;
	private @Nullable Errand errand;
	private boolean finished;
	private long nextCheck;

	PlaceBehavior(LifeGate gate) {
		super(ImmutableMap.of(), 600, 1200);
		this.gate = gate;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		long now = level.getGameTime();
		if (now < nextCheck || gate.busy || com.vnap.dialogue.Infants.minding(level, villager)) return false;
		nextCheck = now + CHECK_EVERY + villager.getRandom().nextInt(CHECK_EVERY);
		if (villager.isBaby() || villager.isSleeping() || villager.isNoAi() || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
				|| com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG) || Hamlets.leaving(villager)) {
			return false;
		}
		Placements.Job found = Placements.forHands(level, villager, TownRules.NEAR_BELL, pos -> CLAIMED.taken(level, pos, villager));
		if (found == null) return false;
		job = found;
		CLAIMED.take(found.pos(), villager);
		errand = Errand.at("place", found.pos(), new ItemStack(found.state().getBlock().asItem()), WORK, SoundEvents.WOOD_PLACE, null,
				() -> Placements.place(level, found));
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
		Placements.Job taken = job;
		if (current == null || taken == null || !Placements.get(level.getServer()).jobs().contains(taken)) {
			finished = true;
			return;
		}
		if (runner.tick(level, villager, current) != ErrandRunner.Result.WORKING) finished = true;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !finished && villager.isAlive() && !villager.isSleeping() && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		gate.busy = false;
		Placements.Job taken = job;
		if (taken != null) CLAIMED.release(taken.pos());
		job = null;
		errand = null;
		WorkerControl.hold(villager, ItemStack.EMPTY);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		nextCheck = gameTime + CHECK_EVERY;
	}
}
