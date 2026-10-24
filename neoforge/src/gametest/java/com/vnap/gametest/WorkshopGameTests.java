package com.vnap.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.VillagerLife;
import com.vnap.block.DecorBlock;
import com.vnap.block.LifeBlocks;
import com.vnap.entity.VillagerLifeData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.life.Chores;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.ProfessionWork;
import com.vnap.village.workshop.WorkshopState;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class WorkshopGameTests {
	private static final Map<String, Block> JOB_BLOCK = Map.ofEntries(
			Map.entry("armorer", Blocks.BLAST_FURNACE),
			Map.entry("butcher", Blocks.SMOKER),
			Map.entry("cartographer", Blocks.CARTOGRAPHY_TABLE),
			Map.entry("cleric", Blocks.BREWING_STAND),
			Map.entry("farmer", Blocks.COMPOSTER),
			Map.entry("fisherman", Blocks.BARREL),
			Map.entry("fletcher", Blocks.FLETCHING_TABLE),
			Map.entry("leatherworker", Blocks.CAULDRON),
			Map.entry("librarian", Blocks.LECTERN),
			Map.entry("mason", Blocks.STONECUTTER),
			Map.entry("shepherd", Blocks.LOOM),
			Map.entry("toolsmith", Blocks.SMITHING_TABLE),
			Map.entry("weaponsmith", Blocks.GRINDSTONE));

	@GameTest(template = "workshop_platform")
	public static void everyWorkshopStandsWhole(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> broken = new ArrayList<>();
		for (int i = 0; i < Workshops.PROFESSIONS.size(); i++) {
			String profession = Workshops.PROFESSIONS.get(i);
			BlockPos site = helper.absolutePos(slot(i));
			BlockPos origin = site.subtract(Workshops.layout(profession).focal());
			String name = Workshops.schematicName(profession, 3);
			SchematicSpec spec = Workshops.schematic(level, name);
			buildAll(level, name, spec, origin, Rotation.NONE);
			level.setBlock(site, jobBlock(profession).defaultBlockState(), 3);
			for (SchematicSpec.PlacedBlock block : spec.blocks) {
				BlockPos pos = BuildJob.at(origin, Rotation.NONE, block.rel());
				BlockState found = level.getBlockState(pos);
				boolean halfMatches = !block.state().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
						|| (found.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
						&& found.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == block.state().getValue(BlockStateProperties.DOUBLE_BLOCK_HALF));
				if (!found.is(block.state().getBlock()) || !halfMatches) {
					broken.add(name + " " + block.rel().toShortString() + " wants " + block.state() + " has " + found);
				}
			}
			Checks.assertTrue(helper, level.getBlockState(site).is(jobBlock(profession)), name + ": the focal cell stays the job block");
		}
		Checks.assertTrue(helper, broken.isEmpty(), "every block of every workshop stands: " + broken);
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void tiersGrowWithoutTearingDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		for (String profession : Workshops.PROFESSIONS) {
			for (int tier = 1; tier <= 3; tier++) {
				String name = Workshops.schematicName(profession, tier);
				Map<BlockPos, Block> blocks = blocks(Workshops.schematic(level, name));
				Workshops.Layout layout = Workshops.layout(profession);
				if (!Blocks.CHEST.equals(blocks.get(layout.chest()))) {
					problems.add(name + " has no chest at " + layout.chest().toShortString());
				}
				if (blocks.containsKey(layout.focal())) {
					problems.add(name + " builds over the job block");
				}
				for (Map.Entry<BlockPos, Block> entry : blocks.entrySet()) {
					BlockPos rel = entry.getKey();
					if (rel.getX() < 0 || rel.getY() < 0 || rel.getZ() < 0
							|| rel.getX() >= layout.size().getX() || rel.getY() >= layout.size().getY() || rel.getZ() >= layout.size().getZ()) {
						problems.add(name + " leaves its footprint at " + rel.toShortString());
					}
					if (PoiTypes.forState(entry.getValue().defaultBlockState()).isPresent()) {
						problems.add(name + " places a point of interest " + entry.getValue());
					}
				}
				if (tier < 3) {
					Map<BlockPos, Block> next = blocks(Workshops.schematic(level, Workshops.schematicName(profession, tier + 1)));
					for (Map.Entry<BlockPos, Block> entry : blocks.entrySet()) {
						if (!entry.getValue().equals(next.get(entry.getKey()))) {
							problems.add(name + " block " + entry.getKey().toShortString() + " changes in the next tier");
						}
					}
				}
			}
		}
		Checks.assertTrue(helper, problems.isEmpty(), "tiers only add blocks: " + problems);
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void planFitsTheBiggestTierAndKeepsNeighboursApart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager open = worker(helper, slot(0), "armorer", 1);
		WorkshopState free = Workshops.plan(level, open, "armorer", site(helper, slot(0)));
		Checks.assertTrue(helper, free.maxTier == 3, "an open field fits the third tier, was " + free.maxTier);
		Checks.assertTrue(helper, free.rotation == Rotation.NONE, "an open field keeps the door opposite the job block, was " + free.rotation);
		((VillagerLifeData) open).vnap$setWorkshop(free);

		BlockPos tight = slot(2);
		BlockPos tightSite = site(helper, tight);
		java.util.Set<BlockPos> room = new java.util.HashSet<>();
		for (SchematicSpec.PlacedBlock block : Workshops.schematic(level, Workshops.schematicName("mason", 1)).blocks) {
			room.add(BuildJob.at(tightSite.subtract(Workshops.FOCAL), Rotation.NONE, block.rel()));
		}
		for (BlockPos pos : BlockPos.betweenClosed(tightSite.offset(-4, 0, -4), tightSite.offset(4, 0, 4))) {
			if (!room.contains(pos) && !pos.equals(tightSite)) level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 3);
		}
		Villager boxed = worker(helper, tight, "mason", 1);
		WorkshopState small = Workshops.plan(level, boxed, "mason", site(helper, tight));
		Checks.assertTrue(helper, small.maxTier == 1, "a room cut to the first tier only fits the first tier, was " + small.maxTier);

		BlockPos close = slot(0).offset(5, 0, 0);
		Villager neighbour = worker(helper, close, "fletcher", 1);
		WorkshopState beside = Workshops.plan(level, neighbour, "fletcher", site(helper, close));
		BoundingBox mine = Workshops.footprint(free);
		Checks.assertTrue(helper, beside.maxTier == 0 || !Workshops.footprint(beside).intersects(mine),
				"a neighbour never builds into another workshop");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void neighboursInARowKeepTheirDoorsOpposite(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String[] professions = {"mason", "librarian", "armorer", "cleric"};
		List<String> turned = new ArrayList<>();
		for (int i = 0; i < professions.length; i++) {
			BlockPos rel = new BlockPos(14 + i * 12, 2, 40);
			Villager villager = worker(helper, rel, professions[i], 5);
			WorkshopState state = Workshops.plan(level, villager, professions[i], site(helper, rel));
			((VillagerLifeData) villager).vnap$setWorkshop(state);
			if (state.rotation != Rotation.NONE || state.maxTier != 3) turned.add(professions[i] + " " + state.rotation + " t" + state.maxTier);
		}
		Checks.assertTrue(helper, turned.isEmpty(), "workshops twelve blocks apart all face the same way: " + turned);
		helper.succeed();
	}

	@GameTest(batch = "life_day", template = "workshop_platform")
	public static void jobsStartDailyThenUpgradeAndRepair(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.Mc.setDayTime(level, 3000L);
		BlockPos rel = slot(3);
		Villager villager = worker(helper, rel, "mason", 1);
		VillagerLifeData data = (VillagerLifeData) villager;
		Workshops.prepare(level, villager, 0L);
		WorkshopState state = data.vnap$workshop();
		Checks.assertTrue(helper, state != null && state.maxTier == 3 && state.job == null, "the first look only plans");
		if (state == null) return;

		Workshops.prepare(level, villager, 1L);
		BuildJob first = state.job;
		Checks.assertTrue(helper, first != null && state.jobTier == 1, "the next work day starts the first tier");
		if (first == null) return;
		Workshops.prepare(level, villager, 1L);
		Checks.assertTrue(helper, first.cursor() == 0, "preparing never places a block");
		finish(level, villager, state);
		Checks.assertTrue(helper, state.tier == 1, "the first tier is done");
		Checks.assertTrue(helper, standsWhole(level, state, 1), "every first tier block stands");

		Workshops.prepare(level, villager, 2L);
		Checks.assertTrue(helper, state.job == null, "a first level villager stops at the first tier");
		villager.setVillagerData(com.vnap.Mc.withLevel(villager.getVillagerData(), 3));
		Workshops.prepare(level, villager, 3L);
		BuildJob upgrade = state.job;
		int delta = delta(level, "mason", 1, 2);
		Checks.assertTrue(helper, upgrade != null && state.jobTier == 2 && upgrade.size() == delta,
				"the upgrade only builds the " + delta + " new blocks, was " + (upgrade == null ? -1 : upgrade.size()));
		finish(level, villager, state);
		Checks.assertTrue(helper, state.tier == 2 && standsWhole(level, state, 2), "the second tier stands whole");

		BlockPos chest = Workshops.chestPos(state);
		level.removeBlock(chest, false);
		Workshops.prepare(level, villager, 4L);
		BuildJob repair = state.job;
		Checks.assertTrue(helper, repair != null && repair.size() == 1 && state.jobTier == 2, "a broken chest is the whole repair");
		finish(level, villager, state);
		Checks.assertTrue(helper, level.getBlockState(chest).is(Blocks.CHEST), "the chest is back");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void everyProfessionHasAToolAndChores(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (String profession : Workshops.PROFESSIONS) {
			if (Items.AIR.equals(ProfessionWork.tool(profession))) missing.add(profession + " has no tool");
			if (!Chores.professionHasChores(profession)) missing.add(profession + " has no chores");
		}
		Checks.assertTrue(helper, missing.isEmpty(), "every profession works: " + missing);
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void decorIsTradedDroppedAndCrafted(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager villager = worker(helper, slot(0), "farmer", 1);
		TagKey<Block> tag = TagKey.create(Registries.BLOCK,
				ResourceLocation.fromNamespaceAndPath(VillagerLife.MOD_ID, "profession_decor"));
		List<String> problems = new ArrayList<>();
		Map<String, Integer> perProfession = new HashMap<>();
		Checks.assertTrue(helper, !LifeBlocks.DECOR.isEmpty(), "decor blocks are registered");
		for (DecorBlock decor : LifeBlocks.DECOR) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(decor);
			Item item = decor.asItem();
			perProfession.merge(decor.profession(), 1, Integer::sum);
			if (Items.AIR.equals(item)) problems.add(id + " has no item");
			if (!decor.defaultBlockState().is(tag)) problems.add(id + " is not tagged");
			if (level.getServer().reloadableRegistries().getLootTable(com.vnap.Mc.lootTable(decor)).equals(LootTable.EMPTY)) {
				problems.add(id + " drops nothing");
			}
			if (!com.vnap.Mc.hasRecipe(level, id)) problems.add(id + " has no recipe");
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				BlockState state = decor.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
				if (state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).isEmpty()) {
					problems.add(id + " has no shape facing " + facing);
				}
			}
			boolean sold = com.vnap.Mc.sells(villager, decor.profession(), 3, item);
			if (!sold) problems.add(id + " is not sold by a journeyman " + decor.profession());
		}
		for (String profession : Workshops.PROFESSIONS) {
			if (perProfession.getOrDefault(profession, 0) < 2) problems.add(profession + " has fewer than two decor blocks");
		}
		Checks.assertTrue(helper, problems.isEmpty(), "decor is complete: " + problems);
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void everyItemHasModelTextureAndName(GameTestHelper helper) {
		JsonObject en = json("/assets/" + VillagerLife.MOD_ID + "/lang/en_us.json");
		JsonObject ru = json("/assets/" + VillagerLife.MOD_ID + "/lang/ru_ru.json");
		List<String> problems = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if (!owned(id)) continue;
			String key = item.getDescriptionId();
			if (!en.has(key)) problems.add(key + " has no English name");
			if (!ru.has(key)) problems.add(key + " has no Russian name");
			checkItem(id.getNamespace(), id.getPath(), problems);
		}
		for (net.minecraft.world.entity.EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (!owned(BuiltInRegistries.ENTITY_TYPE.getKey(type))) continue;
			String key = type.getDescriptionId();
			if (!en.has(key)) problems.add(key + " has no English name");
			if (!ru.has(key)) problems.add(key + " has no Russian name");
		}
		for (Block block : BuiltInRegistries.BLOCK) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			if (owned(id) && !exists("/assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json")) {
				problems.add(id + " has no blockstate");
			}
		}
		Checks.assertTrue(helper, problems.isEmpty(), "every item is complete: " + problems);
		helper.succeed();
	}

	private static Block jobBlock(String profession) {
		return JOB_BLOCK.getOrDefault(profession, Blocks.AIR);
	}

	private static BlockPos slot(int index) {
		// the farmer's farm is 17 by 11: four sites a row, far enough apart for it
		return new BlockPos(6 + index % 4 * 22, 2, 6 + index / 4 * 22);
	}

	private static BlockPos site(GameTestHelper helper, BlockPos rel) {
		return helper.absolutePos(rel);
	}

	private static Villager worker(GameTestHelper helper, BlockPos site, String profession, int villagerLevel) {
		helper.setBlock(site, jobBlock(profession).defaultBlockState());
		Villager villager = helper.spawn(EntityType.VILLAGER, site.getX(), site.getY(), site.getZ() - 3);
		villager.setNoAi(true);
		VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.withDefaultNamespace(profession));
		villager.setVillagerData(com.vnap.Mc.withLevel(com.vnap.Mc.withProfession(villager.getVillagerData(), job), villagerLevel));
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
				GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(site)));
		return villager;
	}

	private static void buildAll(ServerLevel level, String name, SchematicSpec spec, BlockPos origin, Rotation rotation) {
		BuildJob job = BuildJob.all(name, spec, origin, rotation, 0);
		while (!job.done()) {
			job.skipDone(level);
			job.placeNext(level, null);
		}
	}

	/** Builds the open job as the villager's hands would, block by block in job order. */
	private static void finish(ServerLevel level, Villager villager, WorkshopState state) {
		BuildJob job = state.job;
		if (job == null) return;
		for (int step = 0; step < 6000 && !job.done(); step++) {
			job.skipDone(level);
			if (!job.done()) job.placeNext(level, villager);
		}
		Workshops.finish(villager, state);
	}

	private static boolean standsWhole(ServerLevel level, WorkshopState state, int tier) {
		SchematicSpec spec = Workshops.schematic(level, Workshops.schematicName(state.profession, tier));
		for (SchematicSpec.PlacedBlock block : spec.blocks) {
			if (!BuildJob.stands(level, BuildJob.at(state.origin, state.rotation, block.rel()), block.state())) return false;
		}
		return true;
	}

	private static Map<BlockPos, Block> blocks(SchematicSpec spec) {
		Map<BlockPos, Block> blocks = new HashMap<>();
		for (SchematicSpec.PlacedBlock block : spec.blocks) {
			blocks.put(block.rel(), block.state().getBlock());
		}
		return blocks;
	}

	private static int delta(ServerLevel level, String profession, int from, int to) {
		Map<BlockPos, Block> have = blocks(Workshops.schematic(level, Workshops.schematicName(profession, from)));
		int count = 0;
		for (SchematicSpec.PlacedBlock block : Workshops.schematic(level, Workshops.schematicName(profession, to)).blocks) {
			if (!block.state().getBlock().equals(have.get(block.rel()))) count++;
		}
		return count;
	}

	private static boolean owned(ResourceLocation id) {
		return id.getNamespace().equals(VillagerLife.MOD_ID);
	}

	/** The item's model: from 1.21.4 every model its client item (items/<id>.json) picks. */
	private static void checkItem(String namespace, String path, List<String> problems) {
		//? if >=1.21.4 {
		/*String file = "/assets/" + namespace + "/items/" + path + ".json";
		if (!exists(file)) {
			problems.add(namespace + ":" + path + " has no client item");
			return;
		}
		List<String> models = new java.util.ArrayList<>();
		pickedModels(json(file), models);
		if (models.isEmpty()) problems.add(namespace + ":" + path + " picks no model");
		for (String model : models) {
			ResourceLocation id = ResourceLocation.parse(model);
			if (owned(id)) checkModel(id.getNamespace(), id.getPath(), problems);
		}
		*///?} else
		checkModel(namespace, "item/" + path, problems);
	}

	//? if >=1.21.4 {
	/*private static void pickedModels(JsonElement element, List<String> models) {
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) pickedModels(child, models);
		} else if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			if (object.has("type") && object.get("type").getAsString().equals("minecraft:model") && object.has("model")) {
				models.add(object.get("model").getAsString());
			}
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) pickedModels(entry.getValue(), models);
		}
	}
	*///?}

	private static void checkModel(String namespace, String path, List<String> problems) {
		String file = "/assets/" + namespace + "/models/" + path + ".json";
		if (!exists(file)) {
			problems.add(namespace + ":" + path + " has no model");
			return;
		}
		JsonObject model = json(file);
		if (model.has("parent")) {
			ResourceLocation parent = ResourceLocation.parse(model.get("parent").getAsString());
			if (owned(parent)) checkModel(parent.getNamespace(), parent.getPath(), problems);
		}
		if (model.has("textures")) {
			for (Map.Entry<String, JsonElement> texture : model.getAsJsonObject("textures").entrySet()) {
				String value = texture.getValue().getAsString();
				if (value.startsWith("#")) continue;
				ResourceLocation id = ResourceLocation.parse(value);
				// from 1.21.11 the build aliases vanilla item sprites as villager_life:block/item/* in the block atlas
				if (owned(id) && !id.getPath().startsWith("block/item/") && !exists("/assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png")) {
					problems.add(namespace + ":" + path + " misses texture " + id);
				}
			}
		}
	}

	private static boolean exists(String path) {
		try (InputStream in = LifeBlocks.class.getResourceAsStream(path)) {
			return in != null;
		} catch (IOException e) {
			return false;
		}
	}

	private static JsonObject json(String path) {
		try (InputStream in = LifeBlocks.class.getResourceAsStream(path)) {
			if (in == null) return new JsonObject();
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			return new JsonObject();
		}
	}
}
