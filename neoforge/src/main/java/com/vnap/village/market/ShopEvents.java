package com.vnap.village.market;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** A word with the shopkeeper opens its stall; the caravan does not trade; full coin stacks change up on their own. */
public final class ShopEvents {
	private ShopEvents() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, (PlayerInteractEvent.EntityInteract event) -> {
			if (com.vnap.Mc.tags(event.getTarget()).contains(Caravan.TAG)) {
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.SUCCESS);
				return;
			}
			if (!(event.getTarget() instanceof Villager villager) || !Shops.isKeeper(villager)
					|| !(event.getEntity() instanceof ServerPlayer player)) return;
			ServerLevel level = com.vnap.Mc.serverLevel(player);
			Shops.Shop shop = Shops.get(level.getServer()).shopOf(villager.getUUID());
			if (shop == null || shop.counter == null) return;
			ShopMenu.open(level, player, shop.counter);
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.SUCCESS);
		});
		NeoForge.EVENT_BUS.addListener(ItemEntityPickupEvent.Post.class, (ItemEntityPickupEvent.Post event) -> {
			if (Coins.isCoin(event.getOriginalStack())) Coins.consolidate(event.getPlayer());
		});
		NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, (PlayerTickEvent.Post event) -> {
			if (!event.getEntity().level().isClientSide() && event.getEntity().tickCount % 40 == 0) Coins.consolidate(event.getEntity());
		});
	}
}
