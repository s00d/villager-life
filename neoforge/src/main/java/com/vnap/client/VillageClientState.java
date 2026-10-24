package com.vnap.client;

import com.vnap.network.ScriptShakePayload;
import com.vnap.network.VillageMoodPayload;
import org.jspecify.annotations.Nullable;

/** What the client last heard of the village: its mood (the mood bar) and a rite's shaking ground. */
public final class VillageClientState {
    public static @Nullable VillageMoodPayload lastMood;
    /** {@code Util.getMillis()} when the mood, its village or its rite last changed: the bar shows a while after. */
    public static long moodChangedAt;
    public static float shakeIntensity;

    private VillageClientState() {
    }

    public static void onMood(VillageMoodPayload payload) {
        VillageMoodPayload was = lastMood;
        // the server repeats the mood every few seconds: only a real change brings the bar up
        if (was == null || was.villageId() != payload.villageId() || Math.abs(was.mood() - payload.mood()) >= 0.5F
                || !was.script().equals(payload.script())) {
            moodChangedAt = net.minecraft.Util.getMillis();
        }
        lastMood = payload;
    }

    public static void onShake(ScriptShakePayload payload) {
        shakeIntensity = Math.max(shakeIntensity, payload.intensity());
    }
}
