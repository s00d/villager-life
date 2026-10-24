package com.vnap.spire.outer;

import com.vnap.VillagerLife;
import com.vnap.spire.SpireProgress;
import com.vnap.spire.SpireSlots;
import com.vnap.spire.Spires;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Outer Gates (docs/tower/PORTALS.md): three portals in the hall at a spire's foot that open to whoever climbed past
 * its hundredth floor. Each takes in a run of its own — the Colosseum's waves, the Sky Path's islands, the Nameless's
 * bosses — far off in the spire's dimension, one place a run, for the player and whoever stepped in with it. A run
 * ends when nobody is left in it (out by its exit, dead: the dead wake at the spire's gate with their things, as on
 * a floor). What it gives is given as it is won; the best of each kind is kept with the player.
 */
public final class Outer {
	/** How far apart two runs lie, and where the first one is (well off the floors' row along X). */
	static final int SPACING = 4096;
	static final int ROW_Z = 1_000_000;
	static final int BASE_Y = 120;
	/** Between two Sky Path runs (each spreads every way as far as its players go). */
	static final int ISLES_APART = 200_000;
	/** A run others may still join until it is this far on (a wave, a waystone, a boss). */
	static final int JOIN_UNTIL = 2;
	/** Players who step in within this long of the first join its run. */
	static final int GATHER = 200;
	/** A run nobody is in any more goes after this long. */
	private static final int EMPTY = 100;
	/** The floor a player must have climbed past. */
	public static final int OPENS_PAST = 100;
	private static final Map<Integer, Run> RUNS = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Outer() {
	}

	/** The three gates, and their places' kinds. */
	public enum Kind {
		ARENA, ISLES, RUSH;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** One run: its place, its players, how far it has come. */
	public static final class Run {
		final int index;
		public final Kind kind;
		final ServerLevel level;
		public final BlockPos origin;
		final int spire;
		final long seed;
		public final Loadout loadout;
		final Set<UUID> players = new LinkedHashSet<>();
		final long opened;
		/** The wave, the checkpoint, the boss: how far it has come (0 before the first). */
		public int stage;
		/** Ticks to the next thing the run does (a wave's coming, a boss's). */
		int timer;
		long empty = -1;
		/** Its living guards (the run's own, by id). */
		final Set<UUID> guards = new HashSet<>();
		/** The Sky Path's: lives left and where a fall puts a player back. */
		int lives = 3;
		BlockPos checkpoint;
		/** The Nameless's: bosses down this run (each makes the next stronger and the players a little stronger). */
		int felled;
		/** A wave or a boss is on (the Colosseum, the Nameless), and how many came in it. */
		boolean fighting;
		int total;
		/** The Colosseum's hardships so far. */
		final List<String> mods = new ArrayList<>();
		/** The Sky Path's: its cells drawn now (by key), the waystones and guards' islands already come to. */
		final Map<Long, Isles.Cell> cells = new HashMap<>();
		final Set<Long> reached = new HashSet<>();

		Run(int index, Kind kind, ServerLevel level, BlockPos origin, int spire, long seed, Loadout loadout, long opened) {
			this.index = index;
			this.kind = kind;
			this.level = level;
			this.origin = origin;
			this.spire = spire;
			this.seed = seed;
			this.loadout = loadout;
			this.opened = opened;
			this.checkpoint = origin;
		}

		/** Its own guard: counted with the run's, and marked as the run's (a closed run's guard never comes back from disk). */
		void adopt(net.minecraft.world.entity.Entity guard) {
			guards.add(guard.getUUID());
			guard.getPersistentData().putLong(com.vnap.spire.SpireGuards.OUTER, seed);
		}

		/** Its place: everything of it lies within. */
		public AABB box() {
			if (kind == Kind.ISLES) return new AABB(origin).inflate(ISLES_APART / 2.0 - 64, 256, ISLES_APART / 2.0 - 64);
			return new AABB(origin).inflate(64, 96, 64);
		}

		public List<ServerPlayer> players() {
			List<ServerPlayer> out = new ArrayList<>();
			for (UUID id : players) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
				if (player != null && player.isAlive() && !player.isSpectator()
						&& player.level().equals(level) && box().contains(player.position())) out.add(player);
			}
			return out;
		}

		/** How many are in it now (at least one): the waves, the islands' guards grow with them. */
		public int party() {
			return Math.max(1, players().size());
		}

		public java.util.Set<UUID> guards() {
			return guards;
		}

		/** Whether one of its players is still in it at all (one watching as a spectator keeps it, but does not play). */
		boolean anyone() {
			for (UUID id : players) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
				if (player != null && player.isAlive() && player.level().equals(level) && box().contains(player.position())) return true;
			}
			return false;
		}

