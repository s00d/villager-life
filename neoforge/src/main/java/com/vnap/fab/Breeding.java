package com.vnap.fab;

import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.jspecify.annotations.Nullable;

/** NeoForge's BabyEntitySpawnEvent of two animals breeding (com.vnap.fab.mixin.BreedMixin, FoxBreedMixin). */
public final class Breeding {
	private Breeding() {
	}

	/** The child the event leaves (null when cancelled: the parents' love is reset, as NeoForge does). */
	public static @Nullable AgeableMob child(Animal parent, Animal mate, @Nullable AgeableMob child) {
		var cause = parent.getLoveCause() != null ? parent.getLoveCause() : mate.getLoveCause();
		var event = Bridge.fire(new com.vnap.fab.neoforge.event.entity.living.BabyEntitySpawnEvent(parent, mate, cause, child));
		if (event.isCanceled()) {
			parent.setAge(6000);
			mate.setAge(6000);
			parent.resetLove();
			mate.resetLove();
			return null;
		}
		return event.getChild();
	}
}
