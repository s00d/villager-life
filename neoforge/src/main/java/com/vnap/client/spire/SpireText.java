package com.vnap.client.spire;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.VillagerLife;
import com.vnap.network.SpireNotePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * The texts of the spire's tablets, in the client's language (assets/villager_life/spire/): the story notes of floors
 * 1 to 100 as written, a villager's note and a new layer's note put together from pieces by their seed. A resource
 * pack may replace them; a missing language falls back to English.
 */
public final class SpireText {
	/** A note as shown: its title, who wrote it and when, the line over the text, the paragraphs. */
	public record Note(String title, String author, String date, String heading, List<String> paragraphs) {
	}

	private SpireText() {
	}

	public static Note of(SpireNotePayload note) {
		return switch (note.kind()) {
			case SpireNotePayload.VILLAGER -> villager(note);
			case SpireNotePayload.LAYER -> layer(note);
			default -> story(note.floor());
		};
	}

	private static String language() {
		String selected = Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(Locale.ROOT);
		return selected.startsWith("ru") ? "ru_ru" : "en_us";
	}

	private static @Nullable JsonObject read(String path) {
		JsonObject found = read(path, language());
		return found != null ? found : read(path, "en_us");
	}

	private static @Nullable JsonObject read(String path, String language) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VillagerLife.MOD_ID, "spire/" + path + "/" + language + ".json");
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
		if (resource.isEmpty()) return null;
		try (Reader reader = resource.get().openAsReader()) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (IOException | RuntimeException exception) {
			VillagerLife.LOGGER.error("Villager Life spire text {} is broken", id, exception);
			return null;
		}
	}

	static Note story(int floor) {
		JsonObject notes = read("notes");
		JsonObject one = notes == null || !notes.has(Integer.toString(floor)) ? null : notes.getAsJsonObject(Integer.toString(floor));
		String heading = Component.translatable("spire.villager_life.note.heading", (floor - 1) / 10 + 1, floor).getString();
		if (one == null) {
			return new Note(Component.translatable("spire.villager_life.note.title", floor).getString(), "", "", heading,
					List.of(Component.translatable("spire.villager_life.note.missing").getString()));
		}
		List<String> paragraphs = new ArrayList<>();
		for (JsonElement line : array(one, "text")) paragraphs.add(line.getAsString());
		return new Note(string(one, "title"), string(one, "author"), string(one, "date"), heading, paragraphs);
	}

	static Note villager(SpireNotePayload note) {
		JsonObject root = read("fragments");
		JsonObject pieces = root == null || !root.has("villager") ? null : root.getAsJsonObject("villager");
		String heading = Component.translatable("spire.villager_life.note.villager_heading", note.floor()).getString();
		if (pieces == null) return new Note("", note.name(), "", heading, List.of(Component.translatable("spire.villager_life.note.missing").getString()));
		Random random = new Random(note.seed());
		JsonObject events = pieces.getAsJsonObject("event");
		JsonArray themed = events != null && events.has(note.theme()) ? events.getAsJsonArray(note.theme()) : null;
		String text = pick(array(pieces, "opening"), random) + " " + (themed == null ? "" : pick(themed, random) + " ") + pick(array(pieces, "feeling"), random);
		String signature = pick(array(pieces, "signature"), random);
		return new Note(Component.translatable("spire.villager_life.note.villager_title").getString(), signature.replace("%s", note.name()), "",
				heading, List.of(text.trim()));
	}

	static Note layer(SpireNotePayload note) {
		JsonObject root = read("fragments");
		JsonObject pieces = root == null || !root.has("layer") ? null : root.getAsJsonObject("layer");
		String heading = Component.translatable("spire.villager_life.note.layer_heading", note.floor()).getString();
		if (pieces == null) return new Note("", "", "", heading, List.of(Component.translatable("spire.villager_life.note.layer").getString()));
		Random random = new Random(note.seed());
		List<String> paragraphs = List.of(pick(array(pieces, "place"), random) + " " + pick(array(pieces, "find"), random),
				pick(array(pieces, "who"), random) + " " + pick(array(pieces, "conclusion"), random));
		return new Note(Component.translatable("spire.villager_life.note.layer_title").getString(), "", "", heading, paragraphs);
	}

	private static String pick(JsonArray array, Random random) {
		return array.isEmpty() ? "" : array.get(random.nextInt(array.size())).getAsString();
	}

	private static JsonArray array(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	private static String string(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
	}
}
