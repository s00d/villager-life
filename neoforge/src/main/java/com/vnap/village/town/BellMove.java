package com.vnap.village.town;

import com.vnap.village.home.Mayors;
import com.vnap.village.home.Neighbours;
import com.vnap.village.home.Placements;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Shops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A village the world generated with its bell down in a cave under a mountain's crust (a taiga village's meeting
 * point under sixteen blocks of rock, its houses on the slopes above): the villagers who knew the bell were shut in
 * with it, the others never found it. The town hangs a new bell on the open ground above, by hand (Placements), and
 * once it rings there the old one is taken down: the office, the stall, the roads, the neighbours and every
 * villager's meeting point go over to the new one.
 */
public final class BellMove {
	/** Ground over the bell this much higher: it is buried, not under a roof. */
	static final int BURIED = 6;
	/** How far round the old bell the new one may hang. */
	static final int SEARCH = 24;
	/** How far round the bell open ground at its level is looked for. */
	static final int OPEN_GROUND = 12;
	public static final String MARK = "#bell";

	private BellMove() {
	}

	/** Daily (Roads.plan): a buried bell of a world village gets a new one on the surface, set down by hand. */
	static void plan(ServerLevel level, Roads roads, Roads.Network network) {
		BlockPos bell = network.bell;
		BlockPos moving = network.bellMove;
		// the new bell's job is gone with no bell hung (its cell was built on, Placements dropped it): looked for anew,
		// or the town would wait on it for good (Roads.plan plans nothing while a bell is moving)
		if (moving != null && level.isLoaded(moving) && !Placements.pending(level, moving) && !level.getBlockState(moving).is(Blocks.BELL)) {
			network.bellMove = null;
			roads.setDirty();
		}
		if (network.bellMove != null || !buried(level, bell)) return;
		BlockPos spot = spot(level, network);
		if (spot == null) return;
		network.bellMove = spot;
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
		UUID by = office == null ? null : office.mayor;
		Placements.add(level, spot, Blocks.BELL.defaultBlockState(), by, MARK);
		roads.setDirty();
	}

	/**
	 * Down in the rock: no open ground at the bell's own level anywhere round it (under an overhang or a roof the
	 * open ground is a few steps off, and the bell stays: a desert village's bell under its hill was once hung on
	 * the hilltop, and two villagers fell to their deaths climbing to it).
	 */
	public static boolean buried(ServerLevel level, BlockPos bell) {
		if (!level.isLoaded(bell) || !level.getBlockState(bell).is(Blocks.BELL)
				|| Ground.surface(level, bell.getX(), bell.getZ()) <= bell.getY() + BURIED
				|| !level.structureManager().getStructureWithPieceAt(bell, StructureTags.VILLAGE).isValid()) return false;
		for (BlockPos at : Rings.around(bell, 2, OPEN_GROUND, 2, 16)) {
			if (level.isLoaded(at) && Math.abs(Ground.surface(level, at.getX(), at.getZ()) - bell.getY()) <= BURIED) return false;
		}
		return true;
	}

	/** Open, level ground under the sky near the old bell's column, the town's own and nobody's. */
	private static @Nullable BlockPos spot(ServerLevel level, Roads.Network network) {
		BlockPos bell = network.bell;
		Land land = Land.around(level, bell);
		for (BlockPos at : Rings.around(bell, 0, SEARCH, 2, 12)) {
			if (!level.isPositionEntityTicking(at)) continue;
			BlockPos ground = Ground.top(level, at.getX(), at.getZ());
			if (ground == null || ground.getY() != Ground.surface(level, at.getX(), at.getZ()) || !level.canSeeSky(ground.above())) continue;
			BlockPos spot = ground.above();
			if (land.blocked(spot, 1) || !Towns.ours(level, bell, spot) || !level(level, ground)) continue;
			return spot;
		}
		return null;
	}

	/** A square of 3×3 within a block of the middle's height: room to stand round the bell. */
	private static boolean level(ServerLevel level, BlockPos ground) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos top = Ground.top(level, ground.getX() + dx, ground.getZ() + dz);
				if (top == null || Math.abs(top.getY() - ground.getY()) > 1) return false;
			}
		}
		return true;
	}

	/** Placements: the new bell is up. The town is its now, and the old one comes down. */
	public static void hung(ServerLevel level, BlockPos pos) {
		Roads roads = Roads.get(level.getServer());
		for (Roads.Network network : roads.networks()) {
			if (pos.equals(network.bellMove) && network.dimension.equals(level.dimension())) move(level, roads, network, pos);
		}
	}

	public static void move(ServerLevel level, Roads roads, Roads.Network network, BlockPos to) {
		BlockPos from = network.bell;
		Mayors mayors = Mayors.get(level.getServer());
		Mayors.Office office = mayors.officeNear(level.dimension(), from);
		if (office != null) {
			office.bell = to;
			mayors.setDirty();
		}
		Shops.moveBell(level, from, to);
		Neighbours.get(level.getServer()).move(level, from, to);
		network.bell = to;
		network.bellMove = null;
		roads.setDirty();
		for (Villager villager : level.getEntitiesOfClass(Villager.class, new AABB(from).inflate(TownRules.MAX_REACH),
				villager -> from.equals(villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).map(GlobalPos::pos).orElse(null)))) {
			villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), to));
		}
		if (level.getBlockState(from).is(Blocks.BELL)) level.destroyBlock(from, false);
		Chronicle.note(level, to, "bell_moved");
	}
}
