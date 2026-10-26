package com.vnap.neo.neoforge.client.extensions.common;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.minecraft.world.item.Item;

/** With the client setup: an item's client extensions go where Forge keeps them, in the item. */
public final class RegisterClientExtensionsEvent extends Event {
	static {
		Bridge.bind(RegisterClientExtensionsEvent.class, (priority, sink) -> Bridge.mod(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent.class, priority,
				RegisterClientExtensionsEvent::new, sink));
	}

	RegisterClientExtensionsEvent(Object forge) {
		super(forge);
	}

	public void registerItem(net.minecraftforge.client.extensions.common.IClientItemExtensions extensions, Item... items) {
		for (Item item : items) ((com.vnap.neo.mixin.client.ItemRenderPropertiesAccessor) item).vnap$setRenderProperties(extensions);
	}
}
