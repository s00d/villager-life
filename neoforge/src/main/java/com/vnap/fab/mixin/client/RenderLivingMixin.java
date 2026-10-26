package com.vnap.fab.mixin.client;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.client.event.RenderLivingEvent;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's RenderLivingEvent.Pre: a living entity about to be drawn, cancellable. */
@SuppressWarnings("unused")
@Mixin(LivingEntityRenderer.class)
public abstract class RenderLivingMixin {
	// from 1.21.9 a renderer submits its state
	//? if >=1.21.9 {
	/*@SuppressWarnings({"unchecked", "rawtypes"})
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
			at = @At("HEAD"), cancellable = true)
	private void vnap$pre(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack pose,
			net.minecraft.client.renderer.SubmitNodeCollector nodes, net.minecraft.client.renderer.state.CameraRenderState camera, CallbackInfo info) {
		if (Bridge.fire(new RenderLivingEvent.Pre(state, (LivingEntityRenderer) (Object) this)).isCanceled()) info.cancel();
	}
	*///?} else if >=1.21.2 {
	/*@SuppressWarnings({"unchecked", "rawtypes"})
	@Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void vnap$pre(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack pose,
			net.minecraft.client.renderer.MultiBufferSource buffers, int light, CallbackInfo info) {
		if (Bridge.fire(new RenderLivingEvent.Pre(state, (LivingEntityRenderer) (Object) this)).isCanceled()) info.cancel();
	}
	*///?} else {
	@SuppressWarnings({"unchecked", "rawtypes"})
	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void vnap$pre(LivingEntity entity, float yaw, float partialTick, com.mojang.blaze3d.vertex.PoseStack pose,
			net.minecraft.client.renderer.MultiBufferSource buffers, int light, CallbackInfo info) {
		if (Bridge.fire(new RenderLivingEvent.Pre(entity, (LivingEntityRenderer) (Object) this)).isCanceled()) info.cancel();
	}
	//?}
}
