package com.vnap.fab.mixin;

import com.vnap.fab.neoforge.event.tick.EntityTickEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NeoForge's EntityTickEvent around a passenger's tick. */
@SuppressWarnings("unused")
@Mixin(Entity.class)
public abstract class EntityRideTickMixin {
	@Redirect(method = "rideTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
	private void vnap$tick(Entity entity) {
		EntityTickEvent.around(entity, entity::tick);
	}
}
