package com.vnap.fab.fml.loading;

import java.nio.file.Path;

public enum FMLPaths {
	GAMEDIR, MODSDIR, CONFIGDIR, FMLCONFIG;

	public Path get() {
		var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
		return switch (this) {
			case GAMEDIR -> loader.getGameDir();
			case MODSDIR -> loader.getGameDir().resolve("mods");
			case CONFIGDIR -> loader.getConfigDir();
			case FMLCONFIG -> loader.getConfigDir().resolve("fml.toml");
		};
	}

	public Path relative() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().relativize(get());
	}
}
