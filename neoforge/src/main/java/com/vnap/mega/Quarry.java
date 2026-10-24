package com.vnap.mega;

import com.vnap.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Builders' Quarry (Immersive Engineering's Excavator and BuildCraft's quarry the model): a 32×32 square under the
 * structure, dug layer by layer from the top down to the bedrock, a block every {@link #EVERY} ticks; what it digs
 * goes into a container beside the controller (it waits while they are full), its fuel comes from one (coal and
 * charcoal 64 blocks, a block of coal 576, a shard of the Core 256). It leaves the world's water and lava where they
 * are: a block with a fluid beside it is replaced with glass, not dug into a flood.
 */
public final class Quarry {
	/** Half the side of the square it digs. */
	static final int HALF = 16;
	/** Ticks between two blocks dug. */
	static final int EVERY = 2;
	/** Cells looked at a tick at most for the next block (air and the unbreakable are passed over quickly). */
	private static final int LOOKS = 256;

	private Quarry() {
	}

	/** Where it is in the dig: the layer and the cell of it, the fuel left, done at the bedrock. */
	public static final class Dig {
		/** Half the side of its square (a test digs a small one). */
		public int half = HALF;
		int layer = Integer.MIN_VALUE;
		int cell;
		int fuel;
		boolean done;
		public long dug;
		long heard;
		/** A new layer begun: the clients are told (the head is drawn there). */
		boolean moved;

		void save(CompoundTag tag) {
			tag.putInt("Layer", layer);
			tag.putInt("Cell", cell);
			tag.putInt("Fuel", fuel);
			tag.putBoolean("Done", done);
			tag.putLong("Dug", dug);
		}

		void load(CompoundTag tag) {
			layer = tag.contains("Layer") ? Nbt.getInt(tag, "Layer") : Integer.MIN_VALUE;
			cell = Nbt.getInt(tag, "Cell");
			fuel = Nbt.getInt(tag, "Fuel");
			done = Nbt.getBoolean(tag, "Done");
			dug = Nbt.getLong(tag, "Dug");
		}
	}

	/** The middle of the square: the middle of the structure's bed. */
	static BlockPos centre(MegaControllerBlockEntity quarry) {
		return quarry.getBlockPos().relative(quarry.facing().getOpposite(), 2);
	}

	static void tick(ServerLevel level, MegaControllerBlockEntity quarry) {
		Dig dig = quarry.dig;
		if (dig.done || level.getGameTime() % EVERY != 0) return;
		BlockPos centre = centre(quarry);
		if (dig.layer == Integer.MIN_VALUE) {
			dig.layer = quarry.getBlockPos().getY() - 1;
			quarry.shown();
		}
		for (int look = 0; look < LOOKS; look++) {
			if (dig.layer <= level.getMinBuildHeight()) {
				dig.done = true;
				com.vnap.progress.VillageAdvancements.awardNear(level, quarry.getBlockPos(), 48, "mega/quarry");
				level.playSound(null, quarry.getBlockPos(), com.vnap.sound.SpireSounds.QUARRY_DONE, SoundSource.BLOCKS, 1.2F, 1.0F);
				quarry.setChanged();
				return;
			}
			int side = dig.half * 2;
			BlockPos at = new BlockPos(centre.getX() - dig.half + dig.cell % side, dig.layer, centre.getZ() - dig.half + dig.cell / side);
			if (!level.isLoaded(at)) return;
			BlockState state = level.getBlockState(at);
			if (!worthDigging(level, at, state)) {
				next(dig);
				continue;
			}
			if (dig.fuel <= 0 && !refuel(level, quarry)) return;
			List<ItemStack> drops = Block.getDrops(state, level, at, level.getBlockEntity(at), null, new ItemStack(Items.NETHERITE_PICKAXE));
			if (!store(level, quarry, drops)) return;
			boolean wet = false;
			for (Direction direction : Direction.values()) if (!level.getFluidState(at.relative(direction)).isEmpty()) wet = true;
			level.setBlock(at, wet ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
			if (dig.dug % 8 == 0) level.levelEvent(2001, at, Block.getId(state));
			// its grinding, one take at a time while it digs
			if (level.getGameTime() - dig.heard >= 58) {
				dig.heard = level.getGameTime();
				level.playSound(null, quarry.getBlockPos(), com.vnap.sound.SpireSounds.QUARRY_WORK, SoundSource.BLOCKS, 0.8F, 1.0F);
			}
			dig.fuel--;
			dig.dug++;
			next(dig);
			if (dig.moved) {
				dig.moved = false;
				quarry.shown();
			} else {
				quarry.setChanged();
			}
			return;
		}
	}

	private static void next(Dig dig) {
		dig.cell++;
		if (dig.cell >= dig.half * 2 * dig.half * 2) {
			dig.cell = 0;
			dig.layer--;
			dig.moved = true;
		}
	}

	/** Air, fluids, the bedrock, what holds things (a chest in a cave, a spawner) and megastructures are left. */
	static boolean worthDigging(ServerLevel level, BlockPos at, BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, at) < 0 || state.hasBlockEntity()) return false;
		return !(state.is(MegaBlocks.FRAME) || state.is(Blocks.GLASS) || MegaBlocks.blocks().containsValue(state.getBlock()));
	}

	/** One fuel item out of a container beside the controller. */
	private static boolean refuel(ServerLevel level, MegaControllerBlockEntity quarry) {
		for (Container container : containers(level, quarry)) {
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				int worth = worth(container.getItem(slot));
				if (worth > 0) {
					container.removeItem(slot, 1);
					container.setChanged();
					quarry.dig.fuel += worth;
					return true;
				}
			}
		}
		return false;
	}

	static int worth(ItemStack stack) {
		if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return 64;
		if (stack.is(Items.COAL_BLOCK)) return 576;
		if (stack.is(com.vnap.item.LifeItems.CORE_SHARD)) return 256;
		return 0;
	}

	/** All of the drops into the containers beside it, or nothing (and false: it waits). */
	private static boolean store(ServerLevel level, MegaControllerBlockEntity quarry, List<ItemStack> drops) {
		List<Container> containers = containers(level, quarry);
		// a dry run first: a half-stored block would lose the rest (the chests' empty slots, else the town's stall)
		int room = 0;
		for (Container container : containers) for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).isEmpty()) room++;
		boolean town = TownWork.stall(level, quarry) != null;
		if (room < drops.size() && !town) return false;
		for (ItemStack drop : drops) {
			ItemStack left = drop.copy();
			for (Container container : containers) {
				left = HopperBlockEntity.addItem(null, container, left, null);
				if (left.isEmpty()) break;
			}
			// what the chests do not take, the town's stall does (stone for its roads and walls), paid to the owner
			if (!left.isEmpty()) TownWork.deliver(level, quarry, left);
		}
		return true;
	}

	private static List<Container> containers(ServerLevel level, MegaControllerBlockEntity quarry) {
		List<Container> out = new java.util.ArrayList<>();
		for (Direction direction : Direction.values()) {
			if (level.getBlockEntity(quarry.getBlockPos().relative(direction)) instanceof Container container) out.add(container);
		}
		return out;
	}

	static void tell(ServerLevel level, MegaControllerBlockEntity quarry, Player player) {
		Dig dig = quarry.dig;
		if (dig.done) {
			com.vnap.Mc.say(player, Component.translatable("mega.villager_life.quarry.done", dig.dug), false);
		} else if (containers(level, quarry).isEmpty() && TownWork.stall(level, quarry) == null) {
			com.vnap.Mc.say(player, Component.translatable("mega.villager_life.quarry.no_chest"), false);
		} else {
			int layer = dig.layer == Integer.MIN_VALUE ? quarry.getBlockPos().getY() - 1 : dig.layer;
			com.vnap.Mc.say(player, Component.translatable("mega.villager_life.quarry.digging", layer, dig.dug, dig.fuel), false);
		}
	}
}
