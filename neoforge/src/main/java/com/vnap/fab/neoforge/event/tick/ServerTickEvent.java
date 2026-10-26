package com.vnap.fab.neoforge.event.tick;

import net.minecraft.server.MinecraftServer;

public final class ServerTickEvent {
	private ServerTickEvent() {
	}

	public static class Pre extends com.vnap.fab.bus.api.Event {
		private final MinecraftServer server;

		public Pre(MinecraftServer server) {
			this.server = server;
		}

		public MinecraftServer getServer() {
			return server;
		}
	}

	public static class Post extends com.vnap.fab.bus.api.Event {
		private final MinecraftServer server;

		public Post(MinecraftServer server) {
			this.server = server;
		}

		public MinecraftServer getServer() {
			return server;
		}
	}
}
