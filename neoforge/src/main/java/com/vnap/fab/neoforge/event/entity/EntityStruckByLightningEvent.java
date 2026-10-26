package com.vnap.fab.neoforge.event.entity;

/** An entity hit by a bolt (com.vnap.fab.mixin.LightningMixin); a cancel spares it. */
public class EntityStruckByLightningEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.Entity entity;
	private final net.minecraft.world.entity.LightningBolt lightning;

	public EntityStruckByLightningEvent(net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.LightningBolt lightning) {
		this.entity = entity;
		this.lightning = lightning;
	}

	public net.minecraft.world.entity.Entity getEntity() {
		return entity;
	}

	public net.minecraft.world.entity.LightningBolt getLightning() {
		return lightning;
	}
}
