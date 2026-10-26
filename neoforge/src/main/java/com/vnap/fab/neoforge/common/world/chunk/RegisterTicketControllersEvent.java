package com.vnap.fab.neoforge.common.world.chunk;

/** Fired with the mod's common setup (com.vnap.fab.FabricEntry). */
public final class RegisterTicketControllersEvent extends com.vnap.fab.bus.api.Event {
	public RegisterTicketControllersEvent() {
	}

	public void register(TicketController controller) {
		TicketController.register(controller);
	}
}
