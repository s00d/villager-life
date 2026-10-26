package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.bus.api.ICancellableEvent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

public final class RenderLivingEvent {
	private RenderLivingEvent() {
	}

	//? if >=1.21.2 {
	/*/^* Before a living entity's render state is drawn (Forge's event, its state NeoForge's render state). ^/
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final class Pre<T extends LivingEntity, S extends net.minecraft.client.renderer.entity.state.LivingEntityRenderState, M extends EntityModel<? super S>>
			extends Event implements ICancellableEvent {
		static {
			Bridge.bind((Class) Pre.class, (priority, sink) -> Bridge.game(net.minecraftforge.client.event.RenderLivingEvent.Pre.class, priority,
					(net.minecraftforge.client.event.RenderLivingEvent.Pre f) -> new Pre(f), sink));
		}

		private final net.minecraftforge.client.event.RenderLivingEvent.Pre<T, S, M> f;

		Pre(net.minecraftforge.client.event.RenderLivingEvent.Pre<T, S, M> f) {
			super(f);
			this.f = f;
		}

		public S getRenderState() {
			return f.getState();
		}

		public LivingEntityRenderer<T, S, M> getRenderer() {
			return f.getRenderer();
		}
	}
	*///?} else {
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final class Pre<T extends LivingEntity, M extends EntityModel<T>> extends Event implements ICancellableEvent {
		static {
			Bridge.bind((Class) Pre.class, (priority, sink) -> Bridge.game(net.minecraftforge.client.event.RenderLivingEvent.Pre.class, priority,
					(net.minecraftforge.client.event.RenderLivingEvent.Pre f) -> new Pre(f), sink));
		}

		private final net.minecraftforge.client.event.RenderLivingEvent.Pre<T, M> f;

		Pre(net.minecraftforge.client.event.RenderLivingEvent.Pre<T, M> f) {
			super(f);
			this.f = f;
		}

		public LivingEntity getEntity() {
			return f.getEntity();
		}

		public LivingEntityRenderer<T, M> getRenderer() {
			return f.getRenderer();
		}
	}
	//?}
}
