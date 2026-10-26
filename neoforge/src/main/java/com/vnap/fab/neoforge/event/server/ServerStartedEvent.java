package com.vnap.fab.neoforge.event.server;

public class ServerStartedEvent extends com.vnap.fab.bus.api.Event {
	private final net.minecraft.server.MinecraftServer server;

	public ServerStartedEvent(net.minecraft.server.MinecraftServer server) {
		this.server = server;
	}

	public net.minecraft.server.MinecraftServer getServer() {
		return server;
	}
}
