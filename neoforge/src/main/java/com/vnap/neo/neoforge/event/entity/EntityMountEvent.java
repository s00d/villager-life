package com.vnap.neo.neoforge.event.entity;

public class EntityMountEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(EntityMountEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.EntityMountEvent.class, priority, EntityMountEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.EntityMountEvent f;

	EntityMountEvent(net.minecraftforge.event.entity.EntityMountEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.Entity getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.entity.Entity getEntityMounting() {
		return f.getEntityMounting();
	}

	public net.minecraft.world.entity.Entity getEntityBeingMounted() {
		return f.getEntityBeingMounted();
	}

	public boolean isMounting() {
		return f.isMounting();
	}

	public boolean isDismounting() {
		return f.isDismounting();
	}
}
