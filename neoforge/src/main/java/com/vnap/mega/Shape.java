package com.vnap.mega;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A megastructure's plan, as GregTech and Modular Machinery draw theirs: layers from the bottom up, each a few rows
 * from the front back, each row a string across from the left. {@code @} is the controller (it faces the builder), and
 * the rest:
 * <ul>
 * <li>{@code F} — the Builders' frame, {@code C}/{@code P}/{@code N} — a part of the Colosseum, the Path, the Nameless;</li>
 * <li>{@code .} — open (air, or what the structure itself fills there), {@code ' '} — anything.</li>
 * </ul>
 */
public final class Shape {
	/** A drill head on a 5×5 bed, four crowned corners, a mast. */
	public static final Shape QUARRY = new Shape("quarry",
			new String[] {"FF@FF", "FFFFF", "FFPFF", "FFFFF", "FFFFF"},
			new String[] {"CFFFC", "F...F", "F...F", "F...F", "CFFFC"},
			new String[] {"     ", "     ", "  P  ", "     ", "     "});
	/** A frame four across and five high; the keystone goes into the controller. */
	public static final Shape GATE = new Shape("gate",
			new String[] {"F@NF"},
			new String[] {"F..F"},
			new String[] {"F..F"},
			new String[] {"F..F"},
			new String[] {"FPPF"});

	/** A furnace of the frame three each way, a Colosseum heart below and above its chamber, a Nameless flue. */
	public static final Shape SMELTER = new Shape("smelter",
			new String[] {"F@F", "FCF", "FFF"},
			new String[] {"FFF", "F.F", "FFF"},
			new String[] {"FNF", "FCF", "FFF"});
	/** A stepped pyramid five across, three Nameless parts round a Path heart, a Path at its tip. */
	public static final Shape CALM = new Shape("calm",
			new String[] {"FF@FF", "FFFFF", "FFFFF", "FFFFF", "FFFFF"},
			new String[] {"     ", " FNF ", " NPN ", " FFF ", "     "},
			new String[] {"     ", "     ", "  P  ", "     ", "     "});
	/** A ring seven across, three Path parts on its sides, pillars at its corners. */
	public static final Shape DOME = new Shape("dome",
			new String[] {"FFF@FFF", "F     F", "F     F", "P     P", "F     F", "F     F", "FFFPFFF"},
			new String[] {"F     F", "       ", "       ", "       ", "       ", "       ", "F     F"});
	/** A well five across: its rim of the frame, a Colosseum stone at its bottom, a Nameless one at its back. */
	public static final Shape WELL = new Shape("well",
			new String[] {"FF@FF", "F...F", "F.C.F", "F...F", "FFNFF"});
	/** A pump three each way: a Path impeller below its chamber, a Colosseum crown. */
	public static final Shape DRAIN = new Shape("drain",
			new String[] {"F@F", "FPF", "FFF"},
			new String[] {"FFF", "F.F", "FFF"},
			new String[] {" C ", "   ", "   "});

	/** A yard five across: four saw posts of the Colosseum at its corners, a Nameless stump in its middle. */
	public static final Shape SAWMILL = new Shape("sawmill",
			new String[] {"FF@FF", "F   F", "F N F", "F   F", "FFFFF"},
			new String[] {"C   C", "     ", "     ", "     ", "C   C"});

	public final String id;
	private final String[][] layers;
	private final int atX;
	private final int atY;
	private final int atZ;

	Shape(String id, String[]... layers) {
		this.id = id;
		this.layers = layers;
		int x = -1;
		int y = -1;
		int z = -1;
		for (int ly = 0; ly < layers.length; ly++) {
			for (int lz = 0; lz < layers[ly].length; lz++) {
				int found = layers[ly][lz].indexOf('@');
				if (found >= 0) {
					x = found;
					y = ly;
					z = lz;
				}
			}
		}
		if (x < 0) throw new IllegalArgumentException("a shape without its controller: " + id);
		atX = x;
		atY = y;
		atZ = z;
	}

	/** One cell of the plan, where it lies for a controller at {@code at} facing {@code facing}. */
	public record Cell(BlockPos pos, char what) {
	}

