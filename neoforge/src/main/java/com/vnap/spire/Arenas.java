package com.vnap.spire;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Random;

/**
 * A boss's room dressed for it (docs/tower/BOSSES.md): its floor laid in its own pattern — a clock face for the
 * Chronarch, comb for the Honey Golem, a star map for the Gravitor, chalk lines for the Schism — a band of its own
 * along the walls, a feature in each corner and things hanging from the ceiling. Only the floor's top, the walls'
 * face and the corners change: the way in, the chest, the portal and the pillars of its phases stay clear.
 */
final class Arenas {
	private Arenas() {
	}

	enum Pattern {
		RINGS, CHECKER, CROSS, SPOTS, GRID, SPOKES, STARS
	}

	/** One room's dress: the floor's two blocks and how they lie, the wall band, the corners, what hangs. */
	record Dress(Block floor, Block accent, Pattern pattern, @Nullable Block band, Block corner, @Nullable Block cap, Block hang) {
	}

	static @Nullable Dress of(int keeper) {
		return switch (keeper) {
			case 0 -> new Dress(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Pattern.CROSS, Blocks.MOSSY_STONE_BRICKS, Blocks.AMETHYST_BLOCK,
					Blocks.AMETHYST_CLUSTER, Blocks.LANTERN);
			case 1, 15 -> new Dress(Blocks.DARK_OAK_PLANKS, Blocks.BIRCH_PLANKS, Pattern.CHECKER, Blocks.BOOKSHELF, keeper == 1 ? Blocks.BOOKSHELF
					: Blocks.CHISELED_BOOKSHELF, null, Blocks.LANTERN);
			case 2 -> new Dress(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GOLD_BLOCK, Pattern.RINGS, Blocks.POLISHED_BLACKSTONE, Blocks.GRINDSTONE, null,
					Blocks.LANTERN);
			case 3, 17 -> new Dress(Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT, Pattern.SPOTS, null, Blocks.FLOWERING_AZALEA, null, Blocks.HANGING_ROOTS);
			case 4 -> new Dress(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Pattern.RINGS, Blocks.PRISMARINE, Blocks.SEA_LANTERN, null, Blocks.SEA_LANTERN);
			case 5, 19 -> new Dress(Blocks.PACKED_ICE, Blocks.BLUE_ICE, Pattern.CHECKER, Blocks.PACKED_ICE, Blocks.PACKED_ICE, Blocks.SNOW_BLOCK,
					Blocks.SOUL_LANTERN);
			case 6, 20 -> new Dress(Blocks.MYCELIUM, Blocks.MOSS_BLOCK, Pattern.SPOTS, Blocks.MUSHROOM_STEM, Blocks.MUSHROOM_STEM, Blocks.RED_MUSHROOM_BLOCK,
					Blocks.SHROOMLIGHT);
			// never honey underfoot: a sticky tile in the middle of a fight changes it unseen
			case 7 -> new Dress(Blocks.YELLOW_TERRACOTTA, Blocks.HONEYCOMB_BLOCK, Pattern.CHECKER, Blocks.ORANGE_TERRACOTTA, Blocks.BEEHIVE, null, Blocks.LANTERN);
			case 21 -> new Dress(Blocks.SMOOTH_SANDSTONE, Blocks.HONEYCOMB_BLOCK, Pattern.RINGS, Blocks.ORANGE_TERRACOTTA, Blocks.BEE_NEST, null, Blocks.LANTERN);
			case 8, 22 -> new Dress(Blocks.BLACK_CONCRETE, Blocks.WHITE_CONCRETE, Pattern.STARS, Blocks.CALCITE, Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_CLUSTER,
					Blocks.END_ROD);
			case 9 -> new Dress(Blocks.CALCITE, Blocks.QUARTZ_BLOCK, Pattern.RINGS, Blocks.CRYING_OBSIDIAN, Blocks.CRYING_OBSIDIAN, null, Blocks.END_ROD);
			case 10, 11, 12, 13, 18 -> new Dress(Blocks.STONE_BRICKS, Blocks.WHITE_CONCRETE, Pattern.GRID, Blocks.CRACKED_STONE_BRICKS, Blocks.SCAFFOLDING,
					null, Blocks.LANTERN);
			case 14 -> new Dress(Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Pattern.SPOTS, Blocks.DRIPSTONE_BLOCK, Blocks.DRIPSTONE_BLOCK, null, Blocks.HANGING_ROOTS);
			case 16 -> new Dress(Blocks.SMOOTH_QUARTZ, Blocks.POLISHED_BLACKSTONE, Pattern.SPOKES, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GRINDSTONE, null,
					Blocks.LANTERN);
			case 23 -> new Dress(Blocks.SMOOTH_QUARTZ, Blocks.TINTED_GLASS, Pattern.CHECKER, Blocks.QUARTZ_PILLAR, Blocks.GLASS, null, Blocks.END_ROD);
			default -> null;
		};
	}

	/** Which boss a floor has: the chapter's keeper, the half-chapter's trial, the Schism's pair; -1 for none. */
	static int boss(int floor) {
		int chapter = Math.floorMod(Genome.chapter(floor), 10);
		if (floor % 10 == 0) return chapter;
		if (Genome.schism(floor)) return Genome.MASON;
		if (Genome.trial(floor)) return Genome.TRIAL + chapter;
		return -1;
	}

