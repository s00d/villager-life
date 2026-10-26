package com.vnap.neo.bus.api;

import java.util.function.Consumer;

/**
 * A NeoForge bus on Forge: NeoForge.EVENT_BUS and a mod's bus are both this one, since each event knows which Forge bus
 * it comes from (com.vnap.neo.Bridge). Only the listeners that name their event's class: Forge's buses since 1.21.6
 * cannot read it from a lambda.
 */
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
		com.vnap.neo.Bridge.listen(type, priority, receiveCanceled, listener);
	}

	/** Fires an event made by the mod (a test's): its Forge event goes on Forge's bus, and with it to every listener. */
	public <T extends Event> T post(T event) {
		com.vnap.neo.Bridge.post(event);
		return event;
	}
}
