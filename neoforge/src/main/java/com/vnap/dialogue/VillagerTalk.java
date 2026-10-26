package com.vnap.dialogue;

import com.vnap.config.LifeSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** The villagers' talk wired to the game: the events it listens to and the order things run in each tick. */
public final class VillagerTalk {
	/** Why each villager came into the world, from its spawn event to its first join. */
	// FinalizeSpawn of a village the world generates comes from its generation threads
	private static final Map<UUID, MobSpawnType> BORN_OF = new ConcurrentHashMap<>();
	private static @Nullable EggUse lastEgg;

	/** A spawn egg used on a villager: the baby it makes joins the world in the same tick, next to it. */
	private record EggUse(UUID player, long tick, Vec3 at) {
	}

	private VillagerTalk() {
	}

	public static void register() {
		ExpandEvents.register();
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, (ServerTickEvent.Post event) -> tick(event.getServer()));
		NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, (ServerStoppingEvent event) -> reset());
		NeoForge.EVENT_BUS.addListener(FinalizeSpawnEvent.class, (FinalizeSpawnEvent event) -> {
			if (event.getEntity() instanceof Villager) BORN_OF.put(event.getEntity().getUUID(), event.getSpawnType());
		});
		NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, (EntityJoinLevelEvent event) -> {
			if (event.getLevel() instanceof ServerLevel level) joined(level, event.getEntity(), event.loadedFromDisk());
		});
		NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, (EntityLeaveLevelEvent event) -> {
			if (!event.getLevel().isClientSide()) left(event.getEntity());
		});
		NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, (BlockEvent.BreakEvent event) -> {
			if (event.getLevel() instanceof ServerLevel level) WorldEvents.broke(level, event.getPlayer(), event.getPos(), event.getState());
		});
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, (PlayerInteractEvent.RightClickBlock event) -> {
			if (event.getLevel() instanceof ServerLevel level) {
				WorldEvents.usedBlock(event.getEntity(), level, event.getEntity().getItemInHand(event.getHand()), event.getHitVec());
			}
		});
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, (PlayerInteractEvent.RightClickItem event) -> {
			if (event.getLevel() instanceof ServerLevel level) WorldEvents.usedItem(level, event.getEntity(), event.getItemStack());
		});
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, (PlayerInteractEvent.EntityInteract event) -> {
			if (!(event.getLevel() instanceof ServerLevel level)) return;
			InteractionResult result = clicked(level, event.getEntity(), event.getTarget(), event.getHand(), event.getItemStack());
			if (!InteractionResult.PASS.equals(result)) {
				event.setCancellationResult(result);
				event.setCanceled(true);
			}
		});
		NeoForge.EVENT_BUS.addListener(AttackEntityEvent.class, (AttackEntityEvent event) -> {
			Entity target = event.getTarget();
			if (Speech.prop(target)) {
				event.setCanceled(true);
				return;
			}
			Player player = event.getEntity();
			if (!(com.vnap.Mc.levelOf(player) instanceof ServerLevel level)) return;
			if (ModFlavorMatchers.looksLikeGun(player.getMainHandItem())) {
				WorldEvents.observed(level, player, target.position(), "Hear Gunfire Nearby", Speech.SOON);
			}
			if (target instanceof Villager villager && villager.isSleeping()) Bedtime.shake(villager, player);
		});
		NeoForge.EVENT_BUS.addListener(ExplosionEvent.Detonate.class, (ExplosionEvent.Detonate event) -> {
			if (event.getLevel() instanceof ServerLevel level) WorldEvents.exploded(level, event.getExplosion().center());
		});
		NeoForge.EVENT_BUS.addListener(PlayerWakeUpEvent.class, (PlayerWakeUpEvent event) -> Bedtime.wokeUp(event.getEntity()));
		NeoForge.EVENT_BUS.addListener(TradeWithVillagerEvent.class, (TradeWithVillagerEvent event) -> Trades.dealt(event.getAbstractVillager(), event.getEntity()));
		NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, (LivingDamageEvent.Post event) -> Harm.hurt(event.getEntity(), event.getSource(), event.getNewDamage()));
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class, (LivingDeathEvent event) -> Harm.died(event.getEntity(), event.getSource()));
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, (PlayerEvent.PlayerLoggedOutEvent event) -> {
			UUID id = event.getEntity().getUUID();
			PlayerWatch.forget(id);
			Harm.forgetPlayer(id);
			com.vnap.village.town.NoticeBoard.forget(id);
		});
	}

	private static void tick(MinecraftServer server) {
		if (!server.tickRateManager().runsNormally()) {
			Speech.silenceAll(server);
			return;
		}
		Speech.advance();
		WorldEvents.tickBroken();
		if (!LifeSettings.dialogueEnabled()) {
			Speech.silenceAll(server);
			PlayerWatch.clearGreetings();
			return;
		}
		Speech.tick(server);
		Bedtime.tick();
		Conditions.tick();
		SpeechQueue.tick();
		WorldEvents.tickBells();
		Trades.tick(server);
		long now = Speech.now();
		if (now % 10L != 0L) return;
		for (ServerLevel level : server.getAllLevels()) {
			Surroundings.clock(level);
			Surroundings.difficulty(level);
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) {
					PlayerWatch.spectating(player);
					continue;
				}
				PlayerWatch.tick(level, player);
				Trades.wanderer(level, player);
			}
			if (now % 100L == 0L) Chats.tick(level);
		}
		if (now % 1200L == 0L) {
			Speech.prune();
			// villagers of chunks generated but never loaded never join
			if (BORN_OF.size() > 4096) BORN_OF.clear();
		}
	}

	/** A right click on an entity: what a villager says to it; the rest of the click goes on as usual. */
	private static InteractionResult clicked(ServerLevel level, Player player, Entity target, InteractionHand hand, ItemStack held) {
		if (Speech.prop(target)) return InteractionResult.SUCCESS;
		if (held.getItem() instanceof SpawnEggItem && target instanceof Villager) {
			lastEgg = new EggUse(player.getUUID(), level.getGameTime(), target.position());
		}
		if (target instanceof Villager villager) {
			String item = Near.path(held);
			String gift = food(villager.isBaby(), item);
			if (gift != null) {
				Speech.say(villager, gift, "food_gift:" + villager.getUUID() + ":" + item, Speech.SOON, player);
				return InteractionResult.PASS;
			}
			if (ModFlavorMatchers.isFdFood(held)) {
				Speech.title(villager, "Share Farmer's Delight Meal", "fd_gift:" + villager.getUUID(), Speech.SOON, player);
				return InteractionResult.PASS;
			}
			if (villager.isSleeping()) {
				Bedtime.shake(villager, player);
				return InteractionResult.PASS;
			}
			Trades.mayorOffers(villager);
			if (villager.isBaby()) Speech.say(villager, "aezdiy", "baby_trade:" + villager.getUUID(), Speech.SOON, player);
			else if (hand == InteractionHand.MAIN_HAND) Trades.clicked(level, player, villager);
		} else if (target instanceof WanderingTrader trader && hand == InteractionHand.MAIN_HAND) {
			Trades.clicked(level, player, trader);
		}
		WorldEvents.handledAnimal(level, player, target);
		return InteractionResult.PASS;
	}

	private static @Nullable String food(boolean child, String item) {
		return switch (item) {
			case "beetroot" -> child ? "qrdzmt" : "rlfjux";
			case "bread" -> child ? "hbalps" : "bbjsik";
			case "carrot" -> child ? "hcdvqm" : "nqktml";
			case "potato" -> child ? "gotjxf" : "ytydjc";
			case "wheat" -> child ? null : "ebyrtk";
			default -> null;
		};
	}

	private static void joined(ServerLevel level, Entity entity, boolean fromDisk) {
		if (Speech.prop(entity)) return;
		switch (Near.path(entity)) {
			case "firework_rocket" -> WorldEvents.firework(level, entity);
			case "lightning_bolt" -> WorldEvents.lightning(level, entity);
			default -> {
			}
		}
		if (!(entity instanceof Villager villager)) return;
		MobSpawnType why = BORN_OF.remove(villager.getUUID());
		Trades.mayorOffers(villager);
		VillagerMoments.met(villager);
		EggUse egg = lastEgg;
		if (!fromDisk && egg != null && villager.isBaby() && level.getGameTime() - egg.tick() <= 1L
				&& villager.distanceToSqr(egg.at()) <= 9.0) {
			lastEgg = null;
			SpeechQueue.forget(villager.getUUID());
			SpeechQueue.add(level, villager, "abfwiv", level.getPlayerByUUID(egg.player()), Speech.now() + 2L);
			return;
		}
		if (why == null) return;
		long soon = Speech.now() + 2L;
		if (why == com.vnap.Mc.SPAWN_EGG || why == MobSpawnType.DISPENSER) {
			SpeechQueue.add(level, villager, villager.isBaby() ? "abfwiv" : "vskjkl", Near.player(level, villager.position(), 12.0), soon);
		} else if (why == MobSpawnType.BREEDING) {
			Villager parent = Near.villager(level, villager.position(), 12.0, other -> !other.equals(villager) && !other.isBaby());
			if (parent != null) {
				SpeechQueue.add(level, parent, "fbuabj", villager, soon, true);
				SpeechQueue.add(level, villager, "lgjtnf", parent, Speech.now() + DialogueCatalog.durationTicks("fbuabj") + 4L);
			} else {
				SpeechQueue.add(level, villager, "lgjtnf", null, soon);
			}
		} else if (why == MobSpawnType.CONVERSION) {
			SpeechQueue.add(level, villager, villager.isBaby() ? "ggitzq" : "ivumgm", null, soon);
		}
	}

	private static void left(Entity entity) {
		UUID id = entity.getUUID();
		Speech.forget(id);
		SpeechQueue.forget(id);
		Conditions.forget(id);
		VillagerMoments.forget(id);
		Bedtime.forget(id);
		Harm.forget(id);
		Trades.forget(id);
		Chats.forget(id);
	}

	private static void reset() {
		Speech.reset();
		SpeechQueue.clear();
		Conditions.clear();
		VillagerMoments.clear();
		Bedtime.clear();
		Harm.clear();
		Trades.clear();
		Chats.clear();
		PlayerWatch.clear();
		Surroundings.clear();
		WorldEvents.clear();
		BORN_OF.clear();
		lastEgg = null;
	}
}
