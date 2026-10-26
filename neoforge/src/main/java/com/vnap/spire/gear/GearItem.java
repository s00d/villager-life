package com.vnap.spire.gear;

/** A piece of spire gear: its tier and kind (the sword, axe, bow and armour items share no vanilla class). */
//? if forge {
/*public interface GearItem extends net.neoforged.neoforge.common.extensions.IItemExtension {
*///?} else
public interface GearItem {
	GearTier tier();

	GearPiece piece();
}
