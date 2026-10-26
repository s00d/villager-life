package com.vnap.fab.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.2 {
/*import com.vnap.fab.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/** NeoForge's render state modifiers, applied where NeoForge applies them: once a state is extracted. */
@SuppressWarnings("unused")
@Mixin(EntityRenderer.class)
public abstract class EntityRendererStateMixin {
	//? if >=1.21.2 {
	/*@Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
	private void vnap$modify(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
		EntityRenderState state = cir.getReturnValue();
		if (state instanceof com.vnap.fab.client.RenderData data) data.vnap$partialTick(partialTick);
		if ((Object) this instanceof EntityRenderer<?, ?> renderer) RegisterRenderStateModifiersEvent.apply(renderer, entity, state);
	}
	*///?}
}
