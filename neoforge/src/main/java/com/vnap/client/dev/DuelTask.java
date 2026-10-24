package com.vnap.client.dev;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/**
 * A fight as a player films one: the nearest enemy, the head turned to it smoothly, a walk straight at it, a slow
 * circle once close, a blow each time the swing has charged. No jumps, no way-finding, no backing off: the climbing
 * bot hops, reroutes and retreats, and seen through its eyes a fight jerked back and forth. For staged shots in an
 * open room: {@code /vnapbot duel [seconds]}.
 */
final class DuelTask implements DevBot.Task {
	private static final double REACH = 2.8;
	private final int limit;
	private int ticks;
	private int flip;
	private boolean left;
	private @Nullable Entity target;

	DuelTask(int seconds) {
		this.limit = seconds * 20;
	}

	@Override
	public boolean tick(DevBot bot) {
		if (bot.respawning()) return true;
		if (++ticks > limit) return false;
		var options = bot.mc().options;
		Entity current = target;
		if (current == null || !current.isAlive() || ticks % 40 == 0) {
			current = bot.level().getEntitiesOfClass(LivingEntity.class, bot.player().getBoundingBox().inflate(24.0),
							e -> e instanceof Enemy && e.isAlive() && !e.isInvisible() && bot.player().hasLineOfSight(e))
					.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(bot.player()))).orElse(null);
			target = current;
		}
		if (current == null) {
			bot.release();
			return true;
		}
		bot.look(current.getBoundingBox().getCenter().add(0, current.getBbHeight() * 0.15, 0));
		double distance = bot.player().distanceTo(current) - current.getBbWidth() / 2;
		boolean close = distance < REACH;
		options.keyJump.setDown(false);
		options.keySprint.setDown(false);
		options.keyDown.setDown(false);
		options.keyUp.setDown(!close);
		// a slow step round it, one way then the other: a player's dance, not a jitter
		// against a wall or a pillar the step turns the other way at once, not when the 80 ticks are up
		if (ticks - flip >= 80 || (close && bot.player().horizontalCollision && ticks - flip > 10)) {
			left = !left;
			flip = ticks;
		}
		options.keyLeft.setDown(close && left);
		options.keyRight.setDown(close && !left);
		if (distance < REACH + 0.6 && bot.player().getAttackStrengthScale(0.0F) >= 0.95F) {
			var gameMode = bot.mc().gameMode;
			if (gameMode != null) gameMode.attack(bot.player(), current);
			com.vnap.client.McClient.swing(bot.player(), InteractionHand.MAIN_HAND);
		}
		return true;
	}

	@Override
	public void stopped(DevBot bot) {
		bot.release();
	}
}
