package com.vnap.client.dev;

import com.vnap.network.SpireFloorPayload;
import com.vnap.spire.SpireBlocks;
import com.vnap.spire.guard.SpireGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An Outer Gate's run played as a player plays it ({@code /vnapbot outer <arena|isles|rush> [seconds]}): into the run
 * by the debug command, then the Colosseum's waves and the Nameless fought (the small ones first, out of the hits that
 * keep coming), or the Sky Path jumped from island to island toward the nearest waystone not yet stood on. Every stage
 * reached, death and fall goes into devbot.log; at the end, how far it came and in how long.
 */
final class OuterTask implements DevBot.Task {
	private final String kind;
	private final int limit;
	private int ticks;
	private boolean entered;
	private int lastStage;
	private int lastLives = 3;
	private int deaths;
	private long stageAt;
	private @Nullable SpireGuard focus;
	private final Set<BlockPos> stood = new HashSet<>();
	private @Nullable BlockPos landing;
	private @Nullable BlockPos goal;
	private int stuck;
	private int still;
	private int rounds;
	private int sidestep;
	private final Set<java.util.UUID> unreachable = new HashSet<>();

	OuterTask(DevBot bot, String kind, int seconds) {
		this.kind = kind;
		this.limit = seconds * 20;
		bot.log("outer " + kind + ": " + seconds + " s");
	}

	private int alive;

	@Override
	public boolean tick(DevBot bot) {
		if (++alive % 200 == 1) bot.log("  (tick " + alive + ": hud " + com.vnap.client.spire.SpireHud.current().theme() + " " + com.vnap.client.spire.SpireHud.current().floor()
				+ ", dead " + bot.player().isDeadOrDying() + ", screen " + com.vnap.client.McClient.screen() + ")");
		if (bot.respawning()) {
			if (entered) {
				deaths++;
				entered = false;
				bot.log("  died at stage " + lastStage);
			}
			return true;
		}
		SpireFloorPayload here = com.vnap.client.spire.SpireHud.current();
		boolean inRun = here.theme().startsWith("outer_");
		if (!inRun) {
			if (++ticks % 100 == 1) bot.command("vnap spire outer " + kind);
			return ticks < limit;
		}
		if (!entered) {
			entered = true;
			stageAt = ticks;
			bot.log("  in the run at stage " + here.floor());
		}
		ticks++;
		if (here.floor() != lastStage) {
			bot.log("  stage " + here.floor() + " after " + (ticks - stageAt) / 20 + " s");
			lastStage = here.floor();
			stageAt = ticks;
		}
		if (kind.equals("isles") && here.guards() != lastLives) {
			if (here.guards() < lastLives) bot.log("  fell, lives " + here.guards());
			lastLives = here.guards();
		}
		if (ticks >= limit) {
			bot.log("outer " + kind + ": reached stage " + lastStage + " in " + ticks / 20 + " s, deaths " + deaths);
			bot.command("vnap spire leave");
			return false;
		}
		if (kind.equals("isles")) {
			// a guard on its island fought first, then on
			SpireGuard near = nearest(bot, 6, unreachable);
			if (near != null) {
				fightOrClose(bot, near);
				return true;
			}
			hop(bot);
		} else {
			if (focus == null || !focus.isAlive() || focus.isRemoved()) focus = nearest(bot, 64, unreachable);
			// one at its back comes first, as a player turns on what bites it (a sentinel beat the bot to death while it
			// walked at a creeper ten blocks off)
			SpireGuard close = nearest(bot, 4, Set.of());
			if (close != null && bot.inReach(close) && (focus == null || !bot.inReach(focus))) focus = close;
			SpireGuard guard = focus;
			if (guard == null) {
				bot.release();
				return true;
			}
			fightOrClose(bot, guard);
		}
		return true;
	}

	private void fightOrClose(DevBot bot, SpireGuard guard) {
		if (sidestep > 0) {
			sidestep--;
			var options = bot.mc().options;
			options.keyLeft.setDown(rounds % 2 == 1);
			options.keyRight.setDown(rounds % 2 == 0);
			options.keyUp.setDown(sidestep > 10);
			options.keyJump.setDown(sidestep % 10 < 3);
			if (sidestep == 0) {
				bot.release();
				bot.forgetPath();
			}
			return;
		}
		String how;
		if (bot.inReach(guard)) {
			bot.fight(guard);
			how = "fight";
		} else if (guard.position().distanceTo(bot.player().position()) < 5) {
			bot.approach(guard);
			how = "approach";
		} else if (!bot.walk(guard.blockPosition(), cell -> cell.distSqr(guard.blockPosition()) <= 4)) {
			bot.approach(guard);
			how = "no way, straight at it";
		} else {
			how = "walk, " + bot.pathLeft() + " cells";
		}
		// pressing on and going nowhere (a column, a stand's step) five seconds: round it as a player would, twice;
		// still nowhere, it is told as out of reach and left (no teleports: that is a fault to look into)
		if (!how.equals("fight") && bot.player().getDeltaMovement().horizontalDistanceSqr() < 0.0004) {
			if (++still > 100) {
				still = 0;
				if (++rounds <= 2) {
					bot.log("  STUCK going to " + guard.look().body().model() + " at " + guard.blockPosition().toShortString() + " from "
							+ bot.player().blockPosition().toShortString() + ": round it (" + rounds + ")");
					sidestep = 24;
				} else {
					bot.log("  UNREACHABLE " + guard.look().body().model() + " at " + guard.blockPosition().toShortString() + " from "
							+ bot.player().blockPosition().toShortString() + ", sight " + bot.player().hasLineOfSight(guard));
					unreachable.add(guard.getUUID());
					focus = null;
					rounds = 0;
				}
			}
		} else {
			still = 0;
		}
		if (ticks % 100 == 0) {
			bot.log(String.format(java.util.Locale.ROOT, "  %s %s %.1f off, %d high: %s; forward %s, moving %.2f", guard.look().body().model(), guard.blockPosition().toShortString(),
					guard.distanceTo(bot.player()), (int) (guard.getY() - bot.player().getY()), how, bot.mc().options.keyUp.isDown(),
					bot.player().getDeltaMovement().horizontalDistance()));
		}
	}

