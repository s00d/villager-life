package com.vnap.fab.neoforge.event.entity;

/** An entity getting on or off another (com.vnap.fab.mixin.EntityMountMixin). */
public class EntityMountEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.Entity mounting;
	private final net.minecraft.world.entity.Entity mounted;
	private final boolean isMounting;

	public EntityMountEvent(net.minecraft.world.entity.Entity mounting, net.minecraft.world.entity.Entity mounted, boolean isMounting) {
		this.mounting = mounting;
		this.mounted = mounted;
		this.isMounting = isMounting;
	}

	public net.minecraft.world.entity.Entity getEntity() {
		return mounting;
	}

	public net.minecraft.world.entity.Entity getEntityMounting() {
		return mounting;
	}

	public net.minecraft.world.entity.Entity getEntityBeingMounted() {
		return mounted;
	}

	public boolean isMounting() {
		return isMounting;
	}

	public boolean isDismounting() {
		return !isMounting;
	}
}
