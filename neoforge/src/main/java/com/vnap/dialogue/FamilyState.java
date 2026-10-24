package com.vnap.dialogue;

import com.vnap.Nbt;
import com.vnap.village.town.TownRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** One villager's family ties. The gender flag itself is synced entity data on the villager (the client draws it). */
public final class FamilyState {
	/** False until the gender was decided once (on first load: a name tag's name, else the UUID). */
	public boolean genderKnown;
	public @Nullable UUID spouse;
	public String spouseName = "";
	public @Nullable UUID courting;
	public long courtStartDay = -1L;
	/** Day the couple walks to the bell; -1 while no wedding is set. */
	public long weddingDay = -1L;
	public @Nullable UUID mother;
	public @Nullable UUID father;
	/** The household head this villager lives with (itself when it owns the house). */
	public @Nullable UUID head;
	/** Where the head's home sign stands; only the head keeps it. */
	public @Nullable BlockPos signPos;
	public int childrenBorn;
	public long lastBirthDay = Long.MIN_VALUE / 2;
	/** Bed of the household head: the house this villager lives in. Null for a villager without a family house. */
	public @Nullable BlockPos home;
	public long widowedDay = Long.MIN_VALUE / 2;
	public long tickedDay = -1L;
	/** Family ticks in a row this villager has spent in water (Family.outOfWater); not saved. */
	public int wetTicks;
	/** Gifts the player already gave this villager once and for all ({@link #TOY}, {@link #BLANKET}). */
	public int gifts;
	/** Day a flower last sped up this courtship: one flower a day counts. */
	public long flowerDay = -1L;
	/** The day a player last gave this villager food (once a day it counts). */
	public long foodDay = -1L;

	/** Day this villager was born; villagers of the world get a stable guess on first sight. */
	public long bornDay = Long.MIN_VALUE;
	/** Day this villager came of age (a family child's party); {@link #UNSET} before that. Days can be negative. */
	public long grownDay = UNSET;
	/**
	 * Grown-up days lived (Generations): one a day it is about, never the days its town lay unloaded. -1 before its first
	 * grown day here. Age went by the world's calendar once, and a town left for forty days was all elders on the first
	 * night someone slept by it: ten of fourteen died of old age in one night.
	 */
	public int lived = -1;
	/** The coming-of-age cake, cleared the next day. */
	public @Nullable BlockPos cakePos;
	/** The bed the head made ready for the coming baby (its ticket held, so nobody else lies down in it). */
	public @Nullable BlockPos nursery;
	/** A newborn's first day: it lies in its cradle until this game tick, then gets up by daylight (-1 once it is out). */
	public long infantUntil = -1L;
	public @Nullable BlockPos cradle;
	/** Day the father last came to the cradle. */
	public long babyVisitDay = -1L;
	/** The player who named this baby. */
	public @Nullable UUID godparent;
	/** What a groom asked the player for before the wedding (an item id); empty when nothing. */
	public String wish = "";
	public boolean wishGranted;
	/** Day this spouse quarrelled; they make up two days later or when the player brings a flower or a cake. */
	public long quarrelDay = Long.MIN_VALUE / 2;
	/** The family's pet (on the head), the stray it is taming, and the dog's kennel block (its post and bowl beside it). */
	public @Nullable UUID pet;
	public @Nullable UUID stray;
	public long petLostDay = Long.MIN_VALUE / 2;
	public long petSeenDay = Long.MIN_VALUE / 2;
	public @Nullable BlockPos kennel;
	/** The spouse who died, whose grave a widow visits. */
	public @Nullable UUID lateSpouse;
	/** Pet things left at a house the family moved out of (a kennel, a bed, a bowl): the head goes and takes them up. */
	public final java.util.List<BlockPos> takeUp = new java.util.ArrayList<>();
	/** Copper this villager has (the household's money is on its head): wages in, purchases out (village/market). */
	public int coins;
	/** Copper the treasury lent the household for its own build (Market.finance); half of what it earns pays it back. */
	public int debt;
	/** Day the last wage was paid. */
	public long paidDay = Long.MIN_VALUE / 2;
	/** A helper's copper for building others' houses today (Market.dayLabour); not saved, a day's tally. */
	public long labourDay = -1L;
	public int labourToday;
	/** What this villager means to buy at the stall before it can put it up (Market.obtain). */
	public final java.util.List<Want> wants = new java.util.ArrayList<>();

