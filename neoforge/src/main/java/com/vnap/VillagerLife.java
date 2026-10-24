package com.vnap;

import com.vnap.village.life.Herd;
import com.vnap.village.workshop.Workshops;
import com.mojang.logging.LogUtils;
import com.vnap.block.LifeBlocks;
import com.vnap.block.decor.WorkshopEvents;
import com.vnap.client.ClientPayloadHandlers;
import com.vnap.command.DialogueTestCommand;
import com.vnap.command.MoodCommand;
import com.vnap.config.LifeSettings;
import com.vnap.dialogue.VillagerTalk;
import com.vnap.dialogue.DialogueCatalog;
import com.vnap.entity.VillageEntities;
import com.vnap.fx.VillageParticles;
import com.vnap.sound.GolemSounds;
import com.vnap.item.LifeItems;
import com.vnap.mood.MoodConfig;
import com.vnap.mood.MoodHooks;
import com.vnap.network.DialogueAnimationPayload;
import com.vnap.network.ScriptShakePayload;
import com.vnap.network.VillageMoodPayload;
import com.vnap.network.LifeSettingsNetwork;
import com.vnap.village.script.VillageScriptDirector;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

@Mod(VillagerLife.MOD_ID)
public class VillagerLife {
    public static final String MOD_ID = "villager_life";
    /** Where players find the mod and its people (the guide, the first-join message, neoforge.mods.toml, the READMEs). */
    public static final String DISCORD = "https://discord.gg/JkNspxqWk9";
    public static final String MODRINTH = "https://modrinth.com/mod/villager-life-s00d";
    public static final String CURSEFORGE = "https://www.curseforge.com/minecraft/mc-mods/villager-life-s00d";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Lines for the dev tools (deaths.py, slow ticks, monsters): only with debugCommands, so a player's log stays quiet. */
    public static void trace(String message, Object... args) {
        if (com.vnap.mood.MoodConfig.debugCommands()) LOGGER.info(message, args);
    }

