package com.vnap.gametest;

import com.vnap.item.LifeItems;
import com.vnap.spire.SpireRewards;
import com.vnap.spire.gear.CoreAnvilMenu;
import com.vnap.spire.gear.GearItem;
import com.vnap.spire.gear.GearPiece;
import com.vnap.spire.gear.GearTier;
import com.vnap.spire.gear.SpireGear;
import com.vnap.spire.gear.SpireGearItems;
import com.vnap.village.market.Coins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Locale;

/** Spire gear (docs/tower/DESIGN.md §7): honing's curve and cap, its costs, tiers opened by seals, the enchantment list, the damage cap. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class SpireGearGameTests {
	private static final long[] HONES = {0, 1, 5, 10, 25, 50, 51, 100, 1_000, 100_000, 1_000_000, Integer.MAX_VALUE};

	private SpireGearGameTests() {
	}

	@GameTest(template = "platform")
	public static void honingGivesLessAndLessAndNeverPassesItsCap(GameTestHelper helper) {
		Checks.assertTrue(helper, Math.abs(SpireGear.gain(10) - 0.55) < 0.01 && Math.abs(SpireGear.gain(50) - 0.98) < 0.01 && SpireGear.gain(0) == 0.0,
				"+10 gives about 55% of the cap, +50 about 98%: " + SpireGear.gain(10) + ", " + SpireGear.gain(50));
		double before = -1.0;
		for (long n : HONES) {
			double bonus = SpireGear.bonus(10.0, n);
			Checks.assertTrue(helper, bonus >= before && bonus < SpireGear.SHARE * 10.0 + 1.0E-9, "+" + n + " adds " + bonus + " to a base of 10");
			before = bonus;
		}
		for (GearPiece piece : GearPiece.values()) {
			for (GearTier tier : GearTier.values()) {
				GearTier next = tier.next();
				if (next == null) continue;
				for (long n : HONES) {
					int hone = (int) n;
					Checks.assertTrue(helper, SpireGear.headline(next, piece, hone) > SpireGear.headline(tier, piece, hone),
							"a tier up is always better at the same honing: " + piece + " " + tier + " +" + n);
				}
			}
		}
		// the stack carries it: a +10 Root sword hits for its base and 55% of 40% more
		ItemStack sword = new ItemStack(SpireGearItems.get(GearTier.ROOT, GearPiece.SWORD));
		SpireGear.setHoned(sword, 10);
		double[] damage = {1.0};
		sword.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute.equals(Attributes.ATTACK_DAMAGE)) damage[0] += modifier.amount();
		});
		double expected = 7.0 + 0.4 * 7.0 * SpireGear.gain(10);
		Checks.assertTrue(helper, Math.abs(damage[0] - expected) < 1.0E-6, "a +10 Root sword hits for " + damage[0] + ", not " + expected);
		Checks.assertTrue(helper, sword.getHoverName().getString().endsWith("+10"), "its name shows +10: " + sword.getHoverName().getString());
		SpireGear.setHoned(sword, 80);
		Checks.assertTrue(helper, sword.getHoverName().getString().contains("★"), "past +50 a star: " + sword.getHoverName().getString());
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void honingCostsGrowWithEveryStepAndTier(GameTestHelper helper) {
		for (GearTier tier : GearTier.values()) {
			for (int n = 0; n < 300; n++) {
				Checks.assertTrue(helper, SpireGear.honeShards(tier, n + 1) >= SpireGear.honeShards(tier, n)
						&& SpireGear.honeCoins(tier, n + 1) >= SpireGear.honeCoins(tier, n), tier + " +" + n + " costs no less than the step before");
				Checks.assertTrue(helper, SpireGear.honeShards(tier, n + 10) > SpireGear.honeShards(tier, n), tier + ": ten steps on cost more");
			}
			GearTier next = tier.next();
			if (next != null) {
				Checks.assertTrue(helper, SpireGear.honeShards(next, 10) > SpireGear.honeShards(tier, 10) && SpireGear.craftShards(next) > SpireGear.craftShards(tier),
						"a higher tier costs more to hone and to make: " + tier);
			}
			Checks.assertTrue(helper, SpireGear.honeShards(tier, 1_000_000) > 0 && SpireGear.honeCoins(tier, Integer.MAX_VALUE - 1) == Integer.MAX_VALUE,
					"a huge +n costs the most there is, never a wrapped-round number");
		}
		// +10 on a Root piece is a chapter's worth of shards, not a floor's
		int total = 0;
		for (int n = 0; n < 10; n++) total += SpireGear.honeShards(GearTier.ROOT, n);
		Checks.assertTrue(helper, total > SpireRewards.shards(9) * 3, "+10 on a Root piece costs " + total + " shards");
		// honing at the anvil takes exactly what it says and never fails: the shards out of their slot, the coins out of the purse
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack sword = new ItemStack(SpireGearItems.get(GearTier.CLOCKWORK, GearPiece.SWORD));
		ItemStack shards = ItemStack.EMPTY;
		CoreAnvilMenu.Outcome[] outcome = {CoreAnvilMenu.Outcome.NOTHING};
		Checks.assertTrue(helper, CoreAnvilMenu.work(player, sword, shards, ItemStack.EMPTY, outcome).isEmpty()
				&& outcome[0] == CoreAnvilMenu.Outcome.NO_SHARDS, "no shards, no honing: " + outcome[0]);
		shards = new ItemStack(LifeItems.CORE_SHARD, 64);
		Checks.assertTrue(helper, CoreAnvilMenu.work(player, sword, shards, ItemStack.EMPTY, outcome).isEmpty()
				&& outcome[0] == CoreAnvilMenu.Outcome.NO_COINS && shards.getCount() == 64, "no coins, no honing, nothing taken");
		Coins.give(player, 1000);
		for (int n = 0; n < 5; n++) {
			int had = shards.getCount();
			int coins = Coins.count(player.getInventory());
			ItemStack preview = CoreAnvilMenu.result(sword, ItemStack.EMPTY);
			sword = CoreAnvilMenu.work(player, sword, shards, ItemStack.EMPTY, outcome);
			Checks.assertTrue(helper, outcome[0] == CoreAnvilMenu.Outcome.DONE && SpireGear.honed(sword) == SpireGear.honed(preview), "honing +" + n + " goes, as shown");
			Checks.assertTrue(helper, had - shards.getCount() == SpireGear.honeShards(GearTier.CLOCKWORK, n)
					&& coins - Coins.count(player.getInventory()) == SpireGear.honeCoins(GearTier.CLOCKWORK, n), "it costs what it shows at +" + n);
		}
		Checks.assertTrue(helper, SpireGear.honed(sword) == 5, "five hones, +5: " + SpireGear.honed(sword));
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aTierIsOpenedByItsChaptersSeal(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(LifeItems.CORE_SHARD, 64));
		Coins.give(player, 4096);
		ItemStack iron = new ItemStack(Items.IRON_SWORD);
		Holder<Enchantment> sharpness = enchantment(helper, Enchantments.SHARPNESS);
		Holder<Enchantment> smite = enchantment(helper, Enchantments.SMITE);
		EnchantmentHelper.updateEnchantments(iron, enchantments -> {
			enchantments.set(sharpness, 5);
			enchantments.set(smite, 5);
		});
		iron.set(net.minecraft.core.component.DataComponents.DAMAGE, 100);
		CoreAnvilMenu.Outcome[] outcome = {CoreAnvilMenu.Outcome.NOTHING};
		ItemStack shards = new ItemStack(LifeItems.CORE_SHARD, 64);
		ItemStack made = CoreAnvilMenu.work(player, iron, shards, ItemStack.EMPTY, outcome);
		Checks.assertTrue(helper, made.isEmpty() && outcome[0] == CoreAnvilMenu.Outcome.NO_SEAL && shards.getCount() == 64,
				"no Root seal, no Root sword, nothing paid: " + outcome[0]);
		ItemStack rootSeal = new ItemStack(SpireGearItems.seal(GearTier.ROOT));
		made = CoreAnvilMenu.work(player, iron, shards, rootSeal, outcome);
		Checks.assertTrue(helper, outcome[0] == CoreAnvilMenu.Outcome.DONE && made.is(SpireGearItems.get(GearTier.ROOT, GearPiece.SWORD)),
				"an iron sword and the Root seal make a Root sword: " + outcome[0] + " " + made);
		Checks.assertTrue(helper, made.getEnchantmentLevel(sharpness) == 3 && made.getEnchantmentLevel(smite) == 0 && made.getDamageValue() == 0,
				"its sharpness kept to III, smite gone, wear mended");
		Checks.assertTrue(helper, 64 - shards.getCount() == SpireGear.craftShards(GearTier.ROOT) && rootSeal.isEmpty(),
				"it costs the Root price and the seal");
		rootSeal = new ItemStack(SpireGearItems.seal(GearTier.ROOT));
		SpireGear.setHoned(made, 7);
		Checks.assertTrue(helper, CoreAnvilMenu.work(made, rootSeal) == CoreAnvilMenu.Work.HONE, "with the Root seal a Root sword is honed, not raised");
		shards = new ItemStack(LifeItems.CORE_SHARD, 64);
		ItemStack archive = CoreAnvilMenu.work(player, made, shards, new ItemStack(SpireGearItems.seal(GearTier.ARCHIVE)), outcome);
		Checks.assertTrue(helper, archive.is(SpireGearItems.get(GearTier.ARCHIVE, GearPiece.SWORD)) && SpireGear.honed(archive) == 7
				&& archive.getEnchantmentLevel(sharpness) == 3, "with the Archive seal: an Archive sword, still +7 and sharp");
		ItemStack summit = new ItemStack(SpireGearItems.get(GearTier.SUMMIT, GearPiece.BOOTS));
		Checks.assertTrue(helper, CoreAnvilMenu.work(summit, new ItemStack(SpireGearItems.seal(GearTier.SUMMIT))) == CoreAnvilMenu.Work.HONE
				&& CoreAnvilMenu.nextTier(summit) == null, "nothing above Summit: only honing");
		Checks.assertTrue(helper, CoreAnvilMenu.work(new ItemStack(Items.GOLDEN_SWORD), ItemStack.EMPTY) == CoreAnvilMenu.Work.NONE, "a golden sword makes nothing");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void spireGearTakesOnlyItsListOfEnchantments(GameTestHelper helper) {
		ItemStack sword = new ItemStack(SpireGearItems.get(GearTier.HIVE, GearPiece.SWORD));
		ItemStack helmet = new ItemStack(SpireGearItems.get(GearTier.HIVE, GearPiece.HELMET));
		ItemStack bow = new ItemStack(SpireGearItems.get(GearTier.HIVE, GearPiece.BOW));
		Checks.assertTrue(helper, sword.supportsEnchantment(enchantment(helper, Enchantments.SHARPNESS))
				&& !sword.supportsEnchantment(enchantment(helper, Enchantments.SMITE))
				&& !sword.supportsEnchantment(enchantment(helper, Enchantments.BANE_OF_ARTHROPODS)), "a sword: sharpness, not smite or bane");
		Checks.assertTrue(helper, helmet.supportsEnchantment(enchantment(helper, Enchantments.PROTECTION))
				&& helmet.supportsEnchantment(enchantment(helper, Enchantments.RESPIRATION))
				&& !helmet.supportsEnchantment(enchantment(helper, Enchantments.SHARPNESS)), "a helmet: protection and respiration");
		Checks.assertTrue(helper, bow.supportsEnchantment(enchantment(helper, Enchantments.POWER))
				&& !bow.supportsEnchantment(enchantment(helper, Enchantments.MULTISHOT)), "a bow: power, not multishot");
		Checks.assertTrue(helper, bow.isEnchantable() && sword.isEnchantable(), "they go on the enchanting table");
		for (ItemStack stack : new ItemStack[]{sword, helmet, bow}) {
			everything(helper, stack);
			SpireGear.sanitize(stack);
			GearItem gear = (GearItem) stack.getItem();
			for (var entry : stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).entrySet()) {
				int cap = SpireGear.cap(gear.piece(), entry.getKey());
				Checks.assertTrue(helper, cap > 0 && entry.getIntValue() <= cap, gear.piece() + " keeps " + entry.getKey().getRegisteredName() + " " + entry.getIntValue());
			}
		}
		Checks.assertTrue(helper, sword.getEnchantmentLevel(enchantment(helper, Enchantments.SHARPNESS)) == 3
				&& sword.getEnchantmentLevel(enchantment(helper, Enchantments.MENDING)) == 1, "sharpness III and mending stay on the sword");
		Checks.assertTrue(helper, helmet.getEnchantmentLevel(enchantment(helper, Enchantments.PROTECTION)) == 3, "protection III on the helmet");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void noSpireWeaponPassesTheDamageCap(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
		target.setNoAi(true);
		double most = 0.0;
		String worst = "";
		for (GearTier tier : GearTier.values()) {
			for (GearPiece piece : new GearPiece[]{GearPiece.SWORD, GearPiece.AXE, GearPiece.BOW}) {
				for (long n : HONES) {
					ItemStack stack = new ItemStack(SpireGearItems.get(tier, piece));
					SpireGear.setHoned(stack, (int) n);
					everything(helper, stack);
					SpireGear.sanitize(stack);
					double dps = SpireGear.dps(helper.getLevel(), stack, player, target);
					if (dps > most) {
						most = dps;
						worst = tier + " " + piece + " +" + n;
					}
					Checks.assertTrue(helper, dps > 0.0 && dps <= SpireGear.DPS_CAP,
							String.format(Locale.ROOT, "%s %s +%d deals %.2f a second, over the cap %.0f", tier, piece, n, dps, SpireGear.DPS_CAP));
				}
			}
		}
		// the cap is meant to bind, not to sit far above everything
		Checks.assertTrue(helper, most > SpireGear.DPS_CAP * 0.85, "the hardest hitter (" + worst + ") comes near the cap: " + most);
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void everyTenthFloorGivesItsChaptersSeal(GameTestHelper helper) {
		for (int floor = 1; floor <= 120; floor++) {
			Item seal = SpireRewards.seal(floor);
			if (floor % 10 != 0) {
				Checks.assertTrue(helper, seal == null, "floor " + floor + " holds no seal");
				continue;
			}
			Checks.assertTrue(helper, seal != null && seal.equals(SpireGearItems.seal(GearTier.ofChapter(floor / 10 - 1))),
					"floor " + floor + " holds its chapter's seal");
		}
		Checks.assertTrue(helper, java.util.Objects.equals(SpireRewards.seal(10), SpireGearItems.seal(GearTier.ROOT))
				&& java.util.Objects.equals(SpireRewards.seal(100), SpireGearItems.seal(GearTier.SUMMIT)), "floor 10 Root's, floor 100 Summit's");
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		SpireRewards.reward(player, 9);
		Checks.assertFalse(helper, CoreAnvilMenu.hasSeal(player.getInventory(), GearTier.ROOT), "floor 9's chest has no seal");
		Checks.assertTrue(helper, SpireRewards.SEALS == GearPiece.values().length, "a seal for every piece of a set");
		SpireRewards.reward(player, 20);
		Checks.assertTrue(helper, CoreAnvilMenu.hasSeal(player.getInventory(), GearTier.ARCHIVE)
				&& CoreAnvilMenu.shards(player.getInventory()) == SpireRewards.shards(9) + SpireRewards.shards(20), "floor 20's chest: shards and the Archive seal");
		helper.succeed();
	}

	private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
	}

	/** Every enchantment in the game at its most, compatible or not: what sanitizing has to bring down to the list. */
	private static void everything(GameTestHelper helper, ItemStack stack) {
		var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		EnchantmentHelper.updateEnchantments(stack, enchantments -> registry.listElements().forEach(holder -> enchantments.set(holder, holder.value().getMaxLevel())));
	}

	/** A hero's gear is not only looks: its armour and its blows grow with tier and honing, and its blows carry the tier's mark. */
	@GameTest(template = "platform")
	public static void aHerosGearGuardsItAndHitsHarderTierByTier(GameTestHelper helper) {
		double[] armour = new double[2];
		float[] dealt = new float[2];
		boolean[] slowed = new boolean[2];
		GearTier[] tiers = {GearTier.ROOT, GearTier.ICE};
		int[] hones = {0, 30};
		for (int i = 0; i < 2; i++) {
			net.minecraft.world.entity.npc.Villager hero = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(1 + 2 * i, 2, 1));
			for (GearPiece piece : new GearPiece[] {GearPiece.SWORD, GearPiece.HELMET, GearPiece.CHESTPLATE, GearPiece.LEGGINGS, GearPiece.BOOTS}) {
				ItemStack stack = new ItemStack(SpireGearItems.get(tiers[i], piece));
				SpireGear.setHoned(stack, hones[i]);
				com.vnap.spire.Heroes.wear(hero, piece, stack);
			}
			hero.tick();
			armour[i] = hero.getAttributeValue(Attributes.ARMOR);
			Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1 + 2 * i, 2, 3));
			float before = zombie.getHealth();
			com.vnap.village.life.HeroBehavior.strike(helper.getLevel(), hero, zombie);
			dealt[i] = before - zombie.getHealth();
			slowed[i] = zombie.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
		}
		Checks.assertTrue(helper, armour[0] > 0.0 && armour[1] > armour[0], "the armour counts on a villager and grows: " + armour[0] + " -> " + armour[1]);
		Checks.assertTrue(helper, dealt[0] > 1.0F && dealt[1] > dealt[0], "a better sword hits harder: " + dealt[0] + " -> " + dealt[1]);
		Checks.assertTrue(helper, !slowed[0] && slowed[1], "an Ice hero's blow slows, a Root hero's does not");
		helper.succeed();
	}
}
