package com.vnap.fab.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's PlayerTickEvent around a player's tick, on both sides. */
@SuppressWarnings("unused")
@Mixin(Player.class)
public abstract class PlayerTickMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void vnap$pre(CallbackInfo ci) {
		com.vnap.fab.Bridge.fire(new com.vnap.fab.neoforge.event.tick.PlayerTickEvent.Pre((Player) (Object) this));
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void vnap$post(CallbackInfo ci) {
		com.vnap.fab.Bridge.fire(new com.vnap.fab.neoforge.event.tick.PlayerTickEvent.Post((Player) (Object) this));
	}
}
