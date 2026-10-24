package com.vnap.dialogue;

import com.vnap.village.life.LifeBrain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Villagers among themselves, every five seconds near a player: two who stand together long enough start a
 * conversation, a crowd notices itself, children chase each other, a grown-up coos at a child.
 */
final class Chats {
	private static final double AROUND = 24.0;
	/** Two men walking together; the first line of each starts it. */
	private static final List<List<String>> STROLL = List.of(
			List.of("gmrypkswxeva", "gmrypkbayahw", "gmrypkmudlec"),
			List.of("gmrypkoallbt", "gmrypkfobzlt", "gmrypkcljvls"),
			List.of("gmrypkhiqnpi", "gmrypkvkuidc", "gmrypkhnvsiu", "gmrypkvswnrg"),
			List.of("gmrypkmwtiaf", "gmrypkgougka"));
	private static final List<String> BY_THE_FIRE = List.of("wrswgiswxeva", "wrswgibayahw", "wrswgimudlec", "wrswgitvewwu",
			"wrswgisrlwzw", "wrswgicsmkgk");
	private static final List<String> ABOUT_THE_PLAYER = List.of("wrjbddswxeva", "wrjbddbayahw", "wrjbddmudlec", "wrjbddtvewwu",
			"wrjbddsrlwzw");
	private static final String SAME_BOAT = "zqfvby";
	private static final String SHORT_GOSSIP = "wrjbdd";
	private static final String MEET_THE_MAYOR = "lyatyf";
	/** How long each pair has stood together (ticks, in steps of 100). */
	private static final Map<String, Integer> TOGETHER = new HashMap<>();

	private Chats() {
	}

	static void tick(ServerLevel level) {
		Set<UUID> looked = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			for (Villager villager : Near.villagers(level, player.position(), AROUND)) {
				if (looked.add(villager.getUUID())) VillagerMoments.check(villager);
			}
		}
		Set<String> pairs = new HashSet<>();
		for (ServerPlayer player : level.players()) aroundPlayer(level, player, pairs);
	}

	private static void aroundPlayer(ServerLevel level, ServerPlayer player, Set<String> pairs) {
		List<Villager> all = Near.villagers(level, player.position(), AROUND);
		List<Villager> adults = all.stream().filter(v -> !v.isBaby() && !v.isSleeping() && !Conditions.suffering(v)).toList();
		if (adults.size() >= 8 && Speech.sayAsAdult(adults.getFirst(), "kzemrz", "villager_crowd:" + player.getUUID(), Speech.SELDOM, player)) return;
		Villager host = adults.stream().filter(v -> !Voices.mayorMan(v)).findFirst().orElse(null);
		Villager guest = host == null ? null : partner(host, adults);
		if (host != null && guest != null && adults.size() >= 3 && Speech.say(host, "ebfifz", "gathering:" + player.getUUID(), Speech.SELDOM, guest)) return;
		for (Villager one : adults) {
			Villager other = partner(one, adults);
			if (other == null) continue;
			String pair = pair(one.getUUID(), other.getUUID());
			if (!pairs.add(pair)) continue;
			boolean oneFirst = one.getUUID().compareTo(other.getUUID()) <= 0;
			Villager first = oneFirst ? one : other;
			Villager second = oneFirst ? other : one;
			if (Speech.busy(first) || Speech.busy(second) || LifeBrain.busy(first) || LifeBrain.busy(second)) continue;
			if (TOGETHER.merge(pair, 100, Integer::sum) < 200) continue;
			// the men's conversations are written for two men; a pair with a woman has its own talk
			if (Family.female(first) || Family.female(second)) {
				if (Family.pairChat(level, first, second, "pair:" + pair, Speech.SELDOM)) TOGETHER.put(pair, 0);
				continue;
			}
			if (talk(level, player, first, second, pair)) return;
		}
		children(all);
	}

	/** Starts a conversation of the pair. True when this player's round is over (it spoke, or a mayor was met). */
	private static boolean talk(ServerLevel level, ServerPlayer player, Villager first, Villager second, String pair) {
		boolean firstMayor = Voices.mayorMan(first);
		boolean secondMayor = Voices.mayorMan(second);
		if (firstMayor != secondMayor) {
			Villager speaker = firstMayor ? second : first;
			Villager mayor = firstMayor ? first : second;
			if (Speech.say(speaker, MEET_THE_MAYOR, "meet:" + pair, Speech.SELDOM, mayor)) {
				Speech.listen(mayor, speaker, 80L);
				TOGETHER.put(pair, 0);
			}
			return true;
		}
		long round = Speech.now() / Speech.SELDOM;
		boolean fire = Near.block(level, first.blockPosition(), "campfire", 4) && Near.block(level, second.blockPosition(), "campfire", 4);
		boolean grudge = first.getPlayerReputation(player) < -75 || second.getPlayerReputation(player) < -75;
		boolean standing = first.getDeltaMovement().horizontalDistanceSqr() + second.getDeltaMovement().horizontalDistanceSqr() < 0.0004;
		List<String> stroll = STROLL.get(Math.floorMod(pair.hashCode() + (int) round, STROLL.size()));
		List<String> lines;
		if (fire) lines = BY_THE_FIRE;
		else if (first.getVehicle() != null && first.getVehicle().equals(second.getVehicle())) lines = List.of(SAME_BOAT);
		else if (grudge && standing) lines = Math.floorMod(pair.hashCode() + (int) round, 2) == 0 ? List.of(SHORT_GOSSIP) : ABOUT_THE_PLAYER;
		else lines = stroll;
		String opening = lines.getFirst();
		if (!Speech.say(first, opening, "conversation:" + pair + ":" + opening, Speech.SELDOM, second)) return false;
		Speech.listen(second, first, DialogueCatalog.durationTicks(opening));
		if (lines.size() > 1) SpeechQueue.conversation(level, first, second, lines);
		TOGETHER.put(pair, 0);
		return true;
	}

	/** Two children running about chase each other; a grown-up near a child says something to it. */
	private static void children(List<Villager> all) {
		List<Villager> kids = all.stream().filter(v -> v.isBaby() && !v.isSleeping() && !Infants.infant(v)).toList();
		Villager adult = all.stream().filter(v -> !v.isBaby() && !v.isSleeping() && !Conditions.suffering(v)).findFirst().orElse(null);
		if (kids.size() >= 2) {
			Villager a = kids.get(0);
			Villager b = kids.get(1);
			if (a.distanceToSqr(b) <= 64.0 && a.getDeltaMovement().horizontalDistanceSqr() + b.getDeltaMovement().horizontalDistanceSqr() > 0.01) {
				Speech.say(a, "rfnirh", "baby_chase:" + pair(a.getUUID(), b.getUUID()), Speech.SELDOM, b);
			}
		}
		Villager kid = kids.isEmpty() ? null : kids.getFirst();
		if (adult != null && kid != null && adult.distanceToSqr(kid) <= 64.0) {
			Speech.sayAsAdult(adult, "pbmrxx", "see_baby:" + adult.getUUID() + ":" + kid.getUUID(), Speech.SELDOM, kid);
		}
	}

	private static @Nullable Villager partner(Villager villager, List<Villager> around) {
		return Near.closest(around.stream().filter(other -> !other.equals(villager) && !Speech.busy(other)
				&& villager.distanceToSqr(other) <= 6.25 && villager.hasLineOfSight(other)).toList(), villager.position());
	}

	private static String pair(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
	}

	static void forget(UUID id) {
		String text = id.toString();
		TOGETHER.keySet().removeIf(pair -> pair.contains(text));
	}

	static void clear() {
		TOGETHER.clear();
	}
}
