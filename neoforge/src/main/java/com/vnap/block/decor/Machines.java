package com.vnap.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Every machine recipe book by id: the twelve profession machines, four decor benches and the fletching table. */
public final class Machines {
    private static final Map<String, Machine> ALL = new LinkedHashMap<>();
    private static final int[] ONE = {1};
    private static final int[] ONE_ONE = {1, 1};

    public static final Machine SPINNING_WHEEL = add(new Base("spinning_wheel", s -> s.is(ItemTags.WOOL)) {
        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            return c.getItem(0).is(ItemTags.WOOL) ? Craft.of(ONE, new ItemStack(Items.STRING, 3), 400, 0) : null;
        }

        @Override
        public List<Example> examples() {
            return List.of(example(new ItemStack(Items.STRING, 3), 400, tagged(ItemTags.WOOL)));
        }
    });

    public static final Machine INK_AND_QUILL = add(new Base("ink_and_quill", s -> s.is(Items.WRITTEN_BOOK), s -> s.is(Items.WRITABLE_BOOK)) {
        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            ItemStack original = c.getItem(0);
            WrittenBookContent content = original.get(DataComponents.WRITTEN_BOOK_CONTENT);
            WrittenBookContent copy = content == null ? null : com.vnap.Mc.copyOf(content);
            if (copy == null || !c.getItem(1).is(Items.WRITABLE_BOOK)) {
                return null;
            }
            ItemStack result = original.copyWithCount(1);
            result.set(DataComponents.WRITTEN_BOOK_CONTENT, copy);
            return Craft.of(new int[]{0, 1}, result, 1200, 0);
        }

        @Override
        public List<Example> examples() {
            return List.of(example(new ItemStack(Items.WRITTEN_BOOK), 1200, List.of(new ItemStack(Items.WRITTEN_BOOK)),
                    List.of(new ItemStack(Items.WRITABLE_BOOK))));
        }
    });

    public static final Machine CHISEL_BENCH = add(new Chisel());

    public static final Machine CHAINMAIL_RACK = add(new Base("chainmail_rack", s -> s.is(Items.CHAIN), s -> Chainmail.of(s) != null) {
        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            Chainmail piece = Chainmail.of(c.getItem(1));
            if (piece == null || !c.getItem(0).is(Items.CHAIN) || c.getItem(0).getCount() < piece.chains) {
                return null;
            }
            return Craft.of(new int[]{piece.chains, 1}, new ItemStack(piece.result), 1200, 0);
        }

        @Override
        public List<Example> examples() {
            List<Example> list = new ArrayList<>();
            for (Chainmail piece : Chainmail.values()) {
                list.add(example(new ItemStack(piece.result), 1200, List.of(new ItemStack(Items.CHAIN, piece.chains)),
                        List.of(new ItemStack(piece.pattern))));
            }
            return list;
        }
    });

    public static final Machine GREENHOUSE_BOX = add(new Base("greenhouse_box", s -> Greenhouse.of(s) != null, s -> s.is(Items.BONE_MEAL)) {
        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            Greenhouse crop = Greenhouse.of(c.getItem(0));
            return crop != null && c.getItem(1).is(Items.BONE_MEAL) ? Craft.of(ONE_ONE, crop.result(), 1200, 0) : null;
        }

        @Override
        public List<Example> examples() {
            List<Example> list = new ArrayList<>();
            for (Greenhouse crop : Greenhouse.values()) {
                list.add(example(crop.result(), 1200, List.of(new ItemStack(crop.seed)), List.of(new ItemStack(Items.BONE_MEAL))));
            }
            return list;
        }
    });

    public static final Machine SMOKEHOUSE = add(new Cooker("smokehouse", 400, 100, Map.of(
            Items.BEEF, Items.COOKED_BEEF, Items.PORKCHOP, Items.COOKED_PORKCHOP, Items.MUTTON, Items.COOKED_MUTTON,
            Items.CHICKEN, Items.COOKED_CHICKEN, Items.RABBIT, Items.COOKED_RABBIT, Items.COD, Items.COOKED_COD,
            Items.SALMON, Items.COOKED_SALMON, Items.POTATO, Items.BAKED_POTATO, Items.KELP, Items.DRIED_KELP)));

    public static final Machine DYE_VAT = add(new Base("dye_vat", s -> s.is(ItemTags.WOOL), s -> s.getItem() instanceof DyeItem) {
        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            ItemStack wool = c.getItem(0);
            if (!wool.is(ItemTags.WOOL) || wool.getCount() < 4 || !(c.getItem(1).getItem() instanceof DyeItem)) {
                return null;
            }
            Item dyed = wool(com.vnap.Mc.dyeColor(c.getItem(1)));
            return wool.is(dyed) ? null : Craft.of(new int[]{4, 1}, new ItemStack(dyed, 4), 400, 0);
        }

        @Override
        public List<Example> examples() {
            List<Example> list = new ArrayList<>();
            for (DyeColor color : DyeColor.values()) {
                if (color != DyeColor.WHITE) {
                    list.add(example(new ItemStack(wool(color), 4), 400, List.of(new ItemStack(Items.WHITE_WOOL, 4)),
                            List.of(new ItemStack(com.vnap.Mc.dye(color)))));
                }
            }
            return list;
        }
    });

    public static final Machine FISH_TRAP = add(new Base("fish_trap", Machines::bait) {
        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            if (!bait(c.getItem(0)) || !level.getBlockState(pos).getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false)) {
                return null;
            }
            return new Craft(ONE, null, l -> fish(l, pos), 2400, 0);
        }

        @Override
        public List<Example> examples() {
            List<ItemStack> baits = List.of(new ItemStack(Items.BREAD), new ItemStack(Items.WHEAT), new ItemStack(Items.ROTTEN_FLESH));
            return List.of(example(new ItemStack(Items.COD), 2400, baits), example(new ItemStack(Items.SALMON), 2400, baits));
        }
    });

    public static final Machine READING_DESK = add(new Base("reading_desk", s -> s.is(Items.BOOK), s -> s.is(Items.LAPIS_LAZULI)) {
        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            if (!c.getItem(0).is(Items.BOOK) || !c.getItem(1).is(Items.LAPIS_LAZULI) || c.getItem(1).getCount() < 3) {
                return null;
            }
            return new Craft(new int[]{1, 3}, null, l -> EnchantmentHelper.enchantItem(l.getRandom(), new ItemStack(Items.BOOK), 8,
                    l.registryAccess(), l.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.IN_ENCHANTING_TABLE)),
                    6000, 0);
        }

        @Override
        public List<Example> examples() {
            return List.of(example(new ItemStack(Items.ENCHANTED_BOOK), 6000, List.of(new ItemStack(Items.BOOK)),
                    List.of(new ItemStack(Items.LAPIS_LAZULI, 3))));
        }
    });

    public static final Machine SURVEY_TABLE = add(new Base("survey_table", s -> s.is(Items.MAP), s -> s.is(Items.PAPER)) {
        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            if (!c.getItem(0).is(Items.MAP) || !c.getItem(1).is(Items.PAPER) || c.getItem(1).getCount() < 4) {
                return null;
            }
            return new Craft(new int[]{1, 4}, null, l -> MapItem.create(l, pos.getX(), pos.getZ(), (byte) 2, true, false), 1200, 0);
        }

        @Override
        public List<Example> examples() {
            return List.of(example(new ItemStack(Items.FILLED_MAP), 1200, List.of(new ItemStack(Items.MAP)),
                    List.of(new ItemStack(Items.PAPER, 4))));
        }
    });

    public static final Machine FONT = add(new Font());

    public static final Machine TANNING_VAT = add(new Base("tanning_vat", s -> s.is(Items.RABBIT_HIDE) || s.is(Items.ROTTEN_FLESH)) {
        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            ItemStack in = c.getItem(0);
            if (in.is(Items.RABBIT_HIDE) && in.getCount() >= 4) {
                return Craft.of(new int[]{4}, new ItemStack(Items.LEATHER), 600, 0);
            }
            if (in.is(Items.ROTTEN_FLESH) && in.getCount() >= 8) {
                return Craft.of(new int[]{8}, new ItemStack(Items.LEATHER), 1200, 0);
            }
            return null;
        }

        @Override
        public List<Example> examples() {
            return List.of(example(new ItemStack(Items.LEATHER), 600, List.of(new ItemStack(Items.RABBIT_HIDE, 4))),
                    example(new ItemStack(Items.LEATHER), 1200, List.of(new ItemStack(Items.ROTTEN_FLESH, 8))));
        }
    });

    public static final Machine KILN = add(new Cooker("kiln", 300, 100, Map.ofEntries(
            Map.entry(Items.CLAY_BALL, Items.BRICK), Map.entry(Items.CLAY, Items.TERRACOTTA), Map.entry(Items.SAND, Items.GLASS),
            Map.entry(Items.RED_SAND, Items.GLASS), Map.entry(Items.COBBLESTONE, Items.STONE), Map.entry(Items.STONE, Items.SMOOTH_STONE),
            Map.entry(Items.STONE_BRICKS, Items.CRACKED_STONE_BRICKS), Map.entry(Items.COBBLED_DEEPSLATE, Items.DEEPSLATE),
            Map.entry(Items.DEEPSLATE_BRICKS, Items.CRACKED_DEEPSLATE_BRICKS), Map.entry(Items.DEEPSLATE_TILES, Items.CRACKED_DEEPSLATE_TILES),
            Map.entry(Items.NETHERRACK, Items.NETHER_BRICK), Map.entry(Items.NETHER_BRICKS, Items.CRACKED_NETHER_BRICKS),
            Map.entry(Items.POLISHED_BLACKSTONE_BRICKS, Items.CRACKED_POLISHED_BLACKSTONE_BRICKS),
            Map.entry(Items.SANDSTONE, Items.SMOOTH_SANDSTONE), Map.entry(Items.RED_SANDSTONE, Items.SMOOTH_RED_SANDSTONE),
            Map.entry(Items.QUARTZ_BLOCK, Items.SMOOTH_QUARTZ), Map.entry(Items.BASALT, Items.SMOOTH_BASALT))));

    public static final Machine ARMOR_BENCH = add(new Mender("armor_bench", s -> com.vnap.Mc.isArmor(s), false));

    public static final Machine GRINDING_WHEEL = add(new Mender("grinding_wheel", s -> com.vnap.Mc.isDigger(s), false));

    public static final Machine WEAPON_FORGE = add(new Mender("weapon_forge", s -> com.vnap.Mc.isSword(s)
            || s.getItem() instanceof TridentItem || s.getItem() instanceof MaceItem || s.is(ItemTags.AXES), true));

    /** Opens on the vanilla fletching table; works only while the menu is open. */
    public static final Machine FLETCHING = add(new Base("fletching", s -> s.is(Items.FLINT) || s.is(Items.ARROW),
            s -> s.is(Items.STICK) || s.is(Items.LINGERING_POTION), s -> s.is(Items.FEATHER)) {
        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            ItemStack a = c.getItem(0);
            ItemStack b = c.getItem(1);
            if (a.is(Items.FLINT) && b.is(Items.STICK) && c.getItem(2).is(Items.FEATHER)) {
                return Craft.of(new int[]{1, 1, 1}, new ItemStack(Items.ARROW, 6), 60, 0);
            }
            if (a.is(Items.ARROW) && a.getCount() >= 8 && b.is(Items.LINGERING_POTION)) {
                ItemStack tipped = new ItemStack(Items.TIPPED_ARROW, 8);
                tipped.set(DataComponents.POTION_CONTENTS, b.get(DataComponents.POTION_CONTENTS));
                return Craft.of(new int[]{8, 1}, tipped, 100, 0);
            }
            return null;
        }

        @Override
        public List<Example> examples() {
            return List.of(example(new ItemStack(Items.ARROW, 6), 60, List.of(new ItemStack(Items.FLINT)),
                            List.of(new ItemStack(Items.STICK)), List.of(new ItemStack(Items.FEATHER))),
                    example(new ItemStack(Items.TIPPED_ARROW, 8), 100, List.of(new ItemStack(Items.ARROW, 8)),
                            List.of(new ItemStack(Items.LINGERING_POTION))));
        }
    });

    private Machines() {
    }

    private static Machine add(Machine machine) {
        ALL.put(machine.id(), machine);
        return machine;
    }

    public static @Nullable Machine get(String id) {
        return ALL.get(id);
    }

    public static Map<String, Machine> all() {
        return ALL;
    }

    static Item wool(DyeColor color) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color.getName() + "_wool"));
    }

    static boolean bait(ItemStack stack) {
        return stack.is(Items.BREAD) || stack.is(Items.WHEAT) || stack.is(Items.ROTTEN_FLESH);
    }

    private static ItemStack fish(ServerLevel level, BlockPos pos) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                .create(LootContextParamSets.FISHING);
        List<ItemStack> loot = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING_FISH).getRandomItems(params);
        return loot.isEmpty() ? new ItemStack(Items.COD) : loot.get(0);
    }

    private static List<ItemStack> tagged(net.minecraft.tags.TagKey<Item> tag) {
        List<ItemStack> list = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(holder -> list.add(new ItemStack(holder)));
        return list;
    }

    private static Machine.Example example(ItemStack output, int ticks, List<ItemStack> a) {
        return new Machine.Example(List.of(a), output, ticks);
    }

    private static Machine.Example example(ItemStack output, int ticks, List<ItemStack> a, List<ItemStack> b) {
        return new Machine.Example(List.of(a, b), output, ticks);
    }

    private static Machine.Example example(ItemStack output, int ticks, List<ItemStack> a, List<ItemStack> b, List<ItemStack> c) {
        return new Machine.Example(List.of(a, b, c), output, ticks);
    }

    /** Input filters per slot; the rest is up to each machine. */
    private abstract static class Base implements Machine {
        private final String id;
        private final List<Predicate<ItemStack>> slots;

        Base(String id, Predicate<ItemStack> a) {
            this(id, List.of(a));
        }

        Base(String id, Predicate<ItemStack> a, Predicate<ItemStack> b) {
            this(id, List.of(a, b));
        }

        Base(String id, Predicate<ItemStack> a, Predicate<ItemStack> b, Predicate<ItemStack> c) {
            this(id, List.of(a, b, c));
        }

        Base(String id, List<Predicate<ItemStack>> slots) {
            this.id = id;
            this.slots = slots;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public int inputs() {
            return slots.size();
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            return slot < slots.size() && slots.get(slot).test(stack);
        }
    }

    /** Cooks a listed input slowly, burning fuel at half a furnace's rate. */
    private static final class Cooker extends Base {
        private final int ticks;
        private final int fuelPerItem;
        private final Map<Item, Item> results;

        Cooker(String id, int ticks, int fuelPerItem, Map<Item, Item> results) {
            super(id, s -> results.containsKey(s.getItem()), s -> com.vnap.Mc.burnTime(s) > 0);
            this.ticks = ticks;
            this.fuelPerItem = fuelPerItem;
            this.results = results;
        }

        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            Item result = results.get(c.getItem(0).getItem());
            return result == null ? null : Craft.of(ONE, new ItemStack(result), ticks, fuelPerItem);
        }

        @Override
        public int fuel(ItemStack stack) {
            return com.vnap.Mc.burnTime(stack);
        }

        @Override
        public int fuelSlot() {
            return 1;
        }

        @Override
        public List<Example> examples() {
            List<Example> list = new ArrayList<>();
            results.forEach((in, out) -> list.add(example(new ItemStack(out), ticks, List.of(new ItemStack(in)),
                    List.of(new ItemStack(Items.COAL), new ItemStack(Items.CHARCOAL)))));
            return list;
        }
    }

    /** Puts a quarter of durability back for one repair material; the forge also sharpens. */
    private static final class Mender extends Base {
        private final boolean sharpen;

        Mender(String id, Predicate<ItemStack> kind, boolean sharpen) {
            super(id, kind, s -> !s.isEmpty());
            this.sharpen = sharpen;
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            return slot == 0 ? super.accepts(0, stack) && stack.isDamageableItem() : slot == 1 && !stack.isEmpty();
        }

        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            ItemStack tool = c.getItem(0);
            ItemStack material = c.getItem(1);
            if (!accepts(0, tool) || material.isEmpty() || !com.vnap.Mc.repairs(tool, material)) {
                return null;
            }
            boolean worn = tool.getDamageValue() > 0;
            if (!worn && !(sharpen && Sharpening.charges(tool) < Sharpening.CHARGES)) {
                return null;
            }
            ItemStack result = tool.copyWithCount(1);
            result.setDamageValue(Math.max(0, tool.getDamageValue() - tool.getMaxDamage() / 4));
            if (sharpen) {
                Sharpening.set(result, Sharpening.CHARGES);
            }
            return Craft.of(ONE_ONE, result, 600, 0);
        }

        @Override
        public List<Example> examples() {
            Item tool = id().equals("armor_bench") ? Items.IRON_CHESTPLATE : id().equals("grinding_wheel") ? Items.IRON_PICKAXE : Items.IRON_SWORD;
            ItemStack worn = new ItemStack(tool);
            worn.setDamageValue(worn.getMaxDamage() / 2);
            ItemStack mended = new ItemStack(tool);
            mended.setDamageValue(worn.getMaxDamage() / 4);
            if (sharpen) {
                Sharpening.set(mended, Sharpening.CHARGES);
            }
            return List.of(example(mended, 600, List.of(worn), List.of(new ItemStack(Items.IRON_INGOT))));
        }
    }

    /** Brews one bottle per ingredient; blaze powder in the third slot lasts twenty brews. */
    private static final class Font extends Base {
        Font() {
            super("font", s -> s.has(DataComponents.POTION_CONTENTS) && (s.is(Items.POTION) || s.is(Items.SPLASH_POTION)
                    || s.is(Items.LINGERING_POTION)), s -> !s.isEmpty(), s -> s.is(Items.BLAZE_POWDER));
        }

        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            ItemStack potion = c.getItem(0);
            ItemStack reagent = c.getItem(1);
            ItemStack result = !accepts(0, potion) || reagent.isEmpty() ? null : com.vnap.Mc.brew(level, potion.copyWithCount(1), reagent);
            if (result == null) {
                return null;
            }
            return Craft.of(ONE_ONE, result, 400, 1);
        }

        @Override
        public int fuel(ItemStack stack) {
            return stack.is(Items.BLAZE_POWDER) ? 20 : 0;
        }

        @Override
        public int fuelSlot() {
            return 2;
        }

        @Override
        public List<Example> examples() {
            ItemStack awkward = net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,
                    net.minecraft.world.item.alchemy.Potions.AWKWARD);
            ItemStack healing = net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,
                    net.minecraft.world.item.alchemy.Potions.HEALING);
            return List.of(example(healing, 400, List.of(awkward), List.of(new ItemStack(Items.GLISTERING_MELON_SLICE)),
                    List.of(new ItemStack(Items.BLAZE_POWDER))));
        }
    }

    /** Plain makes cracked, vine or moss makes mossy, an iron nugget makes chiseled. */
    private static final class Chisel extends Base {
        private static final Map<Item, Item[]> FAMILIES = new LinkedHashMap<>();

        static {
            family(Items.STONE_BRICKS, Items.CRACKED_STONE_BRICKS, Items.MOSSY_STONE_BRICKS, Items.CHISELED_STONE_BRICKS);
            family(Items.COBBLESTONE, null, Items.MOSSY_COBBLESTONE, null);
            family(Items.DEEPSLATE_BRICKS, Items.CRACKED_DEEPSLATE_BRICKS, null, Items.CHISELED_DEEPSLATE);
            family(Items.DEEPSLATE_TILES, Items.CRACKED_DEEPSLATE_TILES, null, null);
            family(Items.NETHER_BRICKS, Items.CRACKED_NETHER_BRICKS, null, Items.CHISELED_NETHER_BRICKS);
            family(Items.POLISHED_BLACKSTONE_BRICKS, Items.CRACKED_POLISHED_BLACKSTONE_BRICKS, null, Items.CHISELED_POLISHED_BLACKSTONE);
            family(Items.SANDSTONE, null, null, Items.CHISELED_SANDSTONE);
            family(Items.RED_SANDSTONE, null, null, Items.CHISELED_RED_SANDSTONE);
            family(Items.QUARTZ_BLOCK, null, null, Items.CHISELED_QUARTZ_BLOCK);
        }

        Chisel() {
            super("chisel_bench", s -> FAMILIES.containsKey(s.getItem()), Chisel::modifier);
        }

        private static void family(Item base, @Nullable Item cracked, @Nullable Item mossy, @Nullable Item chiseled) {
            FAMILIES.put(base, new Item[]{cracked == null ? Items.AIR : cracked, mossy == null ? Items.AIR : mossy,
                    chiseled == null ? Items.AIR : chiseled});
        }

        private static boolean modifier(ItemStack stack) {
            return stack.is(Items.VINE) || stack.is(Items.MOSS_BLOCK) || stack.is(Items.IRON_NUGGET);
        }

        private static int kind(ItemStack modifier) {
            return modifier.isEmpty() ? 0 : modifier.is(Items.IRON_NUGGET) ? 2 : 1;
        }

        @Override
        public @Nullable Craft match(ServerLevel level, BlockPos pos, Container c) {
            Item[] family = FAMILIES.get(c.getItem(0).getItem());
            ItemStack modifier = c.getItem(1);
            if (family == null || !(modifier.isEmpty() || modifier(modifier))) {
                return null;
            }
            Item result = family[kind(modifier)];
            return result.equals(Items.AIR) ? null : Craft.of(new int[]{1, modifier.isEmpty() ? 0 : 1}, new ItemStack(result), 200, 0);
        }

        @Override
        public List<Example> examples() {
            List<Example> list = new ArrayList<>();
            List<ItemStack> moss = List.of(new ItemStack(Items.VINE), new ItemStack(Items.MOSS_BLOCK));
            FAMILIES.forEach((base, results) -> {
                List<ItemStack> in = List.of(new ItemStack(base));
                if (!results[0].equals(Items.AIR)) {
                    list.add(example(new ItemStack(results[0]), 200, in));
                }
                if (!results[1].equals(Items.AIR)) {
                    list.add(example(new ItemStack(results[1]), 200, in, moss));
                }
                if (!results[2].equals(Items.AIR)) {
                    list.add(example(new ItemStack(results[2]), 200, in, List.of(new ItemStack(Items.IRON_NUGGET))));
                }
            });
            return list;
        }
    }

    /** A leather piece sets the shape; {@code chains} chains make its chainmail twin. */
    private record Chainmail(Item pattern, Item result, int chains) {
        static List<Chainmail> values() {
            return List.of(new Chainmail(Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, 5),
                    new Chainmail(Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, 8),
                    new Chainmail(Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, 7),
                    new Chainmail(Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, 4));
        }

        static @Nullable Chainmail of(ItemStack stack) {
            for (Chainmail piece : values()) {
                if (stack.is(piece.pattern)) {
                    return piece;
                }
            }
            return null;
        }
    }

    /** One seed and one bone meal grow {@code count} of {@code crop}. */
    private record Greenhouse(Item seed, Item crop, int count) {
        static List<Greenhouse> values() {
            return List.of(new Greenhouse(Items.WHEAT_SEEDS, Items.WHEAT, 2), new Greenhouse(Items.BEETROOT_SEEDS, Items.BEETROOT, 2),
                    new Greenhouse(Items.CARROT, Items.CARROT, 3), new Greenhouse(Items.POTATO, Items.POTATO, 3),
                    new Greenhouse(Items.PUMPKIN_SEEDS, Items.PUMPKIN, 1), new Greenhouse(Items.MELON_SEEDS, Items.MELON_SLICE, 5));
        }

        ItemStack result() {
            return new ItemStack(crop, count);
        }

        static @Nullable Greenhouse of(ItemStack stack) {
            for (Greenhouse crop : values()) {
                if (stack.is(crop.seed)) {
                    return crop;
                }
            }
            return null;
        }
    }
}
