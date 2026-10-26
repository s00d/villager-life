package com.vnap.neo.neoforge.event.server;

public class ServerAboutToStartEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(ServerAboutToStartEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.server.ServerAboutToStartEvent.class, priority, ServerAboutToStartEvent::new, sink));
	}

	private final net.minecraftforge.event.server.ServerAboutToStartEvent f;

	ServerAboutToStartEvent(net.minecraftforge.event.server.ServerAboutToStartEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.server.MinecraftServer getServer() {
		return f.getServer();
	}
}
