package com.vnap.fab.neoforge.client.event;

//? if >=26.2 {
/*import com.vnap.fab.bus.api.Event;
import java.util.function.Supplier;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;

/^* The mod's renderers of pictures in the GUI: Fabric API's special GUI elements (from 26.2 a renderer is made from nothing). ^/
public final class RegisterPictureInPictureRenderersEvent extends Event {
	public RegisterPictureInPictureRenderersEvent() {
	}

	public <T extends PictureInPictureRenderState> void register(Class<T> type, Supplier<PictureInPictureRenderer<T>> factory) {
		net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry.register(context -> factory.get());
	}
}
*///?} else if >=1.21.6 {
/*import com.vnap.fab.bus.api.Event;
import java.util.function.Function;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;

/^* The mod's renderers of pictures in the GUI: Fabric API's special GUI elements (each renderer made with the GUI's buffers). ^/
public final class RegisterPictureInPictureRenderersEvent extends Event {
	public RegisterPictureInPictureRenderersEvent() {
	}

	public <T extends PictureInPictureRenderState> void register(Class<T> type, Function<MultiBufferSource.BufferSource, PictureInPictureRenderer<T>> factory) {
		net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry.register(context -> factory.apply(context.vertexConsumers()));
	}
}
*///?} else {
/** NeoForge's from 1.21.6 only. */
public final class RegisterPictureInPictureRenderersEvent {
	private RegisterPictureInPictureRenderersEvent() {
	}
}
//?}
