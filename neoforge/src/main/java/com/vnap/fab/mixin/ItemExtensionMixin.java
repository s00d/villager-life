package com.vnap.fab.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** Every item is NeoForge's extended item (its defaults: the item has nothing of its own to say). */
@Mixin(net.minecraft.world.item.Item.class)
public abstract class ItemExtensionMixin implements com.vnap.fab.neoforge.common.extensions.IItemExtension {
}
