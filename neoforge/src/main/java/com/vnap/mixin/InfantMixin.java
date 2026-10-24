package com.vnap.mixin;

import com.vnap.dialogue.Infants;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A newborn lying in its cradle is not there to hit, pick or push: clicks reach the cradle, nobody shoves it out. */
@Mixin(LivingEntity.class)
@SuppressWarnings("unused")
public abstract class InfantMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void vnap$notPickedInTheCradle(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Villager villager && Infants.lying(villager)) cir.setReturnValue(false);
	}

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void vnap$notPushedInTheCradle(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Villager villager && Infants.lying(villager)) cir.setReturnValue(false);
	}
}
