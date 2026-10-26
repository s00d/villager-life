package com.vnap.fab.neoforge.event.entity.player;

import com.vnap.fab.bus.api.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Bone meal applied to a block (com.vnap.fab.mixin.BonemealMixin). */
public final class BonemealEvent extends Event {
	private final @Nullable Player player;
	private final Level level;
	private final BlockPos pos;
	private final BlockState state;
	private final ItemStack stack;

	public BonemealEvent(@Nullable Player player, Level level, BlockPos pos, BlockState state, ItemStack stack) {
		this.player = player;
		this.level = level;
		this.pos = pos;
		this.state = state;
		this.stack = stack;
	}

	public @Nullable Player getPlayer() {
		return player;
	}

	public Level getLevel() {
		return level;
	}

	public BlockPos getPos() {
		return pos;
	}

	public BlockState getState() {
		return state;
	}

	public ItemStack getStack() {
		return stack;
	}

	/** Whether bone meal does anything here, as vanilla asks the block. */
	public boolean isValidBonemealTarget() {
		//? if >=26.3 {
		/*return state.getBlock() instanceof BonemealableBlock block && block.isValidBonemealTarget(level, pos, state,
				net.minecraft.world.level.block.BonemealSource.INTERACTION);
		*///?} else
		return state.getBlock() instanceof BonemealableBlock block && block.isValidBonemealTarget(level, pos, state);
	}
}
