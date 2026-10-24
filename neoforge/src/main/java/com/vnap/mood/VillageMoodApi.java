package com.vnap.mood;

import com.vnap.network.VillageMoodPayload;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VillageMoodApi {
    private VillageMoodApi() {
    }

    public record VillageRef(long villageId, BlockPos meetingPos, List<Villager> villagers) {
    }

    public static @Nullable VillageRef resolve(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(64.0);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box,
                v -> v.isAlive() && !v.isBaby());
        if (villagers.isEmpty()) {
            return null;
        }

        Map<BlockPos, Integer> meetingVotes = new HashMap<>();
        for (Villager villager : villagers) {
            BlockPos mp = com.vnap.village.home.Towns.bellOf(villager);
            if (mp != null) meetingVotes.merge(mp, 1, Integer::sum);
        }

        BlockPos meetingPos;
        // a town's mood is its office's: the same village the mayor, the stall and the roads go by
        com.vnap.village.home.Mayors.Office office = com.vnap.village.home.Towns.of(level, pos);
        if (office != null) {
            meetingPos = office.bell;
        } else if (!meetingVotes.isEmpty()) {
            meetingPos = meetingVotes.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(BlockPos.ZERO);
        } else {
            double sx = 0;
            double sy = 0;
            double sz = 0;
            for (Villager v : villagers) {
                sx += v.getX();
                sy += v.getY();
                sz += v.getZ();
            }
            int n = villagers.size();
            meetingPos = BlockPos.containing(sx / n, sy / n, sz / n);
        }

        for (MoodState existing : VillageMoodSavedData.get(level).all()) {
            if (existing.meetingPos.closerThan(meetingPos, com.vnap.village.town.TownRules.SPAN)) {
                return new VillageRef(existing.villageId, existing.meetingPos, villagers);
            }
        }

        long villageId = com.vnap.Mc.chunkKey(meetingPos.getX() >> 4, meetingPos.getZ() >> 4);
        return new VillageRef(villageId, meetingPos.immutable(), villagers);
    }

    public static void apply(ServerLevel level, long villageId, BlockPos meetingPos,
                             float deltaMood, float deltaStanding, String reason) {
        if (!MoodConfig.enabled()) {
            return;
        }
        VillageMoodSavedData data = VillageMoodSavedData.get(level);
        MoodState state = data.getOrCreate(villageId, meetingPos);
        float prevMood = state.mood;
        state.mood = clamp(state.mood + deltaMood, -100.0F, 100.0F);
        state.standing = clamp(state.standing + deltaStanding, -1000.0F, 1000.0F);
        onMoodChanged(level, state, prevMood, true);
    }

    /** Titles on crossing ±20, crossing marks, ephemeral cleanup, sync, and (when asked) the next rite. */
    public static void onMoodChanged(ServerLevel level, MoodState state, float prevMood, boolean offer) {
        long now = level.getGameTime();
        if (prevMood < MoodThresholds.festoon() && state.mood >= MoodThresholds.festoon()) {
            speakNearby(level, state, "Village Mood Rising", "mood_up");
        }
        if (prevMood > MoodThresholds.vigil() && state.mood <= MoodThresholds.vigil()) {
            speakNearby(level, state, "Village Mood Falling", "mood_down");
        }
        if (prevMood < MoodThresholds.happy() && state.mood >= MoodThresholds.happy()) {
            state.lastHappyCrossTick = now;
        }
        if (prevMood > MoodThresholds.angry() && state.mood <= MoodThresholds.angry()) {
            state.lastAngryCrossTick = now;
        }
        VillageRites.clearEphemeral(state);
        VillageMoodSavedData.get(level).markDirty();
        syncNearby(level, state);
        if (offer) {
            VillageRiteActions.offer(level, state);
        }
    }

    /** Moves mood by {@code delta} and clamps to ±100. False when there is no village or the scale is already at that end. */
    public static boolean nudge(ServerLevel level, BlockPos pos, float delta) {
        if (!MoodConfig.enabled()) {
            return false;
        }
        VillageRef ref = resolve(level, pos);
        if (ref == null) {
            return false;
        }
        MoodState state = VillageMoodSavedData.get(level).getOrCreate(ref.villageId(), ref.meetingPos());
        float next = clamp(state.mood + delta, -100.0F, 100.0F);
        if (Math.abs(next - state.mood) < 0.01F) {
            return false;
        }
        apply(level, ref.villageId(), ref.meetingPos(), next - state.mood, 0.0F,
                delta > 0.0F ? "staff_bless" : "staff_curse");
        return true;
    }

    /** Writes mood exactly. {@code offer} also starts the strongest missing rite. */
    public static void setExact(ServerLevel level, MoodState state, float mood, boolean offer) {
        float prev = state.mood;
        state.mood = clamp(mood, -100.0F, 100.0F);
        onMoodChanged(level, state, prev, offer);
    }

    public static void applyAt(ServerLevel level, BlockPos pos, float deltaMood, float deltaStanding, String reason) {
        VillageRef ref = resolve(level, pos);
        if (ref == null) {
            return;
        }
        apply(level, ref.villageId(), ref.meetingPos(), deltaMood, deltaStanding, reason);
    }

    /**
     * Every few seconds each player gets the mood of the village it stands in: one who joins or walks in sees the bar
     * at once. Nothing here moves the mood: it changes only by what players do (MoodHooks and the hooks it names).
     */
    public static void syncPlayers(ServerLevel level) {
        if (level.getGameTime() % 100L != 0L) return;
        VillageMoodSavedData data = VillageMoodSavedData.get(level);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            MoodState nearest = null;
            for (MoodState state : data.all()) {
                if (player.blockPosition().closerThan(state.meetingPos, 96.0)
                        && (nearest == null || state.meetingPos.distSqr(player.blockPosition()) < nearest.meetingPos.distSqr(player.blockPosition()))) {
                    nearest = state;
                }
            }
            if (nearest != null) {
                PacketDistributor.sendToPlayer(player, VillageMoodPayload.from(level, nearest));
                if (nearest.script != null) com.vnap.progress.Discoveries.discover(player, "rite");
            }
        }
    }

    public static void syncNearby(ServerLevel level, MoodState state) {
        VillageMoodPayload payload = VillageMoodPayload.from(level, state);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(state.meetingPos, 96.0)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void speakNearby(ServerLevel level, MoodState state, String title, String key) {
        java.util.List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
                new AABB(state.meetingPos).inflate(48.0), v -> v.isAlive() && !v.isBaby());
        if (villagers.isEmpty()) {
            return;
        }
        com.vnap.dialogue.Speech.line(
                villagers.get(0), title, "mood:" + state.villageId + ":" + key, true);
    }
}
