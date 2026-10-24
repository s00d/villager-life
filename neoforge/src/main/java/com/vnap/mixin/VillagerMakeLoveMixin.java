package com.vnap.mixin;

import com.vnap.dialogue.Family;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Children come only to a married couple with room at home; the baby gets its bed in that house. */
@Mixin(VillagerMakeLove.class)
@SuppressWarnings("unused")
public abstract class VillagerMakeLoveMixin {
	@Inject(method = "isBreedingPossible", at = @At("RETURN"), cancellable = true)
	private void vnap$onlySpouses(Villager villager, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() || !(villager.level() instanceof ServerLevel level)) return;
		Optional<AgeableMob> partner = villager.getBrain().getMemory(MemoryModuleType.BREED_TARGET);
		if (partner.isEmpty() || !(partner.get() instanceof Villager other) || !Family.mayHaveChild(level, villager, other)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "takeVacantBed", at = @At("HEAD"), cancellable = true)
	private void vnap$familyBed(ServerLevel level, Villager villager, CallbackInfoReturnable<Optional<BlockPos>> cir) {
		cir.setReturnValue(Family.childBed(level, villager));
	}

	@Inject(method = "breed", at = @At("RETURN"))
	private void vnap$born(ServerLevel level, Villager parent, Villager partner, CallbackInfoReturnable<Optional<Villager>> cir) {
		cir.getReturnValue().ifPresent(child -> Family.onBorn(level, parent, partner, child));
	}
}
