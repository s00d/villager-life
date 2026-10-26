package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's customArrow: the weapon has its say in each arrow it makes. */
@SuppressWarnings("unused")
@Mixin(ProjectileWeaponItem.class)
public abstract class ProjectileWeaponMixin implements com.vnap.fab.neoforge.common.extensions.IProjectileWeaponItemExtension {
	@ModifyReturnValue(method = "createProjectile", at = @At("RETURN"))
	private Projectile vnap$custom(Projectile made, Level level, LivingEntity shooter, ItemStack weapon, ItemStack projectile, boolean critical) {
		return made instanceof AbstractArrow arrow ? customArrow(arrow, projectile, weapon) : made;
	}
}
