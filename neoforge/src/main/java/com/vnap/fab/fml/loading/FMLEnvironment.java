package com.vnap.fab.fml.loading;

import com.vnap.fab.api.distmarker.Dist;

public final class FMLEnvironment {
	public static final Dist dist = net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT
			? Dist.CLIENT : Dist.DEDICATED_SERVER;
	public static final boolean production = !net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();

	private FMLEnvironment() {
	}

	public static boolean isProduction() {
		return production;
	}

	public static Dist getDist() {
		return dist;
	}
}
