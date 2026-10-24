package com.vnap.compat;

import com.vnap.VillagerLife;
import com.vnap.block.DecorBlock;
import com.vnap.block.LifeBlocks;
import com.vnap.block.decor.Machine;
import com.vnap.block.decor.Machines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** What JEI and EMI show for the workshop: one category per machine and an info page per decor block. */
public final class WorkshopCatalog {
    private WorkshopCatalog() {
    }

    /** A machine with the block that runs it. */
    public record Station(Machine machine, ItemStack block, Component title) {
        public String id() {
            return "machine_" + machine.id();
        }
    }

    public static List<Station> stations() {
        List<Station> stations = new ArrayList<>();
        Machines.all().values().forEach(machine -> {
            Block block = machine.equals(Machines.FLETCHING) ? Blocks.FLETCHING_TABLE
                    : BuiltInRegistries.BLOCK.get(VillagerLife.id(machine.id()));
            if (!block.equals(Blocks.AIR)) {
                stations.add(new Station(machine, new ItemStack(block), block.getName()));
            }
        });
        return stations;
    }

    public static Component seconds(int ticks) {
        return Component.translatable("jei." + VillagerLife.MOD_ID + ".time", ticks / 20);
    }

    /** Decor blocks that work without a menu, each with its how-to line. */
    public static List<Info> decorInfo() {
        List<Info> list = new ArrayList<>();
        for (DecorBlock block : LifeBlocks.DECOR) {
            if (block.mechanic().machine() == null) {
                list.add(new Info(new ItemStack(block), Component.translatable("tooltip." + VillagerLife.MOD_ID + "." + block.name())));
            }
        }
        list.add(new Info(new ItemStack(Items.FLETCHING_TABLE), Component.translatable("handbook." + VillagerLife.MOD_ID
                + ".workshop.fletching")));
        return list;
    }

    public record Info(ItemStack stack, Component text) {
    }
}
