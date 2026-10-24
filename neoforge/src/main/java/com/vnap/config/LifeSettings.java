package com.vnap.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.VillagerLife;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The server's settings, {@code config/villager-life.json}: how much villagers talk, how often the rare takes come
 * up, the guide book on joining, keeping a town loaded around its players, and the megastructures.
 */
public final class LifeSettings {
	private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("villager-life.json");
	/** 0 silent, 1 shy (cooldowns doubled), 2 as written, 3 chatty (a fifth). */
	private static int chattiness = 2;
	/** 0 never the rare takes, 1 by their weight, 2 the rare ones first. */
	private static int rareVoicelines = 1;
	private static boolean giveGuideOnJoin = true;
	private static boolean townChunkLoading = true;
	private static int townLingerMinutes = 5;
	private static boolean megastructures = true;

	private LifeSettings() {
	}

	/** Reads the file, then writes it back: keys added since it was written show up with their defaults. */
	public static synchronized void load() {
		try {
			if (Files.exists(FILE)) {
				JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
				chattiness = Math.clamp(number(json, "chattiness", 2), 0, 3);
				rareVoicelines = Math.clamp(number(json, "rareVoicelines", 1), 0, 2);
				giveGuideOnJoin = flag(json, "giveGuideOnJoin");
				townChunkLoading = flag(json, "townChunkLoading");
				townLingerMinutes = Math.clamp(number(json, "townLingerMinutes", 5), 0, 120);
				megastructures = flag(json, "megastructures");
			}
		} catch (IOException | RuntimeException broken) {
			VillagerLife.LOGGER.warn("{} is unreadable; the defaults are used", FILE, broken);
		}
		save();
	}

	private static int number(JsonObject json, String key, int fallback) {
		return json.has(key) ? json.get(key).getAsInt() : fallback;
	}

	private static boolean flag(JsonObject json, String key) {
		return !json.has(key) || json.get(key).getAsBoolean();
	}

	/** An operator changed the talk settings in the settings screen. */
	public static synchronized void update(int newChattiness, int newRareVoicelines) {
		chattiness = Math.clamp(newChattiness, 0, 3);
		rareVoicelines = Math.clamp(newRareVoicelines, 0, 2);
		save();
	}

	private static synchronized void save() {
		JsonObject json = new JsonObject();
		json.addProperty("chattiness", chattiness);
		json.addProperty("rareVoicelines", rareVoicelines);
		json.addProperty("giveGuideOnJoin", giveGuideOnJoin);
		json.addProperty("townChunkLoading", townChunkLoading);
		json.addProperty("townLingerMinutes", townLingerMinutes);
		json.addProperty("megastructures", megastructures);
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n", StandardCharsets.UTF_8);
		} catch (IOException failed) {
			VillagerLife.LOGGER.warn("could not write {}", FILE, failed);
		}
	}

	public static int chattiness() {
		return chattiness;
	}

	public static int rareVoicelines() {
		return rareVoicelines;
	}

	public static boolean dialogueEnabled() {
		return chattiness > 0;
	}

	/** A line's cooldown at this chattiness. */
	public static long scaleCooldown(long ticks) {
		if (chattiness == 1) return ticks > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : ticks * 2L;
		return chattiness == 3 ? Math.max(1L, Math.round(ticks / 5.0)) : ticks;
	}

	/** Each player gets the guide book once, on the first join of a world. */
	public static boolean giveGuideOnJoin() {
		return giveGuideOnJoin;
	}

	/** A town stays loaded whole while a player is in or near it (TownLoading). */
	public static boolean townChunkLoading() {
		return townChunkLoading;
	}

	/** Whether the megastructures (mega/) come to life; a server that does not want them turns them off. */
	public static boolean megastructures() {
		return megastructures;
	}

	/** Tests: the switch, without the file. */
	public static void megastructures(boolean on) {
		megastructures = on;
	}

	/** How long a town stays loaded after its last player left. */
	public static int townLingerMinutes() {
		return townLingerMinutes;
	}
}
