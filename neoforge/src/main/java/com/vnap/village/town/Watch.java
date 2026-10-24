package com.vnap.village.town;

import com.vnap.dialogue.Family;
import com.vnap.village.home.Hamlets;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Shops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The night watch of a walled town: two grown-ups without a trade stay up each night (a different pair each night)
 * and walk from gate to gate with a lantern; one who sees a monster rings the town's bell (WatchBehavior). Paid from
 * the treasury in the morning.
 */
public final class Watch {
	/** Watchmen a night. */
	static final int WATCHMEN = 2;

	private Watch() {
	}

	/** Night (TownRules.NIGHT_FROM..NIGHT_TO): the watch keeps the night, not a daytime storm. */
	public static boolean night(ServerLevel level) {
		long time = Family.timeOfDay(level);
		return time >= TownRules.NIGHT_FROM && time < TownRules.NIGHT_TO;
	}

	/** The town's walled network this villager belongs to, or null. */
	public static Roads.@org.jspecify.annotations.Nullable Network walled(ServerLevel level, Villager villager) {
		Roads.Network network = Roads.networkOf(level, villager);
		return network == null || network.wall <= 0 || network.turrets.isEmpty() ? null : network;
	}

	/** Whether this villager keeps the watch tonight (the pair is chosen the first time a night asks). */
	public static boolean onDuty(ServerLevel level, Villager villager) {
		if (!night(level) || villager.isBaby()) return false;
		Roads.Network network = walled(level, villager);
		if (network == null) return false;
		// the night is named by the day it began on (after midnight the day number has moved on)
		long night = Family.day(level) - (Family.timeOfDay(level) < TownRules.NIGHT_FROM ? 1 : 0);
		if (network.watchNight != night) {
			network.watchNight = night;
			network.watchers.clear();
			network.watchers.addAll(choose(level, network, night));
		}
		return network.watchers.contains(villager.getUUID());
	}

	private static Set<UUID> choose(ServerLevel level, Roads.Network network, long night) {
		List<Villager> able = new ArrayList<>(Towns.people(level, network.bell,
				villager -> !villager.isBaby() && !villager.isNoAi()
						&& com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE)
						&& !com.vnap.village.life.Life.official(villager) && !Hamlets.leaving(villager)
						&& !com.vnap.dialogue.Infants.minding(level, villager) && Towns.ours(level, network.bell, villager.blockPosition())));
		if (able.isEmpty()) return Set.of();
		able.sort(Comparator.comparing(Villager::getUUID));
		// a different pair each night, round the town's able grown-ups
		Set<UUID> out = new java.util.HashSet<>();
		for (int i = 0; i < Math.min(WATCHMEN, able.size()); i++) out.add(able.get(Math.floorMod(night * WATCHMEN + i, able.size())).getUUID());
		return out;
	}

	/** The posts a watchman walks between: inside each gate, a few blocks toward the bell. */
	public static List<BlockPos> posts(Roads.Network network) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos crossbow : network.turrets) {
			BlockPos gate = crossbow.below(TownRules.WALL_HEIGHT + 1);
			int dx = Integer.signum(network.bell.getX() - gate.getX());
			int dz = Integer.signum(network.bell.getZ() - gate.getZ());
			out.add(gate.offset(dx * 3, 1, dz * 3));
		}
		return out;
	}
}
