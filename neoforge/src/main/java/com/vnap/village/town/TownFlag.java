package com.vnap.village.town;

import com.vnap.dialogue.Speech;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Placements;
import com.vnap.mood.VillageMoodSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The town's flag: a player draws it at the notice board (a ground colour and up to three patterns, NoticeBoardScreen),
 * the villagers hang it by hand (Placements) beside the town hall's door, on the gates under their crossbows and by
 * the notice board. A new flag repaints the banners already up. Kept as one int (TownFlag code): the ground colour in
 * the low four bits, then per layer a pattern (0: none) and its colour, four bits each.
 */
public final class TownFlag {
	/** The patterns a flag may have, in the board's order (index + 1 in the code). */
	public static final List<ResourceKey<BannerPattern>> PATTERNS = List.of(BannerPatterns.STRIPE_TOP, BannerPatterns.STRIPE_BOTTOM,
			BannerPatterns.STRIPE_CENTER, BannerPatterns.STRIPE_MIDDLE, BannerPatterns.CROSS, BannerPatterns.STRAIGHT_CROSS,
			BannerPatterns.HALF_HORIZONTAL, BannerPatterns.HALF_VERTICAL, BannerPatterns.BORDER, BannerPatterns.CIRCLE_MIDDLE,
			BannerPatterns.RHOMBUS_MIDDLE, BannerPatterns.TRIANGLE_BOTTOM, BannerPatterns.GRADIENT, BannerPatterns.FLOWER, BannerPatterns.CURLY_BORDER);
	public static final int LAYERS = 3;
	/** The marker a Placements job carries: the banner set down is the town's flag. */
	public static final String MARK = "#flag";
	private static final Map<DyeColor, Block> WALL = new EnumMap<>(DyeColor.class);

	private TownFlag() {
	}

	// ---- the code -------------------------------------------------------------------------------

	public static int code(DyeColor base, int[] patterns, DyeColor[] colours) {
		int code = base.getId();
		for (int i = 0; i < LAYERS; i++) code |= (patterns[i] & 0xF) << (4 + i * 8) | (colours[i].getId() & 0xF) << (8 + i * 8);
		return code;
	}

	public static DyeColor base(int code) {
		return DyeColor.byId(code & 0xF);
	}

	/** Layer {@code i}'s pattern (0: none, else PATTERNS index + 1). */
	public static int pattern(int code, int i) {
		return code >> (4 + i * 8) & 0xF;
	}

	public static DyeColor colour(int code, int i) {
		return DyeColor.byId(code >> (8 + i * 8) & 0xF);
	}

	public static BannerPatternLayers layers(HolderLookup.Provider registries, int code) {
		BannerPatternLayers.Builder layers = new BannerPatternLayers.Builder();
		HolderLookup.RegistryLookup<BannerPattern> lookup = registries.lookupOrThrow(Registries.BANNER_PATTERN);
		for (int i = 0; i < LAYERS; i++) {
			int pattern = pattern(code, i);
			if (pattern <= 0 || pattern > PATTERNS.size()) continue;
			DyeColor colour = colour(code, i);
			lookup.get(PATTERNS.get(pattern - 1)).ifPresent(holder -> layers.add(holder, colour));
		}
		return layers.build();
	}

	/** The flag as a banner in the hand (the board's picture of it). */
	public static ItemStack item(HolderLookup.Provider registries, int code) {
		ItemStack banner = new ItemStack(com.vnap.Mc.banner(base(code)).asItem());
		banner.set(DataComponents.BANNER_PATTERNS, layers(registries, code));
		return banner;
	}

	private static Block wall(DyeColor colour) {
		if (WALL.isEmpty()) {
			for (Block block : BuiltInRegistries.BLOCK) if (block instanceof WallBannerBlock banner) WALL.put(banner.getColor(), block);
		}
		return WALL.getOrDefault(colour, net.minecraft.world.level.block.Blocks.WHITE_WALL_BANNER);
	}

	// ---- in the world ---------------------------------------------------------------------------

