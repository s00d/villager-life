package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.common.util.BlockSnapshot;
import com.vnap.fab.neoforge.event.level.BlockEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's EntityPlaceEvent of a block item: after the block is set, as NeoForge fires it; cancelled, the place gets
 * back what it held and the item stays.
 */
@SuppressWarnings("unused")
@Mixin(BlockItem.class)
public abstract class BlockPlaceMixin {
	@WrapOperation(method = "place", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean vnap$placed(BlockItem item, BlockPlaceContext context, BlockState state, Operation<Boolean> vanilla) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState before = level.getBlockState(pos);
		BlockState against = level.getBlockState(pos.relative(context.getClickedFace().getOpposite()));
		if (!vanilla.call(item, context, state)) return false;
		if (level.isClientSide()) return true;
		var event = Bridge.fire(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, pos), against, context.getPlayer()));
		if (!event.isCanceled()) return true;
		level.setBlock(pos, before, Block.UPDATE_ALL);
		return false;
	}
}
