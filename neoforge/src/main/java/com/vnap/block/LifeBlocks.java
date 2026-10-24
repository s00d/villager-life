package com.vnap.block;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.vnap.Mc;
import com.vnap.VillagerLife;
import com.vnap.block.decor.DecorBlockEntity;
import com.vnap.block.decor.MachineMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.RegisterEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.Map;
import java.util.stream.Stream;

public final class LifeBlocks {
    private static final Map<ResourceLocation, Block> BLOCKS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Item> BLOCK_ITEMS = new LinkedHashMap<>();
    private static final String DECOR_PATH = "/data/villager_life/decor.json";
    private static final String MACHINES_PATH = "/data/villager_life/machines.json";
    private static final String HOME_PATH = "/data/villager_life/home_decor.json";
    private static final VoxelShape TOTEM_SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 16, 13), Block.box(0, 9, 6, 16, 13, 10));

    public static final Block MONUMENT_BASE = block("monument_base", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.5F, 9.0F).sound(SoundType.STONE));
    public static final Block MONUMENT_PILLAR = block("monument_pillar", p -> new ShapedBlock(p, Block.box(1, 0, 1, 15, 16, 15)),
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.5F, 9.0F).sound(SoundType.STONE)
                    .noOcclusion());
    public static final Block MONUMENT_PLAQUE = block("monument_plaque", MonumentPlaqueBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(3.0F, 6.0F).sound(SoundType.STONE).noOcclusion());
    public static final Block ALTAR_BASE = block("altar_base", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(4.0F, 9.0F).sound(SoundType.DEEPSLATE));
    public static final Block ALTAR_PILLAR = block("altar_pillar", p -> new ShapedBlock(p, Block.box(2, 0, 2, 14, 16, 14)),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(4.0F, 9.0F).sound(SoundType.CHAIN)
                    .noOcclusion());
    public static final Block ALTAR_CORE = block("altar_core", AltarCoreBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(5.0F, 12.0F).sound(SoundType.ANVIL)
                    .noOcclusion().lightLevel(s -> AltarCoreBlock.LIGHT[s.getValue(AltarCoreBlock.HEAT)]));
    public static final Block FESTIVAL_TOTEM = block("festival_totem", p -> new ShapedBlock(p, TOTEM_SHAPE),
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(2.0F, 6.0F).sound(SoundType.WOOD)
                    .noOcclusion().lightLevel(s -> 8));
    public static final Block MOURNING_TOTEM = block("mourning_totem", p -> new ShapedBlock(p, TOTEM_SHAPE),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).sound(SoundType.WOOD)
                    .noOcclusion());

    /** The family's double bed (two beds side by side under one frame). */
    public static final DoubleBedBlock DOUBLE_BED = bed("double_bed", DoubleBedBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.4F).sound(SoundType.WOOD).noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /** The wall crossbow on the towers (Wall, TurretBehavior). */
    public static final com.vnap.block.turret.ArrowTurretBlock ARROW_TURRET = block("arrow_turret", com.vnap.block.turret.ArrowTurretBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F, 6.0F)
                    .sound(SoundType.STONE).noOcclusion());

    /** The tower clock of the chapel (Chapel, ClockBehavior): a dial three blocks across, struck hours. */
    public static final com.vnap.block.clock.TowerClockBlock TOWER_CLOCK = block("tower_clock", com.vnap.block.clock.TowerClockBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F, 6.0F)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops());

    /** The tavern's bar: a meal for a few coppers (TownPlots TAVERN). */
    public static final com.vnap.block.town.TavernCounterBlock TAVERN_COUNTER = block("tavern_counter", com.vnap.block.town.TavernCounterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD).noOcclusion());

    /** The town's notice board: the town at a glance, its builds, its stall's wants (NoticeBoard). */
    public static final com.vnap.block.town.NoticeBoardBlock NOTICE_BOARD = block("notice_board", com.vnap.block.town.NoticeBoardBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F, 3.0F)
                    .sound(SoundType.WOOD).noOcclusion());

    /** A player's mailbox: the villagers' letters (village/post). */
    public static final com.vnap.block.town.MailboxBlock MAILBOX = block("mailbox", com.vnap.block.town.MailboxBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.5F, 3.0F)
                    .sound(SoundType.WOOD).noOcclusion());

    /** The smoking pot on top of the villagers' chimneys, in place of a campfire. */
    public static final com.vnap.block.town.ChimneyPotBlock CHIMNEY_POT = block("chimney_pot", com.vnap.block.town.ChimneyPotBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0F, 6.0F)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion());

    /** The small one on a thin chimney (a wall post's top). */
    public static final com.vnap.block.town.SmallChimneyPotBlock SMALL_CHIMNEY_POT = block("small_chimney_pot", com.vnap.block.town.SmallChimneyPotBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.5F, 6.0F)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion());

    /** A family's nameplate by its door, a player's lot's sign (VillagerHome, TownPlan): the mod's home marker. */
    public static final com.vnap.block.town.HouseSignBlock HOUSE_SIGN = block("house_sign", com.vnap.block.town.HouseSignBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F, 3.0F)
                    .sound(SoundType.WOOD).noOcclusion().ignitedByLava());

    /** What a player ordered at a stall comes in here with the caravan (village/market/Orders): theirs alone to open. */
    public static final com.vnap.block.town.ParcelBoxBlock PARCEL_BOX = block("parcel_box", com.vnap.block.town.ParcelBoxBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F, 6.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());

    public static final List<DecorBlock> DECOR = decor(DECOR_PATH, false);
    public static final List<DecorBlock> MACHINES = decor(MACHINES_PATH, true);
    /** Decor a family puts up at home; not part of any workshop. */
    public static final List<DecorBlock> HOME_DECOR = decor(HOME_PATH, false);

    public static final BlockEntityType<MonumentBlockEntity> MONUMENT_BE = blockEntity(MonumentBlockEntity::new, MONUMENT_PLAQUE);
    public static final BlockEntityType<AltarBlockEntity> ALTAR_BE = blockEntity(AltarBlockEntity::new, ALTAR_CORE);
    public static final BlockEntityType<com.vnap.block.turret.ArrowTurretBlockEntity> TURRET_BE =
            blockEntity(com.vnap.block.turret.ArrowTurretBlockEntity::new, ARROW_TURRET);
    public static final BlockEntityType<com.vnap.block.clock.TowerClockBlockEntity> CLOCK_BE =
            blockEntity(com.vnap.block.clock.TowerClockBlockEntity::new, TOWER_CLOCK);
    public static final BlockEntityType<com.vnap.block.town.HouseSignBlockEntity> HOUSE_SIGN_BE =
            blockEntity(com.vnap.block.town.HouseSignBlockEntity::new, HOUSE_SIGN);
    public static final BlockEntityType<com.vnap.block.town.ParcelBoxBlockEntity> PARCEL_BOX_BE =
            blockEntity(com.vnap.block.town.ParcelBoxBlockEntity::new, PARCEL_BOX);
    /** A parcel box opens as a chest of the village's look (client/ui/ThemedChestScreen). */
    public static final MenuType<net.minecraft.world.inventory.ChestMenu> PARCEL_MENU = new MenuType<>((id, inventory) ->
            new net.minecraft.world.inventory.ChestMenu(LifeBlocks.PARCEL_MENU, id, inventory, new net.minecraft.world.SimpleContainer(54), 6),
            net.minecraft.world.flag.FeatureFlags.VANILLA_SET);
    public static final BlockEntityType<DecorBlockEntity> DECOR_BE = blockEntity(DecorBlockEntity::new,
            Stream.of(DECOR, MACHINES, HOME_DECOR).flatMap(List::stream).toArray(Block[]::new));
    public static final MenuType<MachineMenu> MACHINE_MENU = IMenuTypeExtension.create(MachineMenu::new);
    public static final MenuType<com.vnap.village.market.ShopMenu> SHOP_MENU = IMenuTypeExtension.create(com.vnap.village.market.ShopMenu::new);

    private LifeBlocks() {
    }

    private static <B extends Block> B block(String path, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
        return add(path, factory.apply(Mc.block(properties, VillagerLife.id(path))));
    }

    private static <B extends Block> B add(String path, B block) {
        ResourceLocation id = VillagerLife.id(path);
        BLOCKS.put(id, block);
        BLOCK_ITEMS.put(id, new BlockItem(block, Mc.blockItem(new Item.Properties(), id)));
        return block;
    }

    private static DoubleBedBlock bed(String path, Function<BlockBehaviour.Properties, DoubleBedBlock> factory, BlockBehaviour.Properties properties) {
        ResourceLocation id = VillagerLife.id(path);
        DoubleBedBlock block = factory.apply(Mc.block(properties, id));
        BLOCKS.put(id, block);
        // a bed's item is a plain block item from 26.3 (the block sets its other half)
        //? if >=26.3 {
        /*BLOCK_ITEMS.put(id, new net.minecraft.world.item.BlockItem(block, Mc.blockItem(new Item.Properties().stacksTo(1), id)));
        *///?} else
        BLOCK_ITEMS.put(id, new net.minecraft.world.item.BedItem(block, Mc.blockItem(new Item.Properties().stacksTo(1), id)));
        return block;
    }

    private static List<DecorBlock> decor(String path, boolean machine) {
        try (InputStream stream = LifeBlocks.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing " + path);
            }
            List<DecorBlock> decor = new ArrayList<>();
            for (JsonElement element : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject entry = element.getAsJsonObject();
                JsonArray shape = entry.getAsJsonArray("shape");
                double[] box = new double[6];
                for (int i = 0; i < box.length; i++) {
                    box[i] = shape.get(i).getAsDouble();
                }
                int light = entry.get("light").getAsInt();
                String material = entry.get("sound").getAsString();
                SoundType sound = switch (material) {
                    case "wool" -> SoundType.WOOL;
                    case "metal" -> SoundType.COPPER;
                    case "stone" -> SoundType.STONE;
                    case "chain" -> SoundType.CHAIN;
                    case "glass" -> SoundType.GLASS;
                    default -> SoundType.WOOD;
                };
                boolean hard = !material.equals("wool") && !material.equals("wood");
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                        .mapColor(hard ? MapColor.METAL : material.equals("wool") ? MapColor.WOOL : MapColor.WOOD)
                        .strength(hard ? 2.0F : 1.0F, 3.0F).sound(sound).noOcclusion().lightLevel(s -> light);
                if (!hard) {
                    properties.ignitedByLava();
                }
                String id = entry.get("id").getAsString();
                DecorBlock block = new DecorBlock(Mc.block(properties, VillagerLife.id(id)), id, entry.get("profession").getAsString(), box,
                        entry.get("smoke").getAsBoolean(), machine);
                decor.add(block);
                add(id, block);
            }
            return List.copyOf(decor);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void registerBlocks(RegisterEvent.RegisterHelper<Block> helper) {
        BLOCKS.forEach(helper::register);
    }

    public static void registerBlockItems(RegisterEvent.RegisterHelper<Item> helper) {
        BLOCK_ITEMS.forEach(helper::register);
    }

    public static void registerBlockEntities(RegisterEvent.RegisterHelper<BlockEntityType<?>> helper) {
        helper.register(VillagerLife.id("monument_plaque"), MONUMENT_BE);
        helper.register(VillagerLife.id("altar_core"), ALTAR_BE);
        helper.register(VillagerLife.id("arrow_turret"), TURRET_BE);
        helper.register(VillagerLife.id("tower_clock"), CLOCK_BE);
        helper.register(VillagerLife.id("decor"), DECOR_BE);
        helper.register(VillagerLife.id("house_sign"), HOUSE_SIGN_BE);
        helper.register(VillagerLife.id("parcel_box"), PARCEL_BOX_BE);
        helper.register(VillagerLife.id("spire_rift"), com.vnap.spire.SpireBlocks.RIFT_BE);
    }

    public static void registerMenus(RegisterEvent.RegisterHelper<MenuType<?>> helper) {
        helper.register(VillagerLife.id("machine"), MACHINE_MENU);
        helper.register(VillagerLife.id("shop"), SHOP_MENU);
        helper.register(VillagerLife.id("parcel_box"), PARCEL_MENU);
    }

    @SuppressFBWarnings(
            value = "NP_NONNULL_PARAM_VIOLATION",
            justification = "NeoForge 1.21.1 BlockEntityType.Builder.build takes null when the block entity has no data fixer.")
    public static <T extends BlockEntity> BlockEntityType<T> blockEntity(BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
        //? if >=1.21.2 {
        /*return new BlockEntityType<>(factory, java.util.Set.of(blocks));
        *///?} else
        return BlockEntityType.Builder.of(factory, blocks).build(null);
    }

    public static Map<ResourceLocation, Item> blockItems() {
        return BLOCK_ITEMS;
    }
}
