package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * The one big thing of a floor, in the middle of its grandest hall, that a player remembers it by (DESIGN §4): a
 * root column of the Core in the Roots, a reading tower in the Archive, a great gear in the Workshops, a cherry tree
 * in the Gardens, a fountain in the Galleries, an ice spire, a giant mushroom, a honeycomb pillar, a star globe, the
 * beacon's pedestal at the Summit. It takes the room's middle five by five and leaves the way round it free.
 */
final class Wonders {
	private Wonders() {
	}

	/** Builds the theme's wonder centred on (cx, cz) on the floor, up to {@code height} blocks. */
	static void build(FloorPlan plan, Theme theme, Random random, int cx, int cz, int height) {
		int base = FloorGenerator.FLOOR + 1;
		switch (theme.name()) {
			case "roots" -> column(plan, cx, cz, base, height, Blocks.DRIPSTONE_BLOCK.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState());
			case "archive" -> readingTower(plan, cx, cz, base, height);
			case "workshop" -> gear(plan, cx, cz, base, height);
			case "gardens" -> tree(plan, cx, cz, base, height, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, random);
			case "galleries" -> fountain(plan, cx, cz, base, Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState());
			case "ice" -> column(plan, cx, cz, base, height, Blocks.PACKED_ICE.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState());
			case "spores" -> mushroom(plan, cx, cz, base, height);
			case "hive" -> column(plan, cx, cz, base, height, Blocks.HONEYCOMB_BLOCK.defaultBlockState(), Blocks.BEE_NEST.defaultBlockState());
			case "observatory" -> globe(plan, cx, cz, base, height);
			default -> pedestal(plan, cx, cz, base, height);
		}
	}

