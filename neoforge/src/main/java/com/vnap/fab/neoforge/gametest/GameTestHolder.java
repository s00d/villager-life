package com.vnap.fab.neoforge.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** NeoForge's holder of game tests, read by com.vnap.fab.FabricGameTests. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GameTestHolder {
	String value() default "minecraft";
}
