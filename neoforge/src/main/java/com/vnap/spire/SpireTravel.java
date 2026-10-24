package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Going into a spire, from floor to floor and out. A player stepping into a lit portal is taken at once when its floor
 * stands ready, otherwise it waits in the portal (the floor is drawn a few ticks) and goes the moment it is.
 */
public final class SpireTravel {
	/** A player just taken through is not taken again for this long (it lands by no portal, but steps are quick). */
	private static final int COOLDOWN = 40;
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private SpireTravel() {
	}

	static void step(ServerPlayer player, SpirePortalBlock.Kind kind, BlockPos portal) {
		ServerLevel level = com.vnap.Mc.serverLevel(player);
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < COOLDOWN) return;
		switch (kind) {
			case GATE -> enterSpire(player, level, portal);
			case EXIT -> leave(player);
			case ONWARD -> onward(player, level, portal);
		}
	}

	private static void enterSpire(ServerPlayer player, ServerLevel level, BlockPos portal) {
		Spires.Spire spire = Spires.get(level.getServer()).byGate(level.dimension(), portal);
		ServerLevel spireLevel = SpireSlots.level(level.getServer());
		if (spire == null || spireLevel == null) return;
		SpireProgress.setReturn(player, level.dimension(), spire.front, spire.id);
		go(player, spireLevel, spire, SpireProgress.floor(player, spire.id));
	}

	private static void onward(ServerPlayer player, ServerLevel level, BlockPos portal) {
		SpireSlots.Slot slot = SpireSlots.at(portal);
		if (slot == null || !slot.cleared) return;
		Spires.Spire spire = Spires.get(level.getServer()).byId(slot.key.spire());
		if (spire == null) return;
		// the floor's note is the way on: nobody climbs past what it has not read
		if (slot.plan.note != null && SpireProgress.notesRead(player, spire.id) < slot.key.floor()) {
			com.vnap.Mc.say(player, Component.translatable("spire.villager_life.read_first"), true);
			LAST.put(player.getUUID(), level.getGameTime() - COOLDOWN + 20);
			return;
		}
		int next = slot.key.floor() + 1;
		if (SpireProgress.floor(player, spire.id) < next) SpireProgress.setFloor(player, spire.id, next);
		SpireFloors.climbed(level.getServer(), spire, next);
		go(player, level, spire, next);
	}

	/** Into the slot of this floor: at once when it is ready, else as soon as it is. */
	static void go(ServerPlayer player, ServerLevel spireLevel, Spires.Spire spire, int floor) {
		SpireSlots.Slot slot = SpireSlots.open(spireLevel, spire, floor);
		LAST.put(player.getUUID(), spireLevel.getGameTime());
		if (slot.ready()) {
			arrive(player, slot);
		} else {
			slot.waiting.add(player.getUUID());
			// it waits in the airlock: lit, closed, by the slot
			BlockPos airlock = slot.airlock();
			com.vnap.Mc.teleport(player, spireLevel, airlock.getX() + 0.5, airlock.getY(), airlock.getZ() + 0.5, 0.0F, 0.0F);
			com.vnap.Mc.say(player, Component.translatable("spire.villager_life.opening"), true);
		}
	}

	/** Onto the floor at its entrance. */
	static void arrive(ServerPlayer player, SpireSlots.Slot slot) {
		ServerLevel spireLevel = slot.level;
		BlockPos at = slot.world(slot.plan.entrance);
		com.vnap.Mc.teleport(player, spireLevel, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, slot.plan.facing, 0.0F);
		LAST.put(player.getUUID(), spireLevel.getGameTime());
		spireLevel.playSound(null, at, com.vnap.sound.SpireSounds.PORTAL_IN, SoundSource.PLAYERS, 1.0F, 1.0F);
		// a quiet floor has its own music; a boss's floor waits for the boss's
		// to the one who came: a second climber's arrival must not start it again over the first one's
		if (Arenas.boss(slot.key.floor()) < 0) com.vnap.Mc.playTo(player, com.vnap.sound.SpireSounds.MUSIC_FLOOR, SoundSource.RECORDS, 1.0F, 1.0F);
		SpireFloors.tell(player, slot, true);
	}

	/** Out to where the player came in from (the spire's foot), or the world's spawn when that is gone. */
	public static void leave(ServerPlayer player) {
		var server = com.vnap.Mc.serverLevel(player).getServer();
		if (server == null) return;
		net.minecraft.resources.ResourceKey<Level> dimension = SpireProgress.returnDimension(player);
		ServerLevel to = dimension == null ? null : server.getLevel(dimension);
		BlockPos pos = SpireProgress.returnPos(player);
		if (to == null || pos.equals(BlockPos.ZERO)) {
			to = server.overworld();
			pos = com.vnap.Mc.spawnPos(to);
		}
		LAST.put(player.getUUID(), to.getGameTime());
		net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, com.vnap.network.SpireFloorPayload.NONE);
		com.vnap.Mc.teleport(player, to, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), 0.0F);
		to.playSound(null, pos, com.vnap.sound.SpireSounds.PORTAL_OUT, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/** The floor a player in the spire dimension belongs on (its climb of the spire it went into), or null. */
	static SpireSlots.@Nullable Slot slotFor(ServerPlayer player, ServerLevel spireLevel) {
		Spires.Spire spire = Spires.get(spireLevel.getServer()).byId(SpireProgress.inside(player));
		if (spire == null) return null;
		return SpireSlots.open(spireLevel, spire, SpireProgress.floor(player, spire.id));
	}

	static void forget(UUID player) {
		LAST.remove(player);
	}
}
