package com.vnap.fab.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's armour texture of a stack (IItemExtension.getArmorTexture) before its material's; until 1.21.2, where equipment assets name it. */
@SuppressWarnings("unused")
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorTextureMixin {
	@WrapOperation(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ArmorMaterial$Layer;texture(Z)Lnet/minecraft/resources/ResourceLocation;"))
	private ResourceLocation vnap$texture(ArmorMaterial.Layer layer, boolean inner, Operation<ResourceLocation> vanilla,
			PoseStack pose, MultiBufferSource buffers, LivingEntity entity, EquipmentSlot slot) {
		ItemStack stack = entity.getItemBySlot(slot);
		ResourceLocation own = stack.getItem().getArmorTexture(stack, entity, slot, layer, inner);
		return own != null ? own : vanilla.call(layer, inner);
	}
}
