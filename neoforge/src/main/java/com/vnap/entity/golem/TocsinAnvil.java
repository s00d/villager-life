package com.vnap.entity.golem;

import com.vnap.entity.VillageEntities;
import com.vnap.fx.VillageParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A falling anvil that never becomes a block: it lands, hurts, and breaks apart. */
public final class TocsinAnvil extends Entity {
    private static final int MAX_LIFE = 100;
    private @Nullable TocsinGolem owner;
    private double groundY;

    public TocsinAnvil(EntityType<? extends TocsinAnvil> type, Level level) {
        super(type, level);
    }

    public static @Nullable TocsinAnvil drop(ServerLevel level, TocsinGolem owner, double x, double groundY, double z, double height) {
        TocsinAnvil anvil = com.vnap.Mc.create(VillageEntities.TOCSIN_ANVIL, level);
        if (anvil == null) {
            return null;
        }
        anvil.owner = owner;
        anvil.groundY = groundY;
        com.vnap.Mc.snap(anvil, x, groundY + height, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(anvil);
        return anvil;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement().add(0.0, -0.06, 0.0).scale(0.98);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (tickCount % 2 == 0) {
            for (int i = 0; i < 8; i++) {
                double a = i / 8.0 * Math.PI * 2.0;
                server.sendParticles(ParticleTypes.SMOKE, getX() + Math.cos(a) * 1.1, groundY + 0.1,
                        getZ() + Math.sin(a) * 1.1, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (onGround() || verticalCollision || getY() <= groundY + 0.05 || tickCount > MAX_LIFE) {
            land(server);
        }
    }

    private void land(ServerLevel server) {
        server.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.7F);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ANVIL.defaultBlockState()),
                getX(), getY() + 0.3, getZ(), 24, 0.4, 0.2, 0.4, 0.15);
        server.sendParticles(VillageParticles.IRON_SPARK, getX(), getY() + 0.2, getZ(), 10, 0.3, 0.1, 0.3, 0.2);
        if (owner != null && owner.isAlive()) {
            owner.hurtAround(getBoundingBox().getCenter(), 1.7, 12.0F, 0.4);
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    //? if >=1.21.2 {
    /*@Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }
    *///?}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    //? if >=1.21.6 {
    /*protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
    *///?} else {
    protected void readAdditionalSaveData(CompoundTag tag) {
    //?}
    }

    @Override
    //? if >=1.21.6 {
    /*protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
    *///?} else {
    protected void addAdditionalSaveData(CompoundTag tag) {
    //?}
    }
}
