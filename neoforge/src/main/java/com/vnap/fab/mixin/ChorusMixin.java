package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.EntityTeleportEvent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's teleport of an eaten chorus fruit: each try of its jump (cancelled: that try fails). From 1.21.2 a consume
 * effect of any item, NeoForge's ItemConsumption.
 */
@SuppressWarnings("unused")
//? if >=1.21.2 {
/*@Mixin(net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect.class)
*///?} else
@Mixin(net.minecraft.world.item.ChorusFruitItem.class)
public abstract class ChorusMixin {
	//? if >=26.3 {
	/*@WrapOperation(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZLnet/minecraft/tags/TagKey;)Z"))
	private boolean vnap$chorus(LivingEntity entity, double x, double y, double z, boolean particles, net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> blocks,
			Operation<Boolean> vanilla) {
		if (Bridge.fire(new EntityTeleportEvent.ItemConsumption(entity)).isCanceled()) return false;
		return vanilla.call(entity, x, y, z, particles, blocks);
	}
	*///?} else if >=1.21.2 {
	/*@WrapOperation(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZ)Z"))
	private boolean vnap$chorus(LivingEntity entity, double x, double y, double z, boolean particles, Operation<Boolean> vanilla) {
		if (Bridge.fire(new EntityTeleportEvent.ItemConsumption(entity)).isCanceled()) return false;
		return vanilla.call(entity, x, y, z, particles);
	}
	*///?} else {
	@WrapOperation(method = "finishUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZ)Z"))
	private boolean vnap$chorus(LivingEntity entity, double x, double y, double z, boolean particles, Operation<Boolean> vanilla) {
		if (Bridge.fire(new EntityTeleportEvent.ChorusFruit(entity)).isCanceled()) return false;
		return vanilla.call(entity, x, y, z, particles);
	}
	//?}
}
