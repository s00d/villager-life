package com.vnap.fab.neoforge.client.gui;

import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** The mod's config screen: on Fabric the Mod Menu button opens it (com.vnap.fab.client.ModMenu), when Mod Menu is there. */
@FunctionalInterface
public interface IConfigScreenFactory {
	Screen createScreen(com.vnap.fab.fml.ModContainer container, Screen parent);

	/** (Kept by ModContainer.registerExtensionPoint.) */
	final class Registered {
		static @Nullable IConfigScreenFactory factory;

		private Registered() {
		}

		public static @Nullable IConfigScreenFactory get() {
			return factory;
		}

		public static void set(IConfigScreenFactory made) {
			factory = made;
		}
	}
}
