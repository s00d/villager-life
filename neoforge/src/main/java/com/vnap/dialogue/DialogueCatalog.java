package com.vnap.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.VillagerLife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;

/**
 * Every voiced line of the mod, read once from {@code dialogues.json} while the sound registry fills: a group is one
 * situation for one speaker, a variant one recorded take of it (an English and a Russian clip).
 */
public final class DialogueCatalog {
	private static final String FILE = "/assets/villager_life/dialogues.json";
	private static final Map<String, DialogueGroup> BY_ID = new LinkedHashMap<>();
	/** "title|speaker" and plain "title" (the first group of that title). */
	private static final Map<String, DialogueGroup> BY_TITLE = new HashMap<>();

	private DialogueCatalog() {
	}

	public static void register(RegisterEvent.RegisterHelper<SoundEvent> sounds) {
		JsonObject groups;
		try (InputStream in = DialogueCatalog.class.getResourceAsStream(FILE)) {
			if (in == null) throw new IOException(FILE + " is not in the jar");
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				groups = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("groups");
			}
		} catch (IOException | RuntimeException failure) {
			throw new IllegalStateException("the dialogue catalogue did not load", failure);
		}
		int takes = 0;
		for (Map.Entry<String, JsonElement> entry : groups.entrySet()) {
			DialogueGroup group = readGroup(entry.getKey(), entry.getValue().getAsJsonObject(), sounds);
			takes += group.variants().size();
			BY_ID.put(group.id(), group);
			if (group.title().isBlank()) continue;
			BY_TITLE.putIfAbsent(group.title(), group);
			BY_TITLE.putIfAbsent(group.title() + "|" + group.speaker(), group);
		}
		VillagerLife.LOGGER.info("Dialogue catalogue: {} groups, {} takes", BY_ID.size(), takes);
	}

	private static DialogueGroup readGroup(String id, JsonObject json, RegisterEvent.RegisterHelper<SoundEvent> sounds) {
		List<DialogueVariant> variants = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("variants")) {
			JsonObject take = element.getAsJsonObject();
			int index = take.get("index").getAsInt();
			List<SubtitleFrame> frames = new ArrayList<>();
			for (JsonElement frame : take.getAsJsonArray("subtitles")) {
				JsonObject f = frame.getAsJsonObject();
				frames.add(new SubtitleFrame(f.get("time").getAsDouble(), f.get("key").getAsString()));
			}
			variants.add(new DialogueVariant(index, take.get("duration").getAsDouble(), take.get("weight").getAsInt(),
					sound(sounds, "dialogue." + id + "." + index), List.copyOf(frames)));
		}
		return new DialogueGroup(id, json.get("title").getAsString(), json.get("body").getAsString(),
				json.get("speaker").getAsString(), json.get("maximumDuration").getAsDouble(), List.copyOf(variants));
	}

	private static SoundEvent sound(RegisterEvent.RegisterHelper<SoundEvent> sounds, String path) {
		ResourceLocation name = VillagerLife.id(path);
		SoundEvent event = SoundEvent.createVariableRangeEvent(name);
		sounds.register(name, event);
		return event;
	}

	public static @Nullable DialogueGroup byId(String id) {
		return BY_ID.get(id);
	}

	public static @Nullable DialogueGroup byTitle(String title) {
		return BY_TITLE.get(title);
	}

	public static @Nullable DialogueGroup byTitle(String title, String speaker) {
		return BY_TITLE.get(title + "|" + speaker);
	}

	public static long durationTicks(String id) {
		DialogueGroup group = BY_ID.get(id);
		return group == null ? 0L : group.durationTicks();
	}

	public static Map<String, DialogueGroup> groups() {
		return Collections.unmodifiableMap(BY_ID);
	}

	static long ticks(double seconds) {
		return Math.max(20L, (long) Math.ceil(seconds * 20.0));
	}

	public record DialogueGroup(String id, String title, String body, String speaker, double longest,
			List<DialogueVariant> variants) {
		public long durationTicks() {
			return ticks(longest);
		}

		/**
		 * A take by weight. rare 0 keeps to the common takes (within 80% of the heaviest), 2 turns the weights upside
		 * down so the rare ones come up most; {@code skip} are takes heard lately, ignored when nothing else is left.
		 */
		public @Nullable DialogueVariant pick(int rare, Set<Integer> skip) {
			if (variants.isEmpty()) return null;
			int light = Integer.MAX_VALUE;
			int heavy = 0;
			for (DialogueVariant v : variants) {
				light = Math.min(light, v.weight());
				heavy = Math.max(heavy, v.weight());
			}
			int[] odds = new int[variants.size()];
			int total = 0;
			for (int i = 0; i < odds.length; i++) {
				DialogueVariant v = variants.get(i);
				int w = rare == 2 ? heavy + light - v.weight() : rare == 0 && v.weight() < heavy * 0.8 ? 0 : v.weight();
				odds[i] = skip.contains(v.index()) ? 0 : Math.max(0, w);
				total += odds[i];
			}
			if (total == 0) return skip.isEmpty() ? variants.getFirst() : pick(rare, Set.of());
			int roll = ThreadLocalRandom.current().nextInt(total);
			for (int i = 0; i < odds.length; i++) {
				roll -= odds[i];
				if (roll < 0) return variants.get(i);
			}
			return variants.getLast();
		}

		/** How many takes {@link #pick} chooses among at this rare setting. */
		int choices(int rare) {
			if (rare != 0) return variants.size();
			int heavy = variants.stream().mapToInt(DialogueVariant::weight).max().orElse(1);
			return (int) variants.stream().filter(v -> v.weight() >= heavy * 0.8).count();
		}

		public @Nullable DialogueVariant variant(int index) {
			for (DialogueVariant v : variants) if (v.index() == index) return v;
			return null;
		}
	}

	/** {@code voice} plays the clip in the language the jar was built with (neoforge/build.gradle: one jar per voice). */
	public record DialogueVariant(int index, double seconds, int weight, SoundEvent voice, List<SubtitleFrame> subtitles) {
		public long durationTicks() {
			return ticks(seconds);
		}
	}

	public record SubtitleFrame(double time, String key) {
	}
}
