package com.vnap.neo.fml.common;

import net.minecraftforge.api.distmarker.Dist;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Its class's static @SubscribeEvent methods listen from the start (com.vnap.neo.ForgeEntry finds them in the mod's scan). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {
	Dist[] value() default {Dist.CLIENT, Dist.DEDICATED_SERVER};

	String modid() default "";

	Bus bus() default Bus.GAME;

	enum Bus {
		GAME, MOD
	}
}
