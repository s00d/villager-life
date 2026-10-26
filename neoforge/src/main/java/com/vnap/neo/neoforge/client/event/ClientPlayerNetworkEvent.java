package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;

public final class ClientPlayerNetworkEvent {
	private ClientPlayerNetworkEvent() {
	}

	public static final class LoggingOut extends Event {
		static {
			Bridge.bind(LoggingOut.class, (priority, sink) -> Bridge.game(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut.class, priority,
					LoggingOut::new, sink));
		}

		LoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut f) {
			super(f);
		}
	}
}
