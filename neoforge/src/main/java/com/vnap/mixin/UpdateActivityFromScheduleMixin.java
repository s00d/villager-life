package com.vnap.mixin;

import com.vnap.village.town.Shelter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The villager's day by its schedule, or the night's rest in a thunderstorm (Shelter.storm). */
@SuppressWarnings("unused")
@Mixin(UpdateActivityFromSchedule.class)
public abstract class UpdateActivityFromScheduleMixin {
	@Inject(method = "create", at = @At("RETURN"), cancellable = true)
	private static void vnap$storm(CallbackInfoReturnable<BehaviorControl<LivingEntity>> cir) {
		cir.setReturnValue(BehaviorBuilder.create(instance -> instance.point((level, entity, time) -> {
			//? if >=1.21.11 {
			/*if (!Shelter.storm(level, entity)) entity.getBrain().updateActivityFromSchedule(level.environmentAttributes(), level.getGameTime(), entity.position());
			*///?} else
			if (!Shelter.storm(level, entity)) entity.getBrain().updateActivityFromSchedule(com.vnap.Mc.dayTime(level), level.getGameTime());
			return true;
		})));
	}
}
