package com.vnap.entity.golem;

import com.vnap.sound.GolemSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A shed armour plate that skitters on four rivet legs. */
public final class PlateCrawler extends Monster {
    public static final int CAP = 4;

    public PlateCrawler(EntityType<? extends PlateCrawler> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, TocsinGolem.class, PlateCrawler.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(GolemSounds.CRAWLER, 0.4F, 1.0F);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GolemSounds.CLANG;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CHAIN_BREAK;
    }

    @Override
    public float getVoicePitch() {
        return 1.4F;
    }

    /** The Core's spark in its back, crackling; dust where it scuttles. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() || !isAlive()) return;
        if (random.nextInt(8) == 0) {
            level().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, getX() + (random.nextDouble() - 0.5) * 0.6,
                    getY() + getBbHeight(), getZ() + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.05, 0.0);
        }
        if (onGround() && getDeltaMovement().horizontalDistanceSqr() > 0.001 && tickCount % 4 == 0) {
            level().addParticle(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 0.05, getZ(), 0.0, 0.0, 0.0);
        }
    }
}
