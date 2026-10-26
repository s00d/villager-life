package com.vnap.neo.neoforge.event.entity.player;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class BonemealEvent extends Event {
	static {
		Bridge.bind(BonemealEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.entity.player.BonemealEvent.class, priority, BonemealEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.player.BonemealEvent f;

	BonemealEvent(net.minecraftforge.event.entity.player.BonemealEvent f) {
		super(f);
		this.f = f;
	}

	public @Nullable Player getPlayer() {
		return f.getEntity();
	}

	public Level getLevel() {
		return f.getLevel();
	}

	public BlockPos getPos() {
		return f.getPos();
	}

	public BlockState getState() {
		return f.getBlock();
	}

	public ItemStack getStack() {
		return f.getStack();
	}

	/** Whether bone meal does anything here, as vanilla asks the block. */
	public boolean isValidBonemealTarget() {
		//? if >=26.3 {
		/*// a player's bone meal (a dispenser's has no player): the hand's use
		return f.getBlock().getBlock() instanceof BonemealableBlock block && block.isValidBonemealTarget(f.getLevel(), f.getPos(), f.getBlock(),
				net.minecraft.world.level.block.BonemealSource.INTERACTION);
		*///?} else
		return f.getBlock().getBlock() instanceof BonemealableBlock block && block.isValidBonemealTarget(f.getLevel(), f.getPos(), f.getBlock());
	}
}
