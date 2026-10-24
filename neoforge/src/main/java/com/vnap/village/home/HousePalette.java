package com.vnap.village.home;

import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * House plans are written in the plains palette (oak, cobblestone, white bed). A biome palette swaps blocks by
 * id when the schematic is loaded, keeping every property the new block also has (facing, half, axis ...).
 */
public final class HousePalette {
    public static final String PLAINS = "plains";
    public static final List<String> ALL = List.of(PLAINS, "taiga", "desert", "savanna", "snowy");
    /** The second age's palette of a biome (a town under a risen spire restyles its houses: HouseLevels.restyle). */
    public static final String FINE = "_fine";
    public static final List<String> FINES = ALL.stream().map(palette -> palette + FINE).toList();

    private static final Map<String, String> DESERT = Map.ofEntries(
            Map.entry("oak_planks", "smooth_sandstone"),
            Map.entry("oak_log", "cut_sandstone"),
            Map.entry("oak_stairs", "smooth_sandstone_stairs"),
            Map.entry("oak_slab", "smooth_sandstone_slab"),
            Map.entry("oak_door", "jungle_door"),
            Map.entry("oak_fence", "jungle_fence"),
            Map.entry("cobblestone", "sandstone"),
            Map.entry("white_bed", "yellow_bed"),
            Map.entry("red_carpet", "orange_carpet"));
    private static final Map<String, String> SAVANNA = Map.ofEntries(
            Map.entry("cobblestone", "mud_bricks"),
            Map.entry("white_bed", "orange_bed"),
            Map.entry("red_carpet", "orange_carpet"));
    private static final Map<String, String> SNOWY = Map.ofEntries(
            Map.entry("cobblestone", "stone_bricks"),
            Map.entry("white_bed", "light_blue_bed"),
            Map.entry("red_carpet", "light_blue_carpet"));
    private static final Map<String, String> TAIGA = Map.ofEntries(
            Map.entry("cobblestone", "mossy_cobblestone"),
            Map.entry("white_bed", "brown_bed"),
            Map.entry("red_carpet", "brown_carpet"));

    // The second age: a dressed stone plinth, dark beams, a roof of slate, copper or red stone; beds, doors and
    // what is inside keep theirs (HouseLevels.restyle sets none of them anew). Over the biome's palette.
    private static final Map<String, String> PLAINS_FINE = fine("stone_bricks", "dark_oak_log", "birch_planks", "deepslate_tile", "dark_oak");
    private static final Map<String, String> TAIGA_FINE = fine("mossy_stone_bricks", "dark_oak_log", "spruce_planks", "deepslate_brick", "dark_oak");
    private static final Map<String, String> SNOWY_FINE = fine("polished_andesite", "stripped_spruce_log", "spruce_planks", "waxed_oxidized_cut_copper", "spruce");
    private static final Map<String, String> SAVANNA_FINE = fine("polished_granite", "dark_oak_log", "acacia_planks", "waxed_weathered_cut_copper", "dark_oak");
    private static final Map<String, String> DESERT_FINE = Map.ofEntries(
            Map.entry("cobblestone", "cut_sandstone"),
            Map.entry("oak_log", "chiseled_sandstone"),
            Map.entry("oak_stairs", "smooth_red_sandstone_stairs"),
            Map.entry("spruce_stairs", "smooth_red_sandstone_stairs"),
            Map.entry("oak_slab", "smooth_red_sandstone_slab"),
            Map.entry("spruce_slab", "smooth_red_sandstone_slab"));

    private static Map<String, String> fine(String plinth, String beam, String planks, String roof, String trim) {
        return Map.ofEntries(
                Map.entry("cobblestone", plinth),
                Map.entry("oak_log", beam),
                Map.entry("oak_planks", planks),
                Map.entry("oak_stairs", roof + "_stairs"),
                Map.entry("spruce_stairs", roof + "_stairs"),
                Map.entry("oak_slab", roof + "_slab"),
                Map.entry("spruce_slab", roof + "_slab"),
                Map.entry("oak_fence", trim + "_fence"),
                Map.entry("oak_trapdoor", trim + "_trapdoor"));
    }

    public static boolean fine(String palette) {
        return palette.endsWith(FINE);
    }

    /** The second age's palette of {@code palette}'s biome. */
    public static String finer(String palette) {
        return fine(palette) ? palette : palette + FINE;
    }

    private HousePalette() {
    }

    public static String forBiome(Holder<Biome> biome) {
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT) || biome.is(BiomeTags.HAS_DESERT_PYRAMID)) return "desert";
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA) || biome.is(BiomeTags.IS_SAVANNA)) return "savanna";
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY) || biome.is(BiomeTags.HAS_IGLOO)) return "snowy";
        if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA) || biome.is(BiomeTags.IS_TAIGA)) return "taiga";
        return PLAINS;
    }

    /** {@code house/<plan>@<palette>} names the plan in that palette; a copy of a generated village's house keeps its own blocks till the second age. */
    public static String schematicName(String plan, String palette) {
        // in the second age it is restyled like any other (HouseLevels.restyle)
        return plan.startsWith(HouseLevels.WORLD) && !fine(palette) ? "house/" + plan : "house/" + plan + "@" + palette;
    }

    public static SchematicSpec apply(SchematicSpec plan, String palette) {
        if (palette.equals(PLAINS)) {
            return plan;
        }
        List<SchematicSpec.PlacedBlock> blocks = new ArrayList<>(plan.blocks.size());
        for (SchematicSpec.PlacedBlock block : plan.blocks) {
            blocks.add(new SchematicSpec.PlacedBlock(block.rel(), swap(block.state(), palette)));
        }
        return new SchematicSpec(plan.sizeX, plan.sizeY, plan.sizeZ, plan.workerSlots, blocks, plan.focal, plan.focalMarked);
    }

    static BlockState swap(BlockState state, String palette) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (fine(palette)) {
            String biome = palette.substring(0, palette.length() - FINE.length());
            String finer = switch (biome) {
                case "desert" -> DESERT_FINE.get(path);
                case "savanna" -> SAVANNA_FINE.get(path);
                case "snowy" -> SNOWY_FINE.get(path);
                case "taiga" -> TAIGA_FINE.get(path);
                default -> PLAINS_FINE.get(path);
            };
            return finer != null ? paint(state, finer) : swap(state, biome);
        }
        String to = switch (palette) {
            case "desert" -> DESERT.get(path);
            case "savanna" -> SAVANNA.getOrDefault(path, wood(path, "acacia"));
            case "snowy" -> SNOWY.getOrDefault(path, wood(path, "spruce"));
            case "taiga" -> TAIGA.getOrDefault(path, wood(path, "spruce"));
            default -> null;
        };
        return to == null ? state : paint(state, to);
    }

    private static BlockState paint(BlockState state, String to) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(to));
        if (block.equals(Blocks.AIR)) {
            return state;
        }
        BlockState swapped = block.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            swapped = copy(state, swapped, property);
        }
        return swapped;
    }

    private static String wood(String path, String wood) {
        return path.startsWith("oak_") || path.startsWith("stripped_oak_") ? path.replace("oak_", wood + "_") : path;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        Property<?> same = to.getBlock().getStateDefinition().getProperty(property.getName());
        if (same == null) {
            return to;
        }
        return set(to, same, property.getName(from.getValue(property)));
    }

    private static <V extends Comparable<V>> BlockState set(BlockState state, Property<V> property, String value) {
        Optional<V> parsed = property.getValue(value);
        return parsed.isPresent() ? state.setValue(property, parsed.get()) : state;
    }
}
