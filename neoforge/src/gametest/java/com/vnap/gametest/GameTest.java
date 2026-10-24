package com.vnap.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A game test from 1.21.5, where tests are registered rather than annotated (TestRegistrar); the same fields as 1.21.1's
 * net.minecraft.gametest.framework.GameTest, whose import the build swaps for this one.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
	String template() default "";

	String batch() default "defaultBatch";

	int timeoutTicks() default 100;

	boolean required() default true;

	boolean skyAccess() default false;
}
