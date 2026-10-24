package com.vnap.spire.guard;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;

/** A guard on foot: stone sentinels, clockwork hounds, vine creepers. Strolls about its room till someone comes. */
public final class SpireWalker extends SpireGuard {
	public SpireWalker(EntityType<? extends SpireWalker> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
	}
}
