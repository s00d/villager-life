package com.vnap.item;

import com.vnap.entity.VillageEntities;
import com.vnap.entity.golem.TocsinGolem;
import com.vnap.fx.VillageParticles;
import com.vnap.mood.VillageMoodApi;
import com.vnap.progress.VillageAdvancements;
import com.vnap.sound.GolemSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
//? if <1.21.2
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The elder's staff: hold use to charge the little bell, release to toll it.
 * A toll blesses the village (+10), or curses it while sneaking (-10), and breaks a golem's own toll.
 */
public final class BlessingStaffItem extends Item {
    public static final int CHARGE_TICKS = 20;
    private static final int COOLDOWN_TICKS = 40;
    private static final int COUNTER_COOLDOWN_TICKS = 200;
    private static final double COUNTER_RANGE = 16.0;

    public BlessingStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // the pose is the staff's own (its client extension); Fabric has no custom kind: none, and the pose is set alike
        //? if >=1.21.2 || fabric {
        /*return UseAnim.NONE;
        *///?} else
        return UseAnim.CUSTOM;
    }

    @Override
    //? if >=1.21.2 {
    /*public net.minecraft.world.InteractionResult use(Level level, Player player, InteractionHand hand) {
    *///?} else
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (com.vnap.Mc.onCooldown(player, stack)) {
            return com.vnap.Mc.fail(stack);
        }
        player.startUsingItem(hand);
        return com.vnap.Mc.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        int held = getUseDuration(stack, user) - remaining;
        if (level.isClientSide() && held <= CHARGE_TICKS && held % 4 == 0) {
            double a = held / (double) CHARGE_TICKS * Math.PI * 2.0;
            level.addParticle(VillageParticles.IRON_SPARK, user.getX() + Math.cos(a) * 0.6, user.getY() + 2.1,
                    user.getZ() + Math.sin(a) * 0.6, 0.0, 0.02, 0.0);
        }
        if (!level.isClientSide() && held == CHARGE_TICKS) {
            level.playSound(null, user.blockPosition(), GolemSounds.STAFF_CHARGE, SoundSource.PLAYERS, 0.6F, 1.0F);
        }
    }

    @Override
    //? if >=1.21.2 {
    /*public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        release(stack, level, user, remaining);
        return true;
    }
    *///?} else {
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        release(stack, level, user, remaining);
    }
    //?}

    private void release(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (!(user instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) {
            return;
        }
        int held = getUseDuration(stack, user) - remaining;
        if (held < CHARGE_TICKS) {
            server.playSound(null, player.blockPosition(), GolemSounds.STAFF_FIZZLE, SoundSource.PLAYERS, 0.7F, 1.0F);
            return;
        }
        toll(server, player, !player.isShiftKeyDown(), stack);
    }

    private void toll(ServerLevel level, ServerPlayer player, boolean bless, ItemStack stack) {
        com.vnap.Mc.swing(player, player.getUsedItemHand());
        level.playSound(null, player.blockPosition(), GolemSounds.STAFF_TOLL, SoundSource.PLAYERS, 1.4F, bless ? 1.0F : 0.75F);
        VillageParticles.ring(level, player.getX(), player.getY() + 0.05, player.getZ(), 6.0);
        boolean countered = false;
        for (TocsinGolem golem : level.getEntities(VillageEntities.TOCSIN_GOLEM,
                player.getBoundingBox().inflate(COUNTER_RANGE), Entity::isAlive)) {
            if (golem.counterToll()) {
                countered = true;
                level.sendParticles(VillageParticles.IRON_SPARK, golem.getX(), golem.getY() + 2.6, golem.getZ(), 20, 0.5, 0.4, 0.5, 0.3);
            }
        }
        if (countered) {
            VillageAdvancements.award(player, "tocsin/counter_toll");
        }
        boolean nudged = VillageMoodApi.nudge(level, player.blockPosition(), bless ? 10.0F : -10.0F);
        if (nudged) {
            burst(level, player.blockPosition(), bless);
        }
        com.vnap.Mc.cooldown(player, stack, countered ? COUNTER_COOLDOWN_TICKS : COOLDOWN_TICKS);
    }

    private static void burst(ServerLevel level, BlockPos origin, boolean bless) {
        VillageMoodApi.VillageRef ref = VillageMoodApi.resolve(level, origin);
        if (ref == null) {
            return;
        }
        Vec3 at = Vec3.atCenterOf(ref.meetingPos());
        var spark = bless ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.SOUL;
        var mote = bless ? ParticleTypes.END_ROD : ParticleTypes.SMOKE;
        for (int i = 0; i < 28; i++) {
            double angle = i / 28.0 * Math.PI * 2.0;
            double x = at.x + Math.cos(angle) * 2.4;
            double z = at.z + Math.sin(angle) * 2.4;
            level.sendParticles(spark, x, at.y + 0.3, z, 1, 0.0, 0.08, 0.0, 0.01);
            level.sendParticles(mote, x, at.y + 1.1, z, 1, 0.02, 0.04, 0.02, 0.01);
        }
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
                new AABB(ref.meetingPos()).inflate(64.0), v -> v.isAlive() && !v.isBaby());
        int voiced = 0;
        for (Villager villager : villagers) {
            level.sendParticles(mote, villager.getX(), villager.getY() + 1.2, villager.getZ(), 8, 0.25, 0.4, 0.25, 0.02);
            if (voiced < 4) {
                level.playSound(null, villager.blockPosition(), bless ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO,
                        SoundSource.NEUTRAL, 0.8F, bless ? 1.15F : 0.85F);
                voiced++;
            }
        }
    }
}
