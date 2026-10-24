package com.vnap.gametest;

import com.vnap.village.market.Market;
import com.vnap.village.town.TownRules;
import com.vnap.dialogue.Family;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseLevels;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Houses grow by levels: a storey's bedroom, then a cellar dug under the house; the things of what is taken down move in. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class HouseLevelGameTests {
	private HouseLevelGameTests() {
	}

	@GameTest(batch = "homes", template = "home_platform", timeoutTicks = 200)
	public static void aHouseGrowsABedroomThenACellarAndKeepsItsBeds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// five blocks of earth to dig a cellar into
		for (int x = 0; x < 100; x++) for (int z = 0; z < 100; z++) for (int y = 2; y <= 6; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
		helper.setBlock(new BlockPos(50, 7, 50), Blocks.BELL);
		BlockPos bell = helper.absolutePos(new BlockPos(50, 7, 50));
		long day = Family.day(level);
		Villager owner = helper.spawn(EntityType.VILLAGER, 52, 7, 52);
		owner.setNoAi(true);
		owner.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		owner.getPersistentData().putLong(HomeProjects.HOMELESS_SINCE, day - 5);
		HouseProject house = HomeProjects.start(level, owner, day, List.of("cottage"));
		if (house == null) {
			Checks.fail(helper, "a site for the cottage");
			return;
		}
		build(level, house);
		HomeProjects.finish(level, house);
		BlockPos ownBed = ((VillagerLifeData) owner).vnap$homeBed();
		int before = HouseLevels.beds(level, house);
		if (ownBed == null || before != 1) {
			Checks.fail(helper, "the cottage has its one bed and the owner sleeps in it: " + before);
			return;
		}

		// a family that has outgrown its house grows it even owing all it may borrow: the town pays the rest, the loan stays at the cap
		Family.state(owner).debt = TownRules.LOAN_CAP;
		HouseProject storey = HouseLevels.upgrade(level, owner, house, day);
		Checks.assertTrue(helper, Market.debt(level, owner) == TownRules.LOAN_CAP, "the loan never goes over the cap: " + Market.debt(level, owner));
		Family.state(owner).debt = 0;
		if (storey == null) {
			Checks.fail(helper, "the cottage grows its next level");
			return;
		}
		Checks.assertTrue(helper, storey.plan.equals("cottage_l2") && storey.origin.equals(house.origin) && storey.groundY == 0,
				"the bedroom level in the same frame: " + storey.plan + " " + storey.groundY);
		Checks.assertTrue(helper, VillageMoodSavedData.get(level).houses().stream().noneMatch(h -> h == house), "the old record gives way to the new");
		build(level, storey);
		HomeProjects.finish(level, storey);
		Checks.assertTrue(helper, HouseLevels.beds(level, storey) == before + 2, "two beds more upstairs: " + HouseLevels.beds(level, storey));
		Checks.assertTrue(helper, ownBed.equals(((VillagerLifeData) owner).vnap$homeBed()), "the owner keeps the bed it had");

		// a chest of things taken down with the old house is carried into the new one
		BlockPos spare = storey.origin.offset(-3, 0, -3);
		level.setBlock(spare, Blocks.CHEST.defaultBlockState(), 3);
		if (level.getBlockEntity(spare) instanceof Container chest) chest.setItem(0, new ItemStack(Items.BREAD, 7));
		storey.clearing.add(spare);
		HomeProjects.clearOne(level, storey, spare);
		Checks.assertTrue(helper, level.getBlockState(spare).isAir() && storey.stash.stream().anyMatch(s -> s.is(Items.BREAD) && s.getCount() == 7),
				"the things are kept, not spilt: " + storey.stash);
		Checks.assertTrue(helper, HomeProjects.unpack(level, storey), "and put away in the house's chest");

		HouseProject cellar = HouseLevels.upgrade(level, owner, storey, day);
		if (cellar == null) {
			Checks.fail(helper, "the house grows a cellar");
			return;
		}
		Checks.assertTrue(helper, cellar.groundY == 4 && cellar.origin.equals(storey.origin.below(4)), "a cellar four deep: " + cellar.groundY);
		build(level, cellar);
		HomeProjects.finish(level, cellar);
		BoundingBox box = cellar.footprint(level);
		int barrels = 0;
		int ladders = 0;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.minY() + 3, box.maxZ())) {
			if (level.getBlockState(pos).is(Blocks.CHEST) || level.getBlockState(pos).is(Blocks.HAY_BLOCK)) barrels++;
			Checks.assertFalse(helper, level.getBlockState(pos).is(Blocks.BARREL), "no barrel: a fisherman would take it for his work place");
			if (level.getBlockState(pos).is(Blocks.LADDER)) ladders++;
		}
		Checks.assertTrue(helper, barrels >= 2 && ladders == 3, "a cellar with its stores and a ladder down: " + barrels + " stores, " + ladders + " ladders");
		Checks.assertTrue(helper, HouseLevels.beds(level, cellar) == before + 2 && ownBed.equals(((VillagerLifeData) owner).vnap$homeBed()),
				"the beds stay as they were");
		Checks.assertTrue(helper, HouseLevels.next(level, cellar.plan) == null, "and that was the last level");

		// the second age (docs/tower/CITY2.md §3): the house at its top level restyled in the fine palette, its family's own coins only
		int cost = HouseLevels.restyleCost(level, cellar);
		Family.state(owner).coins = cost;
		Checks.assertTrue(helper, cost > 0 && HouseLevels.restyle(level, owner, cellar, day) == null, "no restyle without a bread's reserve over its cost: " + cost);
		Family.state(owner).coins = cost + 4 * TownRules.RESERVE;
		Mayors.Office office0 = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
		long minted = office0 == null ? 0 : office0.minted;
		HouseProject fine = HouseLevels.restyle(level, owner, cellar, day);
		if (fine == null || office0 == null) {
			Checks.fail(helper, "the house is restyled: " + HouseLevels.refusal);
			return;
		}
		Checks.assertTrue(helper, fine.palette.equals("plains_fine") && fine.plan.equals(cellar.plan) && fine.origin.equals(cellar.origin) && !fine.clearing.isEmpty(),
				"the same plan in the same place, what differs taken down: " + fine.palette + " " + fine.clearing.size());
		Checks.assertTrue(helper, office0.minted == minted && Market.debt(level, owner) == 0 && Family.state(owner).coins < 4 * TownRules.RESERVE + 1,
				"its own coins paid, nothing minted, no loan: minted +" + (office0.minted - minted) + ", owes " + Market.debt(level, owner));
		Checks.assertTrue(helper, HouseLevels.restyle(level, owner, fine, day) == null, "and only once");
		build(level, fine);
		HomeProjects.finish(level, fine);
		BoundingBox fineBox = fine.footprint(level);
		int cobbles = 0;
		int dressed = 0;
		for (BlockPos pos : BlockPos.betweenClosed(fineBox.minX(), fineBox.minY(), fineBox.minZ(), fineBox.maxX(), fineBox.maxY(), fineBox.maxZ())) {
			if (level.getBlockState(pos).is(Blocks.COBBLESTONE)) cobbles++;
			if (level.getBlockState(pos).is(Blocks.STONE_BRICKS) || level.getBlockState(pos).is(Blocks.DEEPSLATE_TILE_STAIRS)) dressed++;
		}
		Checks.assertTrue(helper, cobbles == 0 && dressed > 20, "dressed stone and slate for cobbles: " + cobbles + " cobbles left, " + dressed + " dressed");
		Checks.assertTrue(helper, HouseLevels.beds(level, fine) == before + 2 && ownBed.equals(((VillagerLifeData) owner).vnap$homeBed()), "the beds stay");
		VillageMoodSavedData.get(level).houses().removeIf(h -> h.village == bell.asLong());
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
		if (office != null) Mayors.get(level.getServer()).offices().remove(office);
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aWifesBedBesideHerHusbandsMakesADoubleBedBothSleepIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(30, 2, 30);
		FamilyLifeGameTests.Couple couple = FamilyLifeGameTests.couple(helper, door, true);
		BlockPos anchor = Family.home(couple.husband());
		if (anchor == null) {
			Checks.fail(helper, "the husband's bed");
			return;
		}
		// the wedding set her bed down beside his (Family.furnish for the NoAI pair): one double bed
		BlockPos head = com.vnap.dialogue.FamilyHome.bedHead(level, anchor);
		BlockPos wifeBed = ((VillagerLifeData) couple.wife()).vnap$homeBed();
		if (wifeBed == null) {
			Checks.fail(helper, "the wife's bed");
			return;
		}
		BlockPos wifeHead = com.vnap.dialogue.FamilyHome.bedHead(level, wifeBed);
		BlockPos wifeFoot = wifeHead.relative(level.getBlockState(wifeHead).getValue(net.minecraft.world.level.block.BedBlock.FACING).getOpposite());
		Checks.assertTrue(helper, level.getBlockState(head).is(com.vnap.block.LifeBlocks.DOUBLE_BED)
				&& level.getBlockState(wifeHead).is(com.vnap.block.LifeBlocks.DOUBLE_BED)
				&& level.getBlockState(head).getValue(com.vnap.block.DoubleBedBlock.SIDE) != level.getBlockState(wifeHead).getValue(com.vnap.block.DoubleBedBlock.SIDE),
				"the two beds became one double bed: " + level.getBlockState(head) + " / " + level.getBlockState(wifeHead));
		Checks.assertTrue(helper, level.getBlockState(head).is(net.minecraft.tags.BlockTags.BEDS), "a bed as any other");
		Checks.assertTrue(helper, level.getPoiManager().getType(head).filter(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME)).isPresent()
				&& level.getPoiManager().getType(wifeHead).filter(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME)).isPresent(),
				"both halves are homes");
		Checks.assertTrue(helper, head.equals(Family.home(couple.husband())) || anchor.equals(Family.home(couple.husband())), "the husband keeps his half");
		Checks.assertTrue(helper, com.vnap.dialogue.FamilyHome.bedHead(level, wifeBed).equals(wifeHead), "the wife sleeps in the other half");
		// a player lies down in a half like in any bed
		net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		com.vnap.Mc.snap(player, head.getX() + 0.5, head.getY() + 1, head.getZ() + 1.5);
		player.startSleeping(head);
		Checks.assertTrue(helper, player.isSleeping() && level.getBlockState(head).getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)
				&& level.getBlockState(wifeHead).is(com.vnap.block.LifeBlocks.DOUBLE_BED), "a player sleeps in it and the bed holds");
		player.stopSleeping();
		// her half broken: it gives a bed back and his half stays, a single bed still his
		level.destroyBlock(wifeHead, true);
		Checks.assertTrue(helper, level.getBlockState(wifeHead).isAir() && level.getBlockState(wifeFoot).isAir(), "her half is gone");
		Checks.assertTrue(helper, level.getBlockState(head).is(Blocks.RED_BED) && level.getBlockState(head.south()).is(Blocks.RED_BED),
				"his half is a single bed now: " + level.getBlockState(head));
		Checks.assertTrue(helper, level.getPoiManager().getType(head).filter(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME)).isPresent(),
				"and still his home");
		helper.runAfterDelay(2, () -> {
			long beds = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(head).inflate(4.0),
					item -> item.getItem().is(Items.RED_BED)).stream().mapToInt(item -> item.getItem().getCount()).sum();
			Checks.assertTrue(helper, beds == 1, "one bed back: " + beds);
			helper.succeed();
		});
	}

	/** By hand would take a day: the old cells come down and the new ones go up at once, in the builder's order. */
	private static void build(ServerLevel level, HouseProject house) {
		while (!house.clearing.isEmpty()) HomeProjects.clearOne(level, house, house.clearing.get(0));
		BuildJob job = house.job(level);
		job.site = null;
		while (!job.done()) {
			job.skipDone(level);
			if (!job.done()) job.placeNext(level, null);
		}
	}
}
