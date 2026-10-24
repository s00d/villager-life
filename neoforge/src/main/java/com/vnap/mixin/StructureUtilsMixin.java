package com.vnap.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A game test's ground is cleared before it is set out: every cell of the structure's box and twenty above it is placed
 * anew and its neighbours told, half a million cells for a test on the 100x100 platform, nearly all of them air
 * already. A cell that already holds what it is cleared to is left alone: a third of a whole test run went there.
 */
@SuppressWarnings("unused")
@Mixin(StructureUtils.class)
public abstract class StructureUtilsMixin {
	@Inject(method = "clearBlock", at = @At("HEAD"), cancellable = true)
	private static void vnap$leaveWhatIsClear(int structureBlockY, BlockPos pos, ServerLevel level, CallbackInfo ci) {
		BlockState now = level.getBlockState(pos);
		if (pos.getY() < structureBlockY ? now.is(Blocks.STONE) : now.isAir()) ci.cancel();
	}
}
