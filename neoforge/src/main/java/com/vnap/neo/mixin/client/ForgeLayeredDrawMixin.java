package com.vnap.neo.mixin.client;

import com.vnap.neo.neoforge.client.event.RenderGuiLayerEvent;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NeoForge's RenderGuiLayerEvent.Pre: every named HUD layer (vanilla's and every mod's) asks before it draws. */
@SuppressWarnings("unused")
@Mixin(value = net.minecraftforge.client.gui.overlay.ForgeLayeredDraw.class, remap = false)
public abstract class ForgeLayeredDrawMixin {
	//? if >=26.1 {
	/*@Redirect(method = "add(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraftforge/client/gui/overlay/ForgeLayer;)Lnet/minecraftforge/client/gui/overlay/ForgeLayeredDraw;",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
	private Object vnap$layer(Map<Object, Object> layers, Object name, Object layer) {
		ResourceLocation id = (ResourceLocation) name;
		var drawn = (net.minecraftforge.client.gui.overlay.ForgeLayer) layer;
		return layers.put(name, (net.minecraftforge.client.gui.overlay.ForgeLayer) (graphics, delta) -> {
			if (RenderGuiLayerEvent.drawn(id, graphics, delta)) drawn.extract(graphics, delta);
		});
	}
	*///?} else if >=1.21.6 {
	/*@Redirect(method = "add(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraftforge/client/gui/overlay/ForgeLayer;)Lnet/minecraftforge/client/gui/overlay/ForgeLayeredDraw;",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
	private Object vnap$layer(Map<Object, Object> layers, Object name, Object layer) {
		ResourceLocation id = (ResourceLocation) name;
		var drawn = (net.minecraftforge.client.gui.overlay.ForgeLayer) layer;
		return layers.put(name, (net.minecraftforge.client.gui.overlay.ForgeLayer) (graphics, delta) -> {
			if (RenderGuiLayerEvent.drawn(id, graphics, delta)) drawn.render(graphics, delta);
		});
	}
	*///?} else {
	@Redirect(method = "add(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/gui/LayeredDraw$Layer;)Lnet/minecraftforge/client/gui/overlay/ForgeLayeredDraw;",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
	private Object vnap$layer(Map<Object, Object> layers, Object name, Object layer) {
		ResourceLocation id = (ResourceLocation) name;
		var drawn = (net.minecraft.client.gui.LayeredDraw.Layer) layer;
		return layers.put(name, (net.minecraft.client.gui.LayeredDraw.Layer) (graphics, delta) -> {
			if (RenderGuiLayerEvent.drawn(id, graphics, delta)) drawn.render(graphics, delta);
		});
	}
	//?}
}
