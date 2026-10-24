package com.vnap.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
//? if >=1.21.5
/*import net.minecraft.network.chat.Component;*/
import net.minecraft.world.entity.Entity;

/** The tests' asserts with a text message: from 1.21.5 GameTestHelper takes a Component, so the tests call these. */
public final class Checks {
	private Checks() {
	}

	public static void assertTrue(GameTestHelper test, boolean condition, String message) {
		//? if >=1.21.5 {
		/*test.assertTrue(condition, Component.literal(message));
		*///?} else
		test.assertTrue(condition, message);
	}

	public static void assertFalse(GameTestHelper test, boolean condition, String message) {
		//? if >=1.21.5 {
		/*test.assertFalse(condition, Component.literal(message));
		*///?} else
		test.assertFalse(condition, message);
	}

	public static void fail(GameTestHelper test, String message) {
		//? if >=1.21.5 {
		/*test.fail(Component.literal(message));
		*///?} else
		test.fail(message);
	}

	public static void fail(GameTestHelper test, String message, BlockPos pos) {
		//? if >=1.21.5 {
		/*test.fail(Component.literal(message), pos);
		*///?} else
		test.fail(message, pos);
	}

	public static void fail(GameTestHelper test, String message, Entity entity) {
		//? if >=1.21.5 {
		/*test.fail(Component.literal(message), entity);
		*///?} else
		test.fail(message, entity);
	}
}
