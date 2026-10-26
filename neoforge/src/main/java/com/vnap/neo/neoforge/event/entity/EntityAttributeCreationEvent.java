package com.vnap.neo.neoforge.event.entity;

public class EntityAttributeCreationEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(EntityAttributeCreationEvent.class, (priority, sink) -> com.vnap.neo.Bridge.mod(net.minecraftforge.event.entity.EntityAttributeCreationEvent.class, priority, EntityAttributeCreationEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.EntityAttributeCreationEvent f;

	EntityAttributeCreationEvent(net.minecraftforge.event.entity.EntityAttributeCreationEvent f) {
		super(f);
		this.f = f;
	}

	public void put(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity> type, net.minecraft.world.entity.ai.attributes.AttributeSupplier map) {
		f.put(type, map);
	}
}
