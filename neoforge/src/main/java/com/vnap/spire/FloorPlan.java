package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One floor of a spire as its seed makes it, in coordinates of its slot (0..size): every block, where the climber
 * comes in, the portals, the chest, the note, who guards it. Nothing of it is saved: built again from the seed, it
 * is the same floor (FloorGenerator).
 */
public final class FloorPlan {
	public static final int SIZE_X = 64;
	public static final int SIZE_Y = 48;
	public static final int SIZE_Z = 64;

	public final int floor;
	public final long seed;
	private final @Nullable BlockState[] blocks = new BlockState[SIZE_X * SIZE_Y * SIZE_Z];

	/** Where a climber appears (its feet) and which way it looks. */
	public BlockPos entrance = new BlockPos(SIZE_X / 2, 1, 3);
	public float facing;
	public final List<BlockPos> exitPortal = new ArrayList<>();
	public final List<BlockPos> onwardPortal = new ArrayList<>();
	public BlockPos chest = BlockPos.ZERO;
	public @Nullable BlockPos note;
	/** A note of one of the town's villagers who went up before (a side room's): read or not, as the player likes. */
	public @Nullable BlockPos villagerNote;
	public final List<Spawn> guards = new ArrayList<>();
	/** The guards of the floor's pool (in the water, not on foot): beaten like the rest before the floor clears. */
	public final List<Spawn> swimmers = new ArrayList<>();
	/** The chapter's keeper on every tenth floor: the last of the guards, in the exit room. */
	public @Nullable Spawn keeper;
	/** The Schism's pair (Genome.schism floors): the Mason, then the Surveyor; empty on other floors. */
	public final List<Spawn> duo = new ArrayList<>();
	/** One elite an arena (SpireGuard.elite), from floor 8. */
	public final List<Spawn> elites = new ArrayList<>();
	/** Past the hundredth floor: keepers of other chapters beside the floor's own (spawn → its chapter). */
	public final java.util.Map<Spawn, Integer> companions = new java.util.LinkedHashMap<>();
	/** A secret room's cache (behind a cracked wall), null on most floors. */
	public @Nullable BlockPos secret;
	/** A side room's riddle (Puzzles), null on most floors. */
	public @Nullable Puzzle puzzle;
	/** The theme's name key (spire.villager_life.theme.<name>) shown on coming in. */
	public String theme = "roots";

	/**
	 * A side room's riddle: its kind, its pieces in order (levers, chimes, plates), the glyphs that show it (one over each
	 * lever; the chimes' conductor), the tune the chimes are to be played in (their indices), and where its cache rises.
	 */
	public record Puzzle(Puzzles.Kind kind, List<BlockPos> parts, List<BlockPos> glyphs, List<Integer> tune, BlockPos cache) {
	}

	/** A guard of the floor: where it stands and its genome (Genome). Arena: which fight it belongs to. */
	public record Spawn(BlockPos pos, long genome, int arena) {
	}

	public FloorPlan(int floor, long seed) {
		this.floor = floor;
		this.seed = seed;
	}

	public static boolean inside(int x, int y, int z) {
		return x >= 0 && y >= 0 && z >= 0 && x < SIZE_X && y < SIZE_Y && z < SIZE_Z;
	}

	private static int index(int x, int y, int z) {
		return (y * SIZE_Z + z) * SIZE_X + x;
	}

	public void set(int x, int y, int z, @Nullable BlockState state) {
		if (inside(x, y, z)) blocks[index(x, y, z)] = state;
	}

	public void set(BlockPos pos, @Nullable BlockState state) {
		set(pos.getX(), pos.getY(), pos.getZ(), state);
	}

	/** The block here, null for air. */
	public @Nullable BlockState get(int x, int y, int z) {
		return inside(x, y, z) ? blocks[index(x, y, z)] : null;
	}

	public boolean solid(int x, int y, int z) {
		BlockState state = get(x, y, z);
		if (state == null || state.isAir()) return false;
		// a door is a way through: a touch opens it
		if (state.getBlock() instanceof SpireDoorBlock) return false;
		// a carpet, a petal, a low slab is walked over, not round
		net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return !shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) > 0.5;
	}

	public boolean empty(int x, int y, int z) {
		BlockState state = get(x, y, z);
		return !solid(x, y, z) && (state == null || state.getFluidState().isEmpty());
	}
}
