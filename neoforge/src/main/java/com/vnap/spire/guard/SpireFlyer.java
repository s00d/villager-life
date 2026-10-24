package com.vnap.spire.guard;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A guard of the air: crystal moths, mist rays, spark wisps. Circles above its post, and in a fight about its
 * target (a shooter wide, a fighter close); every point it heads for is kept inside its own room.
 */
public final class SpireFlyer extends SpireGuard {
	/** How high over its post it circles, and how wide. */
	private static final double HOVER = 2.5;
	private static final double CIRCLE = 4.0;

	public SpireFlyer(EntityType<? extends SpireFlyer> type, Level level) {
		super(type, level);
		//? if >=26.2 {
		/*moveControl = new FlyingMoveControl<>(this, 20, true);
		*///?} else
		moveControl = new FlyingMoveControl(this, 20, true);
		setNoGravity(true);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
	}

	@Override
	protected void idle() {
		double a = tickCount * 0.03 + getId();
		fly(new Vec3(home.getX() + 0.5 + Math.cos(a) * CIRCLE, home.getY() + HOVER + Math.sin(tickCount * 0.05) * 0.6,
				home.getZ() + 0.5 + Math.sin(a) * CIRCLE), 0.8);
	}

	@Override
	protected void approach(LivingEntity target, double distance) {
		if (keepsAway()) {
			double a = tickCount * 0.04 + getId();
			fly(new Vec3(target.getX() + Math.cos(a) * 7.0, target.getY() + 3.0, target.getZ() + Math.sin(a) * 7.0), 1.0);
		} else {
			fly(target.position().add(0.0, target.getBbHeight() * 0.6, 0.0), 1.2);
		}
	}

	/** Flies as the allay does: the move control's push, then air that slows it alike every way (no falling). */
	@Override
	public void travel(Vec3 input) {
		moveRelative(getSpeed() * 0.15F, input);
		move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
		setDeltaMovement(getDeltaMovement().scale(0.85));
	}

	/** Heads for the point, kept inside its room. */
	private void fly(Vec3 to, double speed) {
		double margin = 0.6;
		double x = net.minecraft.util.Mth.clamp(to.x, room.minX + margin, room.maxX - margin);
		double y = net.minecraft.util.Mth.clamp(to.y, room.minY + 0.3, room.maxY - getBbHeight() - 0.3);
		double z = net.minecraft.util.Mth.clamp(to.z, room.minZ + margin, room.maxZ - margin);
		moveControl.setWantedPosition(x, y, z, speed);
	}

	@Override
	//? if >=1.21.5 {
	/*public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
	*///?} else
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}
}
