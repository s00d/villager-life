package com.vnap.neo.neoforge.event.tick;

public final class ServerTickEvent {
	private ServerTickEvent() {
	}

	public static class Pre extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(ServerTickEvent.Pre.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.TickEvent.ServerTickEvent.Pre.class, priority, Pre::new, sink));
		}

		private final net.minecraftforge.event.TickEvent.ServerTickEvent.Pre f;

		Pre(net.minecraftforge.event.TickEvent.ServerTickEvent.Pre f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.server.MinecraftServer getServer() {
			//? if >=1.21.9 {
			/*return f.server();
			*///?} else
			return f.getServer();
		}
	}

	public static class Post extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(ServerTickEvent.Post.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.TickEvent.ServerTickEvent.Post.class, priority, Post::new, sink));
		}

		private final net.minecraftforge.event.TickEvent.ServerTickEvent.Post f;

		Post(net.minecraftforge.event.TickEvent.ServerTickEvent.Post f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.server.MinecraftServer getServer() {
			//? if >=1.21.9 {
			/*return f.server();
			*///?} else
			return f.getServer();
		}
	}
}
