package com.vnap.neo.neoforge.client.event;

//? if >=1.21.2 {
/*import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import java.util.Set;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

/^* The recipes the server sent this client (com.vnap.neo.network.RecipeContent). ^/
public final class RecipesReceivedEvent extends Event {
	static {
		Bridge.own(RecipesReceivedEvent.class);
	}

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
