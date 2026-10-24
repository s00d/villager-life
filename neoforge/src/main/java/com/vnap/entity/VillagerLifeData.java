package com.vnap.entity;

import com.vnap.dialogue.FamilyState;
import com.vnap.village.workshop.WorkshopState;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/** What the mod keeps on every villager (VillagerDataMixin): its family, home, workshop and the flags the client draws. */
public interface VillagerLifeData {
	FamilyState vnap$family();

	@Nullable WorkshopState vnap$workshop();

	void vnap$setWorkshop(@Nullable WorkshopState state);

	@Nullable BlockPos vnap$homeBed();

	void vnap$setHomeBed(@Nullable BlockPos pos);

	long vnap$homeChoreDay();

	void vnap$setHomeChoreDay(long day);

	int vnap$homeDecor();

	void vnap$setHomeDecor(int count);

	long vnap$homeHurtDay();

	long vnap$homeHurtPos();

	void vnap$setHomeHurt(long day, long pos);

	long vnap$homeThanksDay();

	void vnap$setHomeThanksDay(long day);

	/** Last structure generation this villager already scanned. Not saved. */
	int vnap$homeSeenGen();

	void vnap$setHomeSeenGen(int generation);

	/** Synced: the client draws hair, dress and braid for a woman. */
	boolean vnap$female();

	void vnap$setFemale(boolean value);

	/** Synced: the client greys an old villager's hair. */
	boolean vnap$elder();

	void vnap$setElder(boolean value);

	/** Synced: the village's elected mayor (the client dresses it in the mayor's sash). */
	boolean vnap$mayor();

	void vnap$setMayor(boolean value);

	/** Synced: the village's shopkeeper (the client dresses it in the apron). */
	boolean vnap$shopkeeper();

	void vnap$setShopkeeper(boolean value);

	/** Synced: the clothes it brought back from the spire or bought (spire/SpireOutfits), 0 for none. */
	int vnap$spireOutfit();

	void vnap$setSpireOutfit(int outfit);

	/** The trade a villager had before it took an office with none: kept to give back when it leaves the office. */
	boolean vnap$hasOriginalVillagerState();

	void vnap$captureOriginalVillagerState();

	void vnap$restoreOriginalVillagerState();
}
