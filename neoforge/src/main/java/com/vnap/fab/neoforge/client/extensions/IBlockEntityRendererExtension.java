package com.vnap.fab.neoforge.client.extensions;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/** NeoForge's box a block entity renderer draws in (the build injects this; com.vnap.fab.mixin.client.SectionCompilerMixin reads it). */
public interface IBlockEntityRendererExtension<T extends BlockEntity> {
	default AABB getRenderBoundingBox(T blockEntity) {
		return new AABB(blockEntity.getBlockPos());
	}
}
