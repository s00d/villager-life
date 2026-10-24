package com.vnap.entity;

import com.vnap.VillagerLife;
import com.vnap.entity.golem.PlateCrawler;
import com.vnap.entity.golem.TocsinAnvil;
import com.vnap.entity.golem.TocsinGolem;
import com.vnap.spire.guard.SpireBolt;
import com.vnap.spire.guard.SpireFlyer;
import com.vnap.spire.guard.SpireGuard;
import com.vnap.spire.guard.SpireKeeper;
import com.vnap.spire.guard.SpireSwimmer;
import com.vnap.spire.guard.SpireWalker;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class VillageEntities {
    public static final EntityType<TocsinGolem> TOCSIN_GOLEM = com.vnap.Mc.build(EntityType.Builder.of(TocsinGolem::new, MobCategory.MONSTER)
            .sized(2.2F, 4.0F)
            .eyeHeight(3.4F)
            .fireImmune()
            .clientTrackingRange(12), "tocsin_golem");
    public static final EntityType<PlateCrawler> PLATE_CRAWLER = com.vnap.Mc.build(EntityType.Builder.of(PlateCrawler::new, MobCategory.MONSTER)
            .sized(1.0F, 0.5F)
            .eyeHeight(0.3F)
            .clientTrackingRange(8), "plate_crawler");
    public static final EntityType<TocsinAnvil> TOCSIN_ANVIL = com.vnap.Mc.build(EntityType.Builder.<TocsinAnvil>of(TocsinAnvil::new, MobCategory.MISC)
            .sized(0.98F, 0.98F)
            .clientTrackingRange(8)
            .updateInterval(1), "tocsin_anvil");

    public static final EntityType<HandCart> HAND_CART = com.vnap.Mc.build(EntityType.Builder.<HandCart>of(HandCart::new, MobCategory.MISC)
            .sized(1.1F, 0.9F)
            .clientTrackingRange(8)
            .updateInterval(2), "hand_cart");

    public static final EntityType<Seat> SEAT = com.vnap.Mc.build(EntityType.Builder.<Seat>of(Seat::new, MobCategory.MISC)
            .sized(0.01F, 0.01F)
            .noSummon()
            .clientTrackingRange(8), "seat");

    public static final EntityType<SpireWalker> SPIRE_WALKER = com.vnap.Mc.build(EntityType.Builder.of(SpireWalker::new, MobCategory.MONSTER)
            .sized(0.8F, 1.5F)
            .fireImmune()
            .clientTrackingRange(10), "spire_walker");
    public static final EntityType<SpireFlyer> SPIRE_FLYER = com.vnap.Mc.build(EntityType.Builder.of(SpireFlyer::new, MobCategory.MONSTER)
            .sized(0.8F, 0.8F)
            .fireImmune()
            .clientTrackingRange(10), "spire_flyer");
    public static final EntityType<SpireSwimmer> SPIRE_SWIMMER = com.vnap.Mc.build(EntityType.Builder.of(SpireSwimmer::new, MobCategory.MONSTER)
            .sized(0.9F, 0.6F)
            .fireImmune()
            .clientTrackingRange(10), "spire_swimmer");
    public static final EntityType<SpireKeeper> SPIRE_KEEPER = com.vnap.Mc.build(EntityType.Builder.of(SpireKeeper::new, MobCategory.MONSTER)
            .sized(1.4F, 2.6F)
            .fireImmune()
            .clientTrackingRange(12), "spire_keeper");
    public static final EntityType<SpireBolt> SPIRE_BOLT = com.vnap.Mc.build(EntityType.Builder.<SpireBolt>of(SpireBolt::new, MobCategory.MISC)
            .sized(0.25F, 0.25F)
            .clientTrackingRange(6)
            .updateInterval(10), "spire_bolt");

    private VillageEntities() {
    }

    public static void register(RegisterEvent.RegisterHelper<EntityType<?>> helper) {
        helper.register(VillagerLife.id("tocsin_golem"), TOCSIN_GOLEM);
        helper.register(VillagerLife.id("plate_crawler"), PLATE_CRAWLER);
        helper.register(VillagerLife.id("tocsin_anvil"), TOCSIN_ANVIL);
        helper.register(VillagerLife.id("hand_cart"), HAND_CART);
        helper.register(VillagerLife.id("seat"), SEAT);
        helper.register(VillagerLife.id("spire_walker"), SPIRE_WALKER);
        helper.register(VillagerLife.id("spire_flyer"), SPIRE_FLYER);
        helper.register(VillagerLife.id("spire_swimmer"), SPIRE_SWIMMER);
        helper.register(VillagerLife.id("spire_keeper"), SPIRE_KEEPER);
        helper.register(VillagerLife.id("spire_bolt"), SPIRE_BOLT);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TOCSIN_GOLEM, TocsinGolem.createAttributes().build());
        event.put(PLATE_CRAWLER, PlateCrawler.createAttributes().build());
        event.put(SPIRE_WALKER, SpireGuard.createAttributes().build());
        event.put(SPIRE_FLYER, SpireGuard.createAttributes().build());
        event.put(SPIRE_SWIMMER, SpireGuard.createAttributes().build());
        event.put(SPIRE_KEEPER, SpireGuard.createAttributes().build());
    }
}
