package com.vnap.fab.neoforge.event;

//? if >=1.21.5 {
/*import com.vnap.fab.bus.api.Event;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceLocation;

/^*
 * NeoForge's game test registration on Fabric: tests are data from 1.21.5 (test environments and test instances in
 * registries the data packs fill); these go straight into those registries as Fabric API puts its own tests there,
 * before they freeze (com.vnap.fab.mixin.GameTestRegistriesMixin).
 ^/
public final class RegisterGameTestsEvent extends Event {
	private final Registry<TestEnvironmentDefinition> environments;
	private final Registry<GameTestInstance> tests;

	public RegisterGameTestsEvent(Registry<TestEnvironmentDefinition> environments, Registry<GameTestInstance> tests) {
		this.environments = environments;
		this.tests = tests;
	}

	public Holder<TestEnvironmentDefinition> registerEnvironment(ResourceLocation name, TestEnvironmentDefinition definition) {
		return Registry.registerForHolder(environments, name, definition);
	}

	public void registerTest(ResourceLocation name, Function<TestData<Holder<TestEnvironmentDefinition>>, GameTestInstance> factory,
			TestData<Holder<TestEnvironmentDefinition>> data) {
		Registry.register(tests, name, factory.apply(data));
	}
}
*///?} else {
/** NeoForge's from 1.21.5 only (until then a test is an annotated method: com.vnap.fab.FabricGameTests). */
public final class RegisterGameTestsEvent {
	private RegisterGameTestsEvent() {
	}
}
//?}
