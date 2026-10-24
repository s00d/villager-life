package com.vnap.mixin.client;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The cursor set without a window to move it over (DevBot's {@code /vnapbot mouse}: tooltips in a hidden client). */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
	@Accessor("xpos")
	void vnap$setX(double x);

	@Accessor("ypos")
	void vnap$setY(double y);
}
