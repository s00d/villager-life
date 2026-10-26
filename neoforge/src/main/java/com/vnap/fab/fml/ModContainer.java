package com.vnap.fab.fml;

/** The mod's container: on Fabric the config screen goes to Mod Menu if it is there (com.vnap.fab.ModMenu). */
public final class ModContainer {
	private final net.fabricmc.loader.api.ModContainer fabric;

	public ModContainer(net.fabricmc.loader.api.ModContainer fabric) {
		this.fabric = fabric;
	}

	public String getModId() {
		return fabric.getMetadata().getId();
	}

	public String getNamespace() {
		return getModId();
	}

	public <T> void registerExtensionPoint(Class<T> type, T point) {
		if (point instanceof com.vnap.fab.neoforge.client.gui.IConfigScreenFactory factory) {
			com.vnap.fab.neoforge.client.gui.IConfigScreenFactory.Registered.set(factory);
			return;
		}
		throw new IllegalArgumentException("no Fabric extension point for " + type.getName());
	}
}