	/**
	 * A thing to buy: an item id or {@code #food}, how many, the day it was first wanted (food and beds a poor family
	 * waited for are paid by the treasury, TownRules.HELP_DAYS) and the day the stall last had it.
	 */
	public static final class Want {
		public final String item;
		public int count;
		public final long since;
		public long offered;

		public Want(String item, int count, long since) {
			this.item = item;
			this.count = count;
			this.since = since;
			this.offered = since;
		}
	}

	public static final long UNSET = Long.MIN_VALUE;
	public static final int TOY = 1;
	public static final int BLANKET = 2;

	public boolean quarrelling(long day) {
		return day - quarrelDay < 2L;
	}

	public boolean married() {
		return spouse != null;
	}

	public boolean related(UUID other, FamilyState otherState, UUID self) {
		if (other.equals(mother) || other.equals(father) || self.equals(otherState.mother) || self.equals(otherState.father)) return true;
		return (mother != null && mother.equals(otherState.mother)) || (father != null && father.equals(otherState.father));
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("GenderKnown", genderKnown);
		if (spouse != null) Nbt.putUUID(tag, "Spouse", spouse);
		tag.putString("SpouseName", spouseName);
		if (courting != null) Nbt.putUUID(tag, "Courting", courting);
		tag.putLong("CourtStart", courtStartDay);
		tag.putLong("WeddingDay", weddingDay);
		if (mother != null) Nbt.putUUID(tag, "Mother", mother);
		if (father != null) Nbt.putUUID(tag, "Father", father);
		if (head != null) Nbt.putUUID(tag, "Head", head);
		if (signPos != null) tag.putLong("Sign", signPos.asLong());
		tag.putInt("ChildrenBorn", childrenBorn);
		tag.putLong("LastBirth", lastBirthDay);
		if (home != null) tag.putLong("Home", home.asLong());
		tag.putLong("Widowed", widowedDay);
		tag.putInt("Gifts", gifts);
		tag.putLong("Born", bornDay);
		tag.putLong("Grown", grownDay);
		tag.putInt("Lived", lived);
		tag.putLong("Ticked", tickedDay);
		if (cakePos != null) tag.putLong("Cake", cakePos.asLong());
		if (nursery != null) tag.putLong("Nursery", nursery.asLong());
		tag.putLong("InfantUntil", infantUntil);
		if (cradle != null) tag.putLong("Cradle", cradle.asLong());
		tag.putLong("BabyVisit", babyVisitDay);
		if (godparent != null) Nbt.putUUID(tag, "Godparent", godparent);
		tag.putString("Wish", wish);
		tag.putBoolean("WishGranted", wishGranted);
		tag.putLong("Quarrel", quarrelDay);
		if (lateSpouse != null) Nbt.putUUID(tag, "LateSpouse", lateSpouse);
		if (pet != null) Nbt.putUUID(tag, "Pet", pet);
		if (stray != null) Nbt.putUUID(tag, "Stray", stray);
		tag.putLong("PetLost", petLostDay);
		tag.putLong("PetSeen", petSeenDay);
		if (kennel != null) tag.putLong("Kennel", kennel.asLong());
		if (!takeUp.isEmpty()) tag.putLongArray("TakeUp", takeUp.stream().mapToLong(BlockPos::asLong).toArray());
		tag.putInt("Coins", coins);
		tag.putInt("Debt", debt);
		tag.putLong("PaidDay", paidDay);
		if (!wants.isEmpty()) {
			net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
			for (Want want : wants) {
				CompoundTag one = new CompoundTag();
				one.putString("Item", want.item);
				one.putInt("Count", want.count);
				one.putLong("Since", want.since);
				one.putLong("Offered", want.offered);
				list.add(one);
			}
			tag.put("Wants", list);
		}


		tag.putLong("FlowerDay", flowerDay);
		tag.putLong("FoodDay", foodDay);
		return tag;
	}

