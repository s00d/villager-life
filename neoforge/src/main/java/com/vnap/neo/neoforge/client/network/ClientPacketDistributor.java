package com.vnap.neo.neoforge.client.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** From the client to the server (NeoForge moved it here from 1.21.6): the mod's channel, as PacketDistributor's. */
public final class ClientPacketDistributor {
	private ClientPacketDistributor() {
	}

	public static void sendToServer(CustomPacketPayload payload) {
		com.vnap.neo.neoforge.network.PacketDistributor.toServer(payload);
	}
}
