package com.vnap.fab.neoforge.event;

import com.vnap.fab.bus.api.Event;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.jspecify.annotations.Nullable;

/**
 * The server's data going to a player who joins, or to all after a reload (Fabric API SYNC_DATA_PACK_CONTENTS). From
 * 1.21.2 a client has no recipes but the ones the server sends: the types asked for here go to it as NeoForge sends
 * them (com.vnap.fab.network.RecipeContent).
 */
public final class OnDatapackSyncEvent extends Event {
	private final PlayerList players;
	private final @Nullable ServerPlayer player;
	//? if >=1.21.2
	/*final java.util.Set<net.minecraft.world.item.crafting.RecipeType<?>> types = new java.util.LinkedHashSet<>();*/

	public OnDatapackSyncEvent(PlayerList players, @Nullable ServerPlayer player) {
		this.players = players;
		this.player = player;
	}

	public PlayerList getPlayerList() {
		return players;
	}

	public @Nullable ServerPlayer getPlayer() {
		return player;
	}

	public java.util.stream.Stream<ServerPlayer> getRelevantPlayers() {
		return player != null ? java.util.stream.Stream.of(player) : List.copyOf(players.getPlayers()).stream();
	}
	//? if >=1.21.2 {

	/*public void sendRecipes(net.minecraft.world.item.crafting.RecipeType<?>... types) {
		java.util.Collections.addAll(this.types, types);
	}

	/^* After the listeners: what they asked for goes to the players. ^/
	public void vnap$send() {
		if (!types.isEmpty()) com.vnap.fab.network.RecipeContent.send(players.getServer(), getRelevantPlayers().toList(), types);
	}
	*///?}
}
