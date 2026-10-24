package com.vnap.mixin;

import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every batch starts in the morning: one batch holds the night (its tests set the time every tick) and left the world in
 * it, and the next batch's villagers slept through their test (a mother never carried the coming-of-age cake). Which
 * batch came after which changed with the servers the tests were dealt to.
 */
@SuppressWarnings("unused")
@Mixin(GameTestRunner.class)
public abstract class GameTestRunnerMixin {
	// the runner keeps the server, not a level, from 26.3
	//? if >=26.3 {
	/*@Shadow
	@Final
	private net.minecraft.server.MinecraftServer server;

	@Inject(method = "runBatch", at = @At("HEAD"))
	private void vnap$morning(int index, CallbackInfo ci) {
		vnap$morning(server.overworld());
	}
	*///?} else {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "runBatch", at = @At("HEAD"))
	private void vnap$morning(int index, CallbackInfo ci) {
		vnap$morning(level);
	}
	//?}

	private static void vnap$morning(ServerLevel level) {
		com.vnap.Mc.setDayTime(level, 3000L);
		com.vnap.Mc.clearWeather(level);
	}
}
