package com.vnap.neo;

import com.vnap.VillagerLife;
import net.minecraftforge.api.distmarker.Dist;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.bus.api.IEventBus;
import com.vnap.neo.bus.api.SubscribeEvent;
import com.vnap.neo.fml.ModContainer;
import com.vnap.neo.fml.common.EventBusSubscriber;
import java.lang.annotation.ElementType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * The mod on Forge: Forge builds this class (its @Mod), which builds the mod's own as NeoForge would — the common one,
 * the client one on a client — on the NeoForge API of the shim, and starts the classes that subscribe by annotation.
 */
@net.minecraftforge.fml.common.Mod(VillagerLife.MOD_ID)
public final class ForgeEntry {
	public ForgeEntry(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
		//? if >=1.21.6 {
		/*Bridge.modBus(context.getModBusGroup());
		*///?} else
		Bridge.modBus(context.getModEventBus());
		//? if >=1.21.5
		/*com.vnap.neo.neoforge.common.world.chunk.TicketController.types();*/
		ModContainer container = new ModContainer(context.getContainer());
		new VillagerLife(IEventBus.INSTANCE);
		if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
			try {
				Class.forName("com.vnap.client.VillagerLifeClient").getConstructor(IEventBus.class, ModContainer.class).newInstance(IEventBus.INSTANCE, container);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("the client side of " + VillagerLife.MOD_ID, e);
			}
		}
		subscribers();
	}

	/** The classes marked @EventBusSubscriber (for this side): their static @SubscribeEvent methods listen. */
	private static void subscribers() {
		Dist side = net.minecraftforge.fml.loading.FMLEnvironment.dist;
		com.vnap.neo.fml.ModList.get().getModFileById(VillagerLife.MOD_ID).getFile().getScanResult()
				.getAnnotatedBy(EventBusSubscriber.class, ElementType.TYPE).forEach(found -> {
			Class<?> type;
			try {
				type = Class.forName(found.clazz().getClassName(), false, ForgeEntry.class.getClassLoader());
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException(found.clazz().getClassName(), e);
			}
			EventBusSubscriber marked = type.getAnnotation(EventBusSubscriber.class);
			if (marked == null || !Arrays.asList(marked.value()).contains(side)) return;
			for (Method method : type.getDeclaredMethods()) {
				SubscribeEvent subscribe = method.getAnnotation(SubscribeEvent.class);
				if (subscribe == null || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) continue;
				listen(method, subscribe);
			}
		});
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
