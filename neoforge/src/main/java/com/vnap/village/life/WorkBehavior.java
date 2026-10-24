package com.vnap.village.life;

import com.google.common.collect.ImmutableMap;
import com.vnap.entity.VillagerLifeData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.script.WorkerControl;
import com.vnap.village.workshop.WorkshopState;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The work-hours task of a villager with a workshop: come back when it wandered off, build or repair the
 * workshop and its pen by hand, then go through the profession's chores. It runs only in the WORK activity, so
 * panic and the evening schedule (meeting, then bed) take over by themselves; unfinished work waits for the next
 * work hours.
 */
public final class WorkBehavior extends net.minecraft.world.entity.ai.behavior.Behavior<Villager> {
    /** Same as the vanilla job-site walk: closer than this the villager is "at work". */
    static final int AT_WORK = 9;
    /** Chores reach {@link Life#RADIUS} around the workshop; beyond this the villager walks back first. */
    static final int STRAYED = 16;
    /** Further than this the villager is somewhere else on purpose (a trip, a cart) and is left alone. */
    static final int RETURN_LIMIT = 64;
    private static final int CHECK_EVERY = 20;
    static final int CHORE_PAUSE = 100;
    private static final int FAILED_PAUSE = 400;

    private enum Task { RETURN, BUILD, CHORE }

    private final LifeGate gate;
    private final Hands hands = new Hands();
    private final ErrandRunner runner = new ErrandRunner();
    private @Nullable WorkshopState state;
    private @Nullable Task task;
    private @Nullable Life life;
    private @Nullable Errand errand;
    private boolean finished;
    private long nextCheck;

    WorkBehavior(LifeGate gate) {
        super(ImmutableMap.of(), 1200, 2400);
        this.gate = gate;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        if (now < nextCheck || gate.busy || com.vnap.dialogue.Infants.minding(level, villager)) {
            return false;
        }
        nextCheck = now + CHECK_EVERY;
        if (!canWork(level, villager)) {
            return false;
        }
        long day = com.vnap.dialogue.Family.day(level);
        WorkshopState prepared = Workshops.prepare(level, villager, day);
        if (prepared == null) {
            return false;
        }
        Task chosen = choose(level, villager, prepared, day);
        if (chosen == null) {
            return false;
        }
        state = prepared;
        task = chosen;
        return true;
    }

    private @Nullable Task choose(ServerLevel level, Villager villager, WorkshopState state, long day) {
        double distance = villager.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(state.anchor));
        if (distance > RETURN_LIMIT * RETURN_LIMIT) {
            return null;
        }
        if (distance > STRAYED * STRAYED) {
            return Task.RETURN;
        }
        if (state.job != null && state.pausedDay != com.vnap.dialogue.Family.day(level)) {
            return Task.BUILD;
        }
        if (state.tier <= 0) {
            return null;
        }
        if (level.getGameTime() >= state.nextChore && Chores.professionHasChores(state.profession)) {
            Life today = new Life(level, villager, state, day);
            Errand next = Chores.next(today);
            if (next != null) {
                life = today;
                errand = next;
                return Task.CHORE;
            }
            state.nextChore = level.getGameTime() + CHORE_PAUSE;
        }
        return distance > AT_WORK * AT_WORK ? Task.RETURN : null;
    }

    static boolean canWork(ServerLevel level, Villager villager) {
        return villager.isAlive() && !villager.isSleeping() && Workshops.workTime(level)
                && villager.getBrain().isActive(Activity.WORK)
                && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = true;
        finished = false;
        hands.reset();
        runner.reset();
        if (task == Task.RETURN && state != null) {
            walkHome(villager, state.anchor);
        }
        Errand first = errand;
        if (task == Task.CHORE && first != null && first.start != null) {
            first.start.run();
        }
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        WorkshopState current = state;
        if (current == null || task == null) {
            finished = true;
            return;
        }
        switch (task) {
            case RETURN -> {
                if (villager.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(current.anchor)) <= 4.0 * 4.0) {
                    finished = true;
                } else if (!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                    walkHome(villager, current.anchor);
                }
            }
            case BUILD -> build(level, villager, current, current.job);
            case CHORE -> chore(level, villager, current, gameTime);
        }
    }

    private void build(ServerLevel level, Villager villager, WorkshopState current, @Nullable BuildJob job) {
        if (job == null) {
            finished = true;
            return;
        }
        job.site = current.site;
        switch (hands.tick(level, villager, job)) {
            case DONE -> {
                Workshops.finish(villager, current);
                finished = true;
            }
            case STUCK -> {
                current.pausedDay = com.vnap.dialogue.Family.day(level);
                finished = true;
            }
            case WAITING -> finished = true;
            default -> {
            }
        }
    }

    private void chore(ServerLevel level, Villager villager, WorkshopState current, long gameTime) {
        Errand now = errand;
        Life today = life;
        if (now == null || today == null) {
            finished = true;
            return;
        }
        switch (runner.tick(level, villager, now)) {
            case DONE -> {
                Errand then = today.pending;
                today.pending = null;
                if (then != null) {
                    errand = then;
                    runner.reset();
                    if (then.start != null) {
                        then.start.run();
                    }
                } else {
                    errand = null;
                    current.nextChore = gameTime + CHORE_PAUSE;
                    finished = true;
                }
            }
            case FAILED -> {
                cancel();
                current.nextChore = gameTime + FAILED_PAUSE;
                finished = true;
            }
            default -> {
            }
        }
    }

    private void cancel() {
        Errand open = errand;
        errand = null;
        if (open != null && open.cancel != null) {
            open.cancel.run();
        }
    }

    private static void walkHome(Villager villager, BlockPos anchor) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(anchor, 0.6F, 2));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return !com.vnap.dialogue.Infants.minding(level, villager) && !finished && canWork(level, villager)
                && villager instanceof VillagerLifeData data && data.vnap$workshop() == state
                && !com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG);
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        WorkshopState current = state;
        if (errand != null && current != null) {
            current.nextChore = gameTime + CHORE_PAUSE;
        }
        cancel();
        gate.busy = false;
        hands.reset();
        runner.reset();
        state = null;
        task = null;
        life = null;
        WorkerControl.hold(villager, ItemStack.EMPTY);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextCheck = gameTime + CHECK_EVERY;
    }
}
