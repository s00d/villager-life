package com.vnap.fab.neoforge.client.event;

//? if >=1.21.2 {
/*import com.vnap.fab.Bridge;
import com.vnap.fab.bus.api.Event;
import java.util.Set;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

/^* The recipes the server sent this client (com.vnap.fab.network.RecipeContent). ^/
public final class RecipesReceivedEvent extends Event {

	private final Set<RecipeType<?>> recipeTypes;
	private final RecipeMap recipeMap;

	public RecipesReceivedEvent(Set<RecipeType<?>> recipeTypes, RecipeMap recipeMap) {
		this.recipeTypes = recipeTypes;
		this.recipeMap = recipeMap;
	}

	public Set<RecipeType<?>> getRecipeTypes() {
		return recipeTypes;
	}

	public RecipeMap getRecipeMap() {
		return recipeMap;
	}
}
*///?} else {
/** From 1.21.2 (before it a client has every recipe). */
public final class RecipesReceivedEvent {
	private RecipesReceivedEvent() {
	}
}
//?}
