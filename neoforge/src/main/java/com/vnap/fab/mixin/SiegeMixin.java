package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.village.VillageSiegeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's VillageSiegeEvent: a siege about to set up at the place found (cancelled: no place). */
@SuppressWarnings("unused")
@Mixin(VillageSiege.class)
public abstract class SiegeMixin {
	@WrapOperation(method = "tryToSetupSiege", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/village/VillageSiege;findRandomSpawnPos(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"))
	private @Nullable Vec3 vnap$siege(VillageSiege siege, ServerLevel level, BlockPos pos, Operation<Vec3> vanilla) {
		Vec3 at = vanilla.call(siege, level, pos);
		return at != null && Bridge.fire(new VillageSiegeEvent(level, at)).isCanceled() ? null : at;
	}
}
