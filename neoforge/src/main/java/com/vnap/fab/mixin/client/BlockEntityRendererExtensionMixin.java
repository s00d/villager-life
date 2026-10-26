package com.vnap.fab.mixin.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Every block entity renderer is NeoForge's extended one (its default box: the block). */
@Mixin(BlockEntityRenderer.class)
public interface BlockEntityRendererExtensionMixin<T extends BlockEntity> extends com.vnap.fab.neoforge.client.extensions.IBlockEntityRendererExtension<T> {
}