	static void dress(FloorPlan plan, int floor, int x0, int z0, int height) {
		Dress dress = of(boss(floor));
		if (dress == null) return;
		Random random = new Random(floor * 31L + x0);
		int top = FloorGenerator.FLOOR + height + 1;
		int y = FloorGenerator.FLOOR;
		for (int x = 1; x < 15; x++) {
			for (int z = 1; z < 15; z++) {
				BlockState under = plan.get(x0 + x, y, z0 + z);
				if (under == null || !plan.solid(x0 + x, y, z0 + z) || under.getBlock() instanceof net.minecraft.world.level.block.StairBlock
						|| under.is(SpireBlocks.RUNE)) continue;
				plan.set(x0 + x, y, z0 + z, (accent(dress.pattern(), x, z, random) ? dress.accent() : dress.floor()).defaultBlockState());
			}
		}
		// the band along the walls' face, a block above the floor's trim to below the middle belt
		Block band = dress.band();
		if (band != null) {
			for (int along = 0; along < 16; along++) {
				for (int yy = y + 2; yy <= y + 3; yy++) {
					for (int[] at : new int[][] {{along, 0}, {along, 15}, {0, along}, {15, along}}) {
						BlockState there = plan.get(x0 + at[0], yy, z0 + at[1]);
						// an opaque block of the wall, not a window (isSolidRender lost its arguments from 1.21.2)
						if (there != null && there.canOcclude() && net.minecraft.world.level.block.Block.isShapeFullBlock(
								there.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO))) {
							plan.set(x0 + at[0], yy, z0 + at[1], band.defaultBlockState());
						}
					}
				}
			}
		}
		// a feature in each corner, two high, and its cap
		for (int[] corner : new int[][] {{2, 2}, {13, 2}, {2, 13}, {13, 13}}) {
			int x = x0 + corner[0];
			int z = z0 + corner[1];
			if (plan.get(x, y + 1, z) != null || plan.get(x, y + 2, z) != null || plan.get(x, y + 3, z) != null) continue;
			BlockState feature = state(dress.corner());
			plan.set(x, y + 1, z, feature);
			if (!dress.corner().defaultBlockState().is(Blocks.GRINDSTONE) && !dress.corner().defaultBlockState().is(Blocks.BEEHIVE) && !dress.corner().defaultBlockState().is(Blocks.BEE_NEST) && !dress.corner().defaultBlockState().is(Blocks.FLOWERING_AZALEA)) plan.set(x, y + 2, z, feature);
			Block cap = dress.cap();
			if (cap != null) plan.set(x, plan.get(x, y + 2, z) == null ? y + 2 : y + 3, z, state(cap));
		}
		// what hangs from the ceiling, off the lamps' spots
		for (int[] spot : new int[][] {{3, 7}, {12, 7}, {7, 3}, {8, 12}}) {
			int x = x0 + spot[0];
			int z = z0 + spot[1];
			if (plan.get(x, top - 1, z) != null) continue;
			Block hang = dress.hang();
			if (hang.defaultBlockState().is(Blocks.HANGING_ROOTS) || hang.defaultBlockState().is(Blocks.END_ROD)) {
				plan.set(x, top - 1, z, state(hang));
			} else {
				plan.set(x, top - 1, z, Blocks.CHAIN.defaultBlockState());
				BlockState light = state(hang);
				if (light.hasProperty(net.minecraft.world.level.block.LanternBlock.HANGING)) light = light.setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
				plan.set(x, top - 2, z, light);
			}
		}
	}

	private static BlockState state(Block block) {
		BlockState state = block.defaultBlockState();
		if (state.hasProperty(LeavesBlock.PERSISTENT)) state = state.setValue(LeavesBlock.PERSISTENT, true);
		if (state.is(Blocks.END_ROD)) state = state.setValue(net.minecraft.world.level.block.EndRodBlock.FACING, net.minecraft.core.Direction.DOWN);
		return state;
	}

	private static boolean accent(Pattern pattern, int x, int z, Random random) {
		double dx = x - 7.5;
		double dz = z - 7.5;
		double r = Math.sqrt(dx * dx + dz * dz);
		return switch (pattern) {
			case RINGS -> ((int) Math.round(r)) % 3 == 0;
			case CHECKER -> ((x / 2) + (z / 2)) % 2 == 0;
			case CROSS -> Math.abs(dx) < 1 || Math.abs(dz) < 1 || Math.abs(Math.abs(dx) - Math.abs(dz)) < 0.6;
			case SPOTS -> random.nextInt(4) == 0;
			case GRID -> x % 3 == 1 || z % 3 == 1;
			case SPOKES -> Math.abs(r - 6.0) < 0.6 || (r > 2 && r < 6 && (Math.round(Math.toDegrees(Math.atan2(dz, dx)) + 360) % 30 < 8));
			case STARS -> random.nextInt(9) == 0 || Math.abs(r - 5.5) < 0.45;
		};
	}
}
