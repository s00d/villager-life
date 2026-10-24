package com.vnap.village.town;

import com.vnap.block.DecorBlock;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Market;
import com.vnap.village.market.Shops;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * A trade's front, set up by the town's builders (Roads, the treasury pays: it is the town's look): a sign on chains
 * beside the door, above head height, against the wall (TownDecor works out its ware and sways it), a bell inside over
 * the door (it rings when a player comes in), and at the stall two crates of its goods either side of the door, out
 * of the way in. The stall, the tavern, the bakehouse and the workshops with a door get one; one building a day.
 */
public final class Signage {
	public static final String LABEL = "signs";

	private Signage() {
	}

	private record Front(BoundingBox box, List<Facade.Opening> doors, boolean shop) {
	}

	/** Roads.plan, once a day: the first building with no sign yet gets its front queued. */
	public static void plan(ServerLevel level, Roads roads, Roads.Network network) {
		for (Front front : fronts(level, network)) {
			if (signed(level, network, front.box)) continue;
			List<SchematicSpec.PlacedBlock> blocks = layout(level, network, front);
			if (blocks.isEmpty()) continue;
			roads.queue(level, network, blocks, LABEL);
			for (SchematicSpec.PlacedBlock block : blocks) network.features.add(block.rel().immutable());
			return;
		}
	}

	private static List<Front> fronts(ServerLevel level, Roads.Network network) {
		List<Front> out = new ArrayList<>();
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.done || !Shops.STALLS.contains(house.plan) || !Towns.ours(level, network.bell, house.origin)
					|| !loaded(level, house.footprint(level))) continue;
			out.add(new Front(house.footprint(level), Facade.doors(level, house), true));
		}
		for (TownPlots.Kind kind : new TownPlots.Kind[]{TownPlots.Kind.TAVERN, TownPlots.Kind.BAKEHOUSE}) {
			BoundingBox box = TownPlots.built(network, kind) ? TownPlots.footprint(level, network, kind) : null;
			if (box != null && loaded(level, box)) out.add(new Front(box, Facade.doors(level, box), false));
		}
		for (BoundingBox box : network.workshops.values()) {
			if (loaded(level, box)) out.add(new Front(box, Facade.doors(level, box), false));
		}
		out.removeIf(front -> front.doors.isEmpty());
		return out;
	}

	/** Every chunk round the building is loaded (signed looks two blocks out; a corner check loaded the rest). */
	private static boolean loaded(ServerLevel level, BoundingBox box) {
		for (int cx = (box.minX() - 2) >> 4; cx <= (box.maxX() + 2) >> 4; cx++) {
			for (int cz = (box.minZ() - 2) >> 4; cz <= (box.maxZ() + 2) >> 4; cz++) if (!level.hasChunk(cx, cz)) return false;
		}
		return true;
	}

	/** A sign stands by the building already, or one is on its way. */
	private static boolean signed(ServerLevel level, Roads.Network network, BoundingBox box) {
		BoundingBox near = box.inflatedBy(2);
		for (BlockPos pos : BlockPos.betweenClosed(near.minX(), near.minY(), near.minZ(), near.maxX(), near.maxY(), near.maxZ())) {
			if (level.getBlockState(pos).getBlock() instanceof DecorBlock decor && decor.name().equals("shop_sign")) return true;
		}
		for (Roads.Segment segment : network.segments) {
			if (LABEL.equals(segment.label) && near.isInside(segment.origin)) return true;
		}
		return false;
	}

	/** The front of the first door that has room for it: the sign at least. */
	private static List<SchematicSpec.PlacedBlock> layout(ServerLevel level, Roads.Network network, Front front) {
		for (Facade.Opening door : front.doors) {
			List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
			Direction out = door.out();
			BlockPos sign = null;
			for (Direction side : new Direction[]{out.getClockWise(), out.getCounterClockWise()}) {
				BlockPos wall = door.pos().above(2).relative(side);
				BlockPos at = wall.relative(out);
				if (free(level, at) && level.getBlockState(wall).isFaceSturdy(level, wall, out)) {
					sign = at;
					break;
				}
			}
			if (sign == null) continue;
			blocks.add(new SchematicSpec.PlacedBlock(sign.immutable(), decor("shop_sign", out)));
			// the bell inside, on the wall over the door
			Direction in = out.getOpposite();
			BlockPos over = door.pos().above(2);
			BlockPos bell = over.relative(in);
			if (free(level, bell) && level.getBlockState(over).isFaceSturdy(level, over, in)) blocks.add(new SchematicSpec.PlacedBlock(bell.immutable(), decor("door_bell", in)));
			if (front.shop) {
				for (Direction side : new Direction[]{out.getClockWise(), out.getCounterClockWise()}) {
					BlockPos crate = door.pos().relative(out).relative(side);
					if (free(level, crate) && free(level, crate.above()) && level.getBlockState(crate.below()).isFaceSturdy(level, crate.below(), Direction.UP)
							&& !network.cells.contains(crate.below())) blocks.add(new SchematicSpec.PlacedBlock(crate.immutable(), decor("market_crate", out)));
				}
			}
			return blocks;
		}
		return List.of();
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).isAir();
	}

	private static BlockState decor(String name, Direction facing) {
		return Market.decor(name) instanceof BlockItem item ? item.getBlock().defaultBlockState().setValue(DecorBlock.FACING, facing)
				: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
	}

	/** What fronts take, for the stall's catalogue (Market.building). */
	public static List<SchematicSpec> kit() {
		List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
		int n = 0;
		for (String name : new String[]{"shop_sign", "door_bell", "market_crate", "market_crate"}) {
			blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), decor(name, Direction.NORTH)));
		}
		return List.of(new SchematicSpec(n, 1, 1, List.of(), blocks, BlockPos.ZERO));
	}

	/** Debug (/vnap roads signs): the front of the building nearest {@code pos} queued now. */
	public static String debugNear(ServerLevel level, Roads roads, Roads.Network network, BlockPos pos) {
		Front front = fronts(level, network).stream().min(java.util.Comparator.comparingDouble(f -> f.box.getCenter().distSqr(pos))).orElse(null);
		if (front == null) return "no stall, tavern, bakehouse or workshop with a door near";
		List<SchematicSpec.PlacedBlock> blocks = layout(level, network, front);
		if (blocks.isEmpty()) return "no wall beside its doors for a sign: " + front.box;
		roads.queue(level, network, blocks, LABEL);
		for (SchematicSpec.PlacedBlock block : blocks) network.features.add(block.rel().immutable());
		return "front of " + blocks.size() + " pieces at " + front.box;
	}
}
