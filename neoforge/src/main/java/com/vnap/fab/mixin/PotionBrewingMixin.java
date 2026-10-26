package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.vnap.fab.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The shim's brewing recipes of whole stacks (RegisterBrewingRecipesEvent) beside vanilla's mixes. */
@SuppressWarnings("unused")
@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin {
	@ModifyReturnValue(method = "isIngredient", at = @At("RETURN"))
	private boolean vnap$ingredient(boolean vanilla, ItemStack stack) {
		return vanilla || RegisterBrewingRecipesEvent.RECIPES.stream().anyMatch(recipe -> recipe.ingredient().test(stack));
	}

	@Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
	private void vnap$has(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> info) {
		if (vnap$recipe(input, ingredient) != null) info.setReturnValue(true);
	}

	@Inject(method = "mix", at = @At("HEAD"), cancellable = true)
	private void vnap$mix(ItemStack ingredient, ItemStack input, CallbackInfoReturnable<ItemStack> info) {
		var recipe = vnap$recipe(input, ingredient);
		if (recipe != null) info.setReturnValue(recipe.output().copy());
	}

	private static RegisterBrewingRecipesEvent.@org.jspecify.annotations.Nullable Recipe vnap$recipe(ItemStack input, ItemStack ingredient) {
		if (input.isEmpty() || ingredient.isEmpty()) return null;
		for (var recipe : RegisterBrewingRecipesEvent.RECIPES) {
			if (recipe.input().test(input) && recipe.ingredient().test(ingredient)) return recipe;
		}
		return null;
	}
}
