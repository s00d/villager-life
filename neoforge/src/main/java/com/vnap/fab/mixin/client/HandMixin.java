package com.vnap.fab.mixin.client;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.client.event.RenderHandEvent;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's RenderHandEvent: the first-person hands, cancellable (both at once). */
@SuppressWarnings("unused")
@Mixin(GameRenderer.class)
public abstract class HandMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void vnap$hands(CallbackInfo info) {
		if (Bridge.fire(new RenderHandEvent()).isCanceled()) info.cancel();
	}
}
