package com.vnap.mixin;

import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The statue of a town's heroes (village/town/Plaza): arms out, no base plate. */
@Mixin(ArmorStand.class)
public interface ArmorStandAccessor {
	@Invoker("setShowArms")
	void vnap$setShowArms(boolean show);

	@Invoker("setNoBasePlate")
	void vnap$setNoBasePlate(boolean none);
}
