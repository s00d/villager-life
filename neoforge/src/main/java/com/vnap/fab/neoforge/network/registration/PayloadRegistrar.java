package com.vnap.fab.neoforge.network.registration;

import com.vnap.fab.neoforge.network.handling.DirectionalPayloadHandler;
import com.vnap.fab.neoforge.network.handling.IPayloadContext;
import com.vnap.fab.neoforge.network.handling.IPayloadHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The mod's payloads on Fabric API: their types registered at once, the server's receivers with the mod's common setup
 * (build), the client's from FabricClientEntry (clientReceivers: Fabric's client networking is the client's only).
 */
public final class PayloadRegistrar {
	public record Entry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			IPayloadHandler<T> handler) {
	}

	private static final List<Entry<?>> CLIENT = new ArrayList<>();

	public PayloadRegistrar(String version) {
	}

	/** What goes to a client, for its side to hear (ClientHooks). */
	public static List<Entry<?>> client() {
		return java.util.Collections.unmodifiableList(CLIENT);
	}

	public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			IPayloadHandler<T> handler) {
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(type, codec);
		CLIENT.add(new Entry<>(type, codec, handler));
		return this;
	}

	public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			IPayloadHandler<T> handler) {
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(type, codec);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
				handler.handle(payload, new IPayloadContext(context.player())));
		return this;
	}

	public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			IPayloadHandler<T> handler) {
		playToClient(type, codec, handler);
		return playToServer(type, codec, handler);
	}

	public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			IPayloadHandler<T> clientSide, IPayloadHandler<T> serverSide) {
		return playBidirectional(type, codec, new DirectionalPayloadHandler<>(clientSide, serverSide));
	}

	/** Everything is registered: the shim's own payload too (the recipes a client is sent, which NeoForge sends itself). */
	public void build() {
		//? if >=1.21.2
		/*playToClient(com.vnap.fab.network.RecipeContent.TYPE, com.vnap.fab.network.RecipeContent.CODEC, (payload, context) -> com.vnap.fab.Bridge.fire(new com.vnap.fab.neoforge.client.event.RecipesReceivedEvent(payload.recipeTypes(), com.vnap.fab.network.RecipeContent.map(payload.recipes()))));*/
	}
}
