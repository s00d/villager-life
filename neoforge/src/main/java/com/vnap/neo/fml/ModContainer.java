package com.vnap.neo.fml;

/** The mod's container: on Forge its extension points go to Forge's (the config screen of the mod list). */
public final class ModContainer {
	private final net.minecraftforge.fml.ModContainer forge;

	public ModContainer(net.minecraftforge.fml.ModContainer forge) {
		this.forge = forge;
	}

	public String getModId() {
		return forge.getModId();
	}

	public String getNamespace() {
		return forge.getNamespace();
	}

	public <T> void registerExtensionPoint(Class<T> type, T point) {
		if (point instanceof com.vnap.neo.neoforge.client.gui.IConfigScreenFactory factory) {
			forge.registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
					() -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> factory.createScreen(this, parent)));
			return;
		}
		throw new IllegalArgumentException("no Forge extension point for " + type.getName());
	}
}
