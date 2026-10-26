package com.vnap.neo.neoforge.client.event;

public final class ClientTickEvent {
	private ClientTickEvent() {
	}

	public static class Pre extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(ClientTickEvent.Pre.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.TickEvent.ClientTickEvent.Pre.class, priority, Pre::new, sink));
		}

		Pre(net.minecraftforge.event.TickEvent.ClientTickEvent.Pre f) {
			super(f);
		}
	}

	public static class Post extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(ClientTickEvent.Post.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.TickEvent.ClientTickEvent.Post.class, priority, Post::new, sink));
		}

		Post(net.minecraftforge.event.TickEvent.ClientTickEvent.Post f) {
			super(f);
		}
	}
}
