package com.vnap.village.market;

import com.vnap.village.build.BuildJob;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What building costs in items: the items a schematic block takes (a door or a bed once for both halves, a potted
 * plant is a pot and the plant, a double slab two slabs, candles and pickles by their count, a wall banner or torch
 * its standing item), the bill of a whole job, and wood: every wooden part is made of its tree's logs at the stall
 * (planks, stairs, slabs, fences, doors...), so the village's own felled logs build its houses.
 */
public final class Materials {
	/** Log-equivalents of one wooden part, by the end of its id. */
	private static final Map<String, Double> WOOD_SHAPES = new LinkedHashMap<>();

	static {
		WOOD_SHAPES.put("_planks", 0.25);
		WOOD_SHAPES.put("_stairs", 0.375);
		WOOD_SHAPES.put("_slab", 0.125);
		WOOD_SHAPES.put("_fence_gate", 1.0);
		WOOD_SHAPES.put("_fence", 0.42);
		WOOD_SHAPES.put("_trapdoor", 0.75);
		WOOD_SHAPES.put("_door", 0.5);
		WOOD_SHAPES.put("_pressure_plate", 0.5);
		WOOD_SHAPES.put("_button", 0.25);
		WOOD_SHAPES.put("_sign", 0.55);
		WOOD_SHAPES.put("_wood", 1.33);
		WOOD_SHAPES.put("_log", 1.0);
	}

	private static final List<String> TREES = List.of("dark_oak", "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry");

	private Materials() {
	}

	/** The items this schematic state takes; empty for air, liquids, fire and the second half of a door or bed. */
	public static Map<Item, Integer> items(BlockState state) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return out;
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return out;
		if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) return out;
		Block block = state.getBlock();
		if (block instanceof FlowerPotBlock pot) {
			out.put(Items.FLOWER_POT, 1);
			Item plant = pot.getPotted().asItem();
			if (!plant.equals(Items.AIR)) out.merge(plant, 1, Integer::sum);
			return out;
		}
		// a trodden path is grass a shovel went over (Roads): nothing to buy
		if (block instanceof net.minecraft.world.level.block.DirtPathBlock) return out;
		// tilled ground is dug-up dirt, turf is dirt the grass takes to
		if (block instanceof net.minecraft.world.level.block.FarmBlock || block instanceof net.minecraft.world.level.block.GrassBlock) {
			out.put(Items.DIRT, 1);
			return out;
		}
		Item item = itemOf(block);
		if (item.equals(Items.AIR)) return out;
		int count = 1;
		if (block instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) count = 2;
		if (state.hasProperty(BlockStateProperties.CANDLES)) count = state.getValue(BlockStateProperties.CANDLES);
		if (state.hasProperty(BlockStateProperties.PICKLES)) count = state.getValue(BlockStateProperties.PICKLES);
		if (state.hasProperty(BlockStateProperties.LAYERS)) count = state.getValue(BlockStateProperties.LAYERS);
		out.put(item, count);
		return out;
	}

	/** The item of a block, the standing one for a wall block (wall banner, wall torch, wall sign). */
	public static Item itemOf(Block block) {
		Item item = block.asItem();
		if (!item.equals(Items.AIR)) return item;
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
		String path = id.getPath().replace("_wall_", "_");
		if (path.equals("wall_torch")) path = "torch";
		Item standing = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path));
		return standing;
	}

	/** The bill of what is left to build: every remaining cell that does not stand yet. */
	public static Map<Item, Integer> bill(ServerLevel level, BuildJob job) {
		return job.bill(level, Integer.MAX_VALUE);
	}

	/** The bill of a whole schematic (a new project, a rite, a furnishing set). */
	public static Map<Item, Integer> bill(SchematicSpec schematic) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (SchematicSpec.PlacedBlock block : schematic.blocks) add(out, items(block.state()));
		return out;
	}

	public static void add(Map<Item, Integer> into, Map<Item, Integer> items) {
		items.forEach((item, count) -> into.merge(item, count, Integer::sum));
	}

	/** Copper the bill costs at the stall's prices. */
	public static int cost(Map<Item, Integer> bill) {
		int total = 0;
		for (Map.Entry<Item, Integer> entry : bill.entrySet()) total += Prices.price(new ItemStack(entry.getKey())) * entry.getValue();
		return total;
	}

	/** The log a wooden part is made of and how many logs one part takes; null for anything not made of wood. */
	public static @Nullable Wood wood(Item item) {
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) return null;
		String stripped = path.startsWith("stripped_") ? path.substring("stripped_".length()) : path;
		for (String tree : TREES) {
			if (!stripped.startsWith(tree + "_")) continue;
			String shape = stripped.substring(tree.length());
			Double logs = WOOD_SHAPES.get(shape);
			if (logs == null) {
				for (Map.Entry<String, Double> entry : WOOD_SHAPES.entrySet()) {
					if (shape.endsWith(entry.getKey())) {
						logs = entry.getValue();
						break;
					}
				}
			}
			if (logs == null) return null;
			Item log = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(tree + "_log"));
			return log.equals(Items.AIR) ? null : new Wood(log, logs);
		}
		return null;
	}

	/** {@code logs} of {@code log} make one of the part. */
	public record Wood(Item log, double logs) {
	}
}
