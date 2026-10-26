package com.vnap.fab.mixin.client;

import com.vnap.fab.neoforge.event.tick.EntityTickEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NeoForge's EntityTickEvent around an entity's tick in the client's loop. */
@SuppressWarnings("unused")
@Mixin(ClientLevel.class)
public abstract class ClientLevelTickMixin {
	@Redirect(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
	private void vnap$tick(Entity entity) {
		EntityTickEvent.around(entity, entity::tick);
	}
}
