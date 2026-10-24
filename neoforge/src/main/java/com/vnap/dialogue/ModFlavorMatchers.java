package com.vnap.dialogue;

import org.jspecify.annotations.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Soft-compat matchers for optional CreateAERO mods.
 * No hard imports of foreign mod classes — only namespace/path string checks.
 * Missing mods simply never produce a match.
 */
public final class ModFlavorMatchers {
    private ModFlavorMatchers() {}

    private static final Set<String> CREATE_NS = Set.of(
            "create", "createaddition", "create_connected", "create_new_age",
            "createoreexcavation", "createdieselgenerators", "create_radar",
            "create_jetpack", "create_sa", "railways", "createbigcannons",
            "cbc_additional_guns", "cbcmoreshells");

    private static final List<String> CREATE_MACHINE_PATHS = List.of(
            "cogwheel", "depot", "basin", "press", "mixer", "fan", "belt", "pipe",
            "tank", "chute", "millstone", "crusher", "deployer", "funnel", "tunnel",
            "gearbox", "clutch", "gearshift", "sequenced", "mechanical", "encased",
            "pump", "valve", "spout", "hose", "smart_chute", "weighted_ejector",
            "rope_pulley", "elevator", "clockwork", "speedometer", "stressometer",
            "rotation_speed_controller", "brass", "andesite_casing", "brass_casing",
            "copper_casing", "steam", "boiler", "furnace_engine", "flywheel");

    private static final Set<String> CBC_NS = Set.of(
            "createbigcannons", "cbc_additional_guns", "cbcmoreshells", "create_radar");

    private static final Set<String> TACZ_NS = Set.of("tacz", "lrtactical", "timeless_and_classic");

    private static final Set<String> MEK_NS = Set.of("mekanism", "mekanismgenerators", "mekanismtools", "mekanismadditions");

    private static final Set<String> CN_NS = Set.of("createnuclear");

    private static final Set<String> TFMG_NS = Set.of("tfmg", "bits_n_bobs");

    private static final Set<String> AERO_NS = Set.of(
            "sable", "aeronautics", "aeroworks", "create_jetpack", "vs_eureka", "valkyrienskies");

    private static final Set<String> FD_NS = Set.of("farmersdelight", "farmersrespite", "culturaldelights");

    private static final Set<String> ADASTRA_NS = Set.of("ad_astra", "ad_astra_giselle_addon");

    private static final Set<String> PACK_NS = Set.of(
            "sophisticatedbackpacks", "travelersbackpack", "toms_storage", "sophisticatedstorage");

    private static final Set<String> WAY_NS = Set.of("waystones");

    private static final Set<String> CC_NS = Set.of("computercraft");

    private static final Set<String> MAGIC_NS = Set.of("occultism", "botania", "manaweave", "ars_nouveau", "irons_spellbooks");

    private static final Set<String> DIM_NS = Set.of("aether", "divinerpg", "aoa3", "twilightforest");

    private static final Set<String> QOL_NS = Set.of("gravestone", "supplementaries", "comforts", "dye_depot");

    private static final Set<String> RAD_NS = Set.of("nuclear_radiation", "createnuclear", "mekanism");

