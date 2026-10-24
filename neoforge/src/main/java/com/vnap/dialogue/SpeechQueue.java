package com.vnap.dialogue;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Lines that wait their turn: the rest of a conversation, an answer, a child's echo. A line due while somebody in
 * earshot is talking waits up to {@link #PATIENCE} ticks for the floor, then is said anyway.
 */
final class SpeechQueue {
	private static final long PATIENCE = 200L;
	private static final double ECHO_RANGE = 10.0;
	private static final List<Waiting> WAITING = new ArrayList<>();

	private record Waiting(ServerLevel level, UUID speaker, String group, @Nullable UUID listener, long due, boolean asAdult) {
		boolean involves(UUID id) {
			return speaker.equals(id) || id.equals(listener);
		}
	}

	private SpeechQueue() {
	}

	static void add(ServerLevel level, Entity speaker, String group, @Nullable Entity listener, long due, boolean asAdult) {
		WAITING.add(new Waiting(level, speaker.getUUID(), group, listener == null ? null : listener.getUUID(), due, asAdult));
	}

	static void add(ServerLevel level, Entity speaker, String group, @Nullable Entity listener, long due) {
		add(level, speaker, group, listener, due, false);
	}

	/** Lines due now; the ones said are taken off first (saying a line can queue an echo on this very list). */
	static void tick() {
		List<Waiting> due = new ArrayList<>();
		for (Iterator<Waiting> it = WAITING.iterator(); it.hasNext(); ) {
			Waiting line = it.next();
			if (line.due() > Speech.now()) continue;
			boolean waitsForFloor = line.level().getEntity(line.speaker()) instanceof LivingEntity speaker && speaker.isAlive()
					&& Speech.now() - line.due() < PATIENCE && Speech.floorTaken(line.level(), speaker);
			if (waitsForFloor) continue;
			it.remove();
			due.add(line);
		}
		for (Waiting line : due) {
			if (!(line.level().getEntity(line.speaker()) instanceof LivingEntity speaker) || !speaker.isAlive()) continue;
			DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(line.group());
			if (group == null) continue;
			Entity listener = line.listener() == null ? null : line.level().getEntity(line.listener());
			String key = "queued:" + line.speaker() + ":" + line.group();
			if (Speech.speak(speaker, group, key, 1L, listener, line.asAdult()) && listener instanceof LivingEntity living) {
				Speech.listen(living, speaker, group.durationTicks());
			}
		}
	}

	/** A child near a grown-up who just spoke answers it now and then (60%, not the same title twice soon). */
	static void echo(ServerLevel level, Villager grownUp, DialogueCatalog.DialogueGroup said, long after) {
		DialogueCatalog.DialogueGroup answer = Voices.echoOf(said);
		if (answer == null) return;
		Villager child = Near.villager(level, grownUp.position(), ECHO_RANGE, v -> v.isBaby() && !v.isSleeping()
				&& !Speech.busy(v) && !Infants.infant(v) && v.hasLineOfSight(grownUp));
		if (child == null) return;
		String key = "child_echo:" + child.getUUID() + ":" + answer.title();
		if (!Speech.ready(key, Speech.SOON) || level.getRandom().nextFloat() > 0.6F) return;
		Speech.mark(key);
		add(level, child, answer.id(), grownUp, Speech.now() + after + 4L);
	}

	/** Lines in a row, the two taking turns from the second one on (the first is being said now). */
	static void conversation(ServerLevel level, Villager first, Villager second, List<String> lines) {
		long due = Speech.now() + DialogueCatalog.durationTicks(lines.getFirst()) + 2L;
		for (int i = 1; i < lines.size(); i++) {
			boolean secondsTurn = i % 2 == 1;
			add(level, secondsTurn ? second : first, lines.get(i), secondsTurn ? first : second, due);
			due += DialogueCatalog.durationTicks(lines.get(i)) + 2L;
		}
	}

	static void forget(UUID id) {
		WAITING.removeIf(line -> line.involves(id));
	}

	static String describe(UUID id) {
		StringBuilder out = new StringBuilder();
		for (Waiting line : WAITING) if (line.speaker().equals(id)) out.append(" queued ").append(line.group());
		return out.toString();
	}

	static void clear() {
		WAITING.clear();
	}
}
