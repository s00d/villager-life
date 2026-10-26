package com.vnap.fab.mixin;

//? if >=26.1 {
/*import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.RegisterGameTestsEvent;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.RegistryLoadTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^* The mod's game tests into the server's test registries, right after Fabric API puts its own there (before they freeze). ^/
@SuppressWarnings("unused")
@Mixin(targets = "net.fabricmc.fabric.impl.gametest.FabricGameTestModInitializer", remap = false)
public abstract class GameTestRegistriesMixin {
	@SuppressWarnings("unchecked")
	@Inject(method = "registerDynamicEntries", at = @At("TAIL"))
	private static void vnap$tests(List<RegistryLoadTask<?>> loaders, CallbackInfo info) {
		Registry<TestEnvironmentDefinition<?>> environments = null;
		Registry<GameTestInstance> tests = null;
		for (RegistryLoadTask<?> loader : loaders) {
			Registry<?> registry = loader.readOnlyRegistry();
			if (registry.key().equals(Registries.TEST_ENVIRONMENT)) environments = (Registry<TestEnvironmentDefinition<?>>) registry;
			if (registry.key().equals(Registries.TEST_INSTANCE)) tests = (Registry<GameTestInstance>) registry;
		}
		if (environments != null && tests != null) Bridge.fire(new RegisterGameTestsEvent(environments, tests));
	}
}
*///?} else if >=1.21.5 {
/*import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.RegisterGameTestsEvent;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.RegistryDataLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^* The mod's game tests into the server's test registries, right after Fabric API puts its own there (before they freeze). ^/
@SuppressWarnings("unused")
@Mixin(targets = "net.fabricmc.fabric.impl.gametest.FabricGameTestModInitializer", remap = false)
public abstract class GameTestRegistriesMixin {
	@SuppressWarnings("unchecked")
	@Inject(method = "registerDynamicEntries", at = @At("TAIL"))
	private static void vnap$tests(List<RegistryDataLoader.Loader<?>> loaders, CallbackInfo info) {
		Registry<TestEnvironmentDefinition> environments = null;
		Registry<GameTestInstance> tests = null;
		for (RegistryDataLoader.Loader<?> loader : loaders) {
			if (loader.registry().key().equals(Registries.TEST_ENVIRONMENT)) environments = (Registry<TestEnvironmentDefinition>) loader.registry();
			if (loader.registry().key().equals(Registries.TEST_INSTANCE)) tests = (Registry<GameTestInstance>) loader.registry();
		}
		if (environments != null && tests != null) Bridge.fire(new RegisterGameTestsEvent(environments, tests));
	}
}
*///?} else {
/** The game tests' registries are data from 1.21.5 only (the build leaves this mixin out before). */
public final class GameTestRegistriesMixin {
	private GameTestRegistriesMixin() {
	}
}
//?}
