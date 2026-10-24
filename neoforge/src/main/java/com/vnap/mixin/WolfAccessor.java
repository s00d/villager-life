package com.vnap.mixin;

import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A family dog wears the family's collar colour; vanilla keeps the setter private. */
@Mixin(Wolf.class)
public interface WolfAccessor {
	@Invoker("setCollarColor")
	void vnap$setCollarColor(DyeColor colour);
}
