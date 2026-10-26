package com.vnap.neo.neoforge.event.server;

public class ServerStartingEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(ServerStartingEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.server.ServerStartingEvent.class, priority, ServerStartingEvent::new, sink));
	}

	private final net.minecraftforge.event.server.ServerStartingEvent f;

	ServerStartingEvent(net.minecraftforge.event.server.ServerStartingEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.server.MinecraftServer getServer() {
		return f.getServer();
	}
}
