package com.vnap.fab.mixin;

import com.vnap.fab.neoforge.common.extensions.IItemExtension;
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
		if ((Object) this instanceof Enchantment self) cir.setReturnValue(stack.getItem().supportsEnchantment(stack, IItemExtension.holder(self)));
	}

	@Inject(method = "isPrimaryItem", at = @At("HEAD"), cancellable = true)
	private void vnap$primary(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Enchantment self) cir.setReturnValue(stack.getItem().isPrimaryItemFor(stack, IItemExtension.holder(self)));
	}
}
