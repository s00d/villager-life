package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's EntityMountEvent (getting on, getting off) and EntityStruckByLightningEvent; a cancel stops either. */
@SuppressWarnings("unused")
@Mixin(Entity.class)
public abstract class EntityEventsMixin {
	@Shadow
	public abstract @Nullable Entity getVehicle();

	// from 1.21.11 with a say whether the riding starts on its own
	//? if >=1.21.11 {
	/*@Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
	private void vnap$mount(Entity vehicle, boolean force, boolean sendEvent, CallbackInfoReturnable<Boolean> ci) {
	*///?} else {
	@Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true)
	private void vnap$mount(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> ci) {
	//?}
		if (Bridge.fire(new com.vnap.fab.neoforge.event.entity.EntityMountEvent((Entity) (Object) this, vehicle, true)).isCanceled()) ci.setReturnValue(false);
	}

	@Inject(method = "removeVehicle", at = @At("HEAD"), cancellable = true)
	private void vnap$dismount(CallbackInfo ci) {
		Entity vehicle = getVehicle();
		if (vehicle != null && Bridge.fire(new com.vnap.fab.neoforge.event.entity.EntityMountEvent((Entity) (Object) this, vehicle, false)).isCanceled()) ci.cancel();
	}

	@Inject(method = "thunderHit", at = @At("HEAD"), cancellable = true)
	private void vnap$lightning(ServerLevel level, LightningBolt lightning, CallbackInfo ci) {
		if (Bridge.fire(new com.vnap.fab.neoforge.event.entity.EntityStruckByLightningEvent((Entity) (Object) this, lightning)).isCanceled()) ci.cancel();
	}
}
