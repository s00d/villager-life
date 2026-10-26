package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.bus.api.ICancellableEvent;

public final class RenderHandEvent extends Event implements ICancellableEvent {
	static {
		Bridge.bind(RenderHandEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.client.event.RenderHandEvent.class, priority, RenderHandEvent::new, sink));
	}

	RenderHandEvent(net.minecraftforge.client.event.RenderHandEvent f) {
		super(f);
	}
}
