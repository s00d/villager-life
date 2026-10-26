package com.vnap.spire.gear;

import com.vnap.Mc;
import com.vnap.VillagerLife;
import com.vnap.item.LifeItems;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
//? if <1.21.2 {
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
//?} else {
/*import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
*///?}
//? if <26.3
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
//? if <1.21.5
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The seventy pieces of spire gear (ten tiers × sword, axe, bow and four of armour) and the ten chapter seals. */
public final class SpireGearItems {
	private static final Map<ResourceLocation, Item> ITEMS = new LinkedHashMap<>();
	private static final Map<GearTier, Map<GearPiece, Item>> GEAR = new EnumMap<>(GearTier.class);
	private static final Map<GearTier, Item> SEALS = new EnumMap<>(GearTier.class);
	//? if <1.21.2 {
	private static final Map<GearTier, Holder<ArmorMaterial>> MATERIALS = new EnumMap<>(GearTier.class);
	//?} else {
	/*private static final Map<GearTier, ArmorMaterial> MATERIALS = new EnumMap<>(GearTier.class);
	// what mends the sword, the axe, the bow and the armour: shards of the Core
	private static final TagKey<Item> REPAIRS = TagKey.create(Registries.ITEM, VillagerLife.id("repairs_spire_gear"));
	*///?}

	static {
		for (GearTier tier : GearTier.values()) {
			Map<GearPiece, Item> pieces = new EnumMap<>(GearPiece.class);
			for (GearPiece piece : GearPiece.values()) pieces.put(piece, make(tier, piece));
			GEAR.put(tier, pieces);
		}
		for (GearTier tier : GearTier.values()) {
			SEALS.put(tier, add("spire_seal_" + tier.id(), new Item.Properties().stacksTo(16).fireResistant().rarity(rarity(tier)), Item::new));
		}
	}

	private SpireGearItems() {
	}

	public static Item get(GearTier tier, GearPiece piece) {
		return java.util.Objects.requireNonNull(java.util.Objects.requireNonNull(GEAR.get(tier)).get(piece));
	}

	/** A chapter's seal: what opens its tier at the Core Anvil (it is shown, not spent). */
	public static Item seal(GearTier tier) {
		return java.util.Objects.requireNonNull(SEALS.get(tier));
	}

	public static Map<ResourceLocation, Item> all() {
		return ITEMS;
	}

	public static void register(RegisterEvent.RegisterHelper<Item> helper) {
		ITEMS.forEach(helper::register);
	}

	private static Rarity rarity(GearTier tier) {
		return tier.step() < 3 ? Rarity.UNCOMMON : tier.step() < 7 ? Rarity.RARE : Rarity.EPIC;
	}

	private static Item make(GearTier tier, GearPiece piece) {
		Item.Properties properties = new Item.Properties().fireResistant().rarity(rarity(tier));
		String path = "spire_" + tier.id() + "_" + piece.id();
		if (piece.isArmour()) {
			return add(path, properties.durability(SpireGear.uses(tier, piece)), p -> new Armour(tier, piece, p));
		}
		return switch (piece) {
			case SWORD -> add(path, properties, p -> new Sword(tier, p));
			case AXE -> add(path, properties, p -> new Axe(tier, p));
			//? if >=1.21.2 {
			/*default -> add(path, properties.durability(SpireGear.uses(tier, piece)).enchantable(15).repairable(REPAIRS), p -> new Bow(tier, p));
			*///?} else
			default -> add(path, properties.durability(SpireGear.uses(tier, piece)), p -> new Bow(tier, p));
		};
	}

	private static Item add(String path, Item.Properties properties, java.util.function.Function<Item.Properties, Item> factory) {
		ResourceLocation id = VillagerLife.id(path);
		Item item = factory.apply(Mc.item(properties, id));
		ITEMS.put(id, item);
		return item;
	}


