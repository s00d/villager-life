package com.vnap.neo.fml.common;

import net.minecraftforge.api.distmarker.Dist;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Read by nobody on Forge: the mod's Forge entry is com.vnap.neo.ForgeEntry, which builds the classes marked with it. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
	String value();

	Dist[] dist() default {Dist.CLIENT, Dist.DEDICATED_SERVER};
}
