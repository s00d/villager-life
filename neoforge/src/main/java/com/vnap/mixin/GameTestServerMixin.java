package com.vnap.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.5 {
import com.vnap.TestGround;
import net.minecraft.gametest.framework.TestFunction;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.Collection;
//?}

/**
 * Runs only some game tests when {@code vl.tests} names them, each on its own cleared ground (TestGround). From
 * 1.21.5 tests are registered (com.vnap.gametest.TestRegistrar) and get both there; the game itself never starts a
 * GameTestServer.
 */
@Mixin(GameTestServer.class)
@SuppressWarnings("unused")
public abstract class GameTestServerMixin {
	//? if <1.21.5 {
	@ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true)
	private static Collection<TestFunction> vnap$onlyNamedTests(Collection<TestFunction> tests) {
		return tests.stream()
				.filter(test -> TestGround.selected(test.batchName(), test.testName()))
				.map(test -> new TestFunction(test.batchName(), test.testName(), test.structureName(), test.rotation(), test.maxTicks(),
						test.setupTicks(), test.required(), test.manualOnly(), test.maxAttempts(), test.requiredSuccesses(), test.skyAccess(),
						TestGround.onFreshGround(test.batchName(), test.testName(), test.function())))
				.toList();
	}
	//?}
}
