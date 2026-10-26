package com.vnap.neo.mixin;

import com.vnap.neo.neoforge.event.ItemAttributeModifierEvent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NeoForge's ItemAttributeModifierEvent: a stack's modifiers wherever the game reads them (its slots, its tooltip). */
@SuppressWarnings("unused")
@Mixin(ItemStack.class)
public abstract class ItemStackAttributesMixin {
	@Redirect(method = {"forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Ljava/util/function/BiConsumer;)V",
			"forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V", "addAttributeTooltips"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getOrDefault(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;"))
	private Object vnap$modifiers(ItemStack stack, DataComponentType<?> type, Object fallback) {
		Object value = stack.getOrDefault(cast(type), fallback);
		if (!(value instanceof ItemAttributeModifiers modifiers)) return value;
		// none of its own: the item says (NeoForge's order), then the event
		//? if >=1.21.2 {
		/*if (modifiers.modifiers().isEmpty() && stack.getItem() instanceof com.vnap.neo.neoforge.common.extensions.IItemExtension item) {
			modifiers = item.getDefaultAttributeModifiers(stack);
		}
		*///?} else {
		if (modifiers.modifiers().isEmpty()) modifiers = stack.getItem().getDefaultAttributeModifiers(stack);
		//?}
		return ItemAttributeModifierEvent.of(stack, modifiers);
	}

	@SuppressWarnings("unchecked")
	private static DataComponentType<Object> cast(DataComponentType<?> type) {
		return (DataComponentType<Object>) type;
	}
}
