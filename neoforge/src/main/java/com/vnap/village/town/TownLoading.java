package com.vnap.village.town;

import com.vnap.VillagerLife;
import com.vnap.config.LifeSettings;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A town lives whole while a player is in it or near: every chunk of its land (its reach round the bell) is kept loaded
 * and ticking, the way MineColonies keeps a colony loaded while its owner is in part of it, and for a few minutes after
 * the last player left (LifeSettings.townLingerMinutes; MineColonies' loadtime). Nothing of a town ever runs with no
 * player about (no farm of chunks the world never sees); but with one about, its far streets, lots, workshops and
 * people are all there at once, not just the half the player's view distance reaches. Off with townChunkLoading.
 * The tickets are the mod's own (a TicketController) and dropped when the world loads: they are taken again as
 * players arrive.
 */
public final class TownLoading {
	/** A player this far beyond the town's reach still keeps it awake (walking up to it, flying over). */
	static final int NEARBY = 32;
	/** Ticks between looks at where the players are. */
	static final int EVERY = 40;
	static final TicketController TICKETS = new TicketController(VillagerLife.id("town"),
			(level, tickets) -> tickets.getBlockTickets().keySet().forEach(tickets::removeAllTickets));

	/** The towns held now: by bell, the chunks forced for it and until when (game time) once nobody is near. */
	private static final Map<Key, Held> HELD = new HashMap<>();

	private record Key(ResourceKey<Level> dimension, BlockPos bell) {
	}

	private static final class Held {
		final Set<Long> chunks = new HashSet<>();
		long until;
	}

	private TownLoading() {
	}

	public static void register(IEventBus modBus) {
		modBus.addListener((RegisterTicketControllersEvent event) -> event.register(TICKETS));
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
			if (event.getServer().getTickCount() % EVERY == 0) tick(event.getServer());
		});
		NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> HELD.clear());
	}

	/** Whether the town of this bell is held loaded now. */
	public static boolean held(ServerLevel level, BlockPos bell) {
		Mayors.Office office = Towns.of(level, bell);
		return office != null && HELD.containsKey(new Key(level.dimension(), office.bell));
	}

	static void tick(MinecraftServer server) {
		Set<Key> towns = new HashSet<>();
		boolean on = LifeSettings.townChunkLoading();
		for (Mayors.Office office : Mayors.get(server).offices()) {
			ServerLevel level = server.getLevel(office.dimension);
			if (level == null) continue;
			Key key = new Key(office.dimension, office.bell);
			towns.add(key);
			Held held = HELD.get(key);
			// nobody within the largest a town can be: nothing to work out (the reach and the roads are a look over every
			// office and network; with hundreds of towns, every 40 ticks, it was a third of a game test run)
			boolean anyone = on && near(level, office.bell, TownRules.MAX_REACH + NEARBY);
			if (!anyone && held == null) continue;
			// the whole of its possible land while its land is being surveyed (TownSurvey), then the land it has
			Roads.Network network = Roads.get(server).near(office.dimension, office.bell);
			double reach = network != null && network.capacity < 0 ? TownRules.MAX_REACH : Towns.reach(level, office.bell);
			if (anyone && near(level, office.bell, reach + NEARBY)) {
				if (held == null) {
					// a server with many players in many towns: a ceiling on the towns held whole (each ~130 chunks)
					if (HELD.size() >= TownRules.MAX_HELD_TOWNS) continue;
					held = new Held();
					HELD.put(key, held);
				}
				held.until = level.getGameTime() + LifeSettings.townLingerMinutes() * 1200L;
				hold(level, office.bell, reach, held);
			} else if (held != null && (!on || level.getGameTime() > held.until)) {
				release(level, office.bell, held);
				HELD.remove(key);
			}
		}
		// towns gone (offices merged or removed): their chunks go back
		for (Iterator<Map.Entry<Key, Held>> it = HELD.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Key, Held> entry = it.next();
			if (towns.contains(entry.getKey())) continue;
			ServerLevel level = server.getLevel(entry.getKey().dimension());
			if (level != null) release(level, entry.getKey().bell(), entry.getValue());
			it.remove();
		}
	}

	private static boolean near(ServerLevel level, BlockPos bell, double distance) {
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			double dx = player.getX() - bell.getX();
			double dz = player.getZ() - bell.getZ();
			if (dx * dx + dz * dz <= distance * distance) return true;
		}
		return false;
	}

	/** Every chunk the town's land touches is forced (ticking); chunks it no longer reaches go back. */
	private static void hold(ServerLevel level, BlockPos bell, double reach, Held held) {
		Set<Long> wanted = new HashSet<>();
		int r = (int) Math.ceil(reach / 16.0) + 1;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int cx = (bell.getX() >> 4) + dx;
				int cz = (bell.getZ() >> 4) + dz;
				double nx = Math.max(cx << 4, Math.min(bell.getX(), (cx << 4) + 15)) - bell.getX();
				double nz = Math.max(cz << 4, Math.min(bell.getZ(), (cz << 4) + 15)) - bell.getZ();
				if (nx * nx + nz * nz <= reach * reach) wanted.add(com.vnap.Mc.chunkKey(cx, cz));
			}
		}
		for (long chunk : wanted) {
			if (held.chunks.add(chunk)) TICKETS.forceChunk(level, bell, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true, true);
		}
		for (Iterator<Long> it = held.chunks.iterator(); it.hasNext(); ) {
			long chunk = it.next();
			if (wanted.contains(chunk)) continue;
			TICKETS.forceChunk(level, bell, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, true);
			it.remove();
		}
	}

	private static void release(ServerLevel level, BlockPos bell, Held held) {
		for (long chunk : held.chunks) TICKETS.forceChunk(level, bell, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, true);
		held.chunks.clear();
	}

	/** Debug (/vnap town): how many chunks each held town keeps loaded. */
	public static String describe(ServerLevel level, BlockPos bell) {
		Mayors.Office office = Towns.of(level, bell);
		Held held = office == null ? null : HELD.get(new Key(level.dimension(), office.bell));
		return held == null ? "not held" : "held: " + held.chunks.size() + " chunks, " + Math.max(0, (held.until - level.getGameTime()) / 20) + " s after the last player";
	}
}
