package com.vnap.progress;

import com.vnap.Nbt;
import com.vnap.network.DiscoveriesPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a player has come upon (kept with the player, outliving death): a town, its chapel, a caravan, a newborn, the
 * Spire, its fifth floor… The guide shows a chapter or an entry once one of its keys is here (its "unlock" in the
 * book's json), so the book grows as the game is played rather than telling all of it at once.
 */
public final class Discoveries {
	private static final String KEY = "vnap_discovered";
	private static final int EVERY = 40;

	private Discoveries() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) sync(player);
		});
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) sync(player);
		});
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
			if (event.getServer().getTickCount() % EVERY != 0) return;
			for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
				if (!player.isSpectator()) look(player);
			}
		});
	}

	public static Set<String> of(Player player) {
		String joined = Nbt.getString(root(player), KEY);
		return joined.isEmpty() ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(joined.split(",")));
	}

	/** What some discoveries earn (an advancement's own award is once, so it is asked each time: older worlds catch up). */
	private static final java.util.Map<String, String> EARNS = java.util.Map.of("town", "town/root", "chapel", "town/chapel", "wall", "town/wall",
			"caravan", "town/caravan", "spire", "spire/root", "spire_floor", "spire/floor", "spire_notes", "spire/notes", "mega", "mega/root");

	/** Marks it come upon (once), and tells the player's client. */
	public static void discover(Player player, String key) {
		String earns = EARNS.get(key);
		if (earns != null) VillageAdvancements.award(player, earns);
		Set<String> known = of(player);
		if (!known.add(key)) return;
		root(player).putString(KEY, String.join(",", known));
		if (player instanceof ServerPlayer server) sync(server);
	}

	private static CompoundTag root(Player player) {
		CompoundTag persisted = Nbt.getCompound(player.getPersistentData(), Player.PERSISTED_NBT_TAG);
		player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
		return persisted;
	}

	private static void sync(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new DiscoveriesPayload(new ArrayList<>(of(player))));
	}

	/** What is round the player now: a town and what it has built, a caravan, a newborn, an elder, a spire. */
	private static void look(ServerPlayer player) {
		if (!(com.vnap.Mc.levelOf(player) instanceof ServerLevel level)) return;
		// a megastructure's block in the pockets: the book's chapter on them opens
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (com.vnap.mega.MegaBlocks.items().containsValue(player.getInventory().getItem(slot).getItem())) {
				discover(player, "mega");
				break;
			}
		}
		com.vnap.village.town.Roads.Network town = com.vnap.village.town.Roads.get(level.getServer()).near(level.dimension(), player.blockPosition());
		if (town != null && town.bell.closerThan(player.blockPosition(), com.vnap.village.home.Towns.reach(level, town.bell) + 8)) {
			discover(player, "town");
			for (com.vnap.village.town.TownPlots.Kind kind : com.vnap.village.town.TownPlots.Kind.values()) {
				if (com.vnap.village.town.TownPlots.built(town, kind)) discover(player, kind.name().toLowerCase(java.util.Locale.ROOT));
			}
			if (town.wall > 0) discover(player, "wall");
			if (town.cemetery != null) discover(player, "cemetery");
		}
		List<net.minecraft.world.entity.Entity> near = level.getEntities(player, player.getBoundingBox().inflate(32), entity -> true);
		for (net.minecraft.world.entity.Entity entity : near) {
			if (com.vnap.Mc.tags(entity).contains(com.vnap.village.market.Caravan.TAG)) discover(player, "caravan");
			if (entity instanceof com.vnap.entity.golem.TocsinGolem) discover(player, "golem");
			if (entity instanceof Villager villager) {
				if (com.vnap.dialogue.Infants.lying(villager)) discover(player, "infant");
				if (com.vnap.dialogue.Generations.elder(villager)) discover(player, "elder");
			}
		}
		if (com.vnap.spire.SpireSlots.isSpire(level)) {
			discover(player, "spire");
			return;
		}
		for (com.vnap.spire.Spires.Spire spire : com.vnap.spire.Spires.get(level.getServer()).all()) {
			if (spire.dimension.equals(level.dimension()) && spire.gate.closerThan(player.blockPosition(), 128)) discover(player, "spire");
		}
	}
}
