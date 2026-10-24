package com.vnap.village.market;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.block.DecorBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What things are worth in copper (economy/prices.json): items, then tags, then rules (food by its nutrition, tools
 * and armour by durability left, enchantments on top, the mod's decor). Zero: the stall does not deal in it.
 * Villagers pay the worth; a player pays more and is paid less, by the village's mood, so buying from a player and
 * selling back never pays.
 */
public final class Prices {
	private static final String PATH = "/data/villager_life/economy/prices.json";
	private static final Map<ResourceLocation, Double> ITEMS = new LinkedHashMap<>();
	private static final Map<TagKey<Item>, Double> TAGS = new LinkedHashMap<>();
	private static boolean loaded;
	/** Worth taken from the server's recipes (learn): what an item's ingredients cost, and a little for the work. */
	private static final Map<Item, Double> MADE = new java.util.HashMap<>();
	/** The server's own "never" (config/villager-life-prices.json). */
	private static final java.util.Set<String> BANNED = new java.util.HashSet<>();
	/** The server owner's prices: items, tags and "never", over the mod's own list (any mod's things set right by hand). */
	private static final String CONFIG = "villager-life-prices.json";
	/** A tenth over the ingredients: the hands that made it. */
	private static final double WORK = 1.1;
	/** Things nobody makes or sells: never in a stall and never ordered (spawners, eggs, the dragon's, the Spire's own). */
	private static final java.util.Set<String> NEVER = java.util.Set.of("minecraft:bedrock", "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault",
			"minecraft:budding_amethyst", "minecraft:reinforced_deepslate", "minecraft:end_portal_frame", "minecraft:dragon_egg", "minecraft:dragon_head",
			"minecraft:player_head", "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
			"minecraft:command_block_minecart", "minecraft:structure_block", "minecraft:structure_void", "minecraft:jigsaw", "minecraft:barrier",
			"minecraft:light", "minecraft:debug_stick", "minecraft:knowledge_book", "minecraft:frogspawn", "minecraft:farmland", "minecraft:dirt_path",
			"minecraft:petrified_oak_slab", "minecraft:infested_stone", "minecraft:test_block", "minecraft:test_instance_block");
	/** The stall pays a player this share of the worth (at a mood of 0) and asks this much of him. */
	static final double SELL_SHARE = 0.5;
	static final double BUY_SHARE = 1.0;

	private Prices() {
	}

	/** Worth of one item of this stack, in copper (fractions for cheap things: a wheat is a fifth). */
	public static double worth(ItemStack stack) {
		if (stack.isEmpty() || Coins.isCoin(stack)) return 0.0;
		load();
		Item item = stack.getItem();
		Double listed = ITEMS.get(BuiltInRegistries.ITEM.getKey(item));
		if (listed == null) {
			for (Map.Entry<TagKey<Item>, Double> tag : TAGS.entrySet()) {
				if (stack.is(tag.getKey())) {
					listed = tag.getValue();
					break;
				}
			}
		}
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		if (BANNED.contains(id)) return 0.0;
		if (NEVER.contains(id) || id.endsWith("_spawn_egg") || id.startsWith("minecraft:infested_")) return 0.0;
		if (listed == null) listed = MADE.get(item);
		double worth = listed != null ? listed : rule(stack);
		if (worth <= 0.0) return 0.0;
		int max = stack.getOrDefault(DataComponents.MAX_DAMAGE, 0);
		if (max > 0 && stack.isDamaged()) worth *= Math.max(0.1, 1.0 - (double) stack.getDamageValue() / max);
		ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		for (var entry : enchantments.entrySet()) worth += 4.0 * entry.getIntValue();
		// a book of enchantments keeps them aside (they go onto a tool at an anvil): worth the same
		ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		for (var entry : stored.entrySet()) worth += 4.0 * entry.getIntValue();
		return worth;
	}

	private static double rule(ItemStack stack) {
		// another mod's things go by their recipes, the common tags and the server's own list only: a guess from
		// "a block" or "its durability" would sell a mod's machine for a fifth of a copper (only its food is safe to guess)
		String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
		if (!namespace.equals("minecraft") && !namespace.equals(com.vnap.VillagerLife.MOD_ID)) {
			var modFood = stack.get(DataComponents.FOOD);
			return modFood == null ? 0.0 : Math.max(0.2, modFood.nutrition() * 0.3);
		}
		// a record, whatever tag its kind is in this version (1.21 has none of "music_discs" any more)
		if (stack.has(DataComponents.JUKEBOX_PLAYABLE)) return 20.0;
		if (stack.is(net.minecraft.world.item.Items.POTION)) return 6.0;
		if (stack.is(net.minecraft.world.item.Items.SPLASH_POTION)) return 8.0;
		if (stack.is(net.minecraft.world.item.Items.LINGERING_POTION)) return 14.0;
		if (stack.is(net.minecraft.world.item.Items.TIPPED_ARROW)) return 2.0;
		if (stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) return 2.0;
		var food = stack.get(DataComponents.FOOD);
		if (food != null) return Math.max(0.2, food.nutrition() * 0.3);
		int max = stack.getOrDefault(DataComponents.MAX_DAMAGE, 0);
		if (max > 0) return 2.0 + max / 50.0;
		if (!(stack.getItem() instanceof BlockItem block)) return 0.0;
		if (block.getBlock() instanceof DecorBlock) return 10.0;
		// bedrock, command blocks, barriers: nobody makes or sells those
		if (block.getBlock().defaultDestroyTime() < 0.0F) return 0.0;
		// a block that keeps something in it (a furnace, a hopper, a shulker box) is worth what makes it: its recipe
		// (learn) or the list; with neither it is not dealt in, never "a building block" at a fifth of a copper
		// (the mod's own rite and town blocks are the town's goods, built from the stall: they keep the rules below)
		if (block.getBlock() instanceof net.minecraft.world.level.block.EntityBlock
				&& !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(com.vnap.VillagerLife.MOD_ID)) return 0.0;
		// a wooden part costs its tree's logs (the stall makes it from them)
		Materials.Wood wood = Materials.wood(stack.getItem());
		if (wood != null && !wood.log().equals(stack.getItem())) return wood.logs() * worth(new ItemStack(wood.log()));
		// a storage block is nine of what it stores
		var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (id.getPath().endsWith("_block")) {
			String base = id.getPath().substring(0, id.getPath().length() - "_block".length());
			for (String form : new String[]{base + "_ingot", base, "raw_" + base}) {
				Double stored = ITEMS.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(id.getNamespace(), form));
				if (stored != null) return stored * 9.0;
			}
		}
		// any other building block: cheap, a slab half of it
		return id.getPath().endsWith("_slab") ? 0.1 : 0.2;
	}

	/**
	 * The worth of what is made from the server's recipes (on start and after a reload): passes over them until nothing
	 * changes, each item at the cheapest recipe that makes it from things already worth something, each ingredient at
	 * its cheapest choice. Listed items and tags keep their own worth; a recipe never makes a thing cheaper than that.
	 */
	public static synchronized void learn(net.minecraft.server.level.ServerLevel level) {
		loaded = false;
		ITEMS.clear();
		TAGS.clear();
		BANNED.clear();
		load();
		MADE.clear();
		var recipes = com.vnap.Mc.recipeParts(level);
		for (int pass = 0; pass < 8; pass++) {
			boolean changed = false;
			for (var recipe : recipes) {
				if (ITEMS.containsKey(BuiltInRegistries.ITEM.getKey(recipe.result()))) continue;
				double total = 0.0;
				boolean known = true;
				for (var choices : recipe.ingredients()) {
					double cheapest = Double.MAX_VALUE;
					for (Item choice : choices) {
						double each = worth(new ItemStack(choice));
						if (each > 0.0) cheapest = Math.min(cheapest, each);
					}
					if (cheapest == Double.MAX_VALUE) {
						known = false;
						break;
					}
					total += cheapest;
				}
				if (!known) continue;
				double each = total * WORK / Math.max(1, recipe.count());
				Double before = MADE.get(recipe.result());
				if (before == null || each < before - 1e-6) {
					MADE.put(recipe.result(), each);
					changed = true;
				}
			}
			if (!changed) break;
		}
		com.vnap.VillagerLife.LOGGER.info("prices: {} items priced by their recipes", MADE.size());
	}

	/** Where an item's worth comes from (the prices audit, /vnap shop prices): its own line, a tag, a rule, or none. */
	public static String source(ItemStack stack) {
		load();
		if (ITEMS.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return "item";
		for (TagKey<Item> tag : TAGS.keySet()) if (stack.is(tag)) return "tag " + tag.location();
		if (worth(stack) <= 0.0) return "none";
		if (MADE.containsKey(stack.getItem())) return "recipe";
		return rule(stack) > 0.0 ? "rule" : "none";
	}

	/** What a villager pays for one (whole copper, at least one). */
	public static int price(ItemStack stack) {
		double worth = worth(stack);
		return worth <= 0.0 ? 0 : Math.max(1, (int) Math.ceil(worth));
	}

	/** Mood -100..100 turns the player's prices: friends pay less and are paid more. */
	static double moodFactor(float mood) {
		return Math.max(-0.2, Math.min(0.2, mood / 500.0));
	}

	/** What a player pays for {@code count} of this lot's item. */
	public static int playerBuys(int unitPrice, int count, float mood) {
		return Math.max(1, (int) Math.ceil(unitPrice * count * BUY_SHARE * (1.0 - moodFactor(mood))));
	}

	/** What a player is paid for the stack; 0 when it is too little to pay a copper for. */
	public static int playerSells(ItemStack stack, float mood) {
		return (int) Math.floor(worth(stack) * stack.getCount() * SELL_SHARE * (1.0 + moodFactor(mood)));
	}

	private static synchronized void load() {
		if (loaded) return;
		loaded = true;
		try (var stream = Prices.class.getResourceAsStream(PATH)) {
			if (stream == null) return;
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var entry : root.getAsJsonObject("items").entrySet()) {
				ITEMS.put(ResourceLocation.parse(entry.getKey()), entry.getValue().getAsDouble());
			}
			for (var entry : root.getAsJsonObject("tags").entrySet()) {
				TAGS.put(TagKey.create(Registries.ITEM, ResourceLocation.parse(entry.getKey())), entry.getValue().getAsDouble());
			}
		} catch (Exception exception) {
			com.vnap.VillagerLife.LOGGER.warn("Failed to read the price list", exception);
		}
		java.nio.file.Path own = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(CONFIG);
		try {
			if (!java.nio.file.Files.exists(own)) {
				java.nio.file.Files.writeString(own, """
						{
						  "_comment": "Prices in copper (64 copper = 1 silver). 'items' and 'tags' (item tags, e.g. c:ingots/steel) are put over the mod's own list; 'never' are items no stall sells or takes orders for. Things made by recipes are worth their ingredients and a tenth more; another mod's things with no recipe, tag or line here are not dealt in. /reload applies it.",
						  "items": {},
						  "tags": {},
						  "never": []
						}
						""");
			}
			JsonObject root = JsonParser.parseString(java.nio.file.Files.readString(own)).getAsJsonObject();
			// the server's tags first: they win over the mod's broad ones (c:ingots)
			Map<TagKey<Item>, Double> tags = new LinkedHashMap<>();
			if (root.has("items")) for (var entry : root.getAsJsonObject("items").entrySet()) ITEMS.put(ResourceLocation.parse(entry.getKey()), entry.getValue().getAsDouble());
			if (root.has("tags")) for (var entry : root.getAsJsonObject("tags").entrySet()) tags.put(TagKey.create(Registries.ITEM, ResourceLocation.parse(entry.getKey())), entry.getValue().getAsDouble());
			tags.putAll(TAGS);
			TAGS.clear();
			TAGS.putAll(tags);
			if (root.has("never")) for (var entry : root.getAsJsonArray("never")) BANNED.add(entry.getAsString());
		} catch (Exception exception) {
			com.vnap.VillagerLife.LOGGER.warn("Failed to read {}", own, exception);
		}
	}

	/** Every listed item id (the tests check each one is in the game). */
	public static Map<ResourceLocation, Double> listed() {
		load();
		return Map.copyOf(ITEMS);
	}
}
