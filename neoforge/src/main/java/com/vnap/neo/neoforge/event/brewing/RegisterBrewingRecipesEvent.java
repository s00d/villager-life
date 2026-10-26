package com.vnap.neo.neoforge.event.brewing;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public final class RegisterBrewingRecipesEvent extends Event {
	static {
		Bridge.bind(RegisterBrewingRecipesEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.brewing.BrewingRecipeRegisterEvent.class, priority,
				RegisterBrewingRecipesEvent::new, sink));
	}

	private final net.minecraftforge.event.brewing.BrewingRecipeRegisterEvent f;

	RegisterBrewingRecipesEvent(net.minecraftforge.event.brewing.BrewingRecipeRegisterEvent f) {
		super(f);
		this.f = f;
	}

	public Builder getBuilder() {
		return new Builder();
	}

	/** The recipes of the brewing stand, as NeoForge's builder takes them. */
	public final class Builder {
		public void addRecipe(Ingredient input, Ingredient ingredient, ItemStack output) {
			f.addRecipe(input, ingredient, output);
		}
	}
}
