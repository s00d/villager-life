package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.bus.api.Event;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The mod's HUD layers, each next to a vanilla one: up to 1.21.5 the shim draws them (com.vnap.fab.mixin.client.GuiLayersMixin),
 * from 1.21.6 Fabric API's HUD elements take them (com.vnap.fab.ClientHooks).
 */
public final class RegisterGuiLayersEvent extends Event {
	/** A HUD layer: what it draws, the frame's time. */
	@FunctionalInterface
	public interface Layer {
		void render(GuiGraphics graphics, DeltaTracker delta);
	}

	public static final Map<ResourceLocation, List<Layer>> BELOW = new ConcurrentHashMap<>();
	public static final Map<ResourceLocation, List<Layer>> ABOVE = new ConcurrentHashMap<>();
	public static final List<Layer> TOP = new CopyOnWriteArrayList<>();

	public RegisterGuiLayersEvent() {
	}

	public void registerAbove(ResourceLocation other, ResourceLocation id, Layer layer) {
		ABOVE.computeIfAbsent(other, key -> new CopyOnWriteArrayList<>()).add(layer);
	}

	public void registerBelow(ResourceLocation other, ResourceLocation id, Layer layer) {
		BELOW.computeIfAbsent(other, key -> new CopyOnWriteArrayList<>()).add(layer);
	}

	public void registerAboveAll(ResourceLocation id, Layer layer) {
		TOP.add(layer);
	}

	/** Draws the layers the mod set this side of a vanilla one. */
	public static void draw(Map<ResourceLocation, List<Layer>> side, ResourceLocation vanilla, GuiGraphics graphics, DeltaTracker delta) {
		List<Layer> layers = side.get(vanilla);
		if (layers != null) for (Layer layer : layers) layer.render(graphics, delta);
	}
}
