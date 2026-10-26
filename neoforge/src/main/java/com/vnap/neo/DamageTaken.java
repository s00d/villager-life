package com.vnap.neo;

import net.minecraft.world.damagesource.DamageSource;

/** What the damage mixin keeps on a living entity, for the player's own actuallyHurt. */
public interface DamageTaken {
	void vnap$posted(DamageSource source);

	void vnap$taken(float taken);
}