	/** The nearest guard within {@code r} (a keeper counts as twenty blocks further: the small ones first). */
	private static @Nullable SpireGuard nearest(DevBot bot, double r, Set<java.util.UUID> skip) {
		Vec3 me = bot.player().position();
		List<SpireGuard> guards = new java.util.ArrayList<>(bot.level().getEntitiesOfClass(SpireGuard.class, bot.player().getBoundingBox().inflate(r, 16, r),
				guard -> guard.isAlive() && !guard.look().echo() && !guard.isInvisible() && !skip.contains(guard.getUUID())));
		// a boss with its own parts about it is struck last (they shield it), as on the climb
		guards.removeIf(guard -> guard.look().keeper() >= 0 && guards.stream().anyMatch(other -> other.look().keeper() < 0 && other.distanceToSqr(guard) < 24 * 24));
		return guards.stream().min(Comparator.comparingDouble(guard -> guard.position().distanceToSqr(me) + (guard.look().keeper() >= 0 ? 400.0 : 0.0)))
				.orElse(null);
	}

	/**
	 * The Sky Path: toward the nearest waystone (a brazier on a rune) not stood by yet; on the ground, the next landing
	 * is the top in reach (four across, a block up at most) that brings it nearest, and it is jumped to at a run.
	 */
	private void hop(DevBot bot) {
		var player = bot.player();
		Level level = bot.level();
		BlockPos feet = player.blockPosition();
		// the waystone looked for now and then (a wide look over the loaded blocks)
		if (ticks % 20 == 0 || goal == null) goal = waystone(bot);
		BlockPos goal = this.goal;
		if (goal != null && goal.closerThan(feet, 2.5)) {
			stood.add(goal);
			this.goal = null;
		}
		Vec3 aim = goal == null ? player.position().add(player.getLookAngle().scale(10)) : Vec3.atCenterOf(goal);
		if (player.onGround()) {
			BlockPos best = null;
			double bestScore = aim.distanceTo(Vec3.atCenterOf(feet)) - 0.5;
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					for (int dy = -4; dy <= 1; dy++) {
						BlockPos top = feet.offset(dx, dy - 1, dz);
						if (dx * dx + dz * dz > 18 || (dx == 0 && dz == 0) || !DevBot.stand(level, top.above())) continue;
						double score = aim.distanceTo(Vec3.atCenterOf(top.above())) + 0.15 * Math.sqrt(dx * dx + dz * dz) - 0.2 * dy;
						if (score < bestScore) {
							bestScore = score;
							best = top.above();
						}
					}
				}
			}
			if (best == null) {
				// nothing nearer in reach: any landing at all, to look round from another island
				if (++stuck % 40 == 0) bot.log("  no landing nearer the waystone from " + feet.toShortString());
				best = feet.offset(player.getRandom().nextInt(7) - 3, 0, player.getRandom().nextInt(7) - 3);
			}
			landing = best;
		}
		BlockPos to = landing;
		if (to == null) return;
		var options = bot.mc().options;
		Vec3 target = Vec3.atCenterOf(to);
		bot.look(new Vec3(target.x, player.getEyeY() - 0.3, target.z));
		double away = Math.hypot(target.x - player.getX(), target.z - player.getZ());
		options.keyUp.setDown(away > 0.4);
		options.keySprint.setDown(away > 1.6);
		// at the edge (no ground a step ahead) and the landing further than a step: up and over
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(0.6));
		boolean edge = level.getBlockState(BlockPos.containing(ahead).below()).getCollisionShape(level, BlockPos.containing(ahead).below()).isEmpty();
		options.keyJump.setDown(player.onGround() && (edge || to.getY() > feet.getY()) && away > 1.0);
	}

	/** The nearest waystone the bot has not stood by, within sight of the loaded world. */
	private @Nullable BlockPos waystone(DevBot bot) {
		BlockPos feet = bot.player().blockPosition();
		BlockPos best = null;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-48, -16, -48), feet.offset(48, 16, 48))) {
			if (!bot.level().getBlockState(pos).is(SpireBlocks.BRAZIER) || !bot.level().getBlockState(pos.below()).is(SpireBlocks.RUNE)) continue;
			BlockPos stone = pos.below().immutable();
			if (stood.contains(stone)) continue;
			if (best == null || stone.distSqr(feet) < best.distSqr(feet)) best = stone;
		}
		return best;
	}

	@Override
	public void stopped(DevBot bot) {
		bot.release();
	}
}
