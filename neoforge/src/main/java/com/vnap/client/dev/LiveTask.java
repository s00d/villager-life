package com.vnap.client.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A visitor living in a town as a player does ({@code /vnapbot live [days]}): by day it goes round the town's places
 * again and again (TourTask); from dusk, and in a thunderstorm, it goes to its own bed — set down from its pockets on
 * level ground a little off the bell the first evening — and sleeps the night away (a climber in the spire's own
 * dimension does not hold the night up: sleep is counted world by world). Every day, every bed set down, every night
 * slept and how long it took goes into devbot.log.
 */
final class LiveTask implements DevBot.Task {
	private static final int BED_TRIES = 12;
	private final int days;
	private long firstDay = -1;
	private long lastDay = -1;
	private @Nullable TourTask tour;
	private int rest;
	private @Nullable BlockPos bell;
	private @Nullable BlockPos bed;
	private int bedTries;
	private int useWait;
	private long asleepAt = -1;
	private int nights;
	private int ticks;

	LiveTask(DevBot bot, int days) {
		this.days = days;
		bot.log("live: " + days + " days in the nearest town");
		bot.command("gamemode survival");
		if (!bot.player().getInventory().hasAnyMatching(stack -> stack.is(net.minecraft.tags.ItemTags.BEDS))) bot.command("give @s red_bed 1");
	}

	@Override
	public boolean tick(DevBot bot) {
		if (bot.respawning()) return true;
		ticks++;
		Level level = bot.level();
		long day = com.vnap.Mc.dayTime(level) / 24000L;
		long time = com.vnap.Mc.dayTime(level) % 24000L;
		if (firstDay < 0) firstDay = day;
		if (day != lastDay) {
			lastDay = day;
			bot.log("live: day " + (day - firstDay + 1) + " (world day " + day + ")");
			if (day - firstDay >= days) {
				bot.log("live: done, " + nights + " nights slept");
				return false;
			}
		}
		boolean night = (time >= 12500L && time < 23400L) || level.isThundering();
		var player = bot.player();
		if (player.isSleeping()) {
			if (asleepAt < 0) {
				asleepAt = System.currentTimeMillis();
				bot.log("  asleep in the bed at " + (bed == null ? "?" : bed.toShortString()) + ", time " + time);
			}
			bot.release();
			return true;
		}
		// hungry: something from the pockets, as a player eats when the bar runs down
		if (player.getFoodData().getFoodLevel() < 14 && eat(bot)) return true;
		if (asleepAt >= 0) {
			nights++;
			bot.log("  woke at time " + time + ", the night gone in " + (System.currentTimeMillis() - asleepAt) / 1000 + " s");
			asleepAt = -1;
		}
		if (night) {
			if (tour != null) {
				tour.stopped(bot);
				tour = null;
				bot.closeScreen();
			}
			toBed(bot, time);
			return true;
		}
		bedTries = 0;
		if (tour == null) {
			if (rest-- > 0) {
				bot.release();
				return true;
			}
			tour = new TourTask(bot);
		}
		if (!tour.tick(bot)) {
			if (tour.bell != null) bell = tour.bell;
			tour.stopped(bot);
			tour = null;
			rest = 600;
		} else if (bell == null && tour.bell != null) {
			bell = tour.bell;
		}
		return true;
	}

	private int eating;

