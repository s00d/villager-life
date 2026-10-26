package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.ICancellableEvent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/** Before a living entity is drawn (com.vnap.fab.mixin.client.LivingRenderMixin); a cancel skips it. */
public final class RenderLivingEvent {
	private RenderLivingEvent() {
	}

	//? if >=1.21.2 {
	/*public static final class Pre<T extends LivingEntity, S extends net.minecraft.client.renderer.entity.state.LivingEntityRenderState, M extends EntityModel<? super S>>
			extends Event implements ICancellableEvent {
		private final S state;
		private final LivingEntityRenderer<T, S, M> renderer;

		public Pre(S state, LivingEntityRenderer<T, S, M> renderer) {
			this.state = state;
			this.renderer = renderer;
		}

		public S getRenderState() {
			return state;
		}

		public LivingEntityRenderer<T, S, M> getRenderer() {
			return renderer;
		}
	}
	*///?} else {
	public static final class Pre<T extends LivingEntity, M extends EntityModel<T>> extends Event implements ICancellableEvent {
		private final T entity;
		private final LivingEntityRenderer<T, M> renderer;

		public Pre(T entity, LivingEntityRenderer<T, M> renderer) {
			this.entity = entity;
			this.renderer = renderer;
		}

		public LivingEntity getEntity() {
			return entity;
		}

		public LivingEntityRenderer<T, M> getRenderer() {
			return renderer;
		}
	}
	//?}
}
