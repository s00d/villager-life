package com.vnap.mood;

import com.vnap.village.script.VillageScriptDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/** Starts the one rite {@link VillageRites} picked, and plays its cue. */
public final class VillageRiteActions {
    private VillageRiteActions() {
    }

    public static void offer(ServerLevel level, MoodState state) {
        if (VillageRites.clearEphemeral(state)) {
            VillageMoodSavedData.get(level).markDirty();
        }
        if (state.script != ScriptKind.NONE || level.getGameTime() < state.scriptCooldownUntilTick) {
            return;
        }
        ScriptKind next = VillageRites.nextMissing(state);
        if (next == null) {
            return;
        }
        switch (next) {
            case BUILD_MONUMENT -> VillageScriptDirector.tryStartBuildMonument(level, state);
            case BUILD_ALTAR -> VillageScriptDirector.tryStartBuildAltar(level, state);
            case SUMMON -> VillageScriptDirector.tryStartSummon(level, state);
            default -> VillageScriptDirector.tryStartRite(level, state, next);
        }
    }

    public static void playCue(ServerLevel level, ScriptKind kind, @Nullable BlockPos pos) {
        if (pos == null) {
            return;
        }
        switch (kind) {
            case BUILD_FESTOON -> {
                level.playSound(null, pos, com.vnap.sound.GolemSounds.RITE_BRIGHT, SoundSource.NEUTRAL, 0.9F, 1.0F);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.6, 0.3, 0.6, 0.0);
            }
            case BUILD_VIGIL -> {
                level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.7F, 1.1F);
                level.sendParticles(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.4, 0.2, 0.4, 0.01);
            }
            case BUILD_FESTIVAL -> {
                level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.2F);
                level.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 6, 0.5, 0.4, 0.5, 1.0);
            }
            case BUILD_MOURNING -> {
                level.playSound(null, pos, com.vnap.sound.GolemSounds.RITE_MOURN, SoundSource.NEUTRAL, 0.9F, 1.0F);
                level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.01);
            }
            case BUILD_PAVILION -> {
                level.playSound(null, pos, com.vnap.sound.GolemSounds.RITE_BRIGHT, SoundSource.BLOCKS, 0.9F, 1.1F);
                level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 10, 0.5, 0.6, 0.5, 0.02);
            }
            case BUILD_PYRE -> {
                level.playSound(null, pos, com.vnap.sound.GolemSounds.RITE_DARK, SoundSource.HOSTILE, 1.0F, 1.0F);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 8, 0.4, 0.2, 0.4, 0.01);
            }
            case BUILD_MONUMENT -> level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
            case BUILD_ALTAR -> level.playSound(null, pos, com.vnap.sound.GolemSounds.RITE_DARK, SoundSource.HOSTILE, 0.9F, 0.9F);
            default -> {
            }
        }
    }
}
