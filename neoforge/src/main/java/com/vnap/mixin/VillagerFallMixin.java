package com.vnap.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A villager's path drops one block at most, as it climbs one at most (vanilla lets a mob drop three). A way it
 * jumped down it could not climb back: a desert village's hollow in the hill by the bell took everyone whose way to
 * bed passed its rim, and at night zombies found them penned there (live, 20.10.2026). With every step one block
 * either way, any way a villager walks it can walk back.
 */
@SuppressWarnings("unused")
@Mixin(Mob.class)
public abstract class VillagerFallMixin {
	@Inject(method = "getMaxFallDistance", at = @At("HEAD"), cancellable = true)
	private void vnap$noDropItCannotClimb(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof Villager) cir.setReturnValue(1);
	}
}
