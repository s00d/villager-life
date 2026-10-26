package com.vnap.fab;

import com.vnap.VillagerLife;
import com.vnap.fab.api.distmarker.Dist;
import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.IEventBus;
import com.vnap.fab.bus.api.SubscribeEvent;
import com.vnap.fab.fml.common.EventBusSubscriber;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The mod on Fabric: builds the mod as NeoForge would (its common side here, the client side in FabricClientEntry) on
 * the NeoForge API of the shim, starts the classes that subscribe by annotation, hands each registry its turn
 * (RegisterEvent: Fabric's registries are open now) and fires common setup. Fabric API's callbacks become the shim's
 * events in Hooks.
 */
public final class FabricEntry implements net.fabricmc.api.ModInitializer {
	@Override
	public void onInitialize() {
		new VillagerLife(IEventBus.INSTANCE);
		subscribers(com.vnap.fab.fml.loading.FMLEnvironment.dist);
		com.vnap.fab.neoforge.common.world.chunk.TicketController.types();
		for (var registry : BuiltInRegistries.REGISTRY) {
			Bridge.fire(new com.vnap.fab.neoforge.registries.RegisterEvent(registry));
		}
		Hooks.common();
		Bridge.fire(new com.vnap.fab.neoforge.common.world.chunk.RegisterTicketControllersEvent());
		var payloads = Bridge.fire(new com.vnap.fab.neoforge.network.event.RegisterPayloadHandlersEvent()).vnap$registrar();
		if (payloads != null) payloads.build();
		Bridge.fire(new com.vnap.fab.fml.event.lifecycle.FMLCommonSetupEvent());
	}

	/** The classes marked @EventBusSubscriber for this side (the build lists them): their static @SubscribeEvent methods listen. */
	static void subscribers(Dist side) {
		try (InputStream in = FabricEntry.class.getResourceAsStream("/META-INF/vnap-subscribers.txt")) {
			if (in == null) throw new IllegalStateException("the build lists no subscribers");
			new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().filter(line -> !line.isBlank()).forEach(name -> {
				Class<?> type;
				try {
					type = Class.forName(name, false, FabricEntry.class.getClassLoader());
				} catch (ClassNotFoundException e) {
					// a class the version's build leaves out
					return;
				}
				EventBusSubscriber marked = type.getAnnotation(EventBusSubscriber.class);
				if (marked == null || !Arrays.asList(marked.value()).contains(side)) return;
				for (Method method : type.getDeclaredMethods()) {
					SubscribeEvent subscribe = method.getAnnotation(SubscribeEvent.class);
					if (subscribe == null || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) continue;
					listen(method, subscribe);
				}
			});
		} catch (IOException e) {
			throw new IllegalStateException("the subscribers' list", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static void listen(Method method, SubscribeEvent subscribe) {
		Class<? extends Event> type = (Class<? extends Event>) method.getParameterTypes()[0];
		method.setAccessible(true);
		Bridge.listen(type, subscribe.priority(), subscribe.receiveCanceled(), event -> {
			try {
				method.invoke(null, event);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(method.toString(), e);
			}
		});
	}
}
