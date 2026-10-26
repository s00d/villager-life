package com.vnap.fab;

//? if <1.21.5 {
import com.vnap.fab.neoforge.gametest.GameTestHolder;
import com.vnap.fab.neoforge.gametest.PrefixGameTestTemplate;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
//?}

/**
 * The mod's game tests on Fabric (the fabric-gametest entrypoint), named as NeoForge names them: a holder's namespace
 * on the template, the class's name before the test's and the template's only where PrefixGameTestTemplate asks.
 * Fabric API's own registration would put every test in its class and every template in minecraft's namespace. From
 * 1.21.5 tests are registered (com.vnap.gametest.TestRegistrar).
 */
public final class FabricGameTests {
	public FabricGameTests() {
		//? if <1.21.5 {
		try (InputStream in = FabricGameTests.class.getResourceAsStream("/META-INF/vnap-gametests.txt")) {
			if (in == null) return;
			for (String name : new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().filter(line -> !line.isBlank()).toList()) {
				Class<?> type = Class.forName(name);
				GameTestHolder holder = type.getAnnotation(GameTestHolder.class);
				if (holder == null) continue;
				for (Method method : type.getDeclaredMethods()) {
					if (method.isAnnotationPresent(GameTest.class) && Modifier.isStatic(method.getModifiers())) register(holder.value(), method);
				}
				GameTestRegistry.TEST_CLASS_NAMES.add(type.getSimpleName());
			}
		} catch (IOException | ClassNotFoundException e) {
			throw new IllegalStateException("the game tests' list", e);
		}
		//?}
	}
	//? if <1.21.5 {

	private static void register(String namespace, Method method) {
		GameTest spec = method.getAnnotation(GameTest.class);
		PrefixGameTestTemplate prefix = method.getAnnotation(PrefixGameTestTemplate.class);
		if (prefix == null) prefix = method.getDeclaringClass().getAnnotation(PrefixGameTestTemplate.class);
		String type = method.getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT) + ".";
		String before = prefix == null || prefix.value() ? type : "";
		String name = before + method.getName().toLowerCase(Locale.ROOT);
		String template = namespace + ":" + (spec.template().isEmpty() ? name : before + spec.template());
		GameTestRegistry.TEST_FUNCTIONS.add(new TestFunction(spec.batch(), name, template, StructureUtils.getRotationForRotationSteps(spec.rotationSteps()),
				spec.timeoutTicks(), spec.setupTicks(), spec.required(), spec.manualOnly(), spec.attempts(), spec.requiredSuccesses(), spec.skyAccess(), helper -> {
			try {
				method.invoke(null, helper);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			} catch (InvocationTargetException e) {
				if (e.getCause() instanceof RuntimeException failure) throw failure;
				if (e.getCause() instanceof Error error) throw error;
				throw new IllegalStateException(e.getCause());
			}
		}));
	}
	//?}
}
