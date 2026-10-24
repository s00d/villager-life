package com.vnap.spire;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Random;

/**
 * The look of a floor: what its floors, walls, pillars, ceilings and lights are made of and what stands about in
 * its rooms (docs/tower/DESIGN.md §4). A chapter has a theme of its own and two or three it borrows from.
 */
public record Theme(String name, List<Block> floor, List<Block> wall, Block trim, Block pillar, Block ceiling, Block light,
		Block glass, List<Block> props, List<Block> hanging, boolean water, Block runner) {

	public BlockState floor(Random random) {
		return pick(floor, random).defaultBlockState();
	}

	public BlockState wall(Random random) {
		return pick(wall, random).defaultBlockState();
	}

	/** How many halls in ten of its floors have a dais in the middle (a reading desk, an orrery stand, a forge). */
	public int daises() {
		if (water) return 1;
		return switch (name) {
			case "archive", "observatory" -> 4;
			case "summit", "roots", "workshop" -> 2;
			default -> 1;
		};
	}

	public BlockState prop(Random random) {
		return pick(props, random).defaultBlockState();
	}

	/** The theme of this name (a floor's plan keeps only the name), roots when none is. */
	public static Theme byName(String name) {
		for (Theme theme : List.of(ROOTS, ARCHIVE, WORKSHOP, GARDENS, GALLERIES, ICE, SPORES, HIVE, OBSERVATORY, SUMMIT)) {
			if (theme.name().equals(name)) return theme;
		}
		return ROOTS;
	}

	static Block pick(List<Block> blocks, Random random) {
		// the first is the main one, the rest wear it down here and there: rarely, so it reads as wear, not noise
		return random.nextInt(12) == 0 ? blocks.get(random.nextInt(blocks.size())) : blocks.get(0);
	}

	public static final Theme ROOTS = new Theme("roots",
			List.of(Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS),
			List.of(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS),
			Blocks.POLISHED_ANDESITE, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DRIPSTONE_BLOCK, Blocks.SHROOMLIGHT, Blocks.TINTED_GLASS,
			List.of(Blocks.AMETHYST_CLUSTER, Blocks.MOSS_CARPET, Blocks.FLOWERING_AZALEA, Blocks.LANTERN, Blocks.MOSSY_COBBLESTONE_WALL, Blocks.CANDLE),
			List.of(Blocks.HANGING_ROOTS, Blocks.LANTERN), false, Blocks.MOSS_CARPET);
	public static final Theme ARCHIVE = new Theme("archive",
			List.of(Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS),
			List.of(Blocks.BOOKSHELF, Blocks.DARK_OAK_PLANKS, Blocks.CHISELED_BOOKSHELF),
			Blocks.STRIPPED_SPRUCE_LOG, Blocks.DARK_OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.OCHRE_FROGLIGHT, Blocks.GLASS,
			List.of(Blocks.LECTERN, Blocks.BOOKSHELF, Blocks.CANDLE, Blocks.RED_CARPET, Blocks.CARTOGRAPHY_TABLE, Blocks.FLOWER_POT),
			List.of(Blocks.CHAIN, Blocks.LANTERN), false, Blocks.RED_CARPET);
	public static final Theme WORKSHOP = new Theme("workshop",
			List.of(Blocks.CUT_COPPER, Blocks.EXPOSED_CUT_COPPER, Blocks.WEATHERED_CUT_COPPER),
			List.of(Blocks.BRICKS, Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER),
			Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.COPPER_BLOCK, Blocks.BRICKS, Blocks.OCHRE_FROGLIGHT, Blocks.GLASS,
			List.of(Blocks.ANVIL, Blocks.BLAST_FURNACE, Blocks.GRINDSTONE, Blocks.CHAIN, Blocks.SMITHING_TABLE, Blocks.LIGHTNING_ROD),
			List.of(Blocks.CHAIN, Blocks.LANTERN), false, Blocks.BROWN_CARPET);
	public static final Theme GARDENS = new Theme("gardens",
			List.of(Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT),
			List.of(Blocks.CHERRY_PLANKS, Blocks.STRIPPED_CHERRY_WOOD, Blocks.CALCITE),
			Blocks.CHERRY_LOG, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, Blocks.SEA_LANTERN, Blocks.PINK_STAINED_GLASS,
			List.of(Blocks.PINK_PETALS, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.FLOWERING_AZALEA, Blocks.LILY_OF_THE_VALLEY, Blocks.POPPY),
			List.of(Blocks.CHERRY_LEAVES, Blocks.SHROOMLIGHT), true, Blocks.PINK_CARPET);
	public static final Theme GALLERIES = new Theme("galleries",
			List.of(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE),
			List.of(Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE),
			Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_WALL, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN, Blocks.LIGHT_BLUE_STAINED_GLASS,
			List.of(Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.SEA_PICKLE, Blocks.CONDUIT, Blocks.PRISMARINE_WALL, Blocks.SPONGE),
			List.of(Blocks.CHAIN, Blocks.SEA_LANTERN), true, Blocks.CYAN_CARPET);
	public static final Theme ICE = new Theme("ice",
			List.of(Blocks.PACKED_ICE, Blocks.SNOW_BLOCK, Blocks.BLUE_ICE),
			List.of(Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK),
			Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_PILLAR, Blocks.PACKED_ICE, Blocks.VERDANT_FROGLIGHT, Blocks.LIGHT_BLUE_STAINED_GLASS,
			List.of(Blocks.POWDER_SNOW_CAULDRON, Blocks.SNOW, Blocks.BLUE_ICE, Blocks.LANTERN, Blocks.WHITE_CARPET, Blocks.CHISELED_QUARTZ_BLOCK),
			List.of(Blocks.CHAIN, Blocks.LANTERN), false, Blocks.WHITE_CARPET);
	public static final Theme SPORES = new Theme("spores",
			List.of(Blocks.MYCELIUM, Blocks.MOSS_BLOCK, Blocks.PODZOL),
			List.of(Blocks.MUSHROOM_STEM, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK),
			Blocks.MUSHROOM_STEM, Blocks.MUSHROOM_STEM, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.SHROOMLIGHT, Blocks.LIME_STAINED_GLASS,
			List.of(Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM, Blocks.MOSS_CARPET, Blocks.RED_MUSHROOM_BLOCK, Blocks.SHROOMLIGHT, Blocks.MUSHROOM_STEM),
			List.of(Blocks.MUSHROOM_STEM, Blocks.SHROOMLIGHT), true, Blocks.MOSS_CARPET);
	public static final Theme HIVE = new Theme("hive",
			List.of(Blocks.HONEYCOMB_BLOCK, Blocks.YELLOW_TERRACOTTA, Blocks.ORANGE_TERRACOTTA),
			List.of(Blocks.HONEYCOMB_BLOCK, Blocks.YELLOW_TERRACOTTA, Blocks.BEEHIVE),
			Blocks.STRIPPED_BIRCH_WOOD, Blocks.BIRCH_LOG, Blocks.HONEYCOMB_BLOCK, Blocks.SHROOMLIGHT, Blocks.ORANGE_STAINED_GLASS,
			List.of(Blocks.HONEY_BLOCK, Blocks.BEE_NEST, Blocks.HAY_BLOCK, Blocks.YELLOW_CARPET, Blocks.LANTERN, Blocks.CANDLE),
			List.of(Blocks.CHAIN, Blocks.LANTERN), false, Blocks.YELLOW_CARPET);
	public static final Theme OBSERVATORY = new Theme("observatory",
			List.of(Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE),
			List.of(Blocks.DEEPSLATE_BRICKS, Blocks.AMETHYST_BLOCK, Blocks.DEEPSLATE_TILES),
			Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BASALT, Blocks.DEEPSLATE_TILES, Blocks.PEARLESCENT_FROGLIGHT, Blocks.PURPLE_STAINED_GLASS,
			List.of(Blocks.AMETHYST_CLUSTER, Blocks.LIGHTNING_ROD, Blocks.LODESTONE, Blocks.RESPAWN_ANCHOR, Blocks.PURPLE_CARPET, Blocks.CANDLE),
			List.of(Blocks.CHAIN, Blocks.END_ROD), false, Blocks.PURPLE_CARPET);
	public static final Theme SUMMIT = new Theme("summit",
			List.of(Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, Blocks.CHISELED_QUARTZ_BLOCK),
			List.of(Blocks.QUARTZ_BRICKS, Blocks.WHITE_CONCRETE, Blocks.QUARTZ_PILLAR),
			Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.SMOOTH_QUARTZ, Blocks.SEA_LANTERN, Blocks.WHITE_STAINED_GLASS,
			List.of(Blocks.LANTERN, Blocks.WHITE_BANNER, Blocks.BELL, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.LIGHT_BLUE_CARPET, Blocks.FLOWER_POT),
			List.of(Blocks.CHAIN, Blocks.SOUL_LANTERN), false, Blocks.LIGHT_BLUE_CARPET);
}
