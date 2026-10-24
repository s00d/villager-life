package com.vnap.spire;

import com.vnap.Mc;
import com.vnap.VillagerLife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** The spire's own blocks: none can be broken or moved (the spire dimension and the spire's box are guarded anyway). */
public final class SpireBlocks {
	private static final Map<ResourceLocation, Block> BLOCKS = new LinkedHashMap<>();
	private static final Map<ResourceLocation, Item> ITEMS = new LinkedHashMap<>();

	public static final SpirePortalBlock PORTAL = add("spire_portal", SpirePortalBlock::new, unbreakable(MapColor.COLOR_LIGHT_BLUE, SoundType.AMETHYST)
			.noCollission().noOcclusion().lightLevel(state -> state.getValue(SpirePortalBlock.LIT) ? 12 : 3), false);
	public static final SpireNoteBlock NOTE = add("spire_note", SpireNoteBlock::new, unbreakable(MapColor.SAND, SoundType.STONE)
			.noOcclusion().lightLevel(state -> 10), false);
	public static final SpireChestBlock CHEST = add("spire_chest", SpireChestBlock::new, unbreakable(MapColor.GOLD, SoundType.WOOD)
			.noOcclusion().lightLevel(state -> 7), false);

	/** A shard of the Core hanging in the air over a spire's break: it glows and sheds sparks. */
	public static final SpireShardBlock SHARD = add("spire_shard", SpireShardBlock::new, unbreakable(MapColor.COLOR_PURPLE, SoundType.AMETHYST)
			.noOcclusion().lightLevel(state -> 13), false);

