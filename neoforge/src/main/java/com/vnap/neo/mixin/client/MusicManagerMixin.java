package com.vnap.neo.mixin.client;

import com.vnap.neo.Bridge;
import com.vnap.neo.neoforge.client.event.SelectMusicEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NeoForge's SelectMusicEvent: the music the game would play now, which a listener may change. */
@SuppressWarnings("unused")
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Shadow
	private @Nullable SoundInstance currentMusic;

	//? if >=1.21.4 && <1.21.9 {
	/*@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSituationalMusic()Lnet/minecraft/client/sounds/MusicInfo;"))
	private net.minecraft.client.sounds.MusicInfo vnap$select(Minecraft minecraft) {
		net.minecraft.client.sounds.MusicInfo situational = minecraft.getSituationalMusic();
		var chosen = Bridge.fire(new SelectMusicEvent(situational, currentMusic)).getMusic();
		return chosen == null ? situational : chosen;
	}
	*///?} else {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSituationalMusic()Lnet/minecraft/sounds/Music;"))
	private net.minecraft.sounds.Music vnap$select(Minecraft minecraft) {
		net.minecraft.sounds.Music situational = minecraft.getSituationalMusic();
		var chosen = Bridge.fire(new SelectMusicEvent(situational, currentMusic)).getMusic();
		return chosen == null ? situational : chosen;
	}
	//?}
}
