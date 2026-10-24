package com.vnap.mixin;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.server.timings.TimeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's {@code /neoforge track} times entities and block entities in one static tracker that is not thread-safe;
 * in a single-player world the client's entity tick (render thread) and the integrated server's both report to it,
 * and one thread clearing {@code currentlyTracking} between the other's null check and its read crashed the client
 * (NPE in TimeTracker.trackEnd, 20.10.2026). Only the server thread is timed: the client's ticks were never what the
 * command reports on.
 */
@SuppressWarnings("unused")
@Mixin(value = TimeTracker.class, remap = false)
public abstract class TimeTrackerMixin {
	@Inject(method = {"trackStart(Ljava/lang/Object;)V", "trackEnd(Ljava/lang/Object;)V"}, at = @At("HEAD"), cancellable = true)
	private void vnap$serverThreadOnly(Object tracked, CallbackInfo ci) {
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null || !server.isSameThread()) ci.cancel();
	}
}
