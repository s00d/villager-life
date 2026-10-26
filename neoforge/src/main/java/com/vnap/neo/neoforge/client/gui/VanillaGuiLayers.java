package com.vnap.neo.neoforge.client.gui;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

/** NeoForge's names of the vanilla HUD layers the mod draws next to, as Forge names them. */
public final class VanillaGuiLayers {
	//? if >=1.21.6 {
	/*public static final ResourceLocation HOTBAR = ForgeLayeredDraw.HOTBAR_AND_DECOS;
	*///?} else
	public static final ResourceLocation HOTBAR = ForgeLayeredDraw.HOTBAR;
	public static final ResourceLocation OVERLAY_MESSAGE = ForgeLayeredDraw.HOTBAR_MESSAGE;

	private static final List<ResourceLocation> POST_SLEEP = List.of(ForgeLayeredDraw.DEMO_OVERLAY, ForgeLayeredDraw.DEBUG_OVERLAY,
			ForgeLayeredDraw.SCOREBOARD, ForgeLayeredDraw.HOTBAR_MESSAGE, ForgeLayeredDraw.TITLE_OVERLAY, ForgeLayeredDraw.CHAT_OVERLAY,
			ForgeLayeredDraw.TAB_LIST, ForgeLayeredDraw.SUBTITLE_OVERLAY);

	private VanillaGuiLayers() {
	}

	/** The stack a vanilla layer is in: before the sleep overlay or after it. */
	public static ResourceLocation stackOf(ResourceLocation layer) {
		return POST_SLEEP.contains(layer) ? ForgeLayeredDraw.POST_SLEEP_STACK : ForgeLayeredDraw.PRE_SLEEP_STACK;
	}
}