	/** A twisting column, wide at the foot, a glowing core showing through its gaps. */
	private static void column(FloorPlan plan, int cx, int cz, int base, int height, BlockState stone, BlockState glow) {
		for (int y = 0; y < height; y++) {
			double r = y < 2 ? 2.2 : 1.4;
			double turn = y * 0.6;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > r) continue;
					double angle = Math.atan2(dz, dx) - turn;
					boolean gap = d > 0.9 && Math.cos(angle * 2) > 0.75;
					plan.set(cx + dx, base + y, cz + dz, gap ? glow : stone);
				}
			}
		}
	}

	private static void readingTower(FloorPlan plan, int cx, int cz, int base, int height) {
		int top = Math.min(height, 7);
		for (int y = 0; y < top; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean centre = dx == 0 && dz == 0;
					plan.set(cx + dx, base + y, cz + dz, (centre ? Blocks.DARK_OAK_LOG : y % 3 == 2 ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF).defaultBlockState());
				}
			}
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			plan.set(cx + direction.getStepX() * 2, base, cz + direction.getStepZ() * 2,
					Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, direction));
		}
		plan.set(cx, base + top, cz, Blocks.LANTERN.defaultBlockState());
	}

	private static void gear(FloorPlan plan, int cx, int cz, int base, int height) {
		int r = Math.min(3, Math.max(2, (height - 2) / 2));
		int cy = base + r;
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dy = -r - 1; dy <= r + 1; dy++) {
				double d = Math.sqrt(dx * dx + dy * dy);
				boolean ring = d > r - 0.7 && d <= r + 0.3;
				boolean tooth = d > r + 0.3 && d <= r + 1.2 && (Math.round(Math.atan2(dy, dx) * 8 / Math.PI) % 2 == 0);
				boolean hub = d <= 1.0;
				boolean spoke = d < r && (dx == 0 || dy == 0);
				if (cy + dy < base) continue;
				if (ring || tooth) plan.set(cx + dx, cy + dy, cz, Blocks.COPPER_BLOCK.defaultBlockState());
				else if (hub) plan.set(cx + dx, cy + dy, cz, Blocks.WAXED_EXPOSED_COPPER.defaultBlockState());
				else if (spoke) plan.set(cx + dx, cy + dy, cz, Blocks.CHAIN.defaultBlockState()
						.setValue(RotatedPillarBlock.AXIS, dx == 0 ? Direction.Axis.Y : Direction.Axis.X));
			}
		}
		for (int y = base; y < cy - r; y++) plan.set(cx, y, cz, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
	}

	private static void tree(FloorPlan plan, int cx, int cz, int base, int height, Block log, Block leaves, Random random) {
		int trunk = Math.max(3, height - 4);
		for (int y = 0; y < trunk; y++) plan.set(cx, base + y, cz, log.defaultBlockState());
		BlockState leaf = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		int top = base + trunk;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (dx * dx + dz * dz + dy * dy * 2 > 6 + random.nextInt(2)) continue;
					if (plan.get(cx + dx, top + dy, cz + dz) == null) plan.set(cx + dx, top + dy, cz + dz, leaf);
				}
			}
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			plan.set(cx + direction.getStepX() * 2, base, cz + direction.getStepZ() * 2, Blocks.PINK_PETALS.defaultBlockState());
		}
	}

	private static void fountain(FloorPlan plan, int cx, int cz, int base, BlockState stone, BlockState light) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				plan.set(cx + dx, base, cz + dz, rim ? stone : net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
				if (!rim) plan.set(cx + dx, base - 1, cz + dz, light);
			}
		}
		for (int y = 1; y <= 3; y++) plan.set(cx, base + y, cz, stone);
		plan.set(cx, base + 4, cz, light);
	}

	private static void mushroom(FloorPlan plan, int cx, int cz, int base, int height) {
		int stem = Math.max(3, height - 3);
		for (int y = 0; y < stem; y++) plan.set(cx, base + y, cz, Blocks.MUSHROOM_STEM.defaultBlockState());
		int cap = base + stem;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 3.2) continue;
				plan.set(cx + dx, cap, cz + dz, (d > 2.2 ? Blocks.BROWN_MUSHROOM_BLOCK : Blocks.RED_MUSHROOM_BLOCK).defaultBlockState());
				if (d < 2.2 && cap + 1 < base + height) plan.set(cx + dx, cap + 1, cz + dz, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState());
				if (d > 2.2 && d <= 3.2) plan.set(cx + dx, cap - 1, cz + dz, Blocks.SHROOMLIGHT.defaultBlockState());
			}
		}
	}

	private static void globe(FloorPlan plan, int cx, int cz, int base, int height) {
		plan.set(cx, base, cz, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
		plan.set(cx, base + 1, cz, Blocks.LODESTONE.defaultBlockState());
		int r = 2;
		int cy = base + 2 + r;
		if (cy + r >= base + height) cy = base + height - r - 1;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (d > r + 0.4 || d < r - 0.6) continue;
					plan.set(cx + dx, cy + dy, cz + dz, ((dx + dy + dz) % 2 == 0 ? Blocks.AMETHYST_BLOCK : Blocks.PURPLE_STAINED_GLASS).defaultBlockState());
				}
			}
		}
		plan.set(cx, cy, cz, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			plan.set(cx + direction.getStepX() * 2, base, cz + direction.getStepZ() * 2, Blocks.LIGHTNING_ROD.defaultBlockState());
		}
	}

	private static void pedestal(FloorPlan plan, int cx, int cz, int base, int height) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				plan.set(cx + dx, base, cz + dz, Blocks.GOLD_BLOCK.defaultBlockState());
			}
		}
		int column = Math.max(1, Math.min(5, height - 3));
		for (int y = 1; y <= column; y++) plan.set(cx, base + y, cz, Blocks.QUARTZ_PILLAR.defaultBlockState());
		plan.set(cx, base + column + 1, cz, Blocks.SEA_LANTERN.defaultBlockState());
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos at = new BlockPos(cx + direction.getStepX() * 2, base, cz + direction.getStepZ() * 2);
			plan.set(at, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		}
	}
}