    /** Handbook titles for curated icon items (exact namespace:path). */
    private static final java.util.Map<String, String> ICON_HOLD = java.util.Map.ofEntries(
            java.util.Map.entry("create:extendo_grip", "Hold an Extendo Grip"),
            java.util.Map.entry("create:potato_cannon", "Hold a Potato Cannon"),
            java.util.Map.entry("create:brass_hand", "Hold a Brass Hand"),
            java.util.Map.entry("create:schematic", "Hold a Drafting Table Schematic"),
            java.util.Map.entry("create:blaze_cake", "Hold a Blaze Cake"),
            java.util.Map.entry("create:honeyed_apple", "Hold a Honeyed Apple"),
            java.util.Map.entry("create:diving_helmet", "Hold a Divers Helmet"),
            java.util.Map.entry("create:copper_backtank", "Hold a Copper Backtank"),
            java.util.Map.entry("create:schedule", "Hold a Train Schedule"),
            java.util.Map.entry("create:wrench", "Hold a Create Wrench"),
            java.util.Map.entry("createbigcannons:solid_shot", "Hold a CBC Shell"),
            java.util.Map.entry("createbigcannons:ap_shot", "Hold a CBC Shell"),
            java.util.Map.entry("createbigcannons:he_shell", "Hold a CBC Shell"),
            java.util.Map.entry("createbigcannons:diamond_grapeshot", "Hold a Diamond Cannon Shell"),
            java.util.Map.entry("create_radar:monitor", "Hold a Radar Dish Part"),
            java.util.Map.entry("create_jetpack:jetpack", "Hold a Jetpack"),
            java.util.Map.entry("nuclear_radiation:geiger_counter", "Hold a Geiger Counter"),
            java.util.Map.entry("nuclear_radiation:anti_rad_injection", "Hold Anti-Rad Injection"),
            java.util.Map.entry("createnuclear:yellowcake", "Hold Yellowcake"),
            java.util.Map.entry("createnuclear:uranium_rod", "Hold a Uranium Rod"),
            java.util.Map.entry("mekanism:ingot_osmium", "Hold Osmium Ingot"),
            java.util.Map.entry("mekanism:alloy_atomic", "Hold Atomic Alloy"),
            java.util.Map.entry("mekanismgenerators:heat_generator", "Hold a Heat Generator"),
            java.util.Map.entry("tfmg:steel_ingot", "Hold Steel from TFMG"),
            java.util.Map.entry("tfmg:crude_oil_bucket", "Hold Crude Oil Bucket"),
            java.util.Map.entry("waystones:warp_scroll", "Hold a Waystone Scroll"),
            java.util.Map.entry("naturescompass:naturescompass", "Hold Nature's Compass"),
            java.util.Map.entry("explorerscompass:explorerscompass", "Hold Explorer's Compass"),
            java.util.Map.entry("sophisticatedbackpacks:backpack", "Hold a Backpack"),
            java.util.Map.entry("travelersbackpack:standard", "Hold Traveler's Backpack"),
            java.util.Map.entry("farmersdelight:roast_chicken_block", "Hold a Cooking Pot Meal"),
            java.util.Map.entry("occultism:dictionary_of_spirits", "Hold Occultism Dictionary"),
            java.util.Map.entry("computercraft:turtle_normal", "Hold a Computer Turtle"),
            java.util.Map.entry("ad_astra:oxygen_tank", "Hold Ad Astra Oxygen Gear"),
            java.util.Map.entry("patchouli:guide_book", "Hold Guide Book / Patchouli"),
            java.util.Map.entry("dye_depot:maroon_dye", "Hold Dye Depot Dye")
    );

    public static @Nullable String placeTitle(Block block) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        String ns = key.getNamespace();
        String path = key.getPath();
        if ("minecraft".equals(ns)) return null;

