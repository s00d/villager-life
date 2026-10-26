package com.vnap.fab.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.vnap.fab.neoforge.client.event.RegisterGuiLayersEvent;
import com.vnap.fab.neoforge.client.event.RenderGuiEvent;
import com.vnap.fab.neoforge.client.gui.VanillaGuiLayers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The HUD between NeoForge's RenderGuiEvent.Pre and Post, with the mod's layers drawn next to the vanilla ones they name. */
@SuppressWarnings("unused")
// from 26.2 the HUD is a class of its own
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else
@Mixin(Gui.class)
public abstract class GuiLayersMixin {
	// from 26.1 the HUD is extracted
	//? if >=26.1 {
	/*@WrapMethod(method = "extractRenderState")
	*///?} else
	@WrapMethod(method = "render")
	private void vnap$around(GuiGraphics graphics, DeltaTracker delta, Operation<Void> vanilla) {
		RenderGuiEvent.around(graphics, delta, () -> {
			vanilla.call(graphics, delta);
			for (var layer : RegisterGuiLayersEvent.TOP) layer.render(graphics, delta);
		});
	}
	//? if <1.21.6 {

	@Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"))
	private void vnap$belowHotbar(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		RegisterGuiLayersEvent.draw(RegisterGuiLayersEvent.BELOW, VanillaGuiLayers.HOTBAR, graphics, delta);
	}

	@Inject(method = "renderHotbarAndDecorations", at = @At("TAIL"))
	private void vnap$aboveHotbar(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		RegisterGuiLayersEvent.draw(RegisterGuiLayersEvent.ABOVE, VanillaGuiLayers.HOTBAR, graphics, delta);
	}

	@Inject(method = "renderOverlayMessage", at = @At("HEAD"))
	private void vnap$belowMessage(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		RegisterGuiLayersEvent.draw(RegisterGuiLayersEvent.BELOW, VanillaGuiLayers.OVERLAY_MESSAGE, graphics, delta);
	}

	@Inject(method = "renderOverlayMessage", at = @At("TAIL"))
	private void vnap$aboveMessage(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		RegisterGuiLayersEvent.draw(RegisterGuiLayersEvent.ABOVE, VanillaGuiLayers.OVERLAY_MESSAGE, graphics, delta);
	}
	//?}
}
