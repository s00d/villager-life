package com.vnap.neo.neoforge.common.world.chunk;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;

/** Fired with the mod's common setup: a controller registered here sets the mod's callback on Forge's chunk manager. */
public final class RegisterTicketControllersEvent extends Event {
	static {
		Bridge.bind(RegisterTicketControllersEvent.class, (priority, sink) -> Bridge.mod(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent.class, priority,
				RegisterTicketControllersEvent::new, sink));
	}

	RegisterTicketControllersEvent(Object forge) {
		super(forge);
	}

	public void register(TicketController controller) {
		TicketController.register(controller);
	}
}
