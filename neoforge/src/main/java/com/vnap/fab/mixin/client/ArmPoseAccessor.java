package com.vnap.fab.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The arm pose enum's constructor and its table of constants: a mod's pose is one more constant (com.vnap.fab.client.ArmPoses). */
@SuppressWarnings("DoNotCallSuggester")
@Mixin(HumanoidModel.ArmPose.class)
public interface ArmPoseAccessor {
	//? if >=1.21.11 {
	/*@Invoker("<init>")
	static HumanoidModel.ArmPose vnap$make(String name, int ordinal, boolean twoHanded, boolean affectsOffhandPose) {
		throw new AssertionError();
	}
	*///?} else {
	@Invoker("<init>")
	static HumanoidModel.ArmPose vnap$make(String name, int ordinal, boolean twoHanded) {
		throw new AssertionError();
	}
	//?}

	@Accessor("$VALUES")
	static HumanoidModel.ArmPose[] vnap$values() {
		throw new AssertionError();
	}

	@Mutable
	@Accessor("$VALUES")
	static void vnap$values(HumanoidModel.ArmPose[] values) {
		throw new AssertionError();
	}
}
