package com.vnap.item;

import com.vnap.VillagerLife;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LifeItems {
	private static Item disc(String song) {
		return add("music_disc_" + song, Item::new, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)
				.jukeboxPlayable(ResourceKey.create(Registries.JUKEBOX_SONG, VillagerLife.id(song))));
	}

	private static final Map<ResourceLocation, Item> ITEMS = new LinkedHashMap<>();

	public static final ResourceKey<CreativeModeTab> CREATIVE_TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			VillagerLife.id("items"));
	public static final Item HANDBOOK = add("handbook", Item::new, single());
	public static final Item TOCSIN_CORE = add("tocsin_core", Item::new, new Item.Properties().stacksTo(16).fireResistant()
			.rarity(net.minecraft.world.item.Rarity.RARE));
	public static final Item BLESSING_STAFF = add("villager_blessing_staff", BlessingStaffItem::new, single());
	public static final Item PARISH_VESTMENT = add("parish_vestment", ParishVestmentItem::new, single());
	public static final Item WEDDING_RING = add("wedding_ring", Item::new, new Item.Properties().stacksTo(16)
			.rarity(net.minecraft.world.item.Rarity.UNCOMMON));
	public static final Item WOODEN_TOY = add("wooden_toy", Item::new, new Item.Properties().stacksTo(16));
	public static final Item BABY_BLANKET = add("baby_blanket", Item::new, new Item.Properties().stacksTo(16));
	public static final Item PET_TREAT = add("pet_treat", Item::new, new Item.Properties());
	/** A workshop's hand cart (entity.HandCart): a stall good the workers buy, not placed by players. */
	public static final Item HAND_CART = add("hand_cart", Item::new, single());
	public static final Item HOME_MEAL = add("home_meal", Item::new, new Item.Properties().stacksTo(16)
			//? if >=1.21.2 {
			/*.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build(),
					net.minecraft.world.item.component.Consumables.defaultFood().onConsume(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(
							new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100))).build()));
			*///?} else {
			.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationModifier(0.8F)
					.effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100), 1.0F)
					.build()));
			//?}
	public static final Item HOUSE_DEED = add("house_deed", HouseDeedItem::new, new Item.Properties().stacksTo(16));
	public static final Item TOWN_MAP = add("town_map", TownMapItem::new, new Item.Properties().stacksTo(16));
	public static final Item COPPER_COIN = add("copper_coin", Item::new, new Item.Properties());
	public static final Item SILVER_COIN = add("silver_coin", Item::new, new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON));
	public static final Item GOLD_COIN = add("gold_coin", Item::new, new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE));
	/** The Chronicle of the Spire: the notes a player read up the spires, to read again (spire/SpireNotes). */
	public static final Item SPIRE_JOURNAL = add("spire_journal", com.vnap.spire.SpireJournalItem::new, single());
	/** The Builders' Writ: read, it calls a spire up at once (spire/SpireWritItem); the wandering trader and the stall sell it. */
	public static final Item SPIRE_WRIT = add("spire_writ", com.vnap.spire.SpireWritItem::new,
			single().rarity(net.minecraft.world.item.Rarity.EPIC).fireResistant());
	/** A shard of the Core: what a spire's guards leave, what spire gear is honed with (spire/). */
	public static final Item CORE_SHARD = add("core_shard", Item::new, new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON));
	/** The town's records (jukebox_song/*.json): the villagers put them on at the feast and the tavern (Music). */
	public static final Item DISC_FEAST = disc("feast");
	public static final Item DISC_TAVERN = disc("tavern");
	public static final Item DISC_HYMN = disc("hymn");
	public static final Item DISC_EVENING = disc("evening");

	private LifeItems() {
	}

	public static void registerItems(RegisterEvent.RegisterHelper<Item> helper) {
		ITEMS.forEach((id, item) -> helper.register(id, item));
	}

	public static void registerTab(RegisterEvent.RegisterHelper<CreativeModeTab> helper) {
		helper.register(com.vnap.Mc.id(CREATIVE_TAB_KEY), CreativeModeTab.builder()
				.title(Component.translatable("itemGroup.villager_life.items"))
				.icon(() -> new ItemStack(HANDBOOK))
				.displayItems((parameters, output) -> {
					output.accept(HANDBOOK);
					output.accept(TOCSIN_CORE);
					output.accept(BLESSING_STAFF);
					output.accept(PARISH_VESTMENT);
					output.accept(WEDDING_RING);
					output.accept(WOODEN_TOY);
					output.accept(BABY_BLANKET);
					output.accept(PET_TREAT);
					output.accept(HAND_CART);
					output.accept(HOME_MEAL);
					output.accept(HOUSE_DEED);
					output.accept(TOWN_MAP);
					output.accept(COPPER_COIN);
					output.accept(SILVER_COIN);
					output.accept(GOLD_COIN);
					output.accept(CORE_SHARD);
					output.accept(SPIRE_JOURNAL);
					output.accept(SPIRE_WRIT);
					com.vnap.spire.SpireBlocks.items().values().forEach(output::accept);
					com.vnap.spire.gear.SpireGearItems.all().values().forEach(output::accept);
					com.vnap.spire.Draughts.all().values().forEach(output::accept);
					com.vnap.mega.MegaBlocks.items().values().forEach(output::accept);
					com.vnap.block.LifeBlocks.blockItems().values().forEach(output::accept);
				})
				.build());
	}

	private static Item.Properties single() {
		return new Item.Properties().stacksTo(1);
	}

	private static Item add(String path, java.util.function.Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceLocation id = VillagerLife.id(path);
		Item item = factory.apply(com.vnap.Mc.item(properties, id));
		ITEMS.put(id, item);
		return item;
	}
}
