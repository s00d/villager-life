package com.vnap.block.decor;

import com.vnap.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Progress and stored fuel of one machine; ticks it one game tick at a time. */
public final class MachineWork implements ContainerData {
    int progress;
    int total;
    int fuel;

    /** Advances one tick; true when the container changed (fuel burnt or a craft finished). */
    boolean tick(ServerLevel level, BlockPos pos, Machine machine, Container container) {
        Machine.Craft craft = machine.match(level, pos, container);
        int out = machine.inputs();
        if (craft == null || !fits(container.getItem(out), craft.preview)) {
            progress = 0;
            total = 0;
            return false;
        }
        boolean changed = false;
        if (craft.fuel > fuel) {
            ItemStack burn = container.getItem(machine.fuelSlot());
            int value = machine.fuel(burn);
            if (value <= 0) {
                progress = 0;
                total = 0;
                return false;
            }
            ItemStack rest = com.vnap.Mc.remainder(burn);
            burn.shrink(1);
            if (burn.isEmpty() && !rest.isEmpty()) {
                container.setItem(machine.fuelSlot(), rest);
            }
            fuel += value;
            changed = true;
        }
        total = craft.ticks;
        if (++progress < total) {
            // a soft sound of the work now and then (quiet, short range: a workshop full of them is no din)
            if (progress % WORK_SOUND_EVERY == 1) sound(level, pos, machine, false);
            return changed;
        }
        ItemStack made = craft.make.apply(level);
        ItemStack held = container.getItem(out);
        if (!made.isEmpty() && !fits(held, made)) {
            progress = Math.max(0, total - 20);
            return changed;
        }
        for (int slot = 0; slot < out; slot++) {
            container.getItem(slot).shrink(craft.use(slot));
        }
        if (held.isEmpty()) {
            container.setItem(out, made);
        } else {
            held.grow(made.getCount());
        }
        fuel -= craft.fuel;
        progress = 0;
        container.setChanged();
        sound(level, pos, machine, true);
        return true;
    }

    private static final int WORK_SOUND_EVERY = 60;

    private static void sound(ServerLevel level, BlockPos pos, Machine machine, boolean done) {
        net.minecraft.sounds.SoundEvent sound = com.vnap.sound.GolemSounds.machine(machine.id(), done);
        if (sound != null) level.playSound(null, pos, sound, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.95F + level.getRandom().nextFloat() * 0.1F);
    }

    /** A rolled result (null) is checked again when it is made. */
    static boolean fits(ItemStack held, @Nullable ItemStack result) {
        if (result == null || held.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(held, result) && held.getCount() + result.getCount() <= held.getMaxStackSize();
    }

    void save(CompoundTag tag) {
        tag.putInt("progress", progress);
        tag.putInt("total", total);
        tag.putInt("fuel", fuel);
    }

    void load(CompoundTag tag) {
        progress = Nbt.getInt(tag, "progress");
        total = Nbt.getInt(tag, "total");
        fuel = Nbt.getInt(tag, "fuel");
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case 0 -> progress;
            case 1 -> total;
            default -> fuel;
        };
    }

    @Override
    public void set(int index, int value) {
        switch (index) {
            case 0 -> progress = value;
            case 1 -> total = value;
            default -> fuel = value;
        }
    }

    @Override
    public int getCount() {
        return 3;
    }
}
