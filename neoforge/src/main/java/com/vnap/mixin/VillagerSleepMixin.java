package com.vnap.mixin;

import com.vnap.dialogue.Bedtime;
import com.vnap.dialogue.VillagerHome;
import com.vnap.village.town.Watch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A villager does not lie down in another's bed, before its goodnight, or while it keeps the night watch. */
@Mixin(LivingEntity.class)
@SuppressWarnings("unused")
public abstract class VillagerSleepMixin {
	// from 26.3 startSleeping answers whether the sleeper lay down
	//? if >=26.3 {
	/*@Inject(method = "startSleeping", at = @At("HEAD"), cancellable = true)
	private void vnap$notYet(BlockPos bed, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> lies) {
		if ((Object) this instanceof Villager villager && vnap$staysUp(villager, bed)) lies.setReturnValue(false);
	}
	*///?} else {
	@Inject(method = "startSleeping", at = @At("HEAD"), cancellable = true)
	private void vnap$notYet(BlockPos bed, CallbackInfo lies) {
		if ((Object) this instanceof Villager villager && vnap$staysUp(villager, bed)) lies.cancel();
	}
	//?}

	private static boolean vnap$staysUp(Villager villager, BlockPos bed) {
		return VillagerHome.refuseOtherBed(villager, bed) || Bedtime.delay(villager, bed)
				|| (villager.level() instanceof ServerLevel level && Watch.onDuty(level, villager));
	}
}
