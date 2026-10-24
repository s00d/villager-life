package com.vnap.progress;

import com.vnap.VillagerLife;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;

/** Advancements use the {@code minecraft:impossible} trigger; the mod awards them where things happen. */
public final class VillageAdvancements {
    private VillageAdvancements() {
    }

    public static void award(ServerPlayer player, String path) {
        MinecraftServer server = com.vnap.Mc.serverLevel(player).getServer();
        if (server == null) {
            return;
        }
        AdvancementHolder holder = server.getAdvancements().get(VillagerLife.id(path));
        if (holder != null) {
            player.getAdvancements().award(holder, "done");
        }
    }

    /** For code that holds a Player: only a server player earns anything (mock players of the tests, clients do not). */
    public static void award(net.minecraft.world.entity.player.Player player, String path) {
        if (player instanceof ServerPlayer server) award(server, path);
    }

    public static void awardNear(ServerLevel level, BlockPos pos, double radius, String path) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(radius), p -> !p.isSpectator())) {
            award(player, path);
        }
    }
}
