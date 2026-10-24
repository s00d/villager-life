package com.vnap.gametest;

import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyHome;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseLevels;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Shops;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Every house the villagers build is lit inside: a dark floor under a roof is where the night's monsters come. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class LightGameTests {
	private LightGameTests() {
	}

	@GameTest(batch = "light", template = "home_platform", timeoutTicks = 2400)
	public static void everyHousePlanIsLitInside(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> plans = new ArrayList<>();
		for (String plan : HomeProjects.PLANS) {
			for (String one = plan; one != null; one = HouseLevels.next(level, one)) plans.add(one);
		}
		for (String house : HouseLevels.WORLD_HOUSES) {
			// the copy is the world's own house; what its levels add is lit
			for (String one = HouseLevels.next(level, HouseLevels.WORLD + house); one != null; one = HouseLevels.next(level, one)) plans.add(one);
		}
		plans.addAll(Mayors.HALLS);
		plans.addAll(Shops.STALLS);
		BlockPos bell = helper.absolutePos(new BlockPos(5, 2, 5));
		List<String> dark = new ArrayList<>();
		step(helper, level, plans, 0, bell, dark);
	}

	private static void step(GameTestHelper helper, ServerLevel level, List<String> plans, int index, BlockPos bell, List<String> dark) {
		if (index >= plans.size()) {
			Checks.assertTrue(helper, dark.isEmpty(), "dark floors in " + dark);
			helper.succeed();
			return;
		}
		HouseProject house = new HouseProject(bell.asLong(), bell, UUID.randomUUID(), plans.get(index), "plains", helper.absolutePos(new BlockPos(30, 2, 30)),
				Rotation.NONE, Family.day(level));
		// the earth a cellar is dug into (or nothing under its margins would be ground)
		house.groundY = HouseLevels.floorOf(house.schematic(level));
		BoundingBox frame = house.footprint(level);
		for (BlockPos cell : BlockPos.betweenClosed(frame.minX() - 1, frame.minY(), frame.minZ() - 1, frame.maxX() + 1, frame.minY() + house.groundY - 1, frame.maxZ() + 1)) {
			level.setBlock(cell, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		BuildJob job = house.job(level);
		job.site = null;
		job.completeNow(level);
		BoundingBox box = house.footprint(level);
		measure(helper, level, plans, index, bell, dark, house, box);
	}

	private static void measure(GameTestHelper helper, ServerLevel level, List<String> plans, int index, BlockPos bell, List<String> dark,
								HouseProject house, BoundingBox box) {
		LightWait.after(helper, level, box, () -> {
			List<String> where = new ArrayList<>();
			for (BlockPos cell : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (level.getBlockState(cell).getCollisionShape(level, cell).isEmpty() && level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
						&& level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), net.minecraft.core.Direction.UP)
						&& FamilyHome.roofed(level, cell) && level.getBrightness(LightLayer.BLOCK, cell) == 0) {
					BlockPos rel = cell.subtract(house.origin);
					where.add(rel.getX() + "," + rel.getY() + "," + rel.getZ());
				}
			}
			if (!where.isEmpty()) dark.add(plans.get(index) + " " + where.size() + " " + where);
			for (BlockPos cell : BlockPos.betweenClosed(box.minX() - 1, box.minY(), box.minZ() - 1, box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1)) {
				level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			helper.runAfterDelay(2, () -> step(helper, level, plans, index + 1, bell, dark));
		});
	}
}
