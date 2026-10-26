package com.vnap.fab.mixin.client;

import com.vnap.fab.neoforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The item in a player's hand picks the arm's pose when it has one of its own (its client extensions). */
@SuppressWarnings("unused")
//? if >=1.21.9 {
/*@Mixin(net.minecraft.client.renderer.entity.player.AvatarRenderer.class)
*///?} else
@Mixin(net.minecraft.client.renderer.entity.player.PlayerRenderer.class)
public abstract class PlayerArmPoseMixin {
	//? if >=1.21.9 {
	/*@Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
			at = @At("HEAD"), cancellable = true)
	private static void vnap$pose(net.minecraft.world.entity.Avatar player, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> ci) {
		vnap$own(player, stack, hand, ci);
	}
	*///?} else if >=1.21.2 {
	/*@Inject(method = "getArmPose(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
			at = @At("HEAD"), cancellable = true)
	private static void vnap$pose(net.minecraft.world.entity.player.Player player, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> ci) {
		vnap$own(player, stack, hand, ci);
	}
	*///?} else {
	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private static void vnap$pose(net.minecraft.client.player.AbstractClientPlayer player, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> ci) {
		vnap$own(player, player.getItemInHand(hand), hand, ci);
	}
	//?}

	private static void vnap$own(net.minecraft.world.entity.LivingEntity player, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> ci) {
		if (stack.isEmpty()) return;
		HumanoidModel.ArmPose pose = IClientItemExtensions.of(stack).getArmPose(player, hand, stack);
		if (pose != null) ci.setReturnValue(pose);
	}
}
