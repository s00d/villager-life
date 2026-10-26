package com.vnap.fab.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A block entity whose renderer draws beyond its block (NeoForge's getRenderBoundingBox) is drawn with those vanilla
 * draws wherever they are: its section out of sight would hide it.
 */
@SuppressWarnings("unused")
// from 26.2 a section keeps every block entity: the level keeps those drawn wherever they are, as each is added
//? if >=26.2 {
/*@Mixin(net.minecraft.client.multiplayer.ClientLevel.class)
*///?} else
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
	// from 1.21.5 a renderer says it without the block entity
	//? if >=26.2 {
	/*@WrapOperation(method = "onBlockEntityAdded", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRenderOffScreen()Z"))
	private boolean vnap$wide(BlockEntityRenderer<BlockEntity, ?> renderer, Operation<Boolean> vanilla, BlockEntity blockEntity) {
		return vanilla.call(renderer) || beyond(renderer.getRenderBoundingBox(blockEntity), blockEntity);
	}
	*///?} else if >=1.21.9 {
	/*@WrapOperation(method = "handleBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRenderOffScreen()Z"))
	private <E extends BlockEntity> boolean vnap$wide(BlockEntityRenderer<E, ?> renderer, Operation<Boolean> vanilla, SectionCompiler.Results results, E blockEntity) {
		return vanilla.call(renderer) || beyond(renderer.getRenderBoundingBox(blockEntity), blockEntity);
	}
	*///?} else if >=1.21.5 {
	/*@WrapOperation(method = "handleBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRenderOffScreen()Z"))
	private <E extends BlockEntity> boolean vnap$wide(BlockEntityRenderer<E> renderer, Operation<Boolean> vanilla, SectionCompiler.Results results, E blockEntity) {
		return vanilla.call(renderer) || beyond(renderer.getRenderBoundingBox(blockEntity), blockEntity);
	}
	*///?} else {
	@WrapOperation(method = "handleBlockEntity", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRenderOffScreen(Lnet/minecraft/world/level/block/entity/BlockEntity;)Z"))
	private <E extends BlockEntity> boolean vnap$wide(BlockEntityRenderer<E> renderer, E blockEntity, Operation<Boolean> vanilla) {
		return vanilla.call(renderer, blockEntity) || beyond(renderer.getRenderBoundingBox(blockEntity), blockEntity);
	}
	//?}

	private static boolean beyond(AABB box, BlockEntity blockEntity) {
		AABB own = new AABB(blockEntity.getBlockPos()).inflate(1.0E-4);
		return !own.contains(box.minX, box.minY, box.minZ) || !own.contains(box.maxX, box.maxY, box.maxZ);
	}
}
