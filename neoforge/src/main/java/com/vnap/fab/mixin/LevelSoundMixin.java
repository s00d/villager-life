package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.PlayLevelSoundEvent;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's PlayLevelSoundEvent: a sound the server is about to send, cancellable. */
@SuppressWarnings("unused")
@Mixin(ServerLevel.class)
public abstract class LevelSoundMixin {
	// both of its overloads (the one at a place, the one at an entity), whoever plays it
	@Inject(method = "playSeededSound", at = @At("HEAD"), cancellable = true)
	private void vnap$sound(CallbackInfo info, @Local(argsOnly = true) Holder<SoundEvent> sound) {
		if (Bridge.fire(new PlayLevelSoundEvent(sound, (ServerLevel) (Object) this)).isCanceled()) info.cancel();
	}
}
