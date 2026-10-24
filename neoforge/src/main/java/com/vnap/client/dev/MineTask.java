package com.vnap.client.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;

/**
 * A tunnel dug straight ahead, as a player mines: the block at the head, then at the feet, then a step on.
 * {@code /vnapbot mine [seconds]} (survival, a pickaxe in hand).
 */
final class MineTask implements DevBot.Task {
	private final int limit;
	private final Direction ahead;
	private int ticks;
	private int walking;

	MineTask(DevBot bot, int seconds) {
		this.limit = seconds * 20;
		this.ahead = bot.player().getDirection();
	}

	@Override
	public boolean tick(DevBot bot) {
		if (bot.respawning()) return true;
		if (++ticks > limit) return false;
		var options = bot.mc().options;
		var level = bot.level();
		BlockPos feet = bot.player().blockPosition().relative(ahead);
		BlockPos head = feet.above();
		// the block to dig is chosen, not taken from under the crosshair: the head turning between them would
		// restart each one, and the game digs on a held button only while it holds the mouse (a hidden window never)
		@org.jspecify.annotations.Nullable BlockPos dig = !level.getBlockState(head).isAir() ? head : !level.getBlockState(feet).isAir() ? feet : null;
		bot.turn(ahead.toYRot(), feet.equals(dig) ? 40.0F : 8.0F);
		var gameMode = bot.mc().gameMode;
		if (dig != null && walking == 0 && gameMode != null) {
			options.keyUp.setDown(false);
			gameMode.continueDestroyBlock(dig, ahead.getOpposite());
			com.vnap.client.McClient.swing(bot.player(), InteractionHand.MAIN_HAND);
			return true;
		}
		// open ahead: a step into it
		if (walking == 0) walking = 5;
		walking--;
		options.keyUp.setDown(true);
		return true;
	}

	@Override
	public void stopped(DevBot bot) {
		bot.release();
	}
}
