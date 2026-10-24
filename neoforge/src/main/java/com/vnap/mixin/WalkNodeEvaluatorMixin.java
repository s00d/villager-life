package com.vnap.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla takes every trapdoor for a cell to walk through, an open one (a panel standing upright) too. A taiga
 * village's flower bed is ringed with open trapdoors: villagers on their way to a shelter were led into it, could
 * not get out, and a zombie killed six there in one night (live, 20.10.2026). An open trapdoor is a fence to walk
 * round; over a ladder it is a hatch and stays the way down.
 */
@SuppressWarnings("unused")
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin {
	@Inject(method = "getPathTypeFromState", at = @At("HEAD"), cancellable = true)
	private static void vnap$openTrapdoorIsAWall(BlockGetter level, BlockPos pos, CallbackInfoReturnable<PathType> cir) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN) && !level.getBlockState(pos.below()).is(BlockTags.CLIMBABLE)
				&& !vnap$shutter(level, pos, state)) {
			cir.setReturnValue(PathType.FENCE);
		}
	}

	/**
	 * A shutter: hung over air with its panel flat against a wall (a window's, Dressing) - a sliver by the wall, walked
	 * past at head height. Taken for a fence, every pair of shutters made a dead end along the wall, and a villager
	 * shut in one stood there all night.
	 */
	private static boolean vnap$shutter(BlockGetter level, BlockPos pos, BlockState state) {
		if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) return false;
		// an open trapdoor's panel lies on the side opposite its facing
		net.minecraft.core.Direction toPanel = state.getValue(TrapDoorBlock.FACING).getOpposite();
		BlockPos wall = pos.relative(toPanel);
		return level.getBlockState(wall).isFaceSturdy(level, wall, toPanel.getOpposite());
	}
}
