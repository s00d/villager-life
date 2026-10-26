package com.vnap.fab.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.client.event.ViewportEvent;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's ComputeCameraAngles: the camera's turn as the listeners leave it, roll too (vanilla's camera has none). */
@SuppressWarnings("unused")
@Mixin(Camera.class)
public abstract class CameraMixin {
	// read-only vectors from 26.2
	//? if >=26.2 {
	/*@Shadow
	@Final
	private static org.joml.Vector3fc FORWARDS;
	@Shadow
	@Final
	private static org.joml.Vector3fc UP;
	@Shadow
	@Final
	private static org.joml.Vector3fc LEFT;
	*///?} else {
	@Shadow
	@Final
	private static Vector3f FORWARDS;
	@Shadow
	@Final
	private static Vector3f UP;
	@Shadow
	@Final
	private static Vector3f LEFT;
	//?}
	@Shadow
	@Final
	private Quaternionf rotation;
	@Shadow
	@Final
	private Vector3f forwards;
	@Shadow
	@Final
	private Vector3f up;
	@Shadow
	@Final
	private Vector3f left;

	// the camera's own turn (from 1.21.2 a minecart's rider is turned first, in a branch of its own)
	// from 26.1 the camera follows its entity in a method of its own
	//? if >=26.1 {
	/*@WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1))
	private void vnap$angles(Camera camera, float yaw, float pitch, Operation<Void> vanilla, float partialTick) {
	*///?} else if >=1.21.2 {
	/*@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1))
	private void vnap$angles(Camera camera, float yaw, float pitch, Operation<Void> vanilla, BlockGetter level, Entity entity, boolean detached, boolean reverse, float partialTick) {
	*///?} else {
	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0))
	private void vnap$angles(Camera camera, float yaw, float pitch, Operation<Void> vanilla, BlockGetter level, Entity entity, boolean detached, boolean reverse, float partialTick) {
	//?}
		var event = Bridge.fire(new ViewportEvent.ComputeCameraAngles(partialTick, yaw, pitch, 0));
		vanilla.call(camera, event.getYaw(), event.getPitch());
		if (event.getRoll() == 0) return;
		float radians = (float) (Math.PI / 180.0);
		rotation.rotationYXZ((float) Math.PI - event.getYaw() * radians, -event.getPitch() * radians, -event.getRoll() * radians);
		FORWARDS.rotate(rotation, forwards);
		UP.rotate(rotation, up);
		LEFT.rotate(rotation, left);
	}
}
