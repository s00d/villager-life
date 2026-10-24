package com.vnap.spire.gear;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/** A piece of a spire set and the vanilla pieces a Root one is made from. */
public enum GearPiece {
	SWORD(null, 0.0, 0),
	AXE(null, 0.0, 0),
	BOW(null, 0.0, 0),
	HELMET(EquipmentSlot.HEAD, 0.15, 11),
	CHESTPLATE(EquipmentSlot.CHEST, 0.40, 16),
	LEGGINGS(EquipmentSlot.LEGS, 0.30, 15),
	BOOTS(EquipmentSlot.FEET, 0.15, 13);

	/** The vanilla pieces a Root one is made from: iron, diamond or netherite; the plain bow. */
	private static final Map<Item, GearPiece> SOURCES = Map.ofEntries(
			Map.entry(Items.IRON_SWORD, SWORD), Map.entry(Items.DIAMOND_SWORD, SWORD), Map.entry(Items.NETHERITE_SWORD, SWORD),
			Map.entry(Items.IRON_AXE, AXE), Map.entry(Items.DIAMOND_AXE, AXE), Map.entry(Items.NETHERITE_AXE, AXE),
			Map.entry(Items.BOW, BOW),
			Map.entry(Items.IRON_HELMET, HELMET), Map.entry(Items.DIAMOND_HELMET, HELMET), Map.entry(Items.NETHERITE_HELMET, HELMET),
			Map.entry(Items.IRON_CHESTPLATE, CHESTPLATE), Map.entry(Items.DIAMOND_CHESTPLATE, CHESTPLATE), Map.entry(Items.NETHERITE_CHESTPLATE, CHESTPLATE),
			Map.entry(Items.IRON_LEGGINGS, LEGGINGS), Map.entry(Items.DIAMOND_LEGGINGS, LEGGINGS), Map.entry(Items.NETHERITE_LEGGINGS, LEGGINGS),
			Map.entry(Items.IRON_BOOTS, BOOTS), Map.entry(Items.DIAMOND_BOOTS, BOOTS), Map.entry(Items.NETHERITE_BOOTS, BOOTS));

	private final @Nullable EquipmentSlot armour;
	/** The piece's part of a whole set's armour (vanilla's 3/8/6/3 of 20). */
	final double share;
	/** Vanilla's durability of the piece per point of a material's (11/16/15/13). */
	final int durability;

	GearPiece(@Nullable EquipmentSlot armour, double share, int durability) {
		this.armour = armour;
		this.share = share;
		this.durability = durability;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The slot an armour piece is worn in; null for the weapons. */
	public @Nullable EquipmentSlot armour() {
		return armour;
	}

	public boolean isArmour() {
		return armour != null;
	}

	public boolean melee() {
		return this == SWORD || this == AXE;
	}

	/** The kind of piece a vanilla one makes at the Core Anvil; null when it makes none. */
	public static @Nullable GearPiece source(Item item) {
		return SOURCES.get(item);
	}
}
