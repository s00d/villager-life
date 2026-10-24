package com.vnap.block.decor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What a decor block or machine does. {@link #handles} runs on both sides from synced block entity data,
 * so the client swings only when the server will act; {@link #use} runs on the server.
 */
public interface DecorMechanic {
    float[][] NO_DISPLAY = new float[0][];

    /** Item slots in the block entity. */
    default int slots() {
        return 0;
    }

    /** How many items of one kind the stock holds; 0 for no stock. */
    default int stockCap() {
        return 0;
    }

    default boolean stocks(ItemStack stack) {
        return false;
    }

    boolean handles(DecorBlockEntity be, Player player, ItemStack held);

    void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held);

    /** Whether {@link #tick} runs; it is called once a second. */
    default boolean ticks() {
        return false;
    }

    default void tick(ServerLevel level, DecorBlockEntity be) {
    }

    /** Comparator output, or -1 when the block gives none. */
    default int signal(DecorBlockEntity be) {
        return -1;
    }

    /** The FILL block state value: 0 is the plain decor look. */
    default int fill(DecorBlockEntity be) {
        return 0;
    }

    default @Nullable Machine machine() {
        return null;
    }

    /** Per slot {x, y, z, scale} in model pixels of the north-facing model, for the item renderer. */
    default float[][] display() {
        return NO_DISPLAY;
    }
}
