package com.vnap.gametest;

import com.vnap.village.town.TownRules;
import com.vnap.entity.VillagerLifeData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.life.Chores;
import com.vnap.village.life.Herd;
import com.vnap.village.life.Life;
import com.vnap.village.life.Plots;
import com.vnap.village.workshop.ProfessionWork;
import com.vnap.village.workshop.WorkshopState;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every profession's chores done on the spot: what each one needs, what it leaves behind, and the limits —
 * quotas, the chest cap, the work radius, the bell and animals that belong to someone else.
 */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class LifeChoresGameTests {
	private static final long DAY = 1L;
	private static final BlockPos SITE = new BlockPos(50, 2, 50);
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

	private record Shop(Villager villager, WorkshopState state, Container chest) {
		@Nullable String perform(ServerLevel level) {
			return Chores.perform(level, villager, state, DAY);
		}

		/** Performs chores until {@code wanted} is done; the chores done on the way. */
		List<String> until(ServerLevel level, String wanted, int tries) {
			List<String> done = new ArrayList<>();
			for (int i = 0; i < tries; i++) {
				String chore = perform(level);
				if (chore == null) break;
				done.add(chore);
				if (chore.equals(wanted)) break;
			}
			return done;
		}
	}

	@GameTest(template = "workshop_platform")
	public static void farmerKeepsItsFarm(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "farmer");
		com.vnap.village.market.Farm.Plot farm = com.vnap.village.market.Farm.of(level, shop.state);
		if (farm == null) {
			Checks.fail(helper, "the farmer's workshop is a farm");
			return;
		}
		List<BlockPos> cells = new ArrayList<>(com.vnap.village.market.Farm.cropCells(farm).keySet());
		List<BlockPos> spots = com.vnap.village.market.Farm.treeSpots(farm);
		Checks.assertTrue(helper, cells.size() == 20 && spots.size() == 3, "a field of twenty cells and three trees");
		BlockPos ripe = cells.get(0);
		level.setBlock(ripe, ((CropBlock) Blocks.WHEAT).getStateForAge(7), 3);
		Checks.assertTrue(helper, "harvest".equals(shop.perform(level)), "a ripe crop is harvested first");
		Checks.assertTrue(helper, shop.chest.countItem(Items.WHEAT) > 0, "the wheat goes into the chest");
		Checks.assertTrue(helper, level.getBlockState(ripe).is(Blocks.WHEAT) && level.getBlockState(ripe).getValue(CropBlock.AGE) == 0,
				"the harvested cell is planted again");
		BlockPos bare = cells.get(1);
		level.removeBlock(bare, false);
		shop.chest.setItem(20, new ItemStack(Items.WHEAT_SEEDS, 2));
		Checks.assertTrue(helper, "plant".equals(shop.perform(level)) && level.getBlockState(bare).getBlock() instanceof CropBlock,
				"a bare cell of the field is sown from the chest");
		BlockPos spot = spots.get(0);
		for (int y = 0; y < 5; y++) level.setBlock(spot.above(y), Blocks.OAK_LOG.defaultBlockState(), 3);
		for (BlockPos leaf : BlockPos.betweenClosed(spot.offset(-2, 3, -2), spot.offset(2, 5, 2))) {
			if (level.getBlockState(leaf).isAir()) level.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState(), 3);
		}
		BlockPos neighbours = spot.offset(4, 1, 0);
		level.setBlock(neighbours, Blocks.OAK_LOG.defaultBlockState(), 3);
		Checks.assertTrue(helper, "fell_tree".equals(shop.perform(level)), "a grown tree is felled");
		Checks.assertTrue(helper, level.getBlockState(spot).isAir() && level.getBlockState(spot.offset(0, 4, 1)).isAir(), "whole, crown and all");
		Checks.assertTrue(helper, level.getBlockState(neighbours).is(Blocks.OAK_LOG), "nothing outside the tree's box is touched");
		Checks.assertTrue(helper, shop.chest.countItem(Items.OAK_LOG) == 5 && shop.chest.countItem(Items.OAK_SAPLING) >= 2, "logs and saplings in the chest");
		Checks.assertTrue(helper, "plant_tree".equals(shop.perform(level)) && level.getBlockState(spot).is(Blocks.OAK_SAPLING), "planted again");
		Checks.assertTrue(helper, "tend_tree".equals(shop.perform(level)), "a sapling is tended");
		BlockPos foreign = shop.state.anchor.offset(-6, 0, -6);
		level.setBlock(foreign.below(), Blocks.FARMLAND.defaultBlockState(), 3);
		level.setBlock(foreign, ((CropBlock) Blocks.WHEAT).getStateForAge(7), 3);
		for (int i = 0; i < 6; i++) shop.perform(level);
		Checks.assertTrue(helper, level.getBlockState(foreign).getValue(CropBlock.AGE) == 7, "a crop outside the farm is not the farmer's");
		level.removeBlock(neighbours, false);
		level.removeBlock(foreign, false);
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void butcherCullsOnlyItsOwnSurplusAndBreeds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "butcher");
		Plots.Paddock pen = buildPen(level, shop);
		Checks.assertTrue(helper, pen != null, "the butcher's paddock is part of its workshop");
		if (pen == null) return;
		BlockPos centre = pen.centre();
		List<Cow> herd = new ArrayList<>();
		for (int i = 0; i < 5; i++) herd.add(owned(level, EntityType.COW, centre.offset(i % 3 - 1, 0, i / 3), shop.state));
		Cow named = owned(level, EntityType.COW, centre.offset(1, 0, 1), shop.state);
		named.setCustomName(Component.literal("Burenka"));
		Cow foreign = animal(level, EntityType.COW, centre.offset(-1, 0, 1));
		Herd.adopt(foreign, shop.state.anchor.offset(40, 0, 0));
		Cow wild = animal(level, EntityType.COW, shop.state.anchor.offset(0, 0, -6));
		shop.chest.setItem(5, new ItemStack(Items.WHEAT, 4));

		Checks.assertTrue(helper, "cull".equals(shop.perform(level)), "more than four adults: one is culled");
		Checks.assertTrue(helper, herd.stream().filter(Cow::isAlive).count() == 4, "exactly one of the herd is gone");
		Checks.assertTrue(helper, named.isAlive() && foreign.isAlive() && wild.isAlive(), "named, foreign and wild cows are untouched");
		Checks.assertTrue(helper, shop.chest.countItem(Items.COOKED_BEEF) > 0, "the smoker cooks the beef");
		Checks.assertTrue(helper, shop.chest.countItem(Items.BEEF) == 0, "no raw beef next to a smoker");

		Checks.assertTrue(helper, "breed".equals(shop.perform(level)), "with food in the chest the herd is bred");
		Checks.assertTrue(helper, herd.stream().filter(Cow::isInLove).count() == 2, "two cows of the herd are in love");
		Checks.assertTrue(helper, !foreign.isInLove() && !wild.isInLove(), "only the own herd is fed");
		Checks.assertTrue(helper, shop.chest.countItem(Items.WHEAT) == 2, "two wheat were fed");
		Checks.assertTrue(helper, shop.perform(level) == null, "a herd of five needs no more adopting, the quotas are spent");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void shepherdLeadsAFreeSheepIntoThePenAndShearsIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "shepherd");
		Plots.Paddock pen = buildPen(level, shop);
		Checks.assertTrue(helper, pen != null, "the shepherd's paddock is part of its workshop");
		if (pen == null) return;
		Sheep sheep = animal(level, EntityType.SHEEP, shop.state.anchor.offset(0, 0, -8));
		Sheep named = animal(level, EntityType.SHEEP, shop.state.anchor.offset(2, 0, -8));
		named.setCustomName(Component.literal("Dolly"));

		Checks.assertTrue(helper, "adopt".equals(shop.perform(level)), "a free sheep is adopted");
		Checks.assertTrue(helper, Herd.owned(sheep, shop.state.anchor), "the nearest free sheep joins the herd");
		Checks.assertTrue(helper, !sheep.isLeashed(), "the lead comes off in the pen");
		Checks.assertTrue(helper, !Herd.owned(named, shop.state.anchor), "a named sheep is never taken");
		Checks.assertTrue(helper, level.getBlockState(pen.gate()).getValue(BlockStateProperties.OPEN).equals(false), "the gate is shut again");

		Checks.assertTrue(helper, "shear".equals(shop.perform(level)), "the own sheep is sheared");
		Checks.assertTrue(helper, sheep.isSheared() && !named.isSheared(), "only the herd sheep is sheared");
		Checks.assertTrue(helper, shop.chest.hasAnyMatching(stack -> stack.is(ItemTags.WOOL)), "the wool goes into the chest");
		helper.succeed();
	}

	/** Villagers cannot open a fence gate: one shut on a neighbour in the pen would keep it there day and night. */
	@GameTest(template = "workshop_platform")
	public static void aPenGateIsNeverShutOnAVillagerInside(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "shepherd");
		Plots.Paddock pen = buildPen(level, shop);
		if (pen == null) {
			Checks.fail(helper, "no pen");
			return;
		}
		BlockPos gate = pen.gate();
		BlockPos centre = pen.centre();
		Villager neighbour = com.vnap.Mc.create(EntityType.VILLAGER, level);
		if (neighbour == null) {
			Checks.fail(helper, "a neighbour");
			return;
		}
		com.vnap.Mc.snap(neighbour, centre.getX() + 1.5, centre.getY(), centre.getZ() + 0.5);
		neighbour.setNoAi(true);
		level.addFreshEntity(neighbour);
		animal(level, EntityType.SHEEP, shop.state.anchor.offset(0, 0, -8));
		Checks.assertTrue(helper, "adopt".equals(shop.perform(level)), "a free sheep is led in");
		Checks.assertTrue(helper, level.getBlockState(gate).getValue(BlockStateProperties.OPEN), "a neighbour is in the pen: the gate stays open");
		BlockPos away = pen.outside();
		com.vnap.Mc.snap(neighbour, away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		shop.perform(level);
		Checks.assertTrue(helper, !level.getBlockState(gate).getValue(BlockStateProperties.OPEN), "out again: the shepherd shuts the gate");
		com.vnap.Mc.snap(neighbour, centre.getX() + 1.5, centre.getY(), centre.getZ() + 0.5);
		shop.perform(level);
		Checks.assertTrue(helper, level.getBlockState(gate).getValue(BlockStateProperties.OPEN), "shut in somehow: the shepherd lets it out");
		helper.succeed();
	}

	/** A bought pair comes with the morning delivery into the paddock; a gate knocked out is the workshop's to mend. */
	@GameTest(template = "workshop_platform")
	public static void aBoughtPairComesIntoThePaddock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "butcher");
		Plots.Paddock pen = buildPen(level, shop);
		if (pen == null) {
			Checks.fail(helper, "no paddock");
			return;
		}
		long today = com.vnap.dialogue.Family.day(level);
		shop.state.stockDay = today + 1;
		shop.state.stockKind = "pig";
		shop.perform(level);
		Checks.assertTrue(helper, Herd.of(life(level, shop), net.minecraft.world.entity.animal.Pig.class).isEmpty(), "nothing before the morning delivery");
		shop.state.stockDay = today;
		shop.perform(level);
		List<net.minecraft.world.entity.animal.Pig> pigs = Herd.of(life(level, shop), net.minecraft.world.entity.animal.Pig.class);
		Checks.assertTrue(helper, pigs.size() == 2 && pigs.stream().allMatch(pig -> pen.contains(pig.blockPosition())),
				"the pair is let into the paddock and joins the herd");
		Checks.assertTrue(helper, shop.state.stockDay == -1L, "the delivery is in");
		level.removeBlock(pen.gate(), false);
		Checks.assertTrue(helper, buildPen(level, shop) == null, "no gate: no work in the paddock");
		String name = Workshops.schematicName("butcher", 3);
		BuildJob mend = BuildJob.missing(level, name, Workshops.schematic(level, name), shop.state.origin, shop.state.rotation, 0);
		Checks.assertTrue(helper, mend.size() == 1 && mend.nextPos().equals(pen.gate()), "the gate is the workshop's own: its mending puts it back");
		pigs.forEach(net.minecraft.world.entity.Entity::discard);
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void fishermanNeedsOpenWaterAndStopsAtSix(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "fisherman");
		BlockPos puddle = shop.state.anchor.offset(-6, -1, -8);
		level.setBlock(puddle, Blocks.WATER.defaultBlockState(), 3);
		level.setBlock(puddle.east(), Blocks.WATER.defaultBlockState(), 3);
		Checks.assertTrue(helper, shop.perform(level) == null, "a puddle is no fishing water");
		BlockPos pond = shop.state.anchor.offset(4, -1, -9);
		for (BlockPos pos : BlockPos.betweenClosed(pond.offset(-1, 0, -1), pond.offset(1, 0, 1))) {
			level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
		}
		BlockPos shore = Chores.shore(life(level, shop));
		Checks.assertTrue(helper, shore != null, "a 3x3 pond has a shore");
		int caught = 0;
		for (int i = 0; i < 10; i++) {
			if ("fish".equals(shop.perform(level))) caught++;
		}
		Checks.assertTrue(helper, caught == 6, "six catches a day, was " + caught);
		Checks.assertTrue(helper, !shop.chest.isEmpty(), "the catch goes into the chest");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void fletcherMakesArrows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "fletcher");
		shop.chest.setItem(10, new ItemStack(Items.FLINT));
		shop.chest.setItem(11, new ItemStack(Items.STICK));
		shop.chest.setItem(12, new ItemStack(Items.FEATHER));
		List<String> done = shop.until(level, "fletch", 10);
		Checks.assertTrue(helper, shop.chest.countItem(Items.ARROW) == 4, "flint, a stick and a feather make four arrows: " + done);
		Checks.assertTrue(helper, shop.chest.countItem(Items.FLINT) == 0, "the flint is used");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void leatherworkerFillsTheCauldronAndTans(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "leatherworker");
		Checks.assertTrue(helper, shop.perform(level) == null, "nothing to tan, the cauldron is filled only when there is work");
		shop.chest.setItem(5, new ItemStack(Items.ROTTEN_FLESH, 2));
		List<String> done = shop.until(level, "tan", 4);
		Checks.assertTrue(helper, done.contains("cauldron") && done.contains("tan"), "water first, then tanning: " + done);
		Checks.assertTrue(helper, shop.chest.countItem(Items.LEATHER) == 1, "two pieces of flesh give one leather");
		Checks.assertTrue(helper, level.getBlockState(shop.state.anchor).getValue(LayeredCauldronBlock.LEVEL) == 2, "tanning uses water");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void librarianCopiesABookOntoTheShelf(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "librarian");
		Checks.assertTrue(helper, "read".equals(shop.perform(level)), "without paper the librarian reads");
		shop.chest.setItem(5, new ItemStack(Items.PAPER, 3));
		shop.chest.setItem(6, new ItemStack(Items.LEATHER));
		Checks.assertTrue(helper, "copy".equals(shop.perform(level)), "paper and leather make a copy");
		Checks.assertTrue(helper, level.getBlockEntity(shop.state.at(1, 1, 3)) instanceof ChiseledBookShelfBlockEntity shelf && shelf.count() == 1,
				"the new book goes onto the shelf");
		Checks.assertTrue(helper, shop.chest.countItem(Items.PAPER) == 0 && shop.chest.countItem(Items.LEATHER) == 0, "the materials are used");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void cartographerSurveysBeforeDrawingAMap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "cartographer");
		// the paper a map takes is bought at the stall into the chest (Market.SUPPLIES)
		shop.chest.setItem(20, new ItemStack(Items.PAPER, 3));
		List<String> done = shop.until(level, "map", 8);
		Checks.assertTrue(helper, done.indexOf("map") >= 1 && done.subList(0, done.indexOf("map")).stream().allMatch("survey"::equals),
				"walks first, then the map: " + done);
		Checks.assertTrue(helper, shop.chest.countItem(Items.FILLED_MAP) == 1, "the map goes into the chest");
		Checks.assertTrue(helper, shop.perform(level) == null, "one map a day");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void clericHealsNeighboursAndLightsCandles(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "cleric");
		Villager hurt = com.vnap.Mc.create(EntityType.VILLAGER, level);
		if (hurt == null) return;
		com.vnap.Mc.snap(hurt, net.minecraft.world.phys.Vec3.atBottomCenterOf(shop.state.anchor.offset(3, 0, -5)));
		hurt.setNoAi(true);
		level.addFreshEntity(hurt);
		hurt.setHealth(5.0F);
		Checks.assertTrue(helper, "heal".equals(shop.perform(level)), "a hurt neighbour comes first");
		Checks.assertTrue(helper, hurt.hasEffect(MobEffects.REGENERATION), "the neighbour regenerates");
		Checks.assertTrue(helper, "candles".equals(shop.perform(level)), "then the candles");
		Checks.assertTrue(helper, level.getBlockState(shop.state.at(1, 1, 3)).getOptionalValue(BlockStateProperties.LIT).orElse(false),
				"the candles burn");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void masonCutsStoneAndStopsAtTheChestCap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "mason");
		shop.chest.setItem(5, new ItemStack(Items.COBBLESTONE, 2));
		List<String> done = shop.until(level, "cut", 20);
		Checks.assertTrue(helper, shop.chest.countItem(Items.STONE_BRICKS) == 1 && shop.chest.countItem(Items.COBBLESTONE) == 1,
				"one cobblestone makes one brick: " + done);
		shop.chest.setItem(6, new ItemStack(Items.STONE_BRICKS, Life.STOCK_CAP - 1));
		Checks.assertTrue(helper, shop.chest.countItem(Items.STONE_BRICKS) == Life.STOCK_CAP, "the chest is at the cap");
		Checks.assertTrue(helper, !shop.until(level, "cut", 20).contains("cut"), "a full stock stops the work");
		Checks.assertTrue(helper, shop.chest.countItem(Items.COBBLESTONE) == 1, "the stone is left for later");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void armorerRepairsAGolemWithIron(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "armorer");
		IronGolem golem = animal(level, EntityType.IRON_GOLEM, shop.state.anchor.offset(4, 0, -6));
		golem.setHealth(50.0F);
		Checks.assertTrue(helper, shop.perform(level) == null, "no iron, no repair");
		shop.chest.setItem(5, new ItemStack(Items.IRON_INGOT));
		Checks.assertTrue(helper, "golem".equals(shop.perform(level)), "with iron the golem is repaired");
		Checks.assertTrue(helper, golem.getHealth() == 75.0F && shop.chest.countItem(Items.IRON_INGOT) == 0, "an ingot gives 25 health");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void toolsmithSharpensWornTools(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "toolsmith");
		ItemStack axe = new ItemStack(Items.IRON_AXE);
		axe.setDamageValue(200);
		shop.chest.setItem(5, axe);
		Checks.assertTrue(helper, "sharpen".equals(shop.perform(level)), "a worn tool is sharpened");
		Checks.assertTrue(helper, shop.chest.getItem(5).getDamageValue() == 200 - Items.IRON_AXE.getDefaultInstance().getMaxDamage() / 4,
				"a quarter of the durability comes back");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void weaponsmithTempersASwordFromIron(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "weaponsmith");
		shop.chest.setItem(5, new ItemStack(Items.IRON_INGOT, 2));
		Checks.assertTrue(helper, "temper".equals(shop.perform(level)), "two ingots are tempered");
		Checks.assertTrue(helper, shop.chest.countItem(Items.IRON_SWORD) == 1 && shop.chest.countItem(Items.IRON_INGOT) == 0, "into a sword");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void aHerdNeverGrowsPastSix(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "butcher");
		Plots.Paddock pen = buildPen(level, shop);
		if (pen == null) {
			Checks.fail(helper, "no pen");
			return;
		}
		BlockPos centre = pen.centre();
		for (int i = 0; i < 4; i++) owned(level, EntityType.COW, centre.offset(i % 2, 0, i / 2), shop.state).setCustomName(Component.literal("cow" + i));
		for (int i = 0; i < 2; i++) owned(level, EntityType.COW, centre.offset(-1, 0, i), shop.state).setAge(-24000);
		shop.chest.setItem(5, new ItemStack(Items.WHEAT, 8));
		Checks.assertTrue(helper, Herd.of(life(level, shop), Cow.class).size() == Herd.MAX, "the herd is full");
		Checks.assertTrue(helper, shop.perform(level) == null, "a full herd is neither bred, adopted nor (being named or young) culled");
		Checks.assertTrue(helper, shop.chest.countItem(Items.WHEAT) == 8, "no food is used");
		helper.succeed();
	}

	@GameTest(template = "workshop_platform")
	public static void nothingIsDoneBeyondTheRadiusTheBellOrLoadedLand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Shop shop = shop(helper, "mason");
		BlockPos anchor = shop.state.anchor;
		Checks.assertTrue(helper, !Life.allowed(level, shop.villager, anchor, anchor.offset(0, 0, -(Life.RADIUS + 3)), Life.RADIUS),
				"a cell beyond the radius is not worked");
		shop.villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT,
				GlobalPos.of(level.dimension(), anchor.offset(TownRules.NEAR_BELL + 20, 0, 0)));
		Checks.assertTrue(helper, !Life.allowed(level, shop.villager, anchor, anchor.offset(-5, 0, -6), Life.RADIUS),
				"nothing further than the bell radius from the bell");
		shop.villager.getBrain().eraseMemory(MemoryModuleType.MEETING_POINT);
		Checks.assertTrue(helper, Life.allowed(level, shop.villager, anchor, anchor.offset(-5, 0, -6), Life.RADIUS), "within the radius it is");
		BlockPos unloaded = anchor.offset(100_000, 0, 100_000);
		Checks.assertTrue(helper, !level.isLoaded(unloaded) && !Life.allowed(level, shop.villager, anchor, unloaded, Integer.MAX_VALUE / 2),
				"unloaded land is never worked");
		helper.succeed();
	}

	private static Shop shop(GameTestHelper helper, String profession) {
		ServerLevel level = helper.getLevel();
		BlockPos site = helper.absolutePos(SITE);
		BlockPos origin = site.subtract(Workshops.layout(profession).focal());
		String name = Workshops.schematicName(profession, 3);
		BuildJob job = BuildJob.all(name, Workshops.schematic(level, name), origin, Rotation.NONE, 0);
		while (!job.done()) {
			job.skipDone(level);
			job.placeNext(level, null);
		}
		Block jobBlock = JOB_BLOCK.getOrDefault(profession, Blocks.AIR);
		level.setBlock(site, jobBlock.defaultBlockState(), 3);
		Villager villager = com.vnap.Mc.create(EntityType.VILLAGER, level);
		if (villager == null) throw new IllegalStateException("no villager");
		com.vnap.Mc.snap(villager, net.minecraft.world.phys.Vec3.atBottomCenterOf(site.offset(0, 0, -3)));
		villager.setNoAi(true);
		level.addFreshEntity(villager);
		VillagerProfession trade = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.withDefaultNamespace(profession));
		villager.setVillagerData(com.vnap.Mc.withLevel(com.vnap.Mc.withProfession(villager.getVillagerData(), trade), 5));
		WorkshopState state = new WorkshopState(profession, site, origin, Rotation.NONE, 3);
		state.tier = 3;
		((VillagerLifeData) villager).vnap$setWorkshop(state);
		Container chest = ProfessionWork.chest(level, state);
		if (chest == null) throw new IllegalStateException("no chest in " + name);
		return new Shop(villager, state, chest);
	}

	/** The paddock of the workshop built by {@link #shop} (part of the plan). */
	private static Plots.@Nullable Paddock buildPen(ServerLevel level, Shop shop) {
		Plots.Paddock paddock = Plots.paddock(level, shop.state);
		return paddock != null && Plots.standing(level, paddock) ? paddock : null;
	}

	private static Life life(ServerLevel level, Shop shop) {
		return Life.of(level, shop.villager, shop.state, DAY);
	}

	private static <T extends Mob> T animal(ServerLevel level, EntityType<T> type, BlockPos pos) {
		T mob = com.vnap.Mc.create(type, level);
		if (mob == null) throw new IllegalStateException("no " + type);
		com.vnap.Mc.snap(mob, net.minecraft.world.phys.Vec3.atBottomCenterOf(pos));
		mob.setNoAi(true);
		level.addFreshEntity(mob);
		return mob;
	}

	private static <T extends Animal> T owned(ServerLevel level, EntityType<T> type, BlockPos pos, WorkshopState state) {
		T animal = animal(level, type, pos);
		Herd.adopt(animal, state.anchor);
		return animal;
	}
}
