package com.vnap.neo.neoforge.event.server;

public class ServerStoppingEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(ServerStoppingEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.server.ServerStoppingEvent.class, priority, ServerStoppingEvent::new, sink));
	}

	private final net.minecraftforge.event.server.ServerStoppingEvent f;

	ServerStoppingEvent(net.minecraftforge.event.server.ServerStoppingEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.server.MinecraftServer getServer() {
		return f.getServer();
	}
}
