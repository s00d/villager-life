package com.vnap.neo.mixin;

import com.vnap.neo.Bridge;
import com.vnap.neo.neoforge.event.entity.player.SweepAttackEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's SweepAttackEvent: every melee blow, once whether it sweeps is known, before it lands. */
@SuppressWarnings("unused")
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
	//? if >=1.21.2 {
	/*@Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z", ordinal = 0))
	*///?} else
	@Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", ordinal = 0))
	private void vnap$sweep(Entity target, CallbackInfo ci) {
		if ((Object) this instanceof Player self) Bridge.fire(new SweepAttackEvent(self, target));
	}
}
