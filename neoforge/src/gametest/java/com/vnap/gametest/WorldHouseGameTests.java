package com.vnap.gametest;

import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseLevels;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The small houses of the generated villages: each template has a copy as a plan, a storey (reached up a stair flight,
 * villagers climb no ladders) and a cellar, and a family grows its house where the world put it.
 */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class WorldHouseGameTests {
	private WorldHouseGameTests() {
	}

	private static final List<Rotation> TURNS = List.of(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90);

	static SchematicSpec spec(ServerLevel level, String plan) {
		return Workshops.schematic(level, HousePalette.schematicName(plan, HousePalette.PLAINS));
	}

	/** Every level of every plan: the beds of the last stay, the storey adds beds, no work place, the owner's bed by the door. */
	@GameTest(batch = "world_houses", template = "home_platform")
	public static void everyWorldHouseHasAStoreyAndACellarOverItsCopy(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		for (String house : HouseLevels.WORLD_HOUSES) {
			String plan = HouseLevels.WORLD + house;
			SchematicSpec last = null;
			int groundY = 0;
			for (String one = plan; one != null; one = HouseLevels.next(level, one)) {
				SchematicSpec spec = spec(level, one);
				if (spec.blocks.isEmpty()) {
					problems.add(one + " is missing");
					break;
				}
				for (SchematicSpec.PlacedBlock block : spec.blocks) {
					if (PoiTypes.forState(block.state()).filter(type -> !type.is(PoiTypes.HOME)).isPresent()) problems.add(one + " has a work place " + block.state().getBlock());
				}
				BlockPos owner = HouseProject.ownerBed(spec);
				BlockPos door = lowestDoor(spec);
				if (owner == null || door == null || owner.distSqr(door) > 25) problems.add(one + ": the owner's bed " + owner + " is not by the door " + door);
				if (last != null) {
					BlockPos shift = HouseLevels.offset(last, spec, groundY);
					int matched = 0;
					int floor = 0;
					java.util.Map<BlockPos, Block> cells = new java.util.HashMap<>();
					for (SchematicSpec.PlacedBlock block : spec.blocks) cells.put(block.rel(), block.state().getBlock());
					for (SchematicSpec.PlacedBlock block : last.blocks) {
						// the floor itself (the earth under it is dug out for a cellar, the old eaves by the walls come down)
						if (block.rel().getY() != groundY || BuildJob.naturalGround(block.state())
								|| block.state().getCollisionShape(level, BlockPos.ZERO).isEmpty()) continue;
						floor++;
						if (block.state().getBlock().equals(cells.get(block.rel().offset(shift)))) matched++;
					}
					if (matched < floor * 0.8) problems.add(one + " lies over the last level badly: " + matched + "/" + floor + " at " + shift.toShortString());
					BlockPos lastOwner = HouseProject.ownerBed(last);
					if (lastOwner != null && !lastOwner.offset(shift).equals(owner)) problems.add(one + ": the owner's bed moved");
					int beds = beds(spec);
					if (HouseLevels.level(one) == 2 && beds < beds(last) + 1) problems.add(one + " has no more beds than the house");
					if (HouseLevels.level(one) == 3 && (groundY + shift.getY() < 4 || beds != beds(last))) problems.add(one + ": a cellar " + (groundY + shift.getY()) + " deep, " + beds + " beds");
					groundY += shift.getY();
				} else {
					groundY = HouseLevels.floorOf(spec);
				}
				last = spec;
			}
			if (HouseLevels.next(level, plan) == null) problems.add(plan + " has no next level");
		}
		Checks.assertTrue(helper, problems.isEmpty(), "every world house: " + problems);
		helper.succeed();
	}

	/**
	 * Over every bed of the villagers' plans two blocks are free (and no world house's level lays a floor two over the
	 * beds the house had): one standing on a bed (half a block up) under a floor two up had its head in it and choked
	 * (live in the dev world; Infants keeps one on a bed from choking all the same, under a vanilla house's slab roof).
	 */
	@GameTest(batch = "world_houses", template = "home_platform")
	public static void nobodyChokesStandingOnABed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> low = new ArrayList<>();
		for (String house : HouseLevels.WORLD_HOUSES) {
		  SchematicSpec copy = spec(level, HouseLevels.WORLD + house);
		  for (String plan = HouseLevels.next(level, HouseLevels.WORLD + house); plan != null; plan = HouseLevels.next(level, plan)) {
			SchematicSpec spec = spec(level, plan);
			// the template's own beds under its own ceiling (a desert house's slab roof) are as the world made them
			BlockPos shift = HouseLevels.offset(copy, spec, HouseLevels.floorOf(copy));
			java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = new java.util.HashMap<>();
			for (SchematicSpec.PlacedBlock block : copy.blocks) before.put(block.rel().offset(shift), block.state());
			java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> cells = new java.util.HashMap<>();
			for (SchematicSpec.PlacedBlock block : spec.blocks) cells.put(block.rel(), block.state());
			for (SchematicSpec.PlacedBlock block : spec.blocks) {
				if (!(block.state().getBlock() instanceof BedBlock)) continue;
				for (int up = 1; up <= 2; up++) {
					var over = cells.get(block.rel().above(up));
					var old = before.get(block.rel().above(up));
					if (old != null && over != null && old.getBlock().equals(over.getBlock()) && before.get(block.rel()) != null) continue;
					if (over != null && !over.getCollisionShape(level, BlockPos.ZERO).isEmpty()) {
						low.add(plan + " " + block.rel().toShortString() + "+" + up + "=" + over.getBlock());
						break;
					}
				}
			}
		  }
		}
		// the villagers' own plans, every level, the halls and the shop house with their furnishing: no bed with less
		List<String> own = new ArrayList<>();
		for (String plan : HomeProjects.PLANS) for (String one = plan; one != null; one = HouseLevels.next(level, one)) own.add(one);
		for (String hall : Mayors.HALLS) own.addAll(List.of(hall, hall + "_" + Mayors.OFFICE, hall + "_" + Mayors.HOME));
		own.addAll(com.vnap.village.market.Shops.STALLS);
		for (String plan : own) {
			java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> cells = new java.util.HashMap<>();
			for (SchematicSpec.PlacedBlock block : spec(level, plan).blocks) cells.put(block.rel(), block.state());
			cells.forEach((rel, state) -> {
				if (!(state.getBlock() instanceof BedBlock)) return;
				for (int up = 1; up <= 2; up++) {
					var over = cells.get(rel.above(up));
					// a hanging lantern is knocked by a head, not choked in
					if (over != null && !over.getCollisionShape(level, BlockPos.ZERO).isEmpty() && !(over.getBlock() instanceof net.minecraft.world.level.block.LanternBlock)) {
						low.add(plan + " " + rel.toShortString() + "+" + up + "=" + over.getBlock());
						return;
					}
				}
			});
		}
		Checks.assertTrue(helper, low.isEmpty(), "two free over every bed: " + low);
		helper.succeed();
	}

	/** What a storey or a cellar takes is all to be had at the stall (a level wanting grass the stall never sells waited for good). */
	@GameTest(batch = "world_houses", template = "home_platform")
	public static void everyWorldLevelIsBuiltOfWhatTheStallSells(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		java.util.Set<String> unsold = new java.util.TreeSet<>();
		for (String house : HouseLevels.WORLD_HOUSES) {
			for (String one = HouseLevels.next(level, HouseLevels.WORLD + house); one != null; one = HouseLevels.next(level, one)) {
				String plan = one;
				com.vnap.village.market.Materials.bill(spec(level, plan)).keySet().forEach(item -> {
					if (com.vnap.village.market.Prices.price(new net.minecraft.world.item.ItemStack(item)) <= 0) unsold.add(plan + ":" + item);
				});
			}
		}
		Checks.assertTrue(helper, unsold.isEmpty(), "the stall sells it all: " + unsold);
		helper.succeed();
	}

	/**
	 * Each vanilla template set down (turned every way), taken as its family's house and grown a storey and then a
	 * cellar where it stands: the copy matches the world's house block for block, the old beds stay and every bed upstairs
	 * is reached on foot from the ground outside.
	 */
	@GameTest(batch = "world_houses", template = "home_platform", timeoutTicks = 400)
	public static void aWorldHouseGrowsWhereItStands(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 100; x++) for (int z = 0; z < 100; z++) for (int y = 1; y <= 6; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
		helper.setBlock(new BlockPos(50, 7, 50), Blocks.BELL);
		BlockPos bell = helper.absolutePos(new BlockPos(50, 7, 50));
		long day = com.vnap.dialogue.Family.day(level);
		List<String> problems = new ArrayList<>();
		List<String> houses = HouseLevels.WORLD_HOUSES;
		for (int i = 0; i < houses.size(); i++) {
			String name = houses.get(i);
			Rotation rotation = TURNS.get(i % TURNS.size());
			ResourceLocation id = ResourceLocation.withDefaultNamespace("village/" + name.substring(0, name.indexOf('_')) + "/houses/" + name);
			StructureTemplate template = com.vnap.Mc.templates(level).getOrCreate(id);
			StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation)
					.addProcessor(JigsawReplacementProcessor.INSTANCE).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
			SchematicSpec copy = spec(level, HouseLevels.WORLD + name);
			// each in a slot of its own whichever way it is turned (a level may grow two out), its own ground (a desert
			// house's sand round it) level with the earth's top
			BlockPos slot = helper.absolutePos(new BlockPos(3 + i % 7 * 13, 6 - ground(level, copy), 3 + i / 7 * 13));
			BoundingBox turned = template.getBoundingBox(settings, slot);
			BlockPos origin = slot.offset(slot.getX() - turned.minX(), 0, slot.getZ() - turned.minZ());
			template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
			BoundingBox box = template.getBoundingBox(settings, origin);
			List<String> wrong = mismatches(level, copy, origin, rotation);
			if (!wrong.isEmpty()) problems.add(name + " is not its template: " + wrong);

			BlockPos bed = BuildJob.at(origin, rotation, java.util.Objects.requireNonNull(HouseProject.ownerBed(copy)));
			Villager head = com.vnap.Mc.create(EntityType.VILLAGER, level);
			if (head == null) throw new IllegalStateException("no villager");
			com.vnap.Mc.snap(head, net.minecraft.world.phys.Vec3.atBottomCenterOf(bed.above()));
			head.setNoAi(true);
			level.addFreshEntity(head);
			head.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
			((VillagerLifeData) head).vnap$setHomeBed(bed);
			HouseProject house = HouseLevels.adopt(level, head, new HomeProjects.WorldPiece(box, name, origin, rotation), day);
			if (house == null || !house.footprint(level).equals(box)) {
				problems.add(name + ": not taken as a house, " + (house == null ? "no plan" : house.footprint(level) + " vs " + box));
				continue;
			}
			int before = HouseLevels.beds(level, house);
			for (int step = 2; step <= 3; step++) {
				HouseProject grown = HouseLevels.upgrade(level, head, house, day);
				if (grown == null) {
					problems.add(name + ": no level " + step + " (" + HouseLevels.refusal + ")");
					break;
				}
				// both by hand: the storey set block by block, the cellar dug out from the floor over it
				problems.addAll(byHand(level, grown, bed));
				List<String> left = mismatches(level, grown.schematic(level), grown.origin, grown.rotation);
				if (!left.isEmpty()) problems.add(grown.plan + " stands broken: " + left);
				if (!level.getBlockState(bed).is(net.minecraft.tags.BlockTags.BEDS)) problems.add(grown.plan + " lost the owner's bed");
				int beds = HouseLevels.beds(level, grown);
				if (beds < before + 1) problems.add(grown.plan + ": " + beds + " beds, was " + before);
				if (step == 2) problems.addAll(unreached(level, grown, bed));
				VillageMoodSavedData.get(level).houses().remove(grown);
				grown.done = true;
				house = grown;
			}
			head.discard();
		}
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
		if (office != null) Mayors.get(level.getServer()).offices().remove(office);
		Checks.assertTrue(helper, problems.isEmpty(), "every world house grows: " + problems);
		helper.succeed();
	}

	/** The top of the template's own ground (a desert house's sand round it), -1 when it has none: the earth's top is under its floor. */
	private static int ground(ServerLevel level, SchematicSpec copy) {
		int ground = -1;
		for (SchematicSpec.PlacedBlock block : copy.blocks) {
			if (BuildJob.naturalGround(block.state()) && block.state().isCollisionShapeFullBlock(level, BlockPos.ZERO)
					&& block.rel().getY() <= HouseLevels.floorOf(copy)) ground = Math.max(ground, block.rel().getY());
		}
		return ground;
	}

	/**
	 * The old roof taken down, then the storey set block by block from where a builder can stand and reach (Hands), as
	 * on a real site: nothing out of reach, nobody standing in a block.
	 */
	private static List<String> byHand(ServerLevel level, HouseProject house, BlockPos ownBed) {
		while (!house.clearing.isEmpty()) HomeProjects.clearOne(level, house, house.clearing.get(0));
		BuildJob job = house.job(level);
		job.site = null;
		BlockPos start = outside(level, house.footprint(level), ownBed);
		Villager builder = com.vnap.Mc.create(EntityType.VILLAGER, level);
		if (builder == null || start == null) return List.of(house.plan + ": nowhere outside to start from");
		com.vnap.Mc.snap(builder, net.minecraft.world.phys.Vec3.atBottomCenterOf(start));
		builder.setNoAi(true);
		level.addFreshEntity(builder);
		HomeGameTests.Site site = new HomeGameTests.Site(house.plan, house.schematic(level), house.origin, job, builder);
		for (int i = 0; i < 400 && site.problems.isEmpty() && !job.done(); i++) site.steps(level, 12);
		builder.discard();
		return site.problems.isEmpty() ? List.of() : List.of(house.plan + " by hand: " + site.problems);
	}

	private static @org.jspecify.annotations.Nullable BlockPos outside(ServerLevel level, BoundingBox box, BlockPos ownBed) {
		for (BlockPos at : BlockPos.betweenClosed(box.minX() - 2, ownBed.getY() - 3, box.minZ() - 2, box.maxX() + 2, ownBed.getY() + 1, box.maxZ() + 2)) {
			if (box.inflatedBy(1).isInside(at)) continue;
			if (level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)
					&& level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) return at.immutable();
		}
		return null;
	}

	/** The beds upstairs, each walked to from the ground outside the house (a villager climbs no ladder). */
	private static List<String> unreached(ServerLevel level, HouseProject house, BlockPos ownBed) {
		List<String> out = new ArrayList<>();
		BoundingBox box = house.footprint(level);
		BlockPos start = null;
		// a cell of open ground two out from the house
		for (BlockPos at : BlockPos.betweenClosed(box.minX() - 2, ownBed.getY() - 3, box.minZ() - 2, box.maxX() + 2, ownBed.getY() + 1, box.maxZ() + 2)) {
			if (box.inflatedBy(1).isInside(at)) continue;
			if (level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)
					&& level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
				start = at.immutable();
				break;
			}
		}
		if (start == null) return List.of(house.plan + ": no ground outside");
		Villager walker = com.vnap.Mc.create(EntityType.VILLAGER, level);
		if (walker == null) throw new IllegalStateException("no villager");
		com.vnap.Mc.snap(walker, net.minecraft.world.phys.Vec3.atBottomCenterOf(start));
		level.addFreshEntity(walker);
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), ownBed.getY() + 2, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			var state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) continue;
			walker.setOnGround(true);
			// a villager lies down from beside its bed, a step off (SleepInBed)
			Path path = walker.getNavigation().createPath(pos.immutable(), 2);
			net.minecraft.world.level.pathfinder.Node end = path == null ? null : path.getEndNode();
			// SleepInBed: within two blocks of the bed's middle
			boolean near = end != null && net.minecraft.world.phys.Vec3.atCenterOf(end.asBlockPos()).distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 2.0;
			if (path == null || (!path.canReach() && !near)) {
				walker.setOnGround(true);
				Path down = walker.getNavigation().createPath(ownBed, 2);
				StringBuilder doors = new StringBuilder();
				for (BlockPos d : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
					var st = level.getBlockState(d);
					if (!(st.getBlock() instanceof DoorBlock) || st.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
					doors.append(d.subtract(house.origin).toShortString()).append(' ').append(st.getValue(DoorBlock.FACING));
					for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
						doors.append(' ').append(dir.getName().charAt(0)).append('=').append(net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(walker, d.relative(dir)));
					}
					doors.append(" self=").append(net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(walker, d.immutable())).append(" | ");
				}
				for (BlockPos st : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
					if (level.getBlockState(st).getBlock() instanceof net.minecraft.world.level.block.StairBlock && level.getBlockState(st.above()).isAir()
							&& level.getBlockState(st).getValue(net.minecraft.world.level.block.StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM
							&& st.getY() < ownBed.getY() + 5) {
						walker.setOnGround(true);
						Path up = walker.getNavigation().createPath(st.above().immutable(), 0);
						doors.append(up == null ? "null" : up.canReach() + "/" + (up.getEndNode() == null ? "-" : up.getEndNode().asBlockPos().subtract(house.origin).toShortString()));
						doors.append("step ").append(st.subtract(house.origin).toShortString()).append(level.getBlockState(st).getValue(net.minecraft.world.level.block.StairBlock.FACING).getName().charAt(0))
								.append('=').append(net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(walker, st.above())).append("; ");
					}
				}
				out.add(doors + house.plan + ": the bed upstairs at " + pos.subtract(house.origin).toShortString() + " is not reached ("
						+ (path == null ? "no path" : "ends " + (end == null ? "-" : end.asBlockPos().subtract(house.origin).toShortString())) + ") from " + start.subtract(house.origin).toShortString()
						+ " ground=" + walker.onGround() + " own bed " + (down == null ? "no path" : down.canReach() + " " + nodes(down, house.origin)) + ")");
			}
		}
		walker.discard();
		return out;
	}

	private static String nodes(Path path, BlockPos origin) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < path.getNodeCount(); i++) out.append(path.getNodePos(i).subtract(origin).toShortString()).append(';');
		return out.toString();
	}

	static void build(ServerLevel level, HouseProject house) {
		while (!house.clearing.isEmpty()) HomeProjects.clearOne(level, house, house.clearing.get(0));
		BuildJob job = house.job(level);
		job.site = null;
		while (!job.done()) {
			job.skipDone(level);
			if (!job.done()) job.placeNext(level, null);
		}
	}

	static List<String> mismatches(ServerLevel level, SchematicSpec spec, BlockPos origin, Rotation rotation) {
		List<String> wrong = new ArrayList<>();
		for (SchematicSpec.PlacedBlock block : spec.blocks) {
			BlockPos pos = BuildJob.at(origin, rotation, block.rel());
			if (block.state().isAir() ? !level.getBlockState(pos).isAir() && !(level.getBlockState(pos).getBlock() instanceof LadderBlock)
					: !level.getBlockState(pos).is(block.state().getBlock())) {
				wrong.add(block.rel().toShortString() + "=" + level.getBlockState(pos).getBlock());
				if (wrong.size() > 8) break;
			}
		}
		return wrong;
	}

	private static int beds(SchematicSpec spec) {
		int n = 0;
		for (SchematicSpec.PlacedBlock block : spec.blocks) {
			if (block.state().getBlock() instanceof BedBlock && block.state().getValue(BedBlock.PART) == BedPart.HEAD) n++;
		}
		return n;
	}

	private static @org.jspecify.annotations.Nullable BlockPos lowestDoor(SchematicSpec spec) {
		BlockPos best = null;
		for (SchematicSpec.PlacedBlock block : spec.blocks) {
			if (block.state().getBlock() instanceof DoorBlock && block.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
					&& (best == null || block.rel().getY() < best.getY())) best = block.rel();
		}
		return best;
	}
}
