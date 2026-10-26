package com.vnap.fab.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's ItemTossEvent as a player's dropped item is about to enter the level; a cancel keeps it out. */
@SuppressWarnings("unused")
//? if >=1.21.5 {
/*@Mixin(net.minecraft.world.entity.LivingEntity.class)
*///?} else
@Mixin(ServerPlayer.class)
public abstract class ItemTossMixin {
	// from 1.21.5 a living entity's drop spawns it (a player's calls it first)
	// from 26.3 with a prediction of the drop instead of the thrower's trace
	//? if >=26.3 {
	/*@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"), cancellable = true)
	private void vnap$toss(ItemStack stack, boolean dropAround, net.minecraft.util.Prediction prediction, CallbackInfoReturnable<ItemEntity> ci,
			@com.llamalad7.mixinextras.sugar.Local ItemEntity item) {
	*///?} else {
	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"), cancellable = true)
	private void vnap$toss(ItemStack stack, boolean dropAround, boolean traceItem, CallbackInfoReturnable<ItemEntity> ci, @com.llamalad7.mixinextras.sugar.Local ItemEntity item) {
	//?}
		if ((Object) this instanceof ServerPlayer player
				&& com.vnap.fab.Bridge.fire(new com.vnap.fab.neoforge.event.entity.item.ItemTossEvent(item, player)).isCanceled()) ci.setReturnValue(null);
	}
}
