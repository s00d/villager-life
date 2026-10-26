package com.vnap.fab.mixin.client;

//? if >=1.21.2 {
/*import com.vnap.fab.client.RenderData;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.context.ContextKey;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/^* NeoForge's render data on every entity render state. ^/
@SuppressWarnings("unused")
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements RenderData {
	@Unique
	private final Map<ContextKey<?>, Object> vnap$data = new HashMap<>();

	@Override
	public <T> void vnap$setRenderData(ContextKey<T> key, @Nullable T value) {
		if (value == null) vnap$data.remove(key);
		else vnap$data.put(key, value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> @Nullable T vnap$getRenderData(ContextKey<T> key) {
		return (T) vnap$data.get(key);
	}

	@Unique
	private float vnap$partialTick;

	@Override
	public void vnap$resetRenderData() {
		vnap$data.clear();
	}

	@Override
	public float vnap$partialTick() {
		return vnap$partialTick;
	}

	@Override
	public void vnap$partialTick(float partialTick) {
		vnap$partialTick = partialTick;
	}
}
*///?} else {
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** Nothing to carry before 1.21.2 (a renderer has the entity itself). */
@Mixin(EntityRenderer.class)
public abstract class EntityRenderStateMixin {
}
//?}
