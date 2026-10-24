package com.vnap.fx;

import com.vnap.VillagerLife;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class VillageParticles {
    public static final SimpleParticleType IRON_SPARK = new SimpleParticleType(false);
    /** Flat ring on the ground; the x speed carries the final radius. */
    public static final SimpleParticleType TOLL_RING = new SimpleParticleType(true);
    public static final SimpleParticleType RUST_FLAKE = new SimpleParticleType(false);
    public static final SimpleParticleType MOLTEN_DRIP = new SimpleParticleType(false);
    public static final SimpleParticleType STEAM_PUFF = new SimpleParticleType(false);

    private VillageParticles() {
    }

    public static void register(RegisterEvent.RegisterHelper<ParticleType<?>> helper) {
        helper.register(VillagerLife.id("iron_spark"), IRON_SPARK);
        helper.register(VillagerLife.id("toll_ring"), TOLL_RING);
        helper.register(VillagerLife.id("rust_flake"), RUST_FLAKE);
        helper.register(VillagerLife.id("molten_drip"), MOLTEN_DRIP);
        helper.register(VillagerLife.id("steam_puff"), STEAM_PUFF);
    }

    public static void ring(ServerLevel level, double x, double y, double z, double radius) {
        level.sendParticles(TOLL_RING, x, y, z, 0, radius, 0.0, 0.0, 1.0);
    }
}
