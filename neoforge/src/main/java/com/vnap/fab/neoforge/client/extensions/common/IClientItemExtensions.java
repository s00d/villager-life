package com.vnap.fab.neoforge.client.extensions.common;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** An item's say in how it is held and drawn in hand (registered with RegisterClientExtensionsEvent; the shim's client mixins ask it). */
public interface IClientItemExtensions {
	IClientItemExtensions DEFAULT = new IClientItemExtensions() {
	};

	static IClientItemExtensions of(ItemStack stack) {
		return of(stack.getItem());
	}

	static IClientItemExtensions of(Item item) {
		IClientItemExtensions extensions = RegisterClientExtensionsEvent.ITEMS.get(item);
		return extensions != null ? extensions : DEFAULT;
	}

	/** The pose of the arm that holds it; null: vanilla's. */
	default HumanoidModel.@Nullable ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
		return null;
	}

	/** The first-person hand's transform; true: done, vanilla's is skipped. */
	default boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
			float partialTick, float equipProcess, float swingProcess) {
		return false;
	}
}
