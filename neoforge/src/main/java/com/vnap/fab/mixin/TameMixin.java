package com.vnap.fab.mixin;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's AnimalTameEvent as a pet is tamed by a player; a cancel leaves it wild. */
@SuppressWarnings("unused")
@Mixin(TamableAnimal.class)
public abstract class TameMixin {
	@Inject(method = "tame", at = @At("HEAD"), cancellable = true)
	private void vnap$tame(Player player, CallbackInfo ci) {
		if (com.vnap.fab.Bridge.fire(new com.vnap.fab.neoforge.event.entity.living.AnimalTameEvent((Animal) (Object) this, player)).isCanceled()) ci.cancel();
	}
}
