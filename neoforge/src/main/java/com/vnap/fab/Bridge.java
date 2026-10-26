package com.vnap.fab;

import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.EventPriority;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * NeoForge's events on Fabric: Fabric has no event bus, so each event is the shim's own, fired by its hooks (a Fabric API
 * callback or a mixin of com.vnap.fab.mixin) with {@link #fire}. A listener of a class hears its subclasses too, as on
 * NeoForge's bus.
 */
public final class Bridge {
	private record Listener(EventPriority priority, boolean receiveCanceled, Consumer<Event> consumer) {
	}

	private static final Map<Class<?>, List<Listener>> LISTENERS = new ConcurrentHashMap<>();

	private Bridge() {
	}

	@SuppressWarnings("unchecked")
	public static <T extends Event> void listen(Class<T> type, EventPriority priority, boolean receiveCanceled, Consumer<T> consumer) {
		LISTENERS.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>()).add(new Listener(priority, receiveCanceled, (Consumer<Event>) consumer));
	}

	/** Whether anything listens for this class of event (a hook skips building one nobody hears). */
	public static boolean heard(Class<? extends Event> type) {
		for (Class<?> c = type; c != null && Event.class.isAssignableFrom(c); c = c.getSuperclass()) {
			if (LISTENERS.containsKey(c)) return true;
		}
		return false;
	}

	/** Hands the event to its listeners and those of its superclasses, highest priority first; the event comes back. */
	public static <T extends Event> T fire(T event) {
		for (EventPriority priority : EventPriority.values()) {
			for (Class<?> c = event.getClass(); c != null && Event.class.isAssignableFrom(c); c = c.getSuperclass()) {
				List<Listener> listeners = LISTENERS.get(c);
				if (listeners == null) continue;
				for (Listener listener : listeners) {
					if (listener.priority() == priority && (!event.vnap$canceled() || listener.receiveCanceled())) listener.consumer().accept(event);
				}
			}
		}
		return event;
	}
}
