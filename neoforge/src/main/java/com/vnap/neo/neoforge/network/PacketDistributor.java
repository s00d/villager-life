package com.vnap.neo.neoforge.network;

import java.util.Objects;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/** Sends the mod's payloads over its channel (com.vnap.neo.neoforge.network.registration.PayloadRegistrar builds it). */
public final class PacketDistributor {
	public static net.minecraftforge.network.@Nullable Channel<CustomPacketPayload> channel;

	private PacketDistributor() {
	}

	private static net.minecraftforge.network.Channel<CustomPacketPayload> channel() {
		return Objects.requireNonNull(channel, "the mod's network channel");
	}

	public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
		channel().send(payload, net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
	}

	public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload) {
		channel().send(payload, net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(entity));
	}

	public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
		channel().send(payload, net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(entity));
	}

	public static void sendToServer(CustomPacketPayload payload) {
		toServer(payload);
	}

	/** (sendToServer by another name: from 1.21.6 the build sends that name to ClientPacketDistributor.) */
	public static void toServer(CustomPacketPayload payload) {
		channel().send(payload, net.minecraftforge.network.PacketDistributor.SERVER.noArg());
	}
}
