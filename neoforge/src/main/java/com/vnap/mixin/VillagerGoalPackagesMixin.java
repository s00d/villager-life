package com.vnap.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.vnap.village.life.LifeBrain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerGoalPackages.class)
@SuppressWarnings("unused")
public abstract class VillagerGoalPackagesMixin {
	@Inject(method = "getCorePackage", at = @At("RETURN"), cancellable = true)
	private static void vnap$core(VillagerProfession profession, float speedModifier,
			CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
		// the doors a villager opened are shut once it stops walking (vanilla only shuts them on the way)
		cir.setReturnValue(ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>builder()
				.addAll(cir.getReturnValue()).add(Pair.of(0, new com.vnap.village.life.Doors())).add(Pair.of(0, new com.vnap.village.life.HeroBehavior())).build());
	}

	@Inject(method = "getPanicPackage", at = @At("RETURN"), cancellable = true)
	// the profession is not the panic package's from 26.1
	//? if >=26.1 {
	/*private static void vnap$panic(float speedModifier,
	*///?} else
	private static void vnap$panic(VillagerProfession profession, float speedModifier,
			CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
		// at night home rather than off into the dark (after vanilla's flight, whose walk target it replaces)
		cir.setReturnValue(ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>builder()
				.addAll(cir.getReturnValue()).add(Pair.of(2, new com.vnap.village.life.RunHome(speedModifier * 1.5F))).build());
	}

	@Inject(method = "getWorkPackage", at = @At("RETURN"), cancellable = true)
	private static void vnap$work(VillagerProfession profession, float speedModifier,
			CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
		cir.setReturnValue(LifeBrain.work(cir.getReturnValue()));
	}

	@Inject(method = "getMeetPackage", at = @At("RETURN"), cancellable = true)
	// the profession is not the meeting, resting or idle package's from 26.1
	//? if >=26.1 {
	/*private static void vnap$meet(float speedModifier,
	*///?} else
	private static void vnap$meet(VillagerProfession profession, float speedModifier,
			CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
		cir.setReturnValue(LifeBrain.meet(cir.getReturnValue()));
	}

	@Inject(method = "getRestPackage", at = @At("RETURN"), cancellable = true)
	// the profession is not the meeting, resting or idle package's from 26.1
	//? if >=26.1 {
	/*private static void vnap$rest(float speedModifier,
	*///?} else
	private static void vnap$rest(VillagerProfession profession, float speedModifier,
			CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
		cir.setReturnValue(LifeBrain.rest(cir.getReturnValue()));
	}

	@Inject(method = "getPlayPackage", at = @At("RETURN"), cancellable = true)
	private static void vnap$play(float speedModifier,
			CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
		cir.setReturnValue(LifeBrain.play(cir.getReturnValue()));
	}

	@Inject(method = "getIdlePackage", at = @At("RETURN"), cancellable = true)
	// the profession is not the meeting, resting or idle package's from 26.1
	//? if >=26.1 {
	/*private static void vnap$idle(float speedModifier,
	*///?} else
	private static void vnap$idle(VillagerProfession profession, float speedModifier,
			CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
		cir.setReturnValue(LifeBrain.idle(cir.getReturnValue()));
	}
}
