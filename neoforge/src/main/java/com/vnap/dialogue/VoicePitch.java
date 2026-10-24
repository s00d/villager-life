package com.vnap.dialogue;

import com.vnap.entity.VillagerLifeData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Every villager plays the shared voice clips at its own pitch, fixed by its UUID, so one recording sounds like
 * many people and a villager sounds the same each time. Minecraft pitch is playback rate: a clip at pitch p lasts
 * 1/p as long. The server stretches the line's duration (floor, busy, payload); the client stretches only how
 * fast the mouth and the subtitles move through it.
 */
public final class VoicePitch {
	private static final float SPREAD = 0.08F;
	private static final float ELDER = 0.94F;

	private VoicePitch() {
	}

	public static float of(@Nullable Entity entity) {
		if (!(entity instanceof AbstractVillager)) return 1.0F;
		UUID id = entity.getUUID();
		long bits = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
		float unit = (float) ((bits >>> 24) & 0xFFFF) / 0xFFFF;
		float pitch = 1.0F + (unit * 2.0F - 1.0F) * SPREAD;
		// an elder's voice is a little lower (the flag is synced, so client and server agree)
		return entity instanceof VillagerLifeData data && data.vnap$elder() ? pitch * ELDER : pitch;
	}

	public static long stretchTicks(long ticks, float pitch) {
		return (long) Math.ceil(ticks / pitch);
	}
}
