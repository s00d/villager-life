package com.vnap.fab.neoforge.network.handling;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.entity.player.Player;

/** Who a payload came to or from: the sender on the server, the local player on a client. Fabric hands it over on the main thread. */
public final class IPayloadContext {
	private final Player player;

	public IPayloadContext(Player player) {
		this.player = player;
	}

	public Player player() {
		return player;
	}

	public CompletableFuture<Void> enqueueWork(Runnable work) {
		work.run();
		return CompletableFuture.completedFuture(null);
	}
}
