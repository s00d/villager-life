package com.vnap.fab.neoforge.event.entity;

public class EntityLeaveLevelEvent extends com.vnap.fab.bus.api.Event {
	private final net.minecraft.world.entity.Entity entity;
	private final net.minecraft.world.level.Level level;

	public EntityLeaveLevelEvent(net.minecraft.world.entity.Entity entity, net.minecraft.world.level.Level level) {
		this.entity = entity;
		this.level = level;
	}

	public net.minecraft.world.entity.Entity getEntity() {
		return entity;
	}

	public net.minecraft.world.level.Level getLevel() {
		return level;
	}
}
