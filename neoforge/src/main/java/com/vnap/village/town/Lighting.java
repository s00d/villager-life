package com.vnap.village.town;

import com.vnap.dialogue.FamilyHome;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Placements;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Market;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Light against the night's monsters, which come where no lamp shines (block light 0): in a dark room by the beds
 * (a zombie born there is still there in the morning, under the roof) and in the dark corners of the town. A household
 * hangs a torch on the wall of its room (a torch on the wall is in nobody's way); the mayor has a lamp post set in the
 * darkest open ground of the town, a few a day, out of the treasury; and the town has torches set in the dark hollows
 * under and among its houses that open to the ground (a cellar under a floor, an awning, a cave a few blocks down):
 * the night's zombies and creepers were born there by the bell and walked out to the people.
 */
public final class Lighting {
	/** Ground looked at every this many blocks for darkness. */
	private static final int STEP = 5;
	/** Lamp posts the town puts up in a day. */
	private static final int LAMPS_PER_DAY = 2;

	/** A hollow this deep under the open air it opens to is still the town's to light; deeper caves are left alone. */
	private static final int DEPTH = 8;
	/** Blocks looked down a column from its top block: a roof, the room under it, the hollow under that. */
	private static final int SCAN = 16;
	/**
	 * Air looked through from a dark cell for the open sky; more — it is no hollow by the town but a cave system. A
	 * desert village's cave behind the hollow by its bell was some 600 cells of air (a zombie killed there by day).
	 */
	private static final int OPENING_SEARCH = 4000;
	/** Torches the town has set in hollows in a day. */
	private static final int TORCHES_PER_DAY = 12;
	/** No torch nearer than this to another (set or waiting): its light reaches the cells between by tomorrow. */
	private static final int TORCH_GAP = 8;

	private Lighting() {
	}

	/** Daily for the head of a house (VillagerHome): a torch on a wall of its room where the room is dark. */
	public static void home(ServerLevel level, Villager head, BlockPos bed) {
		List<BlockPos> dark = FamilyHome.dark(level, bed);
		if (dark.isEmpty()) return;
		Set<BlockPos> room = FamilyHome.room(level, bed);
		Set<BlockPos> doorway = FamilyHome.doorwayOf(level, bed);
		// the middle of the dark, and the wall cell nearest it
		double cx = dark.stream().mapToInt(BlockPos::getX).average().orElse(0);
		double cz = dark.stream().mapToInt(BlockPos::getZ).average().orElse(0);
		BlockPos best = null;
		Direction face = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos cell : room) {
			if (doorway.contains(cell)) continue;
			BlockPos at = cell.above();
			if (!level.getBlockState(at).isAir() || Placements.pending(level, at)) continue;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos wall = at.relative(side.getOpposite());
				if (!level.getBlockState(wall).isFaceSturdy(level, wall, side)) continue;
				double distance = (cell.getX() - cx) * (cell.getX() - cx) + (cell.getZ() - cz) * (cell.getZ() - cz);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = at.immutable();
					face = side;
				}
			}
		}
		if (best == null || face == null) return;
		Market.order(level, head, best, Items.TORCH);
		Placements.add(level, best, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, face), head.getUUID());
	}

	/** Daily (Roads.plan): lamp posts on the darkest open ground of the town, nearest the bell first. */
	static void town(ServerLevel level, Roads roads, Roads.Network network) {
		Mayors.Office office = Towns.of(level, network.bell);
		if (office == null || office.mayor == null) return;
		String palette = HousePalette.forBiome(level.getBiome(network.bell));
		Land land = Land.around(level, network.bell);
		int reach = (int) Towns.reach(level, network.bell);
		List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
		List<BlockPos> lit = new ArrayList<>(network.lamps);
		int made = 0;
		for (BlockPos at : Rings.around(network.bell, STEP, reach, STEP, distance -> Math.max(8, (int) (Math.PI * 2.0 * distance / STEP)))) {
			if (made >= LAMPS_PER_DAY) break;
			BlockPos ground = dark(level, network, land, at);
			if (ground == null || lit.stream().anyMatch(lamp -> lamp.closerThan(ground, TownRules.LAMP_GAP))) continue;
			blocks.addAll(Roads.lamp(ground, palette));
			network.lamps.add(ground);
			network.features.add(ground.above());
			lit.add(ground);
			made++;
		}
		if (!blocks.isEmpty()) roads.queue(level, network, blocks, "lamp");
	}

	/** Open natural ground at {@code at} in the dark (block light 0 over it), the town's own and nobody's, or null. */
	private static @Nullable BlockPos dark(ServerLevel level, Roads.Network network, Land land, BlockPos at) {
		if (!level.isPositionEntityTicking(at)) return null;
		BlockPos ground = Ground.top(level, at.getX(), at.getZ());
		if (ground == null || !Towns.ours(level, network.bell, ground) || land.blocked(ground.above(), 1)) return null;
		BlockState state = level.getBlockState(ground);
		if (!state.getFluidState().isEmpty() || !level.getBlockState(ground.above()).isAir() || !level.getBlockState(ground.above(3)).isAir()) return null;
		return level.getBrightness(LightLayer.BLOCK, ground.above()) == 0 ? ground : null;
	}

	/**
	 * Daily (Roads.plan): torches for the dark hollows of the town's land a monster may spawn in and walk out of to the
	 * people, nearest the bell first. Bought by the treasury, set down by hand (Placements: anyone; a hollow nobody
	 * gets into is lit by itself after a while). Sealed hollows and deep caves are left dark: nothing comes out of them.
	 */
	public static String hollows(ServerLevel level, Roads.Network network) {
		Mayors.Office office = Towns.of(level, network.bell);
		if (office == null) return "no town";
		long started = System.nanoTime();
		Towns.Territory land = Towns.territory(level, network.bell);
		int reach = (int) Towns.reach(level, network.bell);
		List<BlockPos> dark = new ArrayList<>();
		BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
		for (int x = network.bell.getX() - reach; x <= network.bell.getX() + reach; x++) {
			for (int z = network.bell.getZ() - reach; z <= network.bell.getZ() + reach; z++) {
				column.set(x, network.bell.getY(), z);
				if (!land.contains(column) || !level.isPositionEntityTicking(column)) continue;
				int top = Ground.surface(level, x, z);
				for (int y = top - 1; y >= top - SCAN; y--) {
					BlockPos cell = new BlockPos(x, y, z);
					if (spawnable(level, cell)) dark.add(cell);
				}
			}
		}
		dark.sort(java.util.Comparator.comparingDouble(cell -> cell.distSqr(network.bell)));
		List<BlockPos> torches = new ArrayList<>();
		for (Placements.Job job : Placements.get(level.getServer()).jobs()) {
			if (job.state().is(Blocks.TORCH) || job.state().is(Blocks.WALL_TORCH)) torches.add(job.pos());
		}
		// a house going up or being taken apart: a torch in it would be a block of nobody's plan in the way
		List<net.minecraft.world.level.levelgen.structure.BoundingBox> works = new ArrayList<>();
		for (com.vnap.village.home.HouseProject house : com.vnap.mood.VillageMoodSavedData.get(level).houses()) {
			if (!house.done || house.mending) works.add(house.footprint(level).inflatedBy(1));
			if (!house.clearing.isEmpty()) {
				works.add(net.minecraft.world.level.levelgen.structure.BoundingBox.encapsulatingPositions(house.clearing).orElseThrow().inflatedBy(1));
			}
		}
		Set<BlockPos> sealed = new java.util.HashSet<>();
		Set<BlockPos> open = new java.util.HashSet<>();
		int made = 0;
		for (BlockPos cell : dark) {
			if (made >= TORCHES_PER_DAY) break;
			if (sealed.contains(cell) || works.stream().anyMatch(box -> box.isInside(cell)) || torches.stream().anyMatch(torch -> torch.closerThan(cell, TORCH_GAP))) continue;
			if (!open.contains(cell) && !opens(level, cell, sealed, open)) continue;
			BlockState torch = torchFor(level, cell);
			if (torch == null) continue;
			Market.order(level, null, network.bell, Items.TORCH);
			Placements.add(level, cell, torch, null);
			torches.add(cell);
			made++;
		}
		long took = (System.nanoTime() - started) / 1_000_000;
		if (made > 0) com.vnap.VillagerLife.trace("vnap hollows: {} torches for the dark of {} ({} dark cells, {} ms)", made, network.bell.toShortString(), dark.size(), took);
		return dark.size() + " dark cells, " + made + " torches to set, " + took + " ms";
	}

	/** Open for a monster to stand (two cells of air over a spawnable floor) and dark under a roof. */
	private static boolean spawnable(ServerLevel level, BlockPos cell) {
		BlockState state = level.getBlockState(cell);
		if (!state.getCollisionShape(level, cell).isEmpty() || !state.getFluidState().isEmpty()) return false;
		BlockPos floor = cell.below();
		if (!level.getBlockState(floor).isValidSpawn(level, floor, net.minecraft.world.entity.EntityType.ZOMBIE)) return false;
		BlockState over = level.getBlockState(cell.above());
		if (!over.getCollisionShape(level, cell.above()).isEmpty() || !over.getFluidState().isEmpty()) return false;
		return level.getBrightness(LightLayer.BLOCK, cell) == 0 && !level.canSeeSky(cell);
	}

	/**
	 * Whether the air from {@code cell} reaches the open sky within {@link #OPENING_SEARCH} cells, no more than
	 * {@link #DEPTH} above it. The air looked through goes to {@code open} or {@code sealed} (not looked at again).
	 */
	private static boolean opens(ServerLevel level, BlockPos cell, Set<BlockPos> sealed, Set<BlockPos> open) {
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>(List.of(cell));
		Set<BlockPos> seen = new java.util.HashSet<>(List.of(cell));
		while (!queue.isEmpty() && seen.size() < OPENING_SEARCH) {
			BlockPos at = queue.poll();
			if (level.canSeeSky(at)) {
				if (at.getY() - cell.getY() <= DEPTH) {
					open.addAll(seen);
					return true;
				}
				// open to the sky high up the hill only: not that way out
				continue;
			}
			for (Direction side : Direction.values()) {
				BlockPos next = at.relative(side);
				if (seen.contains(next) || !level.isLoaded(next)) continue;
				BlockState state = level.getBlockState(next);
				if (!state.getCollisionShape(level, next).isEmpty() || !state.getFluidState().isEmpty()) continue;
				seen.add(next);
				queue.add(next);
			}
		}
		sealed.addAll(seen);
		return false;
	}

	/** A torch on a wall of the cell (out of the way of feet), or standing on its floor, or null. */
	private static @Nullable BlockState torchFor(ServerLevel level, BlockPos cell) {
		if (Placements.pending(level, cell)) return null;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockState wall = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, side);
			if (wall.canSurvive(level, cell)) return wall;
		}
		return Blocks.TORCH.defaultBlockState().canSurvive(level, cell) ? Blocks.TORCH.defaultBlockState() : null;
	}
}
