package com.vnap.fab.neoforge.client.event;

/** The client leaving its server (Fabric API ClientPlayConnectionEvents.DISCONNECT). */
public final class ClientPlayerNetworkEvent {
	private ClientPlayerNetworkEvent() {
	}

	public static class LoggingOut extends com.vnap.fab.bus.api.Event {

		public LoggingOut() {
		}
	}
}
