package com.vnap.fab.neoforge.common.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** An item with components: Fabric API's components ingredient (the item, whose components hold the stack's). */
public final class DataComponentIngredient {
	private DataComponentIngredient() {
	}

	/** Only {@code strict} false: Fabric matches components the stack holds, not exactly them (the mod asks for no other). */
	public static Ingredient of(boolean strict, ItemStack stack) {
		if (strict) throw new UnsupportedOperationException("a strict components ingredient on Fabric");
		return net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients.components(Ingredient.of(stack.getItem()), stack.getComponentsPatch());
	}
}
