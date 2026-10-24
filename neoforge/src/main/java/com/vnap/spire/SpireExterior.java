package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * What a spire looks like from outside, as a function of its seed and how far it has grown (DESIGN §2), after
 * Bruegel's Tower of Babel: a tower the Builders never stopped building. Eight-sided tiers, each a block narrower (it stays broad: a hundred floors make it four times as high as wide),
 * walled with rows of round arches (dark recesses, a sconce of the Core burning in some); a ramp winding round the
 * outside one turn a tier, on corbels, railed; turrets with pointed roofs hung off the top of each finished tier;
 * the lower tiers older (more moss and cracks) than the upper. Over the top, scaffolding and a crane still at work,
 * and the cut where the Core's rift lies with a beam of its light standing out of its heart, until the hundredth floor crowns it with a lantern room and a pointed roof.
 * Coordinates are from the plaza's middle at ground level; the gate faces north (-Z) before the spire is turned
 * toward its town.
 */
public final class SpireExterior {
	/** Height of the hall and first tier as it sprouts; each floor opened adds three quarters of a block above it. */
	public static final int BASE = 24;
	public static final int FULL = 100;
	public static final int PLAZA = 13;
	static final int SHAFT = 6;
	/** How far round the middle anything of it ever stands, and how high over the top: the guarded box. */
	public static final int REACH = 14;
	public static final int CROWN = 16;
	/** A tier's height, and the ramp's rise in one turn round it. */
	private static final int TIER = 16;
	private static final int HALL = 8;
	/** Where the ramp starts at the foot (just east of the gate), as an angle round the middle (atan2 of z, x). */
	private static final double RAMP_START = -Math.PI / 2 + 0.7;

	/** A spire's masonry: its stone with two worn kinds, trim, carved pillars, steps, slabs, parapet, roof, windows, the arches' dark. */
	record Palette(Block stone, Block cracked, Block mossy, Block trim, Block pillar, StairBlock stair, SlabBlock slab, Block wall,
			Block roof, StairBlock roofStair, Block glass, Block dark) {
	}

