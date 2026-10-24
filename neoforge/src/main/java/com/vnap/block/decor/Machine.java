package com.vnap.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/** A slow furnace-like recipe book: up to three inputs, one output, optional fuel. */
public interface Machine {
    String id();

    int inputs();

    boolean accepts(int slot, ItemStack stack);

    /** What the inputs make right now, or null. Never changes the container. */
    @Nullable Craft match(ServerLevel level, BlockPos pos, Container container);

    /** Fuel units one item of {@code stack} adds when burnt from {@link #fuelSlot()}. */
    default int fuel(ItemStack stack) {
        return 0;
    }

    default int fuelSlot() {
        return -1;
    }

    List<Example> examples();

    /**
     * One run: {@code use[i]} items leave input i when it ends, {@code make} builds the result then.
     * {@code preview} is the known result, or null when it is rolled at the end (it must still fit then).
     */
    final class Craft {
        private final int[] use;
        final @Nullable ItemStack preview;
        final Function<ServerLevel, ItemStack> make;
        final int ticks;
        final int fuel;

        public Craft(int[] use, @Nullable ItemStack preview, Function<ServerLevel, ItemStack> make, int ticks, int fuel) {
            this.use = use.clone();
            this.preview = preview;
            this.make = make;
            this.ticks = ticks;
            this.fuel = fuel;
        }

        public static Craft of(int[] use, ItemStack result, int ticks, int fuel) {
            ItemStack fixed = result.copy();
            return new Craft(use, fixed, level -> fixed.copy(), ticks, fuel);
        }

        int use(int slot) {
            return slot < use.length ? use[slot] : 0;
        }
    }

    /** A recipe shown in JEI/EMI: alternatives per input, the result and the time. */
    record Example(List<List<ItemStack>> inputs, ItemStack output, int ticks) {
    }
}
