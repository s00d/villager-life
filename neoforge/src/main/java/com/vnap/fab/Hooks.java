package com.vnap.fab;

import com.vnap.Mc;
import com.vnap.fab.neoforge.event.entity.player.PlayerEvent;
import com.vnap.fab.neoforge.event.entity.player.PlayerInteractEvent;
import com.vnap.fab.neoforge.server.ServerLifecycleHooks;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;

/** Fabric API's callbacks as the shim's NeoForge events, on both sides (the client's own: ClientHooks). */
final class Hooks {
	//? if <26.1
	private static boolean traded;

	private Hooks() {
	}

	static void common() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			ServerLifecycleHooks.vnap$set(server);
			Bridge.fire(new com.vnap.fab.neoforge.event.server.ServerAboutToStartEvent(server));
			trades();
			brewing();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Bridge.fire(new com.vnap.fab.neoforge.event.server.ServerStartingEvent(server));
			Bridge.fire(new com.vnap.fab.neoforge.event.server.ServerStartedEvent(server));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Bridge.fire(new com.vnap.fab.neoforge.event.server.ServerStoppingEvent(server)));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerLifecycleHooks.vnap$set(null));
		ServerTickEvents.START_WORLD_TICK.register(level -> {
			if (com.vnap.fab.neoforge.common.world.chunk.TicketController.keepsAwake(level)) level.resetEmptyTime();
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> Bridge.fire(new com.vnap.fab.neoforge.event.tick.ServerTickEvent.Pre(server)));
		ServerTickEvents.END_SERVER_TICK.register(server -> Bridge.fire(new com.vnap.fab.neoforge.event.tick.ServerTickEvent.Post(server)));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
				Bridge.fire(new com.vnap.fab.neoforge.event.RegisterCommandsEvent(dispatcher, context, selection)));

		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> Bridge.fire(new com.vnap.fab.neoforge.event.entity.EntityLeaveLevelEvent(entity, level)));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
				!Bridge.fire(new com.vnap.fab.neoforge.event.entity.living.LivingDeathEvent(entity, source)).isCanceled());

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				Bridge.fire(new com.vnap.fab.neoforge.event.entity.player.AttackEntityEvent(player, entity)).isCanceled() ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> interaction(new PlayerInteractEvent.RightClickBlock(player, hand, hit)));
		//? if >=1.21.2 {
		/*UseItemCallback.EVENT.register((player, level, hand) -> interaction(new PlayerInteractEvent.RightClickItem(player, hand)));
		*///?} else {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			InteractionResult result = interaction(new PlayerInteractEvent.RightClickItem(player, hand));
			return new net.minecraft.world.InteractionResultHolder<>(result, player.getItemInHand(hand));
		});
		//?}
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			// NeoForge's EntityInteract is the plain use of an entity (from 26.1 the only one: the one at a point merged into it)
			//? if <26.1
			if (hit != null) return InteractionResult.PASS;
			return interaction(new PlayerInteractEvent.EntityInteract(player, hand, entity));
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) ->
				!Bridge.fire(new com.vnap.fab.neoforge.event.level.BlockEvent.BreakEvent(level, pos, state, player)).isCanceled());

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Bridge.fire(new PlayerEvent.PlayerLoggedInEvent(handler.getPlayer())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Bridge.fire(new PlayerEvent.PlayerLoggedOutEvent(handler.getPlayer())));
		ServerPlayerEvents.COPY_FROM.register((original, player, alive) -> Bridge.fire(new PlayerEvent.Clone(player, original, !alive)));
		ServerPlayerEvents.AFTER_RESPAWN.register((original, player, alive) -> Bridge.fire(new PlayerEvent.PlayerRespawnEvent(player, alive)));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) ->
				Bridge.fire(new PlayerEvent.PlayerChangedDimensionEvent(player, from.dimension(), to.dimension())));

		ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
			//? if >=1.21.2 {
			/*Bridge.fire(new com.vnap.fab.neoforge.event.OnDatapackSyncEvent(((net.minecraft.server.level.ServerLevel) Mc.levelOf(player)).getServer().getPlayerList(), player)).vnap$send();
			*///?} else
			Bridge.fire(new com.vnap.fab.neoforge.event.OnDatapackSyncEvent(((net.minecraft.server.level.ServerLevel) Mc.levelOf(player)).getServer().getPlayerList(), player));
		});
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> Bridge.fire(new com.vnap.fab.neoforge.event.TagsUpdatedEvent(client
				? com.vnap.fab.neoforge.event.TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED : com.vnap.fab.neoforge.event.TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD)));

		Bridge.fire(new com.vnap.fab.neoforge.event.entity.EntityAttributeCreationEvent());
	}

	/**
	 * The brewing stand's recipes of the mod, made again as each server starts (as NeoForge builds them with the
	 * server's brewing): the items' components are only bound by then. Recipes from 26.3.
	 */
	private static void brewing() {
		//? if <26.3 {
		com.vnap.fab.neoforge.event.brewing.RegisterBrewingRecipesEvent.RECIPES.clear();
		Bridge.fire(new com.vnap.fab.neoforge.event.brewing.RegisterBrewingRecipesEvent());
		//?}
	}

	/** A use: cancelled, its result is the interaction's; else the game goes on. */
	private static InteractionResult interaction(PlayerInteractEvent.Interaction event) {
		Bridge.fire(event);
		return event.isCanceled() ? event.getCancellationResult() : InteractionResult.PASS;
	}

	/**
	 * Vanilla's table of trades, once: each profession's to its listeners (NeoForge builds its event from the same
	 * table), what they leave written back. Trades are data from 26.1.
	 */
	private static void trades() {
		//? if <26.1 {
		if (traded) return;
		traded = true;
		for (var entry : net.minecraft.world.entity.npc.VillagerTrades.TRADES.entrySet()) {
			var levels = new Int2ObjectOpenHashMap<List<net.minecraft.world.entity.npc.VillagerTrades.ItemListing>>();
			for (int level = 1; level <= 5; level++) {
				net.minecraft.world.entity.npc.VillagerTrades.ItemListing[] listings = entry.getValue().get(level);
				levels.put(level, new ArrayList<>(listings == null ? List.of() : Arrays.asList(listings)));
			}
			Bridge.fire(new com.vnap.fab.neoforge.event.village.VillagerTradesEvent(levels, entry.getKey()));
			for (var level : levels.int2ObjectEntrySet()) entry.getValue().put(level.getIntKey(), level.getValue().toArray(net.minecraft.world.entity.npc.VillagerTrades.ItemListing[]::new));
		}
		//?}
	}
}
