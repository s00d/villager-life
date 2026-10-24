package com.vnap.village.post;

import com.vnap.village.home.Neighbours;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;
import java.util.UUID;

/**
 * Letters the villagers write to a player, as signed books: the page a translated line (the reader's own language)
 * with the names in it, the title the writer's name (titles are plain text). Sent by the post (Post).
 */
public final class Letters {
	private static final String KEY = "letter.villager_life.";

	private Letters() {
	}

	/** A letter from {@code from}: its page {@code letter.villager_life.<kind>} with {@code args}. */
	public static ItemStack write(String from, String kind, Object... args) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		Component page = Component.translatable(KEY + kind, args).append("\n\n").append(Component.literal("— " + from));
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("✉ " + from), from, 0,
				List.of(Filterable.passThrough(page)), true));
		return book;
	}

	public static void to(ServerLevel level, UUID player, String from, String kind, Object... args) {
		Post.send(level, player, write(from, kind, args));
	}

	/** The same letter to every neighbour of the bell (the players who slept by it). */
	public static void toNeighbours(ServerLevel level, BlockPos bell, String from, String kind, Object... args) {
		for (UUID player : Neighbours.get(level.getServer()).of(level, bell)) to(level, player, from, kind, args);
	}
}
