package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;

public final class ModelEvent {
	private ModelEvent() {
	}

	//? if >=1.21.5 {
	/*/^* A model of the mod's own: on Forge a state definition of it (com.vnap.neo.client.Standalone). ^/
	public static final class RegisterStandalone extends Event {
		static {
			Bridge.bind(RegisterStandalone.class, (priority, sink) -> Bridge.mod(net.minecraftforge.client.event.ModelEvent.RegisterModelStateDefinitions.class, priority,
					RegisterStandalone::new, sink));
		}

		private final net.minecraftforge.client.event.ModelEvent.RegisterModelStateDefinitions f;

		RegisterStandalone(net.minecraftforge.client.event.ModelEvent.RegisterModelStateDefinitions f) {
			super(f);
			this.f = f;
		}

		public void register(net.minecraft.resources.ResourceLocation model) {
			f.register(com.vnap.neo.client.Standalone.state(model), com.vnap.neo.client.Standalone.definition(model));
		}
	}
	*///?} else if >=1.21.2 {
	/*/^* A model of the mod's own: on Forge a state definition of it (com.vnap.neo.client.Standalone). ^/
	public static final class RegisterAdditional extends Event {
		static {
			Bridge.bind(RegisterAdditional.class, (priority, sink) -> Bridge.mod(net.minecraftforge.client.event.ModelEvent.RegisterModelStateDefinitions.class, priority,
					RegisterAdditional::new, sink));
		}

		private final net.minecraftforge.client.event.ModelEvent.RegisterModelStateDefinitions f;

		RegisterAdditional(net.minecraftforge.client.event.ModelEvent.RegisterModelStateDefinitions f) {
			super(f);
			this.f = f;
		}

		public void register(net.minecraft.resources.ResourceLocation model) {
			f.register(com.vnap.neo.client.Standalone.state(model), com.vnap.neo.client.Standalone.definition(model));
		}
	}
	*///?} else {
	public static final class RegisterAdditional extends Event {
		static {
			Bridge.bind(RegisterAdditional.class, (priority, sink) -> Bridge.mod(net.minecraftforge.client.event.ModelEvent.RegisterAdditional.class, priority,
					RegisterAdditional::new, sink));
		}

		private final net.minecraftforge.client.event.ModelEvent.RegisterAdditional f;

		RegisterAdditional(net.minecraftforge.client.event.ModelEvent.RegisterAdditional f) {
			super(f);
			this.f = f;
		}

		public void register(net.minecraft.client.resources.model.ModelResourceLocation model) {
			f.register(model);
		}
	}
	//?}
}
