package com.vnap.neo.neoforge.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** NeoForge's name; on Forge the build writes Forge's GameTestDontPrefix in its place (build-forge.gradle). */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PrefixGameTestTemplate {
	boolean value() default true;
}
