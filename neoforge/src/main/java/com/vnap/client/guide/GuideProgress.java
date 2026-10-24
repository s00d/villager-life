package com.vnap.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.VillagerLife;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Entries this player has opened, kept on the client in config/villager-life-guide.json: the rest show a red dot. */
final class GuideProgress {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("villager-life-guide.json");

    private final Set<String> read;

    private GuideProgress(Set<String> read) {
        this.read = read;
    }

    static GuideProgress load() {
        Set<String> read = new LinkedHashSet<>();
        if (Files.isRegularFile(FILE)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("read")) root.getAsJsonArray("read").forEach(id -> read.add(id.getAsString()));
            } catch (IOException | RuntimeException exception) {
                VillagerLife.LOGGER.warn("Villager Life guide progress could not be read; starting afresh", exception);
            }
        }
        return new GuideProgress(read);
    }

    boolean read(String id) {
        return read.contains(id);
    }

    void markRead(String id) {
        if (!read.add(id)) return;
        JsonArray list = new JsonArray();
        read.forEach(list::add);
        JsonObject root = new JsonObject();
        root.add("read", list);
        try {
            Files.writeString(FILE, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            VillagerLife.LOGGER.warn("Villager Life guide progress could not be saved", exception);
        }
    }
}
