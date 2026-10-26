package com.vnap.fab.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A mob a structure spawns, its FinalizeSpawnEvent cancelled, stays out of the chunk being made. */
@SuppressWarnings("unused")
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {
	@Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
	private void vnap$cancelled(Entity entity, CallbackInfoReturnable<Boolean> ci) {
		if (entity instanceof com.vnap.fab.SpawnCancelled spawn && spawn.vnap$spawnCancelled()) ci.setReturnValue(false);
	}
}
