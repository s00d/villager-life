package com.vnap.dialogue;

import com.vnap.block.LifeBlocks;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodApi;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.mood.MoodState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Claims an empty bed-and-door pair and adds one furnishing per Minecraft day; workshops live in {@code Workshops}. */
public final class VillagerHome {
	/** Line 2 of a home sign: a little house, the same in every language. */
	public static final String MARKER = "⌂";

	public static boolean isMarker(String line) {
		return line.equals(MARKER);
	}
	private static final int SCAN = 16;
	/** How far round a bed another villager remembering it is looked for. */
	private static final int SQUATTER_RANGE = 64;
	// houses of this mod have two and three floors: a villager upstairs must still find the bed below
	private static final int SCAN_HEIGHT = 8;
	private static final int PAIR = 5;
	private static final int DECOR_CAP = 6;
	/** Homeless villagers scanned per chunk on one game tick. Cleared next tick. */
	private static final int SCANS_PER_CHUNK = 4;
	private static final Long2IntOpenHashMap SCAN_BUDGET = new Long2IntOpenHashMap();
	private static long scanTick = Long.MIN_VALUE;
	private static int structureGen;
	/** Where a villager stood at the last night check and for how many checks in a row: see homeward. */
	private static final java.util.Map<java.util.UUID, Still> STILL = new java.util.HashMap<>();
	private static final int STUCK_CHECKS = 5;

	private record Still(BlockPos pos, int checks) {
	}
	private static int entityQueries;
	private static long lifeLineTick = Long.MIN_VALUE;
	private static boolean placingOwnPainting;

	private VillagerHome() {
	}

	public static boolean placingOwnPainting() {
		return placingOwnPainting;
	}

	/** A bed, door, or home sign changed. Homeless villagers may look again today. */
	public static void noteStructureChange() {
		structureGen++;
	}

	public static int entityQueries() {
		return entityQueries;
	}

	public static void tick(ServerLevel level, Villager villager, long day) {
		if (!(villager instanceof VillagerLifeData data)) return;
		BlockPos bed = data.vnap$homeBed();
		// a villager away from home (a trading hall, a trip) leaves its house alone: reading it would load its chunks
		if (bed != null && !Family.homeNear(level, villager, bed)) return;
		if (bed != null && !stillBed(level, bed)) {
			clearClaim(villager, data);
			bed = null;
		}
		if (bed != null) bed = keepHome(level, villager, bed);
		// Children and spouses live in the head's house: the head's chore gives them their beds.
		if (villager.isBaby() || Family.member(villager)) return;
		if (bed == null) {
			if (data.vnap$homeChoreDay() == day && data.vnap$homeSeenGen() == structureGen) return;
			if (!allowScan(level, villager.blockPosition())) return;
			// a vanilla bed in a house with no door (a desert house of the world): no sign goes up there and nothing
			// keeps a zombie out; the villager buys a door and sets it in, and the house is its own the next day
			BlockPos vanilla = villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
			if (vanilla != null && level.getBlockState(vanilla).getBlock() instanceof BedBlock && nearestDoor(level, vanilla) == null) {
				shutDoorways(level, villager, vanilla);
			}
			// the household of a vanilla bed lights its dark room as a house of the mod does (a zombie came in the dark)
			if (vanilla != null && level.getBlockState(vanilla).getBlock() instanceof BedBlock) com.vnap.village.town.Lighting.home(level, villager, vanilla);
			tryClaim(level, villager, data);
			data.vnap$setHomeChoreDay(day);
			data.vnap$setHomeSeenGen(structureGen);
			return;
		}
		if (data.vnap$homeChoreDay() == day) return;
		// a golem or a cow walled in while the house went up (before 18.10.2026) is let out
		com.vnap.village.home.HouseProject own = com.vnap.village.home.FamilyRooms.houseOf(level, villager);
		if (own != null) {
			com.vnap.village.home.HomeProjects.shooOut(level, own);
			com.vnap.village.home.HomeProjects.inspect(level, own, villager, Family.day(level));
		}
		shutDoorways(level, villager, bed);
		rehang(level, villager, bed);
		com.vnap.village.town.Lighting.home(level, villager, bed);
		if (Family.furnish(level, villager)) {
			data.vnap$setHomeChoreDay(day);
			return;
		}
		if (data.vnap$homeDecor() < DECOR_CAP && tryDecor(level, data)) {
			data.vnap$setHomeDecor(data.vnap$homeDecor() + 1);
		}
		data.vnap$setHomeChoreDay(day);
	}

