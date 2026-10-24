package com.vnap.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A world counts as "experimental" (the warning every time it is opened) when it has a dimension past the three of
 * vanilla or one vanilla does not know. The spire's is the mod's own: it counts as one of the stable ones, and the
 * stable count is one more while the mod is in. Another mod's dimension still makes the world experimental.
 */
@SuppressWarnings("unused")
@Mixin(WorldDimensions.class)
public abstract class SpireWorldDimensionsMixin {
	@Inject(method = "checkStability", at = @At("HEAD"), cancellable = true, require = 0)
	private static void vnap$spireIsStable(ResourceKey<LevelStem> key, LevelStem stem, CallbackInfoReturnable<Lifecycle> cir) {
		if (com.vnap.Mc.id(key).getNamespace().equals(com.vnap.VillagerLife.MOD_ID)) cir.setReturnValue(Lifecycle.stable());
	}

	@Redirect(method = "bake", at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETSTATIC,
			target = "Lnet/minecraft/world/level/levelgen/WorldDimensions;VANILLA_DIMENSION_COUNT:I"), require = 0)
	private int vnap$withTheSpire() {
		// the overworld, the nether, the end and the spire
		return 4;
	}
}
