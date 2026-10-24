package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The spire's rules: nothing in its dimension or in a spire's box is broken, set, blown up or flooded (a creative
 * operator excepted); a player who dies on a floor is put out before the spire's gate with all they carried and their
 * experience; one who logs in on a floor
 * whose slot is gone (a restart) is taken to it again.
 */
public final class SpireEvents {
	// ponytail: in memory only; a server stopped while a climber lies dead loses what they carried
	private static final java.util.Map<java.util.UUID, java.util.List<net.minecraft.world.item.ItemStack>> KEPT = new java.util.HashMap<>();

	private SpireEvents() {
	}

	public static void register(IEventBus modBus) {
		com.vnap.spire.gear.GearEffects.register();
		modBus.addListener((RegisterTicketControllersEvent event) -> event.register(SpireSlots.TICKETS));
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
			SpireSlots.tick(event.getServer());
			com.vnap.spire.outer.Outer.tick(event.getServer());
			SpireGrowth.tick(event.getServer());
			if (event.getServer().getTickCount() % 100 == 0) Expeditions.tick(event.getServer());
		});
		NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
			SpireSlots.clearAll();
			SpireGrowth.clear();
			SpireRewards.clear();
			com.vnap.spire.outer.Outer.clear();
			com.vnap.mega.MegaWork.forgetAll();
		});
		NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
			if (guarded(event.getPlayer(), event.getLevel(), event.getPos())) event.setCanceled(true);
		});
		NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> {
			Player player = event.getEntity() instanceof Player p ? p : null;
			if (guarded(player, event.getLevel(), event.getPos())) event.setCanceled(true);
		});
		NeoForge.EVENT_BUS.addListener((ExplosionEvent.Detonate event) -> {
			Level level = event.getLevel();
			event.getAffectedBlocks().removeIf(pos -> guarded(null, level, pos));
		});
		NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
			if (event.getItemStack().getItem() instanceof BucketItem && guarded(event.getEntity(), event.getLevel(), event.getPos())) event.setCanceled(true);
		});
		NeoForge.EVENT_BUS.addListener((EntityTeleportEvent.EnderPearl event) -> {
			if (SpireSlots.isSpire(event.getPlayer().level())) event.setCanceled(true);
		});
		NeoForge.EVENT_BUS.addListener((EntityTeleportEvent.ChorusFruit event) -> {
			if (SpireSlots.isSpire(event.getEntityLiving().level())) event.setCanceled(true);
		});
		NeoForge.EVENT_BUS.addListener(SpireEvents::respawn);
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) -> {
			if (event.getEntity() instanceof ServerPlayer player && SpireSlots.isSpire(player.level())) {
				var by = event.getSource().getEntity();
				com.vnap.VillagerLife.trace("vnap spire hurt: {} {} by {} ({}) {} -> {}", player.getName().getString(),
						String.format(java.util.Locale.ROOT, "%.1f", event.getNewDamage()), by == null ? "-" : by.getName().getString(),
						event.getSource().getMsgId(), String.format(java.util.Locale.ROOT, "%.1f", event.getOriginalDamage()), String.format(java.util.Locale.ROOT, "%.1f", player.getHealth()));
			}
			if (event.getEntity() instanceof com.vnap.spire.guard.SpireGuard guard && event.getSource().getEntity() instanceof ServerPlayer player) {
				com.vnap.VillagerLife.trace("vnap spire hit: {} {} {} -> {}/{}", player.getName().getString(), guard.getName().getString(),
						String.format(java.util.Locale.ROOT, "%.1f of %.1f", event.getNewDamage(), event.getOriginalDamage()),
						String.format(java.util.Locale.ROOT, "%.1f", guard.getHealth()), String.format(java.util.Locale.ROOT, "%.1f", guard.getMaxHealth()));
			}
		});
		// a climber keeps what they carry: what fell on a floor would go with the floor
		NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) -> {
			if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !SpireSlots.isSpire(player.level())) return;
			java.util.List<net.minecraft.world.item.ItemStack> kept = new java.util.ArrayList<>();
			net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) kept.add(inventory.removeItemNoUpdate(i));
			KEPT.put(player.getUUID(), kept);
		});
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player && KEPT.containsKey(player.getUUID())) event.setCanceled(true);
		});
		NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> {
			if (!event.isWasDeath()) return;
			java.util.List<net.minecraft.world.item.ItemStack> kept = KEPT.remove(event.getOriginal().getUUID());
			if (kept == null) return;
			Player player = event.getEntity();
			for (int i = 0; i < kept.size() && i < player.getInventory().getContainerSize(); i++) player.getInventory().setItem(i, kept.get(i));
			player.experienceLevel = event.getOriginal().experienceLevel;
			player.experienceProgress = event.getOriginal().experienceProgress;
			player.totalExperience = event.getOriginal().totalExperience;
		});
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartingEvent event) -> SpireGuards.newRun());
		// the Builders' Writ with every wandering trader, not by chance: six diamonds and sixteen emeralds (some ten
		// diamonds' worth, as the stall prices it)
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) -> {
			if (!event.loadedFromDisk() && !event.getLevel().isClientSide()
					&& event.getEntity() instanceof net.minecraft.world.entity.npc.WanderingTrader trader) writ(trader);
		});
		// a guard saved with a floor's chunks by an earlier run is not that floor's any more (it is drawn anew)
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) -> {
			if (event.loadedFromDisk() && SpireSlots.isSpire(event.getLevel()) && !(event.getEntity() instanceof Player)
					&& (!SpireGuards.current(event.getEntity()) || event.getEntity() instanceof com.vnap.spire.guard.SpireBolt
					|| (event.getEntity().getPersistentData().contains(SpireGuards.OUTER)
							&& !com.vnap.spire.outer.Outer.open(com.vnap.Nbt.getLong(event.getEntity().getPersistentData(), SpireGuards.OUTER))))) {
				event.setCanceled(true);
			}
		});
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player && SpireSlots.isSpire(player.level())) back(player);
		});
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			// their untaken shares stay (SpireRewards.LEFT): the chest is marked taken already, a relog must not lose it
			SpireTravel.forget(event.getEntity().getUUID());
			com.vnap.mega.Waygates.forget(event.getEntity().getUUID());
			com.vnap.spire.outer.Outer.forget(event.getEntity().getUUID());
		});
	}

	/** A block nobody may touch here: the spire dimension, or a spire's box in another (a creative operator may). */
	static boolean guarded(@org.jspecify.annotations.Nullable Player player, net.minecraft.world.level.LevelAccessor accessor, BlockPos pos) {
		if (!(accessor instanceof ServerLevel level)) return false;
		if (player != null && player.isCreative() && com.vnap.Mc.op(player)) return false;
		// a secret room's wall is there to be broken
		if (player != null && level.getBlockState(pos).getBlock() instanceof SpireCrackedWallBlock) return false;
		return SpireSlots.isSpire(level) || Spires.get(level.getServer()).guards(level.dimension(), pos);
	}

	/** Died on a floor: out of the spire, before its gate (the floor is open to them again from there). */
	private static void respawn(PlayerRespawnPositionEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || !SpireSlots.isSpire(player.level())) return;
		var server = com.vnap.Mc.serverLevel(player).getServer();
		net.minecraft.resources.ResourceKey<Level> dimension = SpireProgress.returnDimension(player);
		ServerLevel to = dimension == null ? null : server.getLevel(dimension);
		BlockPos pos = SpireProgress.returnPos(player);
		if (to == null || pos.equals(BlockPos.ZERO)) return;
		event.setDimensionTransition(new DimensionTransition(to, Vec3.atBottomCenterOf(pos), Vec3.ZERO, player.getYRot(), 0.0F,
				DimensionTransition.DO_NOTHING));
		event.setCopyOriginalSpawnPosition(true);
		net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, com.vnap.network.SpireFloorPayload.NONE);
	}

	/** Logged in on a floor: the floor is drawn again (nothing of it is kept) and the player waits in its airlock. */
	private static void back(ServerPlayer player) {
		ServerLevel spireLevel = SpireSlots.level(com.vnap.Mc.serverLevel(player).getServer());
		if (spireLevel == null) return;
		Spires.Spire spire = Spires.get(spireLevel.getServer()).byId(SpireProgress.inside(player));
		if (spire == null) {
			SpireTravel.leave(player);
			return;
		}
		SpireTravel.go(player, spireLevel, spire, SpireProgress.floor(player, spire.id));
	}

	/** The writ among a new wandering trader's goods (once: its goods are kept with it). */
	static void writ(net.minecraft.world.entity.npc.WanderingTrader trader) {
		net.minecraft.world.item.trading.MerchantOffers offers = trader.getOffers();
		for (net.minecraft.world.item.trading.MerchantOffer offer : offers) {
			if (offer.getResult().is(com.vnap.item.LifeItems.SPIRE_WRIT)) return;
		}
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.EMERALD, 16),
				java.util.Optional.of(new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.DIAMOND, 6)),
				new net.minecraft.world.item.ItemStack(com.vnap.item.LifeItems.SPIRE_WRIT), 1, 20, 0.0F));
	}
}
