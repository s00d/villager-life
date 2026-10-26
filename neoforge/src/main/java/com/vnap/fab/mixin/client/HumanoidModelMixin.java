package com.vnap.fab.mixin.client;

import com.vnap.fab.fml.common.asm.enumextension.EnumProxy;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mod's arm pose moves its arm once vanilla has posed the model (vanilla's switches pass over the pose). */
@SuppressWarnings("unused")
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	// from 1.21.2 the poses are the render state's
	//? if >=1.21.2 {
	/*@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void vnap$pose(net.minecraft.client.renderer.entity.state.HumanoidRenderState state, CallbackInfo ci) {
		var right = EnumProxy.ARM_POSES.get(state.rightArmPose);
		if (right != null) right.applyTransform((HumanoidModel<?>) (Object) this, state, HumanoidArm.RIGHT);
		var left = EnumProxy.ARM_POSES.get(state.leftArmPose);
		if (left != null) left.applyTransform((HumanoidModel<?>) (Object) this, state, HumanoidArm.LEFT);
	}
	*///?} else {
	@Shadow
	public HumanoidModel.ArmPose rightArmPose;
	@Shadow
	public HumanoidModel.ArmPose leftArmPose;

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void vnap$pose(net.minecraft.world.entity.LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch, CallbackInfo ci) {
		var right = EnumProxy.ARM_POSES.get(rightArmPose);
		if (right != null) right.applyTransform((HumanoidModel<?>) (Object) this, entity, HumanoidArm.RIGHT);
		var left = EnumProxy.ARM_POSES.get(leftArmPose);
		if (left != null) left.applyTransform((HumanoidModel<?>) (Object) this, entity, HumanoidArm.LEFT);
	}
	//?}
}
