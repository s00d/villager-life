package com.vnap.fab.neoforge.network;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/** Sends the mod's payloads with Fabric API's networking (to the server: the client's, set by FabricClientEntry). */
public final class PacketDistributor {
	public static @Nullable Consumer<CustomPacketPayload> toServer;

	private PacketDistributor() {
	}

	public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
	}

	public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload) {
		for (ServerPlayer player : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(entity)) sendToPlayer(player, payload);
	}

	public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
		sendToPlayersTrackingEntity(entity, payload);
		if (entity instanceof ServerPlayer self) sendToPlayer(self, payload);
	}

	public static void sendToServer(CustomPacketPayload payload) {
		toServer(payload);
	}

	/** (sendToServer by another name: from 1.21.6 the build sends that name to ClientPacketDistributor.) */
	public static void toServer(CustomPacketPayload payload) {
		Objects.requireNonNull(toServer, "no client to send from").accept(payload);
	}
}
