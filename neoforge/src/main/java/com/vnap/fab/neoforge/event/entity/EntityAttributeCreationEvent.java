package com.vnap.fab.neoforge.event.entity;

/** The attributes of the mod's living entities: Fabric API's default attribute registry takes them. */
public class EntityAttributeCreationEvent extends com.vnap.fab.bus.api.Event {
	public EntityAttributeCreationEvent() {
	}

	public void put(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity> type, net.minecraft.world.entity.ai.attributes.AttributeSupplier map) {
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(type, map);
	}
}
