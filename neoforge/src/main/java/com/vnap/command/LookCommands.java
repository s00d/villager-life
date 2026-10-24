package com.vnap.command;

import com.vnap.dialogue.FamilyHome;
import com.vnap.village.town.Ground;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Looking at a place without screenshots, for the agent reading chat answers: {@code /vnap spot} (the cell the
 * player stands in: light, roof, the room it is in and its way out), {@code /vnap layer [dy] [r]} (a map of one layer
 * round the player, a character a block, with villagers and mobs on it) and {@code /vnap mobs [r]} (who is about, on the
 * surface and below it).
 */
final class LookCommands {
	private LookCommands() {
	}

	static int spot(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) return 0;
		ServerLevel level = source.getLevel();
		BlockPos at = player.blockPosition();
		Set<BlockPos> room = FamilyHome.roomAt(level, at);
		BlockPos out = FamilyHome.opening(level, room);
		long dark = room.stream().filter(cell -> level.getBrightness(LightLayer.BLOCK, cell) == 0).count();
		String text = at.toShortString() + ": " + name(level.getBlockState(at)) + " on " + name(level.getBlockState(at.below()))
				+ ", light " + level.getBrightness(LightLayer.BLOCK, at) + " sky " + level.getBrightness(LightLayer.SKY, at)
				+ ", roofed " + FamilyHome.roofed(level, at) + ", surface y " + Ground.surface(level, at.getX(), at.getZ())
				+ " | room " + room.size() + " cells, " + (room.isEmpty() ? "none" : out == null ? "closed" : "open at " + out.toShortString())
				+ ", dark " + dark;
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	static int layer(CommandSourceStack source, int dy, int r) {
		ServerPlayer player = source.getPlayer();
		if (player == null) return 0;
		ServerLevel level = source.getLevel();
		BlockPos centre = player.blockPosition().above(dy);
		Map<Long, Character> marks = new java.util.HashMap<>();
		AABB box = new AABB(centre).inflate(r, 1, r);
		for (Entity entity : level.getEntities((Entity) null, box, entity -> entity.isAlive())) {
			char mark = entity instanceof Villager ? 'v' : entity instanceof IronGolem ? 'g' : entity instanceof Enemy ? 'z' : entity.getUUID().equals(player.getUUID()) ? '@' : 0;
			if (mark != 0) marks.put(BlockPos.containing(entity.getX(), centre.getY(), entity.getZ()).asLong(), mark);
		}
		source.sendSuccess(() -> Component.literal("layer y " + centre.getY() + " x " + (centre.getX() - r) + ".." + (centre.getX() + r)
				+ " (#solid D door B bed T trapdoor F fence/wall/gate S stair/slab W water L light G glass C container ~ plant . air;"
				+ " v villager g golem z monster @ you)"), false);
		for (int z = -r; z <= r; z++) {
			StringBuilder row = new StringBuilder();
			for (int x = -r; x <= r; x++) {
				BlockPos cell = centre.offset(x, 0, z);
				Character mark = marks.get(cell.asLong());
				row.append(mark != null ? mark.charValue() : code(level, cell));
			}
			String line = (centre.getZ() + z) + " " + row;
			source.sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	static int mobs(CommandSourceStack source, int r) {
		ServerPlayer player = source.getPlayer();
		if (player == null) return 0;
		ServerLevel level = source.getLevel();
		Map<String, int[]> counts = new TreeMap<>();
		for (Entity entity : level.getEntities((Entity) null, player.getBoundingBox().inflate(r, 96, r),
				entity -> entity.isAlive() && (entity instanceof Enemy || entity instanceof IronGolem || entity instanceof Villager))) {
			int[] count = counts.computeIfAbsent(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath(), key -> new int[2]);
			boolean under = entity.getBlockY() < Ground.surface(level, entity.getBlockX(), entity.getBlockZ()) - 4;
			count[under ? 1 : 0]++;
		}
		StringBuilder text = new StringBuilder("within " + r + " (surface/below):");
		counts.forEach((type, count) -> text.append(' ').append(type).append(' ').append(count[0]).append('/').append(count[1]));
		source.sendSuccess(() -> Component.literal(text.toString()), false);
		return 1;
	}

	private static char code(ServerLevel level, BlockPos cell) {
		BlockState state = level.getBlockState(cell);
		if (state.isAir()) return '.';
		if (state.getLightEmission(level, cell) > 0) return 'L';
		if (state.getBlock() instanceof DoorBlock) return 'D';
		if (state.getBlock() instanceof BedBlock) return 'B';
		if (state.getBlock() instanceof TrapDoorBlock) return 'T';
		if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.getBlock() instanceof FenceGateBlock) return 'F';
		if (state.getBlock() instanceof StairBlock || state.getBlock() instanceof SlabBlock) return 'S';
		if (!state.getFluidState().isEmpty()) return 'W';
		if (state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) || state.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES)) return 'G';
		if (level.getBlockEntity(cell) instanceof net.minecraft.world.Container) return 'C';
		if (state.canBeReplaced() || state.getCollisionShape(level, cell).isEmpty()) return '~';
		return '#';
	}

	private static String name(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}
}
