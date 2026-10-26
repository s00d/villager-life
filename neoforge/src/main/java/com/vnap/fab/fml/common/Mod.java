package com.vnap.fab.fml.common;

import com.vnap.fab.api.distmarker.Dist;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Read by nobody on Fabric: the mod's entry points are com.vnap.fab.FabricEntry and FabricClientEntry. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
	String value();

	Dist[] dist() default {Dist.CLIENT, Dist.DEDICATED_SERVER};
}
