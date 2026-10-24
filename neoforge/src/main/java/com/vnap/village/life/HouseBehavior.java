package com.vnap.village.life;

import com.vnap.village.town.TownRules;
import com.google.common.collect.ImmutableMap;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.script.WorkerControl;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Work hours on a house: the homeless owner builds its own (in the work activity when it has a trade, in the
 * idle one otherwise) and villagers without a trade lend a hand. Storeys go up in {@link
 * com.vnap.village.build.BuildStages} order, so a builder always stands on a floor already laid.
 */
public final class HouseBehavior extends Behavior<Villager> {
    private static final int CHECK_EVERY = 40;

    private final LifeGate gate;
    private final Hands hands = new Hands();
    private final Wrecker wrecker = new Wrecker();
    private @Nullable HouseProject house;
    /** Helpers at work on each house (by its origin): a few hands, not the whole village in a heap on one site. */
    private static final java.util.Map<net.minecraft.core.GlobalPos, java.util.Set<java.util.UUID>> HELPING = new java.util.HashMap<>();

    /** A new world: nobody helps on its builds yet. */
    public static void clear() {
        HELPING.clear();
    }
    private boolean finished;
    private long nextCheck;

    HouseBehavior(LifeGate gate) {
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
        if (villager.isBaby() || villager.isSleeping() || !(Workshops.workTime(level) || HomeProjects.freeToMend(level, villager))
                || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
                || com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG)) {
            return false;
        }
        long day = com.vnap.dialogue.Family.day(level);
        HomeProjects.consider(level, villager, day);
        // a helper goes on to the next site when the nearest waits for the day or has its hands already
        HouseProject found = HomeProjects.forBuilder(level, villager, house -> house.pausedDay != day && !full(level, house, villager));
        // after work hours only a house being mended is worked on
        if (found != null && !found.mending && !Workshops.workTime(level)) return false;
        // a helper on its way to a job site of its own goes there first (the owner builds its own house all the same)
        if (found != null && !found.owner.equals(villager.getUUID()) && !found.base() && ErrandRunner.goingToAJob(villager)) return false;
        // while an old house is taken down the work is there, not at the hall's corner
        BlockPos work = found == null || found.clearing.isEmpty() ? found == null ? null : found.origin : found.clearing.get(0);
        if (found == null || work == null || found.pausedDay == com.vnap.dialogue.Family.day(level)) {
            return false;
        }
        if (!work.closerThan(villager.blockPosition(), TownRules.NEAR_BELL)) {
            // its own build at the village's edge (a hall, a shop house): the owner heads there on its agenda
            if (found.owner.equals(villager.getUUID()) && level.isLoaded(work)) {
                Agenda.go(villager, "own_build", work, Agenda.ROUTINE, null, null);
            }
            return false;
        }
        if (!found.owner.equals(villager.getUUID()) && full(level, found, villager)) return false;
        // an old house on the ground is taken apart first (the hall's own job is only known once it is down)
        if (found.clearing.isEmpty() && found.job(level).done()) {
            if (found.mending) HomeProjects.mended(level, found);
            else HomeProjects.finish(level, found);
            return false;
        }
        house = found;
        return true;
    }

    /** HOUSE_HELPERS (BASE_HELPERS on the town's base) others besides the owner are on it already. */
    private static boolean full(ServerLevel level, HouseProject house, Villager villager) {
        java.util.Set<java.util.UUID> helping = HELPING.getOrDefault(net.minecraft.core.GlobalPos.of(level.dimension(), house.origin), java.util.Set.of());
        if (helping.contains(villager.getUUID())) return false;
        return helping.stream().filter(id -> !id.equals(villager.getUUID()) && level.getEntity(id) instanceof Villager v && v.isAlive()).count()
                >= (house.base() ? TownRules.BASE_HELPERS : TownRules.HOUSE_HELPERS);
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = true;
        finished = false;
        hands.reset();
        wrecker.reset();
        HouseProject working = house;
        if (working != null && !working.owner.equals(villager.getUUID())) {
            HELPING.computeIfAbsent(net.minecraft.core.GlobalPos.of(level.dimension(), working.origin), k -> new java.util.HashSet<>()).add(villager.getUUID());
        }
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        HouseProject current = house;
        if (current == null || (current.done && !current.mending)) {
            finished = true;
            return;
        }
        if (level.getGameTime() % CHECK_EVERY == 0) HomeProjects.shooOut(level, current);
        if (!current.clearing.isEmpty()) {
            Wrecker.Result result = wrecker.tick(level, villager, current);
            if (result == Wrecker.Result.STUCK) {
                current.pausedDay = com.vnap.dialogue.Family.day(level);
                finished = true;
            }
            if (result != Wrecker.Result.WORKING) VillageMoodSavedData.get(level).markDirty();
            return;
        }
        BuildJob job = current.job(level);
        switch (hands.tick(level, villager, job)) {
            case PLACED -> {
                // a helper on someone else's build earns a day labourer's copper out of the build's money
                if (!current.owner.equals(villager.getUUID())) com.vnap.village.market.Market.dayLabour(level, villager, current.site);
                VillageMoodSavedData.get(level).markDirty();
            }
            case DONE -> {
                if (current.mending) HomeProjects.mended(level, current);
                else HomeProjects.finish(level, current);
                finished = true;
            }
            case STUCK -> {
                // a gap nobody can reach is not tried every evening: the household looks at it again in a few days
                if (current.mending) HomeProjects.giveUpMending(level, current, com.vnap.dialogue.Family.day(level));
                current.pausedDay = com.vnap.dialogue.Family.day(level);
                VillageMoodSavedData.get(level).markDirty();
                finished = true;
            }
            case WAITING -> finished = true;
            default -> {
            }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        HouseProject current = house;
        return !com.vnap.dialogue.Infants.minding(level, villager) && !finished && villager.isAlive() && !villager.isSleeping()
                && (Workshops.workTime(level) || (current != null && current.mending && HomeProjects.freeToMend(level, villager)))
                && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
                && !com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG);
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = false;
        hands.reset();
        wrecker.reset();
        HouseProject working = house;
        // its block and the trip to the stall are free for the others at once, not after the claim lapses
        if (working != null) working.job(level).release(villager);
        if (working != null) {
            java.util.Set<java.util.UUID> helping = HELPING.get(net.minecraft.core.GlobalPos.of(level.dimension(), working.origin));
            if (helping != null && helping.remove(villager.getUUID()) && helping.isEmpty()) HELPING.remove(net.minecraft.core.GlobalPos.of(level.dimension(), working.origin));
        }
        house = null;
        WorkerControl.hold(villager, ItemStack.EMPTY);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextCheck = gameTime + CHECK_EVERY;
    }
}
