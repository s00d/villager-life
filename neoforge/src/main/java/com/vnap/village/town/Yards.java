package com.vnap.village.town;

import com.vnap.dialogue.Family;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Market;
import com.vnap.village.market.Materials;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A house's yard, so a town is not a scatter of bare houses: a ring WIDE (else NARROW) blocks round the house (a picket fence of the
 * palette's wood, a clipped hedge or a low stone wall, by the house), a way in at each door between two lantern posts
 * (open: villagers open no gates), a stone path from the door to it, a flower bed along the front and a bench by the
 * path. For the town's own houses and the houses of the village the world made alike. The family pays for it out of
 * its own purse once it can (never on credit) and the town's builders lay it (Roads segment "yard"); where the ground
 * round the house is not the family's to fence (a road, another house, a lot, a slope) the ring keeps off it, and a
 * house with too little room round it gets no yard at all. One yard a town a day.
 */
public final class Yards {
	/** The ring's distance out from the house's walls: a proper yard where there is room, else a narrow one. */
	static final int WIDE = 3;
	static final int NARROW = 2;
	/** The share of the ring that must be free for a yard to be worth it. */
	static final double ROOM = 0.6;
	/** Days before a house without a yard (no money, no room) is looked at again. */
	static final int RETRY_DAYS = 5;
	/** The mark of a house whose yard is laid. */
	static final long LAID = Long.MAX_VALUE;

	private Yards() {
	}

	/** A ring keeps this far off every door (a gap then, with its posts and lanterns). */
	private static final int DOOR_CLEAR = 2;

	private record House(BoundingBox box, List<Facade.Opening> doors, int floor, Villager payer) {
	}

	/** Daily (Roads.plan): one house of the town without a yard gets one, if its family can pay and there is room. */
	public static void plan(ServerLevel level, Roads roads, Roads.Network network, long day) {
		for (House house : houses(level, network)) {
			long key = BlockPos.asLong(house.box.minX(), house.box.minY(), house.box.minZ());
			Long last = network.yards.get(key);
			if (last != null && (last == LAID || day - last < RETRY_DAYS)) continue;
			network.yards.put(key, day);
			roads.setDirty();
			List<SchematicSpec.PlacedBlock> blocks = layout(level, network, house.box, house.doors, house.floor);
			if (blocks == null) continue;
			int cost = (int) Math.ceil(Materials.cost(Materials.bill(new SchematicSpec(1, 1, 1, List.of(), blocks, BlockPos.ZERO))) * 1.1);
			// a yard out of its own purse (what it keeps for bread aside), never on credit
			if (Market.funds(level, house.payer) < cost + TownRules.RESERVE) continue;
			roads.queueFor(level, network, blocks, "yard", house.payer);
			for (SchematicSpec.PlacedBlock block : blocks) if (!block.state().isAir()) network.features.add(block.rel().immutable());
			network.yards.put(key, LAID);
			return;
		}
	}

	/** Debug (/vnap roads yard): the yard of the house nearest {@code pos} laid out now, the treasury paying. */
	public static String debugNear(ServerLevel level, Roads roads, Roads.Network network, BlockPos pos) {
		House house = houses(level, network).stream().min(java.util.Comparator.comparingDouble(h -> h.box.getCenter().distSqr(pos))).orElse(null);
		if (house == null) return "no house with a family near";
		List<SchematicSpec.PlacedBlock> blocks = layout(level, network, house.box, house.doors, house.floor);
		if (blocks == null) return "no room for a yard round " + house.box;
		roads.queue(level, network, blocks, "yard");
		for (SchematicSpec.PlacedBlock block : blocks) if (!block.state().isAir()) network.features.add(block.rel().immutable());
		network.yards.put(BlockPos.asLong(house.box.minX(), house.box.minY(), house.box.minZ()), LAID);
		return "yard of " + blocks.size() + " blocks round " + house.box;
	}

