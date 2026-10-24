package com.vnap.gametest;

import com.vnap.village.script.SchematicSpec;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Every plan is a vanilla structure file the game reads whole: blocks, the rite workers' places, the centre. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class StructureGameTests {
	@GameTest(template = "platform")
	public static void everyStructureLoadsAsItsPlan(GameTestHelper helper) {
		ResourceManager manager = helper.getLevel().getServer().getResourceManager();
		Map<ResourceLocation, ?> found = manager.listResources("structure/village", id -> id.getNamespace().equals("villager_life") && id.getPath().endsWith(".nbt"));
		List<String> problems = new ArrayList<>();
		for (ResourceLocation id : found.keySet()) {
			String name = id.getPath().substring(SchematicSpec.STRUCTURES.length(), id.getPath().length() - 4);
			SchematicSpec spec = SchematicSpec.load(manager, name);
			if (spec.blocks.isEmpty()) problems.add(name + " is empty");
			if (spec.focal.getX() < 0 || spec.focal.getX() >= spec.sizeX || spec.focal.getZ() < 0 || spec.focal.getZ() >= spec.sizeZ) {
				problems.add(name + " has its centre outside");
			}
		}
		Checks.assertTrue(helper, found.size() >= 60, "the plans are there, found " + found.size());
		Checks.assertTrue(helper, problems.isEmpty(), "structures: " + problems);
		helper.succeed();
	}
}
