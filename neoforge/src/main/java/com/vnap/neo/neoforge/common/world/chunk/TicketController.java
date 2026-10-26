package com.vnap.neo.neoforge.common.world.chunk;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * A NeoForge ticket controller on Forge. Until 1.21.4 on Forge's chunk manager: Forge keeps one set of tickets a mod
 * (the mod's id), so every controller of the mod forces chunks under it, and at a world's load each controller's
 * callback sees them all. From 1.21.5 (Forge's manager is gone) on vanilla's tickets of the mod's own types, which are
 * not saved: a world loads with none, so no callback has any to see (the mod takes what it needs again).
 */
public final class TicketController {
	private static final List<TicketController> ALL = new CopyOnWriteArrayList<>();
	//? if >=1.21.5 {
	/*/^* A ticket that keeps a chunk ticking (as vanilla's forced chunks are), and one that keeps it loaded only. ^/
	private static final net.minecraft.server.level.TicketType TICKING = type(true);
	private static final net.minecraft.server.level.TicketType LOADED = type(false);

	/^* Registers the types (ForgeEntry calls it before the registries are filled). ^/
	public static void types() {
		com.vnap.neo.Bridge.listen(com.vnap.neo.neoforge.registries.RegisterEvent.class, com.vnap.neo.bus.api.EventPriority.NORMAL, false,
				event -> event.register(net.minecraft.core.registries.Registries.TICKET_TYPE, helper -> {
					helper.register(com.vnap.VillagerLife.id("forced"), TICKING);
					helper.register(com.vnap.VillagerLife.id("loaded"), LOADED);
				}));
	}
	*///?}
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
	//? if >=1.21.5 {
	/*/^* (Its callback has no saved tickets to see.) ^/
	public TicketController(ResourceLocation id, LoadingValidationCallback callback) {
		this.id = id;
	}
	*///?} else {
	private final LoadingValidationCallback callback;

	public TicketController(ResourceLocation id, LoadingValidationCallback callback) {
		this.id = id;
		this.callback = callback;
	}
	//?}

	public ResourceLocation id() {
		return id;
	}

	//? if >=1.21.5 {
	/*/^* The ticket at the chunk (vanilla's forced chunks' level when ticking, the full level when loaded only). ^/
	public boolean forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
		var pos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
		var type = ticking ? TICKING : LOADED;
		int radius = ticking ? 2 : 0;
		if (add) level.getChunkSource().addTicketWithRadius(type, pos, radius);
		else level.getChunkSource().removeTicketWithRadius(type, pos, radius);
		return true;
	}

	static void register(TicketController controller) {
		if (!ALL.contains(controller)) ALL.add(controller);
	}
	*///?} else {
	public boolean forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
		return net.minecraftforge.common.world.ForgeChunkManager.forceChunk(level, id.getNamespace(), owner, chunkX, chunkZ, add, ticking);
	}

	static void register(TicketController controller) {
		if (ALL.isEmpty()) {
			net.minecraftforge.common.world.ForgeChunkManager.setForcedChunkLoadingCallback(controller.id.getNamespace(),
					(level, tickets) -> ALL.forEach(each -> each.callback.validateTickets(level, new TicketHelper(tickets))));
		}
		if (!ALL.contains(controller)) ALL.add(controller);
	}
	//?}

	@FunctionalInterface
	public interface LoadingValidationCallback {
		void validateTickets(ServerLevel level, TicketHelper ticketHelper);
	}

	//? if >=1.21.5 {
	/*public static final class TicketHelper {
		TicketHelper() {
		}

		public Map<BlockPos, ?> getBlockTickets() {
			return Map.of();
		}

		public void removeAllTickets(BlockPos owner) {
		}
	}
	*///?} else {
	public static final class TicketHelper {
		private final net.minecraftforge.common.world.ForgeChunkManager.TicketHelper forge;

		TicketHelper(net.minecraftforge.common.world.ForgeChunkManager.TicketHelper forge) {
			this.forge = forge;
		}

		public Map<BlockPos, ?> getBlockTickets() {
			return forge.getBlockTickets();
		}

		public void removeAllTickets(BlockPos owner) {
			forge.removeAllTickets(owner);
		}
	}
	//?}
}
