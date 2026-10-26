package com.vnap.fab.neoforge.event.brewing;

import com.vnap.fab.bus.api.Event;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The brewing stand's recipes of whole stacks (an input by its components, not only its item): Fabric's builder
 * matches items, so these are the shim's own, kept here and brewed by com.vnap.fab.mixin.PotionBrewingMixin.
 */
public final class RegisterBrewingRecipesEvent extends Event {
	public record Recipe(Ingredient input, Ingredient ingredient, ItemStack output) {
	}

	public static final List<Recipe> RECIPES = new CopyOnWriteArrayList<>();

	public RegisterBrewingRecipesEvent() {
	}

	public Builder getBuilder() {
		return new Builder();
	}

	/** The recipes of the brewing stand, as NeoForge's builder takes them. */
	public static final class Builder {
		public void addRecipe(Ingredient input, Ingredient ingredient, ItemStack output) {
			RECIPES.add(new Recipe(input, ingredient, output.copy()));
		}
	}
}
