package com.vnap.fab.neoforge.common.extensions;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/** NeoForge's say of a bow or crossbow in the arrow it shoots (com.vnap.fab.mixin.ProjectileWeaponMixin asks it; Loom injects it). */
public interface IProjectileWeaponItemExtension {
	default AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectile, ItemStack weapon) {
		return arrow;
	}
}
