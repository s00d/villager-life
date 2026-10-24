package com.vnap.village.town;

import com.vnap.village.home.HouseProject;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A finished house is dressed the way MineColonies' and the medieval packs' houses are, so no wall is bare: shutters
 * of trapdoors either side of its windows, a potted azalea on a shelf under each upper window, a lantern on a chain
 * under the eave over its door, firewood stacked and hay against its walls; in the second age the Builders' sconces
 * either side of the door. Worked out from the house as it stands
 * (Facade), queued as a stretch for the town's builders (Roads, the treasury pays: it is the town's look), set by hand.
 */
public final class Dressing {
	/** Piles against a house's walls at most (firewood, hay). */
	static final int PILES = 2;

	private Dressing() {
	}

	/** HomeProjects.finish: the house's dressing to the town's builders, when it stands in a town. */
	public static void dress(ServerLevel level, HouseProject house) {
		Roads roads = Roads.get(level.getServer());
		Roads.Network network = roads.near(level.dimension(), house.bell);
		if (network == null) return;
		List<SchematicSpec.PlacedBlock> blocks = plan(level, network, house);
		if (!blocks.isEmpty()) roads.queue(level, network, blocks, "dressing");
	}

	static List<SchematicSpec.PlacedBlock> plan(ServerLevel level, Roads.Network network, HouseProject house) {
		BoundingBox box = house.footprint(level);
		String palette = house.palette;
		List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
		Set<BlockPos> used = new HashSet<>();
		List<Facade.Opening> doors = Facade.doors(level, house);
		for (Facade.Opening window : Facade.windows(level, house)) {
			BlockPos w = window.pos();
			Direction out = window.out();
			for (Direction side : new Direction[]{out.getClockWise(), out.getCounterClockWise()}) {
				BlockPos jamb = w.relative(side);
				// a door between two windows is no jamb: an open shutter in front of it, at head height, shut the way home
				if (level.getBlockState(jamb).getBlock() instanceof DoorBlock) continue;
				if (Facade.window(level.getBlockState(jamb)) || !level.getBlockState(jamb).isFaceSturdy(level, jamb, out)) continue;
				put(level, blocks, used, jamb.relative(out), Roads.palette(Blocks.OAK_TRAPDOOR.defaultBlockState(), palette)
						.setValue(TrapDoorBlock.FACING, out).setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.HALF, Half.BOTTOM));
			}
			BlockPos sill = w.below();
			BlockPos box1 = sill.relative(out);
			// a window box only up a storey: on the ground it would be a bush in the way along the wall
			if (Facade.window(level.getBlockState(sill)) || !level.getBlockState(sill).isFaceSturdy(level, sill, out) || !level.getBlockState(box1.below()).isAir()) continue;
			// a shelf on the wall under the sill and a potted azalea on it (a bare leaf block read as a bush stuck to the wall)
			put(level, blocks, used, box1.below(), Roads.palette(Blocks.OAK_TRAPDOOR.defaultBlockState(), palette)
					.setValue(TrapDoorBlock.FACING, out).setValue(TrapDoorBlock.HALF, Half.TOP));
			put(level, blocks, used, box1, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
		}
		for (Facade.Opening door : doors) {
			BlockPos front = door.pos().relative(door.out());
			int eave = 0;
			for (int up = 3; up <= 5; up++) {
				if (!level.getBlockState(front.above(up)).isAir()) {
					eave = up;
					break;
				}
			}
			if (eave == 0 || !level.getBlockState(front.above(eave)).isFaceSturdy(level, front.above(eave), Direction.DOWN)) continue;
			for (int up = eave - 1; up >= 3; up--) put(level, blocks, used, front.above(up), Blocks.CHAIN.defaultBlockState());
			put(level, blocks, used, front.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		// the second age (HouseLevels.restyle): the Builders' sconces either side of each door
		if (com.vnap.village.home.HousePalette.fine(palette)) {
			for (Facade.Opening door : doors) {
				for (Direction side : new Direction[]{door.out().getClockWise(), door.out().getCounterClockWise()}) {
					BlockPos wall = door.pos().above().relative(side);
					if (Facade.window(level.getBlockState(wall)) || !level.getBlockState(wall).isFaceSturdy(level, wall, door.out())) continue;
					put(level, blocks, used, wall.relative(door.out()), com.vnap.spire.SpireBlocks.SCONCE.defaultBlockState()
							.setValue(com.vnap.spire.SpireFixtureBlock.FACING, door.out()));
				}
			}
		}
		piles(level, network, house, box, doors, used, blocks);
		return blocks;
	}

	private static void put(ServerLevel level, List<SchematicSpec.PlacedBlock> out, Set<BlockPos> used, BlockPos pos, BlockState state) {
		if (!used.add(pos.immutable()) || !level.getBlockState(pos).isAir()) return;
		out.add(new SchematicSpec.PlacedBlock(pos.immutable(), state));
	}

	/** Firewood and hay against the ground floor's walls, away from its doors and off the roads. */
	private static void piles(ServerLevel level, Roads.Network network, HouseProject house, BoundingBox box, List<Facade.Opening> doors, Set<BlockPos> used,
							  List<SchematicSpec.PlacedBlock> blocks) {
		int y = house.origin.getY() + 1;
		int made = 0;
		long seed = house.origin.asLong();
		List<BlockPos> ring = new ArrayList<>();
		for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
			ring.add(new BlockPos(x, y, box.minZ() - 1));
			ring.add(new BlockPos(x, y, box.maxZ() + 1));
		}
		for (int z = box.minZ(); z <= box.maxZ(); z++) {
			ring.add(new BlockPos(box.minX() - 1, y, z));
			ring.add(new BlockPos(box.maxX() + 1, y, z));
		}
		java.util.Collections.shuffle(ring, new java.util.Random(seed));
		for (BlockPos at : ring) {
			if (made >= PILES) break;
			Direction in = inward(box, at);
			if (in == null) continue;
			Direction along = in.getClockWise();
			BlockPos next = at.relative(along);
			if (!spot(level, network, doors, used, at, in) || !spot(level, network, doors, used, next, in)) continue;
			if (made == 0) {
				BlockState log = Roads.palette(Blocks.OAK_LOG.defaultBlockState(), house.palette).trySetValue(RotatedPillarBlock.AXIS, along.getAxis());
				put(level, blocks, used, at, log);
				put(level, blocks, used, next, log);
				put(level, blocks, used, at.above(), log);
			} else {
				put(level, blocks, used, at, Blocks.HAY_BLOCK.defaultBlockState());
				put(level, blocks, used, next, Blocks.HAY_BLOCK.defaultBlockState());
			}
			made++;
		}
	}

	/** Toward the house from a cell of the ring round its box (null at a corner). */
	private static @org.jspecify.annotations.Nullable Direction inward(BoundingBox box, BlockPos at) {
		if (at.getZ() < box.minZ() && at.getX() >= box.minX() && at.getX() <= box.maxX()) return Direction.SOUTH;
		if (at.getZ() > box.maxZ() && at.getX() >= box.minX() && at.getX() <= box.maxX()) return Direction.NORTH;
		if (at.getX() < box.minX() && at.getZ() >= box.minZ() && at.getZ() <= box.maxZ()) return Direction.EAST;
		if (at.getX() > box.maxX() && at.getZ() >= box.minZ() && at.getZ() <= box.maxZ()) return Direction.WEST;
		return null;
	}

	/** Free ground right against a plain wall, two cells and more from a door, off the roads and the town's ways. */
	private static boolean spot(ServerLevel level, Roads.Network network, List<Facade.Opening> doors, Set<BlockPos> used, BlockPos at, Direction in) {
		if (used.contains(at) || !level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) return false;
		if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP) || network.cells.contains(at.below())) return false;
		BlockPos wall = at.relative(in);
		if (!level.getBlockState(wall).isFaceSturdy(level, wall, in.getOpposite()) || Facade.window(level.getBlockState(wall))) return false;
		for (Facade.Opening door : doors) {
			if (Math.abs(door.pos().getX() - at.getX()) + Math.abs(door.pos().getZ() - at.getZ()) <= 3) return false;
		}
		return true;
	}

	/** What a house's dressing takes, in every palette: the stall keeps it (Market.building). */
	static List<SchematicSpec> kit() {
		List<SchematicSpec> out = new ArrayList<>();
		for (String palette : com.vnap.village.home.HousePalette.ALL) {
			List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
			int n = 0;
			for (int i = 0; i < 12; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Roads.palette(Blocks.OAK_TRAPDOOR.defaultBlockState(), palette)));
			for (int i = 0; i < 4; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Roads.palette(Blocks.OAK_TRAPDOOR.defaultBlockState(), palette)));
			for (int i = 0; i < 4; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState()));
			for (int i = 0; i < 4; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.CHAIN.defaultBlockState()));
			for (int i = 0; i < 2; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.LANTERN.defaultBlockState()));
			for (int i = 0; i < 3; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Roads.palette(Blocks.OAK_LOG.defaultBlockState(), palette)));
			for (int i = 0; i < 2; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.HAY_BLOCK.defaultBlockState()));
			for (int i = 0; i < 4; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), com.vnap.spire.SpireBlocks.SCONCE.defaultBlockState()));
			out.add(new SchematicSpec(n, 1, 1, List.of(), blocks, BlockPos.ZERO));
		}
		return out;
	}
}
