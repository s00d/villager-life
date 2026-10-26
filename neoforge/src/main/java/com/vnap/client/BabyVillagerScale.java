package com.vnap.client;

import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * A newborn in its cradle is drawn by the cradle (InfantRenderer); a spectator, who sees the invisible, would see the
 * villager standing in it as well, with its name tag.
 */
public final class BabyVillagerScale {
    private BabyVillagerScale() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Pre.class, BabyVillagerScale::pre);
        //? if >=1.21.2 {
        /*NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderNameTagEvent.CanRender.class, BabyVillagerScale::nameTag);
        *///?} else
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderNameTagEvent.class, BabyVillagerScale::nameTag);
    }

    //? if >=1.21.2 {
    /*public static void pre(RenderLivingEvent.Pre<?, ?, ?> event) {
        net.minecraft.world.entity.Entity entity = RenderedEntity.of(event.getRenderState(), Villager.class);
    *///?} else {
    public static void pre(RenderLivingEvent.Pre<?, ?> event) {
        net.minecraft.world.entity.Entity entity = event.getEntity();
    //?}
        if (entity instanceof Villager villager && com.vnap.dialogue.Infants.lying(villager)) {
            event.setCanceled(true);
        }
    }

    //? if >=1.21.2 {
    /*public static void nameTag(net.neoforged.neoforge.client.event.RenderNameTagEvent.CanRender event) {
    *///?} else
    public static void nameTag(net.neoforged.neoforge.client.event.RenderNameTagEvent event) {
        if (event.getEntity() instanceof Villager villager && com.vnap.dialogue.Infants.lying(villager)) {
            event.setCanRender(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }
}
