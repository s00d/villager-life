package com.vnap.village.life;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.Villager;

/** A vanilla behaviour that steps aside while the villager's {@link LifeGate} is busy. */
final class Gated implements BehaviorControl<Villager> {
    private final BehaviorControl<? super Villager> inner;
    private final LifeGate gate;

    Gated(BehaviorControl<? super Villager> inner, LifeGate gate) {
        this.inner = inner;
        this.gate = gate;
    }

    @Override
    public Behavior.Status getStatus() {
        return inner.getStatus();
    }

    @Override
    public boolean tryStart(ServerLevel level, Villager entity, long gameTime) {
        return !gate.busy && inner.tryStart(level, entity, gameTime);
    }

    @Override
    public void tickOrStop(ServerLevel level, Villager entity, long gameTime) {
        if (gate.busy) {
            inner.doStop(level, entity, gameTime);
        } else {
            inner.tickOrStop(level, entity, gameTime);
        }
    }

    @Override
    public void doStop(ServerLevel level, Villager entity, long gameTime) {
        inner.doStop(level, entity, gameTime);
    }

    //? if >=26.1 {
    /*@Override
    public java.util.Set<net.minecraft.world.entity.ai.memory.MemoryModuleType<?>> getRequiredMemories() {
        return inner.getRequiredMemories();
    }
    *///?}

    @Override
    public String debugString() {
        return inner.debugString();
    }
}
