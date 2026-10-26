package com.vnap.fab.mixin;

import com.vnap.fab.Persisted;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A new body of a player (respawn, the End's exit) takes the old one's persisted data, as on NeoForge. */
@SuppressWarnings("unused")
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDataMixin {
	@Inject(method = "restoreFrom", at = @At("TAIL"))
	private void vnap$persisted(ServerPlayer old, boolean alive, CallbackInfo ci) {
		CompoundTag from = old.getPersistentData();
		if (from.contains(Persisted.TAG)) ((ServerPlayer) (Object) this).getPersistentData().put(Persisted.TAG, from.get(Persisted.TAG).copy());
	}
}
