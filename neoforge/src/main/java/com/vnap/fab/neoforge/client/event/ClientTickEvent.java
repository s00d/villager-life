package com.vnap.fab.neoforge.client.event;

/** Around the client's tick (Fabric API ClientTickEvents). */
public final class ClientTickEvent {
	private ClientTickEvent() {
	}

	public static class Pre extends com.vnap.fab.bus.api.Event {

		public Pre() {
		}
	}

	public static class Post extends com.vnap.fab.bus.api.Event {

		public Post() {
		}
	}
}
