package com.vnap.neo.neoforge.network.handling;

import net.minecraft.world.entity.player.Player;

/** Who a payload came to or from: the sender on the server, the local player on a client. */
public final class IPayloadContext {
	private final net.minecraftforge.event.network.CustomPayloadEvent.Context forge;

	public IPayloadContext(net.minecraftforge.event.network.CustomPayloadEvent.Context forge) {
		this.forge = forge;
	}

	public Player player() {
		if (forge.isServerSide()) return java.util.Objects.requireNonNull(forge.getSender(), "a payload to the server with no player");
		return com.vnap.neo.client.ClientAccess.player();
	}

	public java.util.concurrent.CompletableFuture<Void> enqueueWork(Runnable work) {
		return forge.enqueueWork(work);
	}
}
