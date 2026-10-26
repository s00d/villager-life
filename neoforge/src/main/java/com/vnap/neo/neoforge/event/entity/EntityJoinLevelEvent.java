package com.vnap.neo.neoforge.event.entity;

public class EntityJoinLevelEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(EntityJoinLevelEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.EntityJoinLevelEvent.class, priority, EntityJoinLevelEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.EntityJoinLevelEvent f;

	EntityJoinLevelEvent(net.minecraftforge.event.entity.EntityJoinLevelEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.Entity getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.level.Level getLevel() {
		return f.getLevel();
	}

	public boolean loadedFromDisk() {
		return f.loadedFromDisk();
	}
}
