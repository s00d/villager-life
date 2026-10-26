package com.vnap.fab.neoforge.network.event;

import com.vnap.fab.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.Nullable;

/** With the mod's common setup (com.vnap.fab.FabricEntry): what is registered here goes to Fabric API's networking. */
public final class RegisterPayloadHandlersEvent extends com.vnap.fab.bus.api.Event {
	private @Nullable PayloadRegistrar registrar;

	public RegisterPayloadHandlersEvent() {
	}

	public PayloadRegistrar registrar(String version) {
		if (registrar == null) registrar = new PayloadRegistrar(version);
		return registrar;
	}

	public @Nullable PayloadRegistrar vnap$registrar() {
		return registrar;
	}
}
