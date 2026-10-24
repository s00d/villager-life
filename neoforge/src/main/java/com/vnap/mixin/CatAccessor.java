package com.vnap.mixin;

import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A family cat wears the family's collar colour; vanilla keeps the setter private. */
@Mixin(Cat.class)
public interface CatAccessor {
	@Invoker("setCollarColor")
	void vnap$setCollarColor(DyeColor colour);
}