	//? if >=1.21.2 {
	/*private static ArmorType type(GearPiece piece) {
		return ArmorType.valueOf(piece.name());
	}

	private static ArmorMaterial material(GearTier tier) {
		return MATERIALS.computeIfAbsent(tier, t -> {
			Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
			for (GearPiece piece : GearPiece.values()) {
				if (piece.isArmour()) defense.put(type(piece), (int) Math.round(SpireGear.armour(t, piece)));
			}
			defense.put(ArmorType.BODY, 0);
			return new ArmorMaterial(25 + 3 * t.step(), defense, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, (float) SpireGear.toughness(t),
					(float) SpireGear.knockback(t), REPAIRS, ResourceKey.create(EquipmentAssets.ROOT_ID, VillagerLife.id("spire_" + t.id())));
		});
	}

	// the tool material of the sword and the axe: netherite's reach, the spire's wear, mended with shards of the Core
	private static ToolMaterial tool(GearTier tier, GearPiece piece) {
		return new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, SpireGear.uses(tier, piece), 9.0F, 0.0F, 15, REPAIRS);
	}
	*///?} else {
	private static ArmorItem.Type type(GearPiece piece) {
		return ArmorItem.Type.valueOf(piece.name());
	}

	private static Holder<ArmorMaterial> material(GearTier tier) {
		return MATERIALS.computeIfAbsent(tier, t -> {
			Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
			for (GearPiece piece : GearPiece.values()) {
				if (piece.isArmour()) defense.put(type(piece), (int) Math.round(SpireGear.armour(t, piece)));
			}
			defense.put(ArmorItem.Type.BODY, 0);
			return Holder.direct(new ArmorMaterial(defense, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(LifeItems.CORE_SHARD),
					List.of(new ArmorMaterial.Layer(VillagerLife.id("spire_" + t.id()))), (float) SpireGear.toughness(t), (float) SpireGear.knockback(t)));
		});
	}

	/** The tool tier of the sword and the axe: netherite's reach, the spire's wear, mended with shards of the Core. */
	private record ToolTier(int uses) implements Tier {
		@Override
		public int getUses() {
			return uses;
		}

		@Override
		public float getSpeed() {
			return 9.0F;
		}

		@Override
		public float getAttackDamageBonus() {
			return 0.0F;
		}

		@Override
		public TagKey<Block> getIncorrectBlocksForDrops() {
			return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
		}

		@Override
		public int getEnchantmentValue() {
			return 15;
		}

		@Override
		public Ingredient getRepairIngredient() {
			return Ingredient.of(LifeItems.CORE_SHARD);
		}
	}
	//?}

	// the same few overrides on each kind: the stats of the stack's +n, its name, the tooltip, enchantments kept to the list

	private static void tidy(ItemStack stack, Level level, Entity entity) {
		if (!level.isClientSide() && entity.tickCount % 20 == 0) SpireGear.sanitize(stack);
		// a piece honed to its last look sheds motes of its tier's light while it is held
		if (level instanceof net.minecraft.server.level.ServerLevel server && entity.tickCount % 6 == 0 && entity instanceof net.minecraft.world.entity.LivingEntity living
				&& living.getMainHandItem() == stack && stack.getItem() instanceof GearItem gear && SpireGear.stage(stack) >= 3) {
			server.sendParticles(Mc.dust(SpireGear.glow(gear.tier()), 0.8F), entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
					2, 0.35, 0.3, 0.35, 0.0);
		}
	}

	//? if >=1.21.5 {
	/*public static final class Sword extends Item implements GearItem {
	*///?} else
	public static final class Sword extends SwordItem implements GearItem {
		private final GearTier tier;

		Sword(GearTier tier, Item.Properties properties) {
			//? if >=1.21.5 {
			/*super(properties.sword(tool(tier, GearPiece.SWORD), 0.0F, 0.0F));
			*///?} else if >=1.21.2 {
			/*super(tool(tier, GearPiece.SWORD), 0.0F, 0.0F, properties);
			*///?} else
			super(new ToolTier(SpireGear.uses(tier, GearPiece.SWORD)), properties);
			this.tier = tier;
		}

