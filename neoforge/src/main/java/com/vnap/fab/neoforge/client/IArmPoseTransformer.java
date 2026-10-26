package com.vnap.fab.neoforge.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;

/** How an arm pose of a mod moves the arm (com.vnap.fab.mixin.client.HumanoidModelMixin applies it). */
@FunctionalInterface
public interface IArmPoseTransformer {
	//? if >=1.21.2 {
	/*void applyTransform(HumanoidModel<?> model, net.minecraft.client.renderer.entity.state.HumanoidRenderState state, HumanoidArm arm);
	*///?} else
	void applyTransform(HumanoidModel<?> model, net.minecraft.world.entity.LivingEntity entity, HumanoidArm arm);
}
