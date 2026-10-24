package com.vnap.village.post;

import com.vnap.Nbt;
import com.vnap.block.LifeBlocks;
import com.vnap.block.town.MailboxBlock;
import com.vnap.sound.GolemSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The villagers' post to a player: letters (Letters) wait here for the player's mailbox (MailboxBlock, its flag up
 * while there is mail) and are taken out by hand. Without a mailbox the player is told there is mail waiting.
 */
public final class Post extends SavedData {
	private static final String NAME = "villager_life_post";
	/** Letters a player can have waiting; the oldest go first. */
	static final int CAP = 16;
	private final Map<UUID, List<ItemStack>> waiting = new HashMap<>();
	private final Map<UUID, Box> boxes = new HashMap<>();

	record Box(ResourceKey<Level> dimension, BlockPos pos) {
	}

	public static Post get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, Post::new, Post::load, Post::save);
	}

	/** A letter for {@code player}: into its waiting mail, the mailbox's flag up and a rattle there. */
	public static void send(ServerLevel level, UUID player, ItemStack letter) {
		Post post = get(level.getServer());
		List<ItemStack> mail = post.waiting.computeIfAbsent(player, ignored -> new ArrayList<>());
		mail.add(letter);
		while (mail.size() > CAP) mail.remove(0);
		post.setDirty();
		Box box = post.boxes.get(player);
		ServerLevel at = box == null ? null : level.getServer().getLevel(box.dimension());
		if (box != null && at != null && at.isLoaded(box.pos()) && at.getBlockState(box.pos()).getBlock() instanceof MailboxBlock) {
			flag(at, box.pos(), true);
			at.playSound(null, box.pos(), GolemSounds.MAILBOX, SoundSource.BLOCKS, 1.0F, 1.0F);
			return;
		}
		ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
		if (online != null) {
			com.vnap.Mc.say(online, Component.translatable(box == null ? "post.villager_life.no_box" : "post.villager_life.waiting"), false);
		}
	}

	/** The mailbox is this player's from now on (its last one is forgotten); waiting mail raises its flag. */
	public static void own(ServerLevel level, Player player, BlockPos pos) {
		Post post = get(level.getServer());
		post.boxes.put(player.getUUID(), new Box(level.dimension(), pos.immutable()));
		post.setDirty();
		if (!post.waiting.getOrDefault(player.getUUID(), List.of()).isEmpty()) flag(level, pos, true);
	}

	/** The owner takes the mail out: every waiting letter into its inventory. False when there was none. */
	public static boolean collect(ServerLevel level, Player player, BlockPos pos) {
		Post post = get(level.getServer());
		Box box = post.boxes.get(player.getUUID());
		if (box == null || !box.pos().equals(pos) || !box.dimension().equals(level.dimension())) return false;
		List<ItemStack> mail = post.waiting.remove(player.getUUID());
		flag(level, pos, false);
		post.setDirty();
		if (mail == null || mail.isEmpty()) return false;
		for (ItemStack letter : mail) {
			if (!player.getInventory().add(letter)) com.vnap.Mc.toss(player, letter);
		}
		level.playSound(null, pos, GolemSounds.LETTER, SoundSource.BLOCKS, 1.0F, 1.0F);
		return true;
	}

	public static boolean owns(ServerLevel level, Player player, BlockPos pos) {
		Box box = get(level.getServer()).boxes.get(player.getUUID());
		return box != null && box.pos().equals(pos) && box.dimension().equals(level.dimension());
	}

	public static int waiting(ServerLevel level, UUID player) {
		return get(level.getServer()).waiting.getOrDefault(player, List.of()).size();
	}

	private static void flag(ServerLevel level, BlockPos pos, boolean up) {
		BlockState state = level.getBlockState(pos);
		if (state.is(LifeBlocks.MAILBOX) && state.getValue(MailboxBlock.FLAG) != up) level.setBlock(pos, state.setValue(MailboxBlock.FLAG, up), Block.UPDATE_ALL);
	}

	private static Post load(CompoundTag tag, HolderLookup.Provider registries) {
		Post post = new Post();
		for (Tag entry : Nbt.getList(tag, "Players", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag) entry;
			if (!Nbt.hasUUID(one, "Player")) continue;
			UUID player = Nbt.getUUID(one, "Player");
			ResourceLocation dimension = ResourceLocation.tryParse(Nbt.getString(one, "Dim"));
			if (dimension != null && one.contains("Box")) {
				post.boxes.put(player, new Box(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(Nbt.getLong(one, "Box"))));
			}
			List<ItemStack> mail = new ArrayList<>();
			for (Tag letter : Nbt.getList(one, "Mail", Tag.TAG_COMPOUND)) com.vnap.Mc.parseStack(registries, letter).ifPresent(mail::add);
			if (!mail.isEmpty()) post.waiting.put(player, mail);
		}
		return post;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		java.util.Set<UUID> players = new java.util.HashSet<>(boxes.keySet());
		players.addAll(waiting.keySet());
		for (UUID player : players) {
			CompoundTag one = new CompoundTag();
			Nbt.putUUID(one, "Player", player);
			Box box = boxes.get(player);
			if (box != null) {
				one.putString("Dim", com.vnap.Mc.id(box.dimension()).toString());
				one.putLong("Box", box.pos().asLong());
			}
			ListTag mail = new ListTag();
			for (ItemStack letter : waiting.getOrDefault(player, List.of())) mail.add(com.vnap.Mc.saveStack(letter, registries));
			one.put("Mail", mail);
			list.add(one);
		}
		tag.put("Players", list);
		return tag;
	}

	static @Nullable Box box(ServerLevel level, UUID player) {
		return get(level.getServer()).boxes.get(player);
	}
}
