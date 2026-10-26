package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.CriticalHitEvent;
import com.vnap.fab.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's events of a player where NeoForge fires them: waking up, a critical hit (the mod reads only the hits that are). */
@SuppressWarnings("unused")
@Mixin(Player.class)
public abstract class PlayerEventsMixin {
	@Inject(method = "stopSleepInBed", at = @At("HEAD"))
	private void vnap$wake(boolean immediately, boolean updateLevel, CallbackInfo info) {
		Bridge.fire(new PlayerWakeUpEvent((Player) (Object) this, immediately, updateLevel));
	}

	// from 1.21.11 an attack's effects are a method of their own
	//? if >=1.21.11 {
	/*@Inject(method = "attackVisualEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;crit(Lnet/minecraft/world/entity/Entity;)V"))
	private void vnap$crit(Entity target, boolean critical, boolean sweep, boolean strong, boolean knockback, float damage, CallbackInfo info) {
	*///?} else {
	@Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;crit(Lnet/minecraft/world/entity/Entity;)V"))
	private void vnap$crit(Entity target, CallbackInfo info) {
	//?}
		Bridge.fire(new CriticalHitEvent((Player) (Object) this, target, true, true));
	}
}
