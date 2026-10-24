package com.vnap.client.dev;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A walk on foot through points, as a player walks a shot: straight at each point (the points are the route, no
 * way-finding: the climbing bot's grid path turned the head at every cell), the head easing round, no sprint, a hop
 * over a step; at the end a look at one place with the small sway of a head that is held by a person, not a tripod.
 * {@code /vnapbot walk "x y z; x y z; look x y z"}.
 */
final class WalkTask implements DevBot.Task {
	private static final int GIVE_UP = 20 * 20;
	/** How fast the head follows the way, a share of what is left per tick. */
	private static final float EASE = 0.12F;
	private final List<BlockPos> points;
	private final @Nullable Vec3 look;
	private int index;
	private int ticks;
	private int stood;
	private float yaw = Float.NaN;
	private float pitch;

	WalkTask(List<BlockPos> points, @Nullable Vec3 look) {
		this.points = points;
		this.look = look;
	}

	@Override
	public boolean tick(DevBot bot) {
		if (bot.respawning()) return true;
		var player = bot.player();
		var options = bot.mc().options;
		if (Float.isNaN(yaw)) {
			yaw = player.getYRot();
			pitch = player.getXRot();
		}
		if (index >= points.size()) {
			bot.release();
			if (look != null) {
				Vec3 eye = player.getEyePosition();
				Vec3 d = look.subtract(eye);
				float aimYaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F;
				float aimPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
				stood++;
				// a held head drifts a little and breathes
				ease(aimYaw + (float) Math.sin(stood * 0.045) * 2.2F, aimPitch + (float) Math.sin(stood * 0.07 + 1.0) * 1.1F, 0.08F);
				player.setYRot(yaw);
				player.setXRot(pitch);
			}
			return true;
		}
		BlockPos aim = points.get(index);
		Vec3 to = Vec3.atBottomCenterOf(aim).subtract(player.position());
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		if (flat < 0.9 || ++ticks > GIVE_UP) {
			if (flat >= 0.9) bot.log("walk: did not reach " + aim.toShortString());
			index++;
			ticks = 0;
			return true;
		}
		// the eyes on where the way goes next, a little down at the ground ahead
		ease((float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0F, 6.0F, EASE);
		player.setYRot(yaw);
		player.setXRot(pitch);
		options.keySprint.setDown(false);
		options.keyUp.setDown(true);
		options.keyJump.setDown(player.horizontalCollision && player.onGround());
		return true;
	}

	private void ease(float toYaw, float toPitch, float share) {
		yaw += net.minecraft.util.Mth.wrapDegrees(toYaw - yaw) * share;
		pitch += (toPitch - pitch) * share;
	}

	@Override
	public void stopped(DevBot bot) {
		bot.release();
	}
}
