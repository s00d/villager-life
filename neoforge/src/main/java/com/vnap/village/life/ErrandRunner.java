package com.vnap.village.life;

import com.vnap.village.script.WorkerControl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/** Walks a villager through one {@link Errand}: there on foot, a few swings with the tool, then the effect. */
final class ErrandRunner {
    static final double NEAR = 2.5;
    private static final int WALK_LIMIT = 400;
    private static final int SWING_EVERY = 10;
    private static final float SPEED = 0.55F;

    enum Result { WORKING, DONE, FAILED }

    private int timer;
    private boolean working;
    /**
     * Places each villager could not walk to today (an errand's block): the same errand picked again at the next
     * check (a bed to set, a thing to fetch, a plant on a ledge) fails at once instead of a walk at it every few
     * hundred ticks all day. Only the day's are kept.
     */
    private static final java.util.Map<java.util.UUID, java.util.Map<Long, Long>> UNREACHED = new java.util.HashMap<>();

    static boolean unreached(ServerLevel level, Villager villager, net.minecraft.core.BlockPos pos) {
        java.util.Map<Long, Long> places = UNREACHED.get(villager.getUUID());
        Long day = places == null ? null : places.get(pos.asLong());
        return day != null && day == com.vnap.dialogue.Family.day(level);
    }

    private static void unreachable(ServerLevel level, Villager villager, net.minecraft.core.BlockPos pos) {
        long today = com.vnap.dialogue.Family.day(level);
        java.util.Map<Long, Long> places = UNREACHED.computeIfAbsent(villager.getUUID(), id -> new java.util.HashMap<>());
        places.values().removeIf(day -> day != today);
        places.put(pos.asLong(), today);
    }

    static void forget(java.util.UUID villager) {
        UNREACHED.remove(villager);
    }

    static void clear() {
        UNREACHED.clear();
    }

    /**
     * A villager without a trade that has found a job site it may take walks there first (vanilla's
     * GoToPotentialJobSite): a chore of ours would only keep pulling it back, and it would never take up the trade.
     */
    static boolean goingToAJob(Villager villager) {
        return com.vnap.Mc.is(villager.getVillagerData(), net.minecraft.world.entity.npc.VillagerProfession.NONE)
                && villager.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    void reset() {
        timer = 0;
        working = false;
    }

    Result tick(ServerLevel level, Villager villager, Errand errand) {
        if (errand.mob != null && !errand.mob.isAlive()) {
            return Result.FAILED;
        }
        Vec3 target = errand.mob != null ? errand.mob.position() : Vec3.atBottomCenterOf(errand.at);
        if (!working && errand.mob == null && unreached(level, villager, errand.at)) return Result.FAILED;
        if (!working) {
            if (villager.position().distanceToSqr(target) <= NEAR * NEAR) {
                working = true;
                timer = 0;
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                villager.getNavigation().stop();
                return Result.WORKING;
            }
            if (++timer > WALK_LIMIT) {
                if (errand.mob == null) unreachable(level, villager, errand.at);
                return Result.FAILED;
            }
            if (timer % 20 == 1 || !villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, errand.mob != null
                        ? new WalkTarget(errand.mob, SPEED, 1)
                        : new WalkTarget(errand.at, SPEED, 1));
            }
            return Result.WORKING;
        }
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, errand.mob != null
                ? new EntityTracker(errand.mob, true) : new BlockPosTracker(errand.at));
        WorkerControl.hold(villager, errand.tool);
        if (timer % SWING_EVERY == 0) {
            com.vnap.Mc.swing(villager, InteractionHand.MAIN_HAND);
            if (errand.sound != null) {
                level.playSound(null, errand.at, errand.sound, SoundSource.NEUTRAL, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
            }
            if (errand.particle != null) {
                level.sendParticles(errand.particle, target.x, target.y + 0.6, target.z, 4, 0.25, 0.25, 0.25, 0.02);
            }
        }
        if (++timer < errand.workTicks) {
            return Result.WORKING;
        }
        errand.done.run();
        return Result.DONE;
    }
}