	/** A door bought and set by hand in every way into the home that has none (FamilyHome.doorways). */
	private static void shutDoorways(ServerLevel level, Villager head, BlockPos bed) {
		for (java.util.Map.Entry<BlockPos, Direction> way : FamilyHome.doorways(level, bed).entrySet()) {
			BlockPos at = way.getKey();
			if (com.vnap.village.home.Placements.pending(level, at)) continue;
			BlockState painted = com.vnap.village.town.Roads.palette(Blocks.OAK_DOOR.defaultBlockState(), com.vnap.village.home.HousePalette.forBiome(level.getBiome(at)));
			BlockState door = (painted.getBlock() instanceof DoorBlock ? painted : Blocks.OAK_DOOR.defaultBlockState())
					.setValue(DoorBlock.FACING, way.getValue().getOpposite()).setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
			com.vnap.village.market.Market.order(level, head, at, door.getBlock().asItem());
			com.vnap.village.home.Placements.add(level, at, door, head.getUUID());
		}
	}

	/**
	 * The brain's HOME points at the claimed bed (its head half: claim stores no other): vanilla forgets HOME now and
	 * then (the bed was taken for a night), and without it nobody walks to bed.
	 */
	private static BlockPos keepHome(ServerLevel level, Villager villager, BlockPos head) {
		// a bed marked slept-in with nobody in it (a sleeper unloaded or killed without waking, kept by the save):
		// vanilla erases its owner's HOME every night over it and never clears the mark
		BlockState state = level.getBlockState(head);
		if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock && state.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)
				&& level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(head).inflate(2),
						sleeper -> sleeper.getSleepingPos().filter(head::equals).isPresent()).isEmpty()) {
			level.setBlock(head, state.setValue(net.minecraft.world.level.block.BedBlock.OCCUPIED, false), 3);
		}
		boolean remembered = villager.getBrain().getMemory(MemoryModuleType.HOME)
				.filter(home -> home.dimension().equals(level.dimension()) && home.pos().equals(head)).isPresent();
		if (!remembered) villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
		// its own bed's ticket is held too: one let go (a sharer's death released it, or vanilla when the bed was
		// slept in) is handed to anyone by vanilla, and two then remembered the one bed: each night one of them found
		// it taken, lost its home and walked the dark. Whoever took the ticket of a bed with an owner gives it back
		// (awake: it finds another bed by day, not woken in the dark).
		if (FamilyHome.taken(level, head)) squattersOut(level, villager, head);
		if (!FamilyHome.taken(level, head)) FamilyHome.take(level, head);
		return head;
	}

	private static void squattersOut(ServerLevel level, Villager villager, BlockPos head) {
		for (Villager other : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(head).inflate(SQUATTER_RANGE),
				other -> !other.getUUID().equals(villager.getUUID()) && !other.isSleeping() && other.getBrain().getMemory(MemoryModuleType.HOME)
						.filter(home -> home.dimension().equals(level.dimension()) && home.pos().equals(head)).isPresent())) {
			other.getBrain().eraseMemory(MemoryModuleType.HOME);
			level.getPoiManager().release(head);
		}
	}

	/**
	 * Nobody would see this entity vanish or appear: no player (spectators aside) within 16 blocks, nor one within 64
	 * with a line of sight to it. Every teleport and "goes away" of the mod asks this: a lone 16- or 32-block check let
	 * a second player farther off watch a pet or a traveller blink out.
	 */
	public static boolean unseen(ServerLevel level, net.minecraft.world.entity.Entity entity) {
		for (net.minecraft.server.level.ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			if (player.closerThan(entity, 16.0) || (player.closerThan(entity, 64.0) && player.hasLineOfSight(entity))) return false;
		}
		return true;
	}

	/**
	 * A villager that wants to go somewhere (a walk target, or its bed at night) and has stood on one spot for
	 * {@link #STUCK_CHECKS} family ticks with no path there (shut in a pen, down a crevice, in deep snow) is set
	 * beside its bed, or by the bell, when no player sees it; one standing twice as long with no path home, too.
	 */
	public static void homeward(ServerLevel level, Villager villager) {
		// one in a shelter for the night stands where it should (its bed out of reach): fetched home, it was set down
		// outside its own wall and walked back, all night
		if (com.vnap.village.life.Agenda.has(villager, com.vnap.village.town.Shelter.WHY)) {
			STILL.remove(villager.getUUID());
			return;
		}
		float time = Family.dayFraction(level);
		BlockPos bed = villager instanceof VillagerLifeData data ? data.vnap$homeBed() : null;
		if (bed == null) bed = villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
		BlockPos family = Family.home(villager);
		BlockPos refuge = bed != null ? bed : family != null ? family : bell;
		BlockPos here = villager.blockPosition();
		BlockPos goal = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
				.map(target -> target.getTarget().currentBlockPosition()).orElse(null);
		boolean night = time >= 0.54F && time <= 0.98F;
		if (goal == null || goal.closerThan(here, 3.0)) goal = night ? bed : null;
		if (refuge == null || refuge.closerThan(here, 8.0) || !level.isLoaded(refuge)) {
			STILL.remove(villager.getUUID());
			return;
		}
		Still still = STILL.get(villager.getUUID());
		if (still == null || !still.pos().closerThan(here, 1.5)) {
			STILL.put(villager.getUUID(), new Still(here, 0));
			return;
		}
		// standing about is normal by day: with nowhere to go, only twice as long a stand counts, and then home
		// itself must be out of reach
		if (still.checks() < (goal != null ? STUCK_CHECKS : STUCK_CHECKS * 2)) {
			STILL.put(villager.getUUID(), new Still(still.pos(), still.checks() + 1));
			return;
		}
		if (goal == null) goal = refuge;
		STILL.remove(villager.getUUID());
		if (!unseen(level, villager)) return;
		net.minecraft.world.level.pathfinder.Path path = villager.getNavigation().createPath(goal, 1);
		if (path != null && path.canReach()) return;
		// beside its bed means in its room: two blocks from the bed may be outside the wall
		java.util.Set<BlockPos> room = bed != null && refuge.equals(bed) ? FamilyHome.room(level, bed) : java.util.Set.of();
		for (BlockPos pos : BlockPos.betweenClosed(refuge.offset(-2, -1, -2), refuge.offset(2, 1, 2))) {
			if ((room.isEmpty() || room.contains(pos)) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
					&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
					&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
					&& !(level.getBlockState(pos).getBlock() instanceof BedBlock)) {
				villager.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				villager.getNavigation().stop();
				villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				return;
			}
		}
	}

	public static void forget(java.util.UUID villager) {
		STILL.remove(villager);
	}

	public static void forgetStill() {
		STILL.clear();
	}

	/** True when this villager already has a real bed and {@code bedPos} is a different one. */
	public static boolean refuseOtherBed(Villager villager, BlockPos bedPos) {
		if (!(villager instanceof VillagerLifeData data)) return false;
		BlockPos claimed = data.vnap$homeBed();
		if (claimed == null) return false;
		if (!(villager.level() instanceof ServerLevel level)) return false;
		if (!stillBed(level, claimed)) {
			clearClaim(villager, data);
			return false;
		}
		return !sameBed(level, claimed, bedPos);
	}

	public static @Nullable String moodGroup(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) return null;
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
		if (bell == null) return null;
		MoodState state = moodAt(level, bell);
		if (state == null) return null;
		float mood = state.mood;
		if (mood >= 60.0F) return "life_warm";
		if (mood >= 20.0F) return "life_kind";
		if (mood <= -60.0F) return "life_harsh";
		if (mood <= -20.0F) return "life_sour";
		return "life_plain";
	}

	public static void onBreak(ServerLevel level, BlockPos pos, BlockState state, long day) {
		boolean bedBreak = state.getBlock() instanceof BedBlock;
		boolean doorBreak = state.getBlock() instanceof DoorBlock;
		if (!bedBreak && !doorBreak) return;
		for (Villager villager : nearby(level, pos)) {
			if (!(villager instanceof VillagerLifeData data)) continue;
			BlockPos bed = data.vnap$homeBed();
			if (bed == null) continue;
			boolean ownBed = bedBreak && sameBed(level, bed, pos);
			boolean ownDoor = doorBreak && bed.distSqr(pos) <= (PAIR * PAIR);
			if (!ownBed && !ownDoor) continue;
			if (data.vnap$homeHurtDay() == day && data.vnap$homeHurtPos() == pos.asLong()) return;
			data.vnap$setHomeHurt(day, pos.asLong());
			VillageMoodApi.applyAt(level, pos, -20.0F, 0.0F, "home_hurt");
			say(villager, "life_home_hurt");
			if (ownBed) clearClaim(villager, data);
			return;
		}
	}

	public static void onPainting(ServerLevel level, BlockPos pos, long day) {
		for (Villager villager : nearby(level, pos)) {
			if (!(villager instanceof VillagerLifeData data)) continue;
			BlockPos bed = data.vnap$homeBed();
			if (bed == null || !bed.closerThan(pos, 8.0)) continue;
			if (data.vnap$homeThanksDay() == day) return;
			data.vnap$setHomeThanksDay(day);
			VillageMoodApi.applyAt(level, pos, 5.0F, 0.0F, "home_painting");
			say(villager, "life_home_thanks");
			return;
		}
	}

	/**
	 * A founder takes a house near it for nothing, once (it was there first: the world's village); everyone else in
	 * a town buys a home (Housing): a bed nearby it only lodges in. Out of any town (no bell, nobody to buy from) a
	 * villager settles where it finds a bed, as before.
	 */
	private static boolean tryClaim(ServerLevel level, Villager villager, VillagerLifeData data) {
		boolean townless = com.vnap.village.home.Towns.bellOf(villager) == null;
		if (!townless && !com.vnap.village.market.Housing.founder(villager)) return false;
		BlockPos origin = villager.blockPosition();
		for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-SCAN, -SCAN_HEIGHT, -SCAN), origin.offset(SCAN, SCAN_HEIGHT, SCAN))) {
			BlockPos pos = cursor.immutable();
			if (!(level.getBlockState(pos).getBlock() instanceof BedBlock)) continue;
			if (claim(level, villager, data, pos)) {
				com.vnap.village.market.Housing.settled(villager);
				return true;
			}
		}
		return false;
	}

	/** Whether a villager could move into the house of {@code bed}: a bed with a door near it and no home sign yet. */
	public static boolean claimable(ServerLevel level, BlockPos bed) {
		return level.getBlockState(bed).getBlock() instanceof BedBlock && nearestDoor(level, bed) != null && !marked(level, bed);
	}

	/** ", a house of the world <template> (a plan to grow by: yes/no)" when the bed is in a generated village's house. */
	private static String worldHouse(ServerLevel level, BlockPos bed) {
		com.vnap.village.home.HomeProjects.WorldPiece piece = com.vnap.village.home.HomeProjects.worldPiece(level, bed);
		return piece == null ? "" : ", a house of the world " + piece.template() + " at " + piece.origin().toShortString() + " " + piece.rotation()
				+ (com.vnap.village.home.HouseLevels.WORLD_HOUSES.contains(piece.template()) ? " (grows by its plan)" : " (no plan)");
	}

	/** Debug (/vnap home): why the nearest bed is not this villager's home. */
	public static String whyNoHome(ServerLevel level, Villager villager) {
		if (!(villager instanceof VillagerLifeData data)) return "not a villager";
		BlockPos own = data.vnap$homeBed();
		if (own != null) return "has a home at " + own.toShortString() + ", room " + FamilyHome.room(level, own).size()
				+ " cells, dark " + FamilyHome.dark(level, own).size()
				+ ", ways in without a door " + FamilyHome.doorways(level, own).keySet().stream().map(BlockPos::toShortString).toList()
				+ worldHouse(level, own) + " | night: " + com.vnap.village.town.Shelter.why(level, villager);
		if (Family.member(villager)) return "a member of a house: waits for its head to set a bed down";
		BlockPos origin = villager.blockPosition();
		BlockPos best = null;
		for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-SCAN, -SCAN_HEIGHT, -SCAN), origin.offset(SCAN, SCAN_HEIGHT, SCAN))) {
			if (level.getBlockState(cursor).getBlock() instanceof BedBlock && (best == null || cursor.distSqr(origin) < best.distSqr(origin))) {
				best = cursor.immutable();
			}
		}
		// and why it does not start a house of its own
		String house = " | house: " + java.util.Objects.requireNonNullElse(com.vnap.village.home.HomeProjects.blocker(level, villager, Family.day(level)), "may start one")
				+ " | night: " + com.vnap.village.town.Shelter.why(level, villager)
				+ " | money: " + com.vnap.village.market.Housing.describe(level, villager);
		if (best == null) return "no bed within " + SCAN + house;
		BlockPos head = FamilyHome.bedHead(level, best);
		BlockPos door = nearestDoor(level, best);
		boolean free = level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), head, 0,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE).anyMatch(poi -> poi.getPos().equals(head));
		BlockPos sign = FamilyHome.findSign(level, best, null, "");
		String owner = sign != null && level.getBlockEntity(sign) instanceof SignBlockEntity board ? com.vnap.Mc.signLine(board, 0) : "-";
		return "bed " + head.toShortString() + " door=" + (door == null ? "none" : door.toShortString()) + " marked=" + marked(level, best)
				+ " sign=" + owner + " poiFree=" + free + house;
	}

	/** Moves {@code villager} into the house of {@code bed}: a sign with its name by the door, HOME on the bed. */
	public static boolean claim(ServerLevel level, Villager villager, BlockPos bed) {
		return villager instanceof VillagerLifeData data && claim(level, villager, data, bed);
	}

	private static boolean claim(ServerLevel level, Villager villager, VillagerLifeData data, BlockPos bed) {
		if (!(level.getBlockState(bed).getBlock() instanceof BedBlock) || playersHouse(level, bed) || buildingForAnother(level, bed, villager)) return false;
		// HOME and the POI ticket live on the head half: a foot pos is dropped by vanilla and the bed left to anyone
		BlockPos saved = FamilyHome.bedHead(level, bed).immutable();
		// one bed, one owner: a bed whose ticket someone holds is that one's home (a bride keeps her old bed until her
		// husband sets her one beside his: it stands unmarked, and a neighbour once moved in on it)
		boolean mine = villager.getBrain().getMemory(MemoryModuleType.HOME).filter(home -> home.pos().equals(saved)).isPresent();
		if (!mine && FamilyHome.taken(level, saved)) return false;
		BlockPos door = nearestDoor(level, bed);
		if (door == null || (marked(level, bed) && !freeOrphanSign(level, bed))) return false;
		BlockPos sign = hangSign(level, villager, bed, door, forbidden(level, bed, door));
		if (sign == null) return false;
		data.vnap$setHomeBed(saved);
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), saved));
		// a neighbour who remembered it (vanilla gave it out before the sign went up) looks for another now, not at the owner's next tick
		squattersOut(level, villager, saved);
		FamilyHome.take(level, saved);
		Family.onClaim(villager, saved, sign);
		say(villager, "life_home");
		return true;
	}

	/** A bed in a house a player bought (HouseDeedItem): no villager claims it. */
	static boolean playersHouse(ServerLevel level, BlockPos bed) {
		for (com.vnap.village.home.HouseProject house : com.vnap.mood.VillageMoodSavedData.get(level).houses()) {
			if (house.player != null && house.footprint(level).isInside(bed)) return true;
		}
		return com.vnap.village.town.Roads.bought(level, bed);
	}

	/** A bed in a house still going up for someone else: it is that one's (it moves in when the house is done). */
	static boolean buildingForAnother(ServerLevel level, BlockPos bed, Villager villager) {
		for (com.vnap.village.home.HouseProject house : com.vnap.mood.VillageMoodSavedData.get(level).houses()) {
			if (!house.done && !house.owner.equals(villager.getUUID()) && house.footprint(level).isInside(bed)) return true;
		}
		return false;
	}

	private static boolean tryDecor(ServerLevel level, VillagerLifeData data) {
		BlockPos bed = data.vnap$homeBed();
		if (bed == null) return false;
		Set<BlockPos> blocked = forbidden(level, bed, nearestDoor(level, bed));
		List<BlockPos> spots = openSpots(level, bed, blocked);
		if (spots.isEmpty()) return false;
		int n = data.vnap$homeDecor();
		BlockPos spot = spots.get(n % spots.size());
		Painting painting = null;
		if (n % 3 == 2) {
			Direction face = Direction.NORTH;
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				if (level.getBlockState(spot.relative(direction)).isFaceSturdy(level, spot.relative(direction), direction.getOpposite())) {
					face = direction.getOpposite();
					break;
				}
			}
			Holder<PaintingVariant> variant = level.registryAccess()
					.registryOrThrow(Registries.PAINTING_VARIANT)
					.getHolderOrThrow(PaintingVariants.KEBAB);
			Painting candidate = new Painting(level, spot, face, variant);
			if (candidate.survives()) painting = candidate;
		}
		BlockState decor = switch (n % 3) {
			case 0 -> Blocks.FLOWER_POT.defaultBlockState();
			case 1 -> Blocks.LANTERN.defaultBlockState();
			default -> Blocks.CANDLE.defaultBlockState();
		};
		// with a stall in the village the head buys it first (Market.obtain); a day without it is just skipped
		net.minecraft.world.item.Item thing = painting != null ? net.minecraft.world.item.Items.PAINTING : decor.getBlock().asItem();
		Villager buyer = (Villager) data;
		if (!com.vnap.village.market.Market.obtain(level, buyer, thing)) return false;
		if (painting != null) {
			placingOwnPainting = true;
			try {
				level.addFreshEntity(painting);
			} finally {
				placingOwnPainting = false;
			}
		} else {
			// carried in and set down by hand (PlaceBehavior; a villager without a brain at once); a picture is hung
			// at once (an entity, not a block)
			if (buyer.isNoAi()) level.setBlock(spot, decor, 3);
			else com.vnap.village.home.Placements.add(level, spot, decor, buyer.getUUID());
		}
		com.vnap.village.market.Market.spend(buyer, thing);
		return true;
	}

	static List<BlockPos> openSpots(ServerLevel level, BlockPos bed, Set<BlockPos> blocked) {
		List<BlockPos> spots = new ArrayList<>();
		// inside this house only, never in a doorway or across the way to a bed: a lantern ended up outside, in a
		// neighbour's house or in front of the door from the street
		// the home's places for things (FamilyHome.decorSpots), within three of the bed
		for (BlockPos spot : FamilyHome.decorSpots(level, bed)) {
			if (blocked.contains(spot) || Math.abs(spot.getX() - bed.getX()) > 3 || Math.abs(spot.getZ() - bed.getZ()) > 3) continue;
			spots.add(spot);
		}
		return spots;
	}

	static Set<BlockPos> forbidden(ServerLevel level, BlockPos bed, @Nullable BlockPos door) {
		Set<BlockPos> blocked = new HashSet<>();
		blocked.add(bed.immutable());
		BlockState bedState = level.getBlockState(bed);
		if (bedState.getBlock() instanceof BedBlock) {
			Direction facing = bedState.getValue(BedBlock.FACING);
			BlockPos other = bedState.getValue(BedBlock.PART) == BedPart.HEAD
					? bed.relative(facing.getOpposite())
					: bed.relative(facing);
			blocked.add(other.immutable());
		}
		if (door != null) {
			blocked.add(door.immutable());
			blocked.add(door.above().immutable());
			BlockState doorState = level.getBlockState(door);
			if (doorState.getBlock() instanceof DoorBlock) {
				Direction facing = doorState.getValue(DoorBlock.FACING);
				blocked.add(door.relative(facing).immutable());
				blocked.add(door.relative(facing).above().immutable());
			}
		}
		return blocked;
	}

	/**
	 * The family's nameplate by the door, its writing to the street: on the outside wall beside the door at eye height
	 * (else by the lower half), else on a post beside the doorstep, else on a post inside by the door. The outside
	 * is the side of the door away from the bed's room (a door's FACING says nothing about which side is out).
	 */
	private static @Nullable BlockPos hangSign(ServerLevel level, Villager villager, BlockPos bed, BlockPos door, Set<BlockPos> blocked) {
		BlockState doorState = level.getBlockState(door);
		if (!(doorState.getBlock() instanceof DoorBlock)) return null;
		Direction out = outOf(level, bed, door, doorState.getValue(DoorBlock.FACING));
		BlockPos outside = door.relative(out);
		BlockPos inside = door.relative(out.getOpposite());
		Direction[] sides = {out.getClockWise(), out.getCounterClockWise()};
		BlockPos spot = null;
		BlockState state = null;
		for (int dy = 1; dy >= 0 && spot == null; dy--) {
			for (Direction side : sides) {
				BlockPos at = outside.relative(side).above(dy);
				BlockPos wall = door.relative(side).above(dy);
				if (free(level, at, blocked) && level.getBlockState(wall).isFaceSturdy(level, wall, out)) {
					spot = at;
					state = LifeBlocks.HOUSE_SIGN.facing(out, false);
					break;
				}
			}
		}
		for (boolean onStep : new boolean[]{true, false}) {
			BlockPos front = onStep ? outside : inside;
			for (Direction side : sides) {
				BlockPos at = front.relative(side);
				if (spot != null || !free(level, at, blocked) || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) continue;
				spot = at;
				state = LifeBlocks.HOUSE_SIGN.facing(onStep ? out : out.getOpposite(), true);
			}
		}
		if (spot == null || state == null) return null;
		// the sign is bought for the new home (the treasury helps a family without money)
		com.vnap.village.market.Market.order(level, villager, spot, LifeBlocks.HOUSE_SIGN.asItem());
		level.setBlock(spot, state, 3);
		if (!(level.getBlockEntity(spot) instanceof SignBlockEntity sign)) return null;
		com.vnap.Mc.writeSign(sign, false, Component.literal(signName(villager)), Component.literal(MARKER));
		sign.setChanged();
		return spot.immutable();
	}

	/**
	 * A house marked before the nameplate (22.10.2026) has a vanilla sign standing south whatever the door: it comes
	 * down once the nameplate is up by the door.
	 */
	private static void rehang(ServerLevel level, Villager villager, BlockPos bed) {
		BlockPos old = FamilyHome.findSign(level, bed, null, "");
		if (old == null || level.getBlockState(old).is(LifeBlocks.HOUSE_SIGN)) return;
		BlockPos door = nearestDoor(level, bed);
		if (door == null) return;
		// the new one first: a house without its sign is anyone's
		BlockPos sign = hangSign(level, villager, bed, door, forbidden(level, bed, door));
		if (sign == null) return;
		level.removeBlock(old, false);
		Family.state(villager).signPos = sign;
		Family.rewriteSign(level, villager);
	}

	private static boolean free(ServerLevel level, BlockPos at, Set<BlockPos> blocked) {
		BlockState there = level.getBlockState(at);
		return !blocked.contains(at) && (there.isAir() || (there.canBeReplaced() && there.getFluidState().isEmpty()));
	}

	/** The way out through this door: away from the bed's room, else toward the open sky, else as a player sets a door. */
	static Direction outOf(ServerLevel level, BlockPos bed, BlockPos door, Direction facing) {
		Set<BlockPos> room = FamilyHome.room(level, bed);
		boolean front = room.contains(door.relative(facing));
		boolean back = room.contains(door.relative(facing.getOpposite()));
		if (front != back) return front ? facing.getOpposite() : facing;
		boolean frontSky = level.canSeeSky(door.relative(facing).above());
		boolean backSky = level.canSeeSky(door.relative(facing.getOpposite()).above());
		return frontSky && !backSky ? facing : facing.getOpposite();
	}

	/** The name on the sign: a custom name, else one generated for the villager's gender and kept as its name. */
	public static String nameOf(Villager villager) {
		return signName(villager);
	}

	private static String signName(Villager villager) {
		if (villager.hasCustomName()) {
			Component custom = villager.getCustomName();
			if (custom != null) {
				String existing = custom.getString().trim();
				if (!existing.isEmpty()) return existing.length() > 15 ? existing.substring(0, 15) : existing;
			}
		}
		Family.ensureGender(villager);
		String generated = Family.givenName(villager);
		villager.setCustomName(Component.literal(generated));
		villager.setCustomNameVisible(true);
		return generated;
	}

	static @Nullable BlockPos nearestDoor(ServerLevel level, BlockPos bed) {
		BlockPos found = null;
		int best = Integer.MAX_VALUE;
		BlockPos min = bed.offset(-PAIR, -2, -PAIR);
		BlockPos max = bed.offset(PAIR, 2, PAIR);
		for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
			BlockPos pos = cursor.immutable();
			if (!(level.getBlockState(pos).getBlock() instanceof DoorBlock)) continue;
			if (level.getBlockState(pos).getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) continue;
			int dist = pos.distManhattan(bed);
			if (dist <= PAIR && dist < best) {
				best = dist;
				found = pos.immutable();
			}
		}
		return found;
	}

	/**
	 * A home sign whose villager is gone (one that died before 18.10.2026: a death did not free the house then, and
	 * a bed claimed by its foot kept its ticket taken for good). Nobody of that name lives around and no loaded
	 * villager has its home in this bed: the sign comes down and the ticket is given back. True when it did.
	 */
	private static boolean freeOrphanSign(ServerLevel level, BlockPos bed) {
		BlockPos sign = FamilyHome.findSign(level, bed, null, "");
		if (sign == null || !(level.getBlockEntity(sign) instanceof SignBlockEntity)) return false;
		// the owner is whoever sleeps here, not whoever bears the name on the sign: a namesake elsewhere in the village
		// kept a dead villager's house taken for good
		boolean lives = !level.getEntitiesOfClass(Villager.class, new AABB(bed).inflate(96.0), villager -> villager.isAlive()
				&& villager instanceof VillagerLifeData data && data.vnap$homeBed() != null && data.vnap$homeBed().closerThan(bed, PAIR)).isEmpty();
		// one up the spire lives here all the same (Expeditions)
		if (lives || com.vnap.spire.Expeditions.holds(level.getServer(), bed)) return false;
		level.removeBlock(sign, false);
		FamilyHome.release(level, bed);
		return !marked(level, bed);
	}

	private static boolean marked(ServerLevel level, BlockPos bed) {
		BlockPos min = bed.offset(-PAIR, -2, -PAIR);
		BlockPos max = bed.offset(PAIR, 2, PAIR);
		for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
			BlockPos pos = cursor.immutable();
			if (level.getBlockEntity(pos) instanceof SignBlockEntity sign
					&& isMarker(com.vnap.Mc.signLine(sign, 1))) {
				return true;
			}
		}
		return false;
	}

	public static boolean sameBed(ServerLevel level, BlockPos claimed, BlockPos pos) {
		if (claimed.equals(pos)) return true;
		BlockState state = level.getBlockState(claimed);
		if (!(state.getBlock() instanceof BedBlock)) return false;
		Direction facing = state.getValue(BedBlock.FACING);
		BlockPos other = state.getValue(BedBlock.PART) == BedPart.HEAD
				? claimed.relative(facing.getOpposite())
				: claimed.relative(facing);
		return other.equals(pos);
	}

	static boolean stillBed(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getBlock() instanceof BedBlock;
	}

	private static void clearClaim(Villager villager, VillagerLifeData data) {
		BlockPos lost = data.vnap$homeBed();
		if (lost != null) Family.onLostBed(villager, lost);
		data.vnap$setHomeBed(null);
		villager.getBrain().eraseMemory(MemoryModuleType.HOME);
	}

	private static List<Villager> nearby(ServerLevel level, BlockPos pos) {
		entityQueries++;
		return level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(SCAN), Villager::isAlive);
	}

	private static boolean allowScan(ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		if (now != scanTick) {
			scanTick = now;
			SCAN_BUDGET.clear();
		}
		long key = com.vnap.Mc.chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
		int used = SCAN_BUDGET.get(key);
		if (used >= SCANS_PER_CHUNK) return false;
		SCAN_BUDGET.put(key, used + 1);
		return true;
	}

	public static @Nullable MoodState moodAt(ServerLevel level, BlockPos meeting) {
		VillageMoodSavedData data = VillageMoodSavedData.get(level);
		for (MoodState candidate : data.all()) {
			if (candidate.meetingPos.closerThan(meeting, com.vnap.village.town.TownRules.SPAN)) return candidate;
		}
		return data.get(com.vnap.Mc.chunkKey(meeting.getX() >> 4, meeting.getZ() >> 4));
	}

	private static void say(Villager villager, String id) {
		if (!(villager.level() instanceof ServerLevel level)) return;
		long now = level.getGameTime();
		if (now == lifeLineTick) return;
		lifeLineTick = now;
		Speech.lifeLine(villager, id);
	}
}
