package com.vnap.village.script;

import com.vnap.dialogue.Speech;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class WorkerControl {
    public static final String WORKER_TAG = "vnap_script_worker";

    private WorkerControl() {
    }

    public static List<Villager> pickWorkers(ServerLevel level, BlockPos near, int min, int max) {
        List<Villager> candidates = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(near).inflate(48.0),
                v -> v.isAlive() && !v.isBaby() && !v.isSleeping());
        candidates.sort(Comparator.comparingDouble(v -> v.distanceToSqr(near.getX() + 0.5, near.getY(), near.getZ() + 0.5)));
        if (candidates.size() < min) {
            return List.of();
        }
        List<Villager> picked = new ArrayList<>(candidates.subList(0, Math.min(max, candidates.size())));
        for (Villager villager : picked) {
            villager.addTag(WORKER_TAG);
        }
        return picked;
    }

    public static void clearTags(List<UUID> workerIds, ServerLevel level) {
        for (UUID id : workerIds) {
            if (level.getEntity(id) instanceof Villager villager) {
                villager.removeTag(WORKER_TAG);
                hold(villager, ItemStack.EMPTY);
                villager.getNavigation().stop();
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            }
        }
    }

    /** What the villager shows in its crossed arms while working; never touches its inventory. */
    public static void hold(Villager villager, ItemStack stack) {
        villager.setItemSlot(EquipmentSlot.MAINHAND, stack);
        villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public static void forceWalk(Villager villager, BlockPos dest, double speed) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, speed);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(dest, (float) speed, 1));
    }

    public static void forceIdleLook(Villager villager, BlockPos lookAt) {
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(lookAt));
    }

    public static boolean arrived(Villager villager, BlockPos slot) {
        double dx = villager.getX() - (slot.getX() + 0.5);
        double dz = villager.getZ() - (slot.getZ() + 0.5);
        return dx * dx + dz * dz <= 1.75 * 1.75 && !villager.getNavigation().isInProgress();
    }

    public static void softTeleport(Villager villager, BlockPos slot) {
        villager.teleportTo(slot.getX() + 0.5, slot.getY(), slot.getZ() + 0.5);
        villager.getNavigation().stop();
    }
}
