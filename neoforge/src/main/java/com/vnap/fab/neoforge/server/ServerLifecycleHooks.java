package com.vnap.fab.neoforge.server;

import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/** The server running, from its start to its stop (com.vnap.fab.Hooks keeps it). */
public final class ServerLifecycleHooks {
	private static volatile @Nullable MinecraftServer current;

	private ServerLifecycleHooks() {
	}

	public static @Nullable MinecraftServer getCurrentServer() {
		return current;
	}

	public static void vnap$set(@Nullable MinecraftServer server) {
		current = server;
	}
}
