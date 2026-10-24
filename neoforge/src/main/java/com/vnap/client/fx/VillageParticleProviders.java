package com.vnap.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vnap.fx.VillageParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
//? if <1.21.9
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

public final class VillageParticleProviders {
    private static final int FULL_BRIGHT = 0xF000F0;

    private VillageParticleProviders() {
    }

    public static void register(RegisterParticleProvidersEvent event) {
        //? if >=1.21.9 {
        /*        event.registerSpriteSet(VillageParticles.IRON_SPARK, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
        *///?} else
        event.registerSpriteSet(VillageParticles.IRON_SPARK, sprites -> (type, level, x, y, z, dx, dy, dz) ->
                new Spark(level, x, y, z, dx, dy, dz, sprites));
        //? if >=1.21.9 {
        /*        event.registerSpriteSet(VillageParticles.RUST_FLAKE, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
        *///?} else
        event.registerSpriteSet(VillageParticles.RUST_FLAKE, sprites -> (type, level, x, y, z, dx, dy, dz) ->
                new Flake(level, x, y, z, dx, dy, dz, sprites));
        //? if >=1.21.9 {
        /*        event.registerSpriteSet(VillageParticles.MOLTEN_DRIP, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
        *///?} else
        event.registerSpriteSet(VillageParticles.MOLTEN_DRIP, sprites -> (type, level, x, y, z, dx, dy, dz) ->
                new Drip(level, x, y, z, dx, dy, dz, sprites));
        //? if >=1.21.9 {
        /*        event.registerSpriteSet(VillageParticles.STEAM_PUFF, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
        *///?} else
        event.registerSpriteSet(VillageParticles.STEAM_PUFF, sprites -> (type, level, x, y, z, dx, dy, dz) ->
                new Steam(level, x, y, z, dx, dy, dz, sprites));
        //? if >=1.21.9 {
        /*        event.registerSpriteSet(VillageParticles.TOLL_RING, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
        *///?} else
        event.registerSpriteSet(VillageParticles.TOLL_RING, sprites -> (type, level, x, y, z, dx, dy, dz) ->
                new Ring(level, x, y, z, dx, sprites));
    }

    /** Hot iron chip: falls fast, burns out. */
    static final class Spark extends TextureSheetParticle {
        private final SpriteSet sprites;

        Spark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            //? if >=1.21.9 {
            /*super(level, x, y, z, dx, dy, dz, sprites.first());
            *///?} else
            super(level, x, y, z, dx, dy, dz);
            this.sprites = sprites;
            this.xd = dx + (random.nextDouble() - 0.5) * 0.08;
            this.yd = dy + random.nextDouble() * 0.12;
            this.zd = dz + (random.nextDouble() - 0.5) * 0.08;
            this.gravity = 0.7F;
            this.friction = 0.92F;
            this.lifetime = 8 + random.nextInt(9);
            this.quadSize = 0.05F + random.nextFloat() * 0.05F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            quadSize *= 0.95F;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return FULL_BRIGHT;
        }

