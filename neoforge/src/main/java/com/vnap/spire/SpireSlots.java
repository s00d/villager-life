package com.vnap.spire;

import com.vnap.VillagerLife;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The floors being climbed now, each in a slot of the spire dimension of its own: a spire's floor is one slot for
 * every player on it (they climb together). A slot is built from its plan a few thousand blocks a tick (what stands
 * there and differs from the plan is set; so an old floor is cleared the same way), held loaded by its own tickets
 * and let go a while after the last player left. Nothing of this is saved: a floor is its seed.
 */
public final class SpireSlots {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, VillagerLife.id("spire"));
	/** Slots stand this far apart along X: never in sight of each other. */
	static final int SPACING = 1024;
	/** The slot's floor sits this high in the dimension. */
	static final int BASE_Y = 64;
	/** Cells looked at a tick while building (most are already right): the blocks actually set are fewer. */
	static final int CELLS_PER_TICK = 24_000;
	/** A slot nobody is on is kept this long (a player who died, one stepping out and back). */
	static final int LINGER_TICKS = 1200;
	/** The airlock a player waits in while its floor is drawn: off to the side of every slot, built at once. */
	static final int AIRLOCK_X = -24;

	static final TicketController TICKETS = new TicketController(VillagerLife.id("spire"),
			(level, tickets) -> tickets.getBlockTickets().keySet().forEach(tickets::removeAllTickets));

	public record Key(int spire, int floor) {
	}

	public static final class Slot {
		public final int index;
		public final Key key;
		public final FloorPlan plan;
		/** The world it is built in: the spire dimension (a test builds one in its own). */
		final ServerLevel level;
		final BlockPos origin;
		int cursor;
		boolean built;
		public boolean cleared;
		boolean chestPlaced;
		long empty = -1;
		final Set<UUID> guards = new HashSet<>();
		/** How many guards the floor had, and how many the players were last told of. */
		int total;
		int shown;
		/** Players who asked to be taken in while it was being built. */
		final Set<UUID> waiting = new HashSet<>();
		/** How far its riddle has got (drawn anew with the floor). */
		Puzzles.State riddle = new Puzzles.State();

		Slot(ServerLevel level, int index, Key key, FloorPlan plan, BlockPos origin) {
			this.level = level;
			this.index = index;
			this.key = key;
			this.plan = plan;
			this.origin = origin;
		}

		public BlockPos world(BlockPos local) {
			return origin.offset(local);
		}

		public BoundingBox box() {
			return new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + FloorPlan.SIZE_X - 1,
					origin.getY() + FloorPlan.SIZE_Y - 1, origin.getZ() + FloorPlan.SIZE_Z - 1);
		}

		public boolean ready() {
			return built;
		}

		public BlockPos airlock() {
			return origin.offset(AIRLOCK_X, FloorGenerator.FLOOR + 1, 8);
		}
	}

	private static final Map<Key, Slot> ACTIVE = new HashMap<>();

	private SpireSlots() {
	}

	public static @Nullable ServerLevel level(MinecraftServer server) {
		return server.getLevel(DIMENSION);
	}

	public static boolean isSpire(Level level) {
		return level.dimension().equals(DIMENSION);
	}

	public static List<Slot> active() {
		return new ArrayList<>(ACTIVE.values());
	}

	public static @Nullable Slot at(BlockPos pos) {
		for (Slot slot : ACTIVE.values()) {
			if (slot.box().inflatedBy(32).isInside(pos)) return slot;
		}
		return null;
	}

	/** The slot of this floor, opened (and its building begun) when nobody is on it yet. */
	public static Slot open(ServerLevel level, Spires.Spire spire, int floor) {
		Key key = new Key(spire.id, floor);
		Slot slot = ACTIVE.get(key);
		if (slot != null) return slot;
		Set<Integer> used = new HashSet<>();
		for (Slot other : ACTIVE.values()) used.add(other.index);
		int index = 0;
		while (used.contains(index)) index++;
		BlockPos origin = isSpire(level) ? new BlockPos(index * SPACING, BASE_Y, 0) : new BlockPos(index * SPACING + 2_000_000, BASE_Y, 2_000_000);
		slot = new Slot(level, index, key, FloorGenerator.plan(spire.seed, floor), origin);
		ACTIVE.put(key, slot);
		hold(level, slot, true);
		airlock(level, slot);
		return slot;
	}

	/** Rebuilds the floor from its seed: its guards and everything they did gone (a death on it, nobody else left). */
	public static void reset(ServerLevel level, Slot slot) {
		discardGuards(level, slot);
		slot.cursor = 0;
		slot.built = false;
		slot.cleared = false;
		slot.chestPlaced = false;
		slot.riddle = new Puzzles.State();
	}

	private static void hold(ServerLevel level, Slot slot, boolean on) {
		BoundingBox box = slot.box();
		for (int cx = (box.minX() + AIRLOCK_X) >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				TICKETS.forceChunk(level, slot.origin, cx, cz, on, true);
			}
		}
	}

	/** A small lit room by the slot: where a player waits, dies back to, or comes in to after the server restarted. */
	private static void airlock(ServerLevel level, Slot slot) {
		BlockPos base = slot.origin.offset(AIRLOCK_X - 3, FloorGenerator.FLOOR, 5);
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				for (int y = 0; y < 5; y++) {
					boolean shell = x == 0 || z == 0 || x == 6 || z == 6 || y == 0 || y == 4;
					boolean lamp = y == 4 && x == 3 && z == 3;
					BlockState state = lamp ? Blocks.SEA_LANTERN.defaultBlockState() : shell ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : Blocks.AIR.defaultBlockState();
					level.setBlock(base.offset(x, y, z), state, 2 | 16);
				}
			}
		}
	}

	public static void tick(MinecraftServer server) {
		Puzzles.tick();
		if (ACTIVE.isEmpty()) return;
		for (Slot slot : List.copyOf(ACTIVE.values())) {
			ServerLevel level = slot.level;
			if (!slot.built) {
				build(level, slot);
				continue;
			}
			List<ServerPlayer> on = players(level, slot);
			// nothing under a floor but the void: whoever drops through (a shove into a gap, a glitch) is back at its entry
			for (ServerPlayer player : on) {
				if (player.getY() < slot.origin.getY() + FloorGenerator.FLOOR - 12) {
					SpireTravel.arrive(player, slot);
					com.vnap.Mc.hurt(player, player.damageSources().fall(), 4.0F);
					VillagerLife.trace("vnap spire: " + player.getName().getString() + " fell through floor " + slot.key.floor());
				}
			}
			SpireFloors.tick(level, slot, on);
			if (on.isEmpty()) {
				if (slot.empty < 0) slot.empty = level.getGameTime();
				if (level.getGameTime() - slot.empty > LINGER_TICKS) close(level, slot);
				continue;
			}
			slot.empty = -1;
		}
	}

	private static void build(ServerLevel level, Slot slot) {
		// guards of an earlier visit are saved with the slot's chunks (a restart, a crash): none of them stays
		if (slot.cursor == 0) {
			for (Entity entity : level.getEntities((Entity) null, AABB.of(slot.box()).inflate(8), entity -> !(entity instanceof Player))) entity.discard();
		}
		int total = FloorPlan.SIZE_X * FloorPlan.SIZE_Y * FloorPlan.SIZE_Z;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int end = Math.min(total, slot.cursor + CELLS_PER_TICK);
		for (int i = slot.cursor; i < end; i++) {
			int x = i % FloorPlan.SIZE_X;
			int z = (i / FloorPlan.SIZE_X) % FloorPlan.SIZE_Z;
			int y = i / (FloorPlan.SIZE_X * FloorPlan.SIZE_Z);
			BlockState want = slot.plan.get(x, y, z);
			if (want == null) want = Blocks.AIR.defaultBlockState();
			pos.set(slot.origin.getX() + x, slot.origin.getY() + y, slot.origin.getZ() + z);
			if (!level.getBlockState(pos).equals(want)) level.setBlock(pos, want, 2 | 16 | 32);
		}
		slot.cursor = end;
		if (end < total) return;
		slot.built = true;
		SpireFloors.built(level, slot);
		for (UUID id : slot.waiting) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player != null) SpireTravel.arrive(player, slot);
		}
		slot.waiting.clear();
	}

	public static void close(ServerLevel level, Slot slot) {
		discardGuards(level, slot);
		hold(level, slot, false);
		ACTIVE.remove(slot.key);
	}

	private static void discardGuards(ServerLevel level, Slot slot) {
		for (UUID id : slot.guards) {
			Entity entity = level.getEntity(id);
			if (entity != null) entity.discard();
		}
		slot.guards.clear();
		// and whatever else was left in it: dropped items, arrows
		for (Entity entity : level.getEntities((Entity) null, AABB.of(slot.box()).inflate(4), entity -> !(entity instanceof Player))) {
			entity.discard();
		}
	}

	public static List<ServerPlayer> players(ServerLevel level, Slot slot) {
		BoundingBox box = slot.box();
		List<ServerPlayer> on = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			// a spectator neither holds a floor nor keeps it from being drawn anew
			if (player.isSpectator()) continue;
			BlockPos at = player.blockPosition();
			if (at.getX() >= box.minX() + AIRLOCK_X - 8 && at.getX() <= box.maxX() + 8 && at.getZ() >= box.minZ() - 8 && at.getZ() <= box.maxZ() + 8) {
				on.add(player);
			}
		}
		return on;
	}

	static void clearAll() {
		Puzzles.clear();
		ACTIVE.clear();
	}
}
