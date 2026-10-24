package com.vnap.spire;

import com.vnap.item.LifeItems;
import com.vnap.village.market.Coins;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.vnap.spire.gear.GearTier;
import com.vnap.spire.gear.SpireGearItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A cleared floor's chest: the first time a player opens one of a floor its own share of that floor's reward is in it,
 * taken out by hand (DESIGN §6);
 * after a death the floor is cleared again for nothing (its chest was had). Only forward: no floor is farmed. Every
 * tenth floor also gives its chapter's seals, one for each piece of a set to be raised to that tier at the Core Anvil.
 */
public final class SpireRewards {
	private SpireRewards() {
	}

	/**
	 * Shards of the Core a floor's chest holds; a keeper's floor three times as many, a half-chapter's trial (or the
	 * Schism's pair) twice. Enough that a climber who takes every floor once can raise a whole set a tier at each
	 * keeper and hone it a little (SpireGameTests).
	 */
	public static int shards(int floor) {
		return (6 + floor / 3) * (floor % 10 == 0 ? 3 : floor % 5 == 0 ? 2 : 1);
	}

	/** Coins (copper) a floor's chest holds: a day's wage or two, more higher up, never a fortune. */
	public static int coins(int floor) {
		return Math.min(Coins.SILVER, 6 + 2 * floor);
	}

	/** What a player has not taken out of a floor's chest yet, by player, spire and floor: kept while the server runs. */
	private static final java.util.Map<String, Loot> LEFT = new java.util.HashMap<>();

