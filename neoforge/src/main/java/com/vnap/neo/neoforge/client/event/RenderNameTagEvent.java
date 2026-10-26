package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

//? if >=1.21.2 {
/*/^* Name tags from 1.21.2: whether one is drawn is asked with the entity (com.vnap.neo.mixin.client.EntityRendererNameTagMixin). ^/
public final class RenderNameTagEvent {
	private RenderNameTagEvent() {
	}

	public static final class CanRender extends Event {
		static {
			Bridge.own(CanRender.class);
		}

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
/** Whether a name tag is drawn: Forge's answer is the event's result. */
public final class RenderNameTagEvent extends Event {
	static {
		Bridge.bind(RenderNameTagEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.client.event.RenderNameTagEvent.class, priority, RenderNameTagEvent::new, sink));
	}

	private final net.minecraftforge.client.event.RenderNameTagEvent f;

	RenderNameTagEvent(net.minecraftforge.client.event.RenderNameTagEvent f) {
		super(f);
		this.f = f;
	}

	public @Nullable Entity getEntity() {
		return f.getEntity();
	}

	public void setCanRender(TriState canRender) {
		f.setResult(switch (canRender) {
			case TRUE -> net.minecraftforge.eventbus.api.Event.Result.ALLOW;
			case FALSE -> net.minecraftforge.eventbus.api.Event.Result.DENY;
			case DEFAULT -> net.minecraftforge.eventbus.api.Event.Result.DEFAULT;
		});
	}
}
//?}
