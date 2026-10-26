package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.neoforge.client.gui.VanillaGuiLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

/** The mod's HUD layers: Forge's AddGuiOverlayLayersEvent, each next to a vanilla one in that one's stack. */
public final class RegisterGuiLayersEvent extends Event {
	/** A HUD layer: vanilla's LayeredDraw.Layer until 1.21.5, Forge's own ForgeLayer from 1.21.6. */
	//? if >=1.21.6 {
	/*public interface Layer extends net.minecraftforge.client.gui.overlay.ForgeLayer {
	}
	*///?} else {
	public interface Layer extends net.minecraft.client.gui.LayeredDraw.Layer {
	}
	//?}

	static {
		Bridge.bind(RegisterGuiLayersEvent.class, (priority, sink) -> Bridge.mod(net.minecraftforge.client.event.AddGuiOverlayLayersEvent.class, priority,
				RegisterGuiLayersEvent::new, sink));
	}

	private final ForgeLayeredDraw draw;

	RegisterGuiLayersEvent(net.minecraftforge.client.event.AddGuiOverlayLayersEvent f) {
		super(f);
		this.draw = f.getLayeredDraw();
	}

	public void registerAbove(ResourceLocation other, ResourceLocation id, Layer layer) {
		draw.addAbove(VanillaGuiLayers.stackOf(other), id, other, layer);
	}

	public void registerBelow(ResourceLocation other, ResourceLocation id, Layer layer) {
		draw.addBelow(VanillaGuiLayers.stackOf(other), id, other, layer);
	}

	public void registerAboveAll(ResourceLocation id, Layer layer) {
		draw.add(ForgeLayeredDraw.POST_SLEEP_STACK, id, layer);
	}
}
