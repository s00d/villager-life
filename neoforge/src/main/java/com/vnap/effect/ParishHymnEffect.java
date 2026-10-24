package com.vnap.effect;

import com.vnap.item.LifeItems;
import com.vnap.mood.MoodState;
import com.vnap.mood.VillageMoodApi;
import com.vnap.mood.VillageMoodSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** While the parish vestment is worn in a village: regeneration above zero, mining fatigue below. */
public class ParishHymnEffect extends MobEffect {
    public static final ParishHymnEffect INSTANCE = new ParishHymnEffect();

    public ParishHymnEffect() {
        super(MobEffectCategory.NEUTRAL, 0xE4C57A);
    }

    @Override
    //? if >=1.21.2 {
    /*public boolean applyEffectTick(ServerLevel server, LivingEntity entity, int amplifier) {
    *///?} else
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayer player) || !(com.vnap.Mc.levelOf(player) instanceof ServerLevel level)) {
            return true;
        }
        if (!player.getItemBySlot(EquipmentSlot.CHEST).is(LifeItems.PARISH_VESTMENT)) {
            return true;
        }
        VillageMoodApi.VillageRef ref = VillageMoodApi.resolve(level, player.blockPosition());
        if (ref == null) {
            return true;
        }
        MoodState mood = VillageMoodSavedData.get(level).get(ref.villageId());
        if (mood == null) {
            return true;
        }
        if (mood.mood > 0.0F) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, true, true));
        } else if (mood.mood < 0.0F) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 50, 0, true, true, true));
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }
}