	/** The banner on {@code pos} in the town's colours now (its ground colour is its block, its patterns its block entity). */
	public static void paint(ServerLevel level, BlockPos pos, int code) {
		BlockState state = level.getBlockState(pos);
		BlockState wanted = null;
		if (state.getBlock() instanceof WallBannerBlock) {
			wanted = wall(base(code)).defaultBlockState().setValue(WallBannerBlock.FACING, state.getValue(WallBannerBlock.FACING));
		} else if (state.getBlock() instanceof BannerBlock) {
			wanted = com.vnap.Mc.banner(base(code)).defaultBlockState().setValue(BannerBlock.ROTATION, state.getValue(BannerBlock.ROTATION));
		}
		if (wanted == null) return;
		if (!wanted.is(state.getBlock())) level.setBlock(pos, wanted, Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof BannerBlockEntity banner) {
			banner.applyComponentsFromItemStack(item(level.registryAccess(), code));
			banner.setChanged();
			level.sendBlockUpdated(pos, wanted, wanted, Block.UPDATE_CLIENTS);
		}
	}

	/** A Placements job set a banner down: it is the town's flag. */
	public static void hung(ServerLevel level, BlockPos pos) {
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), pos);
		if (network != null && network.flag >= 0) paint(level, pos, network.flag);
	}

	/** A player raised a new flag over the town: the banners up are repainted, the rest are hung. */
	public static void raise(ServerLevel level, Roads.Network network, int code) {
		if (network.flag < 0) Chronicle.note(level, network.bell, "flag");
		network.flag = code;
		for (BlockPos banner : network.banners) if (level.isLoaded(banner)) paint(level, banner, code);
		plan(level, network);
		Roads.get(level.getServer()).setDirty();
		Villager mayor = Mayors.mayorNear(level, network.bell);
		if (mayor != null) Speech.queue(mayor, "Mayor Raises the Town Flag", null, 20L);
	}

	/** Daily (Roads.plan): banners missing from the town's places are bought and left to be hung by hand. */
	static void plan(ServerLevel level, Roads.Network network) {
		if (network.flag < 0) return;
		network.banners.removeIf(pos -> level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof BannerBlock
				|| level.getBlockState(pos).getBlock() instanceof WallBannerBlock) && !Placements.pending(level, pos));
		for (Spot spot : spots(level, network)) {
			if (network.banners.contains(spot.pos) || !level.getBlockState(spot.pos).canBeReplaced()) continue;
			network.banners.add(spot.pos.immutable());
			com.vnap.village.market.Market.order(level, null, spot.pos, com.vnap.Mc.banner(base(network.flag)).asItem());
			Placements.add(level, spot.pos, spot.state, null, MARK);
		}
		Roads.get(level.getServer()).setDirty();
	}

	record Spot(BlockPos pos, BlockState state) {
	}

	/** Where the town hangs its flag: beside the hall's door, outside its gates' crossbows, by its notice board. */
	static List<Spot> spots(ServerLevel level, Roads.Network network) {
		List<Spot> out = new ArrayList<>();
		DyeColor base = base(network.flag);
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.done || !Mayors.HALLS.contains(house.plan) || !house.bell.closerThan(network.bell, TownRules.SPAN)) continue;
			for (Facade.Opening door : Facade.doors(level, house)) {
				for (Direction side : new Direction[]{door.out().getClockWise(), door.out().getCounterClockWise()}) {
					BlockPos wallAt = door.pos().relative(side).above(2);
					BlockPos at = wallAt.relative(door.out());
					if (level.getBlockState(wallAt).isFaceSturdy(level, wallAt, door.out()) && level.getBlockState(at.below()).canBeReplaced()) {
						out.add(new Spot(at, wall(base).defaultBlockState().setValue(WallBannerBlock.FACING, door.out())));
					}
				}
				break;
			}
		}
		for (BlockPos turret : network.turrets) {
			BlockState state = level.getBlockState(turret);
			if (!state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) continue;
			Direction face = state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
			BlockPos at = turret.below().relative(face);
			if (level.getBlockState(turret.below()).isFaceSturdy(level, turret.below(), face)) {
				out.add(new Spot(at, wall(base).defaultBlockState().setValue(WallBannerBlock.FACING, face)));
			}
		}
		BlockPos board = NoticeBoard.board(level, network);
		if (board != null) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos at = board.relative(side);
				// never on the road (a board at the square's edge may stand where a street comes in)
				if (!level.getBlockState(at).canBeReplaced() || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)
						|| network.cells.contains(at.below())) continue;
				Direction toBell = Direction.getNearest(network.bell.getX() - at.getX(), 0, network.bell.getZ() - at.getZ());
				out.add(new Spot(at, com.vnap.Mc.banner(base).defaultBlockState().setValue(BannerBlock.ROTATION, RotationSegment.convertToSegment(toBell))));
				break;
			}
		}
		return out;
	}
}
