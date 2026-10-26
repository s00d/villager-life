package com.vnap.neo.fml.loading;

import net.minecraftforge.api.distmarker.Dist;

public final class FMLEnvironment {
	public static final Dist dist = net.minecraftforge.fml.loading.FMLEnvironment.dist;
	public static final boolean production = net.minecraftforge.fml.loading.FMLEnvironment.production;

	private FMLEnvironment() {
	}

	public static boolean isProduction() {
		return production;
	}

	public static Dist getDist() {
		return dist;
	}
}
