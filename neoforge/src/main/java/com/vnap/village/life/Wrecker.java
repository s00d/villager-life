package com.vnap.village.life;

import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.script.WorkerControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * {@link Hands} the other way round: takes the old house on a hall's ground apart, top down. The villager walks
 * within reach (a little further than a builder's: it pulls a beam down with the axe), works on the cell with
 * swings and hit sounds and breaks it. A cell nobody can reach goes to the back; when none can be reached the
 * nearest is pulled down anyway, so a house never stands half taken apart for good.
 */
final class Wrecker {
    static final double REACH = 4.5;
    private static final int WALK_LIMIT = 200;
    /** Ticks between looks for a standing spot after one failed: every look is a few path searches. */
    static final int PICK_PAUSE = 10;
    /** Cells in a row out of reach before the nearest is pulled down from below (a pass over hundreds took minutes). */
    private static final int DEFER_PASS = 8;

    private enum Phase { PICK, WALK, WORK }

    enum Result { WORKING, CLEARED, DONE, STUCK }

    private Phase phase = Phase.PICK;
    private @Nullable BlockPos target;
    private int timer;
    private int deferred;
    private int farWalks;
    private long pickAfter;

    Result tick(ServerLevel level, Villager villager, HouseProject hall) {
        while (!hall.clearing.isEmpty() && HomeProjects.cleared(level, hall, hall.clearing.get(0))) hall.clearing.remove(0);
        if (hall.clearing.isEmpty()) {
            reset();
            return Result.DONE;
        }
        BlockPos next = hall.clearing.get(0);
        if (!next.equals(target)) {
            target = next;
            phase = Phase.PICK;
            timer = 0;
        }
        return switch (phase) {
            case PICK -> pick(level, villager, hall, next);
            case WALK -> walk(level, villager, hall, next);
            case WORK -> work(level, villager, hall, next);
        };
    }

    void reset() {
        phase = Phase.PICK;
        target = null;
        timer = 0;
    }

    private Result pick(ServerLevel level, Villager villager, HouseProject hall, BlockPos next) {
        WorkerControl.hold(villager, new ItemStack(Items.IRON_AXE));
        if (inReach(villager, next)) {
            phase = Phase.WORK;
            timer = 0;
            return Result.WORKING;
        }
        if (level.getGameTime() < pickAfter) return Result.WORKING;
        // the roof is taken down from the floors under it, down to the house's lowest cell to go, not from a ravine below
        int bottom = hall.clearing.stream().mapToInt(BlockPos::getY).min().orElse(next.getY());
        BlockPos spot = Hands.findSpot(level, villager, Set.of(), next, REACH, Math.min(Hands.groundUnder(level, next), bottom - 1));
        if (spot == null) {
            pickAfter = level.getGameTime() + PICK_PAUSE;
            return defer(level, villager, hall, next);
        }
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, 0.6F, 0));
        phase = Phase.WALK;
        timer = 0;
        return Result.WORKING;
    }

    private Result walk(ServerLevel level, Villager villager, HouseProject hall, BlockPos next) {
        timer++;
        if (inReach(villager, next) && villager.onGround()) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getNavigation().stop();
            phase = Phase.WORK;
            timer = 0;
            return Result.WORKING;
        }
        boolean idle = !villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET) && timer > 20;
        if (timer > WALK_LIMIT || idle) return defer(level, villager, hall, next);
        return Result.WORKING;
    }

    private Result work(ServerLevel level, Villager villager, HouseProject hall, BlockPos next) {
        BlockState state = level.getBlockState(next);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(next));
        if (timer % Hands.SWING_EVERY == 0) {
            com.vnap.Mc.swing(villager, InteractionHand.MAIN_HAND);
            SoundType sound = state.getSoundType(level, next, villager);
            level.playSound(null, next, sound.getHitSound(), SoundSource.BLOCKS, sound.getVolume() * 0.5F, sound.getPitch());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.02);
        }
        if (++timer < Hands.workTicks(level, next, state)) return Result.WORKING;
        HomeProjects.clearOne(level, hall, next);
        deferred = 0;
        phase = Phase.PICK;
        timer = 0;
        return Result.CLEARED;
    }

    private Result defer(ServerLevel level, Villager villager, HouseProject hall, BlockPos next) {
        hall.clearing.remove(next);
        hall.clearing.add(next);
        phase = Phase.PICK;
        timer = 0;
        if (++deferred >= Math.min(hall.clearing.size(), DEFER_PASS)) {
            // nothing in reach from anywhere a villager can stand: the nearest cell is pulled down from below
            deferred = 0;
            BlockPos nearest = hall.clearing.stream()
                    .min(java.util.Comparator.comparingDouble(pos -> pos.distToCenterSqr(villager.position()))).orElse(next);
            if (nearest.distToCenterSqr(villager.position()) > 10.0 * 10.0) {
                // walked over before and still nowhere near: the site waits a day, not a whole day of walking at it
                if (++farWalks > 3) {
                    farWalks = 0;
                    return Result.STUCK;
                }
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(nearest, 0.6F, 3));
                return Result.WORKING;
            }
            farWalks = 0;
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(nearest));
            com.vnap.Mc.swing(villager, InteractionHand.MAIN_HAND);
            HomeProjects.clearOne(level, hall, nearest);
            return Result.CLEARED;
        }
        return Result.WORKING;
    }

    private static boolean inReach(Villager villager, BlockPos pos) {
        return Hands.covers(villager.position(), pos, REACH);
    }
}
