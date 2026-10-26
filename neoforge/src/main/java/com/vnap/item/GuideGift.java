package com.vnap.item;

import com.vnap.Nbt;
import com.vnap.config.LifeSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Every player gets the guide book once, on the first join of a world, with a line in chat. The mark lives in the
 * player's persisted data, so it survives death; giveGuideOnJoin in villager-life.json turns the gift off.
 */
public final class GuideGift {
    static final String GIVEN = "villager_life_guide_given";

    private GuideGift() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, (PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) give(player);
        });
    }

    /** Gives the book unless this player had it already; true when it was given now. */
    public static boolean give(Player player) {
        CompoundTag persisted = Nbt.getCompound(player.getPersistentData(), Player.PERSISTED_NBT_TAG);
        if (!LifeSettings.giveGuideOnJoin() || Nbt.getBoolean(persisted, GIVEN)) return false;
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = new ItemStack(LifeItems.HANDBOOK);
        if (!player.getInventory().add(book)) com.vnap.Mc.toss(player, book);
        com.vnap.Mc.say(player, Component.translatable("guide.villager_life.given").withStyle(ChatFormatting.GOLD), false);
        com.vnap.Mc.say(player, Component.translatable("guide.villager_life.community", com.vnap.Mc.link(com.vnap.VillagerLife.DISCORD)).withStyle(ChatFormatting.GRAY), false);
        return true;
    }
}
