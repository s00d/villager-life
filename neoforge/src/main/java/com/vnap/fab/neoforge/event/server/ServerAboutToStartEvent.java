package com.vnap.fab.neoforge.event.server;

public class ServerAboutToStartEvent extends com.vnap.fab.bus.api.Event {
	private final net.minecraft.server.MinecraftServer server;

	public ServerAboutToStartEvent(net.minecraft.server.MinecraftServer server) {
		this.server = server;
	}

	public net.minecraft.server.MinecraftServer getServer() {
		return server;
	}
}
