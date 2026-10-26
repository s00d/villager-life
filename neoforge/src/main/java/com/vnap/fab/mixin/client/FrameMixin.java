package com.vnap.fab.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.client.event.RenderFrameEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's RenderFrameEvent around the game renderer's frame. */
@SuppressWarnings("unused")
@Mixin(Minecraft.class)
public abstract class FrameMixin {
	// from 26.1 a frame is drawn in a method of its own
	//? if >=26.3 {
	/*@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render()V"))
	private void vnap$frame(GameRenderer renderer, Operation<Void> vanilla) {
		DeltaTracker delta = ((Minecraft) (Object) this).getDeltaTracker();
		Bridge.fire(new RenderFrameEvent.Pre(delta));
		vanilla.call(renderer);
		Bridge.fire(new RenderFrameEvent.Post(delta));
	}
	*///?} else {
	//? if >=26.1 {
	/*@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
	*///?} else
	@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
	private void vnap$frame(GameRenderer renderer, DeltaTracker delta, boolean level, Operation<Void> vanilla) {
		Bridge.fire(new RenderFrameEvent.Pre(delta));
		vanilla.call(renderer, delta, level);
		Bridge.fire(new RenderFrameEvent.Post(delta));
	}
	//?}
}
