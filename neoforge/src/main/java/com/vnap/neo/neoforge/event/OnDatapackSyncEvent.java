package com.vnap.neo.neoforge.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.jspecify.annotations.Nullable;

/**
 * The server's data going to a player who joins, or to all after a reload. From 1.21.2 a client has no recipes but the
 * ones the server sends: the types asked for here go to it as NeoForge sends them (com.vnap.neo.network.RecipeContent).
 */
public final class OnDatapackSyncEvent extends Event {
	static {
		Bridge.bind(OnDatapackSyncEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.OnDatapackSyncEvent.class, priority, OnDatapackSyncEvent::new,
				event -> {
					sink.accept(event);
					//? if >=1.21.2
					/*com.vnap.neo.network.RecipeContent.send(event.f.getPlayerList().getServer(), event.getRelevantPlayers().toList(), event.types);*/
				}));
	}

	private final net.minecraftforge.event.OnDatapackSyncEvent f;
	//? if >=1.21.2
	/*private final java.util.Set<net.minecraft.world.item.crafting.RecipeType<?>> types = new java.util.LinkedHashSet<>();*/

	OnDatapackSyncEvent(net.minecraftforge.event.OnDatapackSyncEvent f) {
		super(f);
		this.f = f;
	}

	public PlayerList getPlayerList() {
		return f.getPlayerList();
	}

	public @Nullable ServerPlayer getPlayer() {
		return f.getPlayer();
	}

	public java.util.stream.Stream<ServerPlayer> getRelevantPlayers() {
		ServerPlayer player = f.getPlayer();
		return player != null ? java.util.stream.Stream.of(player) : List.copyOf(f.getPlayerList().getPlayers()).stream();
	}
	//? if >=1.21.2 {

	/*public void sendRecipes(net.minecraft.world.item.crafting.RecipeType<?>... types) {
		java.util.Collections.addAll(this.types, types);
	}
	*///?}
}
