package com.vnap.dialogue;

import org.jspecify.annotations.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
//? if >=1.21.5 {
/*import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;
*///?} else
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.ArrowNockEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.event.level.NoteBlockEvent;

/**
 * Extra NeoForge event → dialogue title hooks (CreateAERO expand Phase D).
 * Soft / vanilla only — no foreign mod class imports.
 */
public final class ExpandEvents {
    private ExpandEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ItemFishedEvent event) ->
                observe(event.getEntity(), "Catch a Fish"));

        NeoForge.EVENT_BUS.addListener((AnimalTameEvent event) -> {
            if (event.getTamer() != null) observe(event.getTamer(), "Tame an Animal");
        });

        NeoForge.EVENT_BUS.addListener((LivingUseTotemEvent event) -> {
            if (event.getEntity() instanceof Player player) observe(player, "Use a Totem of Undying");
        });

        NeoForge.EVENT_BUS.addListener((LivingShieldBlockEvent event) -> {
            if (event.getEntity() instanceof Player player && event.getBlocked()) {
                observe(player, "Block with a Shield");
            }
        });

        NeoForge.EVENT_BUS.addListener((ArrowNockEvent event) ->
                observe(event.getEntity(), "Draw a Bow"));

        NeoForge.EVENT_BUS.addListener((BonemealEvent event) -> {
            if (event.getPlayer() != null && !event.getLevel().isClientSide() && event.isValidBonemealTarget()) {
                observe(event.getPlayer(), "Use Bone Meal");
            }
        });

        //? if >=1.21.5 {
        /*NeoForge.EVENT_BUS.addListener((AnvilCraftEvent.Post event) ->
                observe(event.getEntity(), "Repair on an Anvil"));
        *///?} else {
        NeoForge.EVENT_BUS.addListener((AnvilRepairEvent event) ->
                observe(event.getEntity(), "Repair on an Anvil"));
        //?}

        NeoForge.EVENT_BUS.addListener((PlayerEnchantItemEvent event) ->
                observe(event.getEntity(), "Enchant an Item"));

        NeoForge.EVENT_BUS.addListener((PlayerXpEvent.PickupXp event) ->
                observe(event.getEntity(), "Pick Up XP"));

        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) ->
                observe(event.getEntity(), "Change Dimension"));

        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) ->
                observe(event.getEntity(), "Respawn After Death"));

        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemSmeltedEvent event) ->
                observe(event.getEntity(), "Take Smelted Items"));

        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent event) ->
                observe(event.getEntity(), "Craft an Item Nearby"));

        NeoForge.EVENT_BUS.addListener((EntityMountEvent event) -> {
            if (!event.isMounting()) return;
            Entity mounting = event.getEntityMounting();
            if (!(mounting instanceof Player player)) return;
            String path = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntityBeingMounted().getType()).getPath();
            if (path.contains("boat")) observe(player, "Mount a Boat");
            else if (path.contains("horse") || path.contains("donkey") || path.contains("mule")
                    || path.contains("camel") || path.contains("llama") || path.contains("strider")) {
                observe(player, "Mount a Horse");
            } else if (path.contains("minecart")) observe(player, "Ride in a Moving Minecart");
            else observe(player, "Mount a Mob");
        });

        NeoForge.EVENT_BUS.addListener((NoteBlockEvent.Play event) -> {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            ServerPlayer nearest = nearestPlayer(level, Vec3.atCenterOf(event.getPos()), 24.0);
            if (nearest != null) WorldEvents.observe(nearest, Vec3.atCenterOf(event.getPos()),
                    "Play a Note Block", false);
        });

        NeoForge.EVENT_BUS.addListener((ItemTossEvent event) ->
                observe(event.getPlayer(), "Throw an Item"));

        NeoForge.EVENT_BUS.addListener((PlayerDestroyItemEvent event) ->
                observe(event.getEntity(), "Tool Breaks"));

        NeoForge.EVENT_BUS.addListener((PlayerBrewedPotionEvent event) ->
                observe(event.getEntity(), "Brew a Potion"));

        NeoForge.EVENT_BUS.addListener((CriticalHitEvent event) -> {
            if (event.isCriticalHit()) observe(event.getEntity(), "Land a Critical Hit");
        });

        NeoForge.EVENT_BUS.addListener((SweepAttackEvent event) ->
                observe(event.getEntity(), "Sweep Attack"));

        NeoForge.EVENT_BUS.addListener((EntityStruckByLightningEvent event) -> {
            Entity hit = event.getEntity();
            if (!(hit.level() instanceof ServerLevel level)) return;
            Player nearest = nearestPlayer(level, hit.position(), 32.0);
            if (nearest != null) {
                WorldEvents.observe(nearest, hit.position(), "Lightning Hits Nearby", true);
            }
        });

        NeoForge.EVENT_BUS.addListener((BabyEntitySpawnEvent event) -> {
            LivingEntity child = event.getChild();
            if (child == null || child instanceof Villager) return; // existing villager baby lines
            Player player = event.getCausedByPlayer();
            if (player == null && child.level() instanceof ServerLevel level) {
                player = nearestPlayer(level, child.position(), 16.0);
            }
            if (player != null) observe(player, "Animals Have a Baby");
        });
    }

    private static void observe(Player player, String title) {
        WorldEvents.observe(player, player.position(), title, false);
    }

    private static @Nullable ServerPlayer nearestPlayer(ServerLevel level, Vec3 pos, double range) {
        ServerPlayer best = null;
        double bestDist = range * range;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            double d = p.distanceToSqr(pos);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}
