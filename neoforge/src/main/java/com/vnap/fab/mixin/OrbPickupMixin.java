package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.PlayerXpEvent;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's PlayerXpEvent.PickupXp: an orb about to be taken, cancellable. */
@SuppressWarnings("unused")
@Mixin(ExperienceOrb.class)
public abstract class OrbPickupMixin {
	@Inject(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;take(Lnet/minecraft/world/entity/Entity;I)V"), cancellable = true)
	private void vnap$orb(Player player, CallbackInfo info) {
		if (Bridge.fire(new PlayerXpEvent.PickupXp(player, (ExperienceOrb) (Object) this)).isCanceled()) info.cancel();
	}
}
