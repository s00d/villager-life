package com.vnap.dialogue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Something wrong with a villager that lasts (poison, fire, stuck in a wall): it keeps saying so, nothing else, and says
 * it is fine once it is over.
 */
final class Conditions {
	private static final String POISON = "onindz";
	private static final String SLOW = "xemyaj";
	private static final String WEAK = "yebifs";
	private static final String LAVA = "elryje";
	private static final String FIRE = "etkxko";
	private static final String FROZEN = "igebly";
	static final String WALLED = "vnaodx";
	private static final String CHILD_HURT = "ecslqo";
	private static final String CHILD_HIT = "ahcvzd";
	private static final String CHILD_RELIEF = "wsxfok";
	private static final String RELIEF = "wbbxpo";
	private static final String OUT_OF_THE_WALL = "fxbysi";
	/** A slow complaint (poison, slowness, weakness) lasts long: said again only after a while, not every breath. */
	private static final Set<String> LINGERING = Set.of(POISON, SLOW, WEAK);
	private static final long LINGER = 400L;

	/** What each villager has been through since it was last fine. */
	private static final Map<UUID, Set<String>> SUFFERED = new HashMap<>();
	private static final Map<UUID, Integer> NEXT = new HashMap<>();
	private static final Map<UUID, Relief> RELIEF_DUE = new HashMap<>();

	private record Relief(ServerLevel level, String line, long due) {
	}

	private Conditions() {
	}

	static List<String> of(Villager villager) {
		List<String> now = new ArrayList<>(3);
		if (villager.hasEffect(MobEffects.POISON)) now.add(POISON);
		// the town's illness (TownStory) slows without particles and says its own lines
		MobEffectInstance slow = villager.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		if (slow != null && slow.isVisible()) now.add(SLOW);
		if (villager.hasEffect(MobEffects.WEAKNESS)) now.add(WEAK);
		if (villager.isInLava()) now.add(LAVA);
		else if (villager.isOnFire()) now.add(FIRE);
		if (villager.isFullyFrozen()) now.add(FROZEN);
		if (villager.isInWall()) now.add(WALLED);
		return now;
	}

	static boolean suffering(Villager villager) {
		return !of(villager).isEmpty();
	}

	/** While a villager suffers it says only that (a child cries, a mayor groans); after it, only its relief. */
	static boolean allows(Villager villager, String line) {
		List<String> now = of(villager);
		if (now.isEmpty()) {
			Relief relief = RELIEF_DUE.get(villager.getUUID());
			return relief == null || relief.line().equals(line);
		}
		if (now.contains(line)) return true;
		if (villager.isBaby()) return line.equals(CHILD_HIT) || line.equals(CHILD_HURT);
		return Voices.mayorMan(villager) && (line.equals(Cast.MAYOR.hurt()) || line.equals(Cast.MAYOR.attack()));
	}

	/** Every 10 ticks near a player: keep complaining in turn, or, when it is over, queue the relief. */
	static void check(Villager villager) {
		UUID id = villager.getUUID();
		List<String> now = of(villager);
		if (now.isEmpty()) {
			Set<String> past = SUFFERED.remove(id);
			NEXT.remove(id);
			if (past != null && !past.isEmpty()) relieve(villager, past);
			return;
		}
		RELIEF_DUE.remove(id);
		SUFFERED.computeIfAbsent(id, ignored -> new HashSet<>()).addAll(now);
		SpeechQueue.forget(id);
		List<String> cries = villager.isBaby() ? List.of(CHILD_HURT) : Voices.mayorMan(villager) ? List.of(Cast.MAYOR.hurt()) : now;
		String saying = Speech.saying(villager);
		if (saying != null && cries.contains(saying)) return;
		if (Speech.busy(villager)) Speech.hush(villager);
		int start = NEXT.getOrDefault(id, 0);
		for (int step = 0; step < cries.size(); step++) {
			int at = Math.floorMod(start + step, cries.size());
			String cry = cries.get(at);
			if (Speech.say(villager, cry, "condition:" + id + ":" + cry, LINGERING.contains(cry) ? LINGER : 1L, null)) {
				NEXT.put(id, at + 1);
				return;
			}
		}
	}

	private static boolean relieve(Villager villager, Set<String> past) {
		if (Voices.mayorMan(villager) || !(villager.level() instanceof ServerLevel level)) return false;
		String line = villager.isBaby() ? CHILD_RELIEF : past.contains(WALLED) ? OUT_OF_THE_WALL : RELIEF;
		Harm.calmed(villager);
		RELIEF_DUE.put(villager.getUUID(), new Relief(level, line, Speech.now() + 1L));
		return true;
	}

	/** A player broke the block a villager was stuck in: it says it is out. */
	static boolean freedFrom(ServerLevel level, BlockPos pos) {
		AABB block = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
		boolean any = false;
		for (Villager villager : Near.villagers(level, Vec3.atCenterOf(pos), 4.0)) {
			Set<String> past = SUFFERED.get(villager.getUUID());
			boolean stuck = (past != null && past.contains(WALLED)) || VillagerMoments.wasWalled(villager.getUUID());
			if (!stuck || !villager.getBoundingBox().inflate(0.25).intersects(block)) continue;
			SUFFERED.remove(villager.getUUID());
			NEXT.remove(villager.getUUID());
			any |= relieve(villager, Set.of(WALLED));
		}
		return any;
	}

	static void tick() {
		RELIEF_DUE.entrySet().removeIf(entry -> {
			Relief relief = entry.getValue();
			if (relief.due() > Speech.now()) return false;
			if (!(relief.level().getEntity(entry.getKey()) instanceof Villager villager) || !villager.isAlive()) return true;
			if (suffering(villager)) return false;
			String saying = Speech.saying(villager);
			if (saying != null && Harm.locks(saying)) return false;
			if (Speech.busy(villager)) Speech.hush(villager);
			return Speech.say(villager, relief.line(), "condition_relief:" + entry.getKey() + ":" + relief.line(), 1L, null)
					|| !Voices.canVoice(villager, relief.line());
		});
	}

	static void forget(UUID id) {
		SUFFERED.remove(id);
		NEXT.remove(id);
		RELIEF_DUE.remove(id);
	}

	static void clear() {
		SUFFERED.clear();
		NEXT.clear();
		RELIEF_DUE.clear();
	}
}
