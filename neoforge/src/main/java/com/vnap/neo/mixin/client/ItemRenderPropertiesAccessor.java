package com.vnap.neo.mixin.client;

import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Forge keeps an item's client extensions in the item, set when it is made: NeoForge's event sets them later. */
@Mixin(Item.class)
public interface ItemRenderPropertiesAccessor {
	@Accessor(value = "renderProperties", remap = false)
	void vnap$setRenderProperties(Object properties);
}
