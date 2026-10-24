package com.vnap.spire.guard;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A guard of a floor's pool: tide fins and mist rays. No path finding — it steers: towards a point of the pool (a
 * wander point, or the climber when the climber is in the water), turning back from any heading that leaves the
 * water; a shooter fires from it at whoever stands on the rim. Thrown out, it is put back.
 */
public final class SpireSwimmer extends SpireGuard {
	private static final double SPEED = 0.12;
	private static final int DRY_TICKS = 40;
	private @Nullable Vec3 wander;
	private int dry;

	public SpireSwimmer(EntityType<? extends SpireSwimmer> type, Level level) {
		super(type, level);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WaterBoundPathNavigation(this, level);
	}

	/** It breathes water as well as air. */
	@Override
	protected int decreaseAirSupply(int air) {
		return air;
	}

	@Override
	protected void idle() {
		Vec3 to = wander;
		if (to == null || tickCount % 60 == 0 || position().distanceToSqr(to) < 0.5) {
			to = pick();
			wander = to;
		}
		steer(to);
	}

	@Override
	protected void approach(LivingEntity target, double distance) {
		if (target.isInWater() && room.contains(target.position())) {
			steer(target.position().add(0.0, target.getBbHeight() * 0.4, 0.0));
		} else {
			idle();
			getLookControl().setLookAt(target);
		}
	}

	@Override
	protected boolean fits(com.vnap.spire.Attack attack, LivingEntity target, double distance) {
		// a dash would take it out of the water after a climber on the rim
		if (attack == com.vnap.spire.Attack.DASH && !target.isInWater()) return false;
		return super.fits(attack, target, distance);
	}

	private boolean water(Vec3 at) {
		return level().getFluidState(BlockPos.containing(at)).is(FluidTags.WATER);
	}

	/** A point of the pool near its post. */
	private Vec3 pick() {
		for (int i = 0; i < 8; i++) {
			Vec3 at = new Vec3(home.getX() + 0.5 + random.nextGaussian() * 1.5, home.getY() + 0.3 + random.nextGaussian() * 0.6,
					home.getZ() + 0.5 + random.nextGaussian() * 1.5);
			if (water(at) && water(at.add(0.0, getBbHeight(), 0.0))) return at;
		}
		return Vec3.atBottomCenterOf(home).add(0.0, 0.3, 0.0);
	}

	private void steer(Vec3 to) {
		boolean wet = isInWater();
		setNoGravity(wet);
		if (!wet) {
			if (++dry > DRY_TICKS) {
				dry = 0;
				back();
			}
			return;
		}
		dry = 0;
		Vec3 way = to.subtract(position());
		Vec3 want = way.lengthSqr() < 0.04 ? Vec3.ZERO : way.normalize().scale(SPEED);
		// a heading that leaves the water turns back to the middle of the pool
		if (want.lengthSqr() > 0 && !water(position().add(want.scale(6.0)).add(0.0, 0.2, 0.0))) {
			want = Vec3.atBottomCenterOf(home).add(0.0, 0.3, 0.0).subtract(position()).normalize().scale(SPEED);
			wander = null;
		}
		setDeltaMovement(getDeltaMovement().scale(0.6).add(want.scale(0.6)));
		Vec3 moving = getDeltaMovement();
		if (moving.horizontalDistanceSqr() > 1.0E-4) {
			float yaw = (float) (Mth.atan2(moving.z, moving.x) * Mth.RAD_TO_DEG) - 90.0F;
			setYRot(yaw);
			yBodyRot = yaw;
		}
	}
}
