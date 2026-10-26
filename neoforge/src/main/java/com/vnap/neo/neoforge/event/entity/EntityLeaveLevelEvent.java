package com.vnap.neo.neoforge.event.entity;

public class EntityLeaveLevelEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(EntityLeaveLevelEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.EntityLeaveLevelEvent.class, priority, EntityLeaveLevelEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.EntityLeaveLevelEvent f;

	EntityLeaveLevelEvent(net.minecraftforge.event.entity.EntityLeaveLevelEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.Entity getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.level.Level getLevel() {
		return f.getLevel();
	}
}
