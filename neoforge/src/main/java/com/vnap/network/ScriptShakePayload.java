package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ScriptShakePayload(float intensity) implements CustomPacketPayload {
    public static final Type<ScriptShakePayload> TYPE = new Type<>(VillagerLife.id("script_shake"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScriptShakePayload> CODEC = new StreamCodec<>() {
        @Override
        public ScriptShakePayload decode(RegistryFriendlyByteBuf buffer) {
            return new ScriptShakePayload(buffer.readFloat());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ScriptShakePayload payload) {
            buffer.writeFloat(payload.intensity());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
