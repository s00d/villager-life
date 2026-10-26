package com.vnap.neo.neoforge.network.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.Nullable;

/** With the mod's common setup: the payloads registered with it make the mod's channel when its listeners are done. */
public final class RegisterPayloadHandlersEvent extends Event {
	static {
		Bridge.bind(RegisterPayloadHandlersEvent.class, (priority, sink) -> Bridge.mod(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent.class, priority,
				RegisterPayloadHandlersEvent::new, event -> {
					sink.accept(event);
					if (event.registrar != null) event.registrar.build();
				}));
	}

	private @Nullable PayloadRegistrar registrar;

	RegisterPayloadHandlersEvent(Object forge) {
		super(forge);
	}

	public PayloadRegistrar registrar(String version) {
		if (registrar == null) registrar = new PayloadRegistrar(version);
		return registrar;
	}
}
