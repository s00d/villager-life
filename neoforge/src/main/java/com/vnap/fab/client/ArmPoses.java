package com.vnap.fab.client;

import com.vnap.fab.mixin.client.ArmPoseAccessor;
import java.util.Arrays;
import net.minecraft.client.model.HumanoidModel;

/** A mod's arm pose: one more constant of vanilla's enum (vanilla's switches leave it alone; its transformer moves the arm). */
public final class ArmPoses {
	private ArmPoses() {
	}

	//? if >=1.21.11 {
	/*public static synchronized HumanoidModel.ArmPose add(String name, boolean twoHanded, boolean affectsOffhandPose) {
		HumanoidModel.ArmPose[] values = ArmPoseAccessor.vnap$values();
		HumanoidModel.ArmPose pose = ArmPoseAccessor.vnap$make(name, values.length, twoHanded, affectsOffhandPose);
	*///?} else {
	public static synchronized HumanoidModel.ArmPose add(String name, boolean twoHanded) {
		HumanoidModel.ArmPose[] values = ArmPoseAccessor.vnap$values();
		HumanoidModel.ArmPose pose = ArmPoseAccessor.vnap$make(name, values.length, twoHanded);
	//?}
		HumanoidModel.ArmPose[] more = Arrays.copyOf(values, values.length + 1);
		more[values.length] = pose;
		ArmPoseAccessor.vnap$values(more);
		return pose;
	}
}
