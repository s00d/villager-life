package com.vnap.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.WanderingTrader;

/**
 * The hurt sound of a villager or a wandering trader: vanilla's own, which the sound mixins take off the entity so
 * that it plays only here; the voice pitch already tells a baby from an adult.
 */
public final class HurtEffectNetwork {

    private HurtEffectNetwork() {
    }

    public static void send(ServerLevel level, LivingEntity entity, boolean baby) {
        level.playSound(null, entity, entity instanceof WanderingTrader ? SoundEvents.WANDERING_TRADER_HURT : SoundEvents.VILLAGER_HURT,
                entity.getSoundSource(), 1.0F, entity.getVoicePitch());
    }
}
