package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

public final class RegisterParticleProvidersEvent extends Event {
	static {
		Bridge.bind(RegisterParticleProvidersEvent.class, (priority, sink) -> Bridge.mod(net.minecraftforge.client.event.RegisterParticleProvidersEvent.class, priority,
				RegisterParticleProvidersEvent::new, sink));
	}

	private final net.minecraftforge.client.event.RegisterParticleProvidersEvent f;

	RegisterParticleProvidersEvent(net.minecraftforge.client.event.RegisterParticleProvidersEvent f) {
		super(f);
		this.f = f;
	}

	//? if >=1.21.9 {
	/*public <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type, net.minecraft.client.particle.ParticleResources.SpriteParticleRegistration<T> registration) {
		f.registerSpriteSet(type, registration);
	}
	*///?} else {
	public <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type, net.minecraft.client.particle.ParticleEngine.SpriteParticleRegistration<T> registration) {
		f.registerSpriteSet(type, registration);
	}
	//?}

	public <T extends ParticleOptions> void registerSpecial(ParticleType<T> type, ParticleProvider<T> provider) {
		f.registerSpecial(type, provider);
	}
}
