package com.vnap.neo.mixin.client;

import com.vnap.neo.neoforge.client.event.RenderGuiEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NeoForge's RenderGuiEvent around the HUD's layers. */
@SuppressWarnings("unused")
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else
@Mixin(Gui.class)
public abstract class GuiRenderMixin {
	//? if >=26.1 {
	/*@Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/gui/overlay/ForgeLayeredDraw;extractRenderState(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void vnap$layers(GuiGraphics graphics, DeltaTracker delta) {
		RenderGuiEvent.around(graphics, delta, () -> net.minecraftforge.client.gui.overlay.ForgeLayeredDraw.extractRenderState(graphics, delta));
	}
	*///?} else if >=1.21.6 {
	/*@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/gui/overlay/ForgeLayeredDraw;beginRender(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void vnap$layers(GuiGraphics graphics, DeltaTracker delta) {
		RenderGuiEvent.around(graphics, delta, () -> net.minecraftforge.client.gui.overlay.ForgeLayeredDraw.beginRender(graphics, delta));
	}
	*///?} else {
	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/LayeredDraw;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
	private void vnap$layers(net.minecraft.client.gui.LayeredDraw layers, GuiGraphics graphics, DeltaTracker delta) {
		RenderGuiEvent.around(graphics, delta, () -> layers.render(graphics, delta));
	}
	//?}
}
