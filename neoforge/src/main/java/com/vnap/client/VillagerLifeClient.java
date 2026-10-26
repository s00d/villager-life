package com.vnap.client;

import com.vnap.VillagerLife;
import com.vnap.block.LifeBlocks;
import com.vnap.client.fx.VillageParticleProviders;
import com.vnap.client.golem.GolemClientEffects;
import com.vnap.client.model.BedrockModels;
import com.vnap.client.golem.PlateCrawlerRenderer;
import com.vnap.client.spire.SpireGuardRenderer;
import com.vnap.client.golem.TocsinAnvilRenderer;
import com.vnap.client.golem.TocsinGolemRenderer;
import com.vnap.entity.VillageEntities;

import com.vnap.item.LifeItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;


@Mod(value = VillagerLife.MOD_ID, dist = Dist.CLIENT)
public final class VillagerLifeClient {
	static {
		com.vnap.Mc.clientLevel = () -> net.minecraft.client.Minecraft.getInstance().level;
	}

	public VillagerLifeClient(IEventBus modEventBus, ModContainer container) {
		LifeClientSettings.load();
		BabyVillagerScale.register();
		container.registerExtensionPoint(IConfigScreenFactory.class,
			(modContainer, parent) -> new SettingsScreen(parent));
		modEventBus.addListener(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent.class, DialogueSubtitleState::register);
		modEventBus.addListener(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent.class, com.vnap.client.spire.AfflictionOverlay::register);
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System.class, VillagerLifeClient::onSystemChat);
		modEventBus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers.class, VillagerLifeClient::addLayers);
		modEventBus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers.class, VillagerLifeClient::registerEntityRenderers);
		modEventBus.addListener(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.class, VillagerLifeClient::registerScreens);
		// a Waygate's field takes its keystone's colour
		//? if >=26.1 {
		/*modEventBus.addListener(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.BlockTintSources.class, (net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.BlockTintSources event) -> event.register(
				java.util.List.of(new net.minecraft.client.color.block.BlockTintSource() {
					@Override
					public int color(net.minecraft.world.level.block.state.BlockState state) {
						return 0xFF000000 | net.minecraft.world.item.DyeColor.byId(state.getValue(com.vnap.mega.GateFieldBlock.COLOUR)).getTextureDiffuseColor();
					}

					@Override
					public java.util.Set<net.minecraft.world.level.block.state.properties.Property<?>> relevantProperties() {
						return java.util.Set.of(com.vnap.mega.GateFieldBlock.COLOUR);
					}
				}),
				com.vnap.mega.MegaBlocks.GATE_FIELD));
		*///?} else {
		modEventBus.addListener(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block.class, (net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) -> event.register(
				(state, level, pos, tint) -> 0xFF000000 | net.minecraft.world.item.DyeColor.byId(state.getValue(com.vnap.mega.GateFieldBlock.COLOUR)).getTextureDiffuseColor(),
				com.vnap.mega.MegaBlocks.GATE_FIELD));
		//?}
		//? if >=1.21.5 {
		/*modEventBus.addListener(net.neoforged.neoforge.client.event.ModelEvent.RegisterStandalone.class, HandCartRenderer::registerModels);
		*///?} else
		modEventBus.addListener(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional.class, HandCartRenderer::registerModels);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem.class, VillagerLifeClient::onUseItem);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent.class, VillagerLifeClient::onClientCommands);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent.class, VillagerLifeClient::onTooltip);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, VillagerLifeClient::onTick);
		//? if <1.21.2 {
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderFrameEvent.Post.class, com.vnap.client.dev.Recorder::onFrame);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre.class, com.vnap.client.dev.Recorder::onLayer);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderHandEvent.class, com.vnap.client.dev.Recorder::onHand);
		//?}
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre.class, com.vnap.client.dev.Camera::onFrame);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut.class, VillagerLifeClient::onLogout);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderGuiEvent.Post.class, MoodBarOverlay::onRender);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderGuiEvent.Post.class, ErrandHud::onRender);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderGuiEvent.Post.class, com.vnap.client.spire.SpireHud::onRender);
		//? if >=1.21.2
		/*NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RecipesReceivedEvent.class, com.vnap.client.guide.GuideScreen::onRecipes);*/
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre.class, MoodBarOverlay::onFrameStart);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent.BossEventProgress.class, MoodBarOverlay::onBossBar);
		modEventBus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions.class, VillagerLifeClient::registerLayers);
		//? if >=1.21.2
		/*modEventBus.addListener(net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent.class, RenderedEntity::register);*/
		//? if >=1.21.6
		/*modEventBus.addListener(net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent.class, SchematicPreview::register);*/
		modEventBus.addListener(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent.class, VillageParticleProviders::register);
		modEventBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, StaffClient::onSetup);
		//? if <1.21.4
		modEventBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, VillagerLifeClient::bowProperties);
		modEventBus.addListener(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent.class, StaffClient::onExtensions);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent.BossEventProgress.class, GolemClientEffects::onBossBar);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.SelectMusicEvent.class, GolemClientEffects::onMusic);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog.class, GolemClientEffects::onFog);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor.class, GolemClientEffects::onFogColor);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles.class, GolemClientEffects::onCamera);
	}

	private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(VillageEntities.TOCSIN_GOLEM, TocsinGolemRenderer::new);
		event.registerEntityRenderer(VillageEntities.PLATE_CRAWLER, PlateCrawlerRenderer::new);
		event.registerEntityRenderer(VillageEntities.TOCSIN_ANVIL, TocsinAnvilRenderer::new);
		event.registerEntityRenderer(VillageEntities.HAND_CART, HandCartRenderer::new);
		event.registerEntityRenderer(VillageEntities.SEAT, net.minecraft.client.renderer.entity.NoopRenderer::new);
		event.registerEntityRenderer(VillageEntities.SPIRE_WALKER, SpireGuardRenderer::new);
		event.registerEntityRenderer(VillageEntities.SPIRE_FLYER, SpireGuardRenderer::new);
		event.registerEntityRenderer(VillageEntities.SPIRE_SWIMMER, SpireGuardRenderer::new);
		event.registerEntityRenderer(VillageEntities.SPIRE_KEEPER, SpireGuardRenderer::new);
		event.registerEntityRenderer(VillageEntities.SPIRE_BOLT, net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
		event.registerBlockEntityRenderer(LifeBlocks.DECOR_BE, DecorRenderer::new);
		event.registerBlockEntityRenderer(LifeBlocks.CLOCK_BE, TowerClockRenderer::new);
		event.registerBlockEntityRenderer(com.vnap.mega.MegaBlocks.CONTROLLER_BE, MegaControllerRenderer::new);
		event.registerBlockEntityRenderer(LifeBlocks.HOUSE_SIGN_BE, HouseSignRenderer::new);
		event.registerBlockEntityRenderer(com.vnap.spire.SpireBlocks.RIFT_BE, com.vnap.client.spire.SpireRiftRenderer::new);
	}

	private static void registerScreens(RegisterMenuScreensEvent event) {
		event.register(LifeBlocks.MACHINE_MENU, MachineScreen::new);
		event.register(LifeBlocks.SHOP_MENU, ShopScreen::new);
		event.register(com.vnap.spire.SpireBlocks.CORE_ANVIL_MENU, CoreAnvilScreen::new);
		event.register(com.vnap.spire.SpireBlocks.CHEST_MENU, com.vnap.client.ui.ThemedChestScreen::spire);
		event.register(com.vnap.block.LifeBlocks.PARCEL_MENU, com.vnap.client.ui.ThemedChestScreen::village);
	}

	//? if <1.21.4 {
	/** The spire bows draw like the vanilla one: the vanilla "pull" and "pulling" properties for their pulling models. */
	@SuppressWarnings("FutureReturnValueIgnored")
	private static void bowProperties(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			for (com.vnap.spire.gear.GearTier tier : com.vnap.spire.gear.GearTier.values()) {
				net.minecraft.world.item.Item bow = com.vnap.spire.gear.SpireGearItems.get(tier, com.vnap.spire.gear.GearPiece.BOW);
				net.minecraft.client.renderer.item.ItemProperties.register(bow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"),
						(stack, level, entity, seed) -> entity == null || entity.getUseItem() != stack ? 0.0F
								: (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F);
				net.minecraft.client.renderer.item.ItemProperties.register(bow, net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"),
						(stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
			}
		});
	}
	//?}

	private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(TocsinGolemRenderer.LAYER, () -> BedrockModels.geometry(VillagerLife.id("tocsin_golem")));
		event.registerLayerDefinition(PlateCrawlerRenderer.LAYER, () -> BedrockModels.geometry(VillagerLife.id("plate_crawler")));
		event.registerLayerDefinition(InfantRenderer.LAYER, () -> BedrockModels.geometry(VillagerLife.id("infant")));
		event.registerLayerDefinition(com.vnap.client.spire.SpireArmorLayer.LAYER, com.vnap.client.spire.SpireArmorLayer::layer);
		for (com.vnap.spire.Genome.Body body : com.vnap.spire.Genome.Body.values()) {
			event.registerLayerDefinition(SpireGuardRenderer.layer(body), () -> BedrockModels.geometry(VillagerLife.id("spire/" + body.model())));
		}
	}

	private static void addLayers(EntityRenderersEvent.AddLayers event) {
		var renderer = event.getRenderer(EntityType.VILLAGER);
		if (renderer instanceof VillagerRenderer villagerRenderer) {
			villagerRenderer.addLayer(new VillagerOutfitLayer(villagerRenderer));
		}
		// the Spire armour's plates on whoever wears armour in the Spire's way: players, armour stands (and mannequins)
		if (event.getRenderer(EntityType.ARMOR_STAND) instanceof net.minecraft.client.renderer.entity.ArmorStandRenderer stand) {
			stand.addLayer(new com.vnap.client.spire.SpireArmorLayer<>(stand));
		}
		for (var skin : event.getSkins()) {
			//? if >=1.21.9 {
			/*var player = event.getPlayerRenderer(skin);
			if (player != null) player.addLayer(new com.vnap.client.spire.SpireArmorLayer<>(player));
			var mannequin = event.getMannequinRenderer(skin);
			if (mannequin != null) mannequin.addLayer(new com.vnap.client.spire.SpireArmorLayer<>(mannequin));
			*///?} else {
			if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) {
				player.addLayer(new com.vnap.client.spire.SpireArmorLayer<>(player));
			}
			//?}
		}
	}

	private static void onTooltip(ItemTooltipEvent event) {
		if (event.getItemStack().getItem() instanceof com.vnap.item.Tipped tipped) {
			event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), tipped.tips());
		}
		//? if >=1.21.5 {
		/*if (event.getItemStack().getItem() instanceof net.minecraft.world.item.BlockItem item && item.getBlock() instanceof com.vnap.block.DecorBlock decor) {
			event.getToolTip().add(Math.min(1, event.getToolTip().size()), decor.tooltip());
		}
		if (event.getItemStack().getItem() instanceof com.vnap.spire.gear.GearItem) {
			java.util.List<Component> lines = new java.util.ArrayList<>();
			com.vnap.spire.gear.SpireGear.tooltip(event.getItemStack(), lines);
			event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), lines);
		}
		*///?}
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
		String key = "tooltip." + id.getNamespace() + "." + id.getPath();
		if (id.getNamespace().equals(VillagerLife.MOD_ID) && net.minecraft.locale.Language.getInstance().has(key)) {
			event.getToolTip().add(1, Component.translatable(key).withStyle(ChatFormatting.GRAY));
		}
	}

	/** /vnapguide [entry or chapter] [spread]: the guide opened at that page (for checking the book page by page). */
	private static void onClientCommands(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent event) {
		event.getDispatcher().register(net.minecraft.commands.Commands.literal("vnapguide")
				.executes(ctx -> openGuide("", 0))
				.then(net.minecraft.commands.Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
								com.vnap.client.guide.GuideScreen.ids(), builder))
						.executes(ctx -> openGuide(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"), 0))
						.then(net.minecraft.commands.Commands.argument("spread", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
								.executes(ctx -> openGuide(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"),
										com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "spread"))))));
		// /vnapsim [chunks]: the simulation distance now (the integrated server takes it from this option every tick).
		// A run with /tick sprint spent its time on vanilla's 625 chunks round the player, not on the town, which the
		// mod keeps ticking itself (TownLoading); a smaller one sprints several times faster. Not saved to options.txt
		// by itself; /vnapsim 12 puts it back.
		event.getDispatcher().register(net.minecraft.commands.Commands.literal("vnapsim")
				.executes(ctx -> {
					ctx.getSource().sendSystemMessage(Component.translatable("vnap.sim_distance", Minecraft.getInstance().options.simulationDistance().get()));
					return 1;
				})
				.then(net.minecraft.commands.Commands.argument("chunks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 32))
						.executes(ctx -> {
							int chunks = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "chunks");
							Minecraft.getInstance().tell(() -> Minecraft.getInstance().options.simulationDistance().set(chunks));
							ctx.getSource().sendSystemMessage(Component.translatable("vnap.sim_distance", chunks));
							return 1;
						})));
		com.vnap.client.dev.DevBot.register(event.getDispatcher());
		//? if <1.21.2 {
		com.vnap.client.dev.Recorder.register(event.getDispatcher());
		//?}
		com.vnap.client.dev.Camera.register(event.getDispatcher());
		// /vnapreload: resources again, as F3+T (models and textures after an edit)
		event.getDispatcher().register(net.minecraft.commands.Commands.literal("vnapreload").executes(ctx -> {
			Minecraft.getInstance().tell(() -> Minecraft.getInstance().reloadResourcePacks().exceptionally(error -> {
				com.vnap.VillagerLife.LOGGER.error("resource reload failed", error);
				return null;
			}));
			return 1;
		}));
	}

	private static int openGuide(String id, int spread) {
		// /vnapguide all: every chapter open on this client (or closed again), for looking through the whole book
		if (id.equals("all")) {
			com.vnap.client.guide.GuideBook.everything = !com.vnap.client.guide.GuideBook.everything;
			id = "";
		}
		// the chat screen closes after the command has run: open the book on the next tick
		String at = id;
		Minecraft.getInstance().tell(() -> com.vnap.client.McClient.setScreen(com.vnap.client.guide.GuideScreen.at(at, spread)));
		return 1;
	}

	private static void onUseItem(PlayerInteractEvent.RightClickItem event) {
		if (!event.getLevel().isClientSide()) return;
		if (!event.getItemStack().is(LifeItems.HANDBOOK)) return;
		com.vnap.client.McClient.setScreen(new com.vnap.client.guide.GuideScreen());
		event.setCancellationResult(InteractionResult.SUCCESS);
		event.setCanceled(true);
	}

	private static void onSystemChat(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System event) {
		com.vnap.client.dev.DevBot.heard(event.getMessage().getString());
	}

	private static void onTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		DialogueSoundState.tick(client);
		DialogueSubtitleState.tick(client);
		GolemClientEffects.tick();
		com.vnap.client.dev.DevBot.tick();
	}

	private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		DialogueSoundState.clear(Minecraft.getInstance());
		DialogueSubtitleState.clear();
		LifeSettingsState.reset();
		com.vnap.client.VillageClientState.lastMood = null;
		ErrandHud.clear();
		com.vnap.client.spire.SpireHud.clear();
		com.vnap.client.guide.GuideBook.forget();
	}

	static {
		com.vnap.spire.guard.SpireGuard.NAMED = VillagerLifeClient::named;
	}

	/** A guard's health plate: the one under the crosshair, and the nearest guard within six blocks. */
	private static boolean named(com.vnap.spire.guard.SpireGuard guard) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) return false;
		// the plate right before the eyes (a guard at arm's length, taller than the player) filled half the screen
		if (McClient.camera().distanceToSqr(guard.getX(), guard.getY() + guard.getBbHeight() + 0.5, guard.getZ()) < 2.5 * 2.5) return false;
		if (guard.equals(mc.crosshairPickEntity)) return true;
		if (guard.distanceToSqr(mc.player) > 36.0) return false;
		for (com.vnap.spire.guard.SpireGuard other : guard.level().getEntitiesOfClass(com.vnap.spire.guard.SpireGuard.class, mc.player.getBoundingBox().inflate(6.0))) {
			if (!other.equals(guard) && other.isAlive() && other.distanceToSqr(mc.player) < guard.distanceToSqr(mc.player)) return false;
		}
		return true;
	}
}
