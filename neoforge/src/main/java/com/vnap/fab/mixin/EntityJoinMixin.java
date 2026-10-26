package com.vnap.fab.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's EntityJoinLevelEvent where NeoForge fires it on a server: an entity new or from its chunk (a cancel keeps it out). */
@SuppressWarnings("unused")
@Mixin(PersistentEntitySectionManager.class)
public abstract class EntityJoinMixin {
	@Inject(method = "addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z", at = @At("HEAD"), cancellable = true)
	private void vnap$join(EntityAccess access, boolean worldGenSpawned, CallbackInfoReturnable<Boolean> ci) {
		if (!(access instanceof Entity entity) || !(entity.level() instanceof ServerLevel level)) return;
		if (entity instanceof com.vnap.fab.SpawnCancelled spawn && spawn.vnap$spawnCancelled()) {
			ci.setReturnValue(false);
			return;
		}
		if (com.vnap.fab.Bridge.fire(new com.vnap.fab.neoforge.event.entity.EntityJoinLevelEvent(entity, level, worldGenSpawned)).isCanceled()) ci.setReturnValue(false);
	}
}
