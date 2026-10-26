package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's LivingConversionEvent.Post: a mob turned into another (a zombie villager cured, a villager struck into a witch). */
@SuppressWarnings("unused")
@Mixin(Mob.class)
public abstract class ConversionMixin {
	// from 1.21.2 the one with the spawn reason (the shorter one calls it)
	//? if >=1.21.2 {
	/*@ModifyReturnValue(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;",
			at = @At("RETURN"))
	*///?} else
	@ModifyReturnValue(method = "convertTo", at = @At("RETURN"))
	private <T extends Mob> @Nullable T vnap$converted(@Nullable T outcome) {
		if (outcome != null) com.vnap.fab.Bridge.fire(new com.vnap.fab.neoforge.event.entity.living.LivingConversionEvent.Post((Mob) (Object) this, outcome));
		return outcome;
	}
}
