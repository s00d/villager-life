package com.vnap.fab.client;

//? if >=1.21.2 {
/*import net.minecraft.util.context.ContextKey;
import org.jspecify.annotations.Nullable;

/^* What NeoForge lets a render state carry (setRenderData/getRenderData): com.vnap.fab.mixin.client.EntityRenderStateMixin gives it to every entity render state. ^/
public interface RenderData {
	<T> void vnap$setRenderData(ContextKey<T> key, @Nullable T value);

	<T> @Nullable T vnap$getRenderData(ContextKey<T> key);

	void vnap$resetRenderData();

	/^* The partial tick the state was extracted at (NeoForge's field of the state). ^/
	float vnap$partialTick();

	void vnap$partialTick(float partialTick);
}
*///?} else {
/** A render state's data (from 1.21.2; before it a renderer has the entity itself). */
public interface RenderData {
}
//?}
