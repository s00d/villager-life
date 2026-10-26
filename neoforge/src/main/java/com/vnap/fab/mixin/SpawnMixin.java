package com.vnap.fab.mixin;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's FinalizeSpawnEvent as a mob is finished; a cancelled one is kept out of its level (EntityJoinMixin, WorldGenRegionMixin). */
@SuppressWarnings("unused")
@Mixin(Mob.class)
public abstract class SpawnMixin implements com.vnap.fab.SpawnCancelled {
	@Unique
	private boolean vnap$spawnCancelled;

	@Override
	public boolean vnap$spawnCancelled() {
		return vnap$spawnCancelled;
	}

	@Override
	public void vnap$spawnCancelled(boolean cancelled) {
		vnap$spawnCancelled = cancelled;
	}

	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void vnap$finalize(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data,
			CallbackInfoReturnable<SpawnGroupData> ci) {
		var event = com.vnap.fab.Bridge.fire(new com.vnap.fab.neoforge.event.entity.living.FinalizeSpawnEvent((Mob) (Object) this, level, type));
		if (event.isSpawnCancelled() || event.isCanceled()) vnap$spawnCancelled = true;
	}
}
