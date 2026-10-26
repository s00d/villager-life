package com.vnap.neo.client;

import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** The client's side of the shim, loaded only when a client asks for it. */
public final class ClientAccess {
	private ClientAccess() {
	}

	public static Player player() {
		return Objects.requireNonNull(Minecraft.getInstance().player, "a payload with no player to take it");
	}
}
