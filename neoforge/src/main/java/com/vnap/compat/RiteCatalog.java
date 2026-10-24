package com.vnap.compat;

import com.vnap.block.LifeBlocks;
import com.vnap.item.LifeItems;
import com.vnap.mood.MoodThresholds;
import com.vnap.mood.ScriptKind;
import com.vnap.mood.VillageRites;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** What recipe viewers and the handbook show about rites: shipped schematics, thresholds, the forge and its spoils. */
public final class RiteCatalog {
    public static final String NS = "villager_life";
    public static final int MAX_MATERIALS = 16;
    private static List<Rite> rites = List.of();

    public record Rite(ScriptKind kind, SchematicSpec schematic, ItemStack icon, List<ItemStack> materials) {
        public Component title() {
            return Component.translatable("rite." + NS + "." + kind.name().toLowerCase(Locale.ROOT));
        }

        public Component description() {
            return Component.translatable("rite." + NS + "." + kind.name().toLowerCase(Locale.ROOT) + ".desc");
        }

        public Component threshold() {
            return RiteCatalog.threshold(kind);
        }
    }

    public record Forge(SchematicSpec schematic, List<ItemStack> offerings, List<ItemStack> spoils) {
    }

    private RiteCatalog() {
    }

    public static synchronized List<Rite> rites() {
        if (rites.isEmpty()) {
            List<Rite> out = new ArrayList<>();
            for (ScriptKind kind : VillageRites.BUILDS) {
                SchematicSpec spec = SchematicSpec.loadBundled(VillageRites.schematicName(kind));
                out.add(new Rite(kind, spec, icon(kind), materials(spec)));
            }
            rites = List.copyOf(out);
        }
        return rites;
    }

    public static Forge forge() {
        Rite altar = rites().stream().filter(r -> r.kind() == ScriptKind.BUILD_ALTAR).findFirst().orElseThrow();
        return new Forge(altar.schematic(),
                List.of(new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.BELL)),
                List.of(new ItemStack(LifeItems.TOCSIN_CORE), new ItemStack(Items.IRON_INGOT, 10),
                        new ItemStack(Items.BELL), new ItemStack(LifeItems.BLESSING_STAFF)));
    }

    public static Component threshold(ScriptKind kind) {
        int at = Math.round(MoodThresholds.of(kind));
        boolean positive = VillageRites.positive(kind);
        String sign = at > 0 ? "+" + at : String.valueOf(at);
        return Component.translatable(positive ? "rite." + NS + ".needs_above" : "rite." + NS + ".needs_below", sign)
                .withStyle(positive ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED);
    }

    public static ItemStack icon(ScriptKind kind) {
        Item item = switch (kind) {
            case BUILD_FESTOON -> Items.YELLOW_BANNER;
            case BUILD_FESTIVAL, BUILD_PAVILION -> LifeBlocks.FESTIVAL_TOTEM.asItem();
            case BUILD_MONUMENT -> LifeBlocks.MONUMENT_PLAQUE.asItem();
            case BUILD_VIGIL -> Items.WHITE_CANDLE;
            case BUILD_MOURNING -> LifeBlocks.MOURNING_TOTEM.asItem();
            case BUILD_PYRE -> Items.SOUL_CAMPFIRE;
            case BUILD_ALTAR, SUMMON, BOSS_ACTIVE -> LifeBlocks.ALTAR_CORE.asItem();
            case NONE -> Items.BELL;
        };
        return new ItemStack(item.equals(Items.AIR) ? Items.BELL : item);
    }

    private static List<ItemStack> materials(SchematicSpec spec) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (SchematicSpec.PlacedBlock placed : spec.blocks) {
            Block block = placed.state().getBlock();
            if (block instanceof FlowerPotBlock pot) {
                counts.merge(Items.FLOWER_POT, 1, Integer::sum);
                block = pot.getPotted();
            }
            Item item = block.asItem();
            if (!item.equals(Items.AIR)) {
                counts.merge(item, 1, Integer::sum);
            }
        }
        List<ItemStack> out = new ArrayList<>();
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(MAX_MATERIALS)
                .forEach(e -> out.add(new ItemStack(e.getKey(), e.getValue())));
        return List.copyOf(out);
    }
}
