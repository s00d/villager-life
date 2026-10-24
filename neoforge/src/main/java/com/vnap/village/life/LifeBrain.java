package com.vnap.village.life;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.Villager;

/**
 * Adds the life behaviours to a villager's vanilla activity packages. Each brain gets its own package
 * instances; the work and the idle package of one brain share one {@link LifeGate}: vanilla keeps a behaviour of
 * the last activity running until it ends, and the same chore of both packages ran twice at once.
 */
public final class LifeBrain {
    /** Vanilla priorities of the walk-to-job-site memory and the stroll/work-at-POI pick in the work package. */
    private static final int WALK_PRIORITY = 2;
    private static final int STROLL_PRIORITY = 5;
    /** Vanilla priority of the idle package's pick of strolls, visits and play. */
    private static final int IDLE_STROLL_PRIORITY = 2;
    /** Vanilla priority of the meet package's walks: to the bell, strolling about it, socializing there. */
    private static final int MEET_WALK_PRIORITY = 2;
    /** Vanilla priority of a child's games (tag, strolls, visits) in the play package. */
    private static final int PLAY_PRIORITY = 5;

    /** The gate of the work package just built: Villager.registerBrainGoals builds the idle package right after. */
    private static final ThreadLocal<LifeGate> WORK_GATE = new ThreadLocal<>();

    private LifeBrain() {
    }

    /** The behaviours running in the villager's brain now (what /vnap who shows; vanilla's list of them is a debug accessor). */
    @SuppressWarnings("unchecked")
    public static java.util.List<BehaviorControl<? super Villager>> running(Villager villager) {
        java.util.List<BehaviorControl<? super Villager>> out = new java.util.ArrayList<>();
        for (var byActivity : ((com.vnap.mixin.BrainAccessor<Villager>) (Object) villager.getBrain()).vnap$behaviours().values()) {
            for (var behaviours : byActivity.values()) {
                for (BehaviorControl<? super Villager> behavior : behaviours) {
                    if (behavior.getStatus() == net.minecraft.world.entity.ai.behavior.Behavior.Status.RUNNING) out.add(behavior);
                }
            }
        }
        return out;
    }

    /** Building by hand now (a house, a road, a family's bed or room, a workshop): up on what it builds. */
    public static boolean building(Villager villager) {
        for (BehaviorControl<? super Villager> behavior : running(villager)) {
            if (behavior instanceof HouseBehavior || behavior instanceof RoadBehavior || behavior instanceof FamilyBuildBehavior
                    || behavior instanceof WorkBehavior) return true;
        }
        return false;
    }

