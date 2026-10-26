package com.vnap.fab;

import com.vnap.fab.bus.api.IEventBus;
import com.vnap.fab.fml.ModContainer;

/** The mod's client side on Fabric: built as NeoForge would build it; Fabric API's client callbacks become the shim's events. */
public final class FabricClientEntry implements net.fabricmc.api.ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModContainer container = new ModContainer(net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(com.vnap.VillagerLife.MOD_ID).orElseThrow());
		new com.vnap.client.VillagerLifeClient(IEventBus.INSTANCE, container);
		ClientHooks.client();
		Bridge.fire(new com.vnap.fab.fml.event.lifecycle.FMLClientSetupEvent());
	}
}
