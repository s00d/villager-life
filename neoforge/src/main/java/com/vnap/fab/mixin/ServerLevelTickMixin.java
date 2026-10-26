package com.vnap.fab.mixin;

import com.vnap.fab.neoforge.event.tick.EntityTickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NeoForge's EntityTickEvent around an entity's tick in the server's loop. */
@SuppressWarnings("unused")
@Mixin(ServerLevel.class)
public abstract class ServerLevelTickMixin {
	@Redirect(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
	private void vnap$tick(Entity entity) {
		EntityTickEvent.around(entity, entity::tick);
	}
}
