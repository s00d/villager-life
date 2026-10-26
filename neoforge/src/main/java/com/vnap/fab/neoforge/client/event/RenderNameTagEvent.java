package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.bus.api.Event;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

//? if >=1.21.2 {
/*/^* Name tags from 1.21.2: whether one is drawn is asked with the entity (com.vnap.fab.mixin.client.NameTagMixin). ^/
public final class RenderNameTagEvent {
	private RenderNameTagEvent() {
	}

	public static final class CanRender extends Event {

		private final Entity entity;
		private final net.minecraft.client.renderer.entity.state.EntityRenderState state;
		private final net.minecraft.network.chat.@Nullable Component originalContent;
		private net.minecraft.network.chat.@Nullable Component content;
		private TriState canRender = TriState.DEFAULT;

		public CanRender(Entity entity, net.minecraft.client.renderer.entity.state.EntityRenderState state, net.minecraft.network.chat.@Nullable Component content) {
			this.entity = entity;
			this.state = state;
			this.originalContent = content;
			this.content = content;
		}

		public Entity getEntity() {
			return entity;
		}

		public net.minecraft.client.renderer.entity.state.EntityRenderState getState() {
			return state;
		}

		public net.minecraft.network.chat.@Nullable Component getOriginalContent() {
			return originalContent;
		}

		public net.minecraft.network.chat.@Nullable Component getContent() {
			return content;
		}

		public void setContent(net.minecraft.network.chat.@Nullable Component content) {
			this.content = content;
		}

		public void setCanRender(TriState canRender) {
			this.canRender = canRender;
		}

		public TriState canRender() {
			return canRender;
		}
	}
}
*///?} else {
/** Whether a name tag is drawn (com.vnap.fab.mixin.client.NameTagMixin): a listener's yes or no wins over vanilla's. */
public final class RenderNameTagEvent extends Event {
	private final @Nullable Entity entity;
	private TriState canRender = TriState.DEFAULT;

	public RenderNameTagEvent(@Nullable Entity entity) {
		this.entity = entity;
	}

	public @Nullable Entity getEntity() {
		return entity;
	}

	public void setCanRender(TriState canRender) {
		this.canRender = canRender;
	}

	public TriState canRender() {
		return canRender;
	}
}
//?}