	/** Every cell but the anything ones, turned the controller's way. */
	public List<Cell> cells(BlockPos at, Direction facing) {
		Direction right = facing.getCounterClockWise();
		Direction back = facing.getOpposite();
		List<Cell> out = new ArrayList<>();
		for (int ly = 0; ly < layers.length; ly++) {
			for (int lz = 0; lz < layers[ly].length; lz++) {
				String row = layers[ly][lz];
				for (int lx = 0; lx < row.length(); lx++) {
					char what = row.charAt(lx);
					if (what == ' ') continue;
					BlockPos pos = at.relative(right, lx - atX).relative(back, lz - atZ).above(ly - atY);
					out.add(new Cell(pos, what));
				}
			}
		}
		return out;
	}

	/** The first cell that is not what the plan wants there, or null when the structure stands whole. */
	public @Nullable Cell wrong(BlockGetter level, BlockPos at, Direction facing) {
		for (Cell cell : cells(at, facing)) {
			if (!fits(level.getBlockState(cell.pos()), cell.what())) return cell;
		}
		return null;
	}

	/** The cells that are open in the plan ({@code .}): what a structure fills (a gate's field). */
	public List<BlockPos> open(BlockPos at, Direction facing) {
		List<BlockPos> out = new ArrayList<>();
		for (Cell cell : cells(at, facing)) if (cell.what() == '.') out.add(cell.pos());
		return out;
	}

	/** How many blocks of each kind the plan takes, for the book and the recipe viewers. */
	public java.util.Map<Character, Integer> bill() {
		java.util.Map<Character, Integer> out = new java.util.TreeMap<>();
		for (String[] layer : layers) for (String row : layer) for (char c : row.toCharArray()) if (c != ' ' && c != '.') out.merge(c, 1, Integer::sum);
		return out;
	}

	public static @Nullable Block block(char what) {
		return switch (what) {
			case 'F' -> MegaBlocks.FRAME;
			case 'C' -> MegaBlocks.PART_COLOSSEUM;
			case 'P' -> MegaBlocks.PART_PATH;
			case 'N' -> MegaBlocks.PART_NAMELESS;
			default -> null;
		};
	}

	private static boolean fits(BlockState state, char what) {
		return switch (what) {
			case '@' -> state.getBlock() instanceof MegaControllerBlock;
			case '.' -> state.isAir() || state.is(MegaBlocks.GATE_FIELD) || state.canBeReplaced();
			default -> {
				Block want = block(what);
				yield want != null && state.is(want);
			}
		};
	}

	/** A plan by its id ("quarry", "gate"). */
	public static Shape of(String id) {
		return MegaKind.valueOf(id.toUpperCase(java.util.Locale.ROOT)).shape();
	}

	/** The plan as a structure to draw (the book): its blocks, the controller facing the reader. */
	public com.vnap.village.script.SchematicSpec spec() {
		List<Cell> cells = cells(BlockPos.ZERO, Direction.SOUTH);
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (Cell cell : cells) {
			minX = Math.min(minX, cell.pos().getX());
			minY = Math.min(minY, cell.pos().getY());
			minZ = Math.min(minZ, cell.pos().getZ());
			maxX = Math.max(maxX, cell.pos().getX());
			maxY = Math.max(maxY, cell.pos().getY());
			maxZ = Math.max(maxZ, cell.pos().getZ());
		}
		List<com.vnap.village.script.SchematicSpec.PlacedBlock> blocks = new ArrayList<>();
		for (Cell cell : cells) {
			Block block = cell.what() == '@' ? MegaBlocks.controller(MegaKind.valueOf(id.toUpperCase(java.util.Locale.ROOT))) : block(cell.what());
			if (block == null) continue;
			BlockState state = block instanceof MegaControllerBlock ? block.defaultBlockState()
					.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH).setValue(MegaControllerBlock.FORMED, true)
					: block.defaultBlockState();
			blocks.add(new com.vnap.village.script.SchematicSpec.PlacedBlock(cell.pos().offset(-minX, -minY, -minZ), state));
		}
		return new com.vnap.village.script.SchematicSpec(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, List.of(), blocks, BlockPos.ZERO);
	}

	/** The cells of the plan's layers as they are written, for the book's picture (x across, y up, z back). */
	public String[][] layers() {
		return layers;
	}
}
