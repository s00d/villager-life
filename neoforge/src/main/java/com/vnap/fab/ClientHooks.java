package com.vnap.fab;

import com.vnap.fab.neoforge.client.event.ClientChatReceivedEvent;
import com.vnap.fab.neoforge.client.event.ClientPlayerNetworkEvent;
import com.vnap.fab.neoforge.client.event.ClientTickEvent;
import com.vnap.fab.neoforge.client.event.EntityRenderersEvent;
import com.vnap.fab.neoforge.client.event.ModelEvent;
import com.vnap.fab.neoforge.client.event.RegisterColorHandlersEvent;
import com.vnap.fab.neoforge.client.event.RegisterGuiLayersEvent;
import com.vnap.fab.neoforge.client.event.RegisterMenuScreensEvent;
import com.vnap.fab.neoforge.client.event.RegisterParticleProvidersEvent;
import com.vnap.fab.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import com.vnap.fab.neoforge.network.PacketDistributor;
import com.vnap.fab.neoforge.network.handling.IPayloadContext;
import com.vnap.fab.neoforge.network.registration.PayloadRegistrar;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric API's client callbacks as the shim's NeoForge events, and the client's registrations at its start. */
final class ClientHooks {
	private ClientHooks() {
	}

	static void client() {
		PacketDistributor.toServer = ClientPlayNetworking::send;
		for (PayloadRegistrar.Entry<?> entry : PayloadRegistrar.client()) receive(entry);

		ClientTickEvents.START_CLIENT_TICK.register(client -> Bridge.fire(new ClientTickEvent.Pre()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Bridge.fire(new ClientTickEvent.Post()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Bridge.fire(new ClientPlayerNetworkEvent.LoggingOut()));
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !Bridge.fire(new ClientChatReceivedEvent.System(message, overlay)).isCanceled());
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
				Bridge.fire(new com.vnap.fab.neoforge.event.entity.player.ItemTooltipEvent(stack, lines, flag, Minecraft.getInstance().player)));
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> Bridge.fire(new com.vnap.fab.neoforge.event.entity.EntityJoinLevelEvent(entity, level, false)));
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> Bridge.fire(new com.vnap.fab.neoforge.event.entity.EntityLeaveLevelEvent(entity, level)));
		com.vnap.fab.client.ClientCommands.register();

		Bridge.fire(new EntityRenderersEvent.RegisterLayerDefinitions());
		Bridge.fire(new EntityRenderersEvent.RegisterRenderers());
		Bridge.fire(new RegisterParticleProvidersEvent());
		//? if >=26.1 {
		/*Bridge.fire(new RegisterColorHandlersEvent.BlockTintSources());
		*///?} else
		Bridge.fire(new RegisterColorHandlersEvent.Block());
		Bridge.fire(new RegisterMenuScreensEvent());
		Bridge.fire(new RegisterClientExtensionsEvent());
		Bridge.fire(new RegisterGuiLayersEvent());
		//? if >=1.21.6 {
		/*hud();
		Bridge.fire(new com.vnap.fab.neoforge.client.event.RegisterPictureInPictureRenderersEvent());
		*///?}
		//? if >=1.21.2
		/*Bridge.fire(new com.vnap.fab.neoforge.client.renderstate.RegisterRenderStateModifiersEvent());*/
		models();
	}

	/** A payload to the client: its handler, with the client's player. */
	private static <T extends CustomPacketPayload> void receive(PayloadRegistrar.Entry<T> entry) {
		ClientPlayNetworking.registerGlobalReceiver(entry.type(), (payload, context) -> entry.handler().handle(payload, new IPayloadContext(context.player())));
	}

	/** The mod's own models, through Fabric API's model loading plugin (from 1.21.5 its extra models, a key each). */
	private static void models() {
		//? if >=1.21.5 {
		/*net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(plugin -> {
			var event = Bridge.fire(new ModelEvent.RegisterStandalone());
			for (var id : event.vnap$models()) {
				plugin.addModel(ModelEvent.RegisterStandalone.key(id), net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel.blockStateModel(id));
			}
		});
		*///?} else {
		net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(plugin -> {
			var event = Bridge.fire(new ModelEvent.RegisterAdditional());
			plugin.addModels(event.vnap$models());
		});
		//?}
	}
	//? if >=1.21.6 {

	/*/^* The mod's HUD layers as Fabric API's HUD elements, next to the vanilla ones they name. ^/
	private static void hud() {
		RegisterGuiLayersEvent.BELOW.forEach((vanilla, layers) -> {
			for (int i = 0; i < layers.size(); i++) {
				var layer = layers.get(i);
				net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementBefore(element(vanilla),
						com.vnap.VillagerLife.id("below_" + vanilla.getPath() + "_" + i), layer::render);
			}
		});
		RegisterGuiLayersEvent.ABOVE.forEach((vanilla, layers) -> {
			for (int i = 0; i < layers.size(); i++) {
				var layer = layers.get(i);
				net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(element(vanilla),
						com.vnap.VillagerLife.id("above_" + vanilla.getPath() + "_" + i), layer::render);
			}
		});
	}

	private static net.minecraft.resources.ResourceLocation element(net.minecraft.resources.ResourceLocation vanilla) {
		return vanilla.equals(com.vnap.fab.neoforge.client.gui.VanillaGuiLayers.HOTBAR)
				? net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR
				: net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.OVERLAY_MESSAGE;
	}
	*///?}
}
