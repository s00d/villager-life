package com.vnap.spire.gear;

import com.vnap.Nbt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The numbers of spire gear (docs/tower/DESIGN.md §7): a tier's base, honing "+n" (kept in the stack's custom data)
 * that adds {@code SHARE · base · (1 − e^(−K·n))} — +10 is about 55% of the most it can ever add, +50 about 98% — at
 * a cost growing as {@code (1 + n)^1.6}, the enchantments allowed on it and the worst damage a second it can deal.
 */
public final class SpireGear {
	public static final double SHARE = 0.4;
	public static final double K = 0.08;
	public static final double CURVE = 1.6;
	/** Past +50 the gain is under 2% of the cap: the name gets a star instead. */
	public static final int STARS_FROM = 50;
	/**
	 * The most damage a second any spire weapon can deal with any honing and any enchantment it may carry, counting
	 * every critical hit, the Clockwork haste, Spore's poison and Fire Aspect's burning (potions of the player aside).
	 */
	public static final double DPS_CAP = 75.0;
	/** Haste II from a Clockwork combo: +20% attack speed. */
	public static final double COMBO_HASTE = 1.2;
	public static final double CRIT = 1.5;
	/** Spore's poison (1 a 25 ticks) and Fire Aspect's burning (1 a second) at most. */
	private static final double SPORE_DPS = 0.8;
	private static final double FIRE_DPS = 1.0;
	private static final String KEY = "vnap_hone";
	private static final ResourceLocation BASE_DAMAGE = ResourceLocation.withDefaultNamespace("base_attack_damage");
	private static final ResourceLocation BASE_SPEED = ResourceLocation.withDefaultNamespace("base_attack_speed");

	private static final Map<ResourceKey<Enchantment>, Integer> WORN = Map.of(Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1,
			Enchantments.VANISHING_CURSE, 1);
	private static final Map<GearPiece, Map<ResourceKey<Enchantment>, Integer>> ALLOWED = new EnumMap<>(GearPiece.class);

	static {
		Map<ResourceKey<Enchantment>, Integer> armour = Map.of(Enchantments.PROTECTION, 3, Enchantments.FIRE_PROTECTION, 3,
				Enchantments.BLAST_PROTECTION, 3, Enchantments.PROJECTILE_PROTECTION, 3, Enchantments.THORNS, 1, Enchantments.BINDING_CURSE, 1);
		ALLOWED.put(GearPiece.SWORD, Map.of(Enchantments.SHARPNESS, 3, Enchantments.SWEEPING_EDGE, 3, Enchantments.LOOTING, 3,
				Enchantments.KNOCKBACK, 2, Enchantments.FIRE_ASPECT, 1));
		ALLOWED.put(GearPiece.AXE, Map.of(Enchantments.SHARPNESS, 3, Enchantments.EFFICIENCY, 5, Enchantments.FORTUNE, 3,
				Enchantments.SILK_TOUCH, 1));
		ALLOWED.put(GearPiece.BOW, Map.of(Enchantments.POWER, 3, Enchantments.PUNCH, 1, Enchantments.FLAME, 1, Enchantments.INFINITY, 1));
		ALLOWED.put(GearPiece.HELMET, with(armour, Map.of(Enchantments.RESPIRATION, 3, Enchantments.AQUA_AFFINITY, 1)));
		ALLOWED.put(GearPiece.CHESTPLATE, armour);
		ALLOWED.put(GearPiece.LEGGINGS, with(armour, Map.of(Enchantments.SWIFT_SNEAK, 3)));
		ALLOWED.put(GearPiece.BOOTS, with(armour, Map.of(Enchantments.FEATHER_FALLING, 4, Enchantments.DEPTH_STRIDER, 3,
				Enchantments.SOUL_SPEED, 3)));
	}

	private SpireGear() {
	}

	private static Map<ResourceKey<Enchantment>, Integer> with(Map<ResourceKey<Enchantment>, Integer> a, Map<ResourceKey<Enchantment>, Integer> b) {
		Map<ResourceKey<Enchantment>, Integer> out = new java.util.HashMap<>(a);
		out.putAll(b);
		return Map.copyOf(out);
	}