        if (nsContains(ns, CREATE_NS) || ns.startsWith("create")) {
            if (pathContains(path, "cart_assembler", "train_controls", "controls", "schedule")) {
                return "Place Create Contraption Anchor";
            }
            if (pathContains(path, "blaze_burner")) return "See a Blaze Burner";
            if (nsContains(ns, Set.of("createdieselgenerators")) || pathContains(path, "diesel", "fuel", "oil_barrel")) {
                return "Diesel or Fuel Smell";
            }
            if (nsContains(ns, Set.of("createoreexcavation")) || pathContains(path, "drill", "vein_finder")) {
                return "Ore Excavation Drill";
            }
            if (pathContainsAny(path, CREATE_MACHINE_PATHS)) return "Place Create Machinery";
        }
        if (nsContains(ns, CBC_NS) && pathContains(path, "cannon", "breech", "barrel", "mount")) {
            return "Place a Big Cannon";
        }
        if (nsContains(ns, TACZ_NS) && pathContains(path, "workbench", "gun_smith", "ammo")) {
            return "Place Ammo or Gun Bench";
        }
        if (nsContains(ns, MEK_NS)) {
            if (pathContains(path, "fission", "fusion", "reactor", "sps", "boiler")) {
                return "Place a Mekanism Reactor Part";
            }
            if (pathContains(path, "factory", "machine", "casing", "cable", "pipe", "generator", "tank", "bin", "energy")) {
                return "Place Mekanism Machine";
            }
        }
        if (nsContains(ns, CN_NS) && pathContains(path, "reactor", "cooler", "enriching")) {
            return "Place Create Nuclear Reactor Parts";
        }
        if (nsContains(ns, TFMG_NS)) {
            if (pathContains(path, "pumpjack", "oil", "distillation")) return "Oil or Pumpjack Nearby";
            return "Place TFMG Factory Block";
        }
        if (nsContains(ns, AERO_NS) && !ns.equals("create_jetpack")) {
            return "Place Aeronautics Parts";
        }
        if ((nsEquals(ns, "railways") || (nsEquals(ns, "create") && pathContains(path, "track")))
                && pathContains(path, "track", "rail")) {
            return "Place Train Tracks";
        }
        if (nsContains(ns, FD_NS) && pathContains(path, "cooking_pot", "skillet", "stove", "cutting_board")) {
            return "Place a Cooking Pot or Skillet";
        }
        if (nsContains(ns, ADASTRA_NS) && pathContains(path, "rocket", "nasa", "oxygen", "fuel", "launch", "machine", "cable")) {
            return "Place Space Gear";
        }
        if (nsContains(ns, WAY_NS) && pathContains(path, "waystone", "portstone", "sharestone")) {
            return "Place a Waystone";
        }
        if (nsContains(ns, CC_NS) && pathContains(path, "computer", "turtle", "modem", "monitor", "speaker", "printer")) {
            return "Place a Computer";
        }
        if (nsContains(ns, MAGIC_NS)) {
            if (pathContains(path, "bowl", "chalk", "sacrificial", "golden_sacrificial")) return "Occultism Ritual Gear";
            if (pathContains(path, "mystical_flower", "floating", "petal")) return "Botania Flowers Nearby";
            if (pathContains(path, "rune", "mana", "weave")) return "Manaweave or Runes";
        }
        if (nsContains(ns, DIM_NS)) {
            if (pathContains(path, "portal", "aether")) return "Aether Portal Frame";
            if (pathContains(path, "trophy", "boss", "relic", "magic_map")) return "Twilight Forest Relic";
        }
        if (nsContains(ns, QOL_NS)) {
            if (pathContains(path, "gravestone", "grave")) return "Place a Gravestone";
            if (nsEquals(ns, "supplementaries") && pathContains(path, "present", "jar", "notice_board", "safe", "cage")) {
                return "Supplementaries Oddity";
            }
        }
        return null;
    }

    public static @Nullable String breakTitle(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String ns = key.getNamespace();
        String path = key.getPath();
        if ("minecraft".equals(ns)) return null;
        if ((nsContains(ns, CREATE_NS) || ns.startsWith("create")) && pathContainsAny(path, CREATE_MACHINE_PATHS)) {
            return "Break Create Machinery";
        }
        return null;
    }

    public static @Nullable String useBlockTitle(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String ns = key.getNamespace();
        String path = key.getPath();
        if ("minecraft".equals(ns)) return null;
        if (nsContains(ns, WAY_NS) && pathContains(path, "waystone", "portstone", "sharestone")) {
            return "Use a Waystone Nearby";
        }
        if (nsContains(ns, PACK_NS) && pathContains(path, "terminal", "inventory", "storage", "crafting_terminal")) {
            return "Open Tom's Storage Terminal";
        }
        if (nsContains(ns, TACZ_NS) && pathContains(path, "workbench", "gun_smith")) {
            return "Place Ammo or Gun Bench";
        }
        if (nsContains(ns, CBC_NS) && pathContains(path, "cannon", "breech", "loader")) {
            return "Load or Fire Artillery";
        }
        return null;
    }

    public static @Nullable String useItemTitle(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String ns = key.getNamespace();
        String path = key.getPath();
        if (nsContains(ns, FD_NS) && pathContains(path, "pie", "stew", "soup", "salad", "rice", "noodle",
                "bacon", "chicken", "beef", "mutton", "fish", "meal", "roast", "burger", "bread", "cabbage",
                "tomato", "onion", "dumpling", "pasta", "pancake", "pie_crust", "hot_cocoa", "apple_cider")) {
            return "Eat Farmer's Delight Food";
        }
        if (nsContains(ns, QOL_NS) && pathContains(path, "sleeping_bag")) {
            return "Comforts Sleeping Bag";
        }
        if (nsContains(ns, CBC_NS) && pathContains(path, "cannon", "powder", "cartridge", "shell", "shot")) {
            return "Load or Fire Artillery";
        }
        if (nsContains(ns, RAD_NS) && pathContains(path, "geiger")) {
            return "Geiger Counter Clicking";
        }
        return null;
    }

    public static @Nullable String holdTitle(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String id = key.toString();
        String icon = ICON_HOLD.get(id);
        if (icon != null) return icon;
        // Soft pattern fallbacks for guns / shells / similar
        String ns = key.getNamespace();
        String path = key.getPath();
        if (nsContains(ns, TACZ_NS) && (pathContains(path, "gun", "rifle", "pistol", "shotgun", "smg", "sniper")
                || path.contains("modern_kinetic"))) {
            return "Hold a Gun";
        }
        if (nsContains(ns, CBC_NS) && pathContains(path, "shell", "shot", "cartridge", "munition")) {
            return "Hold Cannon Munitions";
        }
        if ((nsContains(ns, MEK_NS) || nsContains(ns, CN_NS) || nsContains(ns, RAD_NS))
                && pathContains(path, "uranium", "yellowcake", "waste", "plutonium", "radioactive")) {
            return "Hold Uranium or Nuclear Waste";
        }
        if (nsContains(ns, RAD_NS) && pathContains(path, "geiger")) {
            return "Hold a Geiger Counter";
        }
        if ((nsContains(ns, CREATE_NS) || ns.startsWith("create")) && pathContains(path, "wrench")) {
            return "Hold a Create Wrench";
        }
        if (nsContains(ns, AERO_NS) && pathContains(path, "jetpack")) {
            return "Hold a Jetpack";
        }
        if (nsContains(ns, ADASTRA_NS) && pathContains(path, "oxygen", "fuel_bucket", "oil_bucket")) {
            return "Hold Oxygen or Fuel Bucket";
        }
        if (nsContains(ns, MAGIC_NS) && pathContains(path, "dictionary", "chalk", "bowl", "rune")) {
            if (pathContains(path, "dictionary")) return "Hold Occultism Dictionary";
            return "Occultism Ritual Gear";
        }
        if (nsContains(ns, DIM_NS) && pathContains(path, "trophy", "relic")) {
            return "Twilight Forest Relic";
        }
        if (pathContains(path, "boss_trophy", "trophy") && (nsContains(ns, Set.of("boss_checklist", "divinerpg", "aoa3"))
                || pathContains(path, "boss"))) {
            return "Hold Boss Trophy";
        }
        if (nsContains(ns, Set.of("void_metal", "enigmaticlegacy", "cataclysm")) && pathContains(path, "sword", "saber", "blade")) {
            return "Hold a Saber / Sword Mod";
        }
        if (nsEquals(ns, "dye_depot") && pathContains(path, "dye")) {
            return "Hold Dye Depot Dye";
        }
        if (nsEquals(ns, "patchouli") || pathContains(path, "guide_book", "guidebook", "lexicon")) {
            return "Hold Guide Book / Patchouli";
        }
        return null;
    }

    public static @Nullable String wearTitle(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        for (ItemStack stack : List.of(chest, head, player.getItemBySlot(EquipmentSlot.LEGS), player.getItemBySlot(EquipmentSlot.FEET))) {
            if (stack.isEmpty()) continue;
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String ns = key.getNamespace();
            String path = key.getPath();
            if (nsContains(ns, ADASTRA_NS) && pathContains(path, "space_suit", "netherite_space", "jet_suit")) {
                return "Wear a Space Suit";
            }
            if ((nsContains(ns, AERO_NS) || nsContains(ns, Set.of("create_jetpack"))) && pathContains(path, "jetpack")) {
                return "Wear a Jetpack";
            }
            if (nsContains(ns, PACK_NS) && pathContains(path, "backpack")) {
                return "Wear a Huge Backpack";
            }
        }
        // Elytra + jetpack
        ItemStack chestItem = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chestItem.isEmpty()) {
            String path = BuiltInRegistries.ITEM.getKey(chestItem.getItem()).getPath();
            if (path.equals("elytra") && holdTitle(player.getMainHandItem()) != null
                    && "Hold a Jetpack".equals(holdTitle(player.getOffhandItem()))) {
                return "Hold an Elytra with Jetpack";
            }
            if (path.equals("elytra") && (pathContains(BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).getPath(), "jetpack")
                    || pathContains(BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath(), "jetpack"))) {
                return "Hold an Elytra with Jetpack";
            }
        }
        return null;
    }

    public static @Nullable String weaponAttackTitle(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String ns = key.getNamespace();
        String path = key.getPath();
        if (nsContains(ns, TACZ_NS) || pathContains(path, "gun", "rifle", "pistol", "shotgun")) {
            return "Attacked with a Gun";
        }
        return null;
    }

    public static @Nullable String nearbyEntityTitle(Entity entity) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String ns = key.getNamespace();
        String path = key.getPath();
        if ("minecraft".equals(ns)) return null;
        if (nsContains(ns, CC_NS) && pathContains(path, "turtle")) return "Turtle Wandering in Village";
        if (nsContains(ns, DIM_NS) && entity instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null) {
            return "See a DivineRPG or AoA Mob";
        }
        if (nsContains(ns, Set.of("divinerpg", "aoa3"))) return "See a DivineRPG or AoA Mob";
        if ((nsContains(ns, CREATE_NS) || ns.startsWith("create") || nsEquals(ns, "railways"))
                && pathContains(path, "train", "carriage", "contraption", "seat")) {
            return "See a Train Pass";
        }
        if (nsContains(ns, AERO_NS) || pathContains(path, "airplane", "airship", "plane")) {
            return "See a Flying Contraption";
        }
        return null;
    }

    public static @Nullable String nearBlockTitle(String ns, String path) {
        if ((nsContains(ns, CREATE_NS) || ns.startsWith("create")) && pathContains(path, "blaze_burner")) {
            return "See a Blaze Burner";
        }
        if (nsContains(ns, CN_NS) && pathContains(path, "enriching")) {
            return "See Enriching Fire";
        }
        if (nsContains(ns, MAGIC_NS) && pathContains(path, "mystical_flower", "floating")) {
            return "Botania Flowers Nearby";
        }
        if (nsContains(ns, TFMG_NS) && pathContains(path, "pumpjack", "oil")) {
            return "Oil or Pumpjack Nearby";
        }
        return null;
    }

    public static boolean isIgniter(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.equals("flint_and_steel") || path.equals("fire_charge");
    }

    public static boolean isHostileSpawnEgg(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = key.getPath();
        if (!path.endsWith("_spawn_egg") && !path.contains("spawn_egg")) return false;
        return pathContains(path,
                "zombie", "skeleton", "creeper", "spider", "enderman", "witch", "pillager",
                "vindicator", "ravager", "phantom", "drowned", "husk", "stray", "warden",
                "blaze", "ghast", "piglin", "hoglin", "wither", "slime", "magma");
    }

    public static boolean looksLikeGun(ItemStack stack) {
        return weaponAttackTitle(stack) != null || "Hold a Gun".equals(holdTitle(stack));
    }

    public static boolean isFdFood(ItemStack stack) {
        return "Eat Farmer's Delight Food".equals(useItemTitle(stack));
    }

    public static boolean isCbcRelated(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return nsContains(key.getNamespace(), CBC_NS)
                || pathContains(key.getPath(), "shell", "shot", "cannon", "cartridge", "munition");
    }

    private static boolean nsContains(String ns, Set<String> set) {
        if (set.contains(ns)) return true;
        for (String s : set) {
            if (ns.equals(s) || ns.startsWith(s + "_")) return true;
        }
        return false;
    }

    private static boolean nsEquals(String ns, String expected) {
        return ns.equals(expected);
    }

    private static boolean pathContains(String path, String... parts) {
        String p = path.toLowerCase(Locale.ROOT);
        for (String part : parts) {
            if (p.contains(part.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean pathContainsAny(String path, List<String> parts) {
        String p = path.toLowerCase(Locale.ROOT);
        for (String part : parts) {
            if (p.contains(part.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
