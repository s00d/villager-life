package com.vnap.fab.neoforge.client.extensions.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.Item;

/** With the client setup (com.vnap.fab.FabricClientEntry): an item's client extensions, kept by the shim. */
public final class RegisterClientExtensionsEvent extends com.vnap.fab.bus.api.Event {
	static final Map<Item, IClientItemExtensions> ITEMS = new ConcurrentHashMap<>();

	public RegisterClientExtensionsEvent() {
	}

	public void registerItem(IClientItemExtensions extensions, Item... items) {
		for (Item item : items) ITEMS.put(item, extensions);
	}
}