		public int livingGuards() {
			guards.removeIf(id -> {
				Entity guard = level.getEntity(id);
				return guard == null || !guard.isAlive();
			});
			return guards.size();
		}
	}

	public static List<Run> active() {
		return new ArrayList<>(RUNS.values());
	}

	public static @Nullable Run at(BlockPos pos) {
		for (Run run : RUNS.values()) if (run.box().contains(net.minecraft.world.phys.Vec3.atCenterOf(pos))) return run;
		return null;
	}

	/** A player in an Outer Gate's portal: into its run (a new one, or the one just opened by its friends). */
	public static void enter(ServerPlayer player, Kind kind, BlockPos portal) {
		enter(player, kind, portal, false);
	}

	/** The same, past the hundredth floor or not when {@code anyway} (a debug command's: the dev bot's runs). */
	public static void enter(ServerPlayer player, Kind kind, BlockPos portal, boolean anyway) {
		ServerLevel level = com.vnap.Mc.serverLevel(player);
		Spires.Spire spire = Spires.get(level.getServer()).around(level.dimension(), portal);
		if (spire != null) enter(player, kind, spire, anyway);
	}

	/** Into a run of this spire's gate, from wherever the player stands (its hall, or one of its floors). */
	public static void enter(ServerPlayer player, Kind kind, Spires.Spire spire, boolean anyway) {
		ServerLevel level = com.vnap.Mc.serverLevel(player);
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < 40) return;
		LAST.put(player.getUUID(), now);
		ServerLevel spireLevel = SpireSlots.level(level.getServer());
		if (spireLevel == null) return;
		if (SpireProgress.floor(player, spire.id) <= OPENS_PAST && !player.isCreative() && !anyway) {
			com.vnap.Mc.say(player, Component.translatable("outer.villager_life.closed", OPENS_PAST), true);
			return;
		}
		// one gate, many players: back into its own run after a death; else into one that has just begun (its first
		// waves, its first waystones, its first boss), with whoever is in it; else a run of its own
		Run run = null;
		for (Run open : RUNS.values()) {
			if (open.kind != kind || open.spire != spire.id) continue;
			if (open.players.contains(player.getUUID()) && !open.players().isEmpty()) {
				run = open;
				break;
			}
			boolean fresh = open.stage == 0 && now - open.opened < GATHER;
			if (fresh || (open.stage <= JOIN_UNTIL && !open.players().isEmpty())) run = open;
		}
		if (run == null) run = open(spireLevel, kind, spire.id, Loadout.of(player), now);
		run.players.add(player.getUUID());
		SpireProgress.setReturn(player, spire.dimension, spire.front, spire.id);
		BlockPos at = start(run);
		com.vnap.Mc.teleport(player, spireLevel, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
		spireLevel.playSound(null, at, com.vnap.sound.SpireSounds.PORTAL_IN, SoundSource.PLAYERS, 1.0F, 1.0F);
		com.vnap.Mc.say(player, Component.translatable("outer.villager_life.enter." + kind.id(), run.loadout.floor()), false);
		com.vnap.progress.VillageAdvancements.award(player, "spire/outer");
		VillagerLife.trace("vnap outer: " + player.getName().getString() + " into " + kind.id() + " run " + run.index + " at floor " + run.loadout.floor()
				+ " " + run.loadout.style());
	}

	static Run open(ServerLevel level, Kind kind, int spire, Loadout loadout, long now) {
		Set<Integer> used = RUNS.keySet();
		int index = 0;
		while (used.contains(index)) index++;
		// the Sky Path spreads every way without end: each run far from the next
		BlockPos origin = kind == Kind.ISLES ? new BlockPos(index * ISLES_APART, BASE_Y, ROW_Z + SPACING * 64 + ISLES_APART)
				: new BlockPos(index * SPACING, BASE_Y, ROW_Z + (kind == Kind.ARENA ? 0 : 2) * SPACING * 64);
		Run run = new Run(index, kind, level, origin, spire, level.getRandom().nextLong(), loadout, now);
		RUNS.put(index, run);
		switch (kind) {
			case ARENA -> Arena.build(run);
			case ISLES -> Isles.build(run);
			case RUSH -> Rush.build(run);
		}
		return run;
	}

	/** A run at a place of the caller's (a test's), not in its row in the spire's dimension. */
	public static Run openAt(ServerLevel level, Kind kind, BlockPos origin, Loadout loadout) {
		int index = -1 - RUNS.size();
		while (RUNS.containsKey(index)) index--;
		Run run = new Run(index, kind, level, origin, -1, level.getRandom().nextLong(), loadout, level.getGameTime());
		RUNS.put(index, run);
		switch (kind) {
			case ARENA -> Arena.build(run);
			case ISLES -> Isles.build(run);
			case RUSH -> Rush.build(run);
		}
		return run;
	}

	/** One tick of a run's own work with these players (what the server's tick does; a test's own). */
	public static void step(Run run, List<ServerPlayer> on) {
		switch (run.kind) {
			case ARENA -> Arena.tick(run, on);
			case ISLES -> Isles.tick(run, on);
			case RUSH -> Rush.tick(run, on);
		}
	}

	/** Whether an Outer run with this mark is open (SpireEvents: a guard of a closed one does not load back from disk). */
	public static boolean open(long seed) {
		for (Run run : RUNS.values()) if (run.seed == seed) return true;
		return false;
	}

	/** A test's run gone, as the server's goes when nobody is left in it. */
	public static void end(Run run) {
		close(run);
	}

	/** Where a player comes in (and back after a fall on the Sky Path). */
	public static BlockPos start(Run run) {
		return switch (run.kind) {
			case ARENA -> Arena.start(run);
			case ISLES -> run.checkpoint.above();
			case RUSH -> Rush.start(run);
		};
	}

	public static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) return;
		for (Run run : List.copyOf(RUNS.values())) {
			List<ServerPlayer> on = run.players();
			if (!run.anyone()) {
				if (run.empty < 0) run.empty = run.level.getGameTime();
				if (run.level.getGameTime() - run.empty > EMPTY) close(run);
				continue;
			}
			run.empty = -1;
			if (!on.isEmpty()) step(run, on);
		}
	}

	/** Gone: its guards, its blocks, its record kept with each who was in it. */
	static void close(Run run) {
		for (UUID id : run.guards) {
			Entity guard = run.level.getEntity(id);
			if (guard != null) guard.discard();
		}
		switch (run.kind) {
			case ARENA -> Arena.clear(run);
			case ISLES -> Isles.clear(run);
			case RUSH -> Rush.clear(run);
		}
		RUNS.remove(run.index);
		VillagerLife.trace("vnap outer: run " + run.index + " " + run.kind.id() + " closed at " + run.stage);
	}

	/** A player out of a run by its exit: to the spire's foot. */
	public static void leave(ServerPlayer player) {
		Run run = at(player.blockPosition());
		if (run != null) run.players.remove(player.getUUID());
		com.vnap.spire.SpireTravel.leave(player);
	}

	/** Its best of this kind, kept with the player (and told when beaten). */
	static void record(ServerPlayer player, Run run) {
		var root = com.vnap.Nbt.getCompound(player.getPersistentData(), Player.PERSISTED_NBT_TAG);
		player.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
		String key = "vnap_outer_" + run.kind.id();
		if (run.kind == Kind.ARENA && run.stage >= 20) com.vnap.progress.VillageAdvancements.award(player, "spire/arena");
		if (run.kind == Kind.ISLES && run.stage >= 20) com.vnap.progress.VillageAdvancements.award(player, "spire/isles");
		if (run.kind == Kind.RUSH && run.stage >= 10) com.vnap.progress.VillageAdvancements.award(player, "spire/rush");
		if (run.stage > com.vnap.Nbt.getInt(root, key)) {
			root.putInt(key, run.stage);
			com.vnap.Mc.say(player, Component.translatable("outer.villager_life.record." + run.kind.id(), run.stage), false);
		}
	}

	public static int best(Player player, Kind kind) {
		return com.vnap.Nbt.getInt(com.vnap.Nbt.getCompound(player.getPersistentData(), Player.PERSISTED_NBT_TAG), "vnap_outer_" + kind.id());
	}

	/** A run's reward to everyone in it: shards and coins as a floor of its strength, and parts of its gate. */
	static void reward(Run run, List<ServerPlayer> on, int strength, int parts) {
		for (ServerPlayer player : on) {
			give(player, new ItemStack(com.vnap.item.LifeItems.CORE_SHARD, com.vnap.spire.SpireRewards.shards(strength)));
			com.vnap.village.market.Coins.give(player, com.vnap.spire.SpireRewards.coins(strength));
			if (parts > 0) {
				if (com.vnap.config.LifeSettings.megastructures()) give(player, new ItemStack(part(run.kind), parts));
				else give(player, new ItemStack(com.vnap.item.LifeItems.CORE_SHARD, 8 * parts));
			}
			com.vnap.Mc.playTo(player, com.vnap.sound.SpireSounds.CHEST, SoundSource.PLAYERS, 1.0F, 1.0F);
			record(player, run);
		}
	}

	static net.minecraft.world.level.block.Block part(Kind kind) {
		return switch (kind) {
			case ARENA -> com.vnap.mega.MegaBlocks.PART_COLOSSEUM;
			case ISLES -> com.vnap.mega.MegaBlocks.PART_PATH;
			case RUSH -> com.vnap.mega.MegaBlocks.PART_NAMELESS;
		};
	}

	static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) com.vnap.Mc.toss(player, stack);
	}

	/** The run's word on everyone's screen: the stage, its kind, what still stands. */
	static void tell(Run run, List<ServerPlayer> on, int standing, int total, boolean cleared, boolean arrived) {
		var payload = new com.vnap.network.SpireFloorPayload(Math.max(1, run.stage), "outer_" + run.kind.id(), standing, total, cleared, arrived);
		for (ServerPlayer player : on) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
	}

	public static void forget(UUID player) {
		LAST.remove(player);
	}

	/** The server stopped: no run outlives it (their places are far off and drawn anew). */
	public static void clear() {
		RUNS.clear();
		LAST.clear();
	}
}
