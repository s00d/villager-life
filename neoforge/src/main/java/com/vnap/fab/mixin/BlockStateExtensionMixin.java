package com.vnap.fab.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** Every block state is NeoForge's extended one (it asks its block). */
@Mixin(net.minecraft.world.level.block.state.BlockState.class)
public abstract class BlockStateExtensionMixin implements com.vnap.fab.neoforge.common.extensions.IBlockStateExtension {
}
