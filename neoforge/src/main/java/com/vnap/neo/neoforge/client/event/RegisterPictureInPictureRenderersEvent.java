package com.vnap.neo.neoforge.client.event;

//? if >=26.2 {
/*import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import java.util.function.Supplier;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;

/^* The mod's renderers of pictures in the GUI: Forge's RegisterPictureInPictureRendererEvent (from 26.2 a renderer is made from nothing: it submits to the frame's list). ^/
public final class RegisterPictureInPictureRenderersEvent extends Event {
	static {
		Bridge.bind(RegisterPictureInPictureRenderersEvent.class, (priority, sink) -> Bridge.game(
				net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent.class, priority, RegisterPictureInPictureRenderersEvent::new, sink));
	}

	private final net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent f;

	RegisterPictureInPictureRenderersEvent(net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent f) {
		super(f);
		this.f = f;
	}

	public <T extends PictureInPictureRenderState> void register(Class<T> type, Supplier<PictureInPictureRenderer<T>> factory) {
		f.register(factory.get());
	}
}
*///?} else if >=1.21.6 {
/*import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import java.util.function.Function;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;

/^* The mod's renderers of pictures in the GUI: Forge's RegisterPictureInPictureRendererEvent (its renderer made here). ^/
public final class RegisterPictureInPictureRenderersEvent extends Event {
	static {
		Bridge.bind(RegisterPictureInPictureRenderersEvent.class, (priority, sink) -> Bridge.game(
				net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent.class, priority, RegisterPictureInPictureRenderersEvent::new, sink));
	}

	private final net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent f;

	RegisterPictureInPictureRenderersEvent(net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent f) {
		super(f);
		this.f = f;
	}

	public <T extends PictureInPictureRenderState> void register(Class<T> type, Function<MultiBufferSource.BufferSource, PictureInPictureRenderer<T>> factory) {
		f.register(factory.apply(f.getBufferSource()));
	}
}
*///?} else {
/** NeoForge's from 1.21.6 only. */
public final class RegisterPictureInPictureRenderersEvent {
	private RegisterPictureInPictureRenderersEvent() {
	}
}
//?}
