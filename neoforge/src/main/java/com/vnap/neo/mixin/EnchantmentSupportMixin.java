package com.vnap.neo.mixin;

import com.vnap.neo.neoforge.common.extensions.IItemExtension;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Where vanilla asks an enchantment whether it fits an item (table, anvil, books, commands), NeoForge asks the item. */
@SuppressWarnings("unused")
@Mixin(Enchantment.class)
public abstract class EnchantmentSupportMixin {
	@Inject(method = {"canEnchant", "isSupportedItem"}, at = @At("HEAD"), cancellable = true)
	private void vnap$supports(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.getItem() instanceof IItemExtension item && (Object) this instanceof Enchantment self) cir.setReturnValue(item.supportsEnchantment(stack, IItemExtension.holder(self)));
	}

	@Inject(method = "isPrimaryItem", at = @At("HEAD"), cancellable = true)
	private void vnap$primary(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.getItem() instanceof IItemExtension item && (Object) this instanceof Enchantment self) cir.setReturnValue(item.isPrimaryItemFor(stack, IItemExtension.holder(self)));
	}
}
