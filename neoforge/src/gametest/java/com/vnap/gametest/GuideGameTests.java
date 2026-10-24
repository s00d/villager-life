package com.vnap.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.item.GuideGift;
import com.vnap.item.LifeItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The guide book: given once on the first join, and both languages describe the same, real things. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class GuideGameTests {
	@GameTest(template = "platform")
	public static void theGuideIsGivenOnceOnTheFirstJoin(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Checks.assertTrue(helper, GuideGift.give(player), "the first join gives the guide");
		Checks.assertTrue(helper, player.getInventory().countItem(LifeItems.HANDBOOK) == 1, "the guide is in the inventory");
		Checks.assertTrue(helper, !GuideGift.give(player), "the next join gives nothing");
		Checks.assertTrue(helper, player.getInventory().countItem(LifeItems.HANDBOOK) == 1, "still one guide");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void bothGuidesShowTheSameRealItemsAndRecipes(GameTestHelper helper) {
		JsonObject en = read("en_us");
		JsonObject ru = read("ru_ru");
		if (en == null || ru == null) {
			Checks.fail(helper, "guide/en_us.json and guide/ru_ru.json are in the mod");
			return;
		}
		List<String> english = shape(en);
		Checks.assertTrue(helper, english.equals(shape(ru)), "the Russian guide has the English one's chapters, entries and pages");
		Set<String> ids = new HashSet<>();
		for (String line : english) if (line.startsWith("entry ")) ids.add(line.substring(6));
		for (String line : english) {
			String[] part = line.split(" ", 2);
			switch (part[0]) {
				case "item" -> Checks.assertTrue(helper, BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(part[1])), "an item of the guide exists: " + part[1]);
				case "recipe" -> Checks.assertTrue(helper, com.vnap.Mc.hasRecipe(helper.getLevel(), ResourceLocation.parse(part[1])),
						"a recipe of the guide is loaded: " + part[1]);
				case "link" -> Checks.assertTrue(helper, ids.contains(part[1]), "a link of the guide leads to an entry: " + part[1]);
				case "image" -> Checks.assertTrue(helper, exists("/assets/" + part[1].replace(':', '/')), "a picture of the guide is in the mod: " + part[1]);
				default -> {
				}
			}
		}
		helper.succeed();
	}

	/** Everything but the words: chapter and entry ids, page types, items, recipes, links, pictures. */
	private static List<String> shape(JsonObject book) {
		List<String> out = new ArrayList<>();
		for (JsonElement categoryElement : book.getAsJsonArray("categories")) {
			JsonObject category = categoryElement.getAsJsonObject();
			out.add("category " + category.get("id").getAsString());
			out.add("item " + category.get("icon").getAsString());
			for (JsonElement entryElement : category.getAsJsonArray("entries")) {
				JsonObject entry = entryElement.getAsJsonObject();
				out.add("entry " + entry.get("id").getAsString());
				out.add("item " + entry.get("icon").getAsString());
				for (JsonElement pageElement : entry.getAsJsonArray("pages")) {
					JsonObject page = pageElement.getAsJsonObject();
					out.add("page " + page.get("type").getAsString());
					if (page.has("item")) out.add("item " + page.get("item").getAsString());
					if (page.has("recipe")) out.add("recipe " + page.get("recipe").getAsString());
					if (page.has("image")) out.add("image " + page.get("image").getAsString());
					if (page.has("schematic")) out.add("schematic " + page.get("schematic").getAsString());
					if (page.has("items")) for (JsonElement item : page.getAsJsonArray("items")) out.add("item " + item.getAsString());
					if (page.has("links")) for (JsonElement link : page.getAsJsonArray("links")) out.add("link " + link.getAsString());
				}
			}
		}
		return out;
	}

	private static boolean exists(String path) {
		try (InputStream stream = GuideGameTests.class.getResourceAsStream(path)) {
			return stream != null;
		} catch (IOException exception) {
			return false;
		}
	}

	private static @org.jspecify.annotations.Nullable JsonObject read(String language) {
		String path = "/assets/villager_life/guide/" + language + ".json";
		try (InputStream stream = GuideGameTests.class.getResourceAsStream(path)) {
			if (stream == null) return null;
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException exception) {
			return null;
		}
	}
}
