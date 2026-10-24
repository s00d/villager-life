package com.vnap.village.life;

import com.google.common.collect.ImmutableMap;
import com.vnap.entity.VillagerLifeData;
import com.vnap.village.workshop.Workshops;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;

/**
 * The WORK activity needs a job site memory, so a villager that lost it (too long away from it) idles
 * through work hours. This idle task takes the old job block back when nobody else holds it; the
 * schedule then switches the villager to WORK and {@link WorkBehavior} walks it home.
 */
final class ReclaimBehavior extends Behavior<Villager> {
    private static final int CHECK_EVERY = 40;

    private long nextCheck;

    ReclaimBehavior() {
        super(ImmutableMap.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        if (now < nextCheck) {
            return false;
        }
        nextCheck = now + CHECK_EVERY;
        if (villager instanceof VillagerLifeData data && data.vnap$workshop() != null && Workshops.workTime(level)) {
            Workshops.prepare(level, villager, com.vnap.dialogue.Family.day(level));
        }
        return false;
    }
}
