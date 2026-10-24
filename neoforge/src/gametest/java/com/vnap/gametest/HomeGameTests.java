package com.vnap.gametest;

import com.vnap.village.town.TownRules;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.HouseProject;
import com.vnap.village.life.Hands;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.WorkshopState;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Villagers' own houses: every plan in every palette, built by hand storey by storey, planned clear of workshops
 * and the bell, started only by the long homeless where no bed is free, and moved into when done.
 */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class HomeGameTests {
	private static final long WORK_TIME = 3000L;
	private static final long TODAY = 0L;
	private static final BlockPos BELL = new BlockPos(50, 2, 50);
	private static final int STEPS_PER_TICK = 12;
	/** The villagers' plans and the mayor's hall. */
	static final List<String> PLANS = java.util.stream.Stream.concat(HomeProjects.PLANS.stream(),
			com.vnap.village.home.Mayors.HALLS.stream()).toList();

	/**
	 * Each plan goes up in build order with the builder's own reach: before every block the builder walks (a real
	 * path) to a spot it can set the block from, exactly as {@link Hands} picks one. Nothing may be left unreachable.
	 */
	@GameTest(batch = "homes", template = "home_platform", timeoutTicks = 600)
	public static void everyPlanCanBeBuiltByHandStoreyByStorey(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Site> sites = new ArrayList<>();
		for (int i = 0; i < PLANS.size(); i++) {
			String name = HousePalette.schematicName(PLANS.get(i), HousePalette.PLAINS);
			SchematicSpec spec = Workshops.schematic(level, name);
			BlockPos origin = helper.absolutePos(new BlockPos(6 + i % 5 * 19, 1, 4 + i / 5 * 32));
			BuildJob job = BuildJob.missing(level, name, spec, origin, Rotation.NONE, 0);
			Villager builder = com.vnap.Mc.create(EntityType.VILLAGER, level);
			if (builder == null) throw new IllegalStateException("no villager");
			com.vnap.Mc.snap(builder, net.minecraft.world.phys.Vec3.atBottomCenterOf(origin.offset(spec.sizeX / 2, 1, -2)));
			builder.setNoAi(true);
			level.addFreshEntity(builder);
			sites.add(new Site(name, spec, origin, job, builder));
		}
		helper.onEachTick(() -> sites.forEach(site -> site.steps(level, STEPS_PER_TICK)));
		helper.succeedWhen(() -> {
			for (Site site : sites) {
				Checks.assertTrue(helper, site.problems.isEmpty(), site.name + " is buildable by hand: " + site.problems);
				Checks.assertTrue(helper, site.job.done(), site.name + " is still going up, " + site.job.remaining() + " left");
			}
			for (Site site : sites) {
				List<String> wrong = mismatches(level, site.spec, site.origin, Rotation.NONE);
				Checks.assertTrue(helper, wrong.isEmpty(), site.name + " stands whole: " + wrong);
			}
		});
	}

	@GameTest(batch = "homes", template = "home_platform")
	public static void everyPlanLoadsWholeInEveryPalette(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		for (String plan : PLANS) {
			SchematicSpec plains = Workshops.schematic(level, HousePalette.schematicName(plan, HousePalette.PLAINS));
			if (plains.blocks.isEmpty()) problems.add(plan + " is missing");
			for (String palette : java.util.stream.Stream.concat(HousePalette.ALL.stream(), HousePalette.FINES.stream()).toList()) {
				SchematicSpec spec = Workshops.schematic(level, HousePalette.schematicName(plan, palette));
				String name = plan + "@" + palette;
				if (spec.blocks.size() != plains.blocks.size()) problems.add(name + " lost blocks");
				int beds = count(spec, BedBlock.class);
				if (beds < 2 || beds % 2 != 0) problems.add(name + " has no whole bed");
				if (count(spec, DoorBlock.class) < 2) problems.add(name + " has no door");
				if (!palette.equals(HousePalette.PLAINS) && same(spec, plains)) problems.add(name + " looks like plains");
			}
		}
		SchematicSpec desert = Workshops.schematic(level, HousePalette.schematicName("cottage", "desert"));
		SchematicSpec taiga = Workshops.schematic(level, HousePalette.schematicName("cottage", "taiga"));
		Checks.assertTrue(helper, has(desert, Blocks.SMOOTH_SANDSTONE) && !has(desert, Blocks.OAK_PLANKS), "a desert house is sandstone");
		Checks.assertTrue(helper, has(taiga, Blocks.SPRUCE_PLANKS) && !has(taiga, Blocks.OAK_PLANKS), "a taiga house is spruce");

		BlockPos origin = helper.absolutePos(new BlockPos(20, 1, 20));
		String name = HousePalette.schematicName("manor", "snowy");
		SchematicSpec manor = Workshops.schematic(level, name);
		BuildJob job = BuildJob.all(name, manor, origin, Rotation.CLOCKWISE_90, 0);
		while (!job.done()) {
			job.skipDone(level);
			job.placeNext(level, null);
		}
		problems.addAll(mismatches(level, manor, origin, Rotation.CLOCKWISE_90));
		Checks.assertTrue(helper, problems.isEmpty(), "every plan in every palette: " + problems);
		helper.succeed();
	}

	@GameTest(batch = "homes", template = "home_platform")
	public static void theBiomePicksThePalette(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<ResourceKey<Biome>, String> expected = Map.of(
				Biomes.PLAINS, "plains", Biomes.MEADOW, "plains",
				Biomes.DESERT, "desert",
				Biomes.SAVANNA, "savanna",
				Biomes.SNOWY_PLAINS, "snowy",
				Biomes.TAIGA, "taiga");
		List<String> wrong = new ArrayList<>();
		var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
		expected.forEach((key, palette) -> {
			String got = HousePalette.forBiome(biomes.getHolderOrThrow(key));
			if (!got.equals(palette)) wrong.add(com.vnap.Mc.id(key) + " -> " + got);
		});
		Checks.assertTrue(helper, wrong.isEmpty(), "each biome builds in its own palette: " + wrong);
		helper.succeed();
	}

	@GameTest(batch = "homes", template = "home_platform")
	public static void aHouseIsPlannedClearOfWorkshopsAndTheBell(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		List<BoundingBox> workshops = new ArrayList<>();
		BlockPos[] sites = {new BlockPos(64, 2, 50), new BlockPos(36, 2, 50), new BlockPos(50, 2, 64), new BlockPos(50, 2, 36)};
		for (BlockPos rel : sites) {
			helper.setBlock(rel, Blocks.STONECUTTER);
			Villager mason = villager(helper, rel.offset(0, 0, -2), "mason");
			WorkshopState state = Workshops.plan(level, mason, "mason", helper.absolutePos(rel));
			((VillagerLifeData) mason).vnap$setWorkshop(state);
			workshops.add(Workshops.footprint(state));
		}
		Villager owner = homeless(helper, level, "none");
		HouseProject house = HomeProjects.start(level, owner, TODAY);
		Checks.assertTrue(helper, house != null, "a site is found around the bell");
		if (house == null) return;
		BoundingBox box = house.footprint(level);
		for (BoundingBox workshop : workshops) {
			Checks.assertFalse(helper, box.intersects(workshop), "the house " + box + " stays off the workshop " + workshop);
		}
		Checks.assertFalse(helper, box.inflatedBy(3).isInside(bell), "the house keeps away from the bell");
		double distance = Math.sqrt(box.getCenter().distSqr(bell));
		Checks.assertTrue(helper, distance >= TownRules.HOUSE_MIN_DISTANCE - 6 && distance <= TownRules.HOUSE_MAX_DISTANCE + 6,
				"the house is in the village, " + (int) distance + " from the bell");
		Checks.assertTrue(helper, box.minY() == bell.getY() - 1, "the house stands on the ground, from y " + box.minY());

		Villager second = homeless(helper, level, "none");
		Checks.assertTrue(helper, HomeProjects.blocker(level, second, TODAY + 5) == null,
				"houses go up side by side, was " + HomeProjects.blocker(level, second, TODAY + 5));
		Checks.assertTrue(helper, "building its own house".equals(HomeProjects.blocker(level, owner, TODAY + 5)), "one of its own at a time");
		HouseProject other = HomeProjects.start(level, second, TODAY);
		Checks.assertTrue(helper, other != null && !other.footprint(level).inflatedBy(1).intersects(box), "two houses never overlap");
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		helper.succeed();
	}

	@GameTest(batch = "homes", template = "home_platform")
	public static void onlyTheLongHomelessWithoutAHomeStartAHouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		bell(helper);
		Villager owner = homeless(helper, level, "none");
		Checks.assertTrue(helper, HomeProjects.blocker(level, owner, TODAY + 5) == null,
				"a villager homeless for days may build, was " + HomeProjects.blocker(level, owner, TODAY + 5));
		owner.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, TODAY + 4);
		Checks.assertTrue(helper, "not long homeless".equals(HomeProjects.blocker(level, owner, TODAY + 5)), "a day without a bed is not enough");
		owner.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, TODAY - 5);

		Villager baby = homeless(helper, level, "none");
		baby.setAge(-24000);
		Checks.assertTrue(helper, "baby".equals(HomeProjects.blocker(level, baby, TODAY + 5)), "babies do not build");

		Villager lost = homeless(helper, level, "none");
		lost.getBrain().eraseMemory(MemoryModuleType.MEETING_POINT);
		Checks.assertTrue(helper, "no bell".equals(HomeProjects.blocker(level, lost, TODAY + 5)), "no house without a village");

		// a bed slept in without a deed is lodging: the lodger still gets a home of its own
		Villager lodger = homeless(helper, level, "none");
		lodger.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(1, 2, 1))));
		Checks.assertTrue(helper, HomeProjects.blocker(level, lodger, TODAY + 5) == null, "a lodger is not housed, was " + HomeProjects.blocker(level, lodger, TODAY + 5));
		Villager housed = homeless(helper, level, "none");
		if (housed instanceof com.vnap.entity.VillagerLifeData data) data.vnap$setHomeBed(helper.absolutePos(new BlockPos(1, 2, 1)));
		Checks.assertTrue(helper, "home".equals(HomeProjects.blocker(level, housed, TODAY + 5)), "a villager with a home of its own does not build");
		helper.succeed();
	}

	@GameTest(batch = "homes", template = "home_platform", timeoutTicks = 100)
	public static void theOwnerMovesIntoTheNewBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = bell(helper);
		Villager owner = homeless(helper, level, "none");
		HouseProject house = HomeProjects.start(level, owner, TODAY);
		Checks.assertTrue(helper, house != null, "a site is found");
		if (house == null) return;
		BuildJob job = house.job(level);
		// set at once for the test: no stall to buy the materials at
		job.site = null;
		while (!job.done()) {
			job.skipDone(level);
			job.placeNext(level, null);
		}
		BlockPos bed = house.bed(level);
		Checks.assertTrue(helper, bed != null && level.getBlockState(bed).getBlock() instanceof BedBlock, "the plan's bed stands");
		if (bed == null) return;
		Villager squatter = homeless(helper, level, "none");
		squatter.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
		helper.runAfterDelay(2, () -> level.getPoiManager().take(type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(bed), bed, 1));
		// its family tick may come before the house is done: a bed of a house going up for another is not its to claim
		helper.runAfterDelay(3, () -> com.vnap.dialogue.VillagerHome.tick(level, squatter, TODAY));
		helper.runAfterDelay(4, () -> {
			HomeProjects.finish(level, house);
			Checks.assertFalse(helper, squatter.getBrain().hasMemoryValue(MemoryModuleType.HOME), "a neighbour who grabbed the new bed gives it up");
			Checks.assertTrue(helper, house.done, "the project is done");
			Checks.assertTrue(helper, bed.equals(((VillagerLifeData) owner).vnap$homeBed()),
					"the owner's bed is the new one, was " + ((VillagerLifeData) owner).vnap$homeBed() + " not " + bed);
			GlobalPos home = owner.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
			Checks.assertTrue(helper, home != null && home.pos().equals(bed), "HOME points at the new bed, was " + home);
			Checks.assertTrue(helper, level.getPoiManager().getCountInRange(type -> type.is(PoiTypes.HOME), bed, 1,
					PoiManager.Occupancy.HAS_SPACE) == 0, "the bed is taken");
			Checks.assertTrue(helper, HomeProjects.active(level, bell) == null, "the village may plan the next house");
			VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
			helper.succeed();
		});
	}

	@GameTest(batch = "life_day", template = "home_platform", timeoutTicks = 1000)
	public static void theOwnerBuildsByHandAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, WORK_TIME);
		BlockPos bell = bell(helper);
		Villager owner = live(homeless(helper, level, "none"), level, bell);
		Villager helperVillager = live(homeless(helper, level, "none"), level, bell);
		helperVillager.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, TODAY);
		// one of a household (no buyer of its own home): a homeless helper began its own house on its first day half the time
		com.vnap.dialogue.Family.state(helperVillager).home = bell;
		HouseProject house = HomeProjects.start(level, owner, TODAY);
		Checks.assertTrue(helper, house != null, "a site is found");
		if (house == null) return;
		// no stall in a test: the materials are on the site already
		com.vnap.village.market.Materials.bill(house.schematic(level)).forEach(house.site::add);
		Set<String> builders = new HashSet<>();
		helper.onEachTick(() -> {
			if (owner.getMainHandItem().getItem() instanceof BlockItem) builders.add("owner");
			// on this house (a road of the town may take it meanwhile: that is other work)
			if (helperVillager.getMainHandItem().getItem() instanceof BlockItem && com.vnap.village.life.LifeBrain.running(helperVillager).stream()
					.anyMatch(b -> b instanceof com.vnap.village.life.HouseBehavior)) builders.add("helper");
		});
		helper.runAfterDelay(950, () -> {
			BuildJob job = house.job(level);
			Checks.assertTrue(helper, job.cursor() >= 6, "the house goes up by hand, " + job.cursor() + " set");
			// one builder a house: helpers only got in each other's way (TownRules.HOUSE_HELPERS)
			Checks.assertTrue(helper, builders.contains("owner") && !builders.contains("helper"), "the owner alone carries blocks: " + builders + "; the helper at " + helperVillager.blockPosition().toShortString() + " running "
					+ com.vnap.village.life.LifeBrain.running(helperVillager).stream().map(b -> b.getClass().getSimpleName()).toList() + ", house at "
					+ house.origin.toShortString() + ", " + com.vnap.village.market.Housing.lastWhy);
			VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
			helper.succeed();
		});
	}

	/** A broken house is noticed the first day and mended from the next by its household; a player's block stays. */
	@GameTest(batch = "homes", template = "home_platform")
	public static void aBrokenHouseIsMendedByItsHousehold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, WORK_TIME);
		BlockPos bell = bell(helper);
		Villager owner = homeless(helper, level, "none");
		HouseProject house = HomeProjects.start(level, owner, TODAY);
		if (house == null) {
			Checks.fail(helper, "a site is found");
			return;
		}
		com.vnap.village.market.Materials.bill(house.schematic(level)).forEach(house.site::add);
		house.job(level).completeNow(level);
		HomeProjects.finish(level, house);
		List<BlockPos> holes = new ArrayList<>();
		java.util.Map<BlockPos, net.minecraft.world.level.block.Block> planned = new java.util.HashMap<>();
		for (SchematicSpec.PlacedBlock block : house.schematic(level).blocks) {
			if (holes.size() == 3) break;
			BlockPos pos = BuildJob.at(house.origin, house.rotation, block.rel());
			if (block.rel().getY() < 2 || BuildJob.naturalGround(block.state()) || block.state().hasBlockEntity()
					|| !level.getBlockState(pos).is(block.state().getBlock()) || !level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) continue;
			planned.put(pos, block.state().getBlock());
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			holes.add(pos);
		}
		if (holes.size() < 3) {
			Checks.fail(helper, "three blocks of the house knocked out");
			return;
		}
		level.setBlock(holes.get(2), Blocks.GLASS.defaultBlockState(), 2);
		HomeProjects.inspect(level, house, owner, TODAY);
		Checks.assertTrue(helper, !house.mending && house.damagedDay == TODAY, "the damage is noticed, not mended the same day");
		HomeProjects.inspect(level, house, owner, TODAY + 1);
		Checks.assertTrue(helper, house.mending && house.done && house.owner.equals(owner.getUUID()), "the household mends it from the next day");
		Checks.assertTrue(helper, HomeProjects.forBuilder(level, owner) == house, "the one without a trade goes at it in the day");
		BuildJob job = house.job(level);
		job.site = null;
		job.completeNow(level);
		HomeProjects.mended(level, house);
		Checks.assertTrue(helper, !house.mending && level.getBlockState(holes.get(0)).is(planned.get(holes.get(0)))
				&& level.getBlockState(holes.get(1)).is(planned.get(holes.get(1))), "the holes are filled");
		Checks.assertTrue(helper, level.getBlockState(holes.get(2)).is(Blocks.GLASS), "a block someone set in a hole is left be");
		HomeProjects.inspect(level, house, owner, TODAY + 2);
		Checks.assertTrue(helper, !house.mending && house.damagedDay < 0, "a whole house is not mended again");
		level.setBlock(holes.get(0), Blocks.AIR.defaultBlockState(), 2);
		HomeProjects.inspect(level, house, owner, TODAY + 3);
		HomeProjects.inspect(level, house, owner, TODAY + 4);
		HomeProjects.giveUpMending(level, house, TODAY + 4);
		HomeProjects.inspect(level, house, owner, TODAY + 5);
		Checks.assertTrue(helper, !house.mending, "a gap nobody reached rests a few days");
		HomeProjects.inspect(level, house, owner, TODAY + 7);
		Checks.assertTrue(helper, house.mending, "and is tried again");
		house.mending = false;
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		helper.succeed();
	}

	static final class Site {
		final String name;
		final SchematicSpec spec;
		final BlockPos origin;
		final BuildJob job;
		final Villager builder;
		final List<String> problems = new ArrayList<>();
		int deferred;

		Site(String name, SchematicSpec spec, BlockPos origin, BuildJob job, Villager builder) {
			this.name = name;
			this.spec = spec;
			this.origin = origin;
			this.job = job;
			this.builder = builder;
		}

		/** The cells a builder could stand on for {@code target} and whether a path reaches the closest. */
		String why(ServerLevel level, BlockPos target) {
			List<BlockPos> spots = new ArrayList<>();
			for (BlockPos cursor : BlockPos.betweenClosed(target.offset(-4, -32, -4), target.offset(4, 10, 4))) {
				if (Hands.standable(level, cursor, builder) && Hands.covers(net.minecraft.world.phys.Vec3.atBottomCenterOf(cursor), target, Hands.REACH)) spots.add(cursor.immutable());
			}
			spots.sort(java.util.Comparator.comparingDouble(cell -> cell.distToCenterSqr(builder.position())));
			if (spots.isEmpty()) return "[no standable cell in reach]";
			var path = builder.getNavigation().createPath(spots.get(0), 0);
			return "[" + spots.size() + " cells, closest " + spots.get(0).subtract(origin).toShortString() + " path="
					+ (path == null ? "null" : path.canReach() + "/" + path.getNodeCount()) + " ground=" + builder.onGround()
					+ " in=" + level.getBlockState(builder.blockPosition()).getBlock() + "/" + level.getBlockState(builder.blockPosition().above()).getBlock() + "]";
		}

		void steps(ServerLevel level, int count) {
			for (int i = 0; i < count && problems.isEmpty() && !job.done(); i++) {
				job.skipDone(level);
				if (job.done()) return;
				BlockPos next = job.upcoming(1).get(0);
				boolean inside = builder.getBoundingBox().intersects(new AABB(next));
				if (inside || !Hands.inReach(builder, next)) {
					builder.setOnGround(true);
					BlockPos spot = Hands.findSpot(level, builder, job, next);
					if (spot == null) {
						job.defer();
						if (++deferred >= job.remaining()) problems.add("nowhere to stand for " + next.subtract(origin).toShortString()
								+ " from " + builder.blockPosition().subtract(origin).toShortString() + " " + why(level, next));
						continue;
					}
					builder.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				}
				job.placeNext(level, builder);
				deferred = 0;
			}
		}
	}

	static List<String> mismatches(ServerLevel level, SchematicSpec spec, BlockPos origin, Rotation rotation) {
		List<String> wrong = new ArrayList<>();
		for (SchematicSpec.PlacedBlock block : spec.blocks) {
			BlockPos pos = BuildJob.at(origin, rotation, block.rel());
			if (!level.getBlockState(pos).is(block.state().getBlock())) {
				wrong.add(block.rel().toShortString() + "=" + level.getBlockState(pos).getBlock());
				if (wrong.size() > 8) break;
			}
		}
		return wrong;
	}

	private static int count(SchematicSpec spec, Class<? extends Block> type) {
		int n = 0;
		for (SchematicSpec.PlacedBlock block : spec.blocks) {
			if (type.isInstance(block.state().getBlock())) n++;
		}
		return n;
	}

	private static boolean has(SchematicSpec spec, Block wanted) {
		return spec.blocks.stream().anyMatch(block -> block.state().is(wanted));
	}

	private static boolean same(SchematicSpec a, SchematicSpec b) {
		for (int i = 0; i < a.blocks.size(); i++) {
			if (!a.blocks.get(i).state().equals(b.blocks.get(i).state())) return false;
		}
		return true;
	}

	private static BlockPos bell(GameTestHelper helper) {
		helper.setBlock(BELL, Blocks.BELL);
		return helper.absolutePos(BELL);
	}

	/** A villager by the bell, without a bed for five days. */
	private static Villager homeless(GameTestHelper helper, ServerLevel level, String profession) {
		Villager villager = com.vnap.Mc.create(EntityType.VILLAGER, level);
		if (villager == null) throw new IllegalStateException("no villager");
		com.vnap.Mc.snap(villager, net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(BELL.offset(2, 0, 2))));
		villager.setNoAi(true);
		level.addFreshEntity(villager);
		VillagerProfession trade = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.withDefaultNamespace(profession));
		villager.setVillagerData(com.vnap.Mc.withProfession(villager.getVillagerData(), trade));
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), helper.absolutePos(BELL)));
		villager.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, TODAY - 5);
		return villager;
	}

	private static Villager villager(GameTestHelper helper, BlockPos rel, String profession) {
		Villager villager = helper.spawn(EntityType.VILLAGER, rel);
		villager.setNoAi(true);
		VillagerProfession trade = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.withDefaultNamespace(profession));
		villager.setVillagerData(com.vnap.Mc.withLevel(com.vnap.Mc.withProfession(villager.getVillagerData(), trade), 2));
		return villager;
	}

	private static Villager live(Villager villager, ServerLevel level, BlockPos bell) {
		villager.setNoAi(false);
		villager.refreshBrain(level);
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		return villager;
	}
}
