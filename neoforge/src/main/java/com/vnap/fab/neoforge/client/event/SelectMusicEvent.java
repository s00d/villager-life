package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.Bridge;
import com.vnap.fab.bus.api.Event;
import net.minecraft.client.resources.sounds.SoundInstance;
//? if >=1.21.4 && <1.21.9 {
/*import net.minecraft.client.sounds.MusicInfo;
*///?} else
import net.minecraft.sounds.Music;
import org.jspecify.annotations.Nullable;

/** The music the game is about to pick (com.vnap.fab.mixin.client.MusicManagerMixin): a Music, from 1.21.4 a MusicInfo. */
public final class SelectMusicEvent extends Event {

	//? if >=1.21.4 && <1.21.9 {
	/*private final MusicInfo original;
	private @Nullable MusicInfo music;
	*///?} else {
	private final Music original;
	private @Nullable Music music;
	//?}
	private final @Nullable SoundInstance playing;

	//? if >=1.21.4 && <1.21.9 {
	/*public SelectMusicEvent(MusicInfo music, @Nullable SoundInstance playing) {
	*///?} else
	public SelectMusicEvent(Music music, @Nullable SoundInstance playing) {
		this.original = music;
		this.music = music;
		this.playing = playing;
	}

	//? if >=1.21.4 && <1.21.9 {
	/*public MusicInfo getOriginalMusic() {
	*///?} else
	public Music getOriginalMusic() {
		return original;
	}

	//? if >=1.21.4 && <1.21.9 {
	/*public @Nullable MusicInfo getMusic() {
	*///?} else
	public @Nullable Music getMusic() {
		return music;
	}

	public @Nullable SoundInstance getPlayingMusic() {
		return playing;
	}

	//? if >=1.21.4 && <1.21.9 {
	/*public void overrideMusic(@Nullable MusicInfo music) {
	*///?} else
	public void overrideMusic(@Nullable Music music) {
		this.music = music;
		vnap$cancel(true);
	}
}
