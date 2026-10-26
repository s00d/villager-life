package com.vnap.fab.mixin.client;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.client.event.EntityRenderersEvent;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's EntityRenderersEvent.AddLayers: the renderers just made, for the mod's layers. */
@SuppressWarnings("unused")
@Mixin(EntityRenderDispatcher.class)
public abstract class RenderersMixin {
	//? if >=1.21.9 {
	/*@Shadow
	private Map<EntityType<?>, EntityRenderer<?, ?>> renderers;
	@Shadow
	private Map<net.minecraft.world.entity.player.PlayerModelType, net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.player.AbstractClientPlayer>> playerRenderers;
	@Shadow
	private Map<net.minecraft.world.entity.player.PlayerModelType, net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.entity.ClientMannequin>> mannequinRenderers;
	*///?} else if >=1.21.2 {
	/*@Shadow
	private Map<EntityType<?>, EntityRenderer<?, ?>> renderers;
	@Shadow
	private Map<net.minecraft.client.resources.PlayerSkin.Model, EntityRenderer<? extends Player, ?>> playerRenderers;
	*///?} else {
	@Shadow
	private Map<EntityType<?>, EntityRenderer<?>> renderers;
	@Shadow
	private Map<net.minecraft.client.resources.PlayerSkin.Model, EntityRenderer<? extends Player>> playerRenderers;
	//?}

	@Inject(method = "onResourceManagerReload", at = @At("TAIL"))
	private void vnap$made(CallbackInfo info) {
		//? if >=1.21.9 {
		/*Bridge.fire(new EntityRenderersEvent.AddLayers(renderers, playerRenderers, mannequinRenderers));
		*///?} else
		Bridge.fire(new EntityRenderersEvent.AddLayers(renderers, playerRenderers));
	}
}
