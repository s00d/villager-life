package com.vnap.dialogue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A villager says goodnight before it lies down, and grumbles when a player wakes it. */
public final class Bedtime {
	private static final String GOODNIGHT = "ioxtmt";
	private static final String WOKEN = "viwaal";
	private static final String GOOD_MORNING = "ctptjt";
	private static final Map<UUID, Turning> TURNING_IN = new HashMap<>();
	private static final Map<UUID, Waking> WAKING = new HashMap<>();
	private static final Map<UUID, UUID> WOKEN_BY = new HashMap<>();
	/** Lying down for real: the sleep mixin lets the call through. */
	private static final Set<UUID> LYING_DOWN = new HashSet<>();

	private static final class Turning {
		private final ServerLevel level;
		private final BlockPos bed;
		private long at;
		private boolean saidIt;

		private Turning(ServerLevel level, BlockPos bed, long at, boolean saidIt) {
			this.level = level;
			this.bed = bed;
			this.at = at;
			this.saidIt = saidIt;
		}
	}

	private record Waking(ServerLevel level, String line, @Nullable UUID by, long at) {
	}

	private Bedtime() {
	}

	/**
	 * Called as a villager is about to lie down. True holds it up: it says goodnight first (or finishes what it is
	 * saying) and lies down in {@link #tick} once the line is over.
	 */
	public static boolean delay(Villager villager, BlockPos bed) {
		UUID id = villager.getUUID();
		if (LYING_DOWN.remove(id)) return false;
		if (!(villager.level() instanceof ServerLevel level) || villager.isSleeping()) return false;
		if (TURNING_IN.containsKey(id)) return true;
		long talking = Speech.endsAt(id);
		if (talking > Speech.now()) {
			TURNING_IN.put(id, new Turning(level, bed.immutable(), talking + 1L, false));
			VillagerMoments.awake(id);
			return true;
		}
		if (!goodnight(villager, bed)) return false;
		TURNING_IN.put(id, new Turning(level, bed.immutable(), lineOver(id), true));
		VillagerMoments.awake(id);
		return true;
	}

	private static boolean goodnight(Villager villager, BlockPos bed) {
		return Speech.say(villager, GOODNIGHT, "bed:" + villager.getUUID(), Speech.SELDOM, Vec3.atCenterOf(bed));
	}

	private static long lineOver(UUID id) {
		long ends = Speech.endsAt(id);
		return ends == Long.MIN_VALUE ? Speech.now() + DialogueCatalog.durationTicks(GOODNIGHT) : ends + 1L;
	}

	/** A player (any entity) woke up; for a villager this comes from the sleep mixin. */
	public static void wokeUp(LivingEntity sleeper) {
		if (sleeper instanceof ServerPlayer player) {
			boolean armoured = false;
			for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				armoured |= !player.getItemBySlot(slot).isEmpty();
			}
			WorldEvents.observed(com.vnap.Mc.serverLevel(player), player, player.position(),
					armoured ? "Wake Up in Armor" : "Player Wakes Up", Speech.SELDOM);
			return;
		}
		if (!(sleeper instanceof Villager villager)) return;
		UUID id = villager.getUUID();
		boolean shaken = WOKEN_BY.containsKey(id);
		UUID by = WOKEN_BY.remove(id);
		TURNING_IN.remove(id);
		LYING_DOWN.remove(id);
		Speech.hush(villager);
		if (!(villager.level() instanceof ServerLevel level)) {
			WAKING.remove(id);
			return;
		}
		boolean early = shaken || night(villager, level);
		WAKING.put(id, new Waking(level, early ? WOKEN : GOOD_MORNING, early ? by : null, Speech.now() + 4L));
		VillagerMoments.awake(id);
	}

	/** A player clicked or hit a sleeping villager: it gets up, cross. */
	static void shake(Villager villager, Player player) {
		WOKEN_BY.put(villager.getUUID(), player.getUUID());
		villager.stopSleeping();
		wokeUp(villager);
	}

	private static boolean night(Villager villager, ServerLevel level) {
		long time = Family.timeOfDay(level);
		return VillagerMoments.profession(villager).equals("nitwit") ? time >= 14000L || time < 2000L : time >= 12000L;
	}

	static void tick() {
		TURNING_IN.entrySet().removeIf(entry -> {
			Turning turning = entry.getValue();
			long talking = Speech.endsAt(entry.getKey());
			if (talking >= turning.at) turning.at = talking + 1L;
			if (turning.at > Speech.now()) return false;
			if (!(turning.level.getEntity(entry.getKey()) instanceof Villager villager) || !villager.isAlive() || villager.isSleeping()
					|| villager.distanceToSqr(Vec3.atCenterOf(turning.bed)) > 16.0 || !Near.path(turning.level.getBlockState(turning.bed)).endsWith("_bed")) {
				return true;
			}
			if (!turning.saidIt && goodnight(villager, turning.bed)) {
				turning.saidIt = true;
				turning.at = lineOver(villager.getUUID());
				return false;
			}
			LYING_DOWN.add(villager.getUUID());
			try {
				villager.startSleeping(turning.bed);
			} finally {
				LYING_DOWN.remove(villager.getUUID());
			}
			return true;
		});
		WAKING.entrySet().removeIf(entry -> {
			Waking waking = entry.getValue();
			if (waking.at() > Speech.now()) return false;
			if (!(waking.level().getEntity(entry.getKey()) instanceof Villager villager) || !villager.isAlive() || villager.isSleeping()) return true;
			if (Speech.busy(villager)) return false;
			Object toward = waking.by() == null ? null : waking.level().getEntity(waking.by());
			return Speech.sayAsAdult(villager, waking.line(), "wake:" + entry.getKey() + ":" + waking.line(), 1L, toward);
		});
	}

	static void forget(UUID id) {
		TURNING_IN.remove(id);
		WAKING.remove(id);
		WOKEN_BY.remove(id);
		LYING_DOWN.remove(id);
	}

	static void clear() {
		TURNING_IN.clear();
		WAKING.clear();
		WOKEN_BY.clear();
		LYING_DOWN.clear();
	}
}
