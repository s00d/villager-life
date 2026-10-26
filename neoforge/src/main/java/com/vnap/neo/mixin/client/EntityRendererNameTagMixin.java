package com.vnap.neo.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.2 {
/*import com.vnap.neo.Bridge;
import com.vnap.neo.neoforge.client.event.RenderNameTagEvent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * NeoForge's RenderNameTagEvent.CanRender where NeoForge decides about a name tag, with the entity: a listener's yes
 * or no wins, else the distance and vanilla's say; the tag is the event's content.
 */
@SuppressWarnings("unused")
@Mixin(EntityRenderer.class)
public abstract class EntityRendererNameTagMixin {
	//? if >=1.21.2 {
	/*@Unique
	private @Nullable EntityRenderState vnap$state;
	@Unique
	private RenderNameTagEvent.@Nullable CanRender vnap$tag;

	@Shadow
	protected abstract @Nullable Component getNameTag(Entity entity);

	@Shadow
	protected abstract boolean shouldShowName(Entity entity, double distance);
	*///?}

	// the name tag is worked out in a method of its own, with the type's name tag distance, from 26.2
	//? if >=26.2 {
	/*private static final String TAGS = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V";

	@Inject(method = TAGS, at = @At("HEAD"))
	private void vnap$state(Entity entity, EntityRenderState state, float partialTick, double nameTagDistance, double belowNameDistance, CallbackInfo ci) {
		vnap$state = state;
	}

	@Redirect(method = TAGS, at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;isNameplateInRenderDistance(Lnet/minecraft/world/entity/Entity;DD)Z", remap = false))
	private boolean vnap$near(Entity entity, double distance, double nameTagDistance) {
		EntityRenderState state = java.util.Objects.requireNonNull(vnap$state, "a render state being extracted");
		RenderNameTagEvent.CanRender tag = Bridge.fire(new RenderNameTagEvent.CanRender(entity, state, getNameTag(entity)));
		vnap$tag = tag;
		if (tag.canRender() == net.neoforged.neoforge.common.util.TriState.TRUE) return true;
		if (tag.canRender() == net.neoforged.neoforge.common.util.TriState.FALSE) return false;
		return net.minecraftforge.client.ForgeHooksClient.isNameplateInRenderDistance(entity, distance, nameTagDistance);
	}
	*///?} else if >=1.21.2 {
	/*private static final String TAGS = "extractRenderState";

	@Inject(method = TAGS, at = @At("HEAD"))
	private void vnap$state(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		vnap$state = state;
	}

	@Redirect(method = TAGS, at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;isNameplateInRenderDistance(Lnet/minecraft/world/entity/Entity;D)Z", remap = false))
	private boolean vnap$near(Entity entity, double distance) {
		EntityRenderState state = java.util.Objects.requireNonNull(vnap$state, "a render state being extracted");
		RenderNameTagEvent.CanRender tag = Bridge.fire(new RenderNameTagEvent.CanRender(entity, state, getNameTag(entity)));
		vnap$tag = tag;
		if (tag.canRender() == net.neoforged.neoforge.common.util.TriState.TRUE) return true;
		if (tag.canRender() == net.neoforged.neoforge.common.util.TriState.FALSE) return false;
		return net.minecraftforge.client.ForgeHooksClient.isNameplateInRenderDistance(entity, distance);
	}
	*///?}

	//? if >=1.21.2 {
	/*@Redirect(method = TAGS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z"))
	private boolean vnap$shown(EntityRenderer<?, ?> renderer, Entity entity, double distance) {
		var tag = vnap$tag;
		return (tag != null && tag.canRender() == net.neoforged.neoforge.common.util.TriState.TRUE) || shouldShowName(entity, distance);
	}

	@Redirect(method = TAGS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;"))
	private @Nullable Component vnap$content(EntityRenderer<?, ?> renderer, Entity entity) {
		var tag = vnap$tag;
		vnap$tag = null;
		return tag != null ? tag.getContent() : getNameTag(entity);
	}
	*///?}
}
