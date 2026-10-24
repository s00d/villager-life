package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * What goes on on a floor once it stands: its guards up, the way out lit, the note on its stand; when the last guard
 * falls the chest appears and the onward portal lights (a player goes on only once it read the floor's note).
 */
public final class SpireFloors {
	private static final int CHECK_EVERY = 10;

	private SpireFloors() {
	}

	/** The floor is built: portals, note, guards. */
	static void built(ServerLevel level, SpireSlots.Slot slot) {
		FloorPlan plan = slot.plan;
		BlockState exit = SpireBlocks.PORTAL.defaultBlockState().setValue(SpirePortalBlock.KIND, SpirePortalBlock.Kind.EXIT)
				.setValue(SpirePortalBlock.LIT, true);
		for (BlockPos cell : plan.exitPortal) level.setBlock(slot.world(cell), exit, 2);
		BlockState onward = SpireBlocks.PORTAL.defaultBlockState().setValue(SpirePortalBlock.KIND, SpirePortalBlock.Kind.ONWARD)
				.setValue(SpirePortalBlock.LIT, false);
		for (BlockPos cell : plan.onwardPortal) level.setBlock(slot.world(cell), onward, 2);
		BlockPos note = plan.note;
		if (note != null) level.setBlock(slot.world(note), SpireBlocks.NOTE.defaultBlockState(), 2);
		BlockPos villagerNote = plan.villagerNote;
		if (villagerNote != null) level.setBlock(slot.world(villagerNote), SpireBlocks.NOTE.defaultBlockState(), 2);
		List<FloorPlan.Spawn> all = new java.util.ArrayList<>(plan.guards);
		all.addAll(plan.swimmers);
		for (FloorPlan.Spawn spawn : all) {
			Entity guard = SpireGuards.spawn(level, slot, spawn);
			if (guard != null) slot.guards.add(guard.getUUID());
		}
		slot.total = slot.guards.size();
		slot.shown = slot.total;
		if (slot.guards.isEmpty()) clear(level, slot);
	}

	/** What the floor's plate says to one player (arrived: the moment it came in, the title card). */
	public static void tell(ServerPlayer player, SpireSlots.Slot slot, boolean arrived) {
		if (arrived) {
			int floor = slot.key.floor();
			com.vnap.progress.Discoveries.discover(player, "spire_floor");
			if (floor >= 5) com.vnap.progress.Discoveries.discover(player, "spire_trial");
			if (floor >= 10) com.vnap.progress.Discoveries.discover(player, "spire_keeper");
			// a floor is only reached past the one under it: arriving is what earns the one below
			if (floor > 5) com.vnap.progress.VillageAdvancements.award(player, "spire/trial");
			if (floor > 10) com.vnap.progress.VillageAdvancements.award(player, "spire/keeper");
			if (floor > 45) com.vnap.progress.VillageAdvancements.award(player, "spire/schism");
			if (floor > 50) com.vnap.progress.VillageAdvancements.award(player, "spire/halfway");
			if (floor > 100) com.vnap.progress.VillageAdvancements.award(player, "spire/summit");
			if (floor > 200) com.vnap.progress.VillageAdvancements.award(player, "spire/beyond");
		}
		net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.vnap.network.SpireFloorPayload(slot.key.floor(), slot.plan.theme,
				slot.guards.size(), slot.total, slot.cleared, arrived));
	}

	static void tick(ServerLevel level, SpireSlots.Slot slot, List<ServerPlayer> on) {
		if (level.getGameTime() % 40 == 0) for (ServerPlayer player : on) tell(player, slot, false);
		if (slot.cleared || level.getGameTime() % CHECK_EVERY != 0) return;
		slot.guards.removeIf(id -> {
			Entity guard = level.getEntity(id);
			return guard == null || !guard.isAlive();
		});
		int before = slot.shown;
		slot.shown = slot.guards.size();
		if (slot.guards.isEmpty()) {
			clear(level, slot);
			for (ServerPlayer player : on) {
				com.vnap.Mc.say(player, Component.translatable("spire.villager_life.cleared"), true);
				tell(player, slot, false);
			}
		} else if (before != slot.shown) {
			for (ServerPlayer player : on) tell(player, slot, false);
		}
	}

	/** The last guard fell: the chest and the onward portal. */
	private static void clear(ServerLevel level, SpireSlots.Slot slot) {
		slot.cleared = true;
		// the floor counts its own guards: anything else still about (a brood its summoner left, a straggler) goes with them
		List<com.vnap.spire.guard.SpireGuard> strays = level.getEntitiesOfClass(com.vnap.spire.guard.SpireGuard.class,
				net.minecraft.world.phys.AABB.of(slot.box()), Entity::isAlive);
		if (!strays.isEmpty()) {
			com.vnap.VillagerLife.trace("spire strays on floor " + slot.key.floor() + ": " + strays.stream()
					.map(guard -> guard.look().body().model() + "@" + guard.blockPosition().toShortString() + " " + (int) guard.getHealth() + "hp").toList());
			for (com.vnap.spire.guard.SpireGuard guard : strays) guard.burst(level, 12);
		}
		// a chapter's keeper beaten: the town hears of it
		if (slot.key.floor() % 10 == 0 && !slot.plan.guards.isEmpty()) {
			Spires.Spire spire = Spires.get(level.getServer()).byId(slot.key.spire());
			if (spire != null && slot.key.floor() >= spire.best) {
				spire.news = "Player Cleared a Chapter";
				Spires.get(level.getServer()).setDirty();
			}
		}
		BlockState onward = SpireBlocks.PORTAL.defaultBlockState().setValue(SpirePortalBlock.KIND, SpirePortalBlock.Kind.ONWARD)
				.setValue(SpirePortalBlock.LIT, true);
		for (BlockPos cell : slot.plan.onwardPortal) level.setBlock(slot.world(cell), onward, 2);
		BlockPos chest = slot.world(slot.plan.chest);
		level.setBlock(chest, SpireBlocks.CHEST.defaultBlockState(), 2);
		slot.chestPlaced = true;
		level.playSound(null, chest, com.vnap.sound.SpireSounds.FLOOR_CLEAR, SoundSource.BLOCKS, 2.0F, 1.0F);
	}

	/** A player opened the onward portal of {@code floor - 1}: how high the town's people got (the spire grows by it). */
	static void climbed(MinecraftServer server, Spires.Spire spire, int floor) {
		if (floor <= spire.best) return;
		spire.best = floor;
		Spires.get(server).setDirty();
	}
}
