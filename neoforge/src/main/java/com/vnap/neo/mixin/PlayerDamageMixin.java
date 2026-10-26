package com.vnap.neo.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player's actuallyHurt is its own: LivingDamageEvent.Post at its end too. */
@SuppressWarnings("unused")
@Mixin(Player.class)
public abstract class PlayerDamageMixin {
	@Redirect(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/ForgeHooks;onLivingDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F"),
			remap = false)
	private float vnap$damage(LivingEntity entity, DamageSource source, float amount) {
		float taken = net.minecraftforge.common.ForgeHooks.onLivingDamage(entity, source, amount);
		((com.vnap.neo.DamageTaken) entity).vnap$taken(taken);
		return taken;
	}

	@Inject(method = "actuallyHurt", at = @At("RETURN"))
	//? if >=1.21.2 {
	/*private void vnap$post(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
		if ((Object) this instanceof Player self && !self.isInvulnerableTo(level, source)) ((com.vnap.neo.DamageTaken) self).vnap$posted(source);
	}
	*///?} else {
	private void vnap$post(DamageSource source, float amount, CallbackInfo ci) {
		if ((Object) this instanceof Player self && !self.isInvulnerableTo(source)) ((com.vnap.neo.DamageTaken) self).vnap$posted(source);
	}
	//?}
}
