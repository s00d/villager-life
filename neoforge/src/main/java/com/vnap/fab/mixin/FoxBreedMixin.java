package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A fox's breeding is its own goal's: NeoForge's BabyEntitySpawnEvent there too. */
@SuppressWarnings("unused")
@Mixin(targets = "net.minecraft.world.entity.animal.Fox$FoxBreedGoal")
public abstract class FoxBreedMixin {
	@WrapOperation(method = "breed", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/animal/Animal;getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/AgeableMob;"))
	private @Nullable AgeableMob vnap$child(Animal self, ServerLevel level, AgeableMob mate, Operation<AgeableMob> vanilla) {
		return com.vnap.fab.Breeding.child(self, (Animal) mate, vanilla.call(self, level, mate));
	}
}
