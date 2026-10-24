package com.vnap.mixin;

import com.vnap.village.home.Mayors;
import com.vnap.village.market.Shops;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.GoToPotentialJobSite;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mayor or a shopkeeper takes no trade, yet vanilla books it a job site again right after we drop it and walks it
 * there: it stood all day at a composter it could not reach instead of at its counter or its build.
 */
@Mixin(GoToPotentialJobSite.class)
@SuppressWarnings("unused")
public abstract class GoToPotentialJobSiteMixin {
	@Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z",
			at = @At("HEAD"), cancellable = true)
	private void vnap$noJobForAnOffice(ServerLevel level, Villager villager, CallbackInfoReturnable<Boolean> cir) {
		if (Mayors.isMayor(villager) || Shops.isKeeper(villager)) cir.setReturnValue(false);
	}

	@Inject(method = "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)Z",
			at = @At("HEAD"), cancellable = true)
	private void vnap$stopForAnOffice(ServerLevel level, Villager villager, long time, CallbackInfoReturnable<Boolean> cir) {
		if (Mayors.isMayor(villager) || Shops.isKeeper(villager)) cir.setReturnValue(false);
	}
}
