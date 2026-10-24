package com.vnap.village.script;

import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import com.vnap.block.AltarBlockEntity;
import com.vnap.block.AltarState;
import com.vnap.block.MonumentBlockEntity;
import com.vnap.block.LifeBlocks;
import com.vnap.dialogue.Speech;
import com.vnap.block.AltarCoreBlock;
import com.vnap.entity.VillageEntities;
import com.vnap.entity.golem.GolemLines;
import com.vnap.entity.golem.TocsinGolem;
import com.vnap.fx.VillageParticles;
import com.vnap.progress.VillageAdvancements;
import com.vnap.sound.GolemSounds;
import com.vnap.mood.MoodConfig;
import com.vnap.mood.MoodFlags;
import com.vnap.mood.MoodState;
import com.vnap.mood.ScriptKind;
import com.vnap.mood.VillageMoodApi;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.mood.VillageRiteActions;
import com.vnap.mood.VillageRites;
import com.vnap.network.ScriptShakePayload;
import com.vnap.village.build.BuildJob;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class VillageScriptDirector {
    /** A village in one world: the village id is its chunk, the same in every dimension. */
    private record Key(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, long village) {
    }

    private static final java.util.Map<Key, ActiveScript> ACTIVE = new java.util.HashMap<>();
    private static final java.util.Map<Key, String> REASONS = new java.util.HashMap<>();
    private static final java.util.Map<Key, Long> BOSS_MISSING = new java.util.HashMap<>();

    private static Key key(net.minecraft.world.level.Level level, long village) {
        return new Key(level.dimension(), village);
    }
    private static final long BOSS_GRACE_TICKS = 600L;
    private static final long WORKER_POLL_TICKS = 20L;
    private static final long WALK_TIMEOUT_TICKS = 300L;
    public static final String FRAME_TAG = "vnap_tocsin_frame";

    private VillageScriptDirector() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        // the worker tag is saved with the villager, the script is not: after a restart nobody would ever free it
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) -> {
            if (event.loadedFromDisk() && event.getEntity() instanceof Villager villager && com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG)
                    && ACTIVE.values().stream().noneMatch(script -> script.workers.contains(villager.getUUID()))) {
                villager.removeTag(WorkerControl.WORKER_TAG);
            }
        });
        // keyed by the meeting point's chunk, not by world: another singleplayer world must not inherit a rite
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            ACTIVE.clear();
            REASONS.clear();
            BOSS_MISSING.clear();
        });
    }

    public static void tick(MinecraftServer server) {
        if (!MoodConfig.enabled()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            VillageMoodApi.syncPlayers(level);
            VillageMoodSavedData data = VillageMoodSavedData.get(level);
            for (MoodState state : data.all()) {
                // only where vanilla ticks the villagers: a bell in a border chunk has frozen workers
                if (!level.isPositionEntityTicking(state.meetingPos)) {
                    continue;
                }
                ActiveScript script = ACTIVE.get(key(level, state.villageId));
                if (script != null) {
                    tickScript(level, state, script);
                } else if (state.script == ScriptKind.NONE) {
                    VillageRiteActions.offer(level, state);
                } else {
                    resume(level, state);
                }
            }
        }
    }

    /** A saved script with nothing running behind it: after a reload, or a boss that is gone. */
    static void resume(ServerLevel level, MoodState state) {
        if (state.script == ScriptKind.BOSS_ACTIVE) {
            watchBoss(level, state);
            return;
        }
        ActiveScript script = new ActiveScript(state.villageId, state.script, level.getGameTime());
        script.phase = state.script == ScriptKind.SUMMON ? ScriptPhase.RING_GATHER : ScriptPhase.SELECT_SITE;
        if (state.script == ScriptKind.SUMMON && state.altarPos != null
                && level.getBlockEntity(state.altarPos) instanceof AltarBlockEntity altar) {
            altar.setAltarState(AltarState.SMOULDERING);
            altar.setCharge(0.0F);
            AltarCoreBlock.setHeat(level, state.altarPos, 1);
        }
        if (state.script == ScriptKind.SUMMON) {
            for (Entity stale : level.getEntities((Entity) null, new AABB(state.altarPos != null ? state.altarPos : state.meetingPos).inflate(24.0),
                    e -> com.vnap.Mc.tags(e).contains(FRAME_TAG))) {
                stale.discard();
            }
        }
        ACTIVE.put(key(level, state.villageId), script);
    }

    private static void watchBoss(ServerLevel level, MoodState state) {
        BlockPos anchor = state.altarPos != null ? state.altarPos : state.meetingPos;
        if (!level.isPositionEntityTicking(anchor)) {
            BOSS_MISSING.remove(key(level, state.villageId));
            return;
        }
        long now = level.getGameTime();
        if (bossNear(level, anchor)) {
            BOSS_MISSING.remove(key(level, state.villageId));
            return;
        }
        long since = BOSS_MISSING.computeIfAbsent(key(level, state.villageId), id -> now);
        if (now - since < BOSS_GRACE_TICKS) {
            return;
        }
        BOSS_MISSING.remove(key(level, state.villageId));
        state.script = ScriptKind.NONE;
        state.flags = MoodFlags.clear(state.flags, MoodFlags.ALTAR_TAINTED);
        if (state.altarPos != null && level.getBlockEntity(state.altarPos) instanceof AltarBlockEntity altar) {
            altar.setAltarState(AltarState.READY);
            altar.setCharge(0.0F);
            AltarCoreBlock.setHeat(level, state.altarPos, 1);
        }
        VillageMoodSavedData.get(level).markDirty();
        VillageMoodApi.syncNearby(level, state);
    }

    public static boolean bossNear(ServerLevel level, BlockPos pos) {
        return !level.getEntities(VillageEntities.TOCSIN_GOLEM,
                new AABB(pos).inflate(128.0), e -> e.isAlive()).isEmpty();
    }

    /** One line per fact: what runs, what blocks the next rite, why the last attempt stopped. */
    public static List<Component> explain(ServerLevel level, MoodState state) {
        List<Component> lines = new ArrayList<>();
        long now = level.getGameTime();
        ActiveScript script = ACTIVE.get(key(level, state.villageId));
        if (script != null) {
            int percent = script.build == null ? 0 : Math.round(script.build.progress() * 100.0F);
            lines.add(Component.translatable("vnap.rite.why.running", riteName(script.kind),
                    script.phase.name(), percent));
        } else if (state.script != ScriptKind.NONE) {
            lines.add(Component.translatable("vnap.rite.why.saved", riteName(state.script)));
        }
        if (now < state.scriptCooldownUntilTick) {
            lines.add(Component.translatable("vnap.rite.why.cooldown", (state.scriptCooldownUntilTick - now + 19) / 20));
        }
        ScriptKind next = VillageRites.nextMissing(state);
        lines.add(next == null
                ? Component.translatable("vnap.rite.why.none")
                : Component.translatable("vnap.rite.why.next", riteName(next)));
        String reason = REASONS.get(key(level, state.villageId));
        if (reason != null) {
            lines.add(Component.translatable("vnap.rite.why.last", Component.translatable("vnap.rite.abort." + reason)));
        }
        return lines;
    }

    public static Component riteName(ScriptKind kind) {
        return Component.translatable("vnap.rite." + kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean tryStartBuildMonument(ServerLevel level, MoodState state) {
        if (!canStartBuild(level, state) || state.hasMonument()) {
            return false;
        }
        return begin(level, state, ScriptKind.BUILD_MONUMENT);
    }

    public static boolean tryStartBuildAltar(ServerLevel level, MoodState state) {
        if (!canStartBuild(level, state) || state.hasAltar() || claimExistingAltar(level, state)) {
            return false;
        }
        return begin(level, state, ScriptKind.BUILD_ALTAR);
    }

    public static boolean tryStartRite(ServerLevel level, MoodState state, ScriptKind kind) {
        if (!canStartBuild(level, state) || kind == ScriptKind.NONE || kind == ScriptKind.SUMMON) {
            return false;
        }
        int bit = VillageRites.flagFor(kind);
        if (bit != 0 && MoodFlags.has(state.flags, bit)) {
            return false;
        }
        return begin(level, state, kind);
    }

    public static boolean tryStartSummon(ServerLevel level, MoodState state) {
        if (!MoodConfig.enabled() || state.script != ScriptKind.NONE) {
            return false;
        }
        if (!state.hasAltar() || state.altarTainted()) {
            return false;
        }
        if (level.getGameTime() < state.scriptCooldownUntilTick) {
            return false;
        }
        if (bossNear(level, state.altarPos != null ? state.altarPos : state.meetingPos)) {
            return false;
        }
        return begin(level, state, ScriptKind.SUMMON);
    }

    public static boolean forceStart(ServerLevel level, MoodState state, ScriptKind kind) {
        abort(level, state, false);
        return begin(level, state, kind);
    }

    public static void abort(ServerLevel level, MoodState state, boolean applyPenalty) {
        abort(level, state, applyPenalty, applyPenalty ? "workers_lost" : "stopped");
    }

    private static void abort(ServerLevel level, MoodState state, boolean applyPenalty, String reason) {
        ActiveScript script = ACTIVE.remove(key(level, state.villageId));
        if (script == null && state.script == ScriptKind.NONE) {
            return;
        }
        if (script != null) {
            WorkerControl.clearTags(script.workers, level);
            clearFrame(level, script);
            if (script.kind == ScriptKind.SUMMON && state.altarPos != null) {
                AltarCoreBlock.setHeat(level, state.altarPos, 1);
            }
            speak(level, script, RiteLines.ABORTED, "abort");
            BuildJob build = script.build;
            if (build != null && build.progress() < 0.3F) {
                for (BlockPos pos : build.built()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        state.script = ScriptKind.NONE;
        state.scriptSite = null;
        state.scriptCooldownUntilTick = level.getGameTime() + MoodConfig.buildCooldownTicks() / 2L;
        noteReason(level, state, reason);
        VillageMoodSavedData.get(level).markDirty();
        if (applyPenalty) {
            VillageMoodApi.apply(level, state.villageId, state.meetingPos, -5.0F, 0.0F, "build_aborted");
        }
        VillageMoodApi.syncNearby(level, state);
    }

    /** Nothing was built yet (no site, too few villagers): retry soon, no penalty. */
    private static void abortEarly(ServerLevel level, MoodState state, String reason) {
        ActiveScript script = ACTIVE.remove(key(level, state.villageId));
        if (script != null) {
            WorkerControl.clearTags(script.workers, level);
        }
        state.script = ScriptKind.NONE;
        state.scriptSite = null;
        state.scriptCooldownUntilTick = level.getGameTime() + MoodConfig.retryCooldownTicks();
        noteReason(level, state, reason);
        VillageMoodSavedData.get(level).markDirty();
        VillageMoodApi.syncNearby(level, state);
    }

    private static void noteReason(ServerLevel level, MoodState state, String reason) {
        REASONS.put(key(level, state.villageId), reason);
        Component line = Component.translatable("vnap.rite.abort.notice",
                Component.translatable("vnap.rite.abort." + reason));
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(state.meetingPos, 64.0)) {
                com.vnap.Mc.say(player, line, true);
            }
        }
    }

    /** Debug (/vnap check): whether a running rite counts this villager among its workers. */
    public static boolean isWorker(java.util.UUID id) {
        return ACTIVE.values().stream().anyMatch(script -> script.workers.contains(id));
    }

    public static @Nullable String lastReason(net.minecraft.world.level.Level level, long villageId) {
        return REASONS.get(key(level, villageId));
    }

    /**
     * If a core already stands, remember it and refuse a second altar. Looks at the block entities of the loaded
     * chunks around the bell only: a block-by-block scan of the area cost ~100k reads and loaded chunks.
     */
    private static boolean claimExistingAltar(ServerLevel level, MoodState state) {
        BlockPos center = state.meetingPos;
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz);
                if (chunk == null) continue;
                for (net.minecraft.world.level.block.entity.BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockPos pos = entity.getBlockPos();
                    if (!(entity instanceof AltarBlockEntity altar) || altar.altarState() == AltarState.RUINED
                            || Math.abs(pos.getX() - center.getX()) > 32 || Math.abs(pos.getZ() - center.getZ()) > 32
                            || pos.getY() < center.getY() - 8 || pos.getY() > center.getY() + 16) continue;
                    state.altarPos = pos.immutable();
                    state.flags = MoodFlags.set(state.flags, MoodFlags.HAS_ALTAR);
                    VillageMoodSavedData.get(level).markDirty();
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canStartBuild(ServerLevel level, MoodState state) {
        return MoodConfig.enabled()
                && state.script == ScriptKind.NONE
                && level.getGameTime() >= state.scriptCooldownUntilTick
                && !ACTIVE.containsKey(key(level, state.villageId));
    }

    private static boolean begin(ServerLevel level, MoodState state, ScriptKind kind) {
        ActiveScript script = new ActiveScript(state.villageId, kind, level.getGameTime());
        script.phase = kind == ScriptKind.SUMMON ? ScriptPhase.RING_GATHER : ScriptPhase.SELECT_SITE;
        ACTIVE.put(key(level, state.villageId), script);
        state.script = kind;
        state.scriptSite = null;
        REASONS.remove(key(level, state.villageId));
        Component start = Component.translatable("vnap.rite.start." + kind.name().toLowerCase(java.util.Locale.ROOT));
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(state.meetingPos, 64.0)) {
                com.vnap.Mc.say(player, start, true);
            }
        }
        VillageMoodSavedData.get(level).markDirty();
        VillageMoodApi.syncNearby(level, state);
        return true;
    }

    private static void tickScript(ServerLevel level, MoodState state, ActiveScript script) {
        long now = level.getGameTime();
        int alive = countAliveWorkers(level, script);
        if (!script.workers.isEmpty() && script.phase != ScriptPhase.SELECT_SITE && script.phase != ScriptPhase.COMPLETE
                && script.phase != ScriptPhase.ABORT && script.phase != ScriptPhase.AFTERMATH && alive < 2
                && script.kind != ScriptKind.BOSS_ACTIVE) {
            // the workers fell (a zombie, a fall): the player is not to blame, so no mood penalty
            abort(level, state, false);
            return;
        }

        switch (script.phase) {
            case SELECT_SITE -> tickSelectSite(level, state, script, now);
            case GATHER, RING_GATHER -> tickGather(level, state, script, now);
            case WAIT_ARRIVE, RING_ARRIVE -> tickWaitArrive(level, script, now);
            case CONSTRUCT -> tickConstruct(level, script, now);
            case CONSECRATE -> tickConsecrate(level, state, script, now);
            case CHANT -> tickChant(level, state, script, now);
            case CHARGE -> tickCharge(level, state, script, now);
            case APEX -> tickApex(level, state, script, now);
            case AFTERMATH -> tickAftermath(level, state, script, now);
            case COMPLETE -> finish(level, state, now);
            case ABORT -> abort(level, state, false);
            default -> {
            }
        }
    }

    private static void tickSelectSite(ServerLevel level, MoodState state, ActiveScript script, long now) {
        if (script.schematic == null) {
            SchematicSpec schematic = SchematicSpec.load(level.getServer().getResourceManager(),
                    VillageRites.schematicName(script.kind));
            boolean resumed = state.scriptSite != null;
            BlockPos origin = state.scriptSite != null ? state.scriptSite : pickSite(level, state.meetingPos, schematic);
            if (origin == null) {
                abortEarly(level, state, "no_site");
                return;
            }
            // the village buys the rite's materials once, out of its treasury (a rite picked up after a restart has them)
            if (!resumed) com.vnap.village.market.Market.kit(level, state.meetingPos, null, com.vnap.village.market.Materials.bill(schematic));
            script.schematic = schematic;
            script.siteOrigin = origin;
            script.focal = origin.offset(schematic.focal);
            script.build = BuildJob.all(VillageRites.schematicName(script.kind), schematic, origin, Rotation.NONE, BuildJob.NO_GROUND);
            script.workerSlots.clear();
            for (BlockPos slot : schematic.workerSlots) {
                script.workerSlots.add(origin.offset(slot));
            }
            if (script.workerSlots.isEmpty()) {
                script.workerSlots.addAll(com.vnap.village.town.Rings.ring(origin, 3.0, MoodConfig.maxWorkers()));
            }
            state.scriptSite = origin.immutable();
            VillageMoodSavedData.get(level).markDirty();
        }
        if (now < script.nextPollTick) {
            return;
        }
        script.nextPollTick = now + WORKER_POLL_TICKS;
        List<Villager> workers = WorkerControl.pickWorkers(level, script.siteOrigin, 2, MoodConfig.maxWorkers());
        if (workers.isEmpty()) {
            if (now - script.phaseStartTick >= 100L) {
                abortEarly(level, state, "no_workers");
            }
            return;
        }
        script.workers.clear();
        for (Villager worker : workers) {
            script.workers.add(worker.getUUID());
        }
        int n = Math.max(1, script.build == null ? 1 : script.build.size());
        int base = Math.max(6, Math.min(16, 400 / n));
        float shortHanded = Math.max(1.0F, MoodConfig.minWorkers() / (float) workers.size());
        script.placeInterval = Math.round(base * shortHanded);
        enter(script, ScriptPhase.GATHER, now);
    }

    /**
     * The best scored site on rings around the bell, nearest first: twelve fixed spots right by the bell found nothing
     * in a dense or hilly village, and its rites never came. Sites over a house project or half out of the loaded
     * land are skipped.
     */
    private static @Nullable BlockPos pickSite(ServerLevel level, BlockPos meeting, SchematicSpec schematic) {
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int reach = Math.max(schematic.sizeX, schematic.sizeZ) / 2 + 4;
        List<net.minecraft.world.level.levelgen.structure.BoundingBox> houses = new ArrayList<>();
        for (com.vnap.village.home.HouseProject house : VillageMoodSavedData.get(level).houses()) {
            if (house.origin.closerThan(meeting, 96.0)) houses.add(house.footprint(level).inflatedBy(2));
        }
        for (int distance = reach; distance <= reach + 20; distance += 4) {
            for (BlockPos center : com.vnap.village.town.Rings.ring(meeting, distance, 16)) {
                int y = level.isLoaded(center) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ()) : 0;
                BlockPos origin = new BlockPos(center.getX() - schematic.sizeX / 2, y, center.getZ() - schematic.sizeZ / 2);
                BlockPos far = origin.offset(schematic.sizeX - 1, schematic.sizeY, schematic.sizeZ - 1);
                if (!level.isLoaded(center) || !level.isLoaded(origin) || !level.isLoaded(far)
                        || !level.isLoaded(new BlockPos(origin.getX(), y, far.getZ())) || !level.isLoaded(new BlockPos(far.getX(), y, origin.getZ()))) {
                    continue;
                }
                var box = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(origin, far);
                if (houses.stream().anyMatch(box::intersects)) continue;
                int site = scoreSite(level, origin, schematic);
                if (site == Integer.MIN_VALUE) {
                    continue;
                }
                int score = site - Math.abs(y - meeting.getY()) - distance / 4;
                if (score > bestScore) {
                    bestScore = score;
                    best = origin;
                }
            }
            if (bestScore >= 0) break;
        }
        return bestScore >= 0 ? best : null;
    }

    /** Flat, dry and free of houses scores high; every cell may cost a little, walls and water cost a lot. */
    static int scoreSite(ServerLevel level, BlockPos origin, SchematicSpec schematic) {
        int score = 20 + schematic.sizeX * schematic.sizeZ / 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < schematic.sizeX; x++) {
            for (int z = 0; z < schematic.sizeZ; z++) {
                cursor.set(origin.getX() + x, origin.getY() - 1, origin.getZ() + z);
                BlockState g = level.getBlockState(cursor);
                if (g.is(Blocks.WATER) || g.is(Blocks.LAVA)) {
                    score -= 20;
                } else if (!g.isFaceSturdy(level, cursor, Direction.UP)) {
                    score -= g.is(Blocks.DIRT_PATH) || g.is(Blocks.FARMLAND) ? 1 : 3;
                }
                for (int y = 0; y < 2; y++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState a = level.getBlockState(cursor);
                    if (a.isAir() || a.canBeReplaced()) {
                        continue;
                    }
                    if (a.is(Blocks.BELL) || a.is(net.minecraft.tags.BlockTags.BEDS)
                            || a.is(net.minecraft.tags.BlockTags.DOORS)) {
                        return Integer.MIN_VALUE;
                    }
                    score -= y == 0 ? 5 : 8;
                }
            }
        }
        return score;
    }

    private static void tickGather(ServerLevel level, MoodState state, ActiveScript script, long now) {
        if (script.workers.isEmpty()) {
            if (now < script.nextPollTick) {
                return;
            }
            script.nextPollTick = now + WORKER_POLL_TICKS;
            if (!prepareWorkersAroundFocal(level, state, script)) {
                if (now - script.phaseStartTick >= 100L) {
                    abortEarly(level, state, "no_workers");
                }
                return;
            }
        }
        if (now - script.phaseStartTick <= 1L) {
            speak(level, script, RiteLines.gather(script.kind), "gather");
        }
        assignWalks(level, script);
        boolean summon = script.kind == ScriptKind.SUMMON;
        if (now - script.phaseStartTick > WALK_TIMEOUT_TICKS) {
            enter(script, summon ? ScriptPhase.RING_ARRIVE : ScriptPhase.WAIT_ARRIVE, now);
            return;
        }
        if (arrivedCount(level, script) >= Math.max(1, (int) Math.ceil(script.workers.size() * 0.5))) {
            enter(script, summon ? ScriptPhase.RING_ARRIVE : ScriptPhase.WAIT_ARRIVE, now);
        }
        if (now - script.phaseStartTick == 1L) {
            VillageRiteActions.playCue(level, script.kind, script.focal);
        }
        if (script.kind == ScriptKind.BUILD_MONUMENT || script.kind == ScriptKind.BUILD_FESTOON
                || script.kind == ScriptKind.BUILD_FESTIVAL || script.kind == ScriptKind.BUILD_PAVILION) {
            spawnSparse(level, script.focal, ParticleTypes.END_ROD, 2);
        } else {
            spawnSparse(level, script.focal, ParticleTypes.SMOKE, 3);
        }
    }

    private static void tickWaitArrive(ServerLevel level, ActiveScript script, long now) {
        assignWalks(level, script);
        int need = Math.max(1, (int) Math.ceil(script.workers.size() * 0.75));
        if (arrivedCount(level, script) >= need || now - script.phaseStartTick > WALK_TIMEOUT_TICKS) {
            if (arrivedCount(level, script) < need) {
                teleportStragglers(level, script);
            }
            if (script.kind == ScriptKind.SUMMON) {
                enter(script, ScriptPhase.CHANT, now);
            } else {
                String title = RiteLines.begin(script.kind);
                if (title != null) {
                    speak(level, script, title, "build_begin");
                }
                enter(script, ScriptPhase.CONSTRUCT, now);
            }
        }
    }

    private static void tickConstruct(ServerLevel level, ActiveScript script, long now) {
        BuildJob build = script.build;
        if (build == null) {
            enter(script, ScriptPhase.ABORT, now);
            return;
        }
        build.skipDone(level);
        if (build.done()) {
            enter(script, ScriptPhase.CONSECRATE, now);
            return;
        }
        if ((now - script.phaseStartTick) % script.placeInterval != 0L) {
            return;
        }
        build.placeNext(level, nearestWorker(level, script, build.nextPos()));
        MoodState owner = VillageMoodSavedData.get(level).get(script.villageId);
        if (owner != null) {
            VillageMoodApi.syncNearby(level, owner);
        }
        String placeTitle = RiteLines.place(script.kind);
        if (placeTitle != null && build.cursor() % 5 == 0) {
            speak(level, script, placeTitle, "place:" + build.cursor());
        }
    }

    private static void tickConsecrate(ServerLevel level, MoodState state, ActiveScript script, long now) {
        long elapsed = now - script.phaseStartTick;
        for (UUID id : script.workers) {
            if (level.getEntity(id) instanceof Villager villager) {
                WorkerControl.forceIdleLook(villager, script.focal);
            }
        }
        if (elapsed == 1L) {
            String title = RiteLines.done(script.kind);
            if (title != null) {
                speak(level, script, title, "consecrate");
            }
            VillageRiteActions.playCue(level, script.kind, script.focal);
        }
        if (elapsed < 80L) {
            boolean bright = script.kind == ScriptKind.BUILD_MONUMENT
                    || script.kind == ScriptKind.BUILD_FESTOON
                    || script.kind == ScriptKind.BUILD_FESTIVAL
                    || script.kind == ScriptKind.BUILD_PAVILION;
            spawnSparse(level, script.focal.above(), bright ? ParticleTypes.END_ROD : ParticleTypes.SOUL, 6);
            return;
        }
        if (script.kind == ScriptKind.BUILD_MONUMENT) {
            activateMonument(level, state, script);
        } else if (script.kind == ScriptKind.BUILD_ALTAR) {
            activateAltar(level, state, script);
            if (state.mood <= MoodConfig.cultReadyThreshold()) {
                script.summonQueuedAfterAltar = true;
            }
        } else {
            VillageRites.markDone(state, script.kind);
            VillageMoodSavedData.get(level).markDirty();
        }
        if (script.kind == ScriptKind.BUILD_MONUMENT) {
            VillageAdvancements.awardNear(level, script.focal, 48.0, "rites/monument");
        } else if (script.kind == ScriptKind.BUILD_FESTIVAL) {
            VillageAdvancements.awardNear(level, script.focal, 48.0, "rites/festival");
        }
        WorkerControl.clearTags(script.workers, level);
        enter(script, ScriptPhase.COMPLETE, now);
    }

    private static void activateMonument(ServerLevel level, MoodState state, ActiveScript script) {
        BlockPos plaque = findPlaced(level, script, LifeBlocks.MONUMENT_PLAQUE);
        BlockPos core = plaque != null ? plaque : script.focal;
        BlockEntity be = level.getBlockEntity(core);
        if (be instanceof MonumentBlockEntity monument) {
            monument.setActive(true);
            String founder = nearestPlayerName(level, script.focal);
            monument.setPlaqueLines(List.of(
                    "plaque.villager_life.honour",
                    founder.isEmpty() ? "plaque.villager_life.its_people" : "plaque.villager_life.founder|" + founder,
                    "plaque.villager_life.mood",
                    "plaque.villager_life.please"));
            state.founderName = founder;
        }
        state.monumentPos = core.immutable();
        state.flags = MoodFlags.set(state.flags, MoodFlags.HAS_MONUMENT);
        VillageMoodSavedData.get(level).markDirty();
    }

    private static void activateAltar(ServerLevel level, MoodState state, ActiveScript script) {
        BlockPos core = findPlaced(level, script, LifeBlocks.ALTAR_CORE);
        if (core == null) {
            core = script.focal;
        }
        BlockEntity be = level.getBlockEntity(core);
        if (be instanceof AltarBlockEntity altar) {
            altar.setAltarState(AltarState.SMOULDERING);
        }
        AltarCoreBlock.setHeat(level, core, 1);
        state.altarPos = core.immutable();
        state.flags = MoodFlags.set(state.flags, MoodFlags.HAS_ALTAR);
        VillageMoodSavedData.get(level).markDirty();
    }

    private static final int[] TOLLS = {60, 150, 240};
    private static final int FRAME_START = 150;
    private static final int FRAME_STEP = 10;
    private static final int INGOT_FLIGHT = 14;

    private static BlockPos forgeCore(MoodState state, ActiveScript script) {
        return state.altarPos != null ? state.altarPos : script.focal;
    }

    /** Workers throw iron into the forge one by one; each ingot raises the heat. */
    private static void tickChant(ServerLevel level, MoodState state, ActiveScript script, long now) {
        long elapsed = now - script.phaseStartTick;
        BlockPos core = forgeCore(state, script);
        for (UUID id : script.workers) {
            if (level.getEntity(id) instanceof Villager villager) {
                WorkerControl.forceIdleLook(villager, core);
                if (elapsed == 1L) {
                    WorkerControl.hold(villager, new ItemStack(Items.IRON_INGOT));
                }
            }
        }
        if (elapsed == 1L) {
            script.ingots = 0;
            speak(level, script, GolemLines.RITE_START, "chant1");
            AltarCoreBlock.setHeat(level, core, 1);
        }
        if (elapsed % 36L == 20L && script.ingots < 6) {
            throwIngot(level, script, core);
        }
        landIngots(level, script, core);
        if (elapsed == 120L) {
            speak(level, script, GolemLines.INGOT, "chant2");
        }
        if (interruptedRitual(level, state, script)) {
            ruinForge(level, state, script);
            return;
        }
        if (elapsed >= 250L) {
            enter(script, ScriptPhase.CHARGE, now);
        }
    }

    private static void throwIngot(ServerLevel level, ActiveScript script, BlockPos core) {
        if (script.workers.isEmpty()) {
            return;
        }
        UUID id = script.workers.get(script.ingots % script.workers.size());
        script.ingots++;
        if (!(level.getEntity(id) instanceof Villager villager)) {
            return;
        }
        com.vnap.Mc.swing(villager, InteractionHand.MAIN_HAND);
        Vec3 from = villager.getEyePosition().subtract(0.0, 0.3, 0.0);
        Vec3 d = Vec3.atCenterOf(core).add(0.0, 0.6, 0.0).subtract(from);
        ItemEntity ingot = new ItemEntity(level, from.x, from.y, from.z, new ItemStack(Items.IRON_INGOT),
                d.x / INGOT_FLIGHT, d.y / INGOT_FLIGHT + 0.02 * INGOT_FLIGHT, d.z / INGOT_FLIGHT);
        ingot.setNeverPickUp();
        ingot.lifespan = INGOT_FLIGHT * 3;
        level.addFreshEntity(ingot);
        script.thrown.add(ingot.getUUID());
        level.playSound(null, villager.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
    }

    private static void landIngots(ServerLevel level, ActiveScript script, BlockPos core) {
        Iterator<UUID> it = script.thrown.iterator();
        Vec3 target = Vec3.atCenterOf(core);
        while (it.hasNext()) {
            Entity ingot = level.getEntity(it.next());
            if (ingot == null || !ingot.isAlive()) {
                it.remove();
                continue;
            }
            if (ingot.tickCount < INGOT_FLIGHT && ingot.position().distanceToSqr(target) > 1.0 && !ingot.onGround()) {
                continue;
            }
            ingot.discard();
            it.remove();
            level.playSound(null, core, GolemSounds.FORGE_INGOT, SoundSource.BLOCKS, 1.2F, 0.85F + 0.05F * script.ingots);
            level.sendParticles(VillageParticles.IRON_SPARK, target.x, target.y + 0.6, target.z, 16, 0.25, 0.2, 0.25, 0.25);
            level.sendParticles(ParticleTypes.LAVA, target.x, target.y + 0.6, target.z, 2, 0.1, 0.1, 0.1, 0.0);
            AltarCoreBlock.setHeat(level, core, 2);
        }
    }

    /** Three tolls of the forge bell, campfires catch, an iron frame rises where the golem will stand. */
    private static void tickCharge(ServerLevel level, MoodState state, ActiveScript script, long now) {
        long elapsed = now - script.phaseStartTick;
        BlockPos core = forgeCore(state, script);
        if (elapsed == 1L) {
            script.golemSpot = golemSpot(level, state, core);
            for (UUID id : script.workers) {
                if (level.getEntity(id) instanceof Villager villager) {
                    WorkerControl.hold(villager, ItemStack.EMPTY);
                }
            }
        }
        landIngots(level, script, core);
        script.chargeProgress = Math.min(1.0F, elapsed / 300.0F);
        if (state.altarPos != null && level.getBlockEntity(state.altarPos) instanceof AltarBlockEntity altar) {
            altar.setAltarState(AltarState.CHARGING);
            altar.setCharge(script.chargeProgress);
        }
        for (int i = 0; i < TOLLS.length; i++) {
            if (elapsed == TOLLS[i]) {
                toll(level, script, core, i);
            }
        }
        if (elapsed >= FRAME_START && (elapsed - FRAME_START) % FRAME_STEP == 0L) {
            raiseFrame(level, script, (int) ((elapsed - FRAME_START) / FRAME_STEP));
        }
        for (UUID id : script.workers) {
            if (level.getEntity(id) instanceof Villager villager) {
                WorkerControl.forceIdleLook(villager, elapsed < FRAME_START ? core : script.golemSpot);
            }
        }
        if (interruptedRitual(level, state, script)) {
            ruinForge(level, state, script);
            return;
        }
        if (elapsed >= 300L) {
            enter(script, ScriptPhase.APEX, now);
        }
    }

    private static void toll(ServerLevel level, ActiveScript script, BlockPos core, int index) {
        BlockPos bell = core.above(3);
        for (int dy = 1; dy <= 5; dy++) {
            if (level.getBlockState(core.above(dy)).is(Blocks.BELL)) {
                bell = core.above(dy);
                break;
            }
        }
        level.playSound(null, bell, GolemSounds.FORGE_TOLL, SoundSource.BLOCKS, 3.0F, 0.55F + 0.1F * index);
        level.blockEvent(bell, Blocks.BELL, 1, Direction.NORTH.get2DDataValue());
        VillageParticles.ring(level, core.getX() + 0.5, core.getY() + 0.05, core.getZ() + 0.5, 6.0 + index * 5.0);
        AltarCoreBlock.setHeat(level, core, 3);
        int lit = 0;
        for (BlockPos pos : BlockPos.betweenClosed(core.offset(-5, -2, -5), core.offset(5, 2, 5))) {
            BlockState fire = level.getBlockState(pos);
            if (fire.is(Blocks.SOUL_CAMPFIRE) && !fire.getValue(CampfireBlock.LIT) && lit++ % TOLLS.length <= index) {
                level.setBlock(pos, fire.setValue(CampfireBlock.LIT, true), Block.UPDATE_ALL);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.02);
            }
        }
        ScriptShakePayload shake = new ScriptShakePayload(0.25F + index * 0.15F);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(core, 32.0)) {
                PacketDistributor.sendToPlayer(player, shake);
            }
        }
        if (index == TOLLS.length - 1) {
            boolean playerNear = !level.getEntitiesOfClass(ServerPlayer.class, new AABB(core).inflate(24.0), p -> true).isEmpty();
            speak(level, script, playerNear ? GolemLines.HIDE : RiteLines.CHANT, "chant3");
        }
    }

    /** Where the golem steps out: in front of the forge, toward the village square. */
    private static BlockPos golemSpot(ServerLevel level, MoodState state, BlockPos core) {
        double dx = state.meetingPos.getX() - core.getX();
        double dz = state.meetingPos.getZ() - core.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0) {
            dx = 0.0;
            dz = 1.0;
            len = 1.0;
        }
        int x = core.getX() + (int) Math.round(dx / len * 7.0);
        int z = core.getZ() + (int) Math.round(dz / len * 7.0);
        // the heightmap of an unloaded chunk is the world's floor: the golem would step out at the bottom of the world
        if (!level.isLoaded(new BlockPos(x, core.getY(), z))) return core.above();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    /** Silhouette parts: offset x, y, z then size, in blocks. The last one is the bell. */
    private static final float[][] FRAME = {
            {-0.75F, 0.0F, -0.35F, 0.6F, 1.5F, 0.7F},
            {0.15F, 0.0F, -0.35F, 0.6F, 1.5F, 0.7F},
            {-0.5F, 1.5F, -0.35F, 1.0F, 0.4F, 0.7F},
            {-1.1F, 1.9F, -0.6F, 2.2F, 1.3F, 1.1F},
            {-1.65F, 0.9F, -0.35F, 0.55F, 2.3F, 0.7F},
            {1.1F, 0.9F, -0.35F, 0.55F, 2.3F, 0.7F},
            {-0.4F, 3.2F, -0.45F, 0.8F, 0.75F, 0.8F},
            {-0.35F, 2.1F, -0.68F, 0.7F, 0.8F, 0.08F},
    };

    private static void raiseFrame(ServerLevel level, ActiveScript script, int index) {
        if (index < 0 || index >= FRAME.length) {
            return;
        }
        float[] part = FRAME[index];
        BlockState block = index == FRAME.length - 1 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        tag.put("block_state", NbtUtils.writeBlockState(block));
        CompoundTag transform = new CompoundTag();
        transform.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        transform.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        transform.put("translation", floats(part[0], part[1], part[2]));
        transform.put("scale", floats(part[3], part[4], part[5]));
        tag.put("transformation", transform);
        CompoundTag light = new CompoundTag();
        light.putInt("block", 15);
        light.putInt("sky", 15);
        tag.put("brightness", light);
        BlockPos spot = script.golemSpot;
        //? if >=1.21.2 {
        /*Entity display = EntityType.loadEntityRecursive(tag, level, net.minecraft.world.entity.EntitySpawnReason.LOAD, entity -> {
        *///?} else
        Entity display = EntityType.loadEntityRecursive(tag, level, entity -> {
            com.vnap.Mc.snap(entity, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0F, 0.0F);
            return entity;
        });
        if (display == null) {
            return;
        }
        display.addTag(FRAME_TAG);
        level.addFreshEntity(display);
        script.frame.add(display.getUUID());
        level.playSound(null, spot, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7F, 0.7F + index * 0.05F);
        level.sendParticles(VillageParticles.IRON_SPARK, spot.getX() + 0.5 + part[0] + part[3] / 2.0, spot.getY() + part[1] + part[4] / 2.0,
                spot.getZ() + 0.5 + part[2] + part[5] / 2.0, 10, part[3] / 2.0, part[4] / 2.0, part[5] / 2.0, 0.05);
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    private static void clearFrame(ServerLevel level, ActiveScript script) {
        for (UUID id : script.frame) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        script.frame.clear();
        for (UUID id : script.thrown) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        script.thrown.clear();
    }

    /** Broken before the golem woke: the forge is ruined and has to be rebuilt. */
    private static void ruinForge(ServerLevel level, MoodState state, ActiveScript script) {
        speak(level, script, GolemLines.INTERRUPTED, "interrupt");
        clearFrame(level, script);
        BlockPos core = forgeCore(state, script);
        if (level.getBlockEntity(core) instanceof AltarBlockEntity altar) {
            altar.setAltarState(AltarState.RUINED);
            altar.setCharge(0.0F);
        }
        AltarCoreBlock.setHeat(level, core, 0);
        level.playSound(null, core, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, core.getX() + 0.5, core.getY() + 1.0, core.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.02);
        state.altarPos = null;
        state.flags = MoodFlags.clear(state.flags, MoodFlags.HAS_ALTAR);
        abort(level, state, false, "interrupted");
    }

    private static boolean interruptedRitual(ServerLevel level, MoodState state, ActiveScript script) {
        if (state.altarPos == null) {
            return true;
        }
        BlockState above = level.getBlockState(state.altarPos.above());
        if (above.is(Blocks.WATER) || above.is(Blocks.WATER_CAULDRON)) {
            return true;
        }
        if (!level.getBlockState(state.altarPos).is(LifeBlocks.ALTAR_CORE)) {
            return true;
        }
        return countAliveWorkers(level, script) < Math.max(2, script.workers.size() / 2);
    }

    private static void tickApex(ServerLevel level, MoodState state, ActiveScript script, long now) {
        long elapsed = now - script.phaseStartTick;
        if (elapsed == 1L) {
            BlockPos core = forgeCore(state, script);
            BlockPos spot = script.golemSpot.equals(BlockPos.ZERO) ? golemSpot(level, state, core) : script.golemSpot;
            clearFrame(level, script);
            script.chargeProgress = 1.0F;
            level.sendParticles(com.vnap.Mc.flash(), spot.getX() + 0.5, spot.getY() + 2.0, spot.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(VillageParticles.IRON_SPARK, spot.getX() + 0.5, spot.getY() + 2.0, spot.getZ() + 0.5, 40, 1.0, 1.5, 1.0, 0.3);
            TocsinGolem golem = com.vnap.Mc.create(VillageEntities.TOCSIN_GOLEM, level);
            if (golem != null) {
                float yaw = (float) (Math.atan2(state.meetingPos.getZ() - spot.getZ(), state.meetingPos.getX() - spot.getX())
                        * (180.0 / Math.PI)) - 90.0F;
                com.vnap.Mc.snap(golem, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0F);
                golem.setYBodyRot(yaw);
                golem.setYHeadRot(yaw);
                level.addFreshEntity(golem);
                golem.bind(state.villageId, spot);
            }
            if (state.altarPos != null && level.getBlockEntity(state.altarPos) instanceof AltarBlockEntity altar) {
                altar.setAltarState(AltarState.TAINTED);
                altar.setCharge(0.0F);
            }
            AltarCoreBlock.setHeat(level, core, 0);
            state.flags = MoodFlags.set(state.flags, MoodFlags.ALTAR_TAINTED);
            state.script = ScriptKind.BOSS_ACTIVE;
            VillageMoodSavedData.get(level).markDirty();
        }
        if (elapsed >= 40L) {
            enter(script, ScriptPhase.AFTERMATH, now);
        }
    }

    private static void tickAftermath(ServerLevel level, MoodState state, ActiveScript script, long now) {
        if (now - script.phaseStartTick >= 100L) {
            WorkerControl.clearTags(script.workers, level);
            ACTIVE.remove(key(level, state.villageId));
            // script stays BOSS_ACTIVE on MoodState until boss death
            VillageMoodApi.syncNearby(level, state);
        }
    }

    private static void finish(ServerLevel level, MoodState state, long now) {
        ActiveScript done = ACTIVE.remove(key(level, state.villageId));
        state.script = ScriptKind.NONE;
        state.scriptSite = null;
        boolean summonNow = done != null && done.summonQueuedAfterAltar;
        state.scriptCooldownUntilTick = summonNow ? 0L : now + 100L;
        VillageMoodSavedData.get(level).markDirty();
        if (summonNow) {
            tryStartSummon(level, state);
        }
        VillageMoodApi.syncNearby(level, state);
    }

    private static void enter(ActiveScript script, ScriptPhase phase, long now) {
        script.phase = phase;
        script.phaseStartTick = now;
    }

    private static boolean prepareWorkersAroundFocal(ServerLevel level, MoodState state, ActiveScript script) {
        BlockPos focal = state.altarPos != null ? state.altarPos
                : state.monumentPos != null ? state.monumentPos : state.meetingPos;
        script.focal = focal.immutable();
        script.siteOrigin = focal.immutable();
        script.workerSlots.clear();
        script.workerSlots.addAll(com.vnap.village.town.Rings.ring(focal, 3.5, MoodConfig.maxWorkers()));
        List<Villager> workers = WorkerControl.pickWorkers(level, focal, 2, MoodConfig.maxWorkers());
        if (workers.isEmpty()) {
            return false;
        }
        script.workers.clear();
        for (Villager worker : workers) {
            script.workers.add(worker.getUUID());
        }
        return true;
    }

    private static void assignWalks(ServerLevel level, ActiveScript script) {
        for (int i = 0; i < script.workers.size(); i++) {
            UUID id = script.workers.get(i);
            if (!(level.getEntity(id) instanceof Villager villager)) {
                continue;
            }
            BlockPos slot = script.workerSlots.get(i % script.workerSlots.size());
            if (!WorkerControl.arrived(villager, slot)) {
                WorkerControl.forceWalk(villager, slot, 0.9);
            }
        }
    }

    private static void teleportStragglers(ServerLevel level, ActiveScript script) {
        for (int i = 0; i < script.workers.size(); i++) {
            UUID id = script.workers.get(i);
            if (!(level.getEntity(id) instanceof Villager villager)) {
                continue;
            }
            BlockPos slot = script.workerSlots.get(i % script.workerSlots.size());
            if (!WorkerControl.arrived(villager, slot)) {
                WorkerControl.softTeleport(villager, slot);
            }
        }
    }

    private static int arrivedCount(ServerLevel level, ActiveScript script) {
        int count = 0;
        for (int i = 0; i < script.workers.size(); i++) {
            UUID id = script.workers.get(i);
            if (!(level.getEntity(id) instanceof Villager villager)) {
                continue;
            }
            BlockPos slot = script.workerSlots.get(i % script.workerSlots.size());
            if (WorkerControl.arrived(villager, slot)) {
                count++;
            }
        }
        return count;
    }

    private static int countAliveWorkers(ServerLevel level, ActiveScript script) {
        int count = 0;
        Iterator<UUID> it = script.workers.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            // a worker out of the loaded land is not dead: only a recorded death counts
            if (level.getEntity(id) instanceof Villager villager ? villager.isAlive()
                    : !com.vnap.dialogue.FamilyDeaths.get(level.getServer()).contains(id)) {
                count++;
            }
        }
        return count;
    }

    private static @Nullable Villager nearestWorker(ServerLevel level, ActiveScript script, BlockPos pos) {
        Villager best = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID id : script.workers) {
            if (level.getEntity(id) instanceof Villager villager && villager.isAlive()) {
                double d = villager.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (d < bestDist) {
                    bestDist = d;
                    best = villager;
                }
            }
        }
        return best;
    }

    private static void speak(ServerLevel level, ActiveScript script, String title, String key) {
        Villager speaker = nearestWorker(level, script, script.focal);
        if (speaker == null) {
            List<Villager> nearby = level.getEntitiesOfClass(Villager.class, new AABB(script.focal).inflate(32.0),
                    v -> v.isAlive() && !v.isBaby());
            if (nearby.isEmpty()) {
                return;
            }
            speaker = nearby.get(0);
        }
        Speech.line(speaker, title,
                "script:" + script.villageId + ":" + key, true);
    }

    private static void spawnSparse(ServerLevel level, BlockPos pos, net.minecraft.core.particles.ParticleOptions particle, int count) {
        level.sendParticles(particle, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                count, 0.6, 0.4, 0.6, 0.01);
    }

    private static @Nullable BlockPos findPlaced(ServerLevel level, ActiveScript script, Block block) {
        if (script.build == null) {
            return null;
        }
        for (BlockPos pos : script.build.placed()) {
            if (level.getBlockState(pos).is(block)) {
                return pos;
            }
        }
        return null;
    }

    private static String nearestPlayerName(ServerLevel level, BlockPos pos) {
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double d = player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (d < best) {
                best = d;
                nearest = player;
            }
        }
        return nearest == null ? "" : com.vnap.Mc.name(nearest);
    }

    /** 0..1 for the HUD: blocks placed, or ritual charge. */
    public static float progressOf(net.minecraft.world.level.Level level, long villageId) {
        ActiveScript script = ACTIVE.get(key(level, villageId));
        if (script == null) {
            return 0.0F;
        }
        if (script.kind == ScriptKind.SUMMON) {
            return script.chargeProgress;
        }
        return script.build == null ? 0.0F : script.build.progress();
    }

    /** The golem walked off with nobody around: the forge cools down and can ring again. */
    public static void onBossLeft(ServerLevel level, long villageId) {
        MoodState state = VillageMoodSavedData.get(level).get(villageId);
        if (state == null) {
            return;
        }
        BOSS_MISSING.remove(key(level, villageId));
        state.script = ScriptKind.NONE;
        state.flags = MoodFlags.clear(state.flags, MoodFlags.ALTAR_TAINTED);
        state.scriptCooldownUntilTick = level.getGameTime() + MoodConfig.buildCooldownTicks();
        if (state.altarPos != null && level.getBlockEntity(state.altarPos) instanceof AltarBlockEntity altar) {
            altar.setAltarState(AltarState.READY);
            altar.setCharge(0.0F);
            AltarCoreBlock.setHeat(level, state.altarPos, 1);
        }
        VillageMoodSavedData.get(level).markDirty();
        VillageMoodApi.syncNearby(level, state);
    }

    /** Tests: the running rite of a village is forgotten (as after a restart), its saved state kept. */
    public static void forgetRunning(net.minecraft.world.level.Level level, long villageId) {
        ACTIVE.remove(key(level, villageId));
    }

    public static @Nullable ActiveScript getActive(net.minecraft.world.level.Level level, long villageId) {
        return ACTIVE.get(key(level, villageId));
    }

    public static void onBossSlain(ServerLevel level, long villageId) {
        MoodState state = VillageMoodSavedData.get(level).get(villageId);
        if (state == null) {
            return;
        }
        state.script = ScriptKind.NONE;
        state.flags = MoodFlags.set(state.flags, MoodFlags.BOSS_SLAIN_ONCE);
        state.flags = MoodFlags.set(state.flags, MoodFlags.SCAR);
        state.scriptCooldownUntilTick = level.getGameTime() + MoodConfig.summonCooldownTicks();
        VillageMoodApi.setExact(level, state, 0.0F, false);
        VillageMoodSavedData.get(level).markDirty();
        if (state.monumentPos != null && level.getBlockEntity(state.monumentPos) instanceof MonumentBlockEntity monument) {
            List<String> lines = new ArrayList<>(monument.plaqueLines());
            if (lines.size() >= 4) {
                lines.set(3, "plaque.villager_life.tocsin");
            } else {
                lines.add("plaque.villager_life.tocsin");
            }
            monument.setPlaqueLines(lines);
        }
        VillageMoodApi.syncNearby(level, state);
    }
}