    public VillagerLife(IEventBus modEventBus) {
        LifeSettings.load();
        MoodConfig.load();
        // a client that joined another server took its thresholds (MoodRulesPayload): a world of its own reads the config again
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) -> MoodConfig.load());
        // what made things are worth comes from the server's recipes: learnt on start and again after a /reload
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent event) ->
                com.vnap.village.market.Prices.learn(event.getServer().overworld()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.TagsUpdatedEvent event) -> {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            //? if >=26.1 {
            /*if (event instanceof net.neoforged.neoforge.event.TagsUpdatedEvent.ServerDataLoad && server != null) {
            *///?} else
            if (event.getUpdateCause() == net.neoforged.neoforge.event.TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD && server != null) {
                server.execute(() -> com.vnap.village.market.Prices.learn(server.overworld()));
            }
        });
        com.vnap.spire.Afflictions.listen();
        com.vnap.spire.Draughts.Events.listen();
        com.vnap.mega.MegaWork.listen();
        com.vnap.progress.Discoveries.register();
        modEventBus.addListener(VillagerLife::onRegister);
        modEventBus.addListener(VillagerLife::onRegisterPayloads);
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(() -> com.vnap.block.DoubleBedBlock.registerHome(com.vnap.block.LifeBlocks.DOUBLE_BED)).exceptionally(error -> {
                    LOGGER.error("the double bed is no villager's home", error);
                    return null;
                }));
        modEventBus.addListener(VillageEntities::registerAttributes);
        //? if >=1.21.6
        /*com.vnap.entity.LookAttachments.register(modEventBus);*/
        //? if <1.21.4
        modEventBus.addListener(com.vnap.datagen.LifeDataGen::gather);
        LifeSettingsNetwork.registerEvents();
        // the guide's crafting pages: from 1.21.2 the client only has the recipes the server sends it
        //? if >=1.21.2
        /*net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.OnDatapackSyncEvent event) -> event.sendRecipes(net.minecraft.world.item.crafting.RecipeType.CRAFTING));*/
        VillagerTalk.register();
        com.vnap.dialogue.Family.register();
        com.vnap.dialogue.Infants.register();
        VillageScriptDirector.register();
        Workshops.register();
        WorkshopEvents.register();
        Herd.register();
        MoodHooks.register();
        com.vnap.village.town.Errands.register();
        com.vnap.village.town.TownLoading.register(modEventBus);
        com.vnap.spire.SpireEvents.register(modEventBus);
        // /dialoguetest needs operator permission (level 2), so it is safe to register in every build.
        DialogueTestCommand.register();
        MoodCommand.register();
        com.vnap.item.GuideGift.register();
        com.vnap.village.market.ShopEvents.register();
        LOGGER.info("Villager Life models, textures, contextual dialogue, and village mood are ready.");
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, helper -> {
            DialogueCatalog.register(helper);
            GolemSounds.register(helper);
            com.vnap.sound.SpireSounds.register(helper);
        });
        event.register(Registries.BLOCK, helper -> {
            LifeBlocks.registerBlocks(helper);
            com.vnap.spire.SpireBlocks.registerBlocks(helper);
            com.vnap.mega.MegaBlocks.registerBlocks(helper);
            com.vnap.spire.outer.OuterBlocks.registerBlocks(helper);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            LifeBlocks.registerBlockEntities(helper);
            com.vnap.mega.MegaBlocks.registerBlockEntities(helper);
        });
        event.register(Registries.MENU, helper -> {
            LifeBlocks.registerMenus(helper);
            com.vnap.spire.SpireBlocks.registerMenus(helper);
        });
        event.register(Registries.ENTITY_TYPE, VillageEntities::register);
        event.register(Registries.PARTICLE_TYPE, VillageParticles::register);
        event.register(Registries.ITEM, helper -> {
            LifeBlocks.registerBlockItems(helper);
            LifeItems.registerItems(helper);
            com.vnap.spire.SpireBlocks.registerItems(helper);
            com.vnap.spire.Draughts.registerItems(helper);
            com.vnap.mega.MegaBlocks.registerItems(helper);
        });
        event.register(Registries.CREATIVE_MODE_TAB, LifeItems::registerTab);
        event.register(Registries.MOB_EFFECT, helper -> {
            helper.register(id("parish_hymn"), com.vnap.effect.ParishHymnEffect.INSTANCE);
            com.vnap.spire.Afflictions.register(helper);
            com.vnap.spire.Draughts.registerEffects(helper);
        });
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        LifeSettingsNetwork.registerPayloads(registrar);
        registrar.playToClient(DialogueAnimationPayload.TYPE, DialogueAnimationPayload.CODEC, ClientPayloadHandlers::dialogueAnimation);
        registrar.playToClient(VillageMoodPayload.TYPE, VillageMoodPayload.CODEC, ClientPayloadHandlers::villageMood);
        registrar.playToClient(ScriptShakePayload.TYPE, ScriptShakePayload.CODEC, ClientPayloadHandlers::scriptShake);
        registrar.playToClient(com.vnap.network.MoodRulesPayload.TYPE, com.vnap.network.MoodRulesPayload.CODEC, (payload, context) -> payload.apply());
        registrar.playToClient(com.vnap.network.ErrandPayload.TYPE, com.vnap.network.ErrandPayload.CODEC, ClientPayloadHandlers::errand);
        registrar.playToClient(com.vnap.network.NoticeBoardPayload.TYPE, com.vnap.network.NoticeBoardPayload.CODEC, ClientPayloadHandlers::noticeBoard);
        registrar.playToClient(com.vnap.network.SpireFloorPayload.TYPE, com.vnap.network.SpireFloorPayload.CODEC, ClientPayloadHandlers::spireFloor);
        registrar.playToClient(com.vnap.network.DiscoveriesPayload.TYPE, com.vnap.network.DiscoveriesPayload.CODEC, ClientPayloadHandlers::discoveries);
        registrar.playToClient(com.vnap.network.KeeperMarksPayload.TYPE, com.vnap.network.KeeperMarksPayload.CODEC, ClientPayloadHandlers::keeperMarks);
        registrar.playToClient(com.vnap.network.SpireNotePayload.TYPE, com.vnap.network.SpireNotePayload.CODEC, ClientPayloadHandlers::spireNote);
        registrar.playToClient(com.vnap.network.SpireJournalPayload.TYPE, com.vnap.network.SpireJournalPayload.CODEC, ClientPayloadHandlers::spireJournal);
        registrar.playToClient(com.vnap.network.ShopListingPayload.TYPE, com.vnap.network.ShopListingPayload.CODEC, ClientPayloadHandlers::shopListing);
        registrar.playToServer(com.vnap.network.NoticeBoardActionPayload.TYPE, com.vnap.network.NoticeBoardActionPayload.CODEC,
                (payload, context) -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player && com.vnap.Mc.levelOf(player) instanceof net.minecraft.server.level.ServerLevel level) {
                        com.vnap.village.town.NoticeBoard.act(level, player, payload.board(), payload.action(), payload.id());
                    }
                });
        registrar.playToServer(com.vnap.network.ShopActionPayload.TYPE, com.vnap.network.ShopActionPayload.CODEC,
                // handlers run on the server thread (the registrar's default)
                (payload, context) -> {
                    if (context.player().containerMenu instanceof com.vnap.village.market.ShopMenu menu && menu.containerId == payload.containerId()) {
                        menu.act(context.player(), payload.action(), payload.index());
                    }
                });
        registrar.playToServer(com.vnap.network.CoreAnvilPayload.TYPE, com.vnap.network.CoreAnvilPayload.CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof com.vnap.spire.gear.CoreAnvilMenu menu && menu.containerId == payload.containerId()
                    && menu.stillValid(context.player())) {
                menu.act(context.player(), payload.action());
            }
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
