package com.vnap.neo;

import com.vnap.neo.bus.api.Event;
import com.vnap.neo.bus.api.EventPriority;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge's events on Forge. Each NeoForge event class of the shim binds itself (in its static initialiser) to the
 * Forge event it stands for: the first listener at a priority attaches one Forge listener at that priority, which wraps
 * each Forge event and hands the wrapper to the mod's listeners there; a cancel goes back to the Forge event. Forge's
 * buses: EventBus 6 (MinecraftForge.EVENT_BUS and the mod's IEventBus) until 1.21.5, EventBus 7 (a bus per event
 * class, the mod's BusGroup) from 1.21.6.
 */
public final class Bridge {
	/** How a NeoForge event class is fired on Forge: attach one Forge listener at a priority that hands each event to the sink. */
	public interface Binding<T extends Event> {
		void attach(EventPriority priority, Consumer<T> sink);
	}

	private record Listener(boolean receiveCanceled, Consumer<Event> consumer) {
	}

	private record Key(Class<?> type, EventPriority priority) {
	}

	private static final Map<Class<?>, Binding<?>> BINDINGS = new ConcurrentHashMap<>();
	private static final Map<Key, List<Listener>> LISTENERS = new ConcurrentHashMap<>();
	//? if >=1.21.6 {
	/*static net.minecraftforge.eventbus.api.bus.@Nullable BusGroup modGroup;
	*///?} else
	static net.minecraftforge.eventbus.api.@Nullable IEventBus modBus;

	private Bridge() {
	}

	/** The mod's own bus, from Forge's mod loading context. */
	//? if >=1.21.6 {
	/*static void modBus(net.minecraftforge.eventbus.api.bus.BusGroup group) {
		modGroup = group;
	}
	*///?} else {
	static void modBus(net.minecraftforge.eventbus.api.IEventBus bus) {
		modBus = bus;
	}
	//?}

	public static <T extends Event> void bind(Class<T> type, Binding<T> binding) {
		BINDINGS.put(type, binding);
	}

	/** An event Forge does not have: the shim's own hooks (com.vnap.neo.mixin) fire it with {@link #fire}. */
	public static <T extends Event> void own(Class<T> type) {
		BINDINGS.put(type, (priority, sink) -> {
		});
	}

	/** Hands one of the shim's own events to its listeners, highest priority first; the event comes back (cancelled or not). */
	public static <T extends Event> T fire(T event) {
		for (EventPriority priority : EventPriority.values()) {
			List<Listener> listeners = LISTENERS.get(new Key(event.getClass(), priority));
			if (listeners != null) dispatch(listeners, event);
		}
		return event;
	}

	@SuppressWarnings("unchecked")
	public static <T extends Event> void listen(Class<T> type, EventPriority priority, boolean receiveCanceled, Consumer<T> consumer) {
		try {
			Class.forName(type.getName(), true, type.getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(type.getName(), e);
		}
		Binding<T> binding = (Binding<T>) BINDINGS.get(type);
		if (binding == null) throw new IllegalStateException("no Forge event stands for " + type.getName());
		List<Listener> list = LISTENERS.computeIfAbsent(new Key(type, priority), key -> {
			List<Listener> listeners = new CopyOnWriteArrayList<>();
			binding.attach(priority, event -> dispatch(listeners, event));
			return listeners;
		});
		list.add(new Listener(receiveCanceled, (Consumer<Event>) consumer));
	}

	private static void dispatch(List<Listener> listeners, Event event) {
		for (Listener listener : listeners) {
			if (!event.vnap$canceled() || listener.receiveCanceled()) listener.consumer().accept(event);
		}
	}

	/** Work handed to the game's thread: a failure is logged. */
	public static void logged(java.util.concurrent.CompletableFuture<?> work, String what) {
		//? if >=26.1 {
		/*var _ = work.exceptionally(error -> {
		*///?} else
		var unused = work.exceptionally(error -> {
			com.vnap.VillagerLife.LOGGER.error("{} failed", what, error);
			return null;
		});
	}

	/** A NeoForge event the mod made itself: its Forge event goes to Forge's bus (and so to every listener); a cancel comes back. */
	public static void post(Event event) {
		Object forge = event.vnap$forge();
		if (forge == null) throw new IllegalStateException(event.getClass().getName() + " has no Forge event to post");
		//? if >=1.21.6 {
		/*event.vnap$cancel(postForge(forge));
		*///?} else {
		var posted = (net.minecraftforge.eventbus.api.Event) forge;
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(posted);
		event.vnap$cancel(posted.isCancelable() && posted.isCanceled());
		//?}
	}

	/** A Forge game event (MinecraftForge's bus) as a NeoForge one. */
	public static <F, T extends Event> void game(Class<F> forge, EventPriority priority, Function<F, T> wrap, Consumer<T> sink) {
		on(forge, false, priority, wrap, sink);
	}

	/** A Forge mod-bus event as a NeoForge one. */
	public static <F, T extends Event> void mod(Class<F> forge, EventPriority priority, Function<F, T> wrap, Consumer<T> sink) {
		on(forge, true, priority, wrap, sink);
	}

	//? if >=1.21.6 {
	/*private static byte priority(EventPriority priority) {
		return switch (priority) {
			case HIGHEST -> net.minecraftforge.eventbus.api.listener.Priority.HIGHEST;
			case HIGH -> net.minecraftforge.eventbus.api.listener.Priority.HIGH;
			case NORMAL -> net.minecraftforge.eventbus.api.listener.Priority.NORMAL;
			case LOW -> net.minecraftforge.eventbus.api.listener.Priority.LOW;
			case LOWEST -> net.minecraftforge.eventbus.api.listener.Priority.LOWEST;
		};
	}

	/^* A Forge event class's bus: a game event's static BUS, a mod event's getBus(the mod's BusGroup). ^/
	static net.minecraftforge.eventbus.api.bus.EventBus<?> busOf(Class<?> forge, boolean mod) {
		try {
			// a mod-bus event of Forge 26 is a game-wide one (its static BUS); before, one of the mod's bus group
			if (mod && java.util.Arrays.stream(forge.getMethods()).anyMatch(method -> method.getName().equals("getBus"))) {
				return (net.minecraftforge.eventbus.api.bus.EventBus<?>) forge.getMethod("getBus", net.minecraftforge.eventbus.api.bus.BusGroup.class)
						.invoke(null, java.util.Objects.requireNonNull(modGroup, "the mod's bus group"));
			}
			return (net.minecraftforge.eventbus.api.bus.EventBus<?>) forge.getField("BUS").get(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("no bus for " + forge.getName(), e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static boolean postForge(Object forge) {
		var bus = (net.minecraftforge.eventbus.api.bus.EventBus) busOf(forge.getClass(), forge instanceof net.minecraftforge.fml.event.IModBusEvent);
		return bus.post((net.minecraftforge.eventbus.internal.Event) forge);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <F, T extends Event> void on(Class<F> forge, boolean mod, EventPriority priority, Function<F, T> wrap, Consumer<T> sink) {
		var bus = busOf(forge, mod);
		byte at = priority(priority);
		// an event with no bus of its own is posted on its parent's (CriticalHitEvent on PlayerEvent's), with its siblings
		if (bus instanceof net.minecraftforge.eventbus.api.bus.CancellableEventBus cancellable) {
			cancellable.addListener(at, (java.util.function.Predicate) f -> {
				if (!forge.isInstance(f)) return false;
				T event = wrap.apply((F) f);
				sink.accept(event);
				return event.vnap$canceled();
			});
		} else {
			((net.minecraftforge.eventbus.api.bus.EventBus) bus).addListener(at, (Consumer) f -> {
				if (forge.isInstance(f)) sink.accept(wrap.apply((F) f));
			});
		}
	}
	*///?} else {
	private static net.minecraftforge.eventbus.api.EventPriority priority(EventPriority priority) {
		return switch (priority) {
			case HIGHEST -> net.minecraftforge.eventbus.api.EventPriority.HIGHEST;
			case HIGH -> net.minecraftforge.eventbus.api.EventPriority.HIGH;
			case NORMAL -> net.minecraftforge.eventbus.api.EventPriority.NORMAL;
			case LOW -> net.minecraftforge.eventbus.api.EventPriority.LOW;
			case LOWEST -> net.minecraftforge.eventbus.api.EventPriority.LOWEST;
		};
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <F, T extends Event> void on(Class<F> forge, boolean mod, EventPriority priority, Function<F, T> wrap, Consumer<T> sink) {
		var bus = mod ? java.util.Objects.requireNonNull(modBus, "the mod's bus") : net.minecraftforge.common.MinecraftForge.EVENT_BUS;
		bus.addListener(priority(priority), true, (Class) forge, (Consumer) f -> {
			var posted = (net.minecraftforge.eventbus.api.Event) f;
			T event = wrap.apply((F) f);
			if (posted.isCancelable()) event.vnap$cancel(posted.isCanceled());
			sink.accept(event);
			if (posted.isCancelable()) posted.setCanceled(event.vnap$canceled());
		});
	}
	//?}
}
