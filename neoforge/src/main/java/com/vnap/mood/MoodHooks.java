package com.vnap.mood;

import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import com.vnap.block.AltarBlockEntity;
import com.vnap.block.AltarState;
import com.vnap.block.MonumentBlockEntity;
import com.vnap.block.LifeBlocks;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.golem.TocsinGolem;
import com.vnap.village.script.VillageScriptDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.entity.decoration.Painting;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Applies §2.4 mood deltas from world events. Additive to dialogue hooks. */
public final class MoodHooks {
    private static final Map<UUID, Long> BELL_CD = new HashMap<>();
    private static final Map<UUID, Long> TRADE_CD = new HashMap<>();
    /** How long a block a player broke can be set back to take its penalty back. */
    public static final long UNDO_TICKS = 20L * 60L * 5L;
    private static final List<Damage> DAMAGE = new ArrayList<>();

    /** What a player's break cost a village: the same block set back where it was, by the same player, returns it. */
    private record Damage(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos, UUID player,
                          net.minecraft.world.level.block.Block block, long villageId, BlockPos meeting, float mood, float standing,
                          long until) {
    }

    private MoodHooks() {
    }

    public static void register() {
        // game times of the last world: in the next singleplayer world they would block the bonus for a long while
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, (ServerStoppingEvent event) -> {
            BELL_CD.clear();
            TRADE_CD.clear();
            DAMAGE.clear();
        });
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, (LivingDeathEvent event) -> onDeath(event));
        // a zombie villager cured (the only way one turns back into a villager)
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Post.class, (net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Post event) -> {
            if (event.getEntity() instanceof net.minecraft.world.entity.monster.ZombieVillager && event.getOutcome() instanceof Villager cured
                    && cured.level() instanceof ServerLevel level) onCure(level, cured.blockPosition());
        });
        // a raid beaten: every player who fought it is given the Hero of the Village
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added.class, (net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer hero && event.getEffectInstance().is(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE)
                    && com.vnap.Mc.levelOf(hero) instanceof ServerLevel level && event.getOldEffectInstance() == null) onRaidVictory(level, hero.blockPosition());
        });
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, (EntityJoinLevelEvent event) -> {
            if (VillagerHome.placingOwnPainting()) return;
            // a painting the player hangs, not one that comes back with its chunk
            if (!(event.getEntity() instanceof Painting painting) || event.loadedFromDisk()) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            VillagerHome.onPainting(level, painting.blockPosition(), com.vnap.dialogue.Family.day(level));
        });
        NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, (BlockEvent.BreakEvent event) -> onBreak(event));
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, (BlockEvent.EntityPlaceEvent event) -> {
            if (wakesHomes(event.getPlacedBlock())) VillagerHome.noteStructureChange();
            if (event.getEntity() instanceof Player player && event.getLevel() instanceof ServerLevel level) {
                restored(level, player, event.getPos(), event.getPlacedBlock());
            }
        });
        NeoForge.EVENT_BUS.addListener(ExplosionEvent.Detonate.class, (ExplosionEvent.Detonate event) -> {
            // only the player's blast: a creeper that blows up on its own is not the player's doing
            if (event.getLevel() instanceof ServerLevel level && event.getExplosion().getIndirectSourceEntity() instanceof Player) {
                VillageMoodApi.applyAt(level, BlockPos.containing(event.getExplosion().center()), -18.0F, -10.0F, "explode");
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, (PlayerInteractEvent.RightClickBlock event) -> {
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            BlockState state = level.getBlockState(event.getPos());
            if (state.is(Blocks.BELL)) {
                TocsinGolem.onBellRung(level, event.getPos(), event.getEntity());
                long now = level.getGameTime();
                Long last = BELL_CD.get(event.getEntity().getUUID());
                if (last != null && now - last < 20L * 60L) {
                    return;
                }
                VillageMoodApi.VillageRef ref = VillageMoodApi.resolve(level, event.getPos());
                if (ref != null) {
                    MoodState mood = VillageMoodSavedData.get(level).get(ref.villageId());
                    if (mood != null && mood.mood > 0.0F) {
                        VillageMoodApi.apply(level, ref.villageId(), ref.meetingPos(), 2.0F, 0.0F, "bell");
                        BELL_CD.put(event.getEntity().getUUID(), now);
                    }
                }
            }
            if (state.is(LifeBlocks.ALTAR_CORE) || state.is(Blocks.LAVA)) {
                maybeDesecrate(level, event.getPos());
            }
        });
    }

    public static void onTradeCompleted(ServerLevel level, Villager villager, Player player) {
        long now = level.getGameTime();
        Long last = TRADE_CD.get(player.getUUID());
        if (last != null && now - last < 40L) {
            return;
        }
        TRADE_CD.put(player.getUUID(), now);
        VillageMoodApi.applyAt(level, villager.blockPosition(), 4.0F, 1.0F, "trade");
    }

    public static void onGiftFood(ServerLevel level, BlockPos pos) {
        VillageMoodApi.applyAt(level, pos, 6.0F, 2.0F, "gift_food");
    }

    public static void onCure(ServerLevel level, BlockPos pos) {
        VillageMoodApi.applyAt(level, pos, 15.0F, 10.0F, "cure");
    }

    public static void onRaidVictory(ServerLevel level, BlockPos pos) {
        VillageMoodApi.applyAt(level, pos, 20.0F, 8.0F, "raid_victory");
    }

    public static void onGriefObserved(ServerLevel level, BlockPos pos, boolean homeBreak) {
        if (homeBreak) {
            VillageMoodApi.applyAt(level, pos, -10.0F, -4.0F, "break_home");
        } else {
            VillageMoodApi.applyAt(level, pos, -12.0F, -5.0F, "grief_fire");
        }
    }

    public static void onSteal(ServerLevel level, BlockPos pos) {
        VillageMoodApi.applyAt(level, pos, -8.0F, -3.0F, "steal");
    }

    private static boolean wakesHomes(BlockState state) {
        return state.getBlock() instanceof BedBlock
                || state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof SignBlock
                || state.getBlock() instanceof com.vnap.block.town.HouseSignBlock;
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(com.vnap.Mc.levelOf(entity) instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        if (entity instanceof Villager) {
            VillageMoodApi.applyAt(level, entity.blockPosition(), -40.0F, -50.0F, "kill_villager");
            maybeDesecrate(level, entity.blockPosition());
        } else if (entity instanceof IronGolem) {
            VillageMoodApi.applyAt(level, entity.blockPosition(), -25.0F, -20.0F, "kill_golem");
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        VillageMoodSavedData data = VillageMoodSavedData.get(level);
        Map<Long, float[]> before = new HashMap<>();
        for (MoodState mood : data.all()) before.put(mood.villageId, new float[]{mood.mood, mood.standing});
        charge(level, event);
        // an altar core or a monument takes its rite with it: setting the block back does not bring that back
        BlockState broken = event.getState();
        if (event.isCanceled() || broken.is(LifeBlocks.ALTAR_CORE) || broken.is(LifeBlocks.MONUMENT_PLAQUE)
                || broken.is(LifeBlocks.MONUMENT_BASE) || broken.is(LifeBlocks.MONUMENT_PILLAR)) {
            return;
        }
        long now = level.getGameTime();
        DAMAGE.removeIf(damage -> damage.until() < now);
        for (MoodState mood : data.all()) {
            float[] was = before.getOrDefault(mood.villageId, new float[2]);
            float lostMood = Math.min(0.0F, mood.mood - was[0]);
            float lostStanding = Math.min(0.0F, mood.standing - was[1]);
            if (lostMood < 0.0F || lostStanding < 0.0F) {
                DAMAGE.add(new Damage(level.dimension(), event.getPos().immutable(), event.getPlayer().getUUID(), broken.getBlock(),
                        mood.villageId, mood.meetingPos, lostMood, lostStanding, now + UNDO_TICKS));
            }
        }
    }

    /** The player set back a block it broke a few minutes ago (either half of a bed or a door): the penalty goes back. */
    private static void restored(ServerLevel level, Player player, BlockPos pos, BlockState placed) {
        if (DAMAGE.isEmpty()) return;
        long now = level.getGameTime();
        DAMAGE.removeIf(damage -> damage.until() < now);
        List<Damage> undone = new ArrayList<>();
        for (Damage damage : DAMAGE) {
            if (damage.dimension().equals(level.dimension()) && damage.player().equals(player.getUUID())
                    && placed.is(damage.block()) && damage.pos().distManhattan(pos) <= 1) {
                undone.add(damage);
            }
        }
        DAMAGE.removeAll(undone);
        for (Damage damage : undone) {
            VillageMoodApi.apply(level, damage.villageId(), damage.meeting(), -damage.mood(), -damage.standing(), "undo");
        }
    }

    private static void charge(ServerLevel level, BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        if (wakesHomes(state)) VillagerHome.noteStructureChange();
        // the cradle a newborn lies in stays
        if (com.vnap.dialogue.Infants.occupied(level, pos)) {
            event.setCanceled(true);
            return;
        }
        VillagerHome.onBreak(level, pos, state, com.vnap.dialogue.Family.day(level));
        if (state.is(LifeBlocks.MONUMENT_PLAQUE)
                || state.is(LifeBlocks.MONUMENT_BASE)
                || state.is(LifeBlocks.MONUMENT_PILLAR)) {
            VillageMoodApi.VillageRef ref = VillageMoodApi.resolve(level, pos);
            if (ref != null) {
                MoodState mood = VillageMoodSavedData.get(level).getOrCreate(ref.villageId(), ref.meetingPos());
                mood.flags = MoodFlags.clear(mood.flags, MoodFlags.HAS_MONUMENT);
                mood.monumentPos = null;
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MonumentBlockEntity monument) {
                    monument.setActive(false);
                }
                VillageMoodSavedData.get(level).markDirty();
                VillageMoodApi.apply(level, ref.villageId(), ref.meetingPos(), -30.0F, -40.0F, "destroy_monument");
                if (!ref.villagers().isEmpty()) {
                    Speech.line(ref.villagers().get(0), "Monument Defiled",
                            "monument_defiled:" + ref.villageId(), true);
                }
            }
        }
        if (state.is(LifeBlocks.ALTAR_CORE)
                || state.is(LifeBlocks.ALTAR_BASE)
                || state.is(LifeBlocks.ALTAR_PILLAR)) {
            if (state.is(LifeBlocks.ALTAR_CORE)) {
                if (level.getBlockEntity(pos) instanceof AltarBlockEntity altar) {
                    altar.setAltarState(AltarState.RUINED);
                }
                VillageMoodApi.VillageRef ref = VillageMoodApi.resolve(level, pos);
                if (ref != null) {
                    MoodState mood = VillageMoodSavedData.get(level).getOrCreate(ref.villageId(), ref.meetingPos());
                    boolean peak = mood.mood >= 90.0F;
                    mood.flags = MoodFlags.clear(mood.flags, MoodFlags.HAS_ALTAR);
                    mood.altarPos = null;
                    VillageMoodSavedData.get(level).markDirty();
                    if (peak) {
                        VillageMoodApi.setExact(level, mood, -100.0F, true);
                        level.playSound(null, pos, com.vnap.sound.GolemSounds.RITE_SHATTER, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.VILLAGER_NO, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 0.8F);
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 16, 0.4, 0.4, 0.4, 0.02);
                        if (!ref.villagers().isEmpty()) {
                            Speech.line(ref.villagers().get(0), "Altar Broken at the Peak",
                                    "altar_peak:" + ref.villageId(), true);
                        }
                        return;
                    }
                }
            }
            VillageMoodApi.applyAt(level, pos, -5.0F, -10.0F, "destroy_altar");
        }
    }

    private static void maybeDesecrate(ServerLevel level, BlockPos pos) {
        VillageMoodApi.VillageRef ref = VillageMoodApi.resolve(level, pos);
        if (ref == null) {
            return;
        }
        MoodState state = VillageMoodSavedData.get(level).get(ref.villageId());
        if (state == null || state.altarPos == null) {
            return;
        }
        if (!state.altarPos.closerThan(pos, 6.0)) {
            return;
        }
        VillageMoodApi.apply(level, ref.villageId(), ref.meetingPos(), -15.0F, -5.0F, "desecrate");
        if (level.getBlockEntity(state.altarPos) instanceof AltarBlockEntity altar) {
            if (altar.altarState() == AltarState.SMOULDERING) {
                altar.setAltarState(AltarState.READY);
                if (altar.consumeReadyAnnounce() && !ref.villagers().isEmpty()) {
                    Speech.line(ref.villagers().get(0), "Altar Ready to Summon",
                            "altar_ready:" + ref.villageId(), true);
                }
            }
        }
        VillageScriptDirector.tryStartSummon(level, state);
    }
}
