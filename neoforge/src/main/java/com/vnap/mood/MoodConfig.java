package com.vnap.mood;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.VillagerLife;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Mood/monument/forge/boss tunables. Separate file keeps LifeSettings small. */
public final class MoodConfig {
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("villager-life-mood.json");

    private static boolean enabled = true;
    private static float happyThreshold = 60.0F;
    private static float angryThreshold = -60.0F;
    private static float cultReadyThreshold = -90.0F;
    private static float festoonThreshold = 20.0F;
    private static float festivalThreshold = 50.0F;
    private static float pavilionThreshold = 75.0F;
    private static float vigilThreshold = -20.0F;
    private static float mourningThreshold = -50.0F;
    private static float pyreThreshold = -75.0F;
    private static int minWorkers = 3;
    private static int maxWorkers = 6;
    private static int buildCooldownSeconds = 1200;
    private static int retryCooldownSeconds = 30;
    private static int summonCooldownSeconds = 3600;
    private static int bossArenaRadius = 24;
    private static float bossMaxHealth = 400.0F;
    private static float bossHealthPerPlayer = 0.25F;
    private static boolean bossBreaksLeaves = true;
    private static boolean bossDarkFog = true;
    private static boolean bossScreenShake = true;
    private static boolean bossMusic = true;
    private static int monumentBuffRadius = 40;
    private static float altarSmolderParticleChance = 0.15F;
    /** /vnap: on in a development run, off in the built mod (a public server's operators would get bots and coins). */
    private static boolean debugCommands = !com.vnap.Mc.production();
    private static int buildWorkTicks = 24;
    private static int courtshipDays = 1;
    private static int maxChildren = 3;
    private static int birthCooldownDays = 2;
    private static float minMoodForBirth = -20.0F;
    private static int remarryDays = 5;
    private static int childhoodDays = 2;
    private static int infantDays = 1;
    private static int maxVillagers = 72;
    private static int elderDays = 40;
    private static int oldAgeDays = 12;
    private static boolean oldAgeDeath = true;

    private MoodConfig() {
    }

