package com.vnap.fab.mixin;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's AnimalTameEvent as a horse takes its rider for its own; a cancel leaves it wild. */
@SuppressWarnings("unused")
@Mixin(AbstractHorse.class)
public abstract class HorseTameMixin {
	@Inject(method = "tameWithName", at = @At("HEAD"), cancellable = true)
	private void vnap$tame(Player player, CallbackInfoReturnable<Boolean> ci) {
		if (com.vnap.fab.Bridge.fire(new com.vnap.fab.neoforge.event.entity.living.AnimalTameEvent((AbstractHorse) (Object) this, player)).isCanceled()) ci.setReturnValue(false);
	}
}