	/** The rift a spire not yet whole is cut off by, and what the Builders left inside it: lights, braziers, runes, tapestries. */
	public static final SpireRiftBlock RIFT = add("spire_rift", SpireRiftBlock::new, unbreakable(MapColor.COLOR_BLACK, SoundType.AMETHYST)
			.noCollission().noOcclusion().lightLevel(state -> 10), false);
	/** Mined and sold too: the second age hangs them by the town's doors (village/town/Dressing); the spire's own are kept by its guard. */
	public static final SpireFixtureBlock SCONCE = add("spire_sconce", properties -> new SpireFixtureBlock(properties, SpireFixtureBlock.Kind.SCONCE),
			BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(1.0F).sound(SoundType.LANTERN).pushReaction(PushReaction.DESTROY)
					.noCollission().noOcclusion().lightLevel(state -> 14), true);
	public static final SpireFixtureBlock TAPESTRY = add("spire_tapestry", properties -> new SpireFixtureBlock(properties, SpireFixtureBlock.Kind.TAPESTRY),
			unbreakable(MapColor.COLOR_BLUE, SoundType.WOOL).noCollission().noOcclusion(), false);
	public static final SpireBrazierBlock BRAZIER = add("spire_brazier", SpireBrazierBlock::new, unbreakable(MapColor.STONE, SoundType.STONE)
			.noOcclusion().lightLevel(state -> 15), false);
	public static final SpireLampBlock LAMP = add("spire_lamp", SpireLampBlock::new, unbreakable(MapColor.GOLD, SoundType.LANTERN)
			.noCollission().noOcclusion().lightLevel(state -> 15), false);
	/** A secret room's walled-up doorway: the one block of the spire a climber may break (SpireCrackedWallBlock). */
	public static final SpireCrackedWallBlock CRACKED_WALL = add("spire_cracked_wall", SpireCrackedWallBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.DEEPSLATE_BRICKS).noLootTable().pushReaction(PushReaction.BLOCK), false);
	/** A trial's breakables: the Honey Golem's amber (jars, the cells it walls a climber in), the Frost Mirror's panes. */
	public static final SpireCrackedWallBlock AMBER = add("spire_amber", SpireCrackedWallBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_ORANGE).strength(0.8F, 3.0F).sound(SoundType.HONEY_BLOCK).noLootTable().pushReaction(PushReaction.BLOCK).lightLevel(state -> 4),
			false);
	public static final SpireCrackedWallBlock ICE_MIRROR = add("spire_ice_mirror", SpireCrackedWallBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.ICE).strength(0.8F, 3.0F).sound(SoundType.GLASS).noLootTable().pushReaction(PushReaction.BLOCK).lightLevel(state -> 6),
			false);
	/** The Builders' door in a doorway (SpireDoorBlock): a touch opens it for good. */
	public static final SpireDoorBlock DOOR = add("spire_door", SpireDoorBlock::new, unbreakable(MapColor.COLOR_BROWN, SoundType.DEEPSLATE_BRICKS)
			.noOcclusion().lightLevel(state -> state.getValue(SpireDoorBlock.OPENING) ? 9 : 2), false);
	/** A side room's riddle (Puzzles): levers, glyphs, chimes, rune plates. */
	public static final SpirePuzzleBlock LEVER = puzzle("spire_lever", SpirePuzzleBlock.Kind.LEVER, SoundType.COPPER, 0, 0);
	public static final SpirePuzzleBlock GLYPH = puzzle("spire_glyph", SpirePuzzleBlock.Kind.GLYPH, SoundType.DEEPSLATE, 2, 9);
	public static final SpirePuzzleBlock CHIME = puzzle("spire_chime", SpirePuzzleBlock.Kind.CHIME, SoundType.AMETHYST, 4, 12);
	public static final SpirePuzzleBlock PLATE = puzzle("spire_plate", SpirePuzzleBlock.Kind.PLATE, SoundType.DEEPSLATE, 1, 8);
	/** The Builders' traps on a floor's way (SpireTrapBlock): spikes in the floor, darts from a wall. */
	public static final SpireTrapBlock SPIKES = add("spire_spikes", properties -> new SpireTrapBlock(properties, SpireTrapBlock.Kind.SPIKES),
			unbreakable(MapColor.DEEPSLATE, SoundType.DEEPSLATE).noOcclusion(), false);
	public static final SpireTrapBlock DART = add("spire_dart", properties -> new SpireTrapBlock(properties, SpireTrapBlock.Kind.DART),
			unbreakable(MapColor.DEEPSLATE, SoundType.DEEPSLATE_BRICKS), false);
	/** What the Builders left to be touched and watched, one kind a theme (SpireCurioBlock). */
	public static final SpireCurioBlock GONG = curio("spire_gong", SpireCurioBlock.Kind.GONG, SoundType.COPPER, 0);
	public static final SpireCurioBlock LECTERN = curio("spire_lectern", SpireCurioBlock.Kind.LECTERN, SoundType.WOOD, 4);
	public static final SpireCurioBlock CRANK = curio("spire_crank", SpireCurioBlock.Kind.CRANK, SoundType.COPPER, 0);
	public static final SpireCurioBlock COG = curio("spire_cog", SpireCurioBlock.Kind.COG, SoundType.COPPER, 0);
	public static final SpireCurioBlock BUD = curio("spire_bud", SpireCurioBlock.Kind.BUD, SoundType.AZALEA, 3);
	public static final SpireCurioBlock VALVE = curio("spire_valve", SpireCurioBlock.Kind.VALVE, SoundType.COPPER, 0);
	public static final SpireCurioBlock PUFFBALL = curio("spire_puffball", SpireCurioBlock.Kind.PUFFBALL, SoundType.FUNGUS, 4);
	public static final SpireCurioBlock HONEY_TAP = curio("spire_honey_tap", SpireCurioBlock.Kind.HONEY_TAP, SoundType.HONEY_BLOCK, 3);
	public static final SpireCurioBlock ORRERY = curio("spire_orrery", SpireCurioBlock.Kind.ORRERY, SoundType.COPPER, 5);
	public static final SpireCurioBlock RESONATOR = curio("spire_resonator", SpireCurioBlock.Kind.RESONATOR, SoundType.AMETHYST, 7);
	public static final Block RUNE = add("spire_rune", Block::new, unbreakable(MapColor.DEEPSLATE, SoundType.DEEPSLATE).lightLevel(state -> 5), false);
	public static final net.minecraft.world.level.block.entity.BlockEntityType<net.minecraft.world.level.block.entity.TheEndPortalBlockEntity> RIFT_BE =
			com.vnap.block.LifeBlocks.blockEntity(SpireRiftBlockEntity::new, RIFT);

	/**
	 * The Core Anvil at the spire's foot: spire gear is honed and raised a tier on it (gear.CoreAnvilMenu). Unlike the
	 * spire's own blocks it is mined and crafted (the one on a spire's plaza is kept by the spire's guard on its box).
	 */
	public static final CoreAnvilBlock CORE_ANVIL = add("core_anvil", CoreAnvilBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.ANVIL)
			.pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(state -> 6), true);
	public static final MenuType<com.vnap.spire.gear.CoreAnvilMenu> CORE_ANVIL_MENU = new MenuType<>(com.vnap.spire.gear.CoreAnvilMenu::new,
			FeatureFlags.VANILLA_SET);

	/** A floor's chest opens as a chest of the spire's look (client/ui/ThemedChestScreen), not the grey vanilla one. */
	public static final MenuType<net.minecraft.world.inventory.ChestMenu> CHEST_MENU = new MenuType<>((id, inventory) ->
			new net.minecraft.world.inventory.ChestMenu(SpireBlocks.CHEST_MENU, id, inventory, new net.minecraft.world.SimpleContainer(27), 3),
			FeatureFlags.VANILLA_SET);

	private SpireBlocks() {
	}

	private static SpirePuzzleBlock puzzle(String path, SpirePuzzleBlock.Kind kind, SoundType sound, int dark, int lit) {
		return add(path, properties -> new SpirePuzzleBlock(properties, kind), unbreakable(MapColor.STONE, sound).noOcclusion()
				.lightLevel(state -> state.getValue(SpirePuzzleBlock.LIT) ? lit : dark), false);
	}

	private static SpireCurioBlock curio(String path, SpireCurioBlock.Kind kind, SoundType sound, int light) {
		return add(path, properties -> new SpireCurioBlock(properties, kind), unbreakable(MapColor.METAL, sound).noOcclusion().lightLevel(state -> light), false);
	}

	/** Every curio, for the generator and the models. */
	public static java.util.List<SpireCurioBlock> curios() {
		return java.util.List.of(GONG, LECTERN, CRANK, COG, BUD, VALVE, PUFFBALL, HONEY_TAP, ORRERY, RESONATOR);
	}

	private static BlockBehaviour.Properties unbreakable(MapColor color, SoundType sound) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3_600_000.0F).sound(sound).noLootTable()
				.pushReaction(PushReaction.BLOCK);
	}

	private static <B extends Block> B add(String path, java.util.function.Function<BlockBehaviour.Properties, B> factory,
			BlockBehaviour.Properties properties, boolean item) {
		ResourceLocation id = VillagerLife.id(path);
		B block = factory.apply(Mc.block(properties, id));
		BLOCKS.put(id, block);
		if (item) ITEMS.put(id, new BlockItem(block, Mc.blockItem(new Item.Properties(), id)));
		return block;
	}

	public static void registerBlocks(RegisterEvent.RegisterHelper<Block> helper) {
		BLOCKS.forEach(helper::register);
	}

	private static @org.jspecify.annotations.Nullable Item dart;

	/** A dart wall's shot as it flies (only drawn: not in the creative tab, never dropped). */
	public static Item dart() {
		return java.util.Objects.requireNonNull(dart);
	}

	public static void registerItems(RegisterEvent.RegisterHelper<Item> helper) {
		ITEMS.forEach(helper::register);
		ResourceLocation dartId = VillagerLife.id("spire_dart_bolt");
		dart = new Item(Mc.item(new Item.Properties(), dartId));
		helper.register(dartId, dart);
		com.vnap.spire.gear.SpireGearItems.register(helper);
	}

	public static Map<ResourceLocation, Item> items() {
		return ITEMS;
	}

	public static void registerMenus(RegisterEvent.RegisterHelper<MenuType<?>> helper) {
		helper.register(VillagerLife.id("core_anvil"), CORE_ANVIL_MENU);
		helper.register(VillagerLife.id("spire_chest"), CHEST_MENU);
	}
}