	/** Holds use with food in hand till it is eaten; false when there is nothing to eat. */
	private boolean eat(DevBot bot) {
		var player = bot.player();
		var inventory = player.getInventory();
		if (com.vnap.client.McClient.selected(inventory).get(net.minecraft.core.component.DataComponents.FOOD) == null) {
			int slot = -1;
			for (int i = 0; i < 9; i++) if (inventory.getItem(i).get(net.minecraft.core.component.DataComponents.FOOD) != null) slot = i;
			if (slot < 0) {
				eating = 0;
				return false;
			}
			com.vnap.client.McClient.select(inventory, slot);
			// the server holds its own idea of the slot in hand: told, as the hotbar keys tell it
			player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(slot));
		}
		bot.closeScreen();
		bot.release();
		bot.mc().options.keyUse.setDown(true);
		// started by hand: a held key starts nothing while the window is hidden; held, it keeps the eating going
		var gameMode = bot.mc().gameMode;
		if (!player.isUsingItem() && gameMode != null) gameMode.useItem(player, InteractionHand.MAIN_HAND);
		if (++eating == 1) bot.log("  eating " + com.vnap.client.McClient.selected(inventory).getHoverName().getString() + ", food " + player.getFoodData().getFoodLevel());
		if (eating > 60) {
			bot.mc().options.keyUse.setDown(false);
			eating = 0;
		}
		return true;
	}

	/** To the bed and into it; the first evening the bed goes down by the bell. */
	private void toBed(DevBot bot, long time) {
		Level level = bot.level();
		var player = bot.player();
		BlockPos home = bed;
		if ((home == null || !(level.getBlockState(home).getBlock() instanceof BedBlock)) && bell != null) {
			// its bed of an earlier evening (or before a restart): a bed under the open sky by the bell is the visitor's
			home = bed = ownBed(level, bell);
		}
		if (home == null || !(level.getBlockState(home).getBlock() instanceof BedBlock)) {
			BlockPos spot = bell == null ? null : spot(level, bell);
			if (spot == null) {
				if (ticks % 400 == 0) bot.log("  no bed and nowhere to set one down (bell " + (bell == null ? "not known yet" : bell.toShortString()) + ")");
				bot.release();
				return;
			}
			if (player.blockPosition().distSqr(spot) > 9) {
				if (!bot.walk(spot, cell -> cell.distSqr(spot) <= 4) && ticks % 400 == 0) bot.log("  no way to the bed's spot " + spot.toShortString());
				return;
			}
			bot.release();
			place(bot, spot);
			return;
		}
		BlockPos to = home;
		if (player.blockPosition().distSqr(to) > 4) {
			if (!bot.walk(to, cell -> cell.distSqr(to) <= 4) && ticks % 400 == 0) bot.log("  no way to the bed at " + to.toShortString());
			return;
		}
		bot.release();
		if (useWait-- > 0) return;
		useWait = 40;
		if (++bedTries > BED_TRIES) {
			if (bedTries == BED_TRIES + 1) bot.log("  the bed at " + home.toShortString() + " will not take it (time " + time + ")");
			return;
		}
		bot.use(home);
	}

	/** A bed out under the sky within the spot's search off the bell (the villagers' are all indoors): the visitor's own. */
	private static @Nullable BlockPos ownBed(Level level, BlockPos bell) {
		for (BlockPos at : BlockPos.betweenClosed(bell.offset(-15, -4, -15), bell.offset(15, 4, 15))) {
			if (level.getBlockState(at).getBlock() instanceof BedBlock && level.canSeeSky(at.above())) return at.immutable();
		}
		return null;
	}

	/** A bed's foot on level ground off the bell (two cells to lie in, open above), nearest first. */
	private static @Nullable BlockPos spot(Level level, BlockPos bell) {
		for (int r = 4; r <= 14; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					for (int dy = -3; dy <= 3; dy++) {
						BlockPos foot = bell.offset(dx, dy, dz);
						BlockPos head = foot.east();
						if (fits(level, foot) && fits(level, head)) return foot;
					}
				}
			}
		}
		return null;
	}

	private static boolean fits(Level level, BlockPos cell) {
		var below = level.getBlockState(cell.below());
		return level.getBlockState(cell).isAir() && level.getBlockState(cell.above()).isAir() && below.isFaceSturdy(level, cell.below(), Direction.UP)
				&& !below.is(Blocks.DIRT_PATH) && level.canSeeSky(cell);
	}

	/** The bed from the pockets onto the spot, its head to the east (the player faces east as it sets it down). */
	private void place(DevBot bot, BlockPos foot) {
		var player = bot.player();
		var inventory = player.getInventory();
		int slot = -1;
		for (int i = 0; i < 9; i++) if (inventory.getItem(i).is(net.minecraft.tags.ItemTags.BEDS)) slot = i;
		if (slot < 0) {
			bot.command("give @s red_bed 1");
			return;
		}
		com.vnap.client.McClient.select(inventory, slot);
		bot.look(Vec3.atCenterOf(foot).add(8, -1, 0));
		var gameMode = bot.mc().gameMode;
		if (gameMode == null) return;
		gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(foot.below()).add(0, 0.5, 0), Direction.UP, foot.below(), false));
		com.vnap.client.McClient.swing(player, InteractionHand.MAIN_HAND);
		// the server's answer comes a tick later: the next evening's look at the spot tells whether it stands
		bed = foot;
		bot.log("  set the bed down at " + foot.toShortString());
	}

	@Override
	public void stopped(DevBot bot) {
		if (tour != null) tour.stopped(bot);
		bot.release();
	}
}
