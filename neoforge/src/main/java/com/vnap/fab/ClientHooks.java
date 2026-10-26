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
		//? if <26.1
		layers();
	}
	//? if <26.1 {

	/**
	 * The mod's blocks in the layer their models name ("render_type", which NeoForge reads and Fabric does not):
	 * a cutout model drawn solid shows its clear pixels black. From 26.1 the game takes the layer from the textures.
	 */
	private static void layers() {
		var mod = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(com.vnap.VillagerLife.MOD_ID).orElseThrow();
		for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
			var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
			if (!id.getNamespace().equals(com.vnap.VillagerLife.MOD_ID)) continue;
			var states = json(mod, "assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json");
			if (states == null) continue;
			java.util.Set<String> found = new java.util.HashSet<>();
			models(states, model -> {
				for (var seen = new java.util.HashSet<String>(); model != null && seen.add(model); ) {
					var at = model.contains(":") ? net.minecraft.resources.ResourceLocation.parse(model) : net.minecraft.resources.ResourceLocation.withDefaultNamespace(model);
					var json = json(mod, "assets/" + at.getNamespace() + "/models/" + at.getPath() + ".json");
					if (json == null) break;
					if (json.has("render_type")) {
						found.add(json.get("render_type").getAsString());
						break;
					}
					model = json.has("parent") ? json.get("parent").getAsString() : null;
				}
			});
			boolean translucent = found.contains("minecraft:translucent") || found.contains("translucent");
			if (!translucent && !found.contains("minecraft:cutout") && !found.contains("cutout")) continue;
			//? if >=1.21.6 {
			/*net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(block, translucent
					? net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT : net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT);
			*///?} else {
			net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(block, translucent
					? net.minecraft.client.renderer.RenderType.translucent() : net.minecraft.client.renderer.RenderType.cutout());
			//?}
		}
	}

	/** Every model a blockstate file names (its variants and its multipart). */
	private static void models(com.google.gson.JsonObject states, java.util.function.Consumer<String> each) {
		java.util.List<com.google.gson.JsonElement> parts = new java.util.ArrayList<>();
		if (states.has("variants")) parts.addAll(states.getAsJsonObject("variants").asMap().values());
		if (states.has("multipart")) {
			for (var part : states.getAsJsonArray("multipart")) parts.add(part.getAsJsonObject().get("apply"));
		}
		for (var part : parts) {
			for (var model : part.isJsonArray() ? part.getAsJsonArray().asList() : java.util.List.of(part)) {
				each.accept(model.getAsJsonObject().get("model").getAsString());
			}
		}
	}

	private static com.google.gson.@org.jspecify.annotations.Nullable JsonObject json(net.fabricmc.loader.api.ModContainer mod, String path) {
		var file = mod.findPath(path);
		if (file.isEmpty()) return null;
		try (var reader = java.nio.file.Files.newBufferedReader(file.get())) {
			return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}
	//?}

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
