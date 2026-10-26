package com.vnap.fab.neoforge.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** NeoForge's choice whether a test's name and template carry its class's, read by com.vnap.fab.FabricGameTests. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PrefixGameTestTemplate {
	boolean value() default true;
}