    /**
     * Whether the villager is about some business of the mod (a build, a road, the stall, a table to carry, its agenda):
     * a word to it is said on the way, it is not stopped to listen (holding every listener still left builders and
     * helpers standing in heaps).
     */
    public static boolean busy(Villager villager) {
        if (Agenda.busy(villager)) return true;
        for (BehaviorControl<? super Villager> behavior : running(villager)) {
            if (behavior.getClass().getPackageName().equals(LifeBrain.class.getPackageName()) && !(behavior instanceof Gated)) return true;
        }
        return false;
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work(
            ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> vanilla) {
        LifeGate gate = new LifeGate();
        WORK_GATE.set(gate);
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> out = ImmutableList.builder();
        for (Pair<Integer, ? extends BehaviorControl<? super Villager>> entry : vanilla) {
            int priority = entry.getFirst();
            out.add(priority == WALK_PRIORITY || priority == STROLL_PRIORITY
                    ? Pair.of(priority, new Gated(entry.getSecond(), gate))
                    : entry);
        }
        out.add(Pair.of(1, new NursingBehavior(gate)));
        out.add(Pair.of(1, new AgendaBehavior(gate)));
        // the counter first: the shopkeeper is a husband's wife and a builder too, and the shop shut while it was those
        out.add(Pair.of(2, new MarketBehavior(gate)));
        out.add(Pair.of(2, new HouseBehavior(gate)));
        out.add(Pair.of(2, new FamilyBuildBehavior(gate)));
        out.add(Pair.of(2, new HomeChoreBehavior(gate)));
        // the clock's winding first: a short job once a day, not to wait behind a day of road work
        out.add(Pair.of(2, new ClockBehavior(gate)));
        out.add(Pair.of(2, new BakeBehavior(gate)));
        out.add(Pair.of(2, new RoadBehavior(gate)));
        out.add(Pair.of(2, new TurretBehavior(gate)));
        out.add(Pair.of(2, new GardenBehavior(gate)));
        out.add(Pair.of(2, new PlaceBehavior(gate)));
        out.add(Pair.of(3, new WorkBehavior(gate)));
        return out.build();
    }

    /**
     * The night: the town's watch (WatchBehavior) stays up; while it does, every vanilla rest behaviour (the walk
     * home, the bed, the strolls) steps aside. Its own gate: the night has no work or idle package to share one with.
     */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> rest(
            ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> vanilla) {
        LifeGate gate = new LifeGate();
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> out = ImmutableList.builder();
        for (Pair<Integer, ? extends BehaviorControl<? super Villager>> entry : vanilla) {
            out.add(Pair.of(entry.getFirst(), new Gated(entry.getSecond(), gate)));
        }
        out.add(Pair.of(0, new WatchBehavior(gate)));
        // the homeless go under a roof for the night (Shelter): out in the open a zombie took one, and the village
        // lost the rest to the zombie it became
        out.add(Pair.of(0, new AgendaBehavior(gate, com.vnap.village.town.Shelter.WHY)));
        return out.build();
    }

    /**
     * The afternoon gathering at the bell: a mother on her newborn's first day stays by the cradle instead (her
     * nursing holds the gate the gathering's walks step aside for). Villager.registerBrainGoals builds this package
     * between the work and the idle one: the work package's gate is still there.
     */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> meet(
            ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> vanilla) {
        LifeGate shared = WORK_GATE.get();
        LifeGate gate = shared != null ? shared : new LifeGate();
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> out = ImmutableList.builder();
        // only the walks to the bell and about it (priority 2) step aside: the schedule's switch (99) must still run,
        // or a mother at the cradle never went to bed
        for (Pair<Integer, ? extends BehaviorControl<? super Villager>> entry : vanilla) {
            out.add(entry.getFirst() == MEET_WALK_PRIORITY ? Pair.of(MEET_WALK_PRIORITY, new Gated(entry.getSecond(), gate)) : entry);
        }
        return out.add(Pair.of(1, new NursingBehavior(gate))).add(Pair.of(1, new AgendaBehavior(gate))).build();
    }

    /**
     * A child's play: its agenda (a lesson, hiding when it got lost, home to meet its father) over the games, whose
     * runs and strolls (priority 5) step aside meanwhile.
     */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> play(
            ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> vanilla) {
        LifeGate gate = new LifeGate();
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> out = ImmutableList.builder();
        for (Pair<Integer, ? extends BehaviorControl<? super Villager>> entry : vanilla) {
            out.add(entry.getFirst() == PLAY_PRIORITY ? Pair.of(PLAY_PRIORITY, new Gated(entry.getSecond(), gate)) : entry);
        }
        return out.add(Pair.of(1, new AgendaBehavior(gate))).build();
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> idle(
            ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> vanilla) {
        LifeGate shared = WORK_GATE.get();
        WORK_GATE.remove();
        LifeGate gate = shared != null ? shared : new LifeGate();
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> out = ImmutableList.builder();
        for (Pair<Integer, ? extends BehaviorControl<? super Villager>> entry : vanilla) {
            out.add(entry.getFirst() == IDLE_STROLL_PRIORITY ? Pair.of(IDLE_STROLL_PRIORITY, new Gated(entry.getSecond(), gate)) : entry);
        }
        return out
                .add(Pair.of(1, new ReclaimBehavior()))
                .add(Pair.of(1, new NursingBehavior(gate)))
                .add(Pair.of(1, new AgendaBehavior(gate)))
                .add(Pair.of(2, new MarketBehavior(gate)))
                .add(Pair.of(2, new HouseBehavior(gate)))
                .add(Pair.of(2, new FamilyBuildBehavior(gate)))
                .add(Pair.of(2, new HomeChoreBehavior(gate)))
                .add(Pair.of(2, new ClockBehavior(gate)))
                .add(Pair.of(2, new BakeBehavior(gate)))
                .add(Pair.of(2, new RoadBehavior(gate)))
                .add(Pair.of(2, new TurretBehavior(gate)))
                .add(Pair.of(2, new GardenBehavior(gate)))
                .add(Pair.of(2, new PlaceBehavior(gate)))
                .add(Pair.of(3, new FlowerBehavior(gate)))
                .build();
    }
}
