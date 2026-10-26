package com.vnap.fab.neoforge.event.entity;

/** An entity added to a level: new (loadedFromDisk false) or from its chunk (com.vnap.fab.mixin.EntityJoinMixin). */
public class EntityJoinLevelEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.Entity entity;
	private final net.minecraft.world.level.Level level;
	private final boolean loadedFromDisk;

	public EntityJoinLevelEvent(net.minecraft.world.entity.Entity entity, net.minecraft.world.level.Level level, boolean loadedFromDisk) {
		this.entity = entity;
		this.level = level;
		this.loadedFromDisk = loadedFromDisk;
	}

	public net.minecraft.world.entity.Entity getEntity() {
		return entity;
	}

	public net.minecraft.world.level.Level getLevel() {
		return level;
	}

	public boolean loadedFromDisk() {
		return loadedFromDisk;
	}
}
