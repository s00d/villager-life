package com.vnap.fab.neoforge.client.renderstate;

//? if >=1.21.2 {
/*import com.google.common.reflect.TypeToken;
import com.vnap.fab.bus.api.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/^* Modifiers of entity render states (with the client's setup: com.vnap.fab.FabricClientEntry); a renderer and its subclasses get theirs (com.vnap.fab.mixin.client.EntityRendererStateMixin). ^/
public final class RegisterRenderStateModifiersEvent extends Event {
	private record Modifier(Class<?> renderer, BiConsumer<?, ?> modify) {
	}

	private static final List<Modifier> ALL = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static final Map<Class<?>, List<BiConsumer<?, ?>>> BY_RENDERER = new ConcurrentHashMap<>();

	public RegisterRenderStateModifiersEvent() {
	}

	public <E extends Entity, S extends EntityRenderState> void registerEntityModifier(TypeToken<? extends EntityRenderer<? extends E, ? extends S>> renderer, BiConsumer<E, S> modifier) {
		ALL.add(new Modifier(renderer.getRawType(), modifier));
		BY_RENDERER.clear();
	}

	public <E extends Entity, S extends EntityRenderState> void registerEntityModifier(Class<? extends EntityRenderer<? extends E, ? extends S>> renderer, BiConsumer<E, S> modifier) {
		ALL.add(new Modifier(renderer, modifier));
		BY_RENDERER.clear();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void apply(EntityRenderer<?, ?> renderer, Entity entity, EntityRenderState state) {
		if (state instanceof com.vnap.fab.client.RenderData data) data.vnap$resetRenderData();
		List<BiConsumer<?, ?>> modifiers = BY_RENDERER.computeIfAbsent(renderer.getClass(), type -> {
			List<BiConsumer<?, ?>> found = new ArrayList<>();
			for (Modifier modifier : ALL) if (modifier.renderer().isAssignableFrom(type)) found.add(modifier.modify());
			return found;
		});
		for (BiConsumer modifier : modifiers) modifier.accept(entity, state);
	}
}
*///?} else {
/** From 1.21.2 (render states). */
public final class RegisterRenderStateModifiersEvent {
	private RegisterRenderStateModifiersEvent() {
	}
}
//?}
