package com.vnap.village.market;

import com.vnap.village.build.BuildJob;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.WorkshopState;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The farmer's farm (its workshop, plans {@code workshop/farmer_t1..3}): a field round a water channel and trees
 * along the back, the composter and the chest in the work corner. The farmer harvests and sows the field, tends the
 * saplings, fells a grown tree whole (its logs and its own leaves, nothing else, no drops) and plants it again; the
 * logs and the produce it does not keep go to the stall ({@link Produce}): the village's planks are its trees.
 * Nothing outside the plan's cells is ever cut or dug.
 */
public final class Farm {
	/** A tree is felled within this box around its spot: wide enough for an oak's crown, never further. */
	private static final int CROWN = 3;
	private static final int TALL = 14;
	private static final int MAX_BLOCKS = 160;
	/** Saplings a felled tree gives back. */
	private static final int SAPLINGS_BACK = 2;

	private Farm() {
	}

	/** The plan the farm stands by now, in the world. */
	public record Plot(SchematicSpec spec, BlockPos origin, Rotation rotation) {
	}

	/** The farmer's farm at its current tier, or null (another trade, or nothing built yet). */
	public static @Nullable Plot of(ServerLevel level, WorkshopState state) {
		if (!state.profession.equals("farmer") || state.tier <= 0) return null;
		return new Plot(Workshops.schematic(level, Workshops.schematicName(state.profession, state.tier)), state.origin, state.rotation);
	}

	/** Where the trees grow: the cells the plan sets a sapling in. */
	public static List<BlockPos> treeSpots(Plot plot) {
		List<BlockPos> out = new ArrayList<>();
		for (SchematicSpec.PlacedBlock block : plot.spec().blocks) {
			if (block.state().is(BlockTags.SAPLINGS)) out.add(BuildJob.at(plot.origin(), plot.rotation(), block.rel()));
		}
		return out;
	}

	/** Where the crops grow, and what grows there by plan. */
	public static Map<BlockPos, BlockState> cropCells(Plot plot) {
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		for (SchematicSpec.PlacedBlock block : plot.spec().blocks) {
			if (block.state().getBlock() instanceof net.minecraft.world.level.block.CropBlock) {
				out.put(BuildJob.at(plot.origin(), plot.rotation(), block.rel()), block.state());
			}
		}
		return out;
	}

	/** A grown tree stands on the spot (its trunk where the sapling was). */
	public static boolean grown(ServerLevel level, BlockPos spot) {
		return level.isLoaded(spot) && level.getBlockState(spot).is(BlockTags.LOGS);
	}

	/** The plan's sapling at this spot, as an item (biome palettes plant their own trees). */
	public static Item saplingOf(Plot plot, BlockPos spot) {
		for (SchematicSpec.PlacedBlock block : plot.spec().blocks) {
			if (BuildJob.at(plot.origin(), plot.rotation(), block.rel()).equals(spot)) return block.state().getBlock().asItem();
		}
		return Items.OAK_SAPLING;
	}

	/**
	 * Fells the tree on the spot: every log joined to its trunk and the tree's own leaves (not a player's, never
	 * outside the box over the spot, never in a chunk that is not loaded), no drops; the logs and two saplings go into
	 * the store. Returns how many logs it gave.
	 */
	public static int fell(ServerLevel level, Plot plot, BlockPos spot, Container store) {
		if (!grown(level, spot)) return 0;
		Item log = level.getBlockState(spot).getBlock().asItem();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(spot));
		List<BlockPos> logs = new ArrayList<>();
		List<BlockPos> leaves = new ArrayList<>();
		while (!queue.isEmpty() && seen.size() < MAX_BLOCKS) {
			BlockPos pos = queue.poll();
			if (!seen.add(pos) || Math.abs(pos.getX() - spot.getX()) > CROWN || Math.abs(pos.getZ() - spot.getZ()) > CROWN
					|| pos.getY() < spot.getY() || pos.getY() > spot.getY() + TALL || !level.isLoaded(pos)) continue;
			BlockState state = level.getBlockState(pos);
			boolean isLog = state.is(BlockTags.LOGS);
			boolean isLeaf = state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT);
			if (!isLog && !isLeaf) continue;
			(isLog ? logs : leaves).add(pos);
			for (BlockPos next : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) queue.add(next.immutable());
		}
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
		for (BlockPos pos : leaves) level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
		for (BlockPos pos : logs) {
			level.levelEvent(2001, pos, Block.getId(level.getBlockState(pos)));
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
		}
		put(store, new ItemStack(log, logs.size()));
		put(store, new ItemStack(saplingOf(plot, spot), SAPLINGS_BACK));
		return logs.size();
	}

	/** Plants the spot's sapling from the store; false without one or with the spot not clear. */
	public static boolean plant(ServerLevel level, Plot plot, BlockPos spot, Container store) {
		Item sapling = saplingOf(plot, spot);
		if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir() || !level.getBlockState(spot.below()).is(BlockTags.DIRT)
				|| !(sapling instanceof net.minecraft.world.item.BlockItem block) || !takeOne(store, sapling)) return false;
		level.setBlock(spot, block.getBlock().defaultBlockState(), Block.UPDATE_ALL);
		return true;
	}

	/** A day's care for a sapling: one step of its growth (two make a tree). */
	public static boolean tend(ServerLevel level, BlockPos spot) {
		if (!level.isLoaded(spot)) return false;
		BlockState state = level.getBlockState(spot);
		if (!(state.getBlock() instanceof SaplingBlock sapling)) return false;
		sapling.advanceTree(level, spot, state, level.getRandom());
		return true;
	}

	static void put(Container store, ItemStack stack) {
		// stack by stack: a container cuts a bigger stack down to its size (a tall tree's 70 logs lost 6)
		for (int left = stack.getCount(); left > 0; ) {
			int part = Math.min(left, stack.getMaxStackSize());
			left -= part;
			HopperBlockEntity.addItem(null, store, stack.copyWithCount(part), null);
		}
	}

	private static boolean takeOne(Container store, Item item) {
		return take(store, item, 1) == 1;
	}

	static int take(Container store, Item item, int count) {
		int taken = 0;
		for (int i = 0; i < store.getContainerSize() && taken < count; i++) {
			ItemStack stack = store.getItem(i);
			if (!stack.is(item)) continue;
			int n = Math.min(count - taken, stack.getCount());
			stack.shrink(n);
			taken += n;
		}
		store.setChanged();
		return taken;
	}
}
