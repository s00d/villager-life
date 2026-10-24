package com.vnap.gametest;

import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.MoodState;
import com.vnap.mood.VillageMoodApi;
import com.vnap.mood.VillageMoodSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class VillagerHomeGameTests {
	@GameTest(template = "platform")
	public static void doorAndBedIsClaimedOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(10, 2, 10);
		layHouse(helper, door);
		Villager first = person(helper, 10, 8);
		VillagerHome.tick(level, first, 0L);
		Checks.assertTrue(helper, hasMarker(helper, door), "the door gets a home sign: " + VillagerHome.whyNoHome(level, first));
		Component given = first.getCustomName();
		String written = signLine(helper, door, 0);
		Checks.assertTrue(helper, given != null && given.getString().equals(written) && !written.isEmpty(),
				"a nameless villager is named, and the sign shows that name");
		BlockPos claimed = ((VillagerLifeData) first).vnap$homeBed();
		Checks.assertTrue(helper, claimed != null, "the bed is claimed");
		Villager second = person(helper, 12, 8);
		VillagerHome.tick(level, second, 0L);
		BlockPos other = ((VillagerLifeData) second).vnap$homeBed();
		Checks.assertTrue(helper, other == null || !other.equals(claimed), "a second villager does not take that bed");
		helper.succeed();
	}

	/** A villager who lived alone dies: its house is free again, and an old world's leftover sign is taken down. */
	@GameTest(template = "platform")
	public static void aHouseIsFreedWhenItsOwnerIsGone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(50, 2, 10);
		layHouse(helper, door);
		Villager first = person(helper, 50, 8);
		VillagerHome.tick(level, first, 0L);
		Checks.assertTrue(helper, hasMarker(helper, door), "the first villager moves in");
		com.vnap.Mc.kill(first);
		Checks.assertTrue(helper, !hasMarker(helper, door), "it dies alone: the sign comes down");
		Villager second = person(helper, 52, 8);
		VillagerHome.tick(level, second, 0L);
		Checks.assertTrue(helper, ((VillagerLifeData) second).vnap$homeBed() != null, "and a neighbour can move in");
		BlockPos otherDoor = new BlockPos(70, 2, 10);
		layHouse(helper, otherDoor);
		helper.setBlock(otherDoor.east(), Blocks.OAK_SIGN);
		if (helper.getLevel().getBlockEntity(helper.absolutePos(otherDoor.east())) instanceof SignBlockEntity sign) {
			com.vnap.Mc.writeSign(sign, false, Component.literal("Edna"), Component.literal(VillagerHome.MARKER));
		}
		Villager third = person(helper, 70, 8);
		VillagerHome.tick(level, third, 0L);
		Checks.assertTrue(helper, ((VillagerLifeData) third).vnap$homeBed() != null && signLine(helper, otherDoor, 0).equals(VillagerHome.nameOf(third)),
				"a sign of someone long gone is replaced by the newcomer's");
		helper.succeed();
	}

	/**
	 * Every plan in every turn: the nameplate hangs by the door with its writing to the street (the old sign stood
	 * turned south wherever the door looked), on the wall beside the door or on a post by the doorstep.
	 */
	@GameTest(batch = "homes", template = "home_platform", timeoutTicks = 400)
	public static void aHouseSignLooksOutBesideTheDoorInEveryTurn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(25, 1, 30));
		java.util.List<String> failed = new java.util.ArrayList<>();
		for (String plan : com.vnap.village.home.HomeProjects.PLANS) {
			for (net.minecraft.world.level.block.Rotation turn : net.minecraft.world.level.block.Rotation.values()) {
				com.vnap.village.home.HouseProject house = new com.vnap.village.home.HouseProject(origin.asLong(), origin, java.util.UUID.randomUUID(),
						plan, "plains", origin, turn, 0L);
				com.vnap.village.build.BuildJob job = house.job(level);
				job.site = null;
				while (!job.done()) {
					job.skipDone(level);
					if (!job.done()) job.placeNext(level, null);
				}
				BlockPos bed = house.bed(level);
				Villager villager = helper.spawn(EntityType.VILLAGER, helper.relativePos(origin.above()));
				villager.setNoAi(true);
				String why = bed == null ? "no bed" : VillagerHome.claim(level, villager, bed) ? null : "not claimed";
				BlockPos sign = com.vnap.dialogue.Family.state(villager).signPos;
				if (why == null && (sign == null || !level.getBlockState(sign).is(com.vnap.block.LifeBlocks.HOUSE_SIGN))) why = "no nameplate";
				if (why == null) why = looksOut(level, bed, sign);
				if (why != null) failed.add(plan + " " + turn + ": " + why);
				villager.discard();
				net.minecraft.world.level.levelgen.structure.BoundingBox box = house.footprint(level).inflatedBy(2);
				for (BlockPos pos : BlockPos.betweenClosed(box.minX(), origin.getY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
					level.setBlock(pos, (pos.getY() == origin.getY() ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState(), 2);
				}
			}
		}
		Checks.assertTrue(helper, failed.isEmpty(), "nameplates: " + failed);
		helper.succeed();
	}

	/** Null when the plate by {@code bed}'s door looks out of the house; else what is wrong. */
	private static @org.jspecify.annotations.Nullable String looksOut(ServerLevel level, @org.jspecify.annotations.Nullable BlockPos bed,
			@org.jspecify.annotations.Nullable BlockPos sign) {
		if (bed == null || sign == null) return "nothing";
		net.minecraft.world.level.block.state.BlockState plate = level.getBlockState(sign);
		Direction out = plate.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
		BlockPos door = null;
		for (BlockPos pos : BlockPos.betweenClosed(sign.offset(-2, -2, -2), sign.offset(2, 1, 2))) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
					&& (door == null || pos.distSqr(sign) < door.distSqr(sign))) door = pos.immutable();
		}
		if (door == null) return "no door by it";
		java.util.Set<BlockPos> room = com.vnap.dialogue.FamilyHome.room(level, bed);
		BlockPos ground = sign.atY(door.getY());
		if (room.contains(ground) || room.contains(ground.relative(out))) return "it looks into the room, " + out;
		if (ground.distManhattan(door) > 2) return "not by the door";
		if (!plate.getValue(com.vnap.block.town.HouseSignBlock.POST)) {
			BlockPos wall = sign.relative(out.getOpposite());
			if (!level.getBlockState(wall).isFaceSturdy(level, wall, out)) return "hangs on nothing";
		}
		return null;
	}

	/** Vanilla keeps a bed's POI on its head half and drops a HOME anywhere else: then nobody walks to bed at night. */
	@GameTest(template = "platform")
	public static void homeIsTheHeadOfTheBedAndComesBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(30, 2, 10);
		layHouse(helper, door);
		Villager villager = person(helper, 30, 8);
		BlockPos foot = helper.absolutePos(door.north(2));
		BlockPos head = helper.absolutePos(door.north(3));
		Checks.assertTrue(helper, VillagerHome.claim(level, villager, foot), "the bed is claimed from its foot");
		Checks.assertTrue(helper, head.equals(((VillagerLifeData) villager).vnap$homeBed()) && head.equals(home(villager)),
				"the claim and HOME are on the head half");
		villager.getBrain().eraseMemory(MemoryModuleType.HOME);
		VillagerHome.tick(level, villager, 1L);
		Checks.assertTrue(helper, head.equals(home(villager)), "a HOME vanilla forgot comes back");
		helper.succeed();
	}

	private static @org.jspecify.annotations.Nullable BlockPos home(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	@GameTest(template = "platform")
	public static void namedVillagerKeepsTheNameOnTheSign(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(90, 2, 10);
		layHouse(helper, door);
		Villager villager = person(helper, 90, 8);
		villager.setCustomName(Component.literal("Marta"));
		VillagerHome.tick(level, villager, 0L);
		Checks.assertTrue(helper, "Marta".equals(signLine(helper, door, 0)), "an existing name is written on the sign");
		Component kept = villager.getCustomName();
		Checks.assertTrue(helper, kept != null && kept.getString().equals("Marta"), "the villager keeps that name");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void bareBedIsNotAHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(4, 2, 4), bed(BedPart.FOOT));
		Villager villager = person(helper, 4, 4);
		VillagerHome.tick(level, villager, 0L);
		Checks.assertTrue(helper, ((VillagerLifeData) villager).vnap$homeBed() == null, "a bed without a door is not claimed");
		Checks.assertTrue(helper, !VillagerHome.refuseOtherBed(villager, helper.absolutePos(new BlockPos(6, 1, 4))),
				"sleep somewhere else is still allowed");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void oneChorePerDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(20, 2, 20);
		layHouse(helper, door);
		Villager villager = person(helper, 20, 18);
		bought(villager);
		VillagerHome.tick(level, villager, 0L);
		VillagerHome.tick(level, villager, 1L);
		int decor = decorCount(helper, door);
		Checks.assertTrue(helper, decor == 1, "the first day places one decoration, was " + decor);
		VillagerHome.tick(level, villager, 1L);
		Checks.assertTrue(helper, decorCount(helper, door) == decor, "the same day does not place a second one");
		VillagerHome.tick(level, villager, 2L);
		Checks.assertTrue(helper, decorCount(helper, door) == decor + 1, "the next day places one block");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void choreStaysOutOfTheDoor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(30, 2, 30);
		layHouse(helper, door);
		Villager villager = person(helper, 30, 28);
		for (long day = 0; day < 8; day++) VillagerHome.tick(level, villager, day);
		BlockPos front = door.relative(Direction.SOUTH);
		Checks.assertTrue(helper, helper.getBlockState(door).getBlock() instanceof DoorBlock, "the door stays a door");
		Checks.assertTrue(helper, helper.getBlockState(front).isAir(), "the cell in front of the door stays open");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void decorationStopsAtSix(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(40, 2, 40);
		layHouse(helper, door);
		Villager villager = person(helper, 40, 38);
		bought(villager);
		VillagerHome.tick(level, villager, 0L);
		for (long day = 1; day <= 6; day++) VillagerHome.tick(level, villager, day);
		int placed = decorCount(helper, door);
		Checks.assertTrue(helper, placed == 6, "six decorations are the ceiling");
		VillagerHome.tick(level, villager, 7L);
		VillagerHome.tick(level, villager, 8L);
		Checks.assertTrue(helper, decorCount(helper, door) == placed, "later days place nothing");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void homeBreakHitsHarderThanAForeignBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(50, 2, 50);
		layHouse(helper, door);
		Villager villager = person(helper, 50, 48);
		BlockPos meeting = helper.absolutePos(door);
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), meeting));
		VillagerHome.tick(level, villager, 0L);
		MoodState state = VillageMoodSavedData.get(level).getOrCreate(meeting.asLong(), meeting);
		VillageMoodApi.setExact(level, state, 0.0F, false);
		VillagerHome.onBreak(level, helper.absolutePos(door.offset(6, 0, 0)), Blocks.STONE.defaultBlockState(), 1L);
		Checks.assertTrue(helper, state.mood == 0.0F, "a foreign block does not move mood");
		VillagerHome.onBreak(level, helper.absolutePos(door), level.getBlockState(helper.absolutePos(door)), 1L);
		Checks.assertTrue(helper, state.mood == -20.0F, "their door moves mood by about -20");
		Checks.assertTrue(helper, state.mood < -5.0F, "that hit is stronger than an altar base");
		VillagerHome.onBreak(level, helper.absolutePos(door), Blocks.OAK_DOOR.defaultBlockState(), 1L);
		Checks.assertTrue(helper, state.mood == -20.0F, "the same cell the same day does not hit again");
		VillagerHome.onPainting(level, helper.absolutePos(door.north()), 2L);
		Checks.assertTrue(helper, state.mood == -15.0F, "a painting lifts mood once");
		VillagerHome.onPainting(level, helper.absolutePos(door.north()), 2L);
		Checks.assertTrue(helper, state.mood == -15.0F, "a second painting the same day does nothing");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void crowdScansFourPerChunk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager[] people = new Villager[6];
		for (int i = 0; i < people.length; i++) people[i] = person(helper, 8, 8);
		for (Villager villager : people) VillagerHome.tick(level, villager, 3L);
		Checks.assertTrue(helper, scanned(people, 3L) == 4, "one chunk scans four homeless villagers on a tick");
		for (Villager villager : people) VillagerHome.tick(level, villager, 3L);
		Checks.assertTrue(helper, scanned(people, 3L) == 4, "the same tick does not scan the rest");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void newDoorWakesTheSameDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = person(helper, 12, 12);
		VillagerHome.tick(level, villager, 4L);
		layHouse(helper, new BlockPos(12, 2, 14));
		VillagerHome.tick(level, villager, 4L);
		Checks.assertTrue(helper, ((VillagerLifeData) villager).vnap$homeBed() == null, "the same day does not scan again");
		VillagerHome.noteStructureChange();
		VillagerHome.tick(level, villager, 4L);
		Checks.assertTrue(helper, ((VillagerLifeData) villager).vnap$homeBed() != null, "a new door wakes one more scan");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void stoneBreakDoesNotSearchVillagers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(16, 2, 16);
		layHouse(helper, door);
		Villager villager = person(helper, 16, 14);
		VillagerHome.tick(level, villager, 0L);
		int before = VillagerHome.entityQueries();
		VillagerHome.onBreak(level, helper.absolutePos(new BlockPos(16, 2, 22)), Blocks.STONE.defaultBlockState(), 1L);
		Checks.assertTrue(helper, VillagerHome.entityQueries() == before, "stone does not search villagers");
		VillagerHome.onBreak(level, helper.absolutePos(door), level.getBlockState(helper.absolutePos(door)), 1L);
		Checks.assertTrue(helper, VillagerHome.entityQueries() == before + 1, "a door searches once");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void moodLineDoesNotSearchVillagers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = person(helper, 24, 24);
		BlockPos meeting = helper.absolutePos(new BlockPos(24, 2, 24));
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), meeting));
		MoodState state = VillageMoodSavedData.get(level).getOrCreate(meeting.asLong(), meeting);
		state.mood = 60.0F;
		int before = VillagerHome.entityQueries();
		Checks.assertTrue(helper, "life_warm".equals(VillagerHome.moodGroup(villager)), "a warm meeting uses the warm line");
		Checks.assertTrue(helper, VillagerHome.entityQueries() == before, "the mood line does not search villagers");
		Villager stranger = person(helper, 26, 24);
		Checks.assertTrue(helper, VillagerHome.moodGroup(stranger) == null, "no meeting leaves the calendar alone");
		helper.succeed();
	}

	private static int scanned(Villager[] people, long day) {
		int count = 0;
		for (Villager villager : people) {
			if (((VillagerLifeData) villager).vnap$homeChoreDay() == day) count++;
		}
		return count;
	}

	private static void layHouse(GameTestHelper helper, BlockPos door) {
		helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState()
				.setValue(DoorBlock.FACING, Direction.SOUTH)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState()
				.setValue(DoorBlock.FACING, Direction.SOUTH)
				.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		BlockPos foot = door.north(2);
		helper.setBlock(foot, bed(BedPart.FOOT));
		helper.setBlock(foot.north(), bed(BedPart.HEAD));
		// a roof makes it a room: decor goes inside the house only, against its walls (FamilyHome.decorSpots)
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -6; dz <= 0; dz++) {
				helper.setBlock(door.offset(dx, 3, dz), Blocks.STONE.defaultBlockState());
				boolean wall = Math.abs(dx) == 4 || dz == -6 || (dz == 0 && dx != 0);
				for (int dy = 0; dy <= 2 && wall; dy++) helper.setBlock(door.offset(dx, dy, dz), Blocks.STONE.defaultBlockState());
			}
		}
	}

	private static net.minecraft.world.level.block.state.BlockState bed(BedPart part) {
		return Blocks.RED_BED.defaultBlockState()
				.setValue(BedBlock.FACING, Direction.NORTH)
				.setValue(BedBlock.PART, part);
	}

	private static Villager person(GameTestHelper helper, int x, int z) {
		Villager villager = helper.spawn(EntityType.VILLAGER, x, 3, z);
		villager.setNoAi(true);
		// one the town found there: it takes a house by right (anyone else in a town buys one, Housing)
		villager.getPersistentData().putBoolean(com.vnap.village.market.Housing.FOUNDER, true);
		return villager;
	}

	private static String signLine(GameTestHelper helper, BlockPos door, int line) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(door);
		for (BlockPos pos : BlockPos.betweenClosed(abs.offset(-3, -1, -3), abs.offset(3, 2, 3))) {
			if (level.getBlockEntity(pos) instanceof SignBlockEntity sign
					&& VillagerHome.isMarker(com.vnap.Mc.signLine(sign, 1))) {
				return com.vnap.Mc.signLine(sign, line);
			}
		}
		return "";
	}

	private static boolean hasMarker(GameTestHelper helper, BlockPos door) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(door);
		for (BlockPos pos : BlockPos.betweenClosed(abs.offset(-3, -1, -3), abs.offset(3, 2, 3))) {
			if (level.getBlockEntity(pos) instanceof SignBlockEntity sign
					&& VillagerHome.isMarker(com.vnap.Mc.signLine(sign, 1))) {
				return true;
			}
		}
		return false;
	}

	private static int count(GameTestHelper helper, BlockPos door, net.minecraft.world.level.block.Block block) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(door);
		int found = 0;
		for (BlockPos pos : BlockPos.betweenClosed(abs.offset(-12, -2, -12), abs.offset(12, 3, 12))) {
			if (level.getBlockState(pos).is(block)) found++;
		}
		return found;
	}

	private static int decorCount(GameTestHelper helper, BlockPos door) {
		int blocks = count(helper, door, Blocks.FLOWER_POT)
				+ count(helper, door, Blocks.LANTERN)
				+ count(helper, door, Blocks.CANDLE);
		ServerLevel level = helper.getLevel();
		AABB box = new AABB(helper.absolutePos(door)).inflate(8.0);
		return blocks + level.getEntitiesOfClass(Painting.class, box, Painting::isAlive).size();
	}

	/** What a head puts up in the house is bought at the stall first: here it has bought it already. */
	private static void bought(Villager villager) {
		villager.getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FLOWER_POT, 3));
		villager.getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LANTERN, 3));
		villager.getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAINTING, 3));
		villager.getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CANDLE, 3));
	}
}
