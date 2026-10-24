package com.vnap.command;

import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HouseLevels;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.HouseProject;
import com.vnap.village.life.Hands;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.Workshops;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Building, looked at from chat: {@code /vnap worldhouse <template|all> [l1|l2|l3] [cut]} (a vanilla house and the
 * mod's level of it side by side on a cleared pad, cut in half to see in), {@code /vnap site} (the nearest build: how far,
 * what is next, money, who works on it and whether its owner finds a spot by the next block) and
 * {@code /vnap worldhouses [r]} (the generated village's houses round the player: template, plan, who lives there).
 */
final class BuildCommands {
	/** One pad: the vanilla house at its corner, the level beside it. */
	private static final int PAD = 30;
	private static final int ROW = 6;

	private BuildCommands() {
	}

	static int worldHouse(CommandSourceStack source, String which, String levelName, boolean cut) {
		ServerLevel level = source.getLevel();
		List<String> houses = which.equals("all") ? HouseLevels.WORLD_HOUSES : List.of(which);
		if (!HouseLevels.WORLD_HOUSES.containsAll(houses)) {
			source.sendFailure(Component.literal("No plan for " + which + "; templates: " + HouseLevels.WORLD_HOUSES));
			return 0;
		}
		String suffix = levelName.equals("l1") ? "" : "_" + levelName;
		BlockPos corner = BlockPos.containing(source.getPosition()).offset(4, 0, 4);
		for (int i = 0; i < houses.size(); i++) {
			String name = houses.get(i);
			BlockPos base = corner.offset(i % ROW * PAD, 0, i / ROW * PAD);
			pad(level, base);
			StructureTemplate template = com.vnap.Mc.templates(level).getOrCreate(
					ResourceLocation.withDefaultNamespace("village/" + name.substring(0, name.indexOf('_')) + "/houses/" + name));
			StructurePlaceSettings settings = new StructurePlaceSettings().addProcessor(JigsawReplacementProcessor.INSTANCE)
					.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
			template.placeInWorld(level, base, base, settings, level.getRandom(), Block.UPDATE_CLIENTS);
			SchematicSpec copy = Workshops.schematic(level, HousePalette.schematicName(HouseLevels.WORLD + name, HousePalette.PLAINS));
			SchematicSpec spec = Workshops.schematic(level, HousePalette.schematicName(HouseLevels.WORLD + name + suffix, HousePalette.PLAINS));
			// the level laid as it grows over the copy: its floor on the copy's floor (a cellar under the pad, the flight beside)
			BlockPos shift = suffix.isEmpty() ? BlockPos.ZERO : HouseLevels.offset(copy, spec, HouseLevels.floorOf(copy));
			BlockPos origin = base.offset(14, 0, 0).subtract(shift);
			BuildJob job = BuildJob.all(name + suffix, spec, origin, Rotation.NONE, HouseLevels.floorOf(spec));
			job.completeNow(level);
			if (cut) {
				// the south half off both: the rooms, the stair and the cellar seen from there
				int half = base.getZ() + Math.max(template.getSize().getZ(), spec.sizeZ) / 2 + 1;
				for (BlockPos pos : BlockPos.betweenClosed(base.getX() - 2, base.getY() - 8, half, base.getX() + 26, base.getY() + 24, base.getZ() + 16)) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
				}
			}
		}
		int count = houses.size();
		source.sendSuccess(() -> Component.literal(count + " world house(s) from " + corner.toShortString() + ", vanilla at the pad's corner, "
				+ levelName + " 14 east of it" + (count > 1 ? ", " + PAD + " apart, " + ROW + " a row" : "")), false);
		return count;
	}

	/** A flat pad: grass on six of earth (a cellar is dug into it), air over it. */
	private static void pad(ServerLevel level, BlockPos base) {
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
		for (BlockPos pos : BlockPos.betweenClosed(base.getX() - 2, base.getY() - 7, base.getZ() - 4, base.getX() + 26, base.getY() + 24, base.getZ() + 16)) {
			int dy = pos.getY() - base.getY();
			level.setBlock(pos, (dy >= 0 ? Blocks.AIR : dy == -1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), flags);
		}
	}

	static int site(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos at = BlockPos.containing(source.getPosition());
		HouseProject near = null;
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if ((house.done && !house.mending) || !house.origin.closerThan(at, 96.0)) continue;
			if (near == null || house.origin.distSqr(at) < near.origin.distSqr(at)) near = house;
		}
		if (near == null) {
			source.sendFailure(Component.literal("No build going within 96 blocks"));
			return 0;
		}
		HouseProject house = near;
		BuildJob job = house.job(level);
		List<String> lines = new ArrayList<>();
		lines.add(house.schematicName() + " @" + house.origin.toShortString() + " " + house.rotation + (house.mending ? " mending" : " building")
				+ ", " + house.clearing.size() + " to take down, " + job.remaining() + " to set"
				+ (house.pausedDay >= 0 ? ", paused day " + house.pausedDay + " (today " + com.vnap.dialogue.Family.day(level) + ")" : ""));
		if (!job.done()) {
			BlockPos next = house.clearing.isEmpty() ? job.nextPos() : house.clearing.get(0);
			lines.add("next " + next.subtract(house.origin).toShortString() + " = " + (house.clearing.isEmpty() ? job.nextState().getBlock().getName().getString()
					: "take down " + level.getBlockState(next).getBlock().getName().getString())
					+ (job.lacksMaterials() ? ", lacks " + com.vnap.village.market.Materials.items(job.nextState()) : ""));
		}
		lines.add("funds " + house.site.funds + "c, wages " + house.site.wages + "c, " + house.site.stock.values().stream().mapToInt(Integer::intValue).sum()
				+ " goods in store, " + house.stash.size() + " things kept");
		Villager owner = level.getEntity(house.owner) instanceof Villager v && v.isAlive() ? v : null;
		if (owner == null) {
			lines.add("owner not loaded or gone (helpers and the widow carry on)");
		} else {
			StringBuilder running = new StringBuilder();
			com.vnap.village.life.LifeBrain.running(owner).forEach(b -> running.append(b.getClass().getSimpleName()).append(' '));
			lines.add("owner " + VillagerHome.nameOf(owner) + " " + (int) Math.sqrt(owner.blockPosition().distSqr(house.origin)) + " off, "
					+ owner.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("-") + ", running " + running.toString().trim());
			if (!job.done() && house.clearing.isEmpty()) {
				BlockPos spot = Hands.findSpot(level, owner, job, job.nextPos());
				lines.add(spot == null ? "no spot by the next block from the owner: it walks over and sets it from below"
						: "a spot by the next block: " + spot.subtract(house.origin).toShortString());
			}
		}
		List<String> workers = new ArrayList<>();
		for (Villager villager : level.getEntitiesOfClass(Villager.class, new AABB(house.origin).inflate(48.0))) {
			for (var behavior : com.vnap.village.life.LifeBrain.running(villager)) {
				if (behavior.getClass().getSimpleName().equals("HouseBehavior")) workers.add(VillagerHome.nameOf(villager));
			}
		}
		lines.add("at work on houses round it: " + workers);
		lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
		return 1;
	}

	static int worldHouses(CommandSourceStack source, int radius) {
		ServerLevel level = source.getLevel();
		BlockPos at = BlockPos.containing(source.getPosition());
		// anywhere in the village's whole box, not only inside one of its pieces
		StructureStart start = StructureStart.INVALID_START;
		var structures = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
		for (var structure : level.structureManager().getAllStructuresAt(at).keySet()) {
			if (structures.wrapAsHolder(structure).is(net.minecraft.tags.StructureTags.VILLAGE)) start = level.structureManager().getStructureAt(at, structure);
		}
		if (!start.isValid()) {
			source.sendFailure(Component.literal("Not in a generated village"));
			return 0;
		}
		int count = 0;
		for (StructurePiece piece : start.getPieces()) {
			if (!(piece instanceof PoolElementStructurePiece pool) || !pool.getElement().toString().contains("/houses/")) continue;
			BoundingBox box = piece.getBoundingBox();
			if (!box.inflatedBy(radius).isInside(at)) continue;
			String element = pool.getElement().toString();
			int end = element.indexOf(']');
			String template = end < 0 ? element : element.substring(element.lastIndexOf('/', end) + 1, end);
			List<String> living = new ArrayList<>();
			for (Villager villager : level.getEntitiesOfClass(Villager.class, AABB.of(box).inflate(48.0))) {
				BlockPos bed = villager instanceof VillagerLifeData data ? data.vnap$homeBed() : null;
				if (bed != null && box.isInside(bed)) living.add(VillagerHome.nameOf(villager) + (com.vnap.dialogue.Family.isHead(villager) ? "*" : ""));
			}
			String project = VillageMoodSavedData.get(level).houses().stream().filter(house -> house.footprint(level).intersects(box))
					.map(house -> house.plan + (house.done ? "" : " (going up)")).findFirst().orElse("-");
			String line = template + " @" + pool.getPosition().toShortString() + " " + pool.getRotation()
					+ (HouseLevels.WORLD_HOUSES.contains(template) ? " [grows]" : " [no plan]") + " lives: " + living + " build: " + project;
			source.sendSuccess(() -> Component.literal(line), false);
			count++;
		}
		int found = count;
		source.sendSuccess(() -> Component.literal(found + " houses within " + radius + " (* a head: /vnap family room [hand] next to it)"), false);
		return count;
	}
}
