package com.vnap.village.town;

import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Infants;
import com.vnap.village.life.Agenda;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Music for the town's gatherings, played the village's way: a villager carries a jukebox out to the spot, sets it
 * down and puts the town's record on (the feast's jig, the tavern's fireside tune, an evening waltz: fal
 * google/lyria-3.5, LifeItems discs); when the gathering is over somebody takes the record off and carries the
 * jukebox away again. Where the jukeboxes stand is kept (Network.jukeboxes): one left from a gathering the world cut
 * short is taken away all the same. A jukebox's music plays from it, fading with distance like any record.
 */
public final class Music {
	/** What each jukebox put out is for: while it holds, the record plays. In memory: a restart ends the music. */
	private static final Map<net.minecraft.core.GlobalPos, Predicate<ServerLevel>> PLAYING = new HashMap<>();

	private Music() {
	}

	/**
	 * Somebody near brings a jukebox to {@code spot} and puts {@code disc} on, for as long as {@code lasts} holds. False
	 * when nobody is free to or the spot is taken.
	 */
	static boolean play(ServerLevel level, Roads.Network network, BlockPos near, Item disc, Predicate<ServerLevel> lasts) {
		// the square is not flat everywhere: the floor a block up or down from where it was meant
		BlockPos spot = null;
		for (int dy : new int[]{0, 1, -1}) {
			BlockPos at = near.above(dy);
			if (level.getBlockState(at).canBeReplaced() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
				spot = at;
				break;
			}
		}
		if (spot == null) return false;
		Villager hand = hand(level, spot);
		if (hand == null) return false;
		BlockPos at = spot.immutable();
		network.jukeboxes.add(at);
		PLAYING.put(net.minecraft.core.GlobalPos.of(level.dimension(), at), lasts);
		Agenda.job(hand, "music_" + at.asLong(), at, Agenda.EVENT, new ItemStack(Items.JUKEBOX), (l, v) -> {
			// the gathering may be over before the jukebox got there
			if (!lasts.test(l) || !l.getBlockState(at).canBeReplaced()) return;
			l.setBlock(at, Blocks.JUKEBOX.defaultBlockState(), Block.UPDATE_ALL);
			if (l.getBlockEntity(at) instanceof JukeboxBlockEntity jukebox) jukebox.setTheItem(new ItemStack(disc));
			Speech.queue(v, "Puts a Record On", null, 20L);
		});
		Roads.get(level.getServer()).setDirty();
		return true;
	}

	/** Every 100 ticks for a ticking town: a jukebox whose gathering is over is taken away by hand (at once with nobody about). */
	static void tick(ServerLevel level, Roads.Network network) {
		for (BlockPos at : java.util.List.copyOf(network.jukeboxes)) {
			if (!level.isLoaded(at)) continue;
			Predicate<ServerLevel> lasts = PLAYING.get(net.minecraft.core.GlobalPos.of(level.dimension(), at));
			if (lasts != null && lasts.test(level)) continue;
			if (!level.getBlockState(at).is(Blocks.JUKEBOX)) {
				// never set down (the gathering was over first) or taken by someone else
				forget(level, network, at);
				continue;
			}
			PLAYING.remove(net.minecraft.core.GlobalPos.of(level.dimension(), at));
			Villager hand = hand(level, at);
			if (hand == null) {
				takeAway(level, at);
				forget(level, network, at);
				continue;
			}
			Agenda.job(hand, "music_off_" + at.asLong(), at, Agenda.EVENT, ItemStack.EMPTY, (l, v) -> {
				takeAway(l, at);
				forget(l, network, at);
			});
		}
	}

	private static void forget(ServerLevel level, Roads.Network network, BlockPos at) {
		network.jukeboxes.remove(at);
		PLAYING.remove(net.minecraft.core.GlobalPos.of(level.dimension(), at));
		Roads.get(level.getServer()).setDirty();
	}

	/** The record off (no disc dropped: it goes home with the villager) and the jukebox gone. */
	private static void takeAway(ServerLevel level, BlockPos at) {
		if (level.getBlockEntity(at) instanceof JukeboxBlockEntity jukebox) jukebox.removeTheItem();
		if (level.getBlockState(at).is(Blocks.JUKEBOX)) {
			level.playSound(null, at, net.minecraft.sounds.SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
			level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	/** The nearest grown-up free to carry it (not minding a baby). */
	private static @Nullable Villager hand(ServerLevel level, BlockPos spot) {
		return level.getEntitiesOfClass(Villager.class, new AABB(spot).inflate(TownRules.NEAR_BELL), v -> v.isAlive() && !v.isBaby() && !v.isNoAi()
						&& !v.isSleeping() && !Infants.minding(level, v) && !Travellers.passing(v) && !com.vnap.village.life.Life.official(v))
				.stream().min(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(spot.getX(), spot.getY(), spot.getZ()))).orElse(null);
	}

	/** A new world: no music is playing in it. */
	public static void clear() {
		PLAYING.clear();
	}
}
