package com.vnap.fab.bus.api;

import java.util.function.Consumer;

/** A NeoForge bus on Fabric: NeoForge.EVENT_BUS and a mod's bus are both this one (every event is the shim's own). */
public final class IEventBus {
	public static final IEventBus INSTANCE = new IEventBus();

	private IEventBus() {
	}

	public <T extends Event> void addListener(Class<T> type, Consumer<T> listener) {
		addListener(EventPriority.NORMAL, false, type, listener);
	}

	public <T extends Event> void addListener(EventPriority priority, Class<T> type, Consumer<T> listener) {
		addListener(priority, false, type, listener);
	}

	public <T extends Event> void addListener(boolean receiveCanceled, Class<T> type, Consumer<T> listener) {
		addListener(EventPriority.NORMAL, receiveCanceled, type, listener);
	}

	public <T extends Event> void addListener(EventPriority priority, boolean receiveCanceled, Class<T> type, Consumer<T> listener) {
		com.vnap.fab.Bridge.listen(type, priority, receiveCanceled, listener);
	}

	public <T extends Event> T post(T event) {
		return com.vnap.fab.Bridge.fire(event);
	}
}
