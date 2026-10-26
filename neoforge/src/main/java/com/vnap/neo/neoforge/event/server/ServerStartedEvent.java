package com.vnap.neo.neoforge.event.server;

public class ServerStartedEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(ServerStartedEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.server.ServerStartedEvent.class, priority, ServerStartedEvent::new, sink));
	}

	private final net.minecraftforge.event.server.ServerStartedEvent f;

	ServerStartedEvent(net.minecraftforge.event.server.ServerStartedEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.server.MinecraftServer getServer() {
		return f.getServer();
	}
}
