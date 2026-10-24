package com.vnap.village.life;

import com.google.common.collect.ImmutableMap;
import com.vnap.village.script.WorkerControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Villagers without a trade (and nitwits) pick a flower near the village now and then and hand it to a
 * neighbour or a player. Twice a day at most; the flower block is really gone.
 */
final class FlowerBehavior extends Behavior<Villager> {
    static final int REACH = 10;
    static final int PER_DAY = 2;
    private static final int CHECK_EVERY = 200;

    private final LifeGate gate;
    private final ErrandRunner runner = new ErrandRunner();
    private @Nullable Errand errand;
    private @Nullable Errand then;
    private boolean finished;
    private long nextCheck;
    private long day = -1L;
    private int given;
    private final java.util.Set<BlockPos> unreachable = new java.util.HashSet<>();

    FlowerBehavior(LifeGate gate) {
        super(ImmutableMap.of(), 600, 1200);
        this.gate = gate;
    }

    static boolean idleHands(Villager villager) {
        return !villager.isBaby() && (com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE)
                || com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NITWIT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        if (now < nextCheck || gate.busy || com.vnap.dialogue.Infants.minding(level, villager)) {
            return false;
        }
        nextCheck = now + CHECK_EVERY + villager.getRandom().nextInt(CHECK_EVERY);
        long today = com.vnap.dialogue.Family.day(level);
        if (today != day) {
            day = today;
            given = 0;
            unreachable.clear();
        }
        if (given >= PER_DAY || !idleHands(villager) || !com.vnap.Mc.isDay(level) || villager.isSleeping()
                || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)) {
            return false;
        }
        BlockPos flower = flower(level, villager, unreachable);
        if (flower == null) {
            return false;
        }
        // a flower on a ledge or behind a fence would keep everyone standing below it the whole day
        net.minecraft.world.level.pathfinder.Path path = villager.getNavigation().createPath(flower, 1);
        if (path == null || !path.canReach()) {
            unreachable.add(flower);
            return false;
        }
        errand = pick(level, villager, flower);
        return true;
    }

    private static @Nullable BlockPos flower(ServerLevel level, Villager villager, java.util.Set<BlockPos> skip) {
        BlockPos home = villager.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(home.offset(-REACH, -3, -REACH), home.offset(REACH, 3, REACH))) {
            if (level.getBlockState(pos).is(BlockTags.SMALL_FLOWERS) && !skip.contains(pos) && Life.allowed(level, villager, home, pos, REACH)) {
                found.add(pos.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(home)));
        return found.isEmpty() ? null : found.get(0);
    }

    private Errand pick(ServerLevel level, Villager villager, BlockPos flower) {
        return Errand.at("flower", flower, ItemStack.EMPTY, 20, SoundEvents.GRASS_BREAK, null, () -> {
            BlockState state = level.getBlockState(flower);
            if (!state.is(BlockTags.SMALL_FLOWERS)) {
                return;
            }
            level.removeBlock(flower, false);
            LivingEntity friend = friend(level, villager);
            if (friend != null) {
                then = give(level, friend, new ItemStack(state.getBlock().asItem()));
            }
        });
    }

    private static @Nullable LivingEntity friend(ServerLevel level, Villager villager) {
        AABB around = villager.getBoundingBox().inflate(16.0);
        Player player = level.getNearestPlayer(villager, 16.0);
        if (player != null && !player.isSpectator()) {
            return player;
        }
        List<Villager> others = level.getEntitiesOfClass(Villager.class, around, other -> !other.equals(villager) && other.isAlive());
        return others.isEmpty() ? null : others.get(villager.getRandom().nextInt(others.size()));
    }

    private Errand give(ServerLevel level, LivingEntity friend, ItemStack flower) {
        return Errand.to("give_flower", friend, flower, 20, SoundEvents.VILLAGER_YES, ParticleTypes.HEART, () -> {
            given++;
            if (friend instanceof Player player) {
                ItemEntity item = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), flower.copy());
                item.setPickUpDelay(0);
                level.addFreshEntity(item);
            }
        });
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = true;
        finished = false;
        then = null;
        runner.reset();
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Errand now = errand;
        if (now == null) {
            finished = true;
            return;
        }
        switch (runner.tick(level, villager, now)) {
            case DONE -> {
                errand = then;
                then = null;
                runner.reset();
                finished = errand == null;
            }
            case FAILED -> finished = true;
            default -> {
            }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return !com.vnap.dialogue.Infants.minding(level, villager) && !finished && villager.isAlive() && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = false;
        errand = null;
        then = null;
        runner.reset();
        WorkerControl.hold(villager, ItemStack.EMPTY);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }
}
