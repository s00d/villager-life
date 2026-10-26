package com.vnap.fab.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** Every block is NeoForge's extended block (its defaults: the block has nothing of its own to say). */
@Mixin(net.minecraft.world.level.block.Block.class)
public abstract class BlockExtensionMixin implements com.vnap.fab.neoforge.common.extensions.IBlockExtension {
}
