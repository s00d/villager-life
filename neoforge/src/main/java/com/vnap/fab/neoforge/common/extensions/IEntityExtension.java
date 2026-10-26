package com.vnap.fab.neoforge.common.extensions;

import net.minecraft.nbt.CompoundTag;

/** What NeoForge adds to every entity: data of the mods', kept with it (com.vnap.fab.mixin.EntityDataMixin; Loom injects it). */
public interface IEntityExtension {
	default CompoundTag getPersistentData() {
		throw new AssertionError("com.vnap.fab.mixin.EntityDataMixin");
	}

	/** Whether the entity is in its level now (added and not yet removed). */
	default boolean isAddedToLevel() {
		var self = (net.minecraft.world.entity.Entity) this;
		if (self.level() instanceof net.minecraft.server.level.ServerLevel level) return self.equals(level.getEntity(self.getUUID()));
		return self.equals(self.level().getEntity(self.getId()));
	}
}
