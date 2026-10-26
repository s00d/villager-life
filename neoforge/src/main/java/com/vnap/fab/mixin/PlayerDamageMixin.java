package com.vnap.fab.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player's actuallyHurt is its own: LivingDamageEvent.Post at its end too, with the health it cost. */
@SuppressWarnings("unused")
@Mixin(Player.class)
public abstract class PlayerDamageMixin {
	@Unique
	private float vnap$health;

	//? if >=1.21.2 {
	/*@Inject(method = "actuallyHurt", at = @At("HEAD"))
	private void vnap$before(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
		vnap$health = ((Player) (Object) this).getHealth();
	}

	@Inject(method = "actuallyHurt", at = @At("RETURN"))
	private void vnap$post(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
		if ((Object) this instanceof Player self && !self.isInvulnerableTo(level, source)) {
			((com.vnap.fab.DamageTaken) self).vnap$taken(Math.max(0, vnap$health - self.getHealth()));
			((com.vnap.fab.DamageTaken) self).vnap$posted(source);
		}
	}
	*///?} else {
	@Inject(method = "actuallyHurt", at = @At("HEAD"))
	private void vnap$before(DamageSource source, float amount, CallbackInfo ci) {
		vnap$health = ((Player) (Object) this).getHealth();
	}

	@Inject(method = "actuallyHurt", at = @At("RETURN"))
	private void vnap$post(DamageSource source, float amount, CallbackInfo ci) {
		if ((Object) this instanceof Player self && !self.isInvulnerableTo(source)) {
			((com.vnap.fab.DamageTaken) self).vnap$taken(Math.max(0, vnap$health - self.getHealth()));
			((com.vnap.fab.DamageTaken) self).vnap$posted(source);
		}
	}
	//?}
}
