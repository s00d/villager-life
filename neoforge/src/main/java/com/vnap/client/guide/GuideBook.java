package com.vnap.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.VillagerLife;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The guide book's content: guide/&lt;language&gt;.json of the mod's assets (a resource pack may replace it). Russian
 * clients read ru_ru, everyone else en_us. Read again each time the book opens, so F3+T shows edits at once.
 */
public final class GuideBook {
    public record Page(String type, String title, String text, String item, String recipe, String image, int imageWidth,
                       int imageHeight, List<String> items, List<String> links, String schematic, String screen) {
    }

    /** {@code unlock}: what the player must have come upon first (progress/Discoveries), any one; none — always open. */
    public record Entry(String id, String icon, String title, List<Page> pages, List<String> unlock) {
        public boolean open() {
            return GuideBook.open(unlock);
        }
    }

    /** {@code color} is the chapter's tab and title ribbon, 0xRRGGBB. */
    public record Category(String id, String icon, int color, String title, String description, List<Entry> entries, List<String> unlock) {
        public boolean open() {
            return GuideBook.open(unlock);
        }
    }

    /** What the server says this player has come upon; every key while not in a world (the dev shots, a menu). */
    private static final java.util.Set<String> DISCOVERED = new java.util.HashSet<>();
    private static boolean told;

    public static void discovered(List<String> keys) {
        DISCOVERED.clear();
        DISCOVERED.addAll(keys);
        told = true;
    }

    /** Off a server: the next one tells its own. */
    public static void forget() {
        DISCOVERED.clear();
        told = false;
    }

    /** /vnapguide all: the whole book open on this client, as the shots of every spread need it. */
    public static boolean everything;

    static boolean open(List<String> unlock) {
        if (unlock.isEmpty() || everything || !told) return true;
        for (String key : unlock) if (DISCOVERED.contains(key)) return true;
        return false;
    }

    public final String title;
    public final String subtitle;
    public final String landing;
    public final List<Category> categories;
    /** Every entry by id, and the entry that explains an item (its spotlight or crafting page, else its icon). */
    public final Map<String, Entry> entries = new LinkedHashMap<>();
    public final Map<String, Entry> byItem = new LinkedHashMap<>();
    public final Map<String, Category> categoryOf = new LinkedHashMap<>();

    private GuideBook(String title, String subtitle, String landing, List<Category> categories) {
        this.title = title;
        this.subtitle = subtitle;
        this.landing = landing;
        this.categories = categories;
        for (Category category : categories) {
            for (Entry entry : category.entries()) {
                entries.put(entry.id(), entry);
                categoryOf.put(entry.id(), category);
                for (Page page : entry.pages()) {
                    if (!page.item().isEmpty()) byItem.putIfAbsent(page.item(), entry);
                }
            }
        }
        for (Category category : categories) {
            for (Entry entry : category.entries()) byItem.putIfAbsent(entry.icon(), entry);
        }
    }

    /** The language file this client reads: ru_ru for any Russian locale, en_us for every other one. */
    public static String language() {
        String selected = Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase(Locale.ROOT);
        return selected.startsWith("ru") ? "ru_ru" : "en_us";
    }

    public static GuideBook load() {
        String language = language();
        GuideBook book = read(language);
        if (book == null && !language.equals("en_us")) book = read("en_us");
        return book != null ? book : new GuideBook("Villager Life", "", "The guide could not be read.", List.of());
    }

    private static @Nullable GuideBook read(String language) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VillagerLife.MOD_ID, "guide/" + language + ".json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (Reader reader = resource.get().openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException exception) {
            VillagerLife.LOGGER.error("Villager Life guide {} is broken", id, exception);
            return null;
        }
    }

    static GuideBook parse(JsonObject root) {
        List<Category> categories = new ArrayList<>();
        for (JsonElement element : array(root, "categories")) {
            JsonObject category = element.getAsJsonObject();
            List<Entry> entries = new ArrayList<>();
            for (JsonElement entryElement : array(category, "entries")) {
                JsonObject entry = entryElement.getAsJsonObject();
                List<Page> pages = new ArrayList<>();
                for (JsonElement pageElement : array(entry, "pages")) {
                    JsonObject page = pageElement.getAsJsonObject();
                    pages.add(new Page(string(page, "type", "text"), string(page, "title", ""), string(page, "text", ""),
                            string(page, "item", ""), string(page, "recipe", ""), string(page, "image", ""),
                            integer(page, "width", 256), integer(page, "height", 144), strings(page, "items"),
                            strings(page, "links"), string(page, "schematic", ""), string(page, "screen", "")));
                }
                entries.add(new Entry(string(entry, "id", ""), string(entry, "icon", "minecraft:book"), string(entry, "title", ""), pages, strings(entry, "unlock")));
            }
            categories.add(new Category(string(category, "id", ""), string(category, "icon", "minecraft:book"),
                    colour(string(category, "color", "#3E8E4E")), string(category, "title", ""), string(category, "description", ""), entries,
                    strings(category, "unlock")));
        }
        return new GuideBook(string(root, "title", "Villager Life"), string(root, "subtitle", ""), string(root, "landing", ""), categories);
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        // long texts may be written as an array of lines
        if (value.isJsonArray()) {
            List<String> lines = new ArrayList<>();
            for (JsonElement line : value.getAsJsonArray()) lines.add(line.getAsString());
            return String.join("\n", lines);
        }
        return value.getAsString();
    }

    private static int colour(String hex) {
        try {
            return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException exception) {
            return 0x3E8E4E;
        }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static List<String> strings(JsonObject object, String key) {
        List<String> out = new ArrayList<>();
        for (JsonElement element : array(object, key)) out.add(element.getAsString());
        return out;
    }
}
