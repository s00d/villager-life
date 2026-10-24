package com.vnap.network;

import com.vnap.VillagerLife;
import com.vnap.mood.MoodState;
import com.vnap.mood.ScriptKind;
import com.vnap.village.script.VillageScriptDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillageMoodPayload(
        long villageId,
        float mood,
        float standing,
        String script,
        float progress,
        boolean hasMonument,
        boolean hasAltar,
        boolean scarred,
        BlockPos meetingPos
) implements CustomPacketPayload {
    public static final Type<VillageMoodPayload> TYPE = new Type<>(VillagerLife.id("village_mood"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VillageMoodPayload> CODEC = new StreamCodec<>() {
        @Override
        public VillageMoodPayload decode(RegistryFriendlyByteBuf buffer) {
            return new VillageMoodPayload(
                    buffer.readLong(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readUtf(32),
                    buffer.readFloat(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBlockPos());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, VillageMoodPayload payload) {
            buffer.writeLong(payload.villageId());
            buffer.writeFloat(payload.mood());
            buffer.writeFloat(payload.standing());
            buffer.writeUtf(payload.script(), 32);
            buffer.writeFloat(payload.progress());
            buffer.writeBoolean(payload.hasMonument());
            buffer.writeBoolean(payload.hasAltar());
            buffer.writeBoolean(payload.scarred());
            buffer.writeBlockPos(payload.meetingPos());
        }
    };

    public static VillageMoodPayload from(net.minecraft.world.level.Level level, MoodState state) {
        return new VillageMoodPayload(
                state.villageId,
                state.mood,
                state.standing,
                state.script == null ? ScriptKind.NONE.name() : state.script.name(),
                VillageScriptDirector.progressOf(level, state.villageId),
                state.hasMonument(),
                state.hasAltar(),
                state.scarred(),
                state.meetingPos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
