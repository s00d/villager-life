package com.vnap.client;

import com.vnap.dialogue.DialogueCatalog;
import com.vnap.dialogue.VoicePitch;
import com.vnap.network.DialogueAnimationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Plays the voice clip a line packet names, in the language the jar was built with (one jar per voice). A packet for an
 * entity the client has not got yet waits for it up to five seconds; a missing clip is silence.
 */
public final class DialogueSoundState {
	private static final long WAIT_NANOS = 5_000_000_000L;
	/** Death lines stay where they were said: the body is gone a moment later. */
	private static final Set<String> STAY_PUT = Set.of("hivgme", "ecslqo");
	private static final Map<UUID, Playing> PLAYING = new HashMap<>();
	private static final Map<UUID, Waiting> WAITING = new HashMap<>();

	private record Playing(SoundInstance sound, boolean follows, long endsAt) {
	}

	private record Waiting(DialogueAnimationPayload line, long giveUpAt) {
	}

	private DialogueSoundState() {
	}

	public static void start(DialogueAnimationPayload line) {
		Minecraft game = Minecraft.getInstance();
		stop(game, line.entityId());
		WAITING.remove(line.entityId());
		if (line.groupId().isEmpty() || game.level == null) return;
		if (!play(game, line)) WAITING.put(line.entityId(), new Waiting(line, System.nanoTime() + WAIT_NANOS));
	}

	/** This entity's line sounds now (a speaker nods, a crying newborn wriggles). */
	public static boolean speaking(UUID entity) {
		return PLAYING.containsKey(entity);
	}

	/** False only while the speaker is not there yet: the line should wait for it. */
	private static boolean play(Minecraft game, DialogueAnimationPayload line) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(line.groupId());
		DialogueCatalog.DialogueVariant take = group == null ? null : group.variant(line.variantIndex());
		if (take == null) return true;
		Entity speaker = ClientEntities.find(line.entityId());
		if (speaker == null) return false;
		SoundEvent clip = take.voice();
		if (!exists(game, clip)) return true;
		boolean follows = speaker.isAlive() && !speaker.isSilent() && !STAY_PUT.contains(line.groupId());
		float pitch = VoicePitch.of(speaker);
		float volume = 1.0F;
		SoundInstance sound = follows
				? new EntityBoundSoundInstance(clip, SoundSource.NEUTRAL, volume, pitch, speaker, speaker.getRandom().nextLong())
				: new SimpleSoundInstance(clip, SoundSource.NEUTRAL, volume, pitch, RandomSource.create(), speaker.getX(), speaker.getY(), speaker.getZ());
		game.getSoundManager().play(sound);
		PLAYING.put(line.entityId(), new Playing(sound, follows, System.nanoTime() + line.durationTicks() * 50_000_000L));
		return true;
	}

	/** A clip that is not in the resources (not voiced yet) plays as nothing rather than as the missing-sound beep. */
	private static boolean exists(Minecraft game, SoundEvent clip) {
		WeighedSoundEvents events = game.getSoundManager().getSoundEvent(com.vnap.Mc.id(clip));
		Sound sound = events == null ? null : events.getSound(RandomSource.create());
		return sound != null && !sound.equals(SoundManager.EMPTY_SOUND) && !sound.equals(SoundManager.INTENTIONALLY_EMPTY_SOUND)
				&& game.getResourceManager().getResource(sound.getPath()).isPresent();
	}

	public static void tick(Minecraft game) {
		if (game.level == null || game.player == null) {
			clear(game);
			return;
		}
		long now = System.nanoTime();
		WAITING.values().removeIf(waiting -> now >= waiting.giveUpAt() || play(game, waiting.line()));
		PLAYING.entrySet().removeIf(entry -> {
			Playing playing = entry.getValue();
			if (playing.follows()) {
				Entity speaker = ClientEntities.find(entry.getKey());
				if (speaker == null || !speaker.isAlive()) {
					game.getSoundManager().stop(playing.sound());
					return true;
				}
			}
			// the clip plays to its end even past the line's length: cutting it there lost the last words
			return now >= playing.endsAt() && !game.getSoundManager().isActive(playing.sound());
		});
	}

	public static void clear(Minecraft game) {
		WAITING.clear();
		PLAYING.values().forEach(playing -> game.getSoundManager().stop(playing.sound()));
		PLAYING.clear();
	}

	private static void stop(Minecraft game, @Nullable UUID entity) {
		Playing playing = PLAYING.remove(entity);
		if (playing != null) game.getSoundManager().stop(playing.sound());
	}
}
