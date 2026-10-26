package com.vnap.fab.mixin.client;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=26.1 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} else if >=1.21.6 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} else if >=1.21.2 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector4f;
*///?} else {
import net.minecraft.client.renderer.FogRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/** NeoForge's fog events: the colour vanilla worked out, the planes after vanilla set them (a cancel keeps the listeners'). */
@SuppressWarnings("unused")
@Mixin(FogRenderer.class)
public abstract class FogMixin {
	// from 1.21.6 the fog is a renderer's of its own: the planes of the fog around (its environment's) before they go to the shader
	// from 26.1 the colour is written into the vector it is handed, the fog's planes are what setupFog returns
	//? if >=26.1 {
	/*@Inject(method = "computeFogColor", at = @At("TAIL"))
	private void vnap$colour(net.minecraft.client.Camera camera, float partialTick, net.minecraft.client.multiplayer.ClientLevel level, int renderDistance,
			float darken, Vector4f colour, CallbackInfo info) {
		var event = Bridge.fire(new ViewportEvent.ComputeFogColor(colour.x(), colour.y(), colour.z()));
		colour.set(event.getRed(), event.getGreen(), event.getBlue(), colour.w());
	}

	@ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
	private FogData vnap$planes(FogData fog) {
		var event = Bridge.fire(new ViewportEvent.RenderFog(fog.environmentalStart, fog.environmentalEnd));
		if (event.isCanceled()) {
			fog.environmentalStart = event.getNearPlaneDistance();
			fog.environmentalEnd = event.getFarPlaneDistance();
		}
		return fog;
	}
	*///?} else if >=1.21.6 {
	/*@ModifyReturnValue(method = "computeFogColor", at = @At("RETURN"))
	private Vector4f vnap$colour(Vector4f colour) {
		var event = Bridge.fire(new ViewportEvent.ComputeFogColor(colour.x(), colour.y(), colour.z()));
		return new Vector4f(event.getRed(), event.getGreen(), event.getBlue(), colour.w());
	}

	@Inject(method = "setupFog", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;"))
	private void vnap$planes(CallbackInfoReturnable<Vector4f> info, @Local FogData fog) {
		var event = Bridge.fire(new ViewportEvent.RenderFog(fog.environmentalStart, fog.environmentalEnd));
		if (!event.isCanceled()) return;
		fog.environmentalStart = event.getNearPlaneDistance();
		fog.environmentalEnd = event.getFarPlaneDistance();
	}
	*///?} else if >=1.21.2 {
	/*@ModifyReturnValue(method = "computeFogColor", at = @At("RETURN"))
	private static Vector4f vnap$colour(Vector4f colour) {
		var event = Bridge.fire(new ViewportEvent.ComputeFogColor(colour.x(), colour.y(), colour.z()));
		return new Vector4f(event.getRed(), event.getGreen(), event.getBlue(), colour.w());
	}

	@ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
	private static FogParameters vnap$planes(FogParameters fog) {
		var event = Bridge.fire(new ViewportEvent.RenderFog(fog.start(), fog.end()));
		if (!event.isCanceled()) return fog;
		var shape = event.vnap$shape();
		return new FogParameters(event.getNearPlaneDistance(), event.getFarPlaneDistance(), shape != null ? shape : fog.shape(), fog.red(), fog.green(), fog.blue(),
				fog.alpha());
	}
	*///?} else {
	@Shadow
	private static float fogRed;
	@Shadow
	private static float fogGreen;
	@Shadow
	private static float fogBlue;

	@WrapOperation(method = "setupColor", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V"))
	private static void vnap$colour(float red, float green, float blue, float alpha, Operation<Void> vanilla) {
		var event = Bridge.fire(new ViewportEvent.ComputeFogColor(red, green, blue));
		fogRed = event.getRed();
		fogGreen = event.getGreen();
		fogBlue = event.getBlue();
		vanilla.call(fogRed, fogGreen, fogBlue, alpha);
	}

	@Inject(method = "setupFog", at = @At("TAIL"))
	private static void vnap$planes(CallbackInfo info) {
		var event = Bridge.fire(new ViewportEvent.RenderFog(RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd()));
		if (!event.isCanceled()) return;
		RenderSystem.setShaderFogStart(event.getNearPlaneDistance());
		RenderSystem.setShaderFogEnd(event.getFarPlaneDistance());
		if (event.vnap$shape() != null) RenderSystem.setShaderFogShape(event.vnap$shape());
	}
	//?}
}
