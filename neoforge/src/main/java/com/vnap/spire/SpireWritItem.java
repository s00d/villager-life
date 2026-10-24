package com.vnap.spire;

import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
//? if <1.21.2
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Builders' Writ: read aloud (held for three seconds) it calls a spire up at once, by day too, without the town
 * being done first (SpireGrowth.due). In a town without a spire it rises where the town's own would (outside its
 * wall); out in the wild, or when the town has no room for it, on the first flat ground ahead of the reader. Not
 * within {@link #APART} of another spire. Sold by the wandering trader and the town's stall (ten diamonds' worth).
 */
public final class SpireWritItem extends Item implements com.vnap.item.Tipped {
	public static final int READ_TICKS = 60;
	/** No spire is called up this close to another (a town's own spire stands outside its wall, within a hundred). */
	public static final int APART = 192;
	/** Ahead of the reader: no nearer than the spire's own box, no farther than the reader sees it rise. */
	private static final int AHEAD_FROM = SpireExterior.REACH + 10;
	private static final int AHEAD_TO = 72;

	public enum Outcome { TOWN, AHEAD, NEAR_SPIRE, NO_ROOM, NOT_HERE }

	public SpireWritItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return READ_TICKS;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BLOCK;
	}

	@Override
	//? if >=1.21.2 {
	/*public net.minecraft.world.InteractionResult use(Level level, Player player, InteractionHand hand) {
	*///?} else
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		player.startUsingItem(hand);
		return com.vnap.Mc.consume(stack);
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
		int read = READ_TICKS - remaining;
		if (level.isClientSide()) {
			// the writ's glyphs come off the page and wind up round the reader, faster and higher as it is read
			double share = read / (double) READ_TICKS;
			for (int i = 0; i < 2; i++) {
				double a = read * 0.35 + i * Math.PI;
				double r = 1.2 - share * 0.5;
				level.addParticle(ParticleTypes.ENCHANT, user.getX() + Math.cos(a) * r, user.getY() + 0.3 + share * 2.2,
						user.getZ() + Math.sin(a) * r, 0.0, 0.3, 0.0);
			}
			if (read % 6 == 0) level.addParticle(ParticleTypes.END_ROD, user.getX(), user.getY() + 2.4 + share, user.getZ(), 0.0, 0.05, 0.0);
			return;
		}
		if (read % 20 == 0) {
			level.playSound(null, user.blockPosition(), com.vnap.sound.SpireSounds.WHISPER, SoundSource.PLAYERS, 0.9F, 0.8F + read / (float) READ_TICKS * 0.4F);
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (!(level instanceof ServerLevel server) || !(user instanceof ServerPlayer player)) return stack;
		BlockPos[] at = new BlockPos[1];
		Outcome outcome = call(server, player.blockPosition(), player.getYRot(), at);
		com.vnap.Mc.say(player, Component.translatable("spire.villager_life.writ." + outcome.name().toLowerCase(java.util.Locale.ROOT)), false);
		if (at[0] == null) {
			server.playSound(null, player.blockPosition(), com.vnap.sound.SpireSounds.PUZZLE_WRONG, SoundSource.PLAYERS, 0.8F, 0.8F);
			return stack;
		}
		answered(server, player, at[0]);
		if (!player.getAbilities().instabuild) stack.shrink(1);
		return stack;
	}

	/** The writ goes up in light toward where the spire will stand, and the ground there answers. */
	private static void answered(ServerLevel level, ServerPlayer player, BlockPos centre) {
		level.playSound(null, player.blockPosition(), com.vnap.sound.SpireSounds.RAISE, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, centre, com.vnap.sound.SpireSounds.KEEPER_APPEAR, SoundSource.BLOCKS, 4.0F, 0.6F);
		double dx = centre.getX() + 0.5 - player.getX();
		double dz = centre.getZ() + 0.5 - player.getZ();
		for (int i = 0; i <= 40; i++) {
			double t = i / 40.0;
			level.sendParticles(ParticleTypes.END_ROD, player.getX() + dx * t, player.getEyeY() + Math.sin(t * Math.PI) * 6.0, player.getZ() + dz * t,
					2, 0.05, 0.05, 0.05, 0.0);
		}
		level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 80, 0.6, 0.8, 0.6, 1.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.getX() + 0.5, centre.getY() + 1.0, centre.getZ() + 0.5, 300,
				SpireExterior.PLAZA / 2.0, 0.5, SpireExterior.PLAZA / 2.0, 0.05);
	}

	/**
	 * Calls a spire up for a reader standing at {@code pos} looking {@code yaw}: where it is placed goes to {@code at[0]}
	 * (null when it is not), and it starts rising at once.
	 */
	public static Outcome call(ServerLevel level, BlockPos pos, float yaw, BlockPos[] at) {
		Spires.Spire[] spire = new Spires.Spire[1];
		Outcome outcome = place(level, pos, yaw, spire);
		Spires.Spire placed = spire[0];
		at[0] = placed == null ? null : placed.centre;
		if (placed != null) SpireGrowth.grow(level, placed, 0, Math.min(placed.best, SpireExterior.FULL));
		return outcome;
	}

	/** Where the writ puts the spire (recorded, not grown yet), to {@code spire[0]}; null when it does not. */
	public static Outcome place(ServerLevel level, BlockPos pos, float yaw, Spires.@Nullable Spire[] spire) {
		spire[0] = null;
		if (!level.dimensionType().hasSkyLight() || SpireSlots.isSpire(level)) return Outcome.NOT_HERE;
		Spires spires = Spires.get(level.getServer());
		for (Spires.Spire other : spires.all()) {
			if (other.dimension.equals(level.dimension()) && other.gate.closerThan(pos, APART)) return Outcome.NEAR_SPIRE;
		}
		Mayors.Office town = townOf(level, pos);
		Spires.Spire placed = town == null ? null : SpireGrowth.place(level, town.bell);
		Outcome outcome = Outcome.TOWN;
		if (placed == null) {
			// the town's own spot (or a lone one): the reader's town still owns it, so its expeditions go up it
			placed = SpireGrowth.place(level, town == null ? pos : town.bell, pos, ahead(pos, yaw));
			outcome = Outcome.AHEAD;
		}
		if (placed == null) return Outcome.NO_ROOM;
		spire[0] = placed;
		return outcome;
	}

	/** The town whose land the reader stands on. */
	private static Mayors.@Nullable Office townOf(ServerLevel level, BlockPos pos) {
		Mayors.Office best = null;
		for (Mayors.Office office : Mayors.get(level.getServer()).offices()) {
			if (!office.dimension.equals(level.dimension())) continue;
			double reach = Towns.reach(level, office.bell) + 16;
			if (office.bell.distSqr(pos) <= reach * reach && (best == null || office.bell.distSqr(pos) < best.bell.distSqr(pos))) best = office;
		}
		return best;
	}

	/** Spots ahead of the reader, the straightest and nearest first. */
	private static List<BlockPos> ahead(BlockPos pos, float yaw) {
		double look = Math.toRadians(yaw + 90.0);
		List<BlockPos> spots = new ArrayList<>();
		for (int d = AHEAD_FROM; d <= AHEAD_TO; d += 4) {
			for (int side = -24; side <= 24; side += 6) {
				spots.add(BlockPos.containing(pos.getX() + Math.cos(look) * d - Math.sin(look) * side, pos.getY(),
						pos.getZ() + Math.sin(look) * d + Math.cos(look) * side));
			}
		}
		spots.sort(Comparator.comparingDouble(spot -> {
			double dx = spot.getX() - pos.getX();
			double dz = spot.getZ() - pos.getZ();
			double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0 - yaw));
			return off * 2.0 + Math.sqrt(dx * dx + dz * dz);
		}));
		return spots;
	}

	@Override
	public List<Component> tips() {
		return List.of(Component.translatable("item.villager_life.spire_writ.tip").withStyle(net.minecraft.ChatFormatting.GRAY),
				Component.translatable("item.villager_life.spire_writ.tip2").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
	}
}
