package com.vnap.fab.neoforge.common.world.chunk;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * A NeoForge ticket controller on Fabric: vanilla tickets of the mod's own types, not saved (a world loads with none, so
 * no callback has any to see: the mod takes what it needs again). Until 1.21.4 one ticket type with the chunk as its
 * key; from 1.21.5 two registered types, ticking and loaded only. Vanilla keeps one ticket of a kind on a chunk, so the
 * owners who hold it are counted here: the ticket goes with the last of them. A dimension with a ticking one stays
 * awake without players, as with NeoForge's forced chunks (com.vnap.fab.Hooks asks keepsAwake).
 */
public final class TicketController {
	private static final List<TicketController> ALL = new CopyOnWriteArrayList<>();
	private static final Map<ServerLevel, Map<Held, Set<Owner>>> HELD = new java.util.WeakHashMap<>();

	private record Held(int x, int z, boolean ticking) {
	}

	private record Owner(ResourceLocation controller, BlockPos pos) {
	}
	//? if >=1.21.5 {
	/*/^* A ticket that keeps a chunk ticking (as vanilla's forced chunks are), and one that keeps it loaded only. ^/
	private static final net.minecraft.server.level.TicketType TICKING = type(true);
	private static final net.minecraft.server.level.TicketType LOADED = type(false);

	/^* Registers the types (FabricEntry calls it before the registries take the mod's entries). ^/
	public static void types() {
		com.vnap.fab.Bridge.listen(com.vnap.fab.neoforge.registries.RegisterEvent.class, com.vnap.fab.bus.api.EventPriority.NORMAL, false,
				event -> event.register(net.minecraft.core.registries.Registries.TICKET_TYPE, helper -> {
					helper.register(com.vnap.VillagerLife.id("forced"), TICKING);
					helper.register(com.vnap.VillagerLife.id("loaded"), LOADED);
				}));
	}
	*///?} else {
	/** Ticking when the ticket is two levels nearer than a full chunk's (as vanilla's forced chunks are), else loaded only. */
	private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> TYPE =
			net.minecraft.server.level.TicketType.create("villager_life:forced", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));

	public static void types() {
	}
	//?}
	// not saved, no timeout; a ticking one keeps its dimension running as vanilla's forced chunks do
	//? if >=1.21.9 {
	/*private static net.minecraft.server.level.TicketType type(boolean ticking) {
		return new net.minecraft.server.level.TicketType(0L, ticking ? net.minecraft.server.level.TicketType.FLAG_LOADING
				| net.minecraft.server.level.TicketType.FLAG_SIMULATION | net.minecraft.server.level.TicketType.FLAG_KEEP_DIMENSION_ACTIVE
				: net.minecraft.server.level.TicketType.FLAG_LOADING);
	}
	*///?} else if >=1.21.5 {
	/*private static net.minecraft.server.level.TicketType type(boolean ticking) {
		return new net.minecraft.server.level.TicketType(0L, false, ticking ? net.minecraft.server.level.TicketType.TicketUse.LOADING_AND_SIMULATION
				: net.minecraft.server.level.TicketType.TicketUse.LOADING);
	}
	*///?}
	private final ResourceLocation id;

	/** (Its callback has no saved tickets to see.) */
	public TicketController(ResourceLocation id, LoadingValidationCallback callback) {
		this.id = id;
	}

	public ResourceLocation id() {
		return id;
	}

	/** The ticket at the chunk (vanilla's forced chunks' level when ticking, the full level when loaded only). */
	public boolean forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
		var pos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
		Map<Held, Set<Owner>> held = HELD.computeIfAbsent(level, any -> new java.util.HashMap<>());
		Held key = new Held(chunkX, chunkZ, ticking);
		Owner who = new Owner(id, owner.immutable());
		if (add) {
			Set<Owner> owners = held.computeIfAbsent(key, any -> new java.util.HashSet<>());
			if (!owners.add(who) || owners.size() > 1) return true;
		} else {
			Set<Owner> owners = held.get(key);
			if (owners == null || !owners.remove(who)) return true;
			if (!owners.isEmpty()) return true;
			held.remove(key);
		}
		int radius = ticking ? 2 : 0;
		//? if >=1.21.5 {
		/*var type = ticking ? TICKING : LOADED;
		if (add) level.getChunk(chunkX, chunkZ);
		if (add) level.getChunkSource().addTicketWithRadius(type, pos, radius);
		else level.getChunkSource().removeTicketWithRadius(type, pos, radius);
		*///?} else {
		if (add) {
			// the chunk first, as vanilla's forced chunks do: one that a ticket loads while a block is set in it can stay
			// with its entities hidden
			level.getChunk(chunkX, chunkZ);
			level.getChunkSource().addRegionTicket(TYPE, pos, radius, pos);
		} else {
			level.getChunkSource().removeRegionTicket(TYPE, pos, radius, pos);
		}
		//?}
		return true;
	}

	/** Whether a ticking ticket of the mod's is in the level (it keeps ticking with nobody in it). */
	public static boolean keepsAwake(ServerLevel level) {
		Map<Held, Set<Owner>> held = HELD.get(level);
		return held != null && held.keySet().stream().anyMatch(Held::ticking);
	}

	static void register(TicketController controller) {
		if (!ALL.contains(controller)) ALL.add(controller);
	}

	@FunctionalInterface
	public interface LoadingValidationCallback {
		void validateTickets(ServerLevel level, TicketHelper ticketHelper);
	}

	public static final class TicketHelper {
		TicketHelper() {
		}

		public Map<BlockPos, ?> getBlockTickets() {
			return Map.of();
		}

		public void removeAllTickets(BlockPos owner) {
		}
	}
}
