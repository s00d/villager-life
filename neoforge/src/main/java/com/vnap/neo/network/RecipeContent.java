package com.vnap.neo.network;

//? if >=1.21.2 {
/*import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/^* The recipes of the types a mod asked for, to a client (NeoForge's RecipeContentPayload): it fires RecipesReceivedEvent. ^/
public record RecipeContent(Set<RecipeType<?>> recipeTypes, List<RecipeHolder<?>> recipes) implements CustomPacketPayload {
	// a recipe as its data (Forge's network codec of a shaped recipe fails on an empty cell of its pattern: Ingredient.of())
	private static final StreamCodec<RegistryFriendlyByteBuf, RecipeHolder<?>> HOLDER = StreamCodec.composite(
			net.minecraft.resources.ResourceKey.streamCodec(Registries.RECIPE), RecipeHolder::id,
			ByteBufCodecs.fromCodecWithRegistries(RecipeLookup.CODEC), RecipeHolder::value,
			(id, recipe) -> new RecipeHolder<>(id, recipe));
	public static final Type<RecipeContent> TYPE = new Type<>(com.vnap.VillagerLife.id("recipe_content"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RecipeContent> CODEC = StreamCodec.composite(
			ByteBufCodecs.registry(Registries.RECIPE_TYPE).apply(ByteBufCodecs.collection(java.util.HashSet::new)), RecipeContent::recipeTypes,
			HOLDER.apply(ByteBufCodecs.list()), RecipeContent::recipes,
			RecipeContent::new);

	@Override
	public Type<RecipeContent> type() {
		return TYPE;
	}

	/^* The recipes as a client's map (from 26.3 made from the recipes' registry: a registry of these alone). ^/
	public static net.minecraft.world.item.crafting.RecipeMap map(List<RecipeHolder<?>> recipes) {
		return net.minecraft.world.item.crafting.RecipeMap.create(RecipeLookup.of(recipes));
	}

	public static void send(MinecraftServer server, Collection<ServerPlayer> players, Set<RecipeType<?>> types) {
		if (types.isEmpty() || players.isEmpty()) return;
		Set<RecipeType<?>> sent = Set.copyOf(types);
		List<RecipeHolder<?>> recipes = server.getRecipeManager().getRecipes().stream().filter(holder -> sent.contains(holder.value().getType())).toList();
		RecipeContent payload = new RecipeContent(sent, recipes);
		for (ServerPlayer player : players) com.vnap.neo.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
	}
}
*///?} else {
/** From 1.21.2 (before it a client has every recipe). */
public final class RecipeContent {
	private RecipeContent() {
	}
}
//?}
