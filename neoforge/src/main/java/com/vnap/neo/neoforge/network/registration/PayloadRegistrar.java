package com.vnap.neo.neoforge.network.registration;

import com.vnap.neo.neoforge.network.handling.DirectionalPayloadHandler;
import com.vnap.neo.neoforge.network.handling.IPayloadContext;
import com.vnap.neo.neoforge.network.handling.IPayloadHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The mod's payloads: on Forge one payload channel (villager_life:main, the registrar's version) built once they are all
 * in. A handler runs on the main thread of its side, as NeoForge's do.
 */
public final class PayloadRegistrar {
	private record Entry<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			IPayloadHandler<T> handler) {
	}

	private final String version;
	private final List<Entry<?>> toClient = new ArrayList<>();
	private final List<Entry<?>> toServer = new ArrayList<>();
	private final List<Entry<?>> both = new ArrayList<>();

	public PayloadRegistrar(String version) {
		this.version = version;
	}

	public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			IPayloadHandler<T> handler) {
		toClient.add(new Entry<>(type, codec, handler));
		return this;
	}

	public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			IPayloadHandler<T> handler) {
		toServer.add(new Entry<>(type, codec, handler));
		return this;
	}

	public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			IPayloadHandler<T> handler) {
		both.add(new Entry<>(type, codec, handler));
		return this;
	}

	public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			IPayloadHandler<T> clientSide, IPayloadHandler<T> serverSide) {
		return playBidirectional(type, codec, new DirectionalPayloadHandler<>(clientSide, serverSide));
	}

	/** Builds the channel: everything is registered. */
	public void build() {
		// the shim's own: the recipes a client is sent (NeoForge sends them itself)
		//? if >=1.21.2
		/*toClient.add(new Entry<>(com.vnap.neo.network.RecipeContent.TYPE, com.vnap.neo.network.RecipeContent.CODEC, (payload, context) -> com.vnap.neo.Bridge.fire(new com.vnap.neo.neoforge.client.event.RecipesReceivedEvent(payload.recipeTypes(), com.vnap.neo.network.RecipeContent.map(payload.recipes())))));*/
		var play = net.minecraftforge.network.ChannelBuilder.named(com.vnap.VillagerLife.id("main"))
				.networkProtocolVersion(Integer.parseInt(version)).payloadChannel().play();
		var flow = play.clientbound();
		for (Entry<?> entry : toClient) flow = add(flow, entry);
		flow = flow.serverbound();
		for (Entry<?> entry : toServer) flow = add(flow, entry);
		flow = flow.bidirectional();
		for (Entry<?> entry : both) flow = add(flow, entry);
		com.vnap.neo.neoforge.network.PacketDistributor.channel = flow.build();
	}

	@SuppressWarnings("unchecked")
	private static <T extends CustomPacketPayload> net.minecraftforge.network.payload.PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> add(
			net.minecraftforge.network.payload.PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow, Entry<T> entry) {
		return flow.add(entry.type(), (StreamCodec<RegistryFriendlyByteBuf, T>) entry.codec(), (payload, context) -> {
			com.vnap.neo.Bridge.logged(context.enqueueWork(() -> entry.handler().handle(payload, new IPayloadContext(context))), "payload " + entry.type().id());
			context.setPacketHandled(true);
		});
	}
}
