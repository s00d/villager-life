package com.vnap.spire.outer;

import com.vnap.spire.gear.GearItem;
import com.vnap.spire.gear.GearPiece;
import com.vnap.spire.gear.SpireGear;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a player brings through an Outer Gate: how hard what meets it should be, and how it fights. The gates' waves
 * and bosses are drawn from the Spire's numbers at a floor its weapon fits (a Root sword the floors 1–10, a Summit one
 * honed far the hundredth and on), not from how far it climbed: who comes in with better gear meets as long a fight.
 */
public record Loadout(int floor, Style style) {
	/** How it fights: up close, from afar, or with neither (a fist, a tool). */
	public enum Style {
		MELEE, RANGED, OTHER
	}

	public static Loadout of(Player player) {
		ItemStack weapon = player.getMainHandItem();
		return new Loadout(floorOf(weapon), styleOf(weapon));
	}

	/** The floor whose guards the weapon is made for (the chapter of its tier, a little more for each honing). */
	static int floorOf(ItemStack weapon) {
		if (weapon.getItem() instanceof GearItem gear) {
			return 10 * gear.tier().step() + 5 + Math.min(SpireGear.honed(weapon), 60) / 3;
		}
		String id = BuiltInRegistries.ITEM.getKey(weapon.getItem()).getPath();
		if (id.equals("bow") || id.equals("crossbow") || id.equals("trident") || id.equals("mace")) return 12;
		if (id.startsWith("netherite_")) return 26;
		if (id.startsWith("diamond_")) return 18;
		if (id.startsWith("iron_")) return 10;
		if (id.startsWith("stone_") || id.startsWith("golden_")) return 5;
		return 3;
	}

	static Style styleOf(ItemStack weapon) {
		if (weapon.getItem() instanceof GearItem gear) return gear.piece() == GearPiece.BOW ? Style.RANGED : Style.MELEE;
		String id = BuiltInRegistries.ITEM.getKey(weapon.getItem()).getPath();
		if (id.equals("bow") || id.equals("crossbow") || id.equals("trident")) return Style.RANGED;
		if (id.endsWith("_sword") || id.endsWith("_axe") || id.equals("mace")) return Style.MELEE;
		return Style.OTHER;
	}
}
