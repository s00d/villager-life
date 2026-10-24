package com.vnap.village.life;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One trip of a chore: walk next to a block or an animal, work there with a tool for a while, then change the
 * world. {@code start} runs when the villager sets off (opening a gate), {@code done} when the work is over.
 */
public final class Errand {
    final String chore;
    final BlockPos at;
    final @Nullable Entity mob;
    final ItemStack tool;
    final int workTicks;
    final @Nullable SoundEvent sound;
    final @Nullable ParticleOptions particle;
    final Runnable done;
    @Nullable Runnable start;
    @Nullable Runnable cancel;

    Errand(String chore, BlockPos at, @Nullable Entity mob, ItemStack tool, int workTicks,
           @Nullable SoundEvent sound, @Nullable ParticleOptions particle, Runnable done) {
        this.chore = chore;
        this.at = at.immutable();
        this.mob = mob;
        this.tool = tool;
        this.workTicks = workTicks;
        this.sound = sound;
        this.particle = particle;
        this.done = done;
    }

    static Errand at(String chore, BlockPos at, ItemStack tool, int workTicks, @Nullable SoundEvent sound,
                     @Nullable ParticleOptions particle, Runnable done) {
        return new Errand(chore, at, null, tool, workTicks, sound, particle, done);
    }

    static Errand to(String chore, Entity mob, ItemStack tool, int workTicks, @Nullable SoundEvent sound,
                     @Nullable ParticleOptions particle, Runnable done) {
        return new Errand(chore, mob.blockPosition(), mob, tool, workTicks, sound, particle, done);
    }

    Errand onStart(Runnable run) {
        this.start = run;
        return this;
    }

    Errand onCancel(Runnable run) {
        this.cancel = run;
        return this;
    }

    Errand withHooks(Errand from) {
        this.start = from.start;
        this.cancel = from.cancel;
        return this;
    }

    public String chore() {
        return chore;
    }
}
