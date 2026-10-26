package com.vnap.neo.neoforge.event.entity;

public class EntityStruckByLightningEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(EntityStruckByLightningEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.EntityStruckByLightningEvent.class, priority, EntityStruckByLightningEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.EntityStruckByLightningEvent f;

	EntityStruckByLightningEvent(net.minecraftforge.event.entity.EntityStruckByLightningEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.Entity getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.entity.LightningBolt getLightning() {
		return f.getLightning();
	}
}
