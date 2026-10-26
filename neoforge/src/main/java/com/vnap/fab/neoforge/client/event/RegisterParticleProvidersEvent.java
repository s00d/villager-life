package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.bus.api.Event;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/** The mod's particles' providers: Fabric API's particle registry. */
public final class RegisterParticleProvidersEvent extends Event {
	public RegisterParticleProvidersEvent() {
	}

	public <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type, net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.PendingParticleFactory<T> registration) {
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(type, registration);
	}

	public <T extends ParticleOptions> void registerSpecial(ParticleType<T> type, ParticleProvider<T> provider) {
		net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance().register(type, provider);
	}
}
