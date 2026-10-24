package com.vnap.entity.golem;

import com.vnap.dialogue.Speech;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import java.util.Comparator;

/** Dialogue titles villagers shout during the fight (dialogues.json, E13–E21). */
public final class GolemLines {
    public static final String RITE_START = "Forge the Tocsin";
    public static final String INGOT = "Feed the Forge";
    public static final String AWAKEN = "Tocsin Awakens";
    public static final String PHASE_TWO = "Tocsin Sheds Plates";
    public static final String PHASE_THREE = "Tocsin Bell Cracks";
    public static final String SLAIN = "Tocsin Silenced";
    public static final String LEFT = "Tocsin Sleeps";
    public static final String INTERRUPTED = "Ritual Interrupted";
    public static final String HIDE = "Hide From the Tocsin";

    private GolemLines() {
    }

    public static boolean say(ServerLevel level, Entity near, String title) {
        // Nearest first; a busy or voiceless villager hands the shout to the next one.
        return level.getEntitiesOfClass(Villager.class, near.getBoundingBox().inflate(48.0), v -> v.isAlive() && !v.isBaby())
                .stream()
                .sorted(Comparator.comparingDouble(v -> v.distanceToSqr(near)))
                .anyMatch(v -> Speech.line(v, title,
                        "golem:" + near.getId() + ":" + title, true));
    }
}
