package com.vnap.spire;

import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.spire.gear.GearItem;
import com.vnap.spire.gear.GearPiece;
import com.vnap.spire.gear.GearTier;
import com.vnap.spire.gear.SpireGear;
import com.vnap.spire.gear.SpireGearItems;
import com.vnap.village.market.Market;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Heroes (docs/tower/CITY2.md §2): villagers who have been up the spire come back armed. One back from a trip buys a
 * piece of the Builders' gear of the chapter it reached, with its household's own coins (the Builders take them: the
 * coins leave the town, nothing is minted); its set full, every good trip hones its weakest piece a little with the
 * shards it found. The gear is worn in the villager's own slots (the sword in its off hand, where vanilla draws
 * nothing; client/spire/HeroLook draws it on the back and the armour over the robe), so the game keeps it, sends it to
 * the clients and counts the armour; a hero fights monsters on its town's land (village/life/HeroBehavior) and does
 * not panic while it is whole (mixin/VillagerHostilesSensorMixin). Its gear goes to its household when it falls.
 */
public final class Heroes {
	/** What a hero buys first: a sword, then the plates that matter most. */
	private static final List<GearPiece> ORDER = List.of(GearPiece.SWORD, GearPiece.CHESTPLATE, GearPiece.HELMET, GearPiece.LEGGINGS, GearPiece.BOOTS);
	/** How much a good trip hones the weakest piece of a full set. */
	private static final int TRIP_HONE = 3;
	/** A hero stops fighting under this share of its health (and panics as anyone would). */
	public static final float SPENT = 1.0F / 3.0F;

	private Heroes() {
	}

	/** The Builders' price of a piece: a sword dearer than a plate, each chapter dearer than the last. */
	public static int price(GearTier tier, GearPiece piece) {
		return piece.melee() ? 40 + 24 * tier.number() : 24 + 16 * tier.number();
	}

	public static EquipmentSlot slot(GearPiece piece) {
		EquipmentSlot armour = piece.armour();
		return armour != null ? armour : EquipmentSlot.OFFHAND;
	}

	/** The hero's sword, or empty. */
	public static ItemStack weapon(Villager villager) {
		ItemStack stack = villager.getItemBySlot(EquipmentSlot.OFFHAND);
		return stack.getItem() instanceof GearItem gear && gear.piece().melee() ? stack : ItemStack.EMPTY;
	}

	public static boolean armed(Villager villager) {
		return !villager.isBaby() && !weapon(villager).isEmpty();
	}

	/** Armed and not yet worn down. */
	public static boolean fighting(Villager villager) {
		return armed(villager) && villager.getHealth() > villager.getMaxHealth() * SPENT;
	}

	/** What a blow of the hero's sword does: the sword's own damage, as a player's would be. */
	public static float damage(ItemStack weapon) {
		float[] sum = {1.0F};
		weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE && attribute.equals(Attributes.ATTACK_DAMAGE)) sum[0] += (float) modifier.amount();
		});
		return sum[0];
	}

	/**
	 * Back from a trip that reached {@code floor}: buys the first piece it lacks (or a better-chaptered one for its
	 * worst) if its household can pay; with the set full and the trip a good one, hones its weakest piece. The piece
	 * bought, or null.
	 */
	public static @Nullable GearPiece afterTrip(ServerLevel level, Villager villager, int floor, boolean good, Random random) {
		if (villager.isBaby()) return null;
		// a town keeps so many heroes for its size: the rest stay its villagers, whatever they can pay
		if (!armed(villager) && !room(level, villager)) return null;
		// past the hundredth floor the chapters come round again (GearTier.ofChapter): a hero keeps to the Summit's
		GearTier tier = tier(floor);
		for (GearPiece piece : ORDER) {
			ItemStack worn = villager.getItemBySlot(slot(piece));
			boolean better = worn.getItem() instanceof GearItem gear && gear.tier().step() < tier.step();
			if (worn.getItem() instanceof GearItem && !better) continue;
			int price = price(tier, piece);
			if (Market.funds(level, villager) < price + com.vnap.village.town.TownRules.RESERVE * 2 || !Market.charge(level, villager, price)) return null;
			ItemStack bought = new ItemStack(SpireGearItems.get(tier, piece));
			// a better piece keeps the work put into the old one
			if (better) SpireGear.setHoned(bought, SpireGear.honed(worn));
			wear(villager, piece, bought);
			return piece;
		}
		if (good) {
			ItemStack weakest = ItemStack.EMPTY;
			for (GearPiece piece : ORDER) {
				ItemStack worn = villager.getItemBySlot(slot(piece));
				if (weakest.isEmpty() || SpireGear.honed(worn) < SpireGear.honed(weakest)) weakest = worn;
			}
			if (!weakest.isEmpty()) {
				SpireGear.setHoned(weakest, SpireGear.honed(weakest) + TRIP_HONE + random.nextInt(2));
				SpireGear.sanitize(weakest);
			}
		}
		return null;
	}

	/** Heroes a town keeps: one for every {@code PEOPLE_PER_HERO} of its people, one at the least. */
	public static int most(int population) {
		return Math.max(1, population / com.vnap.village.town.TownRules.PEOPLE_PER_HERO);
	}

	/**
	 * Room for one more hero in the villager's town. ponytail: counts the heroes loaded about it, not those up the spire
	 * on a trip; keep a count in the town's office if that ever lets a town over its share.
	 */
	public static boolean room(ServerLevel level, Villager villager) {
		net.minecraft.core.BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
		if (bell == null) return true;
		int heroes = com.vnap.village.home.Towns.members(level, bell, Heroes::armed).size();
		return heroes < most(com.vnap.village.home.Towns.population(level, bell));
	}

	/** The chapter's gear a trip to {@code floor} buys. */
	public static GearTier tier(int floor) {
		return GearTier.ofFloor(Math.clamp(floor, 1, GearTier.values().length * 10));
	}

	public static void wear(Villager villager, GearPiece piece, ItemStack stack) {
		SpireGear.sanitize(stack);
		EquipmentSlot slot = slot(piece);
		villager.setItemSlot(slot, stack);
		villager.setDropChance(slot, 0.0F);
	}

	/** A fallen hero's gear goes to one of its household not yet armed (spouse first), loaded here; else it is lost with it. */
	public static void bequeath(ServerLevel level, Villager dead) {
		if (!armed(dead)) return;
		FamilyState family = Family.state(dead);
		for (UUID id : new UUID[] {family.spouse, family.head}) {
			if (id == null || id.equals(dead.getUUID()) || !(level.getEntity(id) instanceof Villager heir) || heir.isBaby() || armed(heir)) continue;
			for (GearPiece piece : ORDER) {
				ItemStack stack = dead.getItemBySlot(slot(piece));
				if (stack.getItem() instanceof GearItem) wear(heir, piece, stack.copy());
				dead.setItemSlot(slot(piece), ItemStack.EMPTY);
			}
			return;
		}
	}
}
