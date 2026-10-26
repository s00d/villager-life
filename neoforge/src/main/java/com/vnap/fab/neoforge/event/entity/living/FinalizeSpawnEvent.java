package com.vnap.fab.neoforge.event.entity.living;

import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.ICancellableEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * A mob about to be finished spawning (com.vnap.fab.mixin.FinalizeSpawnMixin): a cancelled spawn is not added. Comes from
 * the world generation threads too (a village's villagers) with the generation region as its level.
 */
public final class FinalizeSpawnEvent extends Event implements ICancellableEvent {
	private final Mob entity;
	private final ServerLevelAccessor level;
	private final MobSpawnType type;
	private boolean spawnCancelled;

	public FinalizeSpawnEvent(Mob entity, ServerLevelAccessor level, MobSpawnType type) {
		this.entity = entity;
		this.level = level;
		this.type = type;
	}

	public Mob getEntity() {
		return entity;
	}

	public ServerLevelAccessor getLevel() {
		return level;
	}

	public double getX() {
		return entity.getX();
	}

	public double getY() {
		return entity.getY();
	}

	public double getZ() {
		return entity.getZ();
	}

	public MobSpawnType getSpawnType() {
		return type;
	}

	public void setSpawnCancelled(boolean cancel) {
		spawnCancelled = cancel;
	}

	public boolean isSpawnCancelled() {
		return spawnCancelled;
	}
}
