package com.vnap.gametest;

//? if >=1.21.5 {
/*import com.vnap.TestGround;
import com.vnap.VillagerLife;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.annotation.ElementType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
*///?}

/**
 * From 1.21.5 a game test is a registered function and test instance, not an annotated method: this registers every
 * {@link GameTest} method of a {@link GameTestHolder} class as 1.21.1 ran it (its id the method's name in lower case, its
 * batch an environment of its own, its template a structure of the mod) and only those {@code vl.tests} names.
 */
//? if >=1.21.5
/*@EventBusSubscriber(modid = VillagerLife.MOD_ID)*/
public final class TestRegistrar {
	private TestRegistrar() {
	}

	//? if >=1.21.5 {
	/*private record Test(ResourceLocation id, String name, String batch, Method method, GameTest spec) {
	}

	private static List<Test> tests() {
		List<Test> tests = new ArrayList<>();
		ModList.get().getModFileById(VillagerLife.MOD_ID).getFile().getScanResult()
				.getAnnotatedBy(GameTestHolder.class, ElementType.TYPE).forEach(holder -> {
			Class<?> type;
			try {
				type = Class.forName(holder.clazz().getClassName());
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException("game test class " + holder.clazz().getClassName(), e);
			}
			for (Method method : type.getDeclaredMethods()) {
				GameTest spec = method.getAnnotation(GameTest.class);
				if (spec == null || !Modifier.isStatic(method.getModifiers())) continue;
				String name = method.getName().toLowerCase(Locale.ROOT);
				if (!TestGround.selected(spec.batch(), name)) continue;
				tests.add(new Test(ResourceLocation.fromNamespaceAndPath(VillagerLife.MOD_ID, name), name, spec.batch(), method, spec));
			}
		});
		return tests;
	}

	@SubscribeEvent
	public static void functions(RegisterEvent event) {
		event.register(Registries.TEST_FUNCTION, helper -> {
			for (Test test : tests()) helper.register(test.id(), TestGround.onFreshGround(test.batch(), test.name(), run(test.method())));
		});
	}

	@SubscribeEvent
	public static void instances(RegisterGameTestsEvent event) {
		Map<String, Holder<TestEnvironmentDefinition>> batches = new HashMap<>();
		for (Test test : tests()) {
			Holder<TestEnvironmentDefinition> batch = batches.computeIfAbsent(test.batch(), name -> event.registerEnvironment(
					ResourceLocation.fromNamespaceAndPath(VillagerLife.MOD_ID, name.toLowerCase(Locale.ROOT)), new TestEnvironmentDefinition.AllOf()));
			TestData<Holder<TestEnvironmentDefinition>> data = new TestData<>(batch,
					ResourceLocation.fromNamespaceAndPath(VillagerLife.MOD_ID, test.spec().template()), test.spec().timeoutTicks(), 0,
					test.spec().required(), Rotation.NONE, false, 1, 1, test.spec().skyAccess());
			event.registerTest(test.id(), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, test.id()), data));
		}
	}

	private static java.util.function.Consumer<GameTestHelper> run(Method method) {
		return helper -> {
			try {
				method.invoke(null, helper);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			} catch (InvocationTargetException e) {
				if (e.getCause() instanceof RuntimeException failure) throw failure;
				if (e.getCause() instanceof Error error) throw error;
				throw new IllegalStateException(e.getCause());
			}
		};
	}
	*///?}
}