        //? if >=1.21.9 {
        /*@Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }
        *///?} else {
        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
        //?}
    }

    /** Rust flake: drifts down, swaying. */
    static final class Flake extends TextureSheetParticle {
        private final float sway;

        Flake(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            //? if >=1.21.9 {
            /*super(level, x, y, z, dx, dy, dz, sprites.first());
            *///?} else
            super(level, x, y, z, dx, dy, dz);
            this.xd = dx * 0.5;
            this.yd = dy * 0.5 - 0.01;
            this.zd = dz * 0.5;
            this.gravity = 0.06F;
            this.friction = 0.95F;
            this.lifetime = 30 + random.nextInt(25);
            this.quadSize = 0.06F + random.nextFloat() * 0.05F;
            this.sway = random.nextFloat() * Mth.TWO_PI;
            this.roll = random.nextFloat() * Mth.TWO_PI;
            //? if >=1.21.9 {
            /*setSprite(sprites.get(random));
            *///?} else
            pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            xd += Mth.sin(age * 0.3F + sway) * 0.004;
            zd += Mth.cos(age * 0.3F + sway) * 0.004;
            oRoll = roll;
            roll += 0.08F;
        }

        //? if >=1.21.9 {
        /*@Override
        protected Layer getLayer() {
            return Layer.OPAQUE;
        }
        *///?} else {
        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }
        //?}
    }

    /** Molten iron drop: glows, falls, splats. */
    static final class Drip extends TextureSheetParticle {
        private final SpriteSet sprites;

        Drip(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            //? if >=1.21.9 {
            /*super(level, x, y, z, dx, dy, dz, sprites.first());
            *///?} else
            super(level, x, y, z, dx, dy, dz);
            this.sprites = sprites;
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.gravity = 0.9F;
            this.friction = 0.98F;
            this.lifetime = 18 + random.nextInt(10);
            this.quadSize = 0.07F + random.nextFloat() * 0.04F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            if (onGround) {
                quadSize = Math.min(0.16F, quadSize * 1.08F);
                gravity = 0.0F;
            }
        }

        @Override
        protected int getLightColor(float partialTick) {
            return FULL_BRIGHT;
        }

        //? if >=1.21.9 {
        /*@Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }
        *///?} else {
        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
        //?}
    }

    /** Steam: rises, swells and thins out. */
    static final class Steam extends TextureSheetParticle {
        private final SpriteSet sprites;

        Steam(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            //? if >=1.21.9 {
            /*super(level, x, y, z, dx, dy, dz, sprites.first());
            *///?} else
            super(level, x, y, z, dx, dy, dz);
            this.sprites = sprites;
            this.xd = dx + (random.nextDouble() - 0.5) * 0.02;
            this.yd = dy + 0.02 + random.nextDouble() * 0.02;
            this.zd = dz + (random.nextDouble() - 0.5) * 0.02;
            this.gravity = -0.02F;
            this.friction = 0.94F;
            this.lifetime = 28 + random.nextInt(20);
            this.quadSize = 0.2F + random.nextFloat() * 0.1F;
            this.alpha = 0.75F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            quadSize = Math.min(0.75F, quadSize * 1.03F);
            alpha = 0.75F * (1.0F - age / (float) lifetime);
        }

        //? if >=1.21.9 {
        /*@Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }
        *///?} else {
        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
        //?}
    }

    /** Flat bronze ring on the ground that races out to its radius: the toll telegraph. */
    static final class Ring extends TextureSheetParticle {
        private final float radius;

        Ring(ClientLevel level, double x, double y, double z, double radius, SpriteSet sprites) {
            //? if >=1.21.9 {
            /*super(level, x, y, z, 0.0, 0.0, 0.0, sprites.first());
            *///?} else
            super(level, x, y, z, 0.0, 0.0, 0.0);
            this.radius = (float) Math.max(0.5, radius);
            this.xd = 0.0;
            this.yd = 0.0;
            this.zd = 0.0;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.lifetime = 20;
            this.alpha = 0.9F;
            //? if >=1.21.9 {
            /*setSprite(sprites.get(random));
            *///?} else
            pickSprite(sprites);
            setBoundingBox(new AABB(x - this.radius, y - 0.2, z - this.radius, x + this.radius, y + 0.2, z + this.radius));
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
            }
            float t = age / (float) lifetime;
            alpha = t < 0.6F ? 0.9F : 0.9F * (1.0F - (t - 0.6F) / 0.4F);
        }

        //? if >=1.21.9 {
        /*// flat on the ground, both faces: a quad turned up and one turned down
        @Override
        public void extract(net.minecraft.client.renderer.state.QuadParticleRenderState state, Camera camera, float partialTick) {
            extractRotatedQuad(state, camera, new org.joml.Quaternionf().rotateX(-Mth.HALF_PI), partialTick);
            extractRotatedQuad(state, camera, new org.joml.Quaternionf().rotateX(Mth.HALF_PI), partialTick);
        }

        @Override
        public float getQuadSize(float partialTick) {
            float t = Math.min(1.0F, (age + partialTick) / (lifetime * 0.6F));
            return radius * (0.15F + 0.85F * (1.0F - (1.0F - t) * (1.0F - t)));
        }

        @Override
        protected int getLightColor(float partialTick) {
            return FULL_BRIGHT;
        }
        *///?} else {
        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            Vec3 cam = camera.getPosition();
            float px = (float) (Mth.lerp(partialTick, xo, x) - cam.x());
            float py = (float) (Mth.lerp(partialTick, yo, y) - cam.y()) + 0.02F;
            float pz = (float) (Mth.lerp(partialTick, zo, z) - cam.z());
            float t = Math.min(1.0F, (age + partialTick) / (lifetime * 0.6F));
            float r = radius * (0.15F + 0.85F * (1.0F - (1.0F - t) * (1.0F - t)));
            float u0 = getU0();
            float u1 = getU1();
            float v0 = getV0();
            float v1 = getV1();
            vertex(buffer, px - r, py, pz - r, u0, v0);
            vertex(buffer, px - r, py, pz + r, u0, v1);
            vertex(buffer, px + r, py, pz + r, u1, v1);
            vertex(buffer, px + r, py, pz - r, u1, v0);
            vertex(buffer, px + r, py, pz - r, u1, v0);
            vertex(buffer, px + r, py, pz + r, u1, v1);
            vertex(buffer, px - r, py, pz + r, u0, v1);
            vertex(buffer, px - r, py, pz - r, u0, v0);
        }

        private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v) {
            buffer.addVertex(x, y, z).setUv(u, v).setColor(rCol, gCol, bCol, alpha).setLight(FULL_BRIGHT);
        }
        //?}

        //? if >=1.21.9 {
        /*@Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }
        *///?} else {
        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
        //?}
    }
}
