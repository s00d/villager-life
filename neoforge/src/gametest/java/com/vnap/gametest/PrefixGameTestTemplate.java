package com.vnap.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Kept for the tests' 1.21.1 form: their templates are never prefixed by the class name (TestRegistrar). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PrefixGameTestTemplate {
	boolean value();
}
