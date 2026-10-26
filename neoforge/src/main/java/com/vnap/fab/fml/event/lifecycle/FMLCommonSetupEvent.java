package com.vnap.fab.fml.event.lifecycle;

import com.vnap.fab.bus.api.Event;
import java.util.concurrent.CompletableFuture;

/** Fired once the mod and its registries are set up (com.vnap.fab.FabricEntry and FabricClientEntry); work runs at once. */
public final class FMLCommonSetupEvent extends Event {
	public FMLCommonSetupEvent() {
	}

	public CompletableFuture<Void> enqueueWork(Runnable work) {
		work.run();
		return CompletableFuture.completedFuture(null);
	}
}