    public static synchronized void load() {
        if (!Files.exists(PATH)) {
            save();
            push();
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(PATH, StandardCharsets.UTF_8)).getAsJsonObject();
            enabled = b(root, "enabled", true);
            happyThreshold = f(root, "happyThreshold", 60.0F);
            angryThreshold = f(root, "angryThreshold", -60.0F);
            cultReadyThreshold = f(root, "cultReadyThreshold", -90.0F);
            festoonThreshold = f(root, "festoonThreshold", 20.0F);
            festivalThreshold = f(root, "festivalThreshold", 50.0F);
            pavilionThreshold = f(root, "pavilionThreshold", 75.0F);
            vigilThreshold = f(root, "vigilThreshold", -20.0F);
            mourningThreshold = f(root, "mourningThreshold", -50.0F);
            pyreThreshold = f(root, "pyreThreshold", -75.0F);
            minWorkers = i(root, "minWorkers", 3);
            maxWorkers = i(root, "maxWorkers", 6);
            buildCooldownSeconds = i(root, "buildCooldownSeconds", 1200);
            retryCooldownSeconds = i(root, "retryCooldownSeconds", 30);
            summonCooldownSeconds = i(root, "summonCooldownSeconds", 3600);
            bossArenaRadius = i(root, "bossArenaRadius", 24);
            bossMaxHealth = f(root, "bossMaxHealth", 400.0F);
            bossHealthPerPlayer = f(root, "bossHealthPerPlayer", 0.25F);
            bossBreaksLeaves = b(root, "bossBreaksLeaves", true);
            bossDarkFog = b(root, "bossDarkFog", true);
            bossScreenShake = b(root, "bossScreenShake", true);
            bossMusic = b(root, "bossMusic", true);
            monumentBuffRadius = i(root, "monumentBuffRadius", 40);
            altarSmolderParticleChance = f(root, "altarSmolderParticleChance", 0.15F);
            debugCommands = b(root, "debugCommands", !com.vnap.Mc.production());
            buildWorkTicks = i(root, "buildWorkTicks", 24);
            courtshipDays = Math.max(0, i(root, "courtshipDays", 1));
            maxChildren = Math.max(0, i(root, "maxChildren", 3));
            birthCooldownDays = Math.max(0, i(root, "birthCooldownDays", 2));
            minMoodForBirth = f(root, "minMoodForBirth", -20.0F);
            remarryDays = Math.max(0, i(root, "remarryDays", 5));
            childhoodDays = Math.max(1, i(root, "childhoodDays", 2));
            infantDays = Math.max(0, i(root, "infantDays", 1));
            maxVillagers = Math.max(2, i(root, "maxVillagers", 72));
            elderDays = Math.max(1, i(root, "elderDays", 40));
            oldAgeDays = Math.max(1, i(root, "oldAgeDays", 12));
            oldAgeDeath = b(root, "oldAgeDeath", true);
            // keys added since the file was written appear in it with their defaults
            save();
        } catch (IOException | RuntimeException exception) {
            VillagerLife.LOGGER.warn("Could not load mood config; defaults", exception);
            save();
        }
        push();
    }

    private static void push() {
        MoodThresholds.set(happyThreshold, angryThreshold, cultReadyThreshold);
        MoodThresholds.setLesser(festoonThreshold, festivalThreshold, pavilionThreshold,
                vigilThreshold, mourningThreshold, pyreThreshold);
    }

    private static synchronized void save() {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        root.addProperty("happyThreshold", happyThreshold);
        root.addProperty("angryThreshold", angryThreshold);
        root.addProperty("cultReadyThreshold", cultReadyThreshold);
        root.addProperty("festoonThreshold", festoonThreshold);
        root.addProperty("festivalThreshold", festivalThreshold);
        root.addProperty("pavilionThreshold", pavilionThreshold);
        root.addProperty("vigilThreshold", vigilThreshold);
        root.addProperty("mourningThreshold", mourningThreshold);
        root.addProperty("pyreThreshold", pyreThreshold);
        root.addProperty("minWorkers", minWorkers);
        root.addProperty("maxWorkers", maxWorkers);
        root.addProperty("buildCooldownSeconds", buildCooldownSeconds);
        root.addProperty("retryCooldownSeconds", retryCooldownSeconds);
        root.addProperty("summonCooldownSeconds", summonCooldownSeconds);
        root.addProperty("bossArenaRadius", bossArenaRadius);
        root.addProperty("bossMaxHealth", bossMaxHealth);
        root.addProperty("bossHealthPerPlayer", bossHealthPerPlayer);
        root.addProperty("bossBreaksLeaves", bossBreaksLeaves);
        root.addProperty("bossDarkFog", bossDarkFog);
        root.addProperty("bossScreenShake", bossScreenShake);
        root.addProperty("bossMusic", bossMusic);
        root.addProperty("monumentBuffRadius", monumentBuffRadius);
        root.addProperty("altarSmolderParticleChance", altarSmolderParticleChance);
        root.addProperty("debugCommands", debugCommands);
        root.addProperty("buildWorkTicks", buildWorkTicks);
        root.addProperty("courtshipDays", courtshipDays);
        root.addProperty("maxChildren", maxChildren);
        root.addProperty("birthCooldownDays", birthCooldownDays);
        root.addProperty("minMoodForBirth", minMoodForBirth);
        root.addProperty("remarryDays", remarryDays);
        root.addProperty("childhoodDays", childhoodDays);
        root.addProperty("infantDays", infantDays);
        root.addProperty("maxVillagers", maxVillagers);
        root.addProperty("elderDays", elderDays);
        root.addProperty("oldAgeDays", oldAgeDays);
        root.addProperty("oldAgeDeath", oldAgeDeath);
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            VillagerLife.LOGGER.warn("Could not save mood config", exception);
        }
    }

    private static float f(JsonObject root, String key, float def) {
        return root.has(key) ? root.get(key).getAsFloat() : def;
    }

    private static int i(JsonObject root, String key, int def) {
        return root.has(key) ? root.get(key).getAsInt() : def;
    }

    private static boolean b(JsonObject root, String key, boolean def) {
        return root.has(key) ? root.get(key).getAsBoolean() : def;
    }

    /** Days a couple courts before the wedding. */
    public static int courtshipDays() {
        return courtshipDays;
    }

    /** Children one couple may have. */
    public static int maxChildren() {
        return maxChildren;
    }

    public static int birthCooldownDays() {
        return birthCooldownDays;
    }

    /** A village sourer than this has no new children. */
    public static float minMoodForBirth() {
        return minMoodForBirth;
    }

    /** Days a widow or widower mourns before courting again. */
    public static int remarryDays() {
        return remarryDays;
    }


    /** A newborn's days in its cradle, before the childhood days. */
    public static int infantDays() {
        return infantDays;
    }

    /** Game days a newborn stays a child (vanilla is one). */
    public static int childhoodDays() {
        return childhoodDays;
    }

    /** Villagers (children too) around one bell before births stop and young couples leave to found a hamlet. */
    public static int maxVillagers() {
        return maxVillagers;
    }

    /** Days of adult life before a villager grows old. */
    public static int elderDays() {
        return elderDays;
    }

    /** Days an elder lives on before passing away in their sleep (when {@link #oldAgeDeath()}). */
    public static int oldAgeDays() {
        return oldAgeDays;
    }

    public static boolean oldAgeDeath() {
        return oldAgeDeath;
    }

    /** Ticks a villager works on one block by hand before it stands; harder blocks take up to twice as long. */
    public static int buildWorkTicks() {
        return buildWorkTicks;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static float happyThreshold() {
        return happyThreshold;
    }

    public static float angryThreshold() {
        return angryThreshold;
    }

    public static float cultReadyThreshold() {
        return cultReadyThreshold;
    }

    public static int minWorkers() {
        return minWorkers;
    }

    public static int maxWorkers() {
        return maxWorkers;
    }

    public static int buildCooldownTicks() {
        return buildCooldownSeconds * 20;
    }

    public static int retryCooldownTicks() {
        return retryCooldownSeconds * 20;
    }

    public static int summonCooldownTicks() {
        return summonCooldownSeconds * 20;
    }

    public static int bossArenaRadius() {
        return bossArenaRadius;
    }

    public static float bossMaxHealth() {
        return bossMaxHealth;
    }

    public static float bossHealthPerPlayer() {
        return bossHealthPerPlayer;
    }

    public static boolean bossBreaksLeaves() {
        return bossBreaksLeaves;
    }

    public static boolean bossDarkFog() {
        return bossDarkFog;
    }

    public static boolean bossScreenShake() {
        return bossScreenShake;
    }

    public static boolean bossMusic() {
        return bossMusic;
    }

    public static int monumentBuffRadius() {
        return monumentBuffRadius;
    }

    public static float altarSmolderParticleChance() {
        return altarSmolderParticleChance;
    }

    public static boolean debugCommands() {
        return debugCommands;
    }
}
