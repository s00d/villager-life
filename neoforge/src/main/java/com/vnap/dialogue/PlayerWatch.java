package com.vnap.dialogue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What the villagers make of a player near them, every half second: a greeting when it comes up, a word on how it
 * looks and what it holds, staring, standing still, trampling crops.
 */
final class PlayerWatch {
	private static final double RANGE = 16.0;
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final Map<UUID, Watch> WATCHES = new HashMap<>();
	/** Players some villager already greeted since they last walked out of sight of all of them. */
	private static final Set<UUID> GREETED = new HashSet<>();

	static final class Watch {
		private Vec3 lastAt = Vec3.ZERO;
		private int stillFor;
		private int staredFor;
		private int breaksInARow;
		private long lastBreak = Long.MIN_VALUE / 2;
		private @Nullable String mode;
		private @Nullable String state;
		private BlockPos groundAt = BlockPos.ZERO;
		private String ground = "";

		/** "Break Multiple Blocks" after four quick breaks. */
		boolean breakingAway() {
			breaksInARow = Speech.now() - lastBreak <= 30L ? breaksInARow + 1 : 1;
			lastBreak = Speech.now();
			return breaksInARow >= 4;
		}
	}

	private PlayerWatch() {
	}

	static Watch of(ServerPlayer player) {
		return WATCHES.computeIfAbsent(player.getUUID(), ignored -> new Watch());
	}

	/** A spectator is no guest: it is forgotten, and greeted afresh when it comes back. */
	static void spectating(ServerPlayer player) {
		GREETED.remove(player.getUUID());
		Watch watch = of(player);
		watch.lastAt = player.position();
		watch.mode = "spectator";
		watch.state = null;
		watch.stillFor = 0;
		watch.staredFor = 0;
	}

	static void tick(ServerLevel level, ServerPlayer player) {
		Watch watch = of(player);
		String mode = player.gameMode.getGameModeForPlayer().getName();
		boolean modeChanged = watch.mode != null && !watch.mode.equals(mode);
		watch.mode = mode;
		Vec3 step = player.position().subtract(watch.lastAt);
		watch.stillFor = step.horizontalDistanceSqr() < 0.0004 && Math.abs(step.y) < 0.01 ? watch.stillFor + 10 : 0;
		watch.lastAt = player.position();
		BlockPos below = player.blockPosition().below();
		String ground = Near.path(level.getBlockState(below));
		if (below.equals(watch.groundAt) && watch.ground.equals("farmland") && ground.equals("dirt")) {
			WorldEvents.observed(level, player, player.position(), "Trample Crops", Speech.SOON);
		}
		watch.groundAt = below;
		watch.ground = ground;

		List<Villager> awake = Near.villagers(level, player.position(), RANGE).stream().filter(v -> !v.isSleeping()).toList();
		awake.forEach(Conditions::check);
		Villager adult = Near.closest(awake.stream().filter(v -> !v.isBaby() && v.hasLineOfSight(player)).toList(), player.position());
		if (adult == null) {
			child(level, player, awake);
			return;
		}
		boolean firstSight = GREETED.add(player.getUUID());
		if (player.getBoundingBox().inflate(0.15).intersects(adult.getBoundingBox()) && step.horizontalDistanceSqr() > 0.002) {
			boolean boat = adult.getVehicle() != null && Near.path(adult.getVehicle()).contains("boat");
			if (Speech.say(adult, boat ? "zvbnea" : "ajexrq", "nudge:" + adult.getUUID(), Speech.SOON, player)) return;
		}
		if (modeChanged && Speech.sayAsAdult(adult, mode.equals("creative") ? "ohtblt" : "fhhqxg",
				"gamemode:" + player.getUUID() + ":" + mode, Speech.SOON, player)) return;
		if (firstSight && greet(player, adult)) return;

		Vec3 toHim = adult.getEyePosition().subtract(player.getEyePosition()).normalize();
		watch.staredFor = player.getLookAngle().dot(toHim) > 0.985 ? watch.staredFor + 10 : 0;
		String pair = player.getUUID() + ":" + adult.getUUID();
		if (watch.staredFor >= 60 && Speech.titleAsAdult(adult, "Stare at a Villager", "stare:" + pair, Speech.SELDOM, player)) {
			watch.staredFor = 0;
			return;
		}
		if (Speech.now() % 400L == 0L && watch.stillFor >= 2400
				&& Speech.titleAsAdult(adult, "Stand Completely Still", "still:" + player.getUUID(), Speech.SELDOM, player)) {
			watch.stillFor = 0;
			return;
		}
		String state = looks(player);
		boolean newState = state != null && !state.equals(watch.state);
		watch.state = state;
		if (newState && state != null && Speech.titleAsAdult(adult, state, "player_context:" + player.getUUID() + ":" + state, Speech.SELDOM, player)) return;
		long players = level.players().stream().filter(other -> !other.isSpectator() && other.distanceToSqr(adult) <= 64.0).count();
		if (players >= 2 && Speech.sayAsAdult(adult, "cstyvg", "player_crowd:" + adult.getUUID(), Speech.SELDOM, player)) return;
		String place = Surroundings.place(level, adult);
		if (place != null && Speech.title(adult, place, "environment:" + adult.getUUID() + ":" + place, Speech.SELDOM, null)) return;
		if (Speech.now() % 40L == 0L && Surroundings.remark(level, adult)) return;
		if (Speech.now() % 200L == 0L && adult.getRandom().nextInt(3) == 0) {
			String hour = Surroundings.partOfDay(level);
			if (Speech.title(adult, hour, "time:" + adult.getUUID() + ":" + hour, 6000L, null)) return;
		}
		if (Speech.now() % 100L == 0L && adult.getRandom().nextBoolean() && Near.moving(adult)) {
			String line = Voices.mayorMan(adult) ? Cast.MAYOR.idles() : Surroundings.idle(adult);
			Speech.say(adult, line, "idle:" + adult.getUUID() + (Voices.mayorMan(adult) ? "" : ":" + line), Speech.SELDOM, null);
		}
	}

