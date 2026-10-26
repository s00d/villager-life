package com.vnap.fab.neoforge.registries;

import com.vnap.fab.bus.api.Event;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** One registry's turn to take the mod's entries: Fabric's registries are open while the mod starts (FabricEntry). */
public final class RegisterEvent extends Event {
	private final Registry<?> registry;

	public RegisterEvent(Registry<?> registry) {
		this.registry = registry;
	}

	public ResourceKey<? extends Registry<?>> getRegistryKey() {
		return registry.key();
	}

	@SuppressWarnings("unchecked")
	public <T> void register(ResourceKey<? extends Registry<T>> key, Consumer<RegisterHelper<T>> consumer) {
		if (!registry.key().equals(key)) return;
		Registry<T> target = (Registry<T>) registry;
		consumer.accept((name, value) -> Registry.register(target, name, value));
	}

	@FunctionalInterface
	public interface RegisterHelper<T> {
		void register(ResourceLocation name, T value);

		default void register(ResourceKey<T> key, T value) {
			register(com.vnap.Mc.id(key), value);
		}
	}
}
