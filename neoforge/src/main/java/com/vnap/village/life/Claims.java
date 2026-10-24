package com.vnap.village.life;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Places one villager is on the way to (a crossbow to load, a clock to wind, a thing to set down), so that two do
 * not go. A claim of a villager that died or was unloaded on the way (its behaviour never stopped) lapses.
 */
final class Claims {
	private final Map<BlockPos, UUID> held = new HashMap<>();

	boolean taken(ServerLevel level, BlockPos pos, Villager asking) {
		UUID by = held.get(pos);
		if (by == null || by.equals(asking.getUUID())) return false;
		if (level.getEntity(by) instanceof Villager other && other.isAlive()) return true;
		held.remove(pos);
		return false;
	}

	void take(BlockPos pos, Villager villager) {
		held.put(pos.immutable(), villager.getUUID());
	}

	void release(BlockPos pos) {
		held.remove(pos);
	}
}