	/** No grown-up sees the player: the nearest child that does greets it or remarks on something. */
	private static void child(ServerLevel level, ServerPlayer player, List<Villager> awake) {
		Villager kid = Near.closest(awake.stream().filter(v -> v.isBaby() && v.hasLineOfSight(player)).toList(), player.position());
		if (kid == null) {
			GREETED.remove(player.getUUID());
			return;
		}
		boolean firstSight = GREETED.add(player.getUUID());
		if (Speech.now() % 40L == 0L && Surroundings.remark(level, kid)) return;
		String line = player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) ? "fzyrfm" : kid.getPlayerReputation(player) < -75 ? "jfuftm" : "wtuguc";
		if (firstSight) Speech.say(kid, line, "player_greeting:" + player.getUUID(), Speech.SELDOM, player);
	}

	private static boolean greet(ServerPlayer player, Villager adult) {
		String key = "player_greeting:" + player.getUUID();
		if (Voices.mayorMan(adult)) {
			if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) && Speech.sayAsAdult(adult, "gnetsk", key, Speech.SELDOM, player)) return true;
			return Speech.say(adult, Cast.MAYOR.greets(), key, Speech.SELDOM, player);
		}
		return Speech.say(adult, byReputation(adult, player), key, Speech.SELDOM, player);
	}

	private static String byReputation(Villager villager, ServerPlayer player) {
		if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) return "gnetsk";
		int standing = villager.getPlayerReputation(player);
		if (standing < -225) return Near.path(player.getMainHandItem()).endsWith("_sword") ? "stuirs" : "zstdjn";
		if (standing < -75) return "tfzlsw";
		if (standing >= 75) return "kcbenk";
		return standing >= 25 ? "omgcte" : Cast.VILLAGER.greets();
	}

	/** What stands out about the player now (flying, hurt, an effect, what it holds or wears); null for nothing. */
	private static @Nullable String looks(ServerPlayer player) {
		boolean creativeFlight = player.getAbilities().flying && player.gameMode.getGameModeForPlayer().getName().equals("creative");
		boolean sneaking = player.isShiftKeyDown() && player.getDeltaMovement().horizontalDistanceSqr() > 0.0001;
		String motion = player.isFallFlying() ? "Glide with Elytra" : creativeFlight ? "Fly in Creative Mode" : sneaking ? "Crouch-Walk"
				: player.getHealth() / player.getMaxHealth() <= 0.3F ? "Low Health" : null;
		if (motion != null) return motion;
		String effect = effect(player);
		if (effect != null) return effect;
		if (Near.path(player.level().getBlockState(player.blockPosition().below())).endsWith("_bed")) return "Stand on a Villager's Bed";
		if (player.level().isThundering()) return "Thunderstorm";
		if (player.isOnFire()) return "Player on Fire";
		if (player.isSwimming() || player.isUnderWater()) return "Player Swimming";
		String worn = ModFlavorMatchers.wearTitle(player);
		if (worn != null) return worn;
		String held = holding(player.getMainHandItem());
		if (held == null) held = holding(player.getOffhandItem());
		if (held == null) held = ModFlavorMatchers.holdTitle(player.getMainHandItem());
		if (held == null) held = ModFlavorMatchers.holdTitle(player.getOffhandItem());
		if (held != null) return held;
		String ride = riding(player);
		if (ride != null) return ride;
		ItemStack hand = player.getMainHandItem();
		if (hand.isDamageableItem() && hand.getDamageValue() >= hand.getMaxDamage() * 0.85F) return "Hold a Nearly Broken Item";
		return armour(player);
	}

	private static @Nullable String effect(ServerPlayer player) {
		// in this order: the first effect the player has names it
		var named = List.of(Map.entry(MobEffects.INVISIBILITY, "Invisibility"), Map.entry(MobEffects.DARKNESS, "Darkness"),
				Map.entry(MobEffects.NIGHT_VISION, "Night Vision"), Map.entry(MobEffects.WATER_BREATHING, "Water Breathing"),
				Map.entry(MobEffects.MOVEMENT_SPEED, "Swiftness"), Map.entry(MobEffects.MOVEMENT_SLOWDOWN, "Slowness"),
				Map.entry(MobEffects.DAMAGE_BOOST, "Strength"), Map.entry(MobEffects.WEAKNESS, "Weakness"),
				Map.entry(MobEffects.HUNGER, "Hunger"), Map.entry(MobEffects.CONFUSION, "Nausea"),
				Map.entry(MobEffects.BAD_OMEN, "Bad Omen"), Map.entry(MobEffects.RAID_OMEN, "Bad Omen"),
				Map.entry(MobEffects.OOZING, "Oozing"));
		for (var effect : named) {
			if (player.hasEffect(effect.getKey())) return effect.getValue();
		}
		return player.getActiveEffects().size() > 1 ? "Multiple Status Effects" : null;
	}

	private static @Nullable String holding(ItemStack stack) {
		if (stack.isEmpty()) return null;
		return switch (Near.path(stack)) {
			case "spyglass" -> "Hold a Spyglass";
			case "totem_of_undying" -> "Hold a Totem";
			case "shield" -> "Hold a Shield";
			case "map", "filled_map" -> "Hold a Map";
			case "ender_eye" -> "Hold an Ender Eye";
			case "writable_book", "written_book" -> "Hold a Book and Quill";
			case "goat_horn" -> "Hold a Goat Horn";
			case "brush" -> "Hold a Brush";
			case "recovery_compass" -> "Hold a Recovery Compass";
			default -> null;
		};
	}

	/** A modded ride: a Create train or contraption, something that flies. */
	private static @Nullable String riding(ServerPlayer player) {
		Entity ride = player.getVehicle();
		if (ride == null) return null;
		String modded = ModFlavorMatchers.nearbyEntityTitle(ride);
		if (modded != null && (modded.equals("See a Train Pass") || modded.contains("Contraption") || modded.contains("Train"))) {
			return "Ride a Create Train or Contraption";
		}
		String path = Near.path(ride);
		boolean vanilla = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(ride.getType()).getNamespace().equals("minecraft");
		if (!vanilla && (path.contains("contraption") || path.contains("train") || path.contains("carriage") || path.contains("seat"))) {
			return "Ride a Create Train or Contraption";
		}
		return path.contains("plane") || path.contains("airship") ? "See a Flying Contraption" : null;
	}

	private static @Nullable String armour(ServerPlayer player) {
		int pieces = 0;
		Set<String> kinds = new HashSet<>();
		for (EquipmentSlot slot : ARMOUR) {
			ItemStack worn = player.getItemBySlot(slot);
			if (worn.isEmpty()) continue;
			pieces++;
			String path = Near.path(worn);
			int cut = path.indexOf('_');
			kinds.add(cut > 0 ? path.substring(0, cut) : path);
		}
		if (pieces == 0) return null;
		if (pieces < 4) return "Wear Armor";
		if (kinds.size() > 1) return "Wear Mixed Armor";
		if (kinds.contains("iron")) return "Wear Full Iron Armor";
		return kinds.contains("diamond") || kinds.contains("netherite") ? "Wear High-Level Armor" : "Wear Armor";
	}

	static void forget(UUID player) {
		WATCHES.remove(player);
		GREETED.remove(player);
	}

	static void clearGreetings() {
		GREETED.clear();
	}

	static void clear() {
		WATCHES.clear();
		GREETED.clear();
	}
}
