package com.vnap.dialogue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The inside of a family house: the floor reachable from the head's bed without passing a door or a wall, under a
 * roof. Beds for a spouse or a child go there, never in a doorway, and their HOME POI ticket is taken at once so a
 * homeless neighbour cannot book the same bed.
 */
public final class FamilyHome {
	/** Steps from the head's bed the room may reach. */
	private static final int DEPTH = 6;
	private static final int DOOR_REACH = 5;

	private FamilyHome() {
	}

	public static BlockPos bedHead(ServerLevel level, BlockPos bed) {
		BlockState state = level.getBlockState(bed);
		if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
			return bed.relative(state.getValue(BedBlock.FACING)).immutable();
		}
		return bed.immutable();
	}

	/** A solid block two to six above; an upper floor counts. Sky light would lag behind a block change. */
	public static boolean roofed(ServerLevel level, BlockPos pos) {
		for (int dy = 2; dy <= 6; dy++) {
			BlockPos above = pos.above(dy);
			BlockState state = level.getBlockState(above);
			if (!state.isAir() && !state.is(Blocks.BARRIER) && !state.getCollisionShape(level, above).isEmpty()) return true;
		}
		return false;
	}

	/**
	 * Floor cells inside the house around {@code anchor}: open, roofed floor reached from the bed, never through a
	 * door or a wall (a porch under a balcony outside the door is not the room). The cells by a door are part of the
	 * room (people walk there) but nothing is ever put on them; see {@link #placeable}.
	 */
	public static Set<BlockPos> room(ServerLevel level, BlockPos anchor) {
		List<BlockPos> starts = new java.util.ArrayList<>();
		for (BlockPos bedCell : bedCluster(level, anchor)) {
			for (Direction side : Direction.Plane.HORIZONTAL) starts.add(bedCell.relative(side).immutable());
		}
		return flood(level, starts);
	}

	/** The room a floor cell is in (no bed needed): the same floor, walls and doors as {@link #room}. */
	public static Set<BlockPos> roomAt(ServerLevel level, BlockPos cell) {
		return flood(level, List.of(cell.immutable()));
	}

	private static Set<BlockPos> flood(ServerLevel level, List<BlockPos> starts) {
		Set<BlockPos> room = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(starts);
		ArrayDeque<Integer> depth = new ArrayDeque<>();
		for (int i = 0; i < starts.size(); i++) depth.add(1);
		Set<BlockPos> seen = new HashSet<>(queue);
		while (!queue.isEmpty()) {
			BlockPos cell = queue.poll();
			int steps = depth.poll();
			if (!floor(level, cell)) continue;
			room.add(cell);
			if (steps >= DEPTH) continue;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = cell.relative(side).immutable();
				if (seen.add(next)) {
					queue.add(next);
					depth.add(steps + 1);
				}
			}
		}
		return room;
	}

	/**
	 * Ways into the home with no door in them (a desert house of the world has none): a roofed cell of the room with
	 * a wall either side at both heights, the open ground outside it under the sky. Zombies walked in through them and
	 * killed the household in its beds; the head sets a door in each (VillagerHome). The door's way out, by cell.
	 */
	public static Map<BlockPos, Direction> doorways(ServerLevel level, BlockPos anchor) {
		Map<BlockPos, Direction> found = new java.util.LinkedHashMap<>();
		for (BlockPos cell : room(level, anchor)) {
			for (Direction out : Direction.Plane.HORIZONTAL) {
				BlockPos outside = cell.relative(out);
				if (roofed(level, outside) || !open(level, outside) || !open(level, outside.above())
						|| !level.getBlockState(outside.below()).isFaceSturdy(level, outside.below(), Direction.UP)) continue;
				boolean walled = true;
				for (Direction side : new Direction[]{out.getClockWise(), out.getCounterClockWise()}) {
					for (int dy = 0; dy <= 1; dy++) walled &= !open(level, cell.relative(side).above(dy));
				}
				if (walled) found.put(cell, out);
			}
		}
		return found;
	}

	/**
	 * The room has no way in but through doors: no floor cell of it opens onto walkable ground under the sky (a
	 * doorway, a wide gap, an open shop front alike). Its own {@code room} is passed in when the caller has it.
	 */
	public static boolean closed(ServerLevel level, Set<BlockPos> room) {
		return opening(level, room) == null && !room.isEmpty();
	}

	/**
	 * The first way out of the room, or null: a cell beside it, not part of it, from which a walker gets out under the
	 * sky. Roofed or not: an open forge under its eaves (walls on two sides) was taken for a closed room, and beds for the
	 * homeless were set down in it; a cell under a stairwell's hole inside a house leads nowhere, so it is no way out.
	 */
	public static @org.jspecify.annotations.Nullable BlockPos opening(ServerLevel level, Set<BlockPos> room) {
		for (BlockPos cell : room) {
			for (Direction out : Direction.Plane.HORIZONTAL) {
				BlockPos outside = cell.relative(out);
				if (!room.contains(outside) && walkable(level, outside) && leadsOut(level, room, outside)) return outside;
			}
		}
		return null;
	}

	private static boolean walkable(ServerLevel level, BlockPos pos) {
		if (open(level, pos)) return open(level, pos.above()) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
		// a bed, a slab, a carpet is no wall: a zombie steps up onto it and walks on (beds set across an open forge's
		// side made it a "closed room", more beds went in, and the homeless slept there with the night outside)
		net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return shape.max(Direction.Axis.Y) <= STEP && open(level, pos.above()) && open(level, pos.above(2));
	}

	private static final double STEP = 0.6;

	/** From {@code from}, walking on its level outside the room, a cell under the open sky is reached within OUT_STEPS. */
	private static boolean leadsOut(ServerLevel level, Set<BlockPos> room, BlockPos from) {
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>(List.of(from));
		Set<BlockPos> seen = new java.util.HashSet<>(List.of(from));
		while (!queue.isEmpty() && seen.size() <= OUT_STEPS) {
			BlockPos at = queue.poll();
			if (!roofed(level, at)) return true;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = at.relative(side);
				if (!room.contains(next) && seen.add(next) && walkable(level, next)) queue.add(next);
			}
		}
		return false;
	}

	private static final int OUT_STEPS = 48;

	/**
	 * Cells of the room in the dark (no block light): a monster spawns there at night, by the beds, and a zombie
	 * that came in the dark is still there in the morning under the roof. The household lights its rooms (VillagerHome).
	 */
	public static List<BlockPos> dark(ServerLevel level, BlockPos anchor) {
		List<BlockPos> found = new java.util.ArrayList<>();
		for (BlockPos cell : room(level, anchor)) {
			if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, cell) == 0) found.add(cell);
		}
		return found;
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** The roofed cell on each side of every door near the bed: where someone stands who just came in. */
	private static Set<BlockPos> doorSteps(ServerLevel level, BlockPos anchor) {
		Set<BlockPos> steps = new HashSet<>();
		for (BlockPos cursor : BlockPos.betweenClosed(anchor.offset(-DOOR_REACH, -1, -DOOR_REACH), anchor.offset(DOOR_REACH, 1, DOOR_REACH))) {
			BlockState state = level.getBlockState(cursor);
			if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
			Direction facing = state.getValue(DoorBlock.FACING);
			for (BlockPos step : new BlockPos[]{cursor.relative(facing), cursor.relative(facing.getOpposite())}) {
				if (roofed(level, step)) steps.add(step.immutable());
			}
		}
		return steps;
	}

	/** Every door near the bed, the cells in front of and behind it, and the cells beside those: a bed never blocks a way in. */
	private static Set<BlockPos> doorway(ServerLevel level, BlockPos anchor) {
		Set<BlockPos> blocked = new HashSet<>();
		for (BlockPos cursor : BlockPos.betweenClosed(anchor.offset(-DOOR_REACH, -1, -DOOR_REACH), anchor.offset(DOOR_REACH, 1, DOOR_REACH))) {
			BlockState state = level.getBlockState(cursor);
			if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
			BlockPos door = cursor.immutable();
			Direction facing = state.getValue(DoorBlock.FACING);
			for (BlockPos cell : new BlockPos[]{door, door.relative(facing), door.relative(facing.getOpposite())}) {
				blocked.add(cell);
				blocked.add(cell.relative(facing.getClockWise()));
				blocked.add(cell.relative(facing.getCounterClockWise()));
			}
		}
		return blocked;
	}

	private static boolean floor(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
				&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
				&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
				&& roofed(level, pos);
	}

	/** The head's bed and every bed touching it (a double bed, a spouse's bed alongside): the room starts around them all. */
	private static Set<BlockPos> bedCluster(ServerLevel level, BlockPos anchor) {
		Set<BlockPos> cluster = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(bedCells(level, anchor)));
		while (!queue.isEmpty() && cluster.size() < 16) {
			BlockPos cell = queue.poll();
			if (!(level.getBlockState(cell).getBlock() instanceof BedBlock) || !cluster.add(cell)) continue;
			for (Direction side : Direction.Plane.HORIZONTAL) queue.add(cell.relative(side).immutable());
		}
		return cluster;
	}

	private static BlockPos[] bedCells(ServerLevel level, BlockPos bed) {
		BlockState state = level.getBlockState(bed);
		if (!(state.getBlock() instanceof BedBlock)) return new BlockPos[]{bed};
		BlockPos head = bedHead(level, bed);
		return new BlockPos[]{head, head.relative(state.getValue(BedBlock.FACING).getOpposite())};
	}

	/**
	 * Foot and head cells for a new bed inside the room; {@code beside} first tries alongside the head's bed. A spot
	 * that would cut the room in two (a bed across the only way from the door to the back) is skipped.
	 */
	public static BlockPos @Nullable [] bedSpot(ServerLevel level, BlockPos anchor, boolean beside) {
		BlockState anchorState = level.getBlockState(anchor);
		if (!(anchorState.getBlock() instanceof BedBlock)) return null;
		Set<BlockPos> room = room(level, anchor);
		Set<BlockPos> doorway = doorway(level, anchor);
		Direction facing = anchorState.getValue(BedBlock.FACING);
		BlockPos head = bedHead(level, anchor);
		BlockPos foot = head.relative(facing.getOpposite());
		if (beside) {
			for (Direction side : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
				BlockPos newFoot = foot.relative(side);
				BlockPos newHead = head.relative(side);
				if (placeable(level, room, doorway, newFoot) && placeable(level, room, doorway, newHead)
						&& keepsOpen(level, anchor, room, newFoot, newHead)) return new BlockPos[]{newFoot, newHead};
			}
		}
		for (BlockPos cell : room) {
			if (!placeable(level, room, doorway, cell)) continue;
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos other = cell.relative(direction);
				if (placeable(level, room, doorway, other) && keepsOpen(level, anchor, room, cell, other)) return new BlockPos[]{cell, other};
			}
		}
		// no free floor: a little clutter goes (placeBed clears it, the things drop)
		Set<BlockPos> clutter = clutterAround(level, room, doorway);
		for (BlockPos cell : clutter) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos other = cell.relative(direction);
				if ((placeable(level, room, doorway, other) || clutter.contains(other)) && keepsOpen(level, anchor, room, cell, other)) {
					return new BlockPos[]{cell, other};
				}
			}
		}
		return null;
	}

	private static final Set<String> CLEARABLE_DECOR = Set.of("vanity_table", "standing_mirror", "flower_vase", "family_portrait");

	/**
	 * Small things standing about a room that a family moves out of the way for a child's bed or a newborn's cradle:
	 * a crafting table, a pot, a carpet, a candle or a lantern, hay, a pumpkin, a piece of the wife's decor. Never a
	 * job site, a bed, a door, or anything that keeps things (a chest, a furnace).
	 */
	public static boolean clutter(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || level.getPoiManager().getType(pos).isPresent()) return false;
		if (state.getBlock() instanceof com.vnap.block.DecorBlock decor) return CLEARABLE_DECOR.contains(decor.name());
		if (state.hasBlockEntity()) return false;
		Block block = state.getBlock();
		return state.is(Blocks.CRAFTING_TABLE) || block instanceof net.minecraft.world.level.block.FlowerPotBlock
				|| block instanceof net.minecraft.world.level.block.CarpetBlock || block instanceof net.minecraft.world.level.block.CandleBlock
				|| block instanceof net.minecraft.world.level.block.LanternBlock || state.is(Blocks.HAY_BLOCK) || state.is(Blocks.PUMPKIN)
				|| state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.MELON);
	}

	/** Clutter cells beside the room (on its floor, with room above): each would be floor once cleared. */
	public static Set<BlockPos> clutterAround(ServerLevel level, Set<BlockPos> room, Set<BlockPos> doorway) {
		Set<BlockPos> out = new LinkedHashSet<>();
		for (BlockPos cell : room) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = cell.relative(side);
				if (room.contains(next) || doorway.contains(next) || !clutter(level, next)) continue;
				if (level.getBlockState(next.above()).getCollisionShape(level, next.above()).isEmpty()
						&& level.getBlockState(next.below()).isFaceSturdy(level, next.below(), Direction.UP) && roofed(level, next)) {
					out.add(next.immutable());
				}
			}
		}
		return out;
	}

	/** The clutter on this cell goes, dropping as items (nothing of the family's or a player's is lost). */
	public static void clear(ServerLevel level, BlockPos pos) {
		if (clutter(level, pos)) level.destroyBlock(pos, true);
	}

	/** Whether something may stand on this floor cell: inside the room, free, and not by a door. */
	public static boolean placeable(ServerLevel level, Set<BlockPos> room, Set<BlockPos> doorway, BlockPos cell) {
		return room.contains(cell) && !doorway.contains(cell) && level.getBlockState(cell).isAir();
	}

	/** A room keeps one free-standing thing (decor, a chest, a lantern) per this many floor cells at most. */
	static final int CELLS_PER_THING = 5;

	/**
	 * Where a thing may stand in the home, best first: the same places in any house, ours or the world's. A floor
	 * cell against a solid wall (not a window, not a door), never a way through (free floor on two opposite sides),
	 * never in a doorway, never in front of something one uses (a chest, a furnace, a workstation, a ladder, stairs,
	 * a hatch), and only where everyone still walks from the door to every bed. Corners first, then the deepest
	 * nooks. A room already holding its share of things (one per {@link #CELLS_PER_THING} cells) gets nothing more.
	 */
	public static List<BlockPos> decorSpots(ServerLevel level, BlockPos anchor) {
		Set<BlockPos> room = room(level, anchor);
		Set<BlockPos> doorway = doorway(level, anchor);
		Set<BlockPos> things = new HashSet<>();
		for (BlockPos cell : room) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = cell.relative(side);
				if (!room.contains(next) && thing(level, next)) things.add(next.immutable());
			}
		}
		if (things.size() >= Math.max(2, room.size() / CELLS_PER_THING)) return List.of();
		List<BlockPos> out = new java.util.ArrayList<>();
		java.util.Map<BlockPos, Integer> rank = new java.util.HashMap<>();
		for (BlockPos cell : room) {
			if (!placeable(level, room, doorway, cell) || passage(room, cell)) continue;
			int walls = 0;
			boolean inFront = false;
			int open = 0;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = cell.relative(side);
				BlockState state = level.getBlockState(next);
				if (room.contains(next)) open++;
				else if (used(level, next, state)) inFront = true;
				else if (state.isFaceSturdy(level, next, side.getOpposite()) && !(state.getBlock() instanceof DoorBlock)
						&& !(state.getBlock() instanceof net.minecraft.world.level.block.TransparentBlock)
						&& !(state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock)) walls++;
			}
			if (walls == 0 || inFront || used(level, cell.above(), level.getBlockState(cell.above()))
					|| !keepsOpen(level, anchor, room, cell)) continue;
			out.add(cell.immutable());
			rank.put(cell.immutable(), (walls >= 2 ? 0 : 10) + open);
		}
		out.sort(java.util.Comparator.comparingInt((BlockPos cell) -> rank.get(cell)).thenComparingLong(BlockPos::asLong));
		return out;
	}

	/** Free floor on two opposite sides: a way through, nothing stands there. */
	static boolean passage(Set<BlockPos> room, BlockPos cell) {
		return (room.contains(cell.north()) && room.contains(cell.south())) || (room.contains(cell.east()) && room.contains(cell.west()));
	}

	/** Something people use from the cell in front of it: that cell stays free. */
	private static boolean used(ServerLevel level, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		return level.getBlockEntity(pos) instanceof net.minecraft.world.Container || block instanceof net.minecraft.world.level.block.LadderBlock
				|| block instanceof net.minecraft.world.level.block.StairBlock || block instanceof net.minecraft.world.level.block.TrapDoorBlock
				|| block instanceof net.minecraft.world.level.block.CraftingTableBlock
				|| level.getPoiManager().getType(pos).isPresent();
	}

	/** A free-standing thing on the floor (decor, a chest, a pot, a lantern): counted against the room's share. */
	private static boolean thing(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof com.vnap.block.DecorBlock || state.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock
				|| state.getBlock() instanceof net.minecraft.world.level.block.LanternBlock || state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
				|| state.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock;
	}

	/** Doorways near this bed: cells a bed or a piece of decor never goes on. */
	public static Set<BlockPos> doorwayOf(ServerLevel level, BlockPos anchor) {
		return doorway(level, anchor);
	}

	/**
	 * True when, with {@code taken} filled, everyone can still get from the door to every bed (the new one included):
	 * the cells next to beds and just inside the doors stay in one walkable piece. A nook nobody needs may close.
	 */
	public static boolean keepsOpen(ServerLevel level, BlockPos anchor, Set<BlockPos> room, BlockPos... taken) {
		Set<BlockPos> left = new HashSet<>(room);
		for (BlockPos cell : taken) left.remove(cell);
		Set<BlockPos> needed = new HashSet<>();
		for (BlockPos step : doorSteps(level, anchor)) if (left.contains(step)) needed.add(step);
		boolean newReachable = taken.length == 0;
		for (BlockPos cell : left) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = cell.relative(side);
				if (level.getBlockState(next).getBlock() instanceof BedBlock) needed.add(cell);
				for (BlockPos newCell : taken) {
					if (next.equals(newCell)) {
						needed.add(cell);
						newReachable = true;
					}
				}
			}
		}
		if (!newReachable) return false;
		if (needed.isEmpty()) return true;
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		BlockPos start = needed.iterator().next();
		queue.add(start);
		Set<BlockPos> reached = new HashSet<>(Set.of(start));
		while (!queue.isEmpty()) {
			BlockPos cell = queue.poll();
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = cell.relative(side);
				if (left.contains(next) && reached.add(next)) queue.add(next);
			}
		}
		return reached.containsAll(needed);
	}

	/** Sets the bed and registers its HOME POI now (the level does it a tick later), so its ticket can be taken. */
	public static BlockPos placeBed(ServerLevel level, BlockPos anchor, BlockPos foot, BlockPos head) {
		clear(level, foot);
		clear(level, head);
		Block block = level.getBlockState(anchor).getBlock() instanceof BedBlock own && !(own instanceof com.vnap.block.DoubleBedBlock) ? own : Blocks.WHITE_BED;
		Direction facing = com.vnap.Mc.fromDelta(head.getX() - foot.getX(), 0, head.getZ() - foot.getZ());
		// a spouse's bed right beside the head's, the same way round: the two become one double bed
		BlockState own = level.getBlockState(anchor);
		if (facing != null && own.getBlock() instanceof BedBlock && !(own.getBlock() instanceof com.vnap.block.DoubleBedBlock)
				&& own.getValue(BedBlock.FACING) == facing) {
			BlockPos ownHead = bedHead(level, anchor);
			boolean right = head.equals(ownHead.relative(facing.getClockWise()));
			boolean left = head.equals(ownHead.relative(facing.getCounterClockWise()));
			if (right || left) {
				com.vnap.block.DoubleBedBlock.join(level, com.vnap.block.LifeBlocks.DOUBLE_BED, right ? ownHead : head, right ? head : ownHead, facing);
				register(level, ownHead);
				register(level, head);
				VillagerHome.noteStructureChange();
				return head.immutable();
			}
		}
		BlockState base = block.defaultBlockState().setValue(BedBlock.FACING, facing == null ? Direction.NORTH : facing);
		level.setBlock(foot, base.setValue(BedBlock.PART, BedPart.FOOT), 3);
		level.setBlock(head, base.setValue(BedBlock.PART, BedPart.HEAD), 3);
		register(level, head);
		VillagerHome.noteStructureChange();
		return head.immutable();
	}

	/** The bed is HOME at once: the game adds a placed bed's POI only after the running task, if inside one. */
	public static void register(ServerLevel level, BlockPos head) {
		PoiManager poi = level.getPoiManager();
		if (poi.getType(head).isEmpty()) {
			Holder<PoiType> home = level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.HOME);
			poi.add(head, home);
		}
	}

	/** Books the bed for one sleeper; true when the ticket is now held (or was already held by this family). */
	public static boolean take(ServerLevel level, BlockPos bed) {
		BlockPos head = bedHead(level, bed);
		if (!(level.getBlockState(head).getBlock() instanceof BedBlock)) return false;
		register(level, head);
		return level.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(head), head, 1).isPresent();
	}

	public static void release(ServerLevel level, BlockPos bed) {
		BlockPos head = bedHead(level, bed);
		if (booked(level, head, PoiManager.Occupancy.IS_OCCUPIED)) level.getPoiManager().release(head);
	}

	/** Someone's home already: its POI ticket is held (by whoever lives there, loaded or not, or a bed set aside). */
	public static boolean taken(ServerLevel level, BlockPos bed) {
		return booked(level, bedHead(level, bed), PoiManager.Occupancy.IS_OCCUPIED);
	}

	private static boolean booked(ServerLevel level, BlockPos head, PoiManager.Occupancy occupancy) {
		return level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), head, 0, occupancy)
				.anyMatch(record -> record.getPos().equals(head));
	}

	/** A bed inside the room nobody has booked. */
	public static @Nullable BlockPos freeBed(ServerLevel level, BlockPos anchor) {
		Set<BlockPos> cells = new HashSet<>();
		for (BlockPos floor : room(level, anchor)) {
			for (Direction side : Direction.Plane.HORIZONTAL) cells.add(floor.relative(side).immutable());
		}
		for (BlockPos cell : cells) {
			BlockState state = level.getBlockState(cell);
			if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) continue;
			if (VillagerHome.sameBed(level, anchor, cell)) continue;
			if (level.getPoiManager().getType(cell).isEmpty() || booked(level, cell, PoiManager.Occupancy.HAS_SPACE)) return cell.immutable();
		}
		// a house the villagers built has its beds on other floors too (a level's bedroom upstairs)
		for (com.vnap.village.home.HouseProject house : com.vnap.mood.VillageMoodSavedData.get(level).houses()) {
			if (!house.done) continue;
			net.minecraft.world.level.levelgen.structure.BoundingBox box = house.footprint(level);
			if (!box.isInside(anchor)) continue;
			for (BlockPos cell : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockState state = level.getBlockState(cell);
				if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD || VillagerHome.sameBed(level, anchor, cell)) continue;
				if (level.getPoiManager().getType(cell).isEmpty() || booked(level, cell, PoiManager.Occupancy.HAS_SPACE)) return cell.immutable();
			}
			break;
		}
		return null;
	}

	public static boolean roomForChild(ServerLevel level, BlockPos anchor) {
		return freeBed(level, anchor) != null || bedSpot(level, anchor, false) != null;
	}

	/** Line 0 the head, 1 the home marker, 2 the spouse, 3 how many children live here. */
	public static void rewriteSign(ServerLevel level, BlockPos sign, String head, String spouse, int kids) {
		if (!(level.getBlockEntity(sign) instanceof SignBlockEntity board)
				|| !VillagerHome.isMarker(com.vnap.Mc.signLine(board, 1))) return;
		Component kidsLine = kids > 0 ? Component.translatable(Family.KIDS_SIGN_KEY, kids) : Component.empty();
		com.vnap.Mc.writeSign(board, false, Component.literal(head), Component.literal(VillagerHome.MARKER), Component.literal(spouse), kidsLine);
		board.setChanged();
		level.sendBlockUpdated(sign, board.getBlockState(), board.getBlockState(), 3);
	}

	/** The home sign of this bed: the remembered one, else the nearest marker sign whose first line is this name. */
	public static @Nullable BlockPos findSign(ServerLevel level, BlockPos bed, @Nullable BlockPos remembered, String name) {
		if (remembered != null && level.getBlockEntity(remembered) instanceof SignBlockEntity board
				&& VillagerHome.isMarker(com.vnap.Mc.signLine(board, 1))) return remembered;
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos cursor : BlockPos.betweenClosed(bed.offset(-6, -2, -6), bed.offset(6, 2, 6))) {
			if (!(level.getBlockEntity(cursor) instanceof SignBlockEntity board)
					|| !VillagerHome.isMarker(com.vnap.Mc.signLine(board, 1))) continue;
			boolean named = com.vnap.Mc.signLine(board, 0).equals(name);
			double distance = cursor.distSqr(bed) - (named ? 1000.0 : 0.0);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = cursor.immutable();
			}
		}
		return best;
	}
}
