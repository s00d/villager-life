package com.vnap.village.home;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Walks a family pet to where the family tick sent it (home, the kennel, the cat bed, the bowl). It outranks the
 * vanilla stroll, whose stop would otherwise cancel the path; the target is cleared on arrival.
 */
final class PetGoal extends Goal {
	private final TamableAnimal pet;
	private int repath;

	PetGoal(TamableAnimal pet) {
		this.pet = pet;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		BlockPos target = Pets.target(pet);
		return target != null && !pet.isOrderedToSit() && !pet.isLeashed() && !arrived(target);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		repath = 0;
	}

	@Override
	public void tick() {
		BlockPos target = Pets.target(pet);
		if (target == null) return;
		if (arrived(target)) {
			Pets.clearTarget(pet);
			pet.getNavigation().stop();
			return;
		}
		if (--repath <= 0 || pet.getNavigation().isDone()) {
			repath = 40;
			Pets.walkToward(pet, target);
		}
	}

	private boolean arrived(BlockPos target) {
		return pet.blockPosition().closerThan(target, 2.5);
	}

	/**
	 * Vanilla's SitWhenOrderedToGoal keeps an ownerless tame animal sitting for good ("no owner, stay put"); a family pet
	 * has no player owner, so it sits only when told to (at night by the kennel, in a lesson).
	 */
	static final class Sit extends Goal {
		private final TamableAnimal pet;

		Sit(TamableAnimal pet) {
			this.pet = pet;
			setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return pet.isOrderedToSit() && pet.onGround() && !com.vnap.Mc.inWater(pet);
		}

		@Override
		public void start() {
			pet.getNavigation().stop();
			pet.setInSittingPose(true);
		}

		@Override
		public void stop() {
			pet.setInSittingPose(false);
		}
	}
}