	public static FamilyState load(CompoundTag tag) {
		FamilyState state = new FamilyState();
		state.genderKnown = Nbt.getBoolean(tag, "GenderKnown");
		state.spouse = Nbt.hasUUID(tag, "Spouse") ? Nbt.getUUID(tag, "Spouse") : null;
		state.spouseName = Nbt.getString(tag, "SpouseName");
		state.courting = Nbt.hasUUID(tag, "Courting") ? Nbt.getUUID(tag, "Courting") : null;
		state.courtStartDay = tag.contains("CourtStart") ? Nbt.getLong(tag, "CourtStart") : -1L;
		state.weddingDay = tag.contains("WeddingDay") ? Nbt.getLong(tag, "WeddingDay") : -1L;
		state.mother = Nbt.hasUUID(tag, "Mother") ? Nbt.getUUID(tag, "Mother") : null;
		state.father = Nbt.hasUUID(tag, "Father") ? Nbt.getUUID(tag, "Father") : null;
		state.head = Nbt.hasUUID(tag, "Head") ? Nbt.getUUID(tag, "Head") : null;
		state.signPos = tag.contains("Sign") ? BlockPos.of(Nbt.getLong(tag, "Sign")) : null;
		state.childrenBorn = Nbt.getInt(tag, "ChildrenBorn");
		state.lastBirthDay = tag.contains("LastBirth") ? Nbt.getLong(tag, "LastBirth") : Long.MIN_VALUE / 2;
		state.home = tag.contains("Home") ? BlockPos.of(Nbt.getLong(tag, "Home")) : null;
		state.widowedDay = tag.contains("Widowed") ? Nbt.getLong(tag, "Widowed") : Long.MIN_VALUE / 2;
		state.gifts = Nbt.getInt(tag, "Gifts");
		state.bornDay = tag.contains("Born") ? Nbt.getLong(tag, "Born") : Long.MIN_VALUE;
		state.grownDay = tag.contains("Grown") ? Nbt.getLong(tag, "Grown") : UNSET;
		state.lived = tag.contains("Lived") ? Nbt.getInt(tag, "Lived") : -1;
		// a villager loaded again the same day (a chunk, the world) does not live that day twice
		state.tickedDay = tag.contains("Ticked") ? Nbt.getLong(tag, "Ticked") : -1L;
		state.cakePos = tag.contains("Cake") ? BlockPos.of(Nbt.getLong(tag, "Cake")) : null;
		state.nursery = tag.contains("Nursery") ? BlockPos.of(Nbt.getLong(tag, "Nursery")) : null;
		state.infantUntil = tag.contains("InfantUntil") ? Nbt.getLong(tag, "InfantUntil") : -1L;
		state.cradle = tag.contains("Cradle") ? BlockPos.of(Nbt.getLong(tag, "Cradle")) : null;
		state.babyVisitDay = tag.contains("BabyVisit") ? Nbt.getLong(tag, "BabyVisit") : -1L;
		state.godparent = Nbt.hasUUID(tag, "Godparent") ? Nbt.getUUID(tag, "Godparent") : null;
		state.wish = Nbt.getString(tag, "Wish");
		state.wishGranted = Nbt.getBoolean(tag, "WishGranted");
		state.quarrelDay = tag.contains("Quarrel") ? Nbt.getLong(tag, "Quarrel") : Long.MIN_VALUE / 2;
		state.lateSpouse = Nbt.hasUUID(tag, "LateSpouse") ? Nbt.getUUID(tag, "LateSpouse") : null;
		state.pet = Nbt.hasUUID(tag, "Pet") ? Nbt.getUUID(tag, "Pet") : null;
		state.stray = Nbt.hasUUID(tag, "Stray") ? Nbt.getUUID(tag, "Stray") : null;
		state.petLostDay = tag.contains("PetLost") ? Nbt.getLong(tag, "PetLost") : Long.MIN_VALUE / 2;
		state.petSeenDay = tag.contains("PetSeen") ? Nbt.getLong(tag, "PetSeen") : Long.MIN_VALUE / 2;
		state.kennel = tag.contains("Kennel") ? BlockPos.of(Nbt.getLong(tag, "Kennel")) : null;
		for (long pos : Nbt.getLongArray(tag, "TakeUp")) state.takeUp.add(BlockPos.of(pos));
		state.coins = Nbt.getInt(tag, "Coins");
		state.debt = Nbt.getInt(tag, "Debt");
		state.paidDay = tag.contains("PaidDay") ? Nbt.getLong(tag, "PaidDay") : Long.MIN_VALUE / 2;
		for (net.minecraft.nbt.Tag entry : Nbt.getList(tag, "Wants", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag) entry;
			Want want = new Want(Nbt.getString(one, "Item"), Nbt.getInt(one, "Count"), Nbt.getLong(one, "Since"));
			want.offered = Nbt.getLong(one, "Offered");
			state.wants.add(want);
		}


		state.flowerDay = tag.contains("FlowerDay") ? Nbt.getLong(tag, "FlowerDay") : -1L;
		state.foodDay = tag.contains("FoodDay") ? Nbt.getLong(tag, "FoodDay") : -1L;
		return state;
	}
}
