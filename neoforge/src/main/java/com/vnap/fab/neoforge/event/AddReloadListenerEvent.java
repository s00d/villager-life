package com.vnap.fab.neoforge.event;

import java.util.List;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/** The server's data listeners, each reload (com.vnap.fab.mixin.ServerResourcesMixin). */
public class AddReloadListenerEvent extends com.vnap.fab.bus.api.Event {
	private final List<PreparableReloadListener> listeners;

	public AddReloadListenerEvent(List<PreparableReloadListener> listeners) {
		this.listeners = listeners;
	}

	public void addListener(PreparableReloadListener listener) {
		listeners.add(listener);
	}
}