	private static final Palette[] PALETTES = {
			new Palette(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.CHISELED_STONE_BRICKS,
					(StairBlock) Blocks.STONE_BRICK_STAIRS, (SlabBlock) Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL,
					Blocks.DEEPSLATE_TILES, (StairBlock) Blocks.DEEPSLATE_TILE_STAIRS, Blocks.TINTED_GLASS, Blocks.DEEPSLATE_BRICKS),
			new Palette(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.CHISELED_DEEPSLATE,
					(StairBlock) Blocks.DEEPSLATE_BRICK_STAIRS, (SlabBlock) Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_WALL,
					Blocks.DARK_PRISMARINE, (StairBlock) Blocks.DARK_PRISMARINE_STAIRS, Blocks.TINTED_GLASS, Blocks.POLISHED_BLACKSTONE),
			new Palette(Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE,
					(StairBlock) Blocks.SANDSTONE_STAIRS, (SlabBlock) Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_WALL,
					Blocks.SMOOTH_RED_SANDSTONE, (StairBlock) Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.BROWN_STAINED_GLASS, Blocks.BROWN_TERRACOTTA),
			new Palette(Blocks.TUFF_BRICKS, Blocks.TUFF, Blocks.CHISELED_TUFF_BRICKS, Blocks.POLISHED_TUFF, Blocks.CHISELED_TUFF,
					(StairBlock) Blocks.TUFF_BRICK_STAIRS, (SlabBlock) Blocks.TUFF_BRICK_SLAB, Blocks.TUFF_BRICK_WALL,
					Blocks.DARK_PRISMARINE, (StairBlock) Blocks.DARK_PRISMARINE_STAIRS, Blocks.TINTED_GLASS, Blocks.DEEPSLATE_TILES),
			new Palette(Blocks.BRICKS, Blocks.BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.CHISELED_STONE_BRICKS,
					(StairBlock) Blocks.BRICK_STAIRS, (SlabBlock) Blocks.BRICK_SLAB, Blocks.BRICK_WALL,
					Blocks.DEEPSLATE_TILES, (StairBlock) Blocks.DEEPSLATE_TILE_STAIRS, Blocks.TINTED_GLASS, Blocks.POLISHED_DEEPSLATE),
			new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE,
					Blocks.CHISELED_POLISHED_BLACKSTONE, (StairBlock) Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, (SlabBlock) Blocks.POLISHED_BLACKSTONE_BRICK_SLAB,
					Blocks.POLISHED_BLACKSTONE_BRICK_WALL, Blocks.DARK_PRISMARINE, (StairBlock) Blocks.DARK_PRISMARINE_STAIRS, Blocks.TINTED_GLASS,
					Blocks.BLACKSTONE),
	};

	private SpireExterior() {
	}

	/** The height of the shaft once {@code floor} is open. */
	public static int height(int floor) {
		return BASE + (int) Math.round(0.75 * Math.min(Math.max(floor, 1), FULL));
	}

	/** Half the tower's width at height {@code y} (the octagon's inner radius): broad at the foot, a block narrower every tier, never a pole. */
	static double radius(int y) {
		return Math.max(SHAFT - 0.5, SHAFT + 3.5 - Math.floorDiv(Math.max(0, y - 1), TIER));
	}

	/** Distance from the middle in the octagon's own measure: within {@code r} is inside a tower of half-width r. */
	static double octagon(int x, int z) {
		return Math.max(Math.max(Math.abs(x), Math.abs(z)), (Math.abs(x) + Math.abs(z)) * 0.7071);
	}

	/** On one of the eight corners of a tower of half-width {@code r}, where a pilaster stands. */
	static boolean corner(int x, int z, double r) {
		double diagonal = (Math.abs(x) + Math.abs(z)) * 0.7071;
		return diagonal > r - 1.0 && Math.max(Math.abs(x), Math.abs(z)) > r - 1.0;
	}

	/** How far round the ramp a direction is, 0 at its start to 1 a turn later. */
	private static double turn(double angle) {
		double f = (angle - RAMP_START) / (Math.PI * 2);
		return f - Math.floor(f);
	}

	/** Every block of the spire grown to {@code floor}, by offset from its middle; air where it clears its inside. */
	public static Map<BlockPos, BlockState> shape(long seed, int floor) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		Palette palette = PALETTES[(int) Math.floorMod(seed, (long) PALETTES.length)];
		int top = height(floor);
		plaza(out, palette);
		tower(out, palette, seed, top);
		ramp(out, palette, top);
		turrets(out, palette, top);
		hall(out, palette);
		if (floor >= FULL) {
			crown(out, palette, top);
		} else {
			scaffolding(out, palette, top);
			rift(out, new Random(FloorGenerator.mix(seed, floor)), seed, top);
		}
		return out;
	}

	private static BlockState masonry(Palette palette, long seed, int x, int y, int z) {
		int roll = (int) Math.floorMod(FloorGenerator.mix(seed, BlockPos.asLong(x, y, z)), 100L);
		// the lower tiers are older: grey with moss and cracks, the newest stone at the top
		int age = Math.max(0, 4 - (y - 1) / TIER);
		if (roll < 3 + age * 5) return palette.mossy().defaultBlockState();
		if (roll > 92 - age * 3) return palette.cracked().defaultBlockState();
		return palette.stone().defaultBlockState();
	}

	private static void plaza(Map<BlockPos, BlockState> out, Palette palette) {
		for (int x = -PLAZA; x <= PLAZA; x++) {
			for (int z = -PLAZA; z <= PLAZA; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > PLAZA + 0.5) continue;
				// rings of trim among the paving, the edge kerbed
				boolean kerb = d > PLAZA - 1;
				boolean ring = Math.round(d) % 3 == 0;
				out.put(new BlockPos(x, 0, z), (kerb || ring ? palette.trim() : palette.stone()).defaultBlockState());
				out.put(new BlockPos(x, -1, z), palette.stone().defaultBlockState());
				for (int y = 1; y <= 3 && octagon(x, z) > radius(1); y++) out.put(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
			}
		}
		// four lamp posts on the plaza's edge, clear of the ramp's foot
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 4 + i * Math.PI / 2;
			int x = (int) Math.round(Math.cos(angle) * (PLAZA - 1));
			int z = (int) Math.round(Math.sin(angle) * (PLAZA - 1));
			out.put(new BlockPos(x, 1, z), palette.trim().defaultBlockState());
			out.put(new BlockPos(x, 2, z), palette.wall().defaultBlockState());
			out.put(new BlockPos(x, 3, z), palette.wall().defaultBlockState());
			out.put(new BlockPos(x, 4, z), Blocks.LANTERN.defaultBlockState());
		}
	}

	/** The tiers: an eight-sided shell, a course of trim at each tier's foot and top, a ledge where it steps in, rows of arches. */
	private static void tower(Map<BlockPos, BlockState> out, Palette palette, long seed, int top) {
		for (int y = 1; y <= top; y++) {
			double r = radius(y);
			int inTier = (y - 1) % TIER;
			boolean ledge = y > 1 && radius(y - 1) > r;
			boolean course = inTier == 0 || inTier == TIER - 1 || y == top;
			int reach = (int) Math.ceil(radius(y - 1));
			for (int x = -reach; x <= reach; x++) {
				for (int z = -reach; z <= reach; z++) {
					double d = octagon(x, z);
					BlockPos pos = new BlockPos(x, y, z);
					if (d <= r - 1.0) {
						out.put(pos, Blocks.AIR.defaultBlockState());
					} else if (d <= r) {
						out.put(pos, course ? palette.trim().defaultBlockState() : corner(x, z, r) ? palette.pillar().defaultBlockState() : masonry(palette, seed, x, y, z));
					} else if (ledge && d <= radius(y - 1)) {
						out.put(pos, palette.slab().defaultBlockState());
					}
				}
			}
		}
		arcades(out, palette, top);
	}

	/**
	 * Two rows of round arches round each tier: openings three high between the pilasters, an arch's lip over each,
	 * a dark wall a block back, and in every third the Core's light in a sconce. None over the gate.
	 */
	private static void arcades(Map<BlockPos, BlockState> out, Palette palette, int top) {
		int lit = 0;
		for (int base = 1; base < top; base += TIER) {
			for (int row : new int[] {4, 10}) {
				int y = base + row;
				if (y + 4 > top - 2) continue;
				double r = radius(y);
				int arches = r >= 8 ? 20 : r >= 6.5 ? 16 : r >= 5 ? 12 : 8;
				int reach = (int) Math.ceil(r);
				for (int x = -reach; x <= reach; x++) {
					for (int z = -reach; z <= reach; z++) {
						double d = octagon(x, z);
						if (d > r || d <= r - 1.0 || corner(x, z, r)) continue;
						if (y < HALL + 2 && z < 0 && Math.abs(x) <= 3) continue;
						double slot = (Math.atan2(z, x) + Math.PI) / (Math.PI * 2) * arches;
						int index = (int) Math.floor(slot);
						if ((index & 1) == 1 || Math.abs(slot - index - 0.5) > 0.3) continue;
						Direction outward = Direction.getNearest(x, 0, z);
						BlockPos opening = new BlockPos(x, y, z);
						BlockPos back = opening.relative(outward.getOpposite());
						for (int up = 0; up < 3; up++) {
							out.put(opening.above(up), Blocks.AIR.defaultBlockState());
							out.put(back.above(up), palette.dark().defaultBlockState());
						}
						out.put(opening.above(3), palette.stair().defaultBlockState().setValue(StairBlock.FACING, outward.getOpposite())
								.setValue(StairBlock.HALF, Half.TOP));
						out.put(opening.below(), palette.slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
						if (lit++ % 3 == 0) out.put(opening.above(1), sconce(outward));
					}
				}
			}
		}
	}

	/**
	 * The ramp: two blocks wide, round the outside from the plaza, a turn every tier; slabs stepping up half a block at
	 * a time, a parapet on its outer side, corbels under its inner side, head room over it. It stops short of the cut.
	 */
	private static void ramp(Map<BlockPos, BlockState> out, Palette palette, int top) {
		int reach = (int) Math.ceil(radius(1) + 2);
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				double f = turn(Math.atan2(z, x));
				double d = octagon(x, z);
				for (int k = 0; ; k++) {
					double rise = 1 + (k + f) * TIER;
					if (rise > top - 6) break;
					int halves = (int) Math.round(rise * 2);
					int y = halves / 2;
					boolean upper = (halves & 1) == 1;
					double r = radius(y);
					if (d <= r || d > r + 2) continue;
					BlockPos at = new BlockPos(x, y, z);
					out.put(at, palette.slab().defaultBlockState().setValue(SlabBlock.TYPE, upper ? SlabType.TOP : SlabType.BOTTOM));
					for (int up = 1; up <= 3; up++) out.put(at.above(up), Blocks.AIR.defaultBlockState());
					if (d > r + 1) {
						out.put(at.above(), palette.wall().defaultBlockState());
					} else if (y > 1) {
						Direction toward = Direction.getNearest(x, 0, z);
						out.put(at.below(), palette.stair().defaultBlockState().setValue(StairBlock.FACING, toward.getOpposite()).setValue(StairBlock.HALF, Half.TOP));
					}
				}
			}
		}
	}

	/** Three turrets hung off the top of each finished tier, where the ramp is far below or above them. */
	private static void turrets(Map<BlockPos, BlockState> out, Palette palette, int top) {
		for (int base = 1; base + TIER + 6 < top; base += TIER) {
			int y = base + TIER - 2;
			double r = radius(y);
			for (double f : new double[] {0.2, 0.31, 0.42}) {
				double angle = RAMP_START + f * Math.PI * 2 + base * 0.13;
				int cx = (int) Math.round(Math.cos(angle) * (r + 1.5));
				int cz = (int) Math.round(Math.sin(angle) * (r + 1.5));
				Direction outward = Direction.getNearest(cx, 0, cz);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						boolean edge = dx != 0 || dz != 0;
						boolean cornerCell = dx != 0 && dz != 0;
						for (int up = -2; up <= 1; up++) {
							if (cornerCell) continue;
							out.put(new BlockPos(cx + dx, y + up, cz + dz), palette.stone().defaultBlockState());
						}
						out.put(new BlockPos(cx + dx, y + 2, cz + dz), palette.trim().defaultBlockState());
						BlockPos roof = new BlockPos(cx + dx, y + 3, cz + dz);
						out.put(roof, edge ? palette.roofStair().defaultBlockState().setValue(StairBlock.FACING, Direction.getNearest(-dx, 0, -dz))
								: palette.roof().defaultBlockState());
					}
				}
				out.put(new BlockPos(cx, y - 3, cz), palette.stair().defaultBlockState().setValue(StairBlock.FACING, outward.getOpposite()).setValue(StairBlock.HALF, Half.TOP));
				out.put(new BlockPos(cx, y - 4, cz), palette.wall().defaultBlockState());
				BlockPos window = new BlockPos(cx, y, cz).relative(outward);
				out.put(window, palette.glass().defaultBlockState());
				out.put(new BlockPos(cx, y + 4, cz), palette.roof().defaultBlockState());
				out.put(new BlockPos(cx, y + 5, cz), Blocks.LIGHTNING_ROD.defaultBlockState());
			}
		}
	}

	/**
	 * The work still going on at the top: four posts of timber round the last blocks, a walk of planks between them a
	 * little under the cut, and on one post a crane with a block of the spire's stone hanging from its jib.
	 */
	private static void scaffolding(Map<BlockPos, BlockState> out, Palette palette, int top) {
		double r = radius(top);
		BlockState post = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
		int walk = top - 4;
		int reach = (int) Math.ceil(r + 2);
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				double d = octagon(x, z);
				if (d > r && d <= r + 1.5) out.put(new BlockPos(x, walk, z), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
			}
		}
		for (int i = 0; i < 4; i++) {
			double angle = RAMP_START + 0.4 + i * Math.PI / 2;
			int px = (int) Math.round(Math.cos(angle) * (r + 1.5));
			int pz = (int) Math.round(Math.sin(angle) * (r + 1.5));
			int height = i == 0 ? top + 6 : top + 1;
			for (int y = walk - 4; y <= height; y++) out.put(new BlockPos(px, y, pz), post);
			if (i != 0) continue;
			// the crane's jib out from the mast, a chain down from its end, a stone on it
			Direction outward = Direction.getNearest(px, 0, pz);
			BlockPos jib = new BlockPos(px, top + 6, pz);
			for (int step = 1; step <= 4; step++) out.put(jib.relative(outward, step), Blocks.SPRUCE_FENCE.defaultBlockState());
			out.put(jib.above(), Blocks.SPRUCE_FENCE.defaultBlockState());
			BlockPos end = jib.relative(outward, 4);
			for (int down = 1; down <= 3; down++) out.put(end.below(down), Blocks.CHAIN.defaultBlockState());
			out.put(end.below(4), palette.stone().defaultBlockState());
		}
	}

	/** The hall at the foot: a gate arch on the north, the portal across from it, a ceiling over it. */
	private static void hall(Map<BlockPos, BlockState> out, Palette palette) {
		int wall = (int) Math.floor(radius(1));
		int inside = wall - 1;
		for (int x = -2; x <= 2; x++) {
			for (int y = 1; y <= 6; y++) {
				for (int z = -wall - 1; z <= -wall + 2; z++) {
					boolean jamb = Math.abs(x) == 2 || y == 6;
					out.put(new BlockPos(x, y, z), jamb ? palette.trim().defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
			// the arch's shoulders
			for (int z = -wall - 1; z <= -wall + 2; z++) {
				if (Math.abs(x) == 1) out.put(new BlockPos(x, 5, z), palette.stair().defaultBlockState()
						.setValue(StairBlock.FACING, x < 0 ? Direction.WEST : Direction.EAST).setValue(StairBlock.HALF, Half.TOP));
			}
		}
		// the hall's floor and ceiling
		for (int x = -wall; x <= wall; x++) {
			for (int z = -wall; z <= wall; z++) {
				double d = octagon(x, z);
				if (d <= radius(1) - 1.0) {
					// the Builders' runes in a ring round the middle
					out.put(new BlockPos(x, 0, z), Math.round(d) == 3 ? SpireBlocks.RUNE.defaultBlockState()
							: ((x + z) & 1) == 0 ? palette.trim().defaultBlockState() : palette.stone().defaultBlockState());
				}
				if (d <= radius(HALL) - 1.0) out.put(new BlockPos(x, HALL, z), palette.trim().defaultBlockState());
			}
		}
		for (int y = HALL - 1; y >= HALL - 3; y--) out.put(new BlockPos(0, y, 0), Blocks.CHAIN.defaultBlockState());
		out.put(new BlockPos(0, HALL - 4, 0), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		for (int x = -1; x <= 1; x++) {
			for (int y = 0; y <= 4; y++) {
				boolean frame = Math.abs(x) == 1 || y == 0 || y == 4;
				BlockPos pos = new BlockPos(x, y + 1, SHAFT - 2);
				out.put(pos, frame ? palette.trim().defaultBlockState() : gate());
			}
		}
		out.put(new BlockPos(0, 1, SHAFT - 2), palette.trim().defaultBlockState());
		// the Core Anvil by the wall, braziers by the portal, sconces in and out of the gate, tapestries on the sides
		out.put(new BlockPos(3, 1, 1), SpireBlocks.CORE_ANVIL.defaultBlockState());
		out.put(new BlockPos(-2, 1, SHAFT - 3), SpireBlocks.BRAZIER.defaultBlockState());
		out.put(new BlockPos(2, 1, SHAFT - 3), SpireBlocks.BRAZIER.defaultBlockState());
		out.put(new BlockPos(-3, 4, -inside), sconce(Direction.SOUTH));
		out.put(new BlockPos(3, 4, -inside), sconce(Direction.SOUTH));
		out.put(new BlockPos(-3, 5, -wall - 1), sconce(Direction.NORTH));
		out.put(new BlockPos(3, 5, -wall - 1), sconce(Direction.NORTH));
		// the Outer Gates (outer/Outer): the Colosseum's in the west wall, the Sky Path's in the east, the Nameless's in
		// the south behind the spire's own portal; framed in their parts, open to who climbed past the hundredth floor
		outerGate(out, palette, new BlockPos(-inside, 1, 0), Direction.Axis.Z, com.vnap.spire.outer.OuterGateBlock.Kind.ARENA,
				com.vnap.mega.MegaBlocks.PART_COLOSSEUM);
		outerGate(out, palette, new BlockPos(inside, 1, 0), Direction.Axis.Z, com.vnap.spire.outer.OuterGateBlock.Kind.ISLES,
				com.vnap.mega.MegaBlocks.PART_PATH);
		outerGate(out, palette, new BlockPos(0, 1, inside), Direction.Axis.X, com.vnap.spire.outer.OuterGateBlock.Kind.RUSH,
				com.vnap.mega.MegaBlocks.PART_NAMELESS);
		out.put(new BlockPos(-inside, 6, 3), tapestry(Direction.EAST));
		out.put(new BlockPos(inside, 6, -3), tapestry(Direction.WEST));
	}

	/** An Outer Gate's frame, three across and five high round its portal, its corners of its gate's part. */
	private static void outerGate(Map<BlockPos, BlockState> out, Palette palette, BlockPos base, Direction.Axis axis,
			com.vnap.spire.outer.OuterGateBlock.Kind kind, net.minecraft.world.level.block.Block part) {
		for (int a = -1; a <= 1; a++) {
			for (int y = 0; y <= 4; y++) {
				BlockPos pos = axis == Direction.Axis.X ? base.offset(a, y, 0) : base.offset(0, y, a);
				boolean corner = Math.abs(a) == 1 && (y == 0 || y == 4);
				boolean edge = Math.abs(a) == 1 || y == 0 || y == 4;
				out.put(pos, corner ? part.defaultBlockState() : edge ? palette.trim().defaultBlockState()
						: com.vnap.spire.outer.OuterGateBlock.of(kind, axis));
			}
		}
	}

	private static BlockState sconce(Direction facing) {
		return SpireBlocks.SCONCE.defaultBlockState().setValue(SpireFixtureBlock.FACING, facing);
	}

	private static BlockState tapestry(Direction facing) {
		return SpireBlocks.TAPESTRY.defaultBlockState().setValue(SpireFixtureBlock.FACING, facing);
	}

	private static BlockState gate() {
		return SpireBlocks.PORTAL.defaultBlockState().setValue(SpirePortalBlock.KIND, SpirePortalBlock.Kind.GATE).setValue(SpirePortalBlock.LIT, true);
	}

	/** Where the gate's portal is and where one coming out stands, by offset from the middle (before turning). */
	public static BlockPos gateOffset() {
		return new BlockPos(0, 2, SHAFT - 2);
	}

	public static BlockPos frontOffset() {
		return new BlockPos(0, 1, -SHAFT - 6);
	}

	private static void rift(Map<BlockPos, BlockState> out, Random random, long seed, int top) {
		double r = radius(top);
		int reach = (int) Math.ceil(r);
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				double d = octagon(x, z);
				if (d > r) continue;
				out.put(new BlockPos(x, top + 1, z), SpireBlocks.RIFT.defaultBlockState());
				if (d <= r - 1.0) continue;
				int worn = (int) Math.floorMod(FloorGenerator.mix(seed ^ top, BlockPos.asLong(x, 0, z)), 6L);
				for (int down = 0; down < Math.min(worn, 3) - 1; down++) out.put(new BlockPos(x, top - down, z), Blocks.AIR.defaultBlockState());
			}
		}
		// its heart: the beam rises from here (client/spire/SpireRiftRenderer)
		out.put(new BlockPos(0, top + 1, 0), SpireBlocks.RIFT.defaultBlockState().setValue(SpireRiftBlock.HEART, true));
		for (int i = 0; i < 5; i++) {
			double angle = i * Math.PI * 2 / 5 + random.nextDouble() * 0.6;
			double d = random.nextDouble() * (r + 2);
			int y = top + 3 + random.nextInt(4);
			out.put(new BlockPos((int) Math.round(Math.cos(angle) * d), y, (int) Math.round(Math.sin(angle) * d)), SpireBlocks.SHARD.defaultBlockState());
		}
	}

	/** The whole spire's crown: a lantern room of glass between the pilasters, a pointed roof, the beacon inside, the beam over its tip. */
	private static void crown(Map<BlockPos, BlockState> out, Palette palette, int top) {
		double r = radius(top);
		int reach = (int) Math.ceil(r + 1);
		for (int y = 1; y <= 5; y++) {
			for (int x = -reach; x <= reach; x++) {
				for (int z = -reach; z <= reach; z++) {
					double d = octagon(x, z);
					if (d > r) continue;
					BlockPos pos = new BlockPos(x, top + y, z);
					boolean shell = d > r - 1.0;
					if (y == 1 || y == 5) out.put(pos, palette.trim().defaultBlockState());
					else if (shell) out.put(pos, (corner(x, z, r) ? palette.pillar() : palette.glass()).defaultBlockState());
					else out.put(pos, Blocks.AIR.defaultBlockState());
				}
			}
		}
		out.put(new BlockPos(0, top + 2, 0), Blocks.BEACON.defaultBlockState());
		out.put(new BlockPos(0, top + 3, 0), SpireBlocks.SHARD.defaultBlockState());
		// the roof: rings of steps closing in over the lantern, a spike at its tip
		int y = top + 6;
		for (double ring = r + 1.0; ring > 0.5; ring -= 1.0, y++) {
			int rr = (int) Math.ceil(ring);
			for (int x = -rr; x <= rr; x++) {
				for (int z = -rr; z <= rr; z++) {
					double d = octagon(x, z);
					if (d > ring || d <= ring - 1.0) continue;
					Direction inward = Direction.getNearest(-x, 0, -z);
					out.put(new BlockPos(x, y, z), palette.roofStair().defaultBlockState().setValue(StairBlock.FACING, inward));
				}
			}
		}
		out.put(new BlockPos(0, y, 0), palette.roof().defaultBlockState());
		// the Core's light on the tip, its beam standing over the finished spire (client/spire/SpireRiftRenderer)
		out.put(new BlockPos(0, y + 1, 0), SpireBlocks.RIFT.defaultBlockState().setValue(SpireRiftBlock.HEART, true));
	}
}
