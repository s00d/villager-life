package com.vnap.village.town;

import com.vnap.village.home.Graveyard;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.Towns;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The town's churchyard, out at its edge (an English village's: a low fence, a way in between two lantern posts, a
 * stone path down the middle, rows of graves either side with flowers between, flowering azaleas and a bench at
 * the back), not by the square. Sited once, as far out as the town's land allows, where the ground is level and
 * nobody's; laid by the town's builders out of the treasury (Roads segment "cemetery"); a road comes to its way in.
 * Its plots take the graves (Graveyard); an old grave gives way after a while (Graveyard ages them).
 */
public final class Cemetery {
	/** Half its width across, its depth from the way in to the back fence. */
	static final int HALF = 5;
	static final int DEPTH = 8;
	/** Rows (depth) and columns (across) of the plots: a stone each, its name sign one row nearer the way in. */
	private static final int[] ROWS = {2, 4, 6};
	private static final int[] COLUMNS = {-4, -2, 2, 4};

	private Cemetery() {
	}

	/** A cell of the yard: {@code u} across (right of the way in is +), {@code v} back from the way in, on its own ground. */
	static @Nullable BlockPos cell(ServerLevel level, BlockPos front, Direction face, int u, int v) {
		BlockPos column = front.relative(face.getClockWise(), u).relative(face.getOpposite(), v);
		Integer ground = Ground.natural(level, column.getX(), column.getZ(), front.getY());
		return ground == null ? null : new BlockPos(column.getX(), ground, column.getZ());
	}

	static BoundingBox box(BlockPos front, Direction face) {
		BlockPos a = front.relative(face.getClockWise(), -HALF);
		BlockPos b = front.relative(face.getClockWise(), HALF).relative(face.getOpposite(), DEPTH);
		return BoundingBox.fromCorners(a.below(2), b.above(4));
	}

	/** The yard's ground taken (a building of the town's: houses, roads and lots keep off). */
	static @Nullable BoundingBox footprint(Roads.Network network) {
		BlockPos front = network.cemetery;
		return front == null ? null : box(front, network.cemeteryFace);
	}

	/** Daily (Roads.plan) once the town has a few people, or at its first death (Graveyard): the churchyard is sited and laid. */
	public static boolean plan(ServerLevel level, Roads roads, Roads.Network network) {
		if (network.cemetery != null) return true;
		double reach = Towns.reach(level, network.bell);
		Land land = Land.around(level, network.bell);
		// from the edge of the town's land inwards: the far side first
		for (BlockPos at : Rings.around(network.bell, (int) reach - DEPTH - 2, Math.max(TownRules.SQUARE_LARGE + 12, (int) (reach * 0.55)), 3, 24)) {
			if (!level.isLoaded(at) || !level.isPositionEntityTicking(at)) continue;
			Direction face = Direction.getNearest(network.bell.getX() - at.getX(), 0, network.bell.getZ() - at.getZ());
			Integer ground = Ground.natural(level, at.getX(), at.getZ(), Ground.surface(level, at.getX(), at.getZ()));
			if (ground == null) continue;
			BlockPos front = new BlockPos(at.getX(), ground, at.getZ());
			BoundingBox box = box(front, face);
			if (land.intersects(box, 2) || !Towns.ours(level, network.bell, BlockPos.containing(box.getCenter().getX(), front.getY(), box.getCenter().getZ()))) continue;
			List<SchematicSpec.PlacedBlock> blocks = layout(level, network, front, face);
			if (blocks == null) continue;
			network.cemetery = front.immutable();
			network.cemeteryFace = face;
			roads.queue(level, network, blocks, "cemetery");
			Chronicle.note(level, network.bell, "cemetery");
			roads.setDirty();
			return true;
		}
		return false;
	}

