package com.vnap.village.life;

import com.google.common.collect.ImmutableMap;
import com.vnap.dialogue.Infants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;

/**
 * A mother on her newborn's first day: she stays by the cradle and rocks it now and then. She holds the life gate
 * while she does, so work, the stall, building and strolls all wait for the next day.
 */
final class NursingBehavior extends Behavior<Villager> {
    private static final int ROCK_EVERY = 240;

    private final LifeGate gate;
    private int timer;

    NursingBehavior(LifeGate gate) {
        super(ImmutableMap.of(), 1200, 2400);
        this.gate = gate;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return !gate.busy && !villager.isSleeping() && !villager.isBaby() && Infants.cradleOf(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = true;
        timer = level.getRandom().nextInt(ROCK_EVERY);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        BlockPos cradle = Infants.cradleOf(level, villager);
        if (cradle == null) return;
        if (!villager.blockPosition().closerThan(cradle, 2.5)) {
            if (++timer % 20 == 0) villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(cradle, 0.5F, 1));
            return;
        }
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(cradle));
        if (++timer % ROCK_EVERY == 0) Infants.rock(level, villager, cradle);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return villager.isAlive() && !villager.isSleeping() && Infants.cradleOf(level, villager) != null
                && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = false;
    }
}
