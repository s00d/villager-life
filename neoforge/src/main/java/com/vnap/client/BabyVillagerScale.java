package com.vnap.client;

import com.vnap.VillagerLife;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * A newborn in its cradle is drawn by the cradle (InfantRenderer); a spectator, who sees the invisible, would see the
 * villager standing in it as well, with its name tag.
 */
@EventBusSubscriber(modid = VillagerLife.MOD_ID, value = Dist.CLIENT)
public final class BabyVillagerScale {
    private BabyVillagerScale() {
    }

    @SubscribeEvent
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

    @SubscribeEvent
    //? if >=1.21.2 {
    /*public static void nameTag(net.neoforged.neoforge.client.event.RenderNameTagEvent.CanRender event) {
    *///?} else
    public static void nameTag(net.neoforged.neoforge.client.event.RenderNameTagEvent event) {
        if (event.getEntity() instanceof Villager villager && com.vnap.dialogue.Infants.lying(villager)) {
            event.setCanRender(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }
}
