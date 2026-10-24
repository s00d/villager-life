package com.vnap.mega;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The Waygates' work: the book of keyed gates kept, a linked gate's field lit, a player walked through it to the other. */
public final class Waygates {
	/** A player is not taken again this soon after a crossing (they come out in front of the other gate anyway). */
	private static final int AGAIN = 40;
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Waygates() {
	}

	static void look(ServerLevel level, MegaControllerBlockEntity gate, boolean whole) {
		Gates book = Gates.get(level.getServer());
		book.prune(level.getServer());
		book.set(gate.where(), whole ? gate.key() : null);
		boolean lit = whole && gate.key() != null && book.other(gate.where()) != null;
		DyeColor colour = gate.key();
		for (BlockPos open : MegaKind.GATE.shape().open(gate.getBlockPos(), gate.facing())) {
			boolean field = level.getBlockState(open).is(MegaBlocks.GATE_FIELD);
			if (lit && colour != null) {
				net.minecraft.world.level.block.state.BlockState want = MegaBlocks.GATE_FIELD.defaultBlockState()
						.setValue(GateFieldBlock.COLOUR, colour.getId()).setValue(GateFieldBlock.AXIS, gate.facing().getClockWise().getAxis());
				if (!level.getBlockState(open).equals(want) && (field || level.getBlockState(open).isAir())) level.setBlock(open, want, 2);
			} else if (field) {
				level.setBlock(open, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	static void tell(ServerLevel level, MegaControllerBlockEntity gate, Player player) {
		DyeColor colour = gate.key();
		if (colour == null) {
			com.vnap.Mc.say(player, Component.translatable("mega.villager_life.gate.no_key"), false);
			return;
		}
		GlobalPos other = Gates.get(level.getServer()).other(gate.where());
		Component name = Component.translatable("color.minecraft." + colour.getName());
		if (other == null) {
			com.vnap.Mc.say(player, Component.translatable("mega.villager_life.gate.alone", name), false);
		} else {
			com.vnap.Mc.say(player, Component.translatable("mega.villager_life.gate.linked", name, other.pos().getX(), other.pos().getY(), other.pos().getZ(),
					Component.translatable("dimension." + com.vnap.Mc.id(other.dimension()).toString().replace(':', '.'))), false);
		}
	}

	/** The gate whose field this is: its controller under it, the field one of the plan's open cells. */
	static @Nullable MegaControllerBlockEntity gateOf(ServerLevel level, BlockPos field) {
		for (BlockPos at : BlockPos.betweenClosed(field.offset(-2, -3, -2), field.offset(2, 0, 2))) {
			if (level.getBlockEntity(at) instanceof MegaControllerBlockEntity gate && gate.kind() == MegaKind.GATE
					&& MegaKind.GATE.shape().open(gate.getBlockPos(), gate.facing()).contains(field)) return gate;
		}
		return null;
	}

	/** A player in a lit field: out in front of the other gate of the colour, wherever it stands. */
	static void cross(ServerLevel level, ServerPlayer player, BlockPos field) {
		long now = level.getServer().getTickCount();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < AGAIN) return;
		MegaControllerBlockEntity gate = gateOf(level, field);
		if (gate == null || !gate.formed()) return;
		GlobalPos other = Gates.get(level.getServer()).other(gate.where());
		ServerLevel there = other == null ? null : level.getServer().getLevel(other.dimension());
		if (other == null || there == null) return;
		Direction facing = there.getBlockState(other.pos()).getBlock() instanceof MegaControllerBlock
				? there.getBlockState(other.pos()).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) : Direction.NORTH;
		Direction right = facing.getCounterClockWise();
		double x = other.pos().getX() + 0.5 + right.getStepX() * 0.5 + facing.getStepX() * 1.5;
		double z = other.pos().getZ() + 0.5 + right.getStepZ() * 0.5 + facing.getStepZ() * 1.5;
		LAST.put(player.getUUID(), now);
		level.playSound(null, field, com.vnap.sound.SpireSounds.GATE_CROSS, SoundSource.PLAYERS, 1.0F, 1.0F);
		com.vnap.Mc.teleport(player, there, x, other.pos().getY() + 1.0, z, facing.toYRot(), 0.0F);
		com.vnap.progress.VillageAdvancements.award(player, "mega/gate");
		there.playSound(null, other.pos(), com.vnap.sound.SpireSounds.GATE_CROSS, SoundSource.PLAYERS, 1.0F, 1.1F);
		there.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, x, other.pos().getY() + 1.5, z, 30, 0.4, 0.8, 0.4, 0.05);
	}

	public static void forget(UUID player) {
		LAST.remove(player);
	}
}
