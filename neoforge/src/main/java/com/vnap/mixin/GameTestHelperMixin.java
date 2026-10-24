package com.vnap.mixin;

import net.minecraft.gametest.framework.GameTestHelper;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.4 {
/*import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
*///?}

/**
 * The tests are written with 1.21.1's relative positions, counted from the structure block one below the test's
 * structure; from 1.21.4 the helper counts from the structure's own corner. It keeps counting from the block below.
 */
@SuppressWarnings("unused")
@Mixin(GameTestHelper.class)
public abstract class GameTestHelperMixin {
	//? if >=1.21.4 {
	/*@Redirect(method = {"absolutePos", "relativePos", "absoluteVec", "relativeVec"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/gametest/framework/GameTestInfo;getTestOrigin()Lnet/minecraft/core/BlockPos;"))
	private BlockPos vnap$fromTheStructureBlock(GameTestInfo info) {
		return info.getTestOrigin().below();
	}
	*///?}
}