	/** The town's finished houses and the world's village houses with a family in them, each with whoever pays for it. */
	private static List<House> houses(ServerLevel level, Roads.Network network) {
		List<House> out = new ArrayList<>();
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.done || house.player != null || !Towns.ours(level, network.bell, house.origin) || !level.isLoaded(house.origin)) continue;
			if (!(level.getEntity(house.owner) instanceof Villager owner) || !owner.isAlive()) continue;
			out.add(new House(house.footprint(level), Facade.doors(level, house), house.origin.getY(), owner));
		}
		for (BoundingBox box : network.worldHouses) {
			if (!level.isLoaded(BlockPos.containing(box.getCenter().getX(), box.getCenter().getY(), box.getCenter().getZ()))) continue;
			Villager head = level.getEntitiesOfClass(Villager.class, AABB.of(box).inflate(TownRules.NEAR_BELL), v -> v.isAlive() && Family.isHead(v)
					&& v instanceof VillagerLifeData data && data.vnap$homeBed() != null && box.isInside(data.vnap$homeBed())).stream().findFirst().orElse(null);
			if (head == null) continue;
			List<Facade.Opening> doors = Facade.doors(level, box);
			if (doors.isEmpty()) continue;
			out.add(new House(box, doors, doors.get(0).pos().getY() - 1, head));
		}
		return out;
	}

	/**
	 * The yard's blocks: what grows in the way cut first, then the ring, the posts, the paths, the flowers, the bench;
	 * null when too little of the ring is free.
	 */
	static @Nullable List<SchematicSpec.PlacedBlock> layout(ServerLevel level, Roads.Network network, BoundingBox box, List<Facade.Opening> doors, int floor) {
		List<SchematicSpec.PlacedBlock> wide = layout(level, network, box, doors, floor, WIDE);
		return wide != null ? wide : layout(level, network, box, doors, floor, NARROW);
	}

	private static @Nullable List<SchematicSpec.PlacedBlock> layout(ServerLevel level, Roads.Network network, BoundingBox box, List<Facade.Opening> doors, int floor, int out) {
		BlockPos centre = BlockPos.containing(box.getCenter().getX(), floor, box.getCenter().getZ());
		String palette = HousePalette.forBiome(level.getBiome(centre));
		int style = Math.floorMod(java.util.Objects.hash(box.minX(), box.minZ()), 3);
		BlockState ring = switch (style) {
			case 0 -> Roads.palette(Blocks.OAK_FENCE.defaultBlockState(), palette);
			case 1 -> Roads.palette(Blocks.OAK_LEAVES.defaultBlockState(), palette).trySetValue(LeavesBlock.PERSISTENT, true);
			default -> Blocks.COBBLESTONE_WALL.defaultBlockState();
		};
		BlockState post = style == 2 ? Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState() : Roads.palette(Blocks.OAK_FENCE.defaultBlockState(), palette);
		BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);
		BlockState[] flowers = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(),
				Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.ALLIUM.defaultBlockState(), Blocks.RED_TULIP.defaultBlockState(),
				Blocks.ORANGE_TULIP.defaultBlockState(), Blocks.PINK_TULIP.defaultBlockState()};
		Land land = Land.around(level, centre).without(claim -> claim.box().intersects(box));
		Set<BlockPos> queued = new HashSet<>();
		for (Roads.Segment segment : network.segments) for (SchematicSpec.PlacedBlock block : segment.blocks) queued.add(block.rel());

		// the ways in: straight out from each door to the ring
		Set<Long> openings = new HashSet<>();
		List<BlockPos> paths = new ArrayList<>();
		for (Facade.Opening door : doors) {
			for (int i = 1; i <= out + box.getXSpan() + box.getZSpan(); i++) {
				BlockPos at = door.pos().relative(door.out(), i);
				if (onRing(box, at, out)) {
					openings.add(BlockPos.asLong(at.getX(), 0, at.getZ()));
					break;
				}
				if (!box.isInside(at)) paths.add(at);
			}
		}

		// and by any other door about the ring, at any height (a hillside house's upper door, a neighbour's): a ring
		// across it shut a door off for good (a bot found it, NO WAY to the door)
		for (BlockPos pos : BlockPos.betweenClosed(box.minX() - out - 2, box.minY() - 4, box.minZ() - out - 2, box.maxX() + out + 2, box.maxY() + 4, box.maxZ() + out + 2)) {
			if (!level.isLoaded(pos)) continue;
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) continue;
			// straight out of it, either way (the ring may stand a few steps off): the first ring cell is a way in
			Direction facing = state.getValue(net.minecraft.world.level.block.DoorBlock.FACING);
			for (Direction way : new Direction[] {facing, facing.getOpposite()}) {
				for (int i = 1; i <= out + 4; i++) {
					BlockPos ahead = new BlockPos(pos.getX() + way.getStepX() * i, floor, pos.getZ() + way.getStepZ() * i);
					if (!onRing(box, ahead, out)) continue;
					openings.add(BlockPos.asLong(ahead.getX(), 0, ahead.getZ()));
					break;
				}
			}
			for (int dx = -DOOR_CLEAR; dx <= DOOR_CLEAR; dx++) {
				for (int dz = -DOOR_CLEAR; dz <= DOOR_CLEAR; dz++) {
					if (onRing(box, new BlockPos(pos.getX() + dx, floor, pos.getZ() + dz), out)) openings.add(BlockPos.asLong(pos.getX() + dx, 0, pos.getZ() + dz));
				}
			}
		}

		List<SchematicSpec.PlacedBlock> clear = new ArrayList<>();
		List<SchematicSpec.PlacedBlock> build = new ArrayList<>();
		int total = 0;
		int free = 0;
		for (int x = box.minX() - out; x <= box.maxX() + out; x++) {
			for (int z = box.minZ() - out; z <= box.maxZ() + out; z++) {
				BlockPos column = new BlockPos(x, floor, z);
				if (!onRing(box, column, out)) continue;
				total++;
				BlockPos ground = ground(level, network, land, queued, column, floor);
				if (ground == null) continue;
				free++;
				BlockPos at = ground.above();
				long key = BlockPos.asLong(x, 0, z);
				if (openings.contains(key)) continue;
				boolean besideOpening = false;
				for (Direction side : Direction.Plane.HORIZONTAL) besideOpening |= openings.contains(BlockPos.asLong(x + side.getStepX(), 0, z + side.getStepZ()));
				cut(level, at, clear);
				if (besideOpening) {
					build.add(new SchematicSpec.PlacedBlock(at, post));
					build.add(new SchematicSpec.PlacedBlock(at.above(), lantern));
				} else {
					build.add(new SchematicSpec.PlacedBlock(at, ring));
				}
			}
		}
		if (total == 0 || free < total * ROOM) return null;

		// the path from each door to its way in, of the palette's rough stone
		for (BlockPos path : paths) {
			BlockPos ground = ground(level, network, land, queued, path, floor);
			if (ground == null) continue;
			cut(level, ground.above(), clear);
			build.add(new SchematicSpec.PlacedBlock(ground, Roads.surface(1, palette, ground, false)));
		}
		// a flower bed along the front (the side of the first door), a bench by the path
		if (!doors.isEmpty()) {
			Facade.Opening front = doors.get(0);
			Direction along = front.out().getClockWise();
			BlockPos bedRow = front.pos().relative(front.out(), 1);
			int span = front.out().getAxis() == Direction.Axis.X ? box.getZSpan() : box.getXSpan();
			boolean bench = false;
			for (int i = -span; i <= span; i++) {
				BlockPos cell = bedRow.relative(along, i);
				if (i == 0 || box.isInside(cell) || onRing(box, cell, out)) continue;
				BlockPos ground = ground(level, network, land, queued, cell, floor);
				if (ground == null || !level.getBlockState(ground).is(net.minecraft.tags.BlockTags.DIRT)) continue;
				cut(level, ground.above(), clear);
				if (!bench && Math.abs(i) == 1) {
					// sitting looking out to the street
					build.add(new SchematicSpec.PlacedBlock(ground.above(), Roads.palette(Blocks.OAK_STAIRS.defaultBlockState()
							.setValue(StairBlock.FACING, front.out().getOpposite()), palette)));
					bench = true;
				} else if (Math.abs(i) > 1) {
					build.add(new SchematicSpec.PlacedBlock(ground.above(), flowers[Math.floorMod(cell.getX() * 31 + cell.getZ() * 17, flowers.length)]));
				}
			}
		}
		// a wide yard: a flowering bush in each corner and a flower here and there along the fence inside
		if (out == WIDE) {
			for (int x = box.minX() - out + 1; x <= box.maxX() + out - 1; x++) {
				for (int z = box.minZ() - out + 1; z <= box.maxZ() + out - 1; z++) {
					BlockPos column = new BlockPos(x, floor, z);
					if (!onRing(box, column, out - 1) || paths.stream().anyMatch(p -> p.getX() == column.getX() && p.getZ() == column.getZ())) continue;
					boolean corner = (x == box.minX() - out + 1 || x == box.maxX() + out - 1) && (z == box.minZ() - out + 1 || z == box.maxZ() + out - 1);
					int roll = Math.floorMod(java.util.Objects.hash(x, z), 3);
					if (!corner && roll != 0) continue;
					BlockPos ground = ground(level, network, land, queued, column, floor);
					if (ground == null || !level.getBlockState(ground).is(net.minecraft.tags.BlockTags.DIRT)) continue;
					cut(level, ground.above(), clear);
					build.add(new SchematicSpec.PlacedBlock(ground.above(), corner ? Blocks.FLOWERING_AZALEA.defaultBlockState()
							: flowers[Math.floorMod(x * 13 + z * 7, flowers.length)]));
				}
			}
		}
		clear.addAll(build);
		return clear;
	}

	/** Exactly {@code out} blocks out from the house's walls (the ring's rectangle). */
	private static boolean onRing(BoundingBox box, BlockPos pos, int out) {
		int dx = pos.getX() < box.minX() ? box.minX() - pos.getX() : pos.getX() > box.maxX() ? pos.getX() - box.maxX() : 0;
		int dz = pos.getZ() < box.minZ() ? box.minZ() - pos.getZ() : pos.getZ() > box.maxZ() ? pos.getZ() - box.maxZ() : 0;
		return Math.max(dx, dz) == out;
	}

	/** The ground of a yard's cell: natural, about the house's floor, open over it, nobody else's, no road, nothing queued. */
	private static @Nullable BlockPos ground(ServerLevel level, Roads.Network network, Land land, Set<BlockPos> queued, BlockPos column, int floor) {
		if (!level.isLoaded(column)) return null;
		Integer y = Ground.natural(level, column.getX(), column.getZ(), floor);
		if (y == null || Math.abs(y - floor) > 1) return null;
		BlockPos ground = new BlockPos(column.getX(), y, column.getZ());
		BlockPos at = ground.above();
		if (network.cells.contains(ground) || queued.contains(at) || queued.contains(ground) || land.blocked(at, 0)) return null;
		BlockState there = level.getBlockState(at);
		if (!(there.isAir() || there.canBeReplaced() || there.getBlock() instanceof net.minecraft.world.level.block.BushBlock) || !level.getBlockState(at.above()).canBeReplaced()) return null;
		return ground;
	}

	private static void cut(ServerLevel level, BlockPos at, List<SchematicSpec.PlacedBlock> clear) {
		for (int up = 1; up >= 0; up--) {
			BlockState there = level.getBlockState(at.above(up));
			if (!there.isAir() && there.getFluidState().isEmpty() && !there.hasBlockEntity()) clear.add(new SchematicSpec.PlacedBlock(at.above(up), Blocks.AIR.defaultBlockState()));
		}
	}
}
