package com.vnap.neo.neoforge.event;

public class AddReloadListenerEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(AddReloadListenerEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.AddReloadListenerEvent.class, priority, AddReloadListenerEvent::new, sink));
	}

	private final net.minecraftforge.event.AddReloadListenerEvent f;

	AddReloadListenerEvent(net.minecraftforge.event.AddReloadListenerEvent f) {
		super(f);
		this.f = f;
	}

	public void addListener(net.minecraft.server.packs.resources.PreparableReloadListener listener) {
		f.addListener(listener);
	}
}
