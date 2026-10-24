package com.vnap.mega;

import com.vnap.Mc;
import com.vnap.VillagerLife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** The megastructures' blocks: the frame, the parts the Outer Gates give, the controllers, a gate's field, the keystones. */
public final class MegaBlocks {
	private static final Map<ResourceLocation, Block> BLOCKS = new LinkedHashMap<>();
	private static final Map<ResourceLocation, Item> ITEMS = new LinkedHashMap<>();
	private static final Map<DyeColor, Item> KEYS = new EnumMap<>(DyeColor.class);

	public static final Block FRAME = add("mega_frame", Block::new, solid(MapColor.COLOR_GRAY, SoundType.NETHERITE_BLOCK, 5.0F), Rarity.COMMON);
	public static final Block PART_COLOSSEUM = add("mega_part_colosseum", Block::new, solid(MapColor.COLOR_RED, SoundType.NETHERITE_BLOCK, 8.0F)
			.lightLevel(state -> 6), Rarity.RARE);
	public static final Block PART_PATH = add("mega_part_path", Block::new, solid(MapColor.COLOR_LIGHT_BLUE, SoundType.AMETHYST, 8.0F)
			.lightLevel(state -> 6), Rarity.RARE);
	public static final Block PART_NAMELESS = add("mega_part_nameless", Block::new, solid(MapColor.COLOR_PURPLE, SoundType.SCULK_CATALYST, 8.0F)
			.lightLevel(state -> 6), Rarity.EPIC);
	public static final MegaControllerBlock QUARRY = controller("mega_quarry", MegaKind.QUARRY);
	public static final MegaControllerBlock GATE = controller("mega_gate", MegaKind.GATE);
	public static final MegaControllerBlock SMELTER = controller("mega_smelter", MegaKind.SMELTER);
	public static final MegaControllerBlock CALM = controller("mega_calm", MegaKind.CALM);
	public static final MegaControllerBlock DOME = controller("mega_dome", MegaKind.DOME);
	public static final MegaControllerBlock WELL = controller("mega_well", MegaKind.WELL);
	public static final MegaControllerBlock DRAIN = controller("mega_drain", MegaKind.DRAIN);
	public static final MegaControllerBlock SAWMILL = controller("mega_sawmill", MegaKind.SAWMILL);
	/** What fills a whole, keyed and linked Waygate (Gates): walked into, it takes a player to the other one. */
	public static final GateFieldBlock GATE_FIELD = add("mega_gate_field", GateFieldBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3_600_000.0F).noCollission().noOcclusion().noLootTable()
			.sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK).lightLevel(state -> 11), null);
	public static final BlockEntityType<MegaControllerBlockEntity> CONTROLLER_BE = com.vnap.block.LifeBlocks.blockEntity(MegaControllerBlockEntity::new,
			QUARRY, GATE, SMELTER, CALM, DOME, WELL, DRAIN, SAWMILL);

	static {
		for (DyeColor colour : DyeColor.values()) {
			ResourceLocation id = VillagerLife.id("gate_key_" + colour.getName());
			Item key = new Item(Mc.item(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), id));
			ITEMS.put(id, key);
			KEYS.put(colour, key);
		}
	}

	private MegaBlocks() {
	}

	public static MegaControllerBlock controller(MegaKind kind) {
		return switch (kind) {
			case QUARRY -> QUARRY;
			case GATE -> GATE;
			case SMELTER -> SMELTER;
			case CALM -> CALM;
			case DOME -> DOME;
			case WELL -> WELL;
			case DRAIN -> DRAIN;
			case SAWMILL -> SAWMILL;
		};
	}

	public static Item key(DyeColor colour) {
		return java.util.Objects.requireNonNull(KEYS.get(colour));
	}

	/** The colour of a Waygate keystone, or null for anything else. */
	public static @org.jspecify.annotations.Nullable DyeColor colourOf(net.minecraft.world.item.ItemStack stack) {
		for (Map.Entry<DyeColor, Item> key : KEYS.entrySet()) if (stack.is(key.getValue())) return key.getKey();
		return null;
	}

	private static MegaControllerBlock controller(String path, MegaKind kind) {
		return add(path, properties -> new MegaControllerBlock(properties, kind), solid(MapColor.COLOR_GRAY, SoundType.NETHERITE_BLOCK, 6.0F)
				.lightLevel(state -> state.getValue(MegaControllerBlock.FORMED) ? 10 : 0), Rarity.EPIC);
	}

	private static BlockBehaviour.Properties solid(MapColor colour, SoundType sound, float strength) {
		return BlockBehaviour.Properties.of().mapColor(colour).strength(strength, 1200.0F).requiresCorrectToolForDrops().sound(sound)
				.pushReaction(PushReaction.BLOCK);
	}

	private static <B extends Block> B add(String path, java.util.function.Function<BlockBehaviour.Properties, B> factory,
			BlockBehaviour.Properties properties, @org.jspecify.annotations.Nullable Rarity rarity) {
		ResourceLocation id = VillagerLife.id(path);
		B block = factory.apply(Mc.block(properties, id));
		BLOCKS.put(id, block);
		if (rarity != null) ITEMS.put(id, new BlockItem(block, Mc.blockItem(new Item.Properties().rarity(rarity), id)));
		return block;
	}

	public static Map<ResourceLocation, Block> blocks() {
		return BLOCKS;
	}

	public static Map<ResourceLocation, Item> items() {
		return ITEMS;
	}

	public static void registerBlocks(RegisterEvent.RegisterHelper<Block> helper) {
		BLOCKS.forEach(helper::register);
	}

	public static void registerItems(RegisterEvent.RegisterHelper<Item> helper) {
		ITEMS.forEach(helper::register);
	}

	public static void registerBlockEntities(RegisterEvent.RegisterHelper<BlockEntityType<?>> helper) {
		helper.register(VillagerLife.id("mega_controller"), CONTROLLER_BE);
	}
}
