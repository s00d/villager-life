package com.vnap.neo.neoforge.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** The server's data listeners (NeoForge names each one; Forge's event takes them as they come). */
public final class AddServerReloadListenersEvent extends Event {
	static {
		Bridge.bind(AddServerReloadListenersEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.AddReloadListenerEvent.class, priority,
				AddServerReloadListenersEvent::new, sink));
	}

	private final net.minecraftforge.event.AddReloadListenerEvent f;

	AddServerReloadListenersEvent(net.minecraftforge.event.AddReloadListenerEvent f) {
		super(f);
		this.f = f;
	}

	public void addListener(ResourceLocation id, PreparableReloadListener listener) {
		f.addListener(listener);
	}
}
