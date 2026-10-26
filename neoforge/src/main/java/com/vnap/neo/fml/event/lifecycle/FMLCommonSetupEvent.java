package com.vnap.neo.fml.event.lifecycle;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import java.util.concurrent.CompletableFuture;

public final class FMLCommonSetupEvent extends Event {
	static {
		Bridge.bind(FMLCommonSetupEvent.class, (priority, sink) -> Bridge.mod(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent.class, priority, FMLCommonSetupEvent::new, sink));
	}

	private final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent forge;

	FMLCommonSetupEvent(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent forge) {
		super(forge);
		this.forge = forge;
	}

	public CompletableFuture<Void> enqueueWork(Runnable work) {
		return forge.enqueueWork(work);
	}
}
