package com.vnap.neo.neoforge.registries;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public final class RegisterEvent extends Event {
	static {
		Bridge.bind(RegisterEvent.class, (priority, sink) -> Bridge.mod(net.minecraftforge.registries.RegisterEvent.class, priority, RegisterEvent::new, sink));
	}

	private final net.minecraftforge.registries.RegisterEvent f;

	RegisterEvent(net.minecraftforge.registries.RegisterEvent f) {
		super(f);
		this.f = f;
	}

	public ResourceKey<? extends Registry<?>> getRegistryKey() {
		return f.getRegistryKey();
	}

	public <T> void register(ResourceKey<? extends Registry<T>> key, Consumer<RegisterHelper<T>> consumer) {
		f.register(key, helper -> consumer.accept(helper::register));
	}

	@FunctionalInterface
	public interface RegisterHelper<T> {
		void register(ResourceLocation name, T value);

		default void register(ResourceKey<T> key, T value) {
			register(com.vnap.Mc.id(key), value);
		}
	}
}
