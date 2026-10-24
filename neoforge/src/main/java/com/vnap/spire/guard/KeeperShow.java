package com.vnap.spire.guard;

import com.vnap.Mc;
import com.vnap.sound.SpireSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * How each keeper comes into its fight and how it goes out of it (references: Hollow Knight's and Terraria's boss
 * entrances, Bosses of Mass Destruction, Elden Ring's deaths): it is not in its hall until a climber steps in, then
 * it comes its own way — up out of the floor, gathered out of motes, grown from a seed, dropped from above, blinked in
 * out of nothing — and when it falls it goes its own way too: crumbles, bursts, sinks, rises off, shatters, folds in
 * on itself. Its size is played on its scale (an attribute modifier), its place on its position; the rest is
 * particles and sound.
 */
final class KeeperShow {
	static final int ENTER = 50;
	static final int FALL = 60;
	private static final net.minecraft.resources.ResourceLocation SIZE = com.vnap.VillagerLife.id("show_size");

	enum Way {
		RISE, GROW, DROP, BLINK, GATHER
	}

	enum End {
		CRUMBLE, BURST, SINK, ASCEND, SHATTER, IMPLODE
	}

	record Style(Way way, End end, ParticleOptions mote, Block stone, SoundEvent come, SoundEvent go) {
	}

	private KeeperShow() {
	}

	static Style of(int keeper) {
		return switch (keeper) {
			// I. the gate's guardian comes up through the floor and crumbles back to rubble
			case 0 -> new Style(Way.RISE, End.CRUMBLE, ParticleTypes.WAX_OFF, Blocks.STONE_BRICKS, SpireSounds.ROCKFALL, SpireSounds.ROCKFALL);
			// II. pages whirl together into the Archivist; at its end they fly apart
			case 1 -> new Style(Way.GATHER, End.BURST, ParticleTypes.ENCHANT, Blocks.BOOKSHELF, SpireSounds.PAGES, SpireSounds.PAGES);
			// III. sparks fly together and the machine is there, a gust of steam; it shakes itself to pieces
			case 2 -> new Style(Way.GATHER, End.CRUMBLE, ParticleTypes.ELECTRIC_SPARK, Blocks.COPPER_BLOCK, SpireSounds.STEAM, SpireSounds.STEAM);
			// IV. it grows up out of the ground; it withers back into it
			case 3 -> new Style(Way.GROW, End.SINK, ParticleTypes.CHERRY_LEAVES, Blocks.MOSS_BLOCK, SpireSounds.KEEPER_APPEAR, SpireSounds.KEEPER_PHASE);
			// V. it bursts up out of the water; it sinks back in a boil of bubbles
			case 4 -> new Style(Way.RISE, End.SINK, ParticleTypes.SPLASH, Blocks.PRISMARINE_BRICKS, SpireSounds.DIVE, SpireSounds.DIVE);
			// VI. it freezes together out of the snow; it shatters
			case 5 -> new Style(Way.GROW, End.SHATTER, ParticleTypes.SNOWFLAKE, Blocks.PACKED_ICE, SpireSounds.ICE_FORM, SpireSounds.GLASS_SPLIT);
			// VII. she opens like a bloom; she goes up in a cloud of spores
			case 6 -> new Style(Way.GROW, End.BURST, ParticleTypes.SPORE_BLOSSOM_AIR, Blocks.MYCELIUM, SpireSounds.KEEPER_APPEAR, SpireSounds.KEEPER_PHASE);
			// VIII. the swarm comes together into its master; it runs down into honey
			case 7 -> new Style(Way.GATHER, End.SINK, ParticleTypes.DRIPPING_HONEY, Blocks.HONEYCOMB_BLOCK, SpireSounds.KEEPER_APPEAR, SpireSounds.KEEPER_PHASE);
			// IX. it falls like a star; it rises off into the dark and fades
			case 8 -> new Style(Way.DROP, End.ASCEND, ParticleTypes.END_ROD, Blocks.AMETHYST_BLOCK, SpireSounds.STAR_MARK, SpireSounds.STAR_MARK);
			// X. it is drawn in out of nothing; it folds back into nothing
			case 9 -> new Style(Way.BLINK, End.IMPLODE, ParticleTypes.REVERSE_PORTAL, Blocks.CRYING_OBSIDIAN, SpireSounds.KEEPER_APPEAR, SpireSounds.KEEPER_PHASE);
			// the Schism: the Mason comes down from the ceiling with its stones, the Surveyor steps out of a flash
			case 10, 12 -> new Style(Way.DROP, End.CRUMBLE, ParticleTypes.WHITE_ASH, Blocks.STONE_BRICKS, SpireSounds.ROCKFALL, SpireSounds.ROCKFALL);
			case 11, 13 -> new Style(Way.BLINK, End.BURST, ParticleTypes.ENCHANT, Blocks.LAPIS_BLOCK, SpireSounds.KEEPER_APPEAR, SpireSounds.PAGES);
			// the trials
			case 14 -> new Style(Way.RISE, End.SINK, ParticleTypes.MYCELIUM, Blocks.ROOTED_DIRT, SpireSounds.ROCKFALL, SpireSounds.ROCKFALL);
			case 15 -> new Style(Way.GATHER, End.BURST, ParticleTypes.ENCHANT, Blocks.CHISELED_BOOKSHELF, SpireSounds.PAGES, SpireSounds.PAGES);
			case 16 -> new Style(Way.BLINK, End.IMPLODE, ParticleTypes.ELECTRIC_SPARK, Blocks.COPPER_BLOCK, SpireSounds.STEAM, SpireSounds.STEAM);
			case 17 -> new Style(Way.GROW, End.CRUMBLE, ParticleTypes.CHERRY_LEAVES, Blocks.STRIPPED_OAK_WOOD, SpireSounds.KEEPER_APPEAR, SpireSounds.KEEPER_PHASE);
			case 19 -> new Style(Way.GATHER, End.SHATTER, ParticleTypes.SNOWFLAKE, Blocks.ICE, SpireSounds.ICE_FORM, SpireSounds.GLASS_SPLIT);
			case 20 -> new Style(Way.GROW, End.SINK, ParticleTypes.SPORE_BLOSSOM_AIR, Blocks.MUSHROOM_STEM, SpireSounds.KEEPER_APPEAR, SpireSounds.KEEPER_PHASE);
			case 21 -> new Style(Way.RISE, End.SINK, ParticleTypes.DRIPPING_HONEY, Blocks.HONEY_BLOCK, SpireSounds.KEEPER_APPEAR, SpireSounds.KEEPER_PHASE);
			case 22 -> new Style(Way.DROP, End.ASCEND, ParticleTypes.END_ROD, Blocks.OBSIDIAN, SpireSounds.STAR_MARK, SpireSounds.STAR_MARK);
			case 23 -> new Style(Way.BLINK, End.SHATTER, ParticleTypes.WAX_ON, Blocks.GLASS, SpireSounds.GLASS_SPLIT, SpireSounds.GLASS_SPLIT);
			default -> new Style(Way.GROW, End.BURST, ParticleTypes.END_ROD, Blocks.STONE, SpireSounds.KEEPER_APPEAR, SpireSounds.KEEPER_PHASE);
		};
	}

	/** Its size as a share of its own (1 its full size), kept as a modifier on top of what its genome gave it. */
	static void size(SpireKeeper k, double share) {
		AttributeInstance scale = k.getAttribute(Attributes.SCALE);
		if (scale == null) return;
		scale.removeModifier(SIZE);
		if (share < 0.999) scale.addTransientModifier(new AttributeModifier(SIZE, Math.max(0.02, share) - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static double ease(double t) {
		return t * t * (3 - 2 * t);
	}

	/** Tick {@code t} of its coming, 0..ENTER; {@code at} is where it stands when it is all there. */
	static void enter(SpireKeeper k, ServerLevel level, int t, Vec3 at) {
		Style style = of(k.genome().keeper());
		double share = t / (double) ENTER;
		ParticleOptions dust = new BlockParticleOption(ParticleTypes.BLOCK, style.stone().defaultBlockState());
		switch (style.way()) {
			case RISE -> {
				k.setInvisible(false);
				Mc.snap(k, at.x, at.y - 3.2 * (1.0 - ease(share)), at.z, k.getYRot(), k.getXRot());
				level.sendParticles(dust, at.x, at.y + 0.2, at.z, 8, 1.0, 0.1, 1.0, 0.15);
				level.sendParticles(style.mote(), at.x, at.y + 0.3, at.z, 4, 1.2, 0.2, 1.2, 0.05);
				if (t % 10 == 0) k.playSound(style.come(), 1.6F, 0.6F + (float) share * 0.4F);
			}
			case GROW -> {
				k.setInvisible(false);
				size(k, 0.05 + 0.95 * ease(share));
				for (int i = 0; i < 3; i++) {
					double a = k.rng().nextDouble() * Math.PI * 2;
					double r = 0.5 + k.rng().nextDouble() * 1.8;
					level.sendParticles(style.mote(), at.x + Math.cos(a) * r, at.y + 0.2, at.z + Math.sin(a) * r, 1, 0.0, 0.25, 0.0, 0.02);
				}
				if (t % 12 == 0) k.playSound(style.come(), 1.4F, 0.7F + (float) share * 0.5F);
			}
			case DROP -> {
				// a shadow first (dust sifting down where it will land), then it comes down hard
				int falls = ENTER - 14;
				k.setInvisible(t < falls);
				if (t < falls) {
					level.sendParticles(dust, at.x, at.y + 6.0, at.z, 2, 0.6, 0.1, 0.6, 0.0);
					level.sendParticles(style.mote(), at.x, at.y + 0.1, at.z, 2, 0.8, 0.0, 0.8, 0.0);
				} else {
					double fall = (t - falls) / 14.0;
					Mc.snap(k, at.x, at.y + 10.0 * (1.0 - fall * fall), at.z, k.getYRot(), k.getXRot());
					level.sendParticles(style.mote(), k.getX(), k.getY() + 1.0, k.getZ(), 6, 0.3, 0.6, 0.3, 0.02);
				}
				if (t == ENTER - 1) {
					level.sendParticles(dust, at.x, at.y + 0.3, at.z, 80, 1.6, 0.3, 1.6, 0.25);
					level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.6, 0.2, 0.6, 0.0);
					k.playSound(style.come(), 2.4F, 0.6F);
				}
			}
			case BLINK -> {
				// motes spiral in to a point; it flickers into being
				k.setInvisible(t < ENTER - 8 || (t < ENTER - 2 && t % 2 == 0));
				double r = 3.5 * (1.0 - share);
				for (int i = 0; i < 6; i++) {
					double a = t * 0.35 + i * Math.PI / 3;
					level.sendParticles(style.mote(), at.x + Math.cos(a) * r, at.y + 1.0 + Math.sin(t * 0.2 + i) * 0.5, at.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
				}
				if (t == ENTER - 8) k.playSound(style.come(), 2.0F, 1.2F);
			}
			case GATHER -> {
				// motes stream in from round the hall and pack together; it is there when they are
				k.setInvisible(t < ENTER - 6);
				for (int i = 0; i < 5; i++) {
					double a = k.rng().nextDouble() * Math.PI * 2;
					double r = 6.0 * (1.0 - share) + 0.5;
					Vec3 from = new Vec3(at.x + Math.cos(a) * r, at.y + 0.5 + k.rng().nextDouble() * 2.0, at.z + Math.sin(a) * r);
					Vec3 to = new Vec3(at.x, at.y + 1.2, at.z).subtract(from).normalize().scale(0.25);
					level.sendParticles(style.mote(), from.x, from.y, from.z, 0, to.x, to.y, to.z, 1.0);
				}
				if (t % 10 == 0) k.playSound(style.come(), 1.4F, 0.8F + (float) share * 0.6F);
			}
		}
		if (t == ENTER) {
			size(k, 1.0);
			k.setInvisible(false);
			Mc.snap(k, at.x, at.y, at.z, k.getYRot(), k.getXRot());
		}
	}

	/** Tick {@code t} of its end, 0..FALL. */
	static void fall(SpireKeeper k, ServerLevel level, int t) {
		Style style = of(k.genome().keeper());
		ParticleOptions dust = new BlockParticleOption(ParticleTypes.BLOCK, style.stone().defaultBlockState());
		double h = k.getBbHeight();
		// it stands and shudders first
		if (t < 15) {
			k.setYRot(k.getYRot() + (k.rng().nextFloat() - 0.5F) * 12.0F);
			level.sendParticles(style.mote(), k.getX(), k.getY() + h * 0.6, k.getZ(), 3, 0.4, h * 0.3, 0.4, 0.02);
			return;
		}
		double on = (t - 15) / (double) (FALL - 15);
		switch (style.end()) {
			case CRUMBLE -> {
				size(k, 1.0 - 0.75 * on);
				level.sendParticles(dust, k.getX(), k.getY() + h * 0.7, k.getZ(), 10, 0.5, h * 0.3, 0.5, 0.1);
				if (t % 9 == 0) k.playSound(style.go(), 1.6F, 0.7F + (float) on * 0.4F);
			}
			case BURST -> {
				size(k, 1.0 + 0.15 * Math.sin(t * 0.9) * on);
				level.sendParticles(style.mote(), k.getX(), k.getY() + h * 0.5, k.getZ(), 6, 0.4, h * 0.4, 0.4, 0.05);
				if (t == FALL - 1) {
					level.sendParticles(style.mote(), k.getX(), k.getY() + h * 0.5, k.getZ(), 160, 1.2, h * 0.6, 1.2, 0.6);
					level.sendParticles(dust, k.getX(), k.getY() + h * 0.5, k.getZ(), 60, 0.8, h * 0.5, 0.8, 0.3);
					k.playSound(style.go(), 2.4F, 0.8F);
				}
			}
			case SINK -> {
				k.noPhysics = true;
				Mc.snap(k, k.getX(), k.getY() - 0.06, k.getZ(), k.getYRot(), k.getXRot());
				level.sendParticles(style.mote(), k.getX(), k.getY() + h * 0.8, k.getZ(), 6, 0.5, 0.3, 0.5, 0.05);
				level.sendParticles(dust, k.getX(), k.getY() + 0.2, k.getZ(), 4, 0.8, 0.1, 0.8, 0.05);
				if (t % 12 == 0) k.playSound(style.go(), 1.6F, 0.6F);
			}
			case ASCEND -> {
				k.noPhysics = true;
				Mc.snap(k, k.getX(), k.getY() + 0.12 + on * 0.1, k.getZ(), k.getYRot() + 6.0F, k.getXRot());
				size(k, 1.0 - 0.6 * on);
				level.sendParticles(style.mote(), k.getX(), k.getY(), k.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
				if (t % 15 == 0) k.playSound(style.go(), 1.8F, 1.2F + (float) on * 0.5F);
			}
			case SHATTER -> {
				level.sendParticles(dust, k.getX(), k.getY() + h * 0.5, k.getZ(), 3, 0.4, h * 0.4, 0.4, 0.0);
				if (t == FALL - 1) {
					size(k, 0.02);
					level.sendParticles(dust, k.getX(), k.getY() + h * 0.5, k.getZ(), 140, 1.0, h * 0.5, 1.0, 0.4);
					level.sendParticles(style.mote(), k.getX(), k.getY() + h * 0.5, k.getZ(), 40, 1.0, h * 0.5, 1.0, 0.1);
					k.playSound(style.go(), 2.4F, 0.7F);
				}
			}
			case IMPLODE -> {
				size(k, 1.0 - on * on);
				for (int i = 0; i < 6; i++) {
					double a = t * 0.4 + i * Math.PI / 3;
					double r = 2.5 * (1.0 - on);
					level.sendParticles(style.mote(), k.getX() + Math.cos(a) * r, k.getY() + h * 0.5, k.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
				}
				if (t == FALL - 1) {
					level.sendParticles(com.vnap.Mc.flash(), k.getX(), k.getY() + 1.0, k.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
					k.playSound(style.go(), 2.4F, 1.4F);
				}
			}
		}
	}
}
