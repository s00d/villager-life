package com.vnap.compat;

import com.vnap.item.LifeItems;
import com.vnap.spire.SpireBlocks;
import com.vnap.spire.gear.GearPiece;
import com.vnap.spire.gear.GearTier;
import com.vnap.spire.gear.SpireGear;
import com.vnap.spire.gear.SpireGearItems;
import com.vnap.village.market.Coins;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What JEI and EMI show for the Core Anvil, laid out as its window: the piece, the shards of the Core, the chapter's
 * seal (none for honing) and what comes out, with the smith's coins underneath (CoreAnvilMenu).
 */
public final class AnvilCatalog {
	private AnvilCatalog() {
	}

	public record Work(String id, List<ItemStack> pieces, ItemStack shards, ItemStack seal, ItemStack result, Component cost) {
	}

	public static ItemStack anvil() {
		return new ItemStack(SpireBlocks.CORE_ANVIL);
	}

	public static Component title() {
		return SpireBlocks.CORE_ANVIL.getName();
	}

	public static List<Work> works() {
		List<Work> out = new ArrayList<>();
		for (GearPiece piece : GearPiece.values()) {
			// an iron, diamond or netherite piece and the Root seal make a Root piece
			List<ItemStack> sources = BuiltInRegistries.ITEM.stream().filter(item -> GearPiece.source(item) == piece).map(ItemStack::new).toList();
			out.add(raise("root_" + piece.id(), sources, GearTier.ROOT, piece));
			for (GearTier tier : GearTier.values()) {
				GearTier next = tier.next();
				if (next != null) out.add(raise(next.id() + "_" + piece.id(), List.of(new ItemStack(SpireGearItems.get(tier, piece))), next, piece));
			}
		}
		for (GearTier tier : GearTier.values()) {
			ItemStack sword = new ItemStack(SpireGearItems.get(tier, GearPiece.SWORD));
			ItemStack honed = sword.copy();
			SpireGear.setHoned(honed, 1);
			SpireGear.sanitize(honed);
			out.add(new Work("hone_" + tier.id(), List.of(sword), new ItemStack(LifeItems.CORE_SHARD, SpireGear.honeShards(tier, 0)), ItemStack.EMPTY,
					honed, Component.translatable("jei.villager_life.anvil_hone", Coins.text(SpireGear.honeCoins(tier, 0)))));
		}
		return out;
	}

	private static Work raise(String id, List<ItemStack> pieces, GearTier next, GearPiece piece) {
		return new Work(id, pieces, new ItemStack(LifeItems.CORE_SHARD, SpireGear.craftShards(next)), new ItemStack(SpireGearItems.seal(next)),
				new ItemStack(SpireGearItems.get(next, piece)), Component.translatable("jei.villager_life.anvil_raise", Coins.text(SpireGear.craftCoins(next))));
	}
}