	/** The churchyard's blocks, or null when the ground there is not level enough (a step of one is fine) or not free. */
	static @Nullable List<SchematicSpec.PlacedBlock> layout(ServerLevel level, Roads.Network network, BlockPos front, Direction face) {
		String palette = HousePalette.forBiome(level.getBiome(front));
		BlockState fence = Roads.palette(Blocks.OAK_FENCE.defaultBlockState(), palette);
		BlockState post = Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState();
		BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);
		BlockState[] flowers = {Blocks.POPPY.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(),
				Blocks.LILY_OF_THE_VALLEY.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState()};
		List<SchematicSpec.PlacedBlock> clear = new ArrayList<>();
		List<SchematicSpec.PlacedBlock> build = new ArrayList<>();
		for (int u = -HALF; u <= HALF; u++) {
			for (int v = 0; v <= DEPTH; v++) {
				BlockPos ground = cell(level, front, face, u, v);
				if (ground == null || Math.abs(ground.getY() - front.getY()) > 1 || network.cells.contains(ground)) return null;
				BlockPos at = ground.above();
				// what grows there goes first (the yard is laid in this order)
				for (int up = 2; up >= 1; up--) {
					BlockState there = level.getBlockState(ground.above(up));
					if (!there.isAir() && there.getFluidState().isEmpty() && !there.hasBlockEntity()) clear.add(new SchematicSpec.PlacedBlock(ground.above(up), Blocks.AIR.defaultBlockState()));
				}
				boolean edge = Math.abs(u) == HALF || v == 0 || v == DEPTH;
				// a churchyard is turf, whatever the hill under it is (flowers take no stone)
				if (u != 0 && !level.getBlockState(ground).is(net.minecraft.tags.BlockTags.DIRT)) {
					build.add(new SchematicSpec.PlacedBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState()));
				}
				if (edge) {
					boolean corner = Math.abs(u) == HALF && (v == 0 || v == DEPTH);
					boolean gatePost = v == 0 && Math.abs(u) == 1;
					if (v == 0 && u == 0) {
						// the way in: open (villagers open no gates), a path through it
						build.add(new SchematicSpec.PlacedBlock(ground, Roads.surface(1, palette, ground, false)));
					} else if (corner || gatePost) {
						build.add(new SchematicSpec.PlacedBlock(at, post));
						build.add(new SchematicSpec.PlacedBlock(at.above(), lantern));
					} else {
						build.add(new SchematicSpec.PlacedBlock(at, fence));
					}
					continue;
				}
				if (u == 0) {
					build.add(new SchematicSpec.PlacedBlock(ground, Roads.surface(1, palette, ground, false)));
				} else if (Math.abs(u) == 3 && v % 2 == 0 && v < DEPTH - 1) {
					build.add(new SchematicSpec.PlacedBlock(at, flowers[Math.floorMod(u * 7 + v * 3, flowers.length)]));
				} else if (v == DEPTH - 1 && Math.abs(u) == HALF - 1) {
					build.add(new SchematicSpec.PlacedBlock(at, Blocks.FLOWERING_AZALEA.defaultBlockState()));
				} else if (v == DEPTH - 1 && Math.abs(u) == 1) {
					build.add(new SchematicSpec.PlacedBlock(at, Roads.palette(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, face.getOpposite()), palette)));
				}
			}
		}
		clear.addAll(build);
		return clear;
	}

	/** The plots of the town's churchyard, each a gravestone's cell (the stone faces the way in, its sign one row nearer). */
	public static List<BlockPos> plots(ServerLevel level, Roads.Network network) {
		List<BlockPos> out = new ArrayList<>();
		BlockPos front = network.cemetery;
		if (front == null) return out;
		for (int v : ROWS) {
			for (int u : COLUMNS) {
				BlockPos ground = cell(level, front, network.cemeteryFace, u, v);
				if (ground != null) out.add(ground.above());
			}
		}
		return out;
	}

	/** The front of the way in: a road comes to it (Roads.nodes). */
	static @Nullable BlockPos node(Roads.Network network) {
		BlockPos front = network.cemetery;
		return front == null ? null : front.relative(network.cemeteryFace);
	}

	/** A free plot of the town's churchyard (no grave on it, nothing waiting to be set there), or null. */
	public static @Nullable BlockPos freePlot(ServerLevel level, Roads.Network network) {
		Graveyard yard = Graveyard.get(level.getServer());
		for (BlockPos plot : plots(level, network)) {
			if (!level.isLoaded(plot)) continue;
			if (yard.graves().stream().anyMatch(grave -> grave.pos.equals(plot))) continue;
			if (com.vnap.village.home.Placements.pending(level, plot) || !level.getBlockState(plot).canBeReplaced()) continue;
			return plot;
		}
		return null;
	}
}
