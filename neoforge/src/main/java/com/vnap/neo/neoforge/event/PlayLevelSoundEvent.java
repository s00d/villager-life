package com.vnap.neo.neoforge.event;

public class PlayLevelSoundEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(PlayLevelSoundEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.PlayLevelSoundEvent.class, priority, PlayLevelSoundEvent::new, sink));
	}

	private final net.minecraftforge.event.PlayLevelSoundEvent f;

	PlayLevelSoundEvent(net.minecraftforge.event.PlayLevelSoundEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.core.@org.jspecify.annotations.Nullable Holder<net.minecraft.sounds.SoundEvent> getSound() {
		return f.getSound();
	}

	public net.minecraft.world.level.Level getLevel() {
		return f.getLevel();
	}
}