		@Override
		public GearTier tier() {
			return tier;
		}

		@Override
		public GearPiece piece() {
			return GearPiece.SWORD;
		}

		@Override
		public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
			return SpireGear.attributes(stack);
		}

		@Override
		public Component getName(ItemStack stack) {
			return SpireGear.name(super.getName(stack), stack);
		}

		//? if >=1.21.5 {
		/*@Override
		public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, Entity entity,
				net.minecraft.world.entity.@org.jspecify.annotations.Nullable EquipmentSlot slot) {
			super.inventoryTick(stack, level, entity, slot);
			tidy(stack, level, entity);
		}
		*///?} else {
		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
			super.appendHoverText(stack, context, lines, flag);
			SpireGear.tooltip(stack, lines);
		}

		@Override
		public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
			super.inventoryTick(stack, level, entity, slot, selected);
			tidy(stack, level, entity);
		}
		//?}

		@Override
		public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
			//? if forge {
			/*return SpireGear.cap(piece(), enchantment) > 0 && GearItem.super.supportsEnchantment(stack, enchantment);
			*///?} else
			return SpireGear.cap(piece(), enchantment) > 0 && super.supportsEnchantment(stack, enchantment);
		}
	}

	//? if >=26.3 {
	/*public static final class Axe extends Item implements GearItem {
	*///?} else
	public static final class Axe extends AxeItem implements GearItem {
		private final GearTier tier;

		Axe(GearTier tier, Item.Properties properties) {
			//? if >=26.3 {
			/*super(properties.axe(tool(tier, GearPiece.AXE), 0.0F, 0.0F));
			*///?} else if >=1.21.2 {
			/*super(tool(tier, GearPiece.AXE), 0.0F, 0.0F, properties);
			*///?} else
			super(new ToolTier(SpireGear.uses(tier, GearPiece.AXE)), properties);
			this.tier = tier;
		}

		@Override
		public GearTier tier() {
			return tier;
		}

		@Override
		public GearPiece piece() {
			return GearPiece.AXE;
		}

		@Override
		public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
			return SpireGear.attributes(stack);
		}

		@Override
		public Component getName(ItemStack stack) {
			return SpireGear.name(super.getName(stack), stack);
		}

		//? if >=1.21.5 {
		/*@Override
		public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, Entity entity,
				net.minecraft.world.entity.@org.jspecify.annotations.Nullable EquipmentSlot slot) {
			super.inventoryTick(stack, level, entity, slot);
			tidy(stack, level, entity);
		}
		*///?} else {
		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
			super.appendHoverText(stack, context, lines, flag);
			SpireGear.tooltip(stack, lines);
		}

		@Override
		public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
			super.inventoryTick(stack, level, entity, slot, selected);
			tidy(stack, level, entity);
		}
		//?}

		@Override
		public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
			//? if forge {
			/*return SpireGear.cap(piece(), enchantment) > 0 && GearItem.super.supportsEnchantment(stack, enchantment);
			*///?} else
			return SpireGear.cap(piece(), enchantment) > 0 && super.supportsEnchantment(stack, enchantment);
		}
	}

	public static final class Bow extends BowItem implements GearItem {
		private final GearTier tier;

		Bow(GearTier tier, Item.Properties properties) {
			super(properties);
			this.tier = tier;
		}

		@Override
		public GearTier tier() {
			return tier;
		}

		@Override
		public GearPiece piece() {
			return GearPiece.BOW;
		}

		// Forge hands over the arrow alone: the bow that shot it is the arrow's weapon
		//? if forge {
		/*@Override
		public AbstractArrow customArrow(AbstractArrow arrow) {
			AbstractArrow out = super.customArrow(arrow);
			ItemStack weapon = java.util.Objects.requireNonNullElse(arrow.getWeaponItem(), ItemStack.EMPTY);
		*///?} else {
		@Override
		public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectile, ItemStack weapon) {
			AbstractArrow out = super.customArrow(arrow, projectile, weapon);
		//?}
			// ponytail: the arrow's base damage has no getter from 1.21.5: there a vanilla arrow's 2 (as SpireGear.dps counts it)
			//? if >=1.21.5 {
			/*out.setBaseDamage(2.0 * SpireGear.bowPower(weapon));
			*///?} else
			out.setBaseDamage(out.getBaseDamage() * SpireGear.bowPower(weapon));
			return out;
		}

		//? if <1.21.2 {
		@Override
		public int getEnchantmentValue() {
			return 15;
		}

		@Override
		public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
			return repair.is(LifeItems.CORE_SHARD);
		}
		//?}

		@Override
		public Component getName(ItemStack stack) {
			return SpireGear.name(super.getName(stack), stack);
		}

		//? if >=1.21.5 {
		/*@Override
		public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, Entity entity,
				net.minecraft.world.entity.@org.jspecify.annotations.Nullable EquipmentSlot slot) {
			super.inventoryTick(stack, level, entity, slot);
			tidy(stack, level, entity);
		}
		*///?} else {
		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
			super.appendHoverText(stack, context, lines, flag);
			SpireGear.tooltip(stack, lines);
		}

		@Override
		public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
			super.inventoryTick(stack, level, entity, slot, selected);
			tidy(stack, level, entity);
		}
		//?}

		@Override
		public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
			//? if forge {
			/*return SpireGear.cap(piece(), enchantment) > 0 && GearItem.super.supportsEnchantment(stack, enchantment);
			*///?} else
			return SpireGear.cap(piece(), enchantment) > 0 && super.supportsEnchantment(stack, enchantment);
		}
	}

	//? if >=1.21.5 {
	/*public static final class Armour extends Item implements GearItem {
	*///?} else
	public static final class Armour extends net.minecraft.world.item.ArmorItem implements GearItem {
		private final GearTier tier;
		private final GearPiece piece;

		Armour(GearTier tier, GearPiece piece, Item.Properties properties) {
			//? if >=1.21.5 {
			/*super(properties.humanoidArmor(material(tier), type(piece)));
			*///?} else
			super(material(tier), type(piece), properties);
			this.tier = tier;
			this.piece = piece;
		}

		@Override
		public GearTier tier() {
			return tier;
		}

		@Override
		public GearPiece piece() {
			return piece;
		}

		@Override
		public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
			return SpireGear.attributes(stack);
		}

		@Override
		public Component getName(ItemStack stack) {
			return SpireGear.name(super.getName(stack), stack);
		}

		//? if >=1.21.5 {
		/*@Override
		public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, Entity entity,
				net.minecraft.world.entity.@org.jspecify.annotations.Nullable EquipmentSlot slot) {
			super.inventoryTick(stack, level, entity, slot);
			tidy(stack, level, entity);
		}
		*///?} else {
		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
			super.appendHoverText(stack, context, lines, flag);
			SpireGear.tooltip(stack, lines);
		}

		@Override
		public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
			super.inventoryTick(stack, level, entity, slot, selected);
			tidy(stack, level, entity);
		}
		//?}

		@Override
		public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
			//? if forge {
			/*return SpireGear.cap(piece(), enchantment) > 0 && GearItem.super.supportsEnchantment(stack, enchantment);
			*///?} else
			return SpireGear.cap(piece(), enchantment) > 0 && super.supportsEnchantment(stack, enchantment);
		}

		// the flat layers by the stack's hone (from 1.21.2 its equipment asset, SpireGear.look); the plates in the round
		// over them are client/spire/SpireArmorLayer's
		//? if <1.21.2 {
		@Override
		public net.minecraft.resources.ResourceLocation getArmorTexture(ItemStack stack, Entity entity, net.minecraft.world.entity.EquipmentSlot slot,
				net.minecraft.world.item.ArmorMaterial.Layer layer, boolean inner) {
			return VillagerLife.id("textures/models/armor/" + SpireGear.look(tier, SpireGear.stage(stack)) + "_layer_" + (inner ? 2 : 1) + ".png");
		}
		//?}
	}
}
