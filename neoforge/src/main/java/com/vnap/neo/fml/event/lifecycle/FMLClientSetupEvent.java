package com.vnap.neo.fml.event.lifecycle;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import java.util.concurrent.CompletableFuture;

public final class FMLClientSetupEvent extends Event {
	static {
		Bridge.bind(FMLClientSetupEvent.class, (priority, sink) -> Bridge.mod(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent.class, priority, FMLClientSetupEvent::new, sink));
	}

	private final net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent forge;

	FMLClientSetupEvent(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent forge) {
		super(forge);
		this.forge = forge;
	}

	public CompletableFuture<Void> enqueueWork(Runnable work) {
		return forge.enqueueWork(work);
	}
}
