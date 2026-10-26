package com.vnap.fab.neoforge.client.gui;

import net.minecraft.resources.ResourceLocation;

/** NeoForge's names of the vanilla HUD layers the mod draws next to (com.vnap.fab.mixin.client.GuiLayersMixin draws by them). */
public final class VanillaGuiLayers {
	public static final ResourceLocation HOTBAR = ResourceLocation.withDefaultNamespace("hotbar");
	public static final ResourceLocation OVERLAY_MESSAGE = ResourceLocation.withDefaultNamespace("overlay_message");

	private VanillaGuiLayers() {
	}
}
