package com.vnap.village.town;

import com.vnap.mood.MoodConfig;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The square round the bell, laid like the roads (a town job, the treasury's money, the stall's stone): once the
 * village has a dozen people, a paved square with a lamp at each corner; once it is half full, a wider square with a
 * well, benches facing the bell and flowers along its edges. Paved over the ground as it lies (only where it is about
 * the bell's height), round anything that stands there.
 */
public final class Plaza {

	private Plaza() {
	}

	/**
	 * The square the town is due: 0 none yet, 1 paved with lamps, 2 with a well, benches and flowers, 3 (a town with its
	 * spire and the treasury for it) a fountain and the statue of its heroes.
	 */
	static int due(ServerLevel level, BlockPos bell) {
		int people = Towns.population(level, bell);
		int max = Towns.limit(level, bell);
		int due = people * 2 >= max ? 2 : people * 6 >= max ? 1 : 0;
		Mayors.Office office = Towns.of(level, bell);
		if (due == 2 && office != null && office.treasury >= TownRules.FOUNTAIN_PURSE && spire(level, bell)) due = 3;
		return due;
	}

	private static boolean spire(ServerLevel level, BlockPos bell) {
		return com.vnap.spire.Spires.get(level.getServer()).all().stream().anyMatch(spire -> spire.dimension.equals(level.dimension()) && spire.bell.closerThan(bell, TownRules.SPAN));
	}

	/** Lays out the square the town is due and has not got yet as a stretch to build; true when one was queued. */
	static boolean plan(ServerLevel level, Roads roads, Roads.Network network) {
		return plan(level, roads, network, due(level, network.bell));
	}

	/** {@code plan} for a given square (debug: /vnap roads square lays the next one whatever the town's size). */
	public static boolean plan(ServerLevel level, Roads roads, Roads.Network network, int due) {
		if (due <= network.plaza) return false;
		Mayors.Office office = Towns.of(level, network.bell);
		BlockPos bell = office == null ? network.bell : office.bell;
		if (!level.isPositionEntityTicking(bell)) return false;
		int ground = bell.getY() - 1;
		String palette = HousePalette.forBiome(level.getBiome(bell));
		int r = due >= 2 ? TownRules.SQUARE_LARGE : TownRules.SQUARE_SMALL;
		List<BoundingBox> blocked = new ArrayList<>(Roads.blocked(level, network));
		blocked.removeIf(box -> box.isInside(bell));
		List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
		BlockState edge = Roads.kerb(palette).defaultBlockState();
		BlockState accent = Roads.surface(1, palette);
		for (int ring = 1; ring <= r; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					BlockPos cell = flat(level, network, bell, dx, dz, ground, blocked);
					if (cell == null) continue;
					BlockState paving = ring == r ? edge : (dx + dz) % 2 == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : accent;
					network.cells.add(cell);
					if (!level.getBlockState(cell).is(paving.getBlock())) blocks.add(new SchematicSpec.PlacedBlock(cell, paving));
				}
			}
		}
		// a lamp at each corner
		for (int sx : new int[]{-r, r}) {
			for (int sz : new int[]{-r, r}) {
				BlockPos cell = flat(level, network, bell, sx, sz, ground, blocked);
				// a street lamp already lighting this corner is enough
				if (cell == null || network.lamps.stream().anyMatch(lamp -> !lamp.equals(cell) && lamp.closerThan(cell, TownRules.LAMP_GAP)) || !network.lamps.add(cell)) continue;
				blocks.addAll(Roads.lamp(cell, palette));
			}
		}
		// the notice board, once, on the square's edge facing the bell (one the mayor set up early is that one)
		if (network.plaza == 0 && NoticeBoard.board(level, network) == null && !boardPending(level, network)) board(level, network, bell, ground, blocked, r, blocks);
		if (due >= 2 && network.plaza < 2) {
			well(level, network, bell, ground, blocked, palette, blocks);
			benches(level, network, bell, ground, blocked, palette, r, blocks);
			flowers(level, network, bell, ground, blocked, r, blocks);
		}
		if (due >= 3) {
			Mayors.Office paying = Towns.of(level, bell);
			if (paying != null) paying.treasury -= TownRules.FOUNTAIN_PURSE;
			fountain(level, network, bell, ground, blocked, blocks);
			BlockPos plinth = statue(level, network, bell, ground, blocked);
			if (plinth != null) {
				// not paved over first: the plinth is set into the ground, as the well and the fountain are
				blocks.removeIf(block -> block.rel().equals(plinth));
				List<SchematicSpec.PlacedBlock> stand = new ArrayList<>();
				stand.add(new SchematicSpec.PlacedBlock(plinth, Blocks.AIR.defaultBlockState()));
				stand.add(new SchematicSpec.PlacedBlock(plinth, Blocks.CHISELED_STONE_BRICKS.defaultBlockState()));
				stand.add(new SchematicSpec.PlacedBlock(plinth.above(), Blocks.POLISHED_ANDESITE.defaultBlockState()));
				roads.queue(level, network, stand, STATUE);
			}
		}
		network.plaza = due;
		if (!blocks.isEmpty()) roads.queue(level, network, blocks, "square");
		roads.setDirty();
		return true;
	}

	/** The paving cell at this offset from the bell: firm ground about the bell's height, in a ticking chunk, free. */
	private static @Nullable BlockPos flat(ServerLevel level, Roads.Network network, BlockPos bell, int dx, int dz, int ground, List<BoundingBox> blocked) {
		RoadFinder.Cell cell = RoadFinder.surface(level, bell.getX() + dx, bell.getZ() + dz, network.cells::contains);
		if (cell == null || cell.water() || Math.abs(cell.pos().getY() - ground) > 1 || Land.inside(blocked, cell.pos())
				|| network.features.contains(cell.pos())) return null;
		return cell.pos().immutable();
	}

	/** The label of the statue's plinth: once laid, the hero stands on it (Roads.finish → Plaza.unveil). */
	public static final String STATUE = "statue";

	/**
	 * A fountain on a side of the square the well left free: a round curb of stone brick five across round a basin, a
	 * pillar in its middle with water running down it from the top.
	 */
	private static void fountain(ServerLevel level, Roads.Network network, BlockPos bell, int ground, List<BoundingBox> blocked, List<SchematicSpec.PlacedBlock> blocks) {
		for (int[] at : around(3, TownRules.SQUARE_LARGE + 2)) {
			List<BlockPos> cells = new ArrayList<>();
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					// round, not square: its corners are where the square's benches stand
					if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
					BlockPos cell = flat(level, network, bell, at[0] + dx, at[1] + dz, ground, blocked);
					// not over a bench, a pot or the well
					if (cell != null && cell.getY() == ground && !network.features.contains(cell.above())) cells.add(cell);
				}
			}
			if (cells.size() < 21) continue;
			BlockPos middle = new BlockPos(bell.getX() + at[0], ground, bell.getZ() + at[1]);
			blocks.removeIf(block -> cells.contains(block.rel()));
			// the stone first, all of it, and the water last: water laid among the curb ran out over the square before
			// the curb round it was up, and over the pillar's top before it was laid
			List<SchematicSpec.PlacedBlock> water = new ArrayList<>();
			for (BlockPos cell : cells) {
				int ring = Math.max(Math.abs(cell.getX() - middle.getX()), Math.abs(cell.getZ() - middle.getZ()));
				network.features.add(cell);
				network.cells.remove(cell);
				blocks.add(new SchematicSpec.PlacedBlock(cell, Blocks.AIR.defaultBlockState()));
				if (ring == 2) {
					blocks.add(new SchematicSpec.PlacedBlock(cell, Blocks.STONE_BRICKS.defaultBlockState()));
					blocks.add(new SchematicSpec.PlacedBlock(cell.above(), Blocks.SMOOTH_STONE_SLAB.defaultBlockState()));
					network.features.add(cell.above());
				} else if (ring == 0) {
					for (int h = 0; h < 3; h++) {
						blocks.add(new SchematicSpec.PlacedBlock(cell.above(h), h == 2 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICK_WALL.defaultBlockState()));
						network.features.add(cell.above(h));
					}
					// a bowl of water on top: a spout's water ran down before the stone under it was laid, and it never was
					blocks.add(new SchematicSpec.PlacedBlock(cell.above(3), Blocks.WATER_CAULDRON.defaultBlockState()
							.setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3)));
					network.features.add(cell.above(3));
				} else {
					blocks.add(new SchematicSpec.PlacedBlock(cell.below(), Blocks.STONE_BRICKS.defaultBlockState()));
					water.add(new SchematicSpec.PlacedBlock(cell, Blocks.WATER.defaultBlockState()));
				}
			}
			blocks.addAll(water);
			return;
		}
	}

	/**
	 * Offsets round the bell from ring {@code from} to {@code to}, nearest first: the fountain and the statue were looked
	 * for only three blocks off it, and by a village of the world's well there was never room (no fountain at all).
	 */
	private static List<int[]> around(int from, int to) {
		List<int[]> out = new ArrayList<>();
		for (int dx = -to; dx <= to; dx++) {
			for (int dz = -to; dz <= to; dz++) {
				int ring = Math.max(Math.abs(dx), Math.abs(dz));
				if (ring >= from && ring <= to) out.add(new int[]{dx, dz});
			}
		}
		out.sort(java.util.Comparator.comparingInt((int[] at) -> at[0] * at[0] + at[1] * at[1]));
		return out;
	}

	/** Where the statue of the town's heroes stands: a free cell of the square, nearest the bell. */
	private static @Nullable BlockPos statue(ServerLevel level, Roads.Network network, BlockPos bell, int ground, List<BoundingBox> blocked) {
		for (int[] at : around(2, TownRules.SQUARE_LARGE + 2)) {
			BlockPos cell = flat(level, network, bell, at[0], at[1], ground, blocked);
			if (cell == null || cell.getY() != ground || network.features.contains(cell) || network.features.contains(cell.above())) continue;
			network.features.add(cell);
			network.features.add(cell.above());
			network.features.add(cell.above(2));
			network.cells.remove(cell);
			return cell;
		}
		return null;
	}

	/**
	 * The plinth is laid: the town's hero stands on it in stone-still armour of the chapter the town's climbers have
	 * reached (an armour stand: Builders' plates and sword, no base plate, never moved).
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "BC_IMPOSSIBLE_CAST", justification = "ArmorStandAccessor is a mixin: the cast holds at run time")
	public static void unveil(ServerLevel level, BlockPos plinth) {
		BlockPos top = plinth.above(2);
		int floor = com.vnap.spire.Spires.get(level.getServer()).all().stream().filter(spire -> spire.bell.closerThan(plinth, TownRules.SPAN * 2))
				.mapToInt(spire -> Math.max(spire.best, spire.expeditionFloor)).max().orElse(1);
		com.vnap.spire.gear.GearTier tier = com.vnap.spire.Heroes.tier(floor);
		net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(level, top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
		for (com.vnap.spire.gear.GearPiece piece : new com.vnap.spire.gear.GearPiece[] {com.vnap.spire.gear.GearPiece.HELMET, com.vnap.spire.gear.GearPiece.CHESTPLATE,
				com.vnap.spire.gear.GearPiece.LEGGINGS, com.vnap.spire.gear.GearPiece.BOOTS, com.vnap.spire.gear.GearPiece.SWORD}) {
			net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(com.vnap.spire.gear.SpireGearItems.get(tier, piece));
			stand.setItemSlot(piece.armour() != null ? piece.armour() : net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack);
		}
		com.vnap.Mc.invulnerable(stand, true);
		stand.setNoGravity(true);
		stand.addTag("vnap_statue");
		((com.vnap.mixin.ArmorStandAccessor) stand).vnap$setShowArms(true);
		((com.vnap.mixin.ArmorStandAccessor) stand).vnap$setNoBasePlate(true);
		level.addFreshEntity(stand);
	}

	/** A village well east of the bell: a stone curb round water, posts at its corners and a little roof. */
	private static void well(ServerLevel level, Roads.Network network, BlockPos bell, int ground, List<BoundingBox> blocked, String palette,
							 List<SchematicSpec.PlacedBlock> blocks) {
		for (int[] at : new int[][]{{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
			List<BlockPos> cells = new ArrayList<>();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos cell = flat(level, network, bell, at[0] + dx, at[1] + dz, ground, blocked);
					if (cell != null && cell.getY() == ground) cells.add(cell);
				}
			}
			if (cells.size() < 9) continue;
			BlockState curb = Roads.palette(Blocks.COBBLESTONE.defaultBlockState(), palette);
			BlockState post = Roads.palette(Blocks.OAK_FENCE.defaultBlockState(), palette);
			BlockState roof = Roads.palette(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), palette);
			BlockPos middle = new BlockPos(bell.getX() + at[0], ground, bell.getZ() + at[1]);
			// the well stands instead of the paving there (stone laid first would keep the water out)
			blocks.removeIf(block -> cells.contains(block.rel()));
			for (BlockPos cell : cells) {
				boolean centre = cell.getX() == middle.getX() && cell.getZ() == middle.getZ();
				boolean corner = Math.abs(cell.getX() - middle.getX()) == 1 && Math.abs(cell.getZ() - middle.getZ()) == 1;
				network.features.add(cell);
				network.cells.remove(cell);
				// what is there (the first square's paving) is taken up first: laid stone is no ground to set things into
				blocks.add(new SchematicSpec.PlacedBlock(cell, Blocks.AIR.defaultBlockState()));
				if (centre) {
					blocks.add(new SchematicSpec.PlacedBlock(cell, Blocks.WATER.defaultBlockState()));
					continue;
				}
				blocks.add(new SchematicSpec.PlacedBlock(cell, curb));
				blocks.add(new SchematicSpec.PlacedBlock(cell.above(), curb));
				network.features.add(cell.above());
				if (corner) {
					blocks.add(new SchematicSpec.PlacedBlock(cell.above(2), post));
					network.features.add(cell.above(2));
				}
			}
			for (BlockPos cell : cells) {
				blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(cell.getX(), ground + 3, cell.getZ()), roof));
				network.features.add(new BlockPos(cell.getX(), ground + 3, cell.getZ()));
			}
			return;
		}
	}

	/**
	 * Daily (Roads.plan): a village with a mayor has its notice board from the start, not only once its square is
	 * laid (a village that never reached a sixth of its limit never had one: no errands, no lots, no flag). The
	 * treasury buys it, the mayor sets it by hand on the edge the small square will have.
	 */
	static void earlyBoard(ServerLevel level, Roads roads, Roads.Network network) {
		Mayors.Office office = Towns.of(level, network.bell);
		if (office == null || office.mayor == null || NoticeBoard.board(level, network) != null || boardPending(level, network)) return;
		BlockPos bell = office.bell;
		if (!level.isPositionEntityTicking(bell)) return;
		List<BoundingBox> blocked = new ArrayList<>(Roads.blocked(level, network));
		blocked.removeIf(box -> box.isInside(bell));
		List<SchematicSpec.PlacedBlock> spot = new ArrayList<>();
		board(level, network, bell, bell.getY() - 1, blocked, TownRules.SQUARE_SMALL, spot);
		if (spot.isEmpty()) return;
		SchematicSpec.PlacedBlock board = spot.get(0);
		com.vnap.village.market.Market.order(level, null, bell, com.vnap.block.LifeBlocks.NOTICE_BOARD.asItem());
		com.vnap.village.home.Placements.add(level, board.rel(), board.state(), office.mayor);
		roads.setDirty();
	}

	private static boolean boardPending(ServerLevel level, Roads.Network network) {
		for (BlockPos thing : network.features) {
			if (com.vnap.village.home.Placements.pending(level, thing)) return true;
		}
		return false;
	}

	/** The town's notice board (NoticeBoard) on the first edge with room, off the streets' way in, its notices toward the bell. */
	private static void board(ServerLevel level, Roads.Network network, BlockPos bell, int ground, List<BoundingBox> blocked, int r,
							  List<SchematicSpec.PlacedBlock> blocks) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			// two along the edge from its middle: the town's four streets come in at the middles (TownPlan)
			Direction along = side.getClockWise();
			BlockPos cell = flat(level, network, bell, side.getStepX() * r + along.getStepX() * 2, side.getStepZ() * r + along.getStepZ() * 2, ground, blocked);
			if (cell == null || !level.getBlockState(cell.above()).canBeReplaced()) continue;
			blocks.add(new SchematicSpec.PlacedBlock(cell.above(), com.vnap.block.LifeBlocks.NOTICE_BOARD.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, side.getOpposite())));
			network.features.add(cell.above());
			return;
		}
	}

	/** Benches facing the bell on each side, a little in from the edge. */
	private static void benches(ServerLevel level, Roads.Network network, BlockPos bell, int ground, List<BoundingBox> blocked, String palette, int r,
								List<SchematicSpec.PlacedBlock> blocks) {
		int d = r - 1;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			for (int along : new int[]{-2, 2}) {
				int dx = side.getStepX() * d + (side.getAxis() == Direction.Axis.Z ? along : 0);
				int dz = side.getStepZ() * d + (side.getAxis() == Direction.Axis.X ? along : 0);
				BlockPos cell = flat(level, network, bell, dx, dz, ground, blocked);
				if (cell == null || !level.getBlockState(cell.above()).canBeReplaced()) continue;
				// a stair seat faces the way one sits: its back to the edge, towards the bell
				BlockState seat = Roads.palette(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, side), palette);
				blocks.add(new SchematicSpec.PlacedBlock(cell.above(), seat));
				network.features.add(cell.above());
			}
		}
	}

	/** Pots of flowers along the edge, between the corners. */
	private static void flowers(ServerLevel level, Roads.Network network, BlockPos bell, int ground, List<BoundingBox> blocked, int r,
								List<SchematicSpec.PlacedBlock> blocks) {
		BlockState[] plants = {Blocks.POTTED_POPPY.defaultBlockState(), Blocks.POTTED_DANDELION.defaultBlockState(),
				Blocks.POTTED_BLUE_ORCHID.defaultBlockState(), Blocks.POTTED_OXEYE_DAISY.defaultBlockState()};
		int i = 0;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			for (int along : new int[]{-3, 0, 3}) {
				int dx = side.getStepX() * r + (side.getAxis() == Direction.Axis.Z ? along : 0);
				int dz = side.getStepZ() * r + (side.getAxis() == Direction.Axis.X ? along : 0);
				BlockPos cell = flat(level, network, bell, dx, dz, ground, blocked);
				if (cell == null || !level.getBlockState(cell.above()).canBeReplaced()) continue;
				blocks.add(new SchematicSpec.PlacedBlock(cell.above(), plants[i++ % plants.length]));
				network.features.add(cell.above());
			}
		}
	}

	/** Everything a square takes, in every palette: the stall keeps it (Market.building). */
	static List<SchematicSpec> kit() {
		List<SchematicSpec> out = new ArrayList<>();
		for (String palette : HousePalette.ALL) {
			List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
			int n = 0;
			int cells = (2 * TownRules.SQUARE_LARGE + 1) * (2 * TownRules.SQUARE_LARGE + 1);
			for (int i = 0; i < cells / 2; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.STONE_BRICKS.defaultBlockState()));
			for (int i = 0; i < cells / 2; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Roads.surface(1, palette)));
			for (int i = 0; i < 8 * TownRules.SQUARE_LARGE + 8; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Roads.kerb(palette).defaultBlockState()));
			for (int i = 0; i < 8 * TownRules.SQUARE_LARGE + 16; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Roads.palette(Blocks.COBBLESTONE.defaultBlockState(), palette)));
			for (int i = 0; i < 4; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Roads.palette(Blocks.OAK_FENCE.defaultBlockState(), palette)));
			blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), com.vnap.block.LifeBlocks.NOTICE_BOARD.defaultBlockState()));
			for (int i = 0; i < 9; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Roads.palette(Blocks.OAK_SLAB.defaultBlockState(), palette)));
			for (int i = 0; i < 8; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Roads.palette(Blocks.OAK_STAIRS.defaultBlockState(), palette)));
			for (BlockState pot : new BlockState[]{Blocks.POTTED_POPPY.defaultBlockState(), Blocks.POTTED_DANDELION.defaultBlockState(),
					Blocks.POTTED_BLUE_ORCHID.defaultBlockState(), Blocks.POTTED_OXEYE_DAISY.defaultBlockState()}) {
				for (int i = 0; i < 3; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), pot));
			}
			// the fountain and the statue's plinth
			for (int i = 0; i < 12 + 9; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.STONE_BRICKS.defaultBlockState()));
			for (int i = 0; i < 16; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.SMOOTH_STONE_SLAB.defaultBlockState()));
			for (int i = 0; i < 2; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.STONE_BRICK_WALL.defaultBlockState()));
			for (int i = 0; i < 2; i++) blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.CHISELED_STONE_BRICKS.defaultBlockState()));
			blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.WATER_CAULDRON.defaultBlockState()));
			blocks.add(new SchematicSpec.PlacedBlock(new BlockPos(n++, 0, 0), Blocks.POLISHED_ANDESITE.defaultBlockState()));
			out.add(new SchematicSpec(n, 1, 1, List.of(), blocks, BlockPos.ZERO));
		}
		return out;
	}
}
