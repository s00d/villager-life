package com.vnap.gametest;

import com.vnap.entity.VillagerLifeData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.build.BuildStages;
import com.vnap.village.life.Hands;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.WorkshopState;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The builder with a live brain: hands, work hours, panic, coming back and the order of the build. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class LifeBuildGameTests {
	private static final long WORK_TIME = 3000L;
	private static final long NIGHT_TIME = 12500L;

	/** Rites build with NO_GROUND (Integer.MIN_VALUE): ordering their blocks must not walk two billion layers. */
	@GameTest(template = "platform")
	public static void buildOrderWithoutGroundIsQuick(GameTestHelper helper) {
		SchematicSpec pen = Workshops.schematic(helper.getLevel(), "festoon");
		long start = System.nanoTime();
		int top = BuildStages.topFloor(pen, BuildJob.NO_GROUND);
		long millis = (System.nanoTime() - start) / 1_000_000L;
		Checks.assertTrue(helper, millis < 200, "a rite's build order takes " + millis + " ms");
		Checks.assertTrue(helper, top == Integer.MAX_VALUE || top < pen.sizeY, "and finds no storey floor above the schematic");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void wallsComeBeforeTheRoofAndDoorsAfterWalls(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int roofs = 0;
		for (int i = 0; i < Workshops.PROFESSIONS.size(); i++) {
			String name = Workshops.schematicName(Workshops.PROFESSIONS.get(i), 3);
			SchematicSpec spec = Workshops.schematic(level, name);
			BlockPos origin = helper.absolutePos(new BlockPos(6 + i % 4 * 22, 2, 6 + i / 4 * 22))
					.subtract(Workshops.layout(Workshops.PROFESSIONS.get(i)).focal());
			BuildJob job = BuildJob.missing(level, name, spec, origin, Rotation.NONE, 0);
			Map<BlockPos, SchematicSpec.PlacedBlock> byPos = new HashMap<>();
			for (SchematicSpec.PlacedBlock block : spec.blocks) byPos.put(BuildJob.at(origin, Rotation.NONE, block.rel()), block);
			int topFloor = BuildStages.topFloor(spec, 0);
			int lastStage = -1;
			int lastShellY = Integer.MIN_VALUE;
			int lastWall = -1;
			int firstRoof = Integer.MAX_VALUE;
			int firstDoor = Integer.MAX_VALUE;
			List<BlockPos> order = job.upcoming(job.remaining());
			for (int index = 0; index < order.size(); index++) {
				SchematicSpec.PlacedBlock block = byPos.get(order.get(index));
				if (block == null) continue;
				int stage = BuildStages.stage(block, 0, topFloor);
				if (stage < lastStage) problems.add(name + " goes back a stage at " + block.rel().toShortString());
				lastStage = stage;
				if (stage == 1) {
					if (block.rel().getY() < lastShellY) problems.add(name + " builds a wall top-down at " + block.rel().toShortString());
					lastShellY = block.rel().getY();
					lastWall = index;
				}
				if (stage == 2) firstRoof = Math.min(firstRoof, index);
				if (block.state().getBlock() instanceof DoorBlock) firstDoor = Math.min(firstDoor, index);
			}
			if (firstRoof != Integer.MAX_VALUE) roofs++;
			if (lastWall > firstRoof) problems.add(name + " puts the roof before the last wall");
			if (firstDoor != Integer.MAX_VALUE && firstDoor < lastWall) problems.add(name + " hangs a door before the walls");
		}
		Checks.assertTrue(helper, roofs > 0, "some walled workshop has a roof");
		Checks.assertTrue(helper, problems.isEmpty(), "walls first, roof next, then openings: " + problems);
		helper.succeed();
	}

	@GameTest(batch = "life_day", template = "workshop_platform", timeoutTicks = 900)
	public static void blocksAreSetByHandAfterSwings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, WORK_TIME);
		Villager villager = builder(helper, new BlockPos(10, 2, 10), "mason");
		WorkshopState state = state(villager);
		int[] cursor = {0};
		int[] swings = {0};
		List<BlockPos> next = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		helper.onEachTick(() -> {
			BuildJob job = state.job;
			//? if >=26.3 {
			/*// from 26.3 a swing runs out by itself on the server too, and a new one waits for half the last: ticks of swinging count
			if (villager.isSwinging()) swings[0]++;
			*///?} else {
			// A villager never runs updateSwingTime on the server, so the flag stays up until cleared here.
			if (villager.swinging) {
				swings[0]++;
				villager.swinging = false;
			}
			//?}
			if (job == null) return;
			if (job.cursor() > cursor[0] && !next.isEmpty()) {
				BlockPos placed = next.get(0);
				if (swings[0] == 0) problems.add(placed.toShortString() + " stood without a swing");
				if (!Hands.covers(villager.position(), placed, Hands.REACH + 1.0)) {
					problems.add(placed.toShortString() + " was set from " + villager.blockPosition().toShortString());
				}
				swings[0] = 0;
			}
			cursor[0] = job.cursor();
			next.clear();
			if (!job.done()) next.addAll(job.upcoming(1));
		});
		helper.runAfterDelay(800, () -> {
			BuildJob job = state.job;
			int built = job == null ? state.tier > 0 ? Integer.MAX_VALUE : 0 : job.cursor();
			Checks.assertTrue(helper, problems.isEmpty(), "every block is set by hand: " + problems);
			Checks.assertTrue(helper, built >= 3, "the builder sets a few blocks in work hours, set " + built + " " + brain(villager));
			helper.succeed();
		});
	}

	@GameTest(batch = "life_day", template = "workshop_platform", timeoutTicks = 1400)
	public static void panicStopsTheBuildAndTheBuilderComesBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, WORK_TIME);
		BlockPos site = new BlockPos(30, 2, 30);
		Villager villager = builder(helper, site, "farmer");
		WorkshopState state = state(villager);
		List<Zombie> zombies = new ArrayList<>();
		int[] frozen = {-1};
		helper.runAfterDelay(120, () -> {
			Zombie spawned = com.vnap.Mc.create(EntityType.ZOMBIE, level);
			if (spawned == null) throw new IllegalStateException("a zombie spawns");
			com.vnap.Mc.snap(spawned, villager.getX() + 3.0, villager.getY(), villager.getZ(), 0.0F, 0.0F);
			spawned.setNoAi(true);
			spawned.setPersistenceRequired();
			level.addFreshEntity(spawned);
			spawned.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			zombies.add(spawned);
		});
		int[] panicTicks = {0};
		List<String> builtInPanic = new ArrayList<>();
		helper.onEachTick(() -> {
			if (!villager.getBrain().isActive(Activity.PANIC)) {
				frozen[0] = cursor(state);
				return;
			}
			panicTicks[0]++;
			if (cursor(state) != frozen[0]) builtInPanic.add(String.valueOf(cursor(state)));
		});
		helper.runAfterDelay(300, () -> {
			Checks.assertTrue(helper, panicTicks[0] > 0, "a zombie close by makes the builder panic " + brain(villager));
			Checks.assertTrue(helper, builtInPanic.isEmpty(), "nothing is built while the builder panics: " + builtInPanic);
			zombies.forEach(Zombie::discard);
		});
		int[] beforeScare = {0};
		helper.runAfterDelay(119, () -> beforeScare[0] = cursor(state));
		helper.runAfterDelay(1300, () -> {
			Checks.assertFalse(helper, villager.getBrain().isActive(Activity.PANIC), "the builder calms down");
			Checks.assertTrue(helper, villager.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(state.anchor)) <= 10.0 * 10.0,
					"the builder is back at the workshop, at " + villager.blockPosition().toShortString());
			Checks.assertTrue(helper, cursor(state) > beforeScare[0], "the build goes on after the scare");
			helper.succeed();
		});
	}

	@GameTest(batch = "life_day", template = "workshop_platform", timeoutTicks = 1400)
	public static void aBuilderFarAwayComesBackAndTakesItsJobSiteAgain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, WORK_TIME);
		BlockPos site = new BlockPos(8, 2, 60);
		Villager villager = builder(helper, site, "fletcher");
		WorkshopState state = state(villager);
		helper.runAfterDelay(40, () -> {
			BlockPos away = helper.absolutePos(site.offset(40, 0, 0));
			villager.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			villager.releasePoi(MemoryModuleType.JOB_SITE);
			villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		});
		helper.runAfterDelay(1300, () -> {
			GlobalPos jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
			Checks.assertTrue(helper, jobSite != null && jobSite.pos().equals(state.anchor), "the job site is taken back, was " + jobSite + " " + brain(villager));
			Checks.assertTrue(helper, villager.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(state.anchor)) <= 9.0 * 9.0,
					"the builder walked back, at " + villager.blockPosition().toShortString());
			helper.succeed();
		});
	}

	@GameTest(batch = "life_night", template = "workshop_platform", timeoutTicks = 700)
	public static void atNightTheBuilderGoesToBedInsteadOfBuilding(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, NIGHT_TIME);
		BlockPos site = new BlockPos(20, 2, 20);
		Villager villager = builder(helper, site, "mason");
		WorkshopState state = state(villager);
		String name = Workshops.schematicName(state.profession, 1);
		state.job = BuildJob.missing(level, name, Workshops.schematic(level, name), state.origin, state.rotation, 0);
		state.jobTier = 1;
		BlockPos head = site.offset(-8, 0, 12);
		helper.setBlock(head.south(), bed(BedPart.FOOT));
		helper.setBlock(head, bed(BedPart.HEAD));
		BlockPos bed = helper.absolutePos(head);
		helper.runAfterDelay(1, () -> level.getPoiManager().take(holder -> holder.is(PoiTypes.HOME), (holder, pos) -> pos.equals(bed), bed, 1));
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
		helper.runAfterDelay(600, () -> {
			Checks.assertTrue(helper, villager.getBrain().isActive(Activity.REST), "night is rest time");
			Checks.assertTrue(helper, cursor(state) == 0, "nothing is built at night");
			Checks.assertTrue(helper, villager.isSleeping() || villager.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(bed)) <= 3.0 * 3.0,
					"the builder went to bed, at " + villager.blockPosition().toShortString());
			helper.succeed();
		});
	}

	private static String brain(Villager villager) {
		return "[" + villager.getBrain().getActiveNonCoreActivity() + " day=" + com.vnap.Mc.dayTime(villager.level())
				+ " at=" + villager.blockPosition().toShortString() + " job=" + villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
				+ " hostile=" + villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).isPresent()
				+ " prof=" + com.vnap.Mc.profession(villager.getVillagerData()) + " walk=" + villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent()
				+ " noai=" + villager.isNoAi() + "]";
	}

	private static BlockState bed(BedPart part) {
		return Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, part);
	}

	private static int cursor(WorkshopState state) {
		BuildJob job = state.job;
		return job == null ? state.tier > 0 ? Integer.MAX_VALUE : 0 : job.cursor();
	}

	private static WorkshopState state(Villager villager) {
		WorkshopState state = ((VillagerLifeData) villager).vnap$workshop();
		if (state == null) throw new IllegalStateException("the builder has a workshop");
		return state;
	}

	/**
	 * An apprentice with a live brain, its job block taken and a workshop planned; the first tier starts today.
	 * The brain is rebuilt for the profession as vanilla does on hiring, and the job block becomes a point of
	 * interest only after the tick, so it is taken a tick later.
	 */
	private static Villager builder(GameTestHelper helper, BlockPos site, String profession) {
		ServerLevel level = helper.getLevel();
		Map<String, Block> jobBlocks = Map.of("mason", Blocks.STONECUTTER,
				"farmer", Blocks.COMPOSTER, "fletcher", Blocks.FLETCHING_TABLE);
		helper.setBlock(site, jobBlocks.getOrDefault(profession, Blocks.STONECUTTER).defaultBlockState());
		BlockPos anchor = helper.absolutePos(site);
		Villager villager = helper.spawn(EntityType.VILLAGER, site.getX() + 0.5F, site.getY(), site.getZ() - 2.5F);
		VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.withDefaultNamespace(profession));
		villager.setVillagerData(com.vnap.Mc.withLevel(com.vnap.Mc.withProfession(villager.getVillagerData(), job), 2));
		villager.refreshBrain(level);
		helper.runAfterDelay(1, () -> level.getPoiManager().take(job.heldJobSite(), (holder, pos) -> pos.equals(anchor), anchor, 1));
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), anchor));
		WorkshopState state = Workshops.plan(level, villager, profession, anchor);
		state.checkedDay = -1L;
		// no stall in a test: the materials of every tier are on the site already (bought, as it were)
		for (int tier = 1; tier <= 3; tier++) {
			com.vnap.village.market.Materials.bill(Workshops.schematic(level, Workshops.schematicName(profession, tier))).forEach(state.site::add);
		}
		((VillagerLifeData) villager).vnap$setWorkshop(state);
		return villager;
	}
}
