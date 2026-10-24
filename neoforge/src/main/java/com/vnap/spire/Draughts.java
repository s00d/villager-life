package com.vnap.spire;

import com.vnap.Mc;
import com.vnap.VillagerLife;
import com.vnap.spire.gear.GearTier;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The section draughts (docs/tower/TODO.md): every floor's chest gives a reagent of its chapter the first time; at a
 * brewing stand, as any potion is brewed, an awkward potion and a reagent of a chapter make the draught of the chapter
 * after it (one reagent, three bottles). Drunk, it wards its drinker for a
 * while, but only on the floors of its own chapter: nothing there knocks them back (the shooters' bolts least of all)
 * and the guards' blows leave no weak affliction. Elsewhere it does nothing; without it all is as it was.
 */
public final class Draughts {
	/** How long a draught wards: about a chapter's ten floors. */
	public static final int WARD_TICKS = 20 * 60 * 12;
	/** A chapter's colour: its reagent, its draught's liquid, its ward's swirl. */
	private static final int[] COLOURS = {0x7A9A3A, 0x4A4A9A, 0xD0962E, 0xE87AA8, 0x2A9AB0, 0x9ADCF4, 0xA070C0, 0xE8B020, 0x7A6AF0, 0xF0E6C8};
	private static final Map<ResourceLocation, Item> ITEMS = new LinkedHashMap<>();
	private static final Map<GearTier, Item> REAGENT = new EnumMap<>(GearTier.class);
	private static final Map<GearTier, Item> DRAUGHT = new EnumMap<>(GearTier.class);
	private static final Map<GearTier, Ward> WARD = new EnumMap<>(GearTier.class);

	static {
		for (GearTier tier : GearTier.values()) {
			WARD.put(tier, new Ward(tier, COLOURS[tier.step()]));
			Rarity rarity = tier.step() < 3 ? Rarity.UNCOMMON : tier.step() < 7 ? Rarity.RARE : Rarity.EPIC;
			REAGENT.put(tier, add("spire_reagent_" + tier.id(), new Item.Properties().rarity(rarity), properties -> new Reagent(properties, tier)));
			DRAUGHT.put(tier, add("spire_draught_" + tier.id(), drink().rarity(rarity), properties -> new Draught(properties, tier)));
		}
	}

	private Draughts() {
	}

	public static Item reagent(GearTier tier) {
		return java.util.Objects.requireNonNull(REAGENT.get(tier));
	}

	public static Item draught(GearTier tier) {
		return java.util.Objects.requireNonNull(DRAUGHT.get(tier));
	}

	public static Ward ward(GearTier tier) {
		return java.util.Objects.requireNonNull(WARD.get(tier));
	}

	/** The chapter whose reagents make a chapter's draught: the one before it (Root's from the Summit's). */
	public static GearTier brewedFrom(GearTier tier) {
		return GearTier.ofChapter(tier.step() - 1);
	}

	public static Map<ResourceLocation, Item> all() {
		return ITEMS;
	}

	public static void registerItems(RegisterEvent.RegisterHelper<Item> helper) {
		ITEMS.forEach(helper::register);
	}

	public static void registerEffects(RegisterEvent.RegisterHelper<MobEffect> helper) {
		WARD.forEach((tier, ward) -> helper.register(VillagerLife.id("spire_ward_" + tier.id()), ward));
	}

	/** Subscribed while the mod is built: a class of its own, so the items are not made before their registry opens. */
	public static final class Events {
		private Events() {
		}

		public static void listen() {
			NeoForge.EVENT_BUS.addListener(Draughts::onKnockBack);
			// brewing is recipes from 26.3: generateBrewing in the build writes them
			//? if <26.3 {
			NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent event) -> {
				net.minecraft.world.item.crafting.Ingredient awkward = net.neoforged.neoforge.common.crafting.DataComponentIngredient.of(false,
						net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.AWKWARD));
				for (GearTier tier : GearTier.values()) {
					event.getBuilder().addRecipe(awkward, net.minecraft.world.item.crafting.Ingredient.of(reagent(brewedFrom(tier))), new ItemStack(draught(tier)));
				}
			});
			//?}
			NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) -> {
				if (event.getEffectInstance().getEffect().value() instanceof Ward && event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
					com.vnap.progress.VillageAdvancements.award(player, "spire/draught");
				}
			});
		}
	}

	/** The chapter of the floor slot the entity stands in, or null off the floors. */
	public static @Nullable GearTier chapterAt(LivingEntity entity) {
		SpireSlots.Slot slot = SpireSlots.at(entity.blockPosition());
		if (slot == null || !slot.level.equals(Mc.levelOf(entity))) return null;
		return GearTier.ofFloor(slot.key.floor());
	}

	/** Whether the entity is on a floor of a chapter whose ward it has drunk. */
	public static boolean warded(LivingEntity entity) {
		GearTier chapter = chapterAt(entity);
		return chapter != null && entity.hasEffect(holder(ward(chapter)));
	}

	public static Holder<MobEffect> holder(Ward ward) {
		return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ward);
	}

	private static void onKnockBack(LivingKnockBackEvent event) {
		LivingEntity entity = event.getEntity();
		if (!warded(entity)) return;
		event.setCanceled(true);
		if (Mc.levelOf(entity) instanceof ServerLevel level) {
			// the blow breaks on the ward: a ring of the chapter's motes where it would have pushed
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, entity.getX(), entity.getY() + 1.0, entity.getZ(), 8, 0.35, 0.4, 0.35, 0.1);
		}
	}

	// the ward is put on in Draught.finishUsingItem: a food's effect holds its effect from when the item is made, before
	// the effects are registered (a holder that is not the registry's own, and hasEffect never finds it)
	private static Item.Properties drink() {
		//? if >=1.21.2 {
		/*return new Item.Properties().stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
				.food(new net.minecraft.world.food.FoodProperties.Builder().alwaysEdible().build(),
						net.minecraft.world.item.component.Consumables.defaultDrink().build());
		*///?} else
		return new Item.Properties().stacksTo(16).food(new net.minecraft.world.food.FoodProperties.Builder().alwaysEdible().build());
	}

	private static Item add(String path, Item.Properties properties, java.util.function.Function<Item.Properties, Item> factory) {
		ResourceLocation id = VillagerLife.id(path);
		Item item = factory.apply(Mc.item(properties, id));
		ITEMS.put(id, item);
		return item;
	}

	/** A chapter's floors and their theme's name, for the tooltips. */
	private static net.minecraft.network.chat.Component floors(GearTier tier) {
		int first = tier.step() * 10 + 1;
		return net.minecraft.network.chat.Component.translatable("spire.villager_life.draught.floors", first, first + 9,
				net.minecraft.network.chat.Component.translatable("spire.villager_life.theme." + Genome.THEMES.get(tier.step())));
	}

	private static final class Reagent extends Item implements com.vnap.item.Tipped {
		private final GearTier tier;

		Reagent(Properties properties, GearTier tier) {
			super(properties);
			this.tier = tier;
		}

		@Override
		public java.util.List<net.minecraft.network.chat.Component> tips() {
			GearTier next = GearTier.ofChapter(tier.step() + 1);
			return java.util.List.of(net.minecraft.network.chat.Component.translatable("spire.villager_life.reagent.found", floors(tier))
					.withStyle(net.minecraft.ChatFormatting.GRAY),
					net.minecraft.network.chat.Component.translatable("spire.villager_life.reagent.brews",
					new ItemStack(draught(next)).getHoverName()).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
		}

	}

	/** A draught: drunk, not eaten. */
	private static final class Draught extends Item implements com.vnap.item.Tipped {
		private final GearTier tier;

		Draught(Properties properties, GearTier tier) {
			super(properties);
			this.tier = tier;
		}

		@Override
		public java.util.List<net.minecraft.network.chat.Component> tips() {
			return java.util.List.of(
					net.minecraft.network.chat.Component.translatable("spire.villager_life.draught.where", floors(tier)).withStyle(net.minecraft.ChatFormatting.GRAY),
					net.minecraft.network.chat.Component.translatable("spire.villager_life.draught.what").withStyle(net.minecraft.ChatFormatting.BLUE),
					net.minecraft.network.chat.Component.translatable("spire.villager_life.draught.long", WARD_TICKS / 1200).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
		}


		@Override
		public UseAnim getUseAnimation(ItemStack stack) {
			return UseAnim.DRINK;
		}
		@Override
		public ItemStack finishUsingItem(ItemStack stack, net.minecraft.world.level.Level level, LivingEntity entity) {
			if (!level.isClientSide()) entity.addEffect(new MobEffectInstance(holder(ward(tier)), WARD_TICKS));
			ItemStack left = super.finishUsingItem(stack, level, entity);
			//? if <1.21.2 {
			// the food's usingConvertsTo here hands back the bottle for the whole stack (a stack of 16 went at one sip): as a honey bottle does
			if (entity instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().instabuild) return left;
			if (left.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
			if (entity instanceof net.minecraft.world.entity.player.Player player && !player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
				player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
			}
			//?}
			return left;
		}
	}

	/** A chapter's ward: what its draught leaves on the drinker (it acts only on that chapter's floors). */
	public static final class Ward extends MobEffect {
		public final GearTier chapter;

		Ward(GearTier chapter, int colour) {
			super(MobEffectCategory.BENEFICIAL, colour);
			this.chapter = chapter;
		}
	}
}
