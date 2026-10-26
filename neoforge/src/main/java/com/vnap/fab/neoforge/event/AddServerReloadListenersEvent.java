package com.vnap.fab.neoforge.event;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** The server's data listeners, each reload (com.vnap.fab.mixin.ServerResourcesMixin); NeoForge names each one. */
public final class AddServerReloadListenersEvent extends com.vnap.fab.bus.api.Event {
	private final List<PreparableReloadListener> listeners;

	public AddServerReloadListenersEvent(List<PreparableReloadListener> listeners) {
		this.listeners = listeners;
	}

	public void addListener(ResourceLocation id, PreparableReloadListener listener) {
		listeners.add(listener);
	}
}
