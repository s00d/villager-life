package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.EntityTeleportEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.portal.DimensionTransition;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's EntityTeleportEvent.EnderPearl: a player about to land where the pearl did (cancelled: stays). */
@SuppressWarnings("unused")
@Mixin(ThrownEnderpearl.class)
public abstract class TeleportMixin {
	//? if >=1.21.2 {
	/*@WrapOperation(method = "onHit", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;"))
	private @Nullable ServerPlayer vnap$pearl(ServerPlayer player, net.minecraft.world.level.portal.TeleportTransition to, Operation<ServerPlayer> vanilla) {
		if (Bridge.fire(new EntityTeleportEvent.EnderPearl(player)).isCanceled()) return null;
		return vanilla.call(player, to);
	}
	*///?} else {
	@WrapOperation(method = "onHit", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;"))
	private @Nullable Entity vnap$pearl(Entity entity, DimensionTransition to, Operation<Entity> vanilla) {
		if (entity instanceof ServerPlayer player && Bridge.fire(new EntityTeleportEvent.EnderPearl(player)).isCanceled()) return null;
		return vanilla.call(entity, to);
	}
	//?}
}