	// ---- the base of a tier

	/** A melee hit's damage, the fist's 1 included: the sword 7 at Root to 16 at Summit, the axe 2 more. */
	public static double damage(GearTier tier, GearPiece piece) {
		return (piece == GearPiece.AXE ? 9.0 : 7.0) + tier.step();
	}

	/** Hits a second: the sword's 1.6, the axe's 1.0 (as vanilla's). */
	public static double speed(GearPiece piece) {
		return piece == GearPiece.AXE ? 1.0 : 1.6;
	}

	/** What a bow's arrows hit for, times a plain bow's: ×1.05 at Root to ×1.5 at Summit. */
	public static double power(GearTier tier) {
		return 1.05 + 0.05 * tier.step();
	}

	/** A piece's armour: a whole set 17 at Root (between iron's 15 and diamond's 20) to 21.5 at Summit (netherite 20). */
	public static double armour(GearTier tier, GearPiece piece) {
		return (17.0 + 0.5 * tier.step()) * piece.share;
	}

	/** A piece's toughness: 0.5 at Root to 3.2 at Summit (netherite 3). */
	public static double toughness(GearTier tier) {
		return 0.5 + 0.3 * tier.step();
	}

	/** A piece's knockback resistance: 0.01 at Root to netherite's 0.1 at Summit; honing does not raise it. */
	public static double knockback(GearTier tier) {
		return 0.01 * tier.number();
	}

	public static int uses(GearTier tier, GearPiece piece) {
		if (piece.isArmour()) return piece.durability * (25 + 3 * tier.step());
		return piece == GearPiece.BOW ? 500 + 100 * tier.step() : 1000 + 200 * tier.step();
	}

	// ---- honing

	public static int honed(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : Math.max(0, Nbt.getInt(data.copyTag(), KEY));
	}

