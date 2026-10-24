package com.vnap.village.life;

import com.vnap.dialogue.VillagerHome;

import com.google.common.collect.ImmutableMap;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.Hamlets;
import com.vnap.village.home.Mayors;
import com.vnap.village.script.WorkerControl;
import com.vnap.village.town.Roads;
import com.vnap.village.workshop.Workshops;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jspecify.annotations.Nullable;

/**
 * A town job: villagers without a trade lay the town's roads in work hours, two to a stretch, a copper a block out
 * of the stretch's money (Roads). Houses come first (HouseBehavior goes before this), the mayor and the shopkeeper
 * have their own work, and a villager on its way to a job site of its own goes there.
 */
final class RoadBehavior extends Behavior<Villager> {
    private static final int CHECK_EVERY = 100;

    private final LifeGate gate;
    private final Hands hands = new Hands();
    private Roads.@Nullable Segment segment;
    private boolean finished;
    private long nextCheck;

    RoadBehavior(LifeGate gate) {
        super(ImmutableMap.of(), 1200, 2400);
        this.gate = gate;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        if (now < nextCheck || gate.busy || com.vnap.dialogue.Infants.minding(level, villager)) return false;
        nextCheck = now + CHECK_EVERY + villager.getRandom().nextInt(CHECK_EVERY);
        if (villager.isBaby() || villager.isSleeping() || !Workshops.workTime(level)
                || !com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE)
                || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY) || com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG)
                || Life.official(villager) || Hamlets.leaving(villager)
                || ErrandRunner.goingToAJob(villager)) {
            return false;
        }
        Roads roads = Roads.get(level.getServer());
        Roads.Segment found = roads.work(level, villager);
        if (found == null) return false;
        if (found.job().done()) {
            roads.finish(level, found);
            return false;
        }
        segment = found;
        roads.join(found, villager);
        return true;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = true;
        finished = false;
        hands.reset();
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Roads.Segment current = segment;
        if (current == null) {
            finished = true;
            return;
        }
        Roads roads = Roads.get(level.getServer());
        BuildJob job = current.job();
        switch (hands.tick(level, villager, job)) {
            case PLACED -> {
                com.vnap.village.market.Market.dayLabour(level, villager, current.site);
                roads.setDirty();
            }
            case DONE -> {
                roads.finish(level, current);
                finished = true;
            }
            case STUCK -> {
                // cells nobody can reach today: the stretch waits for tomorrow
                com.vnap.VillagerLife.trace("vnap stuck: {} {} left, {}: {}", current.label, job.remaining(), VillagerHome.nameOf(villager), hands.why);
                current.pausedDay = com.vnap.dialogue.Family.day(level);
                roads.setDirty();
                finished = true;
            }
            case WAITING -> finished = true;
            default -> {
            }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return !finished && villager.isAlive() && !villager.isSleeping() && Workshops.workTime(level)
                && !com.vnap.dialogue.Infants.minding(level, villager)
                && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
                && !com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG);
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = false;
        hands.reset();
        Roads.Segment current = segment;
        if (current != null) current.job().release(villager);
        if (current != null) Roads.get(level.getServer()).leave(current, villager);
        segment = null;
        WorkerControl.hold(villager, net.minecraft.world.item.ItemStack.EMPTY);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextCheck = gameTime + CHECK_EVERY;
    }
}
