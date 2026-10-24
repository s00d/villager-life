package com.vnap.village.workshop;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** The tool a profession is seen with and the workshop chest its chores fill. */
public final class ProfessionWork {
    private ProfessionWork() {
    }

    public static Item tool(String profession) {
        return switch (profession) {
            case "farmer" -> Items.IRON_HOE;
            case "butcher" -> Items.IRON_AXE;
            case "shepherd" -> Items.SHEARS;
            case "fisherman" -> Items.FISHING_ROD;
            case "fletcher" -> Items.BOW;
            case "librarian" -> Items.WRITABLE_BOOK;
            case "cartographer" -> Items.COMPASS;
            case "cleric" -> Items.FLINT_AND_STEEL;
            case "leatherworker" -> Items.LEATHER;
            case "mason" -> Items.IRON_PICKAXE;
            case "armorer" -> Items.IRON_CHESTPLATE;
            case "toolsmith" -> Items.IRON_SHOVEL;
            case "weaponsmith" -> Items.IRON_SWORD;
            default -> Items.AIR;
        };
    }

    public static @Nullable Container chest(ServerLevel level, WorkshopState state) {
        BlockPos pos = Workshops.chestPos(state);
        return level.getBlockEntity(pos) instanceof Container container ? container : null;
    }
}
