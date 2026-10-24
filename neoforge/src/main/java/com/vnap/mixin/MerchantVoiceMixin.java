package com.vnap.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Villagers and the wandering trader do not hum and groan on their own: they speak their lines, and the hurt sound is
 * played by HurtEffectNetwork with the voice's pitch.
 */
@Mixin({Villager.class, WanderingTrader.class})
@SuppressWarnings("unused")
public abstract class MerchantVoiceMixin {
	@Inject(method = {"getAmbientSound", "getDeathSound"}, at = @At("HEAD"), cancellable = true)
	private void vnap$noIdleOrDeathSound(CallbackInfoReturnable<SoundEvent> sound) {
		sound.setReturnValue(SoundEvents.EMPTY);
	}

	@Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
	private void vnap$noHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> sound) {
		sound.setReturnValue(SoundEvents.EMPTY);
	}
}
