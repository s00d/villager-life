package com.vnap.fab.neoforge.event;

/** A sound a level is about to play (com.vnap.fab.mixin.LevelSoundMixin). */
public class PlayLevelSoundEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.core.@org.jspecify.annotations.Nullable Holder<net.minecraft.sounds.SoundEvent> sound;
	private final net.minecraft.world.level.Level level;

	public PlayLevelSoundEvent(net.minecraft.core.@org.jspecify.annotations.Nullable Holder<net.minecraft.sounds.SoundEvent> sound, net.minecraft.world.level.Level level) {
		this.sound = sound;
		this.level = level;
	}

	public net.minecraft.core.@org.jspecify.annotations.Nullable Holder<net.minecraft.sounds.SoundEvent> getSound() {
		return sound;
	}

	public net.minecraft.world.level.Level getLevel() {
		return level;
	}
}
