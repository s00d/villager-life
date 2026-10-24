package com.vnap.spire;

import com.vnap.dialogue.Family;
import com.vnap.network.SpireJournalPayload;
import com.vnap.network.SpireNotePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The tablets of the floors (DESIGN §1): the story's notes for floors 1 to 100 (Irma's diary, the Builders' papers),
 * past them the new layers' notes put together from pieces, and in a side room now and then a note of one of the
 * town's villagers who went up before. Reading the floor's own tablet is what lets a player on (SpireTravel); the
 * first one read gives it the Chronicle to read them all again.
 */
public final class SpireNotes {
	/** Story notes written out one a floor; above this the new layers' notes are made of pieces. */
	public static final int STORY = 100;

	private SpireNotes() {
	}

	static void read(ServerLevel level, ServerPlayer player, BlockPos pos) {
		SpireSlots.Slot slot = SpireSlots.at(pos);
		if (slot == null) return;
		int floor = slot.key.floor();
		FloorPlan plan = slot.plan;
		level.playSound(null, pos, com.vnap.sound.SpireSounds.TABLET, SoundSource.BLOCKS, 1.0F, 1.0F);
		BlockPos villager = plan.villagerNote;
		if (villager != null && slot.world(villager).equals(pos)) {
			long seed = FloorGenerator.mix(plan.seed, 5);
			String name = Family.someName(seed);
			PacketDistributor.sendToPlayer(player, new SpireNotePayload(SpireNotePayload.VILLAGER, floor, seed, plan.theme, name));
			return;
		}
		boolean first = SpireProgress.notesRead(player, slot.key.spire()) == 0;
		SpireProgress.read(player, slot.key.spire(), floor);
		int kind = floor <= STORY ? SpireNotePayload.STORY : SpireNotePayload.LAYER;
		com.vnap.progress.Discoveries.discover(player, "spire_notes");
		PacketDistributor.sendToPlayer(player, new SpireNotePayload(kind, floor, plan.seed, plan.theme, ""));
		if (first && !player.getInventory().contains(new ItemStack(com.vnap.item.LifeItems.SPIRE_JOURNAL))) {
			ItemStack journal = new ItemStack(com.vnap.item.LifeItems.SPIRE_JOURNAL);
			if (!player.getInventory().add(journal)) com.vnap.Mc.toss(player, journal);
		}
	}

	/** The Chronicle: every spire this player read a note of, by its town, and how far. */
	static void journal(ServerPlayer player) {
		List<SpireJournalPayload.Climb> climbs = new ArrayList<>();
		for (Spires.Spire spire : Spires.get(com.vnap.Mc.serverLevel(player).getServer()).all()) {
			int read = SpireProgress.notesRead(player, spire.id);
			if (read <= 0) continue;
			String town = spire.bell.getX() + ", " + spire.bell.getZ();
			climbs.add(new SpireJournalPayload.Climb(town, spire.seed, read));
		}
		PacketDistributor.sendToPlayer(player, new SpireJournalPayload(climbs));
	}
}
