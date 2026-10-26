package com.vnap.fab.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.vnap.fab.neoforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The first-person hand of an item with a transform of its own (its client extensions): that one instead of vanilla's. */
@SuppressWarnings("unused")
//? if >=26.3 {
/*@Mixin(net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.class)
*///?} else
@Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
public abstract class HandTransformMixin {
	// from 26.3 the hands are submitted from their render state (the hand drawn in first person is the local player's)
	//? if >=26.3 {
	/*@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"))
	private void vnap$hand(net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer self, PoseStack pose, HumanoidArm arm, float equip, Operation<Void> vanilla,
			@Local(argsOnly = true, ordinal = 0) float partialTick, @Local(argsOnly = true, ordinal = 2) float swing, @Local(argsOnly = true) ItemStack stack) {
		LocalPlayer local = net.minecraft.client.Minecraft.getInstance().player;
		if (local != null && IClientItemExtensions.of(stack).applyForgeHandTransform(pose, local, arm, stack, partialTick, equip, swing)) return;
		vanilla.call(self, pose, arm, equip);
	}
	*///?} else {
	// submitted from 26.2
	//? if >=26.2 {
	/*@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"))
	*///?} else
	@WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"))
	private void vnap$hand(net.minecraft.client.renderer.ItemInHandRenderer self, PoseStack pose, HumanoidArm arm, float equip, Operation<Void> vanilla,
			@Local(argsOnly = true) AbstractClientPlayer player, @Local(argsOnly = true, ordinal = 0) float partialTick,
			@Local(argsOnly = true, ordinal = 2) float swing, @Local(argsOnly = true) ItemStack stack) {
		if (player instanceof LocalPlayer local && IClientItemExtensions.of(stack).applyForgeHandTransform(pose, local, arm, stack, partialTick, equip, swing)) return;
		vanilla.call(self, pose, arm, equip);
	}
	//?}
}
