package com.vnap.neo.network;

//? if >=26.3 {
/*import java.util.List;
import net.minecraft.world.item.crafting.RecipeHolder;

/^* A client's recipes from 26.3 are made from a registry of them: one of the recipes a server sent. ^/
final class RecipeLookup {
	static final com.mojang.serialization.Codec<net.minecraft.world.item.crafting.Recipe<?>> CODEC = net.minecraft.world.item.crafting.Recipe.DIRECT_CODEC;

	private RecipeLookup() {
	}

	static net.minecraft.core.HolderLookup<net.minecraft.world.item.crafting.Recipe<?>> of(List<RecipeHolder<?>> recipes) {
		net.minecraft.core.MappedRegistry<net.minecraft.world.item.crafting.Recipe<?>> registry =
				new net.minecraft.core.MappedRegistry<>(net.minecraft.core.registries.Registries.RECIPE, com.mojang.serialization.Lifecycle.stable());
		for (RecipeHolder<?> recipe : recipes) net.minecraft.core.Registry.register(registry, recipe.id(), recipe.value());
		return registry;
	}
}
*///?} else if >=1.21.2 {
/*import java.util.List;
import net.minecraft.world.item.crafting.RecipeHolder;

/^* Until 26.3 a client's recipes are the list itself. ^/
final class RecipeLookup {
	static final com.mojang.serialization.Codec<net.minecraft.world.item.crafting.Recipe<?>> CODEC = net.minecraft.world.item.crafting.Recipe.CODEC;

	private RecipeLookup() {
	}

	static List<RecipeHolder<?>> of(List<RecipeHolder<?>> recipes) {
		return recipes;
	}
}
*///?} else {
/** From 1.21.2. */
final class RecipeLookup {
	private RecipeLookup() {
	}
}
//?}
