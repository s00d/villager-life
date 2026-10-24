package com.vnap.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.VillagerLife;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** This player's own choices, {@code config/villager-life-client.json}: subtitles and the mood bar. */
public final class LifeClientSettings {
	private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("villager-life-client.json");
	/** Where the mood bar sits: top centre under the boss bars (no minimap is there), a corner, or above the hotbar. */
	public enum Place {
		TOP, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

		public Place next() {
			return switch (this) {
				case TOP -> TOP_LEFT;
				case TOP_LEFT -> TOP_RIGHT;
				case TOP_RIGHT -> BOTTOM_LEFT;
				case BOTTOM_LEFT -> BOTTOM_RIGHT;
				case BOTTOM_RIGHT -> TOP;
			};
		}
	}

	private static boolean subtitles = true;
	private static boolean moodBar = true;
	private static boolean moodBarAlways;
	private static Place moodBarPlace = Place.TOP;

	private LifeClientSettings() {
	}

	public static synchronized void load() {
		try {
			if (Files.exists(FILE)) {
				JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
				subtitles = !json.has("showSubtitles") || json.get("showSubtitles").getAsBoolean();
				moodBar = !json.has("showMoodBar") || json.get("showMoodBar").getAsBoolean();
				moodBarAlways = json.has("moodBarAlways") && json.get("moodBarAlways").getAsBoolean();
				moodBarPlace = Place.TOP;
				if (json.has("moodBarPlace")) {
					for (Place place : Place.values()) {
						if (place.name().equalsIgnoreCase(json.get("moodBarPlace").getAsString())) moodBarPlace = place;
					}
				}
				return;
			}
		} catch (IOException | RuntimeException broken) {
			VillagerLife.LOGGER.warn("{} is unreadable; the defaults are used", FILE, broken);
		}
		save();
	}

	public static boolean showSubtitles() {
		return subtitles;
	}

	public static synchronized void setShowSubtitles(boolean on) {
		subtitles = on;
		save();
	}

	public static boolean showMoodBar() {
		return moodBar;
	}

	public static synchronized void setShowMoodBar(boolean on) {
		moodBar = on;
		save();
	}

	/** False: the bar shows for a few seconds when the mood changes or the player walks into a village, then hides. */
	public static boolean moodBarAlways() {
		return moodBarAlways;
	}

	public static synchronized void setMoodBarAlways(boolean on) {
		moodBarAlways = on;
		save();
	}

	public static Place moodBarPlace() {
		return moodBarPlace;
	}

	public static synchronized void setMoodBarPlace(Place place) {
		moodBarPlace = place;
		save();
	}

	private static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("showSubtitles", subtitles);
		json.addProperty("showMoodBar", moodBar);
		json.addProperty("moodBarAlways", moodBarAlways);
		json.addProperty("moodBarPlace", moodBarPlace.name().toLowerCase(java.util.Locale.ROOT));
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n", StandardCharsets.UTF_8);
		} catch (IOException failed) {
			VillagerLife.LOGGER.warn("could not write {}", FILE, failed);
		}
	}
}