	public static void setHoned(ItemStack stack, int n) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			if (n > 0) tag.putInt(KEY, n);
			else tag.remove(KEY);
		});
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
	}

	/** The part of the most honing can ever add that +n gives: 0 at +0, 0.55 at +10, 0.98 at +50, never 1. */
	public static double gain(long n) {
		return n <= 0 ? 0.0 : 1.0 - Math.exp(-K * n);
	}

	/** What +n adds to a base. */
	public static double bonus(double base, long n) {
		return SHARE * base * gain(n);
	}

	/** Shards of the Core to hone a piece from +n to +n+1. */
	public static int honeShards(GearTier tier, int n) {
		return cost(0.25 * tier.number(), n);
	}

	/** Copper to hone a piece from +n to +n+1. */
	public static int honeCoins(GearTier tier, int n) {
		return cost(2.0 * tier.number(), n);
	}

	private static int cost(double scale, int n) {
		return (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(scale * Math.pow(1.0 + Math.max(0, n), CURVE)));
	}

	/** Shards to make a piece of {@code tier} out of the tier before (or out of a vanilla piece for Root). */
	public static int craftShards(GearTier tier) {
		return 6 * tier.number();
	}

	public static int craftCoins(GearTier tier) {
		return 64 * tier.number();
	}

	// ---- what a stack is worth

	public static ItemAttributeModifiers attributes(ItemStack stack) {
		return stack.getItem() instanceof GearItem gear ? attributes(gear.tier(), gear.piece(), honed(stack)) : ItemAttributeModifiers.EMPTY;
	}

	public static ItemAttributeModifiers attributes(GearTier tier, GearPiece piece, int n) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		EquipmentSlot slot = piece.armour();
		if (piece.melee()) {
			double damage = damage(tier, piece);
			builder.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_DAMAGE, damage + bonus(damage, n) - 1.0,
					AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
			builder.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_SPEED, speed(piece) - 4.0, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND);
		} else if (slot != null) {
			ResourceLocation id = ResourceLocation.withDefaultNamespace("armor." + piece.id());
			EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
			double armour = armour(tier, piece);
			double toughness = toughness(tier);
			builder.add(Attributes.ARMOR, new AttributeModifier(id, armour + bonus(armour, n), AttributeModifier.Operation.ADD_VALUE), group);
			builder.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, toughness + bonus(toughness, n), AttributeModifier.Operation.ADD_VALUE), group);
			builder.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, knockback(tier), AttributeModifier.Operation.ADD_VALUE), group);
		}
		return builder.build();
	}

	/** A bow's arrow damage, times a plain bow's. */
	public static double bowPower(ItemStack stack) {
		if (!(stack.getItem() instanceof GearItem gear) || gear.piece() != GearPiece.BOW) return 1.0;
		double power = power(gear.tier());
		return power + bonus(power, honed(stack));
	}

	/** The one number the anvil shows a piece by: hit damage, ×arrow damage or armour. */
	public static double headline(GearTier tier, GearPiece piece, int n) {
		double base = piece.melee() ? damage(tier, piece) : piece == GearPiece.BOW ? power(tier) : armour(tier, piece);
		return base + bonus(base, n);
	}

	/**
	 * The most damage a second the weapon can deal to {@code target} from {@code attacker}: every hit at full charge and
	 * critical, with the Clockwork haste, Spore's poison and Fire Aspect; a bow's every shot drawn full (a second) and
	 * critical. 0 for armour.
	 */
	public static double dps(ServerLevel level, ItemStack stack, LivingEntity attacker, LivingEntity target) {
		if (!(stack.getItem() instanceof GearItem gear) || gear.piece().isArmour()) return 0.0;
		double fire = level(level, stack, Enchantments.FIRE_ASPECT) > 0 || level(level, stack, Enchantments.FLAME) > 0 ? FIRE_DPS : 0.0;
		if (gear.piece() == GearPiece.BOW) {
			Arrow arrow = new Arrow(EntityType.ARROW, level);
			DamageSource source = level.damageSources().arrow(arrow, attacker);
			double base = EnchantmentHelper.modifyDamage(level, stack, target, source, (float) (2.0 * bowPower(stack)));
			// AbstractArrow.onHitEntity: ceil(speed × damage), a critical arrow up to half as much and 1 more
			int hit = (int) Math.ceil(3.0 * base);
			return hit + hit / 2 + 1 + SPORE_DPS + fire;
		}
		double[] sums = {1.0, 4.0};
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) return;
			if (attribute.equals(Attributes.ATTACK_DAMAGE)) sums[0] += modifier.amount();
			if (attribute.equals(Attributes.ATTACK_SPEED)) sums[1] += modifier.amount();
		});
		DamageSource source = level.damageSources().mobAttack(attacker);
		double hit = EnchantmentHelper.modifyDamage(level, stack, target, source, (float) sums[0]);
		return hit * CRIT * sums[1] * COMBO_HASTE + SPORE_DPS + fire;
	}

	private static int level(ServerLevel level, ItemStack stack, ResourceKey<Enchantment> key) {
		//? if forge || fabric {
		/*return level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(key)
				.map(holder -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
		*///?} else {
		return level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(key)
				.map(stack::getEnchantmentLevel).orElse(0);
		//?}
	}

	// ---- enchantments

	/** The most of {@code enchantment} a piece may carry; 0 when not at all. */
	public static int cap(GearPiece piece, Holder<Enchantment> enchantment) {
		ResourceKey<Enchantment> key = enchantment.unwrapKey().orElse(null);
		if (key == null) return 0;
		Integer own = ALLOWED.getOrDefault(piece, Map.of()).get(key);
		if (own != null) return own;
		return WORN.getOrDefault(key, 0);
	}

	/** Drops what a piece may not carry and lowers what is above its cap; true when anything changed. */
	public static boolean sanitize(ItemStack stack) {
		if (!(stack.getItem() instanceof GearItem gear)) return false;
		boolean[] changed = {look(stack, gear)};
		EnchantmentHelper.updateEnchantments(stack, enchantments -> {
			for (Holder<Enchantment> enchantment : List.copyOf(enchantments.keySet())) {
				int cap = cap(gear.piece(), enchantment);
				if (enchantments.getLevel(enchantment) > cap) {
					enchantments.set(enchantment, cap);
					changed[0] = true;
				}
			}
		});
		return changed[0];
	}

	// ---- how it looks

	/** The hone at which a piece takes on its next look: a gilded edge, the Core's rune and gem, a halo of light. */
	private static final int[] STAGES = {5, 15, 30};

	public static int stage(int honed) {
		int stage = 0;
		for (int at : STAGES) if (honed >= at) stage++;
		return stage;
	}

	public static int stage(ItemStack stack) {
		return stage(honed(stack));
	}

	/** Each tier's light (tools/art/paint_spire_gear.py: its gem): motes off a piece at its last look, a hero's glow. */
	private static final int[] GLOW = {0xA8D88F, 0xE8D27A, 0x9FE0D0, 0xF6C8D9, 0x8FD6C4, 0xA8D8F8, 0xC8E89A, 0xFFE08A, 0xFFF0A8, 0xFFF4C8};

	public static int glow(GearTier tier) {
		return GLOW[tier.step()];
	}

	/** The look's name of a piece's tier at a stage: spire_root, spire_root_s1… (textures, models, armour). */
	public static String look(GearTier tier, int stage) {
		return "spire_" + tier.id() + (stage > 0 ? "_s" + stage : "");
	}

	/**
	 * Keeps a piece's look to its hone: its model by custom_model_data (one vanilla way on every version), from 1.21.2
	 * the equipment asset its armour is drawn with; true when it changed.
	 */
	@SuppressWarnings("UnusedVariable") // the gear names its equipment asset from 1.21.2
	private static boolean look(ItemStack stack, GearItem gear) {
		int stage = stage(stack);
		boolean changed = com.vnap.Mc.customModelData(stack) != stage;
		if (changed) com.vnap.Mc.setCustomModelData(stack, stage);
		//? if >=1.21.2 {
		/*EquipmentSlot slot = gear.piece().armour();
		if (slot != null) {
			ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> asset = ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID,
					com.vnap.VillagerLife.id(look(gear.tier(), stage)));
			net.minecraft.world.item.equipment.Equippable now = stack.get(DataComponents.EQUIPPABLE);
			if (now == null || !now.assetId().equals(java.util.Optional.of(asset))) {
				stack.set(DataComponents.EQUIPPABLE, net.minecraft.world.item.equipment.Equippable.builder(slot)
						.setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE).setAsset(asset).build());
				changed = true;
			}
		}
		*///?}
		return changed;
	}

	// ---- how it reads

	/** "Root Sword +12", and past +50 stars instead of more numbers' worth. */
	public static Component name(Component base, ItemStack stack) {
		int n = honed(stack);
		if (n <= 0) return base;
		MutableComponent out = base.copy().append(" +" + n);
		if (n > STARS_FROM) out.append(" " + "★".repeat(Math.min(5, 1 + (n - STARS_FROM - 1) / 25)));
		return out;
	}

	public static void tooltip(ItemStack stack, List<Component> lines) {
		if (!(stack.getItem() instanceof GearItem gear)) return;
		GearTier tier = gear.tier();
		lines.add(Component.translatable("spire_gear.villager_life.tier", tier.number(), Component.translatable(tierKey(tier)))
				.withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable("spire_gear.villager_life.property." + tier.id()).withStyle(ChatFormatting.GRAY));
		int n = honed(stack);
		if (n > 0) {
			lines.add(Component.translatable("spire_gear.villager_life.honed", n, percent(gain(n))).withStyle(ChatFormatting.AQUA));
		}
		if (gear.piece() == GearPiece.BOW) {
			lines.add(Component.translatable("spire_gear.villager_life.bow_power", number(bowPower(stack))).withStyle(ChatFormatting.DARK_GREEN));
		}
	}

	public static String tierKey(GearTier tier) {
		return "spire_gear.villager_life.tier." + tier.id();
	}

	public static String percent(double part) {
		return String.format(Locale.ROOT, "%.0f%%", part * 100.0);
	}

	public static String number(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	public static @Nullable GearItem gear(ItemStack stack) {
		return stack.getItem() instanceof GearItem gear ? gear : null;
	}
}