	static void open(ServerLevel level, ServerPlayer player, BlockPos pos) {
		SpireSlots.Slot slot = SpireSlots.at(pos);
		if (slot == null) return;
		if (slot.plan.secret != null && slot.world(slot.plan.secret).equals(pos)) {
			cache(level, player, pos, slot, false);
			return;
		}
		if (Puzzles.cache(slot, pos)) {
			cache(level, player, pos, slot, true);
			return;
		}
		if (!slot.cleared) return;
		int spire = slot.key.spire();
		int floor = slot.key.floor();
		if (slot.plan.note != null && SpireProgress.notesRead(player, spire) < floor) {
			com.vnap.Mc.say(player, Component.translatable("spire.villager_life.read_first"), true);
			return;
		}
		String key = player.getUUID() + "/" + spire + "/" + floor;
		Loot loot = LEFT.get(key);
		if (loot == null) {
			com.vnap.progress.Discoveries.discover(player, "spire_chest");
			if (!SpireProgress.firstChest(player, spire, floor)) {
				com.vnap.Mc.say(player, Component.translatable("spire.villager_life.chest_taken"), true);
				return;
			}
			loot = new Loot(level, pos, floor);
			LEFT.put(key, loot);
			Item seal = seal(floor);
			player.sendSystemMessage(Component.translatable("spire.villager_life.chest", shards(floor), Coins.text(coins(floor))));
			if (seal != null) {
				player.sendSystemMessage(Component.translatable("spire.villager_life.chest_seal",
						Component.translatable(com.vnap.spire.gear.SpireGear.tierKey(GearTier.ofFloor(floor)))));
			}
		}
		Loot shown = loot;
		shown.pos = pos;
		player.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(id, inventory, who) -> new net.minecraft.world.inventory.ChestMenu(SpireBlocks.CHEST_MENU, id, inventory, shown, 3),
				Component.translatable("block.villager_life.spire_chest")));
	}

	/**
	 * A secret room's cache: half a floor's shards and coins and a trinket (a golden apple, bottles of the Core's
	 * light), once a climber a floor, whether the floor is cleared or not.
	 */
	private static void cache(ServerLevel level, ServerPlayer player, BlockPos pos, SpireSlots.Slot slot, boolean riddle) {
		int spire = slot.key.spire();
		int floor = slot.key.floor();
		String key = player.getUUID() + "/" + spire + "/" + floor + (riddle ? "/riddle" : "/secret");
		Loot loot = LEFT.get(key);
		if (loot == null) {
			if (!(riddle ? SpireProgress.riddle(player, spire, floor) : SpireProgress.secret(player, spire, floor))) {
				com.vnap.Mc.say(player, Component.translatable("spire.villager_life.chest_taken"), true);
				return;
			}
			com.vnap.progress.VillageAdvancements.award(player, riddle ? "spire/riddle" : "spire/secret");
			java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
			stacks.add(new ItemStack(LifeItems.CORE_SHARD, Math.max(3, shards(floor) / 2)));
			stacks.add(floor % 2 == 0 ? new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE) : new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, 6));
			stacks.addAll(Coins.stacks(coins(floor) / 2));
			loot = new Loot(level, pos, stacks);
			LEFT.put(key, loot);
			player.sendSystemMessage(Component.translatable(riddle ? "spire.villager_life.puzzle.cache" : "spire.villager_life.secret"));
			com.vnap.Mc.playTo(player, com.vnap.sound.SpireSounds.CACHE, SoundSource.BLOCKS, 1.2F, 1.0F);
		}
		Loot shown = loot;
		shown.pos = pos;
		player.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(id, inventory, who) -> new net.minecraft.world.inventory.ChestMenu(SpireBlocks.CHEST_MENU, id, inventory, shown, 3),
				Component.translatable(riddle ? "spire.villager_life.puzzle.cache_title" : "block.villager_life.spire_cache")));
	}

	/** Forgets what is left in a player's chests (the player's own, it never reaches anyone else). */
	static void clear() {
		LEFT.clear();
	}

	/**
	 * One player's share of a floor's chest: the reward laid out in the middle row, taken out by hand like any chest's.
	 * The chest's lid stands open while someone looks into it.
	 */
	static final class Loot extends net.minecraft.world.SimpleContainer {
		private final ServerLevel level;
		BlockPos pos;
		private int viewers;

		Loot(ServerLevel level, BlockPos pos, int floor) {
			this(level, pos, reward(floor));
		}

		Loot(ServerLevel level, BlockPos pos, java.util.List<ItemStack> stacks) {
			super(27);
			this.level = level;
			this.pos = pos;
			int start = 13 - stacks.size() / 2;
			for (int i = 0; i < stacks.size(); i++) setItem(Math.min(26, start + i), stacks.get(i));
		}

		@Override
		//? if >=1.21.11 {
		/*public void startOpen(net.minecraft.world.entity.ContainerUser user) {
		*///?} else
		public void startOpen(Player user) {
			if (viewers++ == 0) lid(true);
		}

		@Override
		//? if >=1.21.11 {
		/*public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
		*///?} else
		public void stopOpen(Player user) {
			if (--viewers <= 0) {
				viewers = 0;
				// another climber may still be looking into the same chest (each has a share of its own)
				for (Loot other : LEFT.values()) if (other != this && other.viewers > 0 && other.pos.equals(pos)) return;
				lid(false);
			}
		}

		private void lid(boolean open) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(SpireBlocks.CHEST)) return;
			level.setBlock(pos, state.setValue(SpireChestBlock.OPEN, open), 3);
			level.playSound(null, pos, open ? com.vnap.sound.SpireSounds.CHEST : SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.9F, 1.0F);
			if (open) level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.25, 0.2, 0.25, 0.04);
		}
	}

	private static java.util.List<ItemStack> reward(int floor) {
		java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
		stacks.add(new ItemStack(LifeItems.CORE_SHARD, shards(floor)));
		stacks.add(new ItemStack(Draughts.reagent(com.vnap.spire.gear.GearTier.ofFloor(floor))));
		Item seal = seal(floor);
		if (seal != null) stacks.add(new ItemStack(seal, SEALS));
		stacks.addAll(Coins.stacks(coins(floor)));
		return stacks;
	}

	/** Seals a tenth floor's chest holds: one to raise each piece of a set (the anvil spends one a piece). */
	public static final int SEALS = com.vnap.spire.gear.GearPiece.values().length;

	/** The seal a floor's chest holds: every tenth floor's, that of the chapter it closes (DESIGN §6); null for the rest. */
	public static @Nullable Item seal(int floor) {
		return floor > 0 && floor % 10 == 0 ? SpireGearItems.seal(GearTier.ofFloor(floor)) : null;
	}

	/** Puts a floor's first-time reward into the player's pockets (what does not fit at its feet); the seal it gave, if any. */
	public static @Nullable Item reward(Player player, int floor) {
		give(player, new ItemStack(LifeItems.CORE_SHARD, shards(floor)));
		Coins.give(player, coins(floor));
		give(player, new ItemStack(Draughts.reagent(com.vnap.spire.gear.GearTier.ofFloor(floor))));
		Item seal = seal(floor);
		if (seal != null) give(player, new ItemStack(seal, SEALS));
		return seal;
	}

	private static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) com.vnap.Mc.toss(player, stack);
	}
}
