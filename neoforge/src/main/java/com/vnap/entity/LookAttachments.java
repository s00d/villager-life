package com.vnap.entity;

//? if >=1.21.6 && neoforge {
/*import com.vnap.VillagerLife;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;
*///?}

/**
 * What a client draws a villager by (woman, elder, mayor, shopkeeper, its spire clothes): synced entity data
 * until 1.21.5; from 1.21.6 NeoForge will not have a mod add synced data to another mod's or the game's entity, and
 * synced attachments carry it. Saved with the villager by VillagerDataMixin either way.
 */
public final class LookAttachments {
    private LookAttachments() {
    }

    //? if >=1.21.6 && neoforge {
    /*private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VillagerLife.MOD_ID);

    public static final Supplier<AttachmentType<Boolean>> FEMALE = flag("female", false);
    public static final Supplier<AttachmentType<Boolean>> ELDER = flag("elder", false);
    public static final Supplier<AttachmentType<Boolean>> MAYOR = flag("mayor", false);
    public static final Supplier<AttachmentType<Boolean>> SHOPKEEPER = flag("shopkeeper", false);
    public static final Supplier<AttachmentType<Integer>> SPIRE_OUTFIT = TYPES.register("spire_outfit",
            () -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

    private static Supplier<AttachmentType<Boolean>> flag(String name, boolean fallback) {
        return TYPES.register(name, () -> AttachmentType.builder(() -> fallback).sync(ByteBufCodecs.BOOL).build());
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
    *///?}
}
