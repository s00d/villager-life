package com.vnap.fab.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's RenderNameTagEvent where vanilla decides about a name tag: a listener's yes or no wins, else vanilla's. */
@SuppressWarnings("unused")
@Mixin(EntityRenderer.class)
public abstract class NameTagMixin {
	// from 26.2 the name tag is worked out in a method of its own
	//? if >=26.2 {
	/*@org.spongepowered.asm.mixin.Shadow
	protected abstract net.minecraft.network.chat.@org.jspecify.annotations.Nullable Component getNameTag(Entity entity);

	@WrapOperation(method = "extractNameTags", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z"))
	private boolean vnap$shown(EntityRenderer<Entity, net.minecraft.client.renderer.entity.state.EntityRenderState> renderer, Entity entity, double distance,
			Operation<Boolean> vanilla, Entity extracted, net.minecraft.client.renderer.entity.state.EntityRenderState state, float partialTick, double tagDistance,
			double belowDistance) {
		TriState says = Bridge.fire(new RenderNameTagEvent.CanRender(entity, state, getNameTag(entity))).canRender();
		if (says == TriState.TRUE) return true;
		if (says == TriState.FALSE) return false;
		return vanilla.call(renderer, entity, distance);
	}
	*///?} else if >=1.21.2 {
	/*@org.spongepowered.asm.mixin.Shadow
	protected abstract net.minecraft.network.chat.@org.jspecify.annotations.Nullable Component getNameTag(Entity entity);

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z"))
	private boolean vnap$shown(EntityRenderer<Entity, net.minecraft.client.renderer.entity.state.EntityRenderState> renderer, Entity entity, double distance,
			Operation<Boolean> vanilla, Entity extracted, net.minecraft.client.renderer.entity.state.EntityRenderState state, float partialTick) {
		TriState says = Bridge.fire(new RenderNameTagEvent.CanRender(entity, state, getNameTag(entity))).canRender();
		if (says == TriState.TRUE) return true;
		if (says == TriState.FALSE) return false;
		return vanilla.call(renderer, entity, distance);
	}
	*///?} else {
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldShowName(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean vnap$shown(EntityRenderer<Entity> renderer, Entity entity, Operation<Boolean> vanilla) {
		TriState says = Bridge.fire(new RenderNameTagEvent(entity)).canRender();
		if (says == TriState.TRUE) return true;
		if (says == TriState.FALSE) return false;
		return vanilla.call(renderer, entity);
	}
	//?}
}
