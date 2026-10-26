package com.vnap.neo.fml.loading;

import java.nio.file.Path;

public enum FMLPaths {
	GAMEDIR(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR),
	MODSDIR(net.minecraftforge.fml.loading.FMLPaths.MODSDIR),
	CONFIGDIR(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR),
	FMLCONFIG(net.minecraftforge.fml.loading.FMLPaths.FMLCONFIG);

	private final net.minecraftforge.fml.loading.FMLPaths forge;

	FMLPaths(net.minecraftforge.fml.loading.FMLPaths forge) {
		this.forge = forge;
	}

	public Path get() {
		return forge.get();
	}

	public Path relative() {
		return forge.relative();
	}
}
