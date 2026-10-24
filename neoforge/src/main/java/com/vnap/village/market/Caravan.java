package com.vnap.village.market;

import com.vnap.dialogue.Speech;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The weekly caravan: it fills the stall's catalogue up to its targets for half the worth, paid from the till (the
 * treasury pays what the till lacks), so the stall never runs out of goods or of money for long. With a player near
 * it is seen: a trader and two llamas come up to the stall and go again (they do not trade, see ShopEvents).
 */
public final class Caravan {
	public static final String TAG = "vnap_caravan";
	private static final int STAY = 2400;

	private Caravan() {
	}

	/**
	 * Delivers now: the orders and what each catalogue good lacks of its target, at the wholesale price; and buys what
	 * the stall has too much of (over twice its target, or a player's lot over a stack) at the same price, so a
	 * thousand logs never fill the shelf for ever. Returns the copper the stall paid (minus what it got).
	 */
	public static int arrive(ServerLevel level, Shops.Shop shop, long day) {
		com.vnap.village.town.Chronicle.note(level, shop.bell, "caravan");
		int cost = 0;
		java.util.Map<Item, Integer> deliver = new java.util.LinkedHashMap<>(shop.orders);
		shop.orders.clear();
		boolean bakes = com.vnap.village.town.Bakehouse.bakes(level, shop);
		if (bakes) deliver.remove(net.minecraft.world.item.Items.BREAD);
		for (Map.Entry<Item, Integer> entry : Market.catalogue().entrySet()) {
			if (bakes && entry.getKey().equals(net.minecraft.world.item.Items.BREAD)) continue;
			int missing = entry.getValue() - shop.count(entry.getKey()) - deliver.getOrDefault(entry.getKey(), 0);
			if (missing > 0) deliver.merge(entry.getKey(), missing, Integer::sum);
		}
		for (Map.Entry<Item, Integer> entry : deliver.entrySet()) {
			ItemStack item = new ItemStack(entry.getKey());
			cost += (int) Math.ceil(Prices.price(item) * Market.WHOLESALE * entry.getValue());
			shop.put(item, entry.getValue(), false);
		}
		for (Shops.Lot lot : new java.util.ArrayList<>(shop.stock)) {
			int keep = Math.max(2 * Market.target(lot.item.getItem()), lot.fromPlayer ? lot.item.getMaxStackSize() : 32);
			if (lot.count <= keep) continue;
			int surplus = lot.count - keep;
			cost -= (int) Math.floor(lot.price * Market.WHOLESALE * surplus);
			shop.take(lot, surplus);
		}
		Orders.deliver(level, shop);
		if (cost >= 0) Market.pay(level, shop, cost);
		else shop.till -= cost;
		shop.caravanDay = day;
		shop.fairDay = day;
		Shops.get(level.getServer()).setDirty();
		Villager keeper = Shops.keeper(level, shop);
		if (keeper != null) Speech.queue(keeper, "Shopkeeper Welcomes the Caravan", null, 2L);
		BlockPos counter = shop.counter;
		if (counter != null && !level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(counter).inflate(64.0)).isEmpty()) {
			show(level, counter);
		}
		return cost;
	}

	/** A trader and his llamas walk up to the stall from the edge of sight and leave after a while. */
	static void show(ServerLevel level, BlockPos counter) {
		BlockPos start = null;
		for (BlockPos at : com.vnap.village.town.Rings.around(counter, 24, 12, 4, 8)) {
			if (!level.isLoaded(at)) continue;
			// at the stall's height or below it, not on a roof by the way
			BlockPos ground = com.vnap.village.town.Ground.near(level, at.getX(), at.getZ(), counter.getY(), 0, 6);
			if (ground != null && level.getFluidState(ground.above()).isEmpty()) {
				start = ground.above();
				break;
			}
		}
		if (start == null) return;
		WanderingTrader trader = com.vnap.Mc.create(EntityType.WANDERING_TRADER, level);
		if (trader == null) return;
		com.vnap.Mc.snap(trader, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0.0F, 0.0F);
		trader.finalizeSpawn(level, level.getCurrentDifficultyAt(start), MobSpawnType.EVENT, null);
		trader.addTag(TAG);
		trader.setDespawnDelay(STAY);
		trader.setWanderTarget(counter);
		level.addFreshEntity(trader);
		level.playSound(null, start, com.vnap.sound.GolemSounds.CARAVAN, net.minecraft.sounds.SoundSource.NEUTRAL, 1.2F, 1.0F);
		for (int i = 0; i < 2; i++) {
			TraderLlama llama = com.vnap.Mc.create(EntityType.TRADER_LLAMA, level);
			if (llama == null) continue;
			com.vnap.Mc.snap(llama, start.getX() + 0.5 + i, start.getY(), start.getZ() + 1.5, 0.0F, 0.0F);
			llama.finalizeSpawn(level, level.getCurrentDifficultyAt(start), MobSpawnType.EVENT, null);
			llama.setLeashedTo(trader, true);
			llama.addTag(TAG);
			level.addFreshEntity(llama);
		}
	}
}
