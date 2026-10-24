package com.vnap.village.town;

import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Towns;
import com.vnap.village.life.Agenda;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Placements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A night under a roof for a villager with no bed (a newcomer, one whose house is still going up): it walks into
 * the nearest building of its town (a house, the tavern, the chapel, a house of the world's village) and stays on a
 * floor cell of a closed room until dawn. Out in the open a zombie took one, and the village lost the rest to the zombie it
 * became. Looked for every family tick of a night (Family.tickAll), carried out by the night's agenda.
 */
public final class Shelter {
	public static final String WHY = "shelter";
	/** From evening to dawn (the time of day, ticks): it walks there by daylight, before the monsters come. */
	private static final long DUSK = 11000;
	/** Into the morning: one let out at 23400 met a zombie at the door before the sun had burnt it. */
	private static final long DAWN = 1000;
	/** Rooms of a building looked at, the ground floor and its middle first, and the least floor a room has. */
	private static final int CHECKED = 8;
	private static final int ROOM = 4;

	/**
	 * When (game time) each villager last looked for a shelter: a minute's wait, not once a night - one that found no
	 * room at dusk (the rooms still dark, a newcomer not yet in) stood out the whole night, and a storm by day after
	 * the night's look counted as the same night.
	 */
	private static final long LOOK_AGAIN = 1200;
	private static final java.util.Map<java.util.UUID, Long> LOOKED = new java.util.HashMap<>();

	private Shelter() {
	}

	/** The room each was last sent to, and the rooms it set off for and never reached today (a shopkeeper of a snowy town
	 * was sent to the same room twelve times in one storm, never got there, and died outside). */
	private static final java.util.Map<java.util.UUID, BlockPos> SENT = new java.util.HashMap<>();
	private static final java.util.Map<java.util.UUID, java.util.Map<Long, Long>> MISSED = new java.util.HashMap<>();

	public static void forget(java.util.UUID villager) {
		STILL.remove(villager);
		LOOKED.remove(villager);
		SENT.remove(villager);
		MISSED.remove(villager);
	}

	/** A new world: the last one's nights mean nothing. */
	public static void clear() {
		STILL.clear();
		LOOKED.clear();
		SENT.clear();
		MISSED.clear();
	}

	public static void night(ServerLevel level, Villager villager) {
		if (!night(level) || villager.isBaby() || villager.isSleeping() || villager.isNoAi() || Agenda.has(villager, WHY)
				|| Watch.onDuty(level, villager)) return;
		BlockPos bed = bed(level, villager);
		if (bed != null && !stuck(villager)) return;
		Roads.Network network = Roads.networkOf(level, villager);
		if (network == null) return;
		// no path is worked out for a mob off the ground (a step, a fall): a look then would be spent for nothing
		if (!villager.onGround() && !villager.isInWater()) return;
		long now = level.getGameTime();
		if (now - LOOKED.getOrDefault(villager.getUUID(), Long.MIN_VALUE / 2) < LOOK_AGAIN) return;
		LOOKED.put(villager.getUUID(), now);
		if (bed != null && reaches(villager, bed)) return;
		if (bed != null) com.vnap.VillagerLife.trace("vnap stranded: {} at {} has no way to its bed {}", com.vnap.dialogue.VillagerHome.nameOf(villager),
				villager.blockPosition().toShortString(), bed.toShortString());
		long day = Family.day(level);
		java.util.Map<Long, Long> missed = MISSED.computeIfAbsent(villager.getUUID(), id -> new java.util.HashMap<>());
		missed.values().removeIf(on -> on != day);
		// the last room it was sent to and is not in: its agenda gave it up on the way (no way there after all)
		BlockPos sent = SENT.remove(villager.getUUID());
		if (sent != null && !villager.blockPosition().closerThan(sent, 3.0)) missed.put(sent.asLong(), day);
		java.util.Map<String, Integer> refused = new java.util.TreeMap<>();
		BlockPos spot = spot(level, network, villager, refused, missed.keySet());
		if (spot == null) {
			com.vnap.VillagerLife.trace("vnap shelter: none for {} at {}: {}", com.vnap.dialogue.VillagerHome.nameOf(villager), villager.blockPosition().toShortString(), refused);
			return;
		}
		SENT.put(villager.getUUID(), spot);
		Agenda.add(villager, new Agenda.Item(WHY, spot, Agenda.EVENT, 0, 0.6F, Shelter::night, null, null));
		com.vnap.VillagerLife.trace("vnap shelter: {} from {} to {} time {}", com.vnap.dialogue.VillagerHome.nameOf(villager),
				villager.blockPosition().toShortString(), spot.toShortString(), Family.timeOfDay(level));
	}

	/** Its bed (vanilla's memory, else the one the mod keeps for it), or null. */
	private static @Nullable BlockPos bed(ServerLevel level, Villager villager) {
		BlockPos home = villager.getBrain().getMemory(MemoryModuleType.HOME).filter(at -> at.dimension().equals(level.dimension()))
				.map(net.minecraft.core.GlobalPos::pos).orElse(null);
		return home != null ? home : villager instanceof VillagerLifeData data ? data.vnap$homeBed() : null;
	}

	/**
	 * A bed it cannot get to tonight is no bed (a shutter hung in front of the door, a block a player set, a house
	 * pulled down): it stood all night under its own wall, and the drowned of the harbour took it; it shelters like
	 * the homeless. Asked only of one at rest (or in a panic) standing on one spot, awake, for STILL_CHECKS family
	 * ticks: one walking home is on its way (a path worked out once from across the town ran out of search halfway and
	 * sent villagers on their way home to shelters), and from where a stuck one stands the way is short to work out.
	 */
	private static boolean stuck(Villager villager) {
		boolean resting = villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.REST)
				|| villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC);
		long at = villager.blockPosition().asLong();
		long[] seen = STILL.get(villager.getUUID());
		if (!resting) {
			STILL.remove(villager.getUUID());
			return false;
		}
		if (seen == null || seen[0] != at) {
			STILL.put(villager.getUUID(), new long[]{at, 1});
			return false;
		}
		return ++seen[1] >= STILL_CHECKS;
	}

	private static boolean reaches(Villager villager, BlockPos bed) {
		// as close as vanilla walks it to its bed (one block): two blocks off may be the far side of the wall
		net.minecraft.world.level.pathfinder.Path path = villager.getNavigation().createPath(bed, 1);
		return path != null && path.canReach();
	}

	private static final int STILL_CHECKS = 3;
	/** Where each villager at rest stood at the last family tick and for how many ticks in a row. */
	private static final java.util.Map<java.util.UUID, long[]> STILL = new java.util.HashMap<>();

	/** Debug: why a villager has no shelter tonight (or where it goes). */
	public static String why(ServerLevel level, Villager villager) {
		BlockPos bed = bed(level, villager);
		Roads.Network network = Roads.networkOf(level, villager);
		if (network == null) return "no town";
		if (bed != null && reaches(villager, bed)) return "goes to its bed";
		String state = (Agenda.has(villager, WHY) ? "sent to a shelter, " : "") + (Watch.onDuty(level, villager) ? "on watch, " : "")
				+ (bed != null ? "no way to its bed, " : "") + (villager.onGround() ? "" : "off the ground, ");
		java.util.Map<String, Integer> refused = new java.util.TreeMap<>();
		BlockPos spot = spot(level, network, villager, refused, MISSED.getOrDefault(villager.getUUID(), java.util.Map.of()).keySet());
		return state + (spot == null ? "no closed room in " + network.worldHouses.size() + " world houses and the town's buildings " + refused : "shelter at " + spot.toShortString());
	}

	/** Night, or a thunderstorm: in one the monsters spawn in the open by day (seven of a savanna died in its minute). */
	static boolean night(ServerLevel level) {
		long time = Family.timeOfDay(level);
		return time >= DUSK || time < DAWN || level.isThundering();
	}

	/**
	 * In a thunderstorm by day a villager of a town lives the night: its rest (home to bed, a homeless one to its
	 * shelter), the schedule's switch again once the storm is over. The monsters spawn in the open in a storm, and the
	 * villagers worked on in the fields round them: seven of a savanna died in its minute. (Ringing the bell for it
	 * was worse: vanilla's hiding walks each to a bed up to 80 blocks off and does not run from a zombie on the way.)
	 */
	public static boolean storm(ServerLevel level, net.minecraft.world.entity.LivingEntity entity) {
		if (!level.isThundering() || !(entity instanceof Villager villager) || villager.isNoAi() || Towns.bellOf(villager) == null) return false;
		if (!villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.REST) && level.getGameTime() % 20 == 0) {
			villager.getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.REST);
		}
		return true;
	}

	/** A floor cell of a closed room inside one of the nearest buildings of the town (not a bed's, not a door's). */
	private static @Nullable BlockPos spot(ServerLevel level, Roads.Network network, Villager villager, java.util.@Nullable Map<String, Integer> refused,
			Set<Long> missed) {
		for (BoundingBox box : buildings(level, network, villager.blockPosition(), 6)) {
			for (Set<BlockPos> room : rooms(level, box)) {
				BlockPos cell = room.iterator().next();
				// a closed room, not a porch or an open hall under a roof: homeless villagers died to zombies on those
				String no = missed.contains(cell.asLong()) ? "missed" : room.size() < ROOM ? "small" : !FamilyHome.closed(level, room) ? "open"
						: !lit(level, room) ? "dark" : !reached(villager, cell) ? "unreached" : null;
				if (no == null) return cell;
				if (refused != null) refused.merge(no, 1, Integer::sum);
			}
		}
		return null;
	}

	/** The town's buildings a villager may sleep in (not a player's), the nearest {@code count} to {@code near}. */
	private static List<BoundingBox> buildings(ServerLevel level, Roads.Network network, BlockPos near, int count) {
		List<BoundingBox> boxes = new ArrayList<>(network.worldHouses);
		boxes.addAll(TownPlots.footprints(level, network));
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (house.done && house.player == null && Towns.ours(level, network.bell, house.origin)) boxes.add(house.footprint(level));
		}
		boxes.removeIf(box -> Roads.bought(level, box.getCenter()));
		boxes.sort(java.util.Comparator.comparingDouble(box -> box.getCenter().distSqr(near)));
		return boxes.subList(0, Math.min(count, boxes.size()));
	}

	/** The first CHECKED rooms of a building: the ground floor first (an attic under the rafters is open at its gables), its middle first. */
	private static List<Set<BlockPos>> rooms(ServerLevel level, BoundingBox box) {
		List<BlockPos> cells = new ArrayList<>();
		for (BlockPos cell : BlockPos.betweenClosed(box.minX() + 1, box.minY(), box.minZ() + 1, box.maxX() - 1, box.maxY(), box.maxZ() - 1)) {
			if (level.isLoaded(cell) && inside(level, cell)) cells.add(cell.immutable());
		}
		cells.sort(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getY)
				.thenComparingDouble(cell -> cell.distToCenterSqr(box.getCenter().getX(), cell.getY(), box.getCenter().getZ())));
		Set<BlockPos> seen = new java.util.HashSet<>();
		List<Set<BlockPos>> rooms = new ArrayList<>();
		for (BlockPos cell : cells) {
			if (rooms.size() >= CHECKED) break;
			if (!seen.add(cell)) continue;
			Set<BlockPos> room = new java.util.LinkedHashSet<>(List.of(cell));
			room.addAll(FamilyHome.roomAt(level, cell));
			seen.addAll(room);
			rooms.add(room);
		}
		return rooms;
	}

	/**
	 * Daily (Roads.plan): a bed for every grown villager of the town with none (a newcomer, the shopkeeper, one whose
	 * bed went to another) and no free bed it could still go for. A fresh village of the world has as many beds as
	 * people and no more: the town's newcomers stood out at night, and the zombie that took one went on to the rest.
	 * The mayor sees to it on his daily round: the treasury buys the bed, he sets it down by hand (Placements, anyone after a day) in a closed, lit room of the town's
	 * buildings with floor to spare; the homeless takes it the vanilla way or by walking to it (HomeProjects).
	 */
	public static void beds(ServerLevel level, Roads.Network network) {
		Mayors.Office office = Towns.of(level, network.bell);
		if (office == null || office.mayor == null) return;
		java.util.List<net.minecraft.world.entity.npc.Villager> homeless = Towns.people(level, network.bell, villager -> !villager.isBaby()
				&& network.bell.equals(Towns.bellOf(villager)) && !villager.getBrain().hasMemoryValue(MemoryModuleType.HOME)
				&& !HomeProjects.claimed(villager) && !com.vnap.village.home.Hamlets.leaving(villager));
		// the town's free beds against its homeless, not each one's: a bed one of them tried and lost is still a bed
		// for the next (counted per villager, a new bed was bought every day for the same three)
		long wanted = homeless.size() - HomeProjects.freeBeds(level, network.bell, homeless)
				- Placements.pendingBeds(level, network.bell, Towns.reach(level, network.bell));
		java.util.Map<String, Integer> refused = new java.util.TreeMap<>();
		for (BoundingBox box : buildings(level, network, network.bell, Integer.MAX_VALUE)) {
			if (wanted <= 0) return;
			// not in anyone's home: a family's house filled up with beds for the homeless until its owner had no way
			// through to his own, and slept outside
			if (lived(level, box)) {
				refused.merge("lived", 1, Integer::sum);
				continue;
			}
			for (Set<BlockPos> room : rooms(level, box)) {
				if (wanted <= 0) return;
				// the room stays a room to stand in: a shelter for the night needs ROOM cells besides
				String no = room.size() < ROOM + 2 ? "small" : !FamilyHome.closed(level, room) ? "open" : !lit(level, room) ? "dark" : null;
				BlockPos[] bed = no == null ? bedSpot(level, room) : null;
				if (no == null && bed == null) no = "no spot";
				if (no != null || bed == null) {
					refused.merge(no == null ? "no spot" : no, 1, Integer::sum);
					continue;
				}
				com.vnap.village.market.Market.order(level, null, network.bell, net.minecraft.world.item.Items.WHITE_BED);
				Direction facing = com.vnap.Mc.fromDelta(bed[1].getX() - bed[0].getX(), 0, bed[1].getZ() - bed[0].getZ());
				Placements.add(level, bed[0], net.minecraft.world.level.block.Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, facing), office.mayor);
				wanted--;
			}
		}
		if (wanted > 0) com.vnap.VillagerLife.trace("vnap shelter beds: {} short at {}: {}", wanted, network.bell.toShortString(), refused);
	}

	/** Someone sleeps here: a bed in the building is a villager's home (its POI ticket is held). */
	private static boolean lived(ServerLevel level, BoundingBox box) {
		BlockPos centre = box.getCenter();
		int reach = Math.max(box.getXSpan(), Math.max(box.getYSpan(), box.getZSpan()));
		return level.getPoiManager().getInRange(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME), centre, reach,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.IS_OCCUPIED).anyMatch(poi -> box.isInside(poi.getPos()));
	}

	/** Foot and head of a bed on free floor of the room, off its doorways, keeping every bed and door reachable. */
	private static BlockPos @Nullable [] bedSpot(ServerLevel level, Set<BlockPos> room) {
		BlockPos anchor = room.iterator().next();
		Set<BlockPos> doorway = FamilyHome.doorwayOf(level, anchor);
		for (BlockPos foot : room) {
			if (!FamilyHome.placeable(level, room, doorway, foot) || Placements.pending(level, foot)) continue;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos head = foot.relative(side);
				if (FamilyHome.placeable(level, room, doorway, head) && !Placements.pending(level, head)
						&& FamilyHome.keepsOpen(level, anchor, room, foot, head)) return new BlockPos[]{foot, head};
			}
		}
		return null;
	}

	/** Lit all over: a monster is born on a dark floor, shut in with whoever sheltered there. */
	private static boolean lit(ServerLevel level, Set<BlockPos> room) {
		return room.stream().noneMatch(cell -> level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, cell) == 0);
	}

	/** A path there: two stood the whole night on a hill under a shelter they had no way to. */
	private static boolean reached(Villager villager, BlockPos cell) {
		net.minecraft.world.level.pathfinder.Path path = villager.getNavigation().createPath(cell, 1);
		return path != null && path.canReach();
	}

	private static boolean inside(ServerLevel level, BlockPos cell) {
		return level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
				&& level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
				&& level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), net.minecraft.core.Direction.UP)
				&& !(level.getBlockState(cell.below()).getBlock() instanceof BedBlock) && !(level.getBlockState(cell).getBlock() instanceof DoorBlock)
				&& FamilyHome.roofed(level, cell);
	}
}
