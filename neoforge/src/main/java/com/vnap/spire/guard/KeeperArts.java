package com.vnap.spire.guard;

import com.vnap.Mc;
import com.vnap.spire.Attack;
import com.vnap.spire.Genome;
import com.vnap.spire.SpireBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What each keeper does of its own (DESIGN §5; references in docs/tower/BOSSES.md): one art from the first phase,
 * more opening with each change, its pillars, its little ones, what its shots carry. Every art shows itself before it
 * hurts — a mark on the floor fills, a beam is drawn still, a breath gathers — and most leave a window: a keeper that
 * slams, vents, dives or loses its shell stands stunned. The arts keep no state of their own (one per chapter, shared
 * by every keeper of it); what lasts lives on the keeper.
 */
public final class KeeperArts {
	private static final List<Art> ARTS = List.of(new Gatekeeper(), new Archivist(), new Clockwork(), new Gardener(), new Serpent(),
			new Colossus(), new SporeQueen(), new HiveMaster(), new Stargazer(), new Mind(), new Pair(false, false), new Pair(true, false),
			new Pair(false, true), new Pair(true, true), new RootWyrm(), new Binder(), new Chronarch(), new Heartwood(), new Pair(false, false), new FrostMirror(),
			new MycelHydra(), new HoneyGolem(), new Gravitor(), new Reflection(), new Patchwork());

	private KeeperArts() {
	}

	public static Art of(int keeper) {
		return ARTS.get(Math.floorMod(keeper, ARTS.size()));
	}

	/** What a keeper gives off as it stands, client side (its chapter's: dust, glyphs, steam, petals, ...). */
	public static ParticleOptions mote(int keeper) {
		return switch (Math.floorMod(keeper, ARTS.size())) {
			case 0 -> ParticleTypes.WAX_OFF;
			case 1 -> ParticleTypes.ENCHANT;
			case 2 -> ParticleTypes.SMOKE;
			case 3 -> ParticleTypes.CHERRY_LEAVES;
			case 4 -> ParticleTypes.DRIPPING_WATER;
			case 5 -> ParticleTypes.SNOWFLAKE;
			case 6 -> ParticleTypes.SPORE_BLOSSOM_AIR;
			case 7 -> ParticleTypes.DRIPPING_HONEY;
			case 8 -> ParticleTypes.END_ROD;
			case 10, 12 -> ParticleTypes.WHITE_ASH;
			case 11, 13 -> ParticleTypes.ENCHANT;
			case 14 -> ParticleTypes.MYCELIUM;
			case 15 -> ParticleTypes.ENCHANT;
			case 16 -> ParticleTypes.ELECTRIC_SPARK;
			case 17 -> ParticleTypes.CHERRY_LEAVES;
			case 19 -> ParticleTypes.SNOWFLAKE;
			case 20 -> ParticleTypes.SPORE_BLOSSOM_AIR;
			case 21 -> ParticleTypes.DRIPPING_HONEY;
			case 22 -> ParticleTypes.REVERSE_PORTAL;
			case 23 -> ParticleTypes.WAX_ON;
			default -> ParticleTypes.REVERSE_PORTAL;
		};
	}

	/** A keeper's own: its bar's colour, pillars, rhythm, arts, little ones. */
	public abstract static class Art {
		abstract BossEvent.BossBarColor colour();

		/** The pillars that rise at its first change, none for some. */
		@Nullable BlockState pillar() {
			return null;
		}

		/** Ticks between its arts at the first phase (faster later). */
		int every() {
			return 140;
		}

		/** Its {@code n}th art in this phase. */
		abstract void art(SpireKeeper k, ServerLevel level, int phase, int n);

		/** At a change of phase (2 or 3). */
		void phase(SpireKeeper k, ServerLevel level, int phase) {
		}

		void tick(SpireKeeper k, ServerLevel level, int phase) {
		}

		float taken(SpireKeeper k, DamageSource source, float amount) {
			return amount;
		}

		void landed(SpireKeeper k, ServerLevel level, Attack attack) {
		}

		/** What its blows and shots leave on a climber: its own affliction, or nothing. */
		void afflict(SpireKeeper k, LivingEntity target) {
		}

		Genome minion(SpireKeeper k, long seed) {
			return small(k, seed, Genome.Movement.WALK, Genome.Body.HOUND, Genome.Role.SWARM, List.of(Attack.STRIKE), 0.6F);
		}

		double orbitRadius() {
			return 3.2;
		}

		int orbitCount() {
			return 3;
		}

		ParticleOptions orbitMote(SpireKeeper k) {
			return ParticleTypes.END_ROD;
		}

		/** Fights from afar, backing off a climber who closes in (the shooters among them). */
		boolean keepsAway() {
			return false;
		}
	}

	// ---- shared pieces ----------------------------------------------------------------------

	static Genome small(SpireKeeper k, long seed, Genome.Movement movement, Genome.Body body, Genome.Role role, List<Attack> attacks, float size) {
		Genome g = k.genome();
		return new Genome(seed, g.floor(), movement, body, role, attacks, Genome.Defence.NONE, size, g.palette(), g.shade(), -1);
	}

	static @Nullable LivingEntity aim(SpireKeeper k) {
		LivingEntity target = k.getTarget();
		// not one out of its hall (gone through a portal, or never in it): its marks and leaps would take it out after them
		if (target != null && target.isAlive() && k.room().inflate(2.0).contains(target.position())) return target;
		Player nearest = null;
		for (Player player : k.climbers()) if (nearest == null || k.distanceToSqr(player) < k.distanceToSqr(nearest)) nearest = player;
		return nearest;
	}

	/** The climbers in its room other than the one it is after (a group's others, whom a fight must not forget). */
	static List<Player> others(SpireKeeper k, @Nullable LivingEntity target) {
		List<Player> out = new java.util.ArrayList<>(k.climbers());
		out.removeIf(player -> player.equals(target));
		return out;
	}

	static double angleTo(SpireKeeper k, Vec3 at) {
		return Math.atan2(at.z - k.getZ(), at.x - k.getX());
	}

	static BlockParticleOption block(net.minecraft.world.level.block.Block block, boolean falling) {
		return new BlockParticleOption(falling ? ParticleTypes.FALLING_DUST : ParticleTypes.BLOCK, block.defaultBlockState());
	}

	/** Each climber within {@code r} of {@code at}. */
	static void from(SpireKeeper k, Vec3 at, double r, java.util.function.Consumer<Player> what) {
		for (Player player : k.climbers()) if (player.position().distanceTo(at) <= r) what.accept(player);
	}

	/** Rooted where it stands (a bud). */
	static void still(LivingEntity guard) {
		AttributeInstance speed = guard.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) speed.setBaseValue(0.0);
	}

	/** Stones from the ceiling where each climber stands (and {@code extra} more beside each). */
	static void rockfall(SpireKeeper k, ServerLevel level, int extra, net.minecraft.world.level.block.Block stone) {
		float damage = k.damage() * 1.3F;
		for (Player player : k.climbers()) {
			for (int i = 0; i <= extra; i++) {
				Vec3 at = i == 0 ? player.position() : k.near(player.position(), 3.0);
				k.mark(at, 0.0, 1.6, 30, block(stone, true), true, (l, m) -> {
					k.blow(m.at, 0.0, m.outer, damage, 0.3, 0.0, null);
					l.sendParticles(block(stone, false), m.at.x, m.at.y + 0.5, m.at.z, 30, 0.6, 0.4, 0.6, 0.1);
					l.playSound(null, BlockPos.containing(m.at), com.vnap.sound.SpireSounds.ROCKFALL, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 1.0F);
				});
			}
		}
	}

	/** Roots out of the floor under each climber: a blow, a toss and a hold. */
	static void roots(SpireKeeper k, ServerLevel level) {
		float damage = k.damage() * 1.1F;
		for (Player player : k.climbers()) {
			k.mark(player.position(), 0.0, 1.5, 25, ParticleTypes.COMPOSTER, false, (l, m) -> {
				k.blow(m.at, 0.0, m.outer, damage, 0.0, 0.5, com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.ENTANGLED, 60, 0));
				l.sendParticles(block(Blocks.ROOTED_DIRT, false), m.at.x, m.at.y + 0.3, m.at.z, 30, 0.5, 0.6, 0.5, 0.1);
				l.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.at.x, m.at.y + 1.0, m.at.z, 10, 0.5, 0.8, 0.5, 0.0);
				l.playSound(null, BlockPos.containing(m.at), com.vnap.sound.SpireSounds.ROOTS, net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 1.0F);
			});
		}
	}

	/** Stars come down: on each climber and round them; {@code count} in all at the least. */
	static void starfall(SpireKeeper k, ServerLevel level, int count) {
		float damage = k.damage() * 1.3F;
		List<Player> climbers = k.climbers();
		if (climbers.isEmpty()) return;
		for (int i = 0; i < Math.max(count, climbers.size()); i++) {
			Player player = climbers.get(i % climbers.size());
			Vec3 at = i < climbers.size() ? player.position() : k.near(player.position(), 4.0);
			k.mark(at, 0.0, 1.8, 40, ParticleTypes.END_ROD, true, (l, m) -> {
				k.blow(m.at, 0.0, m.outer, damage, 0.4, 0.2, null);
				l.sendParticles(Mc.flash(), m.at.x, m.at.y + 0.5, m.at.z, 1, 0.0, 0.0, 0.0, 0.0);
				l.sendParticles(ParticleTypes.FIREWORK, m.at.x, m.at.y + 0.5, m.at.z, 20, 0.3, 0.3, 0.3, 0.15);
				l.playSound(null, BlockPos.containing(m.at), com.vnap.sound.SpireSounds.STARFALL, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 1.0F);
			});
		}
	}

	/** A cloud of spores where each climber stands, left hanging there. */
	static void spores(SpireKeeper k, ServerLevel level) {
		for (Player player : k.climbers()) {
			k.mark(player.position(), 0.0, 2.2, 30, ParticleTypes.SPORE_BLOSSOM_AIR, false, (l, m) -> {
				k.blow(m.at, 0.0, m.outer, k.damage() * 0.6F, 0.0, 0.0, null);
				k.lingering(l, m.at, 2.2F, 100, com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.BLIGHT, 80, 0), ParticleTypes.SPORE_BLOSSOM_AIR);
				l.playSound(null, BlockPos.containing(m.at), com.vnap.sound.SpireSounds.SPORES, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 1.0F);
			});
		}
	}

	/** Away to another spot of the room, never next to the climber: the spot glows first, then it is there. */
	static void relocate(SpireKeeper k, @Nullable LivingEntity target) {
		Vec3 middle = new Vec3(k.room().getCenter().x, k.getY(), k.room().getCenter().z);
		Vec3 to = k.near(middle, 6.0);
		for (int i = 0; i < 12 && target != null && to.distanceTo(target.position()) < 7.0; i++) to = k.near(middle, 6.0);
		k.mark(to, 0.0, 1.2, 20, ParticleTypes.ENCHANT, false, (l, m) -> {
			l.sendParticles(ParticleTypes.REVERSE_PORTAL, k.getX(), k.getY() + 1.0, k.getZ(), 30, 0.5, 1.0, 0.5, 0.05);
			Mc.snap(k, m.at.x, m.at.y, m.at.z, k.getYRot(), k.getXRot());
			l.sendParticles(ParticleTypes.REVERSE_PORTAL, m.at.x, m.at.y + 1.0, m.at.z, 30, 0.5, 1.0, 0.5, 0.05);
			k.playSound(com.vnap.sound.SpireSounds.PAGES, 1.6F, 0.7F);
		});
	}

	/** A beam that sweeps {@code arc} radians across the climber over 40 ticks (or round, a whole turn). */
	static void sweep(SpireKeeper k, double arc, int beams, ParticleOptions light) {
		LivingEntity target = aim(k);
		double at = target == null ? k.getYRot() * Math.PI / 180 : angleTo(k, target.position());
		for (int i = 0; i < beams; i++) {
			double start = at - arc / 2 + Math.PI * 2 * i / beams;
			k.beam(start, arc / 40.0, 25, 40, 12.0, k.damage() * 0.5F, light);
		}
		k.cast(65);
		k.playSound(com.vnap.sound.SpireSounds.BEAM_CHARGE, 1.6F, 1.0F);
	}

	// ---- I. the Gatekeeper ---------------------------------------------------------------------

	/**
	 * A stone colossus with a shield (Mowzie's Ferrous Wroughtnaut, Cataclysm's Ignis): blows from the front mostly
	 * glance off; its slam leaves it stuck, shield down — the window. Stones fall where the climbers stand, more of
	 * them later, and later still its slams come quicker and leave it open for less.
	 */
	static final class Gatekeeper extends Art {
		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.PURPLE;
		}

		@Override
		BlockState pillar() {
			return Blocks.AMETHYST_BLOCK.defaultBlockState();
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			rockfall(k, level, phase == 3 ? 2 : phase == 2 ? 1 : 0, Blocks.COBBLED_DEEPSLATE);
		}

		@Override
		void landed(SpireKeeper k, ServerLevel level, Attack attack) {
			if (attack == Attack.SLAM) k.stun(k.phase() == 3 ? 30 : 45);
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			k.chain(phase - 1);
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			// the gate comes down: a wall across the hall through its middle, with one way through, never twice in one place
			if (phase < 2 || k.tickCount % 220 != 0) return;
			LivingEntity target = aim(k);
			if (target == null) return;
			Vec3 middle = k.room().getCenter();
			boolean alongX = Math.abs(target.getX() - k.getX()) < Math.abs(target.getZ() - k.getZ());
			int gap = -5 + k.rng().nextInt(9);
			double y = floorAt(k, middle.x, middle.z).y;
			for (int i = -7; i <= 7; i++) {
				if (Math.abs(i - gap) <= 1) continue;
				int col = i;
				BlockPos foot = BlockPos.containing(alongX ? middle.x + i : middle.x, y, alongX ? middle.z : middle.z + i);
				k.mark(Vec3.atCenterOf(foot).add(0, -0.5, 0), 0.0, 0.6, 20 + Math.abs(col) * 2, block(Blocks.IRON_BARS, false), false, (l, m) -> {
					for (int h = 0; h < 3; h++) k.raise(l, foot.above(h), Blocks.IRON_BARS.defaultBlockState(), 140);
				});
			}
			k.playSound(com.vnap.sound.SpireSounds.WALL_RISE, 2.0F, 0.6F);
		}
	}

	// ---- II. the Archivist ---------------------------------------------------------------------

	/**
	 * A flying machine of pages (Bosses of Mass Destruction's Night Lich): a storm of pages from the start, a jump to
	 * another shelf; at its first change four pages bind it — while one flies, blows hardly touch it; at its last, ink
	 * spills under the climbers and blinds them.
	 */
	static final class Archivist extends Art {
		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.YELLOW;
		}

		@Override
		BlockState pillar() {
			return Blocks.BOOKSHELF.defaultBlockState();
		}

		@Override
		int every() {
			return 110;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			LivingEntity target = aim(k);
			if (n % 3 == 2) {
				relocate(k, target);
				return;
			}
			if (target == null) return;
			int pages = phase == 3 ? 7 : 5;
			for (int i = 0; i < pages; i++) k.shoot(level, target, k.damage() * 0.7F, (i - pages / 2) * 10.0F);
			// the rest of a group get a pair of pages each: no one stands aside from the storm
			for (Player other : others(k, target)) {
				for (int i = 0; i < 2; i++) k.shoot(level, other, k.damage() * 0.6F, (i - 0.5F) * 8.0F);
			}
			k.playSound(com.vnap.sound.SpireSounds.PAGES, 2.0F, 1.0F);
			if (phase == 3) {
				for (Player player : k.climbers()) {
					k.mark(player.position(), 0.0, 2.0, 25, Mc.dust(0x2A2640, 1.2F), false, (l, m) -> {
						k.blow(m.at, 0.0, m.outer, k.damage() * 0.8F, 0.0, 0.0, null);
						from(k, m.at, m.outer, p -> com.vnap.spire.Afflictions.stack(p, com.vnap.spire.Afflictions.INKED, 160, 3));
						l.sendParticles(Mc.dust(0x2A2640, 1.4F), m.at.x, m.at.y + 0.5, m.at.z, 40, 0.8, 0.4, 0.8, 0.02);
					});
				}
			}
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			// pages bind it: they turn round it at a sword's reach, and while one is whole blows hardly touch it
			int pages = phase == 2 ? 4 : 2;
			for (int i = 0; i < pages; i++) {
				SpireGuard page = k.part(level, SpireGuard.Part.PLANET, Genome.Body.MOTH, Genome.Role.SWARM, List.of(Attack.STRIKE), 0.8F, k.position());
				if (page != null) {
					AttributeInstance health = page.getAttribute(Attributes.MAX_HEALTH);
					if (health != null) health.setBaseValue(Math.max(6.0, k.getMaxHealth() * 0.04));
					page.setHealth(page.getMaxHealth());
				}
			}
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			List<SpireGuard> pages = k.parts(SpireGuard.Part.PLANET);
			for (int i = 0; i < pages.size(); i++) {
				double a = k.tickCount * 0.06 + Math.PI * 2 * i / pages.size();
				SpireGuard page = pages.get(i);
				Mc.snap(page, k.getX() + Math.cos(a) * 2.6, k.getY() + 1.2, k.getZ() + Math.sin(a) * 2.6, (float) Math.toDegrees(a), 0.0F);
				if (k.tickCount % 4 == 0) k.line(level, k.position().add(0, k.getBbHeight() * 0.5, 0), page.position().add(0, 0.5, 0), ParticleTypes.ENCHANT, 0.8);
			}
		}

		@Override
		float taken(SpireKeeper k, DamageSource source, float amount) {
			return k.parts(SpireGuard.Part.PLANET).isEmpty() ? amount : amount * 0.3F;
		}

		@Override
		boolean keepsAway() {
			return true;
		}

		@Override
		Genome minion(SpireKeeper k, long seed) {
			return small(k, seed, Genome.Movement.FLY, Genome.Body.MOTH, Genome.Role.SHOOTER, List.of(Attack.BOLT), 0.7F);
		}
	}

	// ---- III. the Clockwork Warden -------------------------------------------------------------

	/**
	 * A four-legged engine (Cataclysm's Harbinger): a beam swept across the room from a line drawn first; after two it
	 * vents steam and stands, open; at its first change gears roll across the floor; at its last its beams turn round
	 * it, two at a time.
	 */
	static final class Clockwork extends Art {
		@Override
		void afflict(SpireKeeper k, LivingEntity target) {
			// a charge left in you: the next swing you make shocks you, so wait it out
			if (!com.vnap.spire.Afflictions.has(target, com.vnap.spire.Afflictions.JOLTED)) target.addEffect(com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.JOLTED, 100, k.phase() - 1));
		}

		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.RED;
		}

		@Override
		BlockState pillar() {
			return Blocks.RAW_COPPER_BLOCK.defaultBlockState();
		}

		@Override
		int every() {
			return 150;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			if (n % 3 == 2) {
				// vents: steam, a hiss, and it stands open
				k.stun(60);
				level.sendParticles(ParticleTypes.CLOUD, k.getX(), k.getY() + k.getBbHeight(), k.getZ(), 40, 0.8, 0.6, 0.8, 0.08);
				k.playSound(com.vnap.sound.SpireSounds.STEAM, 2.0F, 1.0F);
				return;
			}
			if (phase == 3) sweep(k, Math.PI * 2, 2, ParticleTypes.ELECTRIC_SPARK);
			else sweep(k, Math.PI * 0.55, 1, ParticleTypes.ELECTRIC_SPARK);
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			belts(k, level);
			if (phase < 2) {
				// before its gears loose themselves at the one it fights, a stray one rolls through each of the others
				if (k.tickCount % 140 != 0) return;
				for (Player other : others(k, aim(k))) {
					net.minecraft.world.phys.AABB room = k.room();
					boolean alongX = k.rng().nextBoolean();
					Vec3 from = alongX ? new Vec3(room.minX + 1.0, other.getY(), other.getZ()) : new Vec3(other.getX(), other.getY(), room.minZ + 1.0);
					k.roll(from, alongX ? new Vec3(0.3, 0.0, 0.0) : new Vec3(0.0, 0.0, 0.3), 60, k.damage() * 0.6F, ParticleTypes.ELECTRIC_SPARK);
				}
				return;
			}
			if (k.tickCount % (phase == 3 ? 80 : 120) != 0) return;
			LivingEntity target = aim(k);
			if (target == null) return;
			net.minecraft.world.phys.AABB room = k.room();
			for (int i = 0; i < 2; i++) {
				boolean alongX = k.rng().nextBoolean();
				Vec3 from = alongX ? new Vec3(room.minX + 1.0, target.getY(), target.getZ() + (i - 0.5) * 3)
						: new Vec3(target.getX() + (i - 0.5) * 3, target.getY(), room.minZ + 1.0);
				Vec3 step = alongX ? new Vec3(0.35, 0.0, 0.0) : new Vec3(0.0, 0.0, 0.35);
				if (k.rng().nextBoolean()) {
					from = alongX ? new Vec3(room.maxX - 1.0, from.y, from.z) : new Vec3(from.x, from.y, room.maxZ - 1.0);
					step = step.scale(-1.0);
				}
				k.roll(from, step, 60, k.damage() * 0.7F, ParticleTypes.ELECTRIC_SPARK);
			}
			k.playSound(com.vnap.sound.SpireSounds.MARK_WARN, 1.6F, 0.8F);
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			// the workshop's belts start: two lanes across the hall that carry whoever stands on them towards it
			if (phase != 2) return;
			k.memory[4] = k.rng().nextBoolean() ? 1 : 2;
			k.memory[5] = -4 + k.rng().nextInt(3);
			k.memory[6] = 2 + k.rng().nextInt(3);
			k.playSound(com.vnap.sound.SpireSounds.STEAM, 2.0F, 0.6F);
		}

		/** The belts once they run: what lies across them, every tick a push towards the keeper and arrows of sparks. */
		private static void belts(SpireKeeper k, ServerLevel level) {
			if (k.memory[4] == 0) return;
			boolean alongX = k.memory[4] == 1;
			Vec3 middle = k.room().getCenter();
			for (int lane : new int[] {k.memory[5], k.memory[6]}) {
				double across = (alongX ? middle.z : middle.x) + lane;
				for (Player player : k.climbers()) {
					double at = alongX ? player.getZ() : player.getX();
					if (Math.abs(at - across) > 1.5 || !player.onGround()) continue;
					double toward = Math.signum((alongX ? k.getX() - player.getX() : k.getZ() - player.getZ()));
					player.push(alongX ? toward * 0.07 : 0.0, 0.0, alongX ? 0.0 : toward * 0.07);
					Mc.knocked(player);
				}
				if (k.tickCount % 6 == 0) {
					double y = floorAt(k, middle.x, middle.z).y + 0.15;
					for (int i = -7; i <= 7; i += 2) {
						double off = (k.tickCount / 6 % 2) * 1.0;
						level.sendParticles(ParticleTypes.ELECTRIC_SPARK, alongX ? middle.x + i + off : across, y, alongX ? across : middle.z + i + off, 1, 0.0, 0.0, 0.0, 0.0);
					}
				}
			}
		}

		@Override
		Genome minion(SpireKeeper k, long seed) {
			return small(k, seed, Genome.Movement.WALK, Genome.Body.HOUND, Genome.Role.SWARM, List.of(Attack.STRIKE), 0.6F);
		}
	}

	// ---- IV. the Gardener ----------------------------------------------------------------------

	/**
	 * A giant of vines (Terraria's Plantera): roots break the floor under the climbers and hold them; at its first
	 * change three buds open round it and feed it while they live; at its last thorny whips turn round it and the buds
	 * open again.
	 */
	static final class Gardener extends Art {
		@Override
		void afflict(SpireKeeper k, LivingEntity target) {
			// the garden drinks what would heal you
			if (!com.vnap.spire.Afflictions.has(target, com.vnap.spire.Afflictions.SAPPED)) target.addEffect(com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.SAPPED, 80, 0));
		}

		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.GREEN;
		}

		@Override
		BlockState pillar() {
			return Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		}

		@Override
		int every() {
			return 120;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			roots(k, level);
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			for (int i = 0; i < 3; i++) {
				double a = Math.PI * 2 * i / 3 + 0.5;
				BlockPos at = BlockPos.containing(k.getX() + Math.cos(a) * 4.5, k.getY(), k.getZ() + Math.sin(a) * 4.5);
				SpireGuard bud = k.bring(level, small(k, k.genome().seed() + 17L * i + phase, Genome.Movement.WALK, Genome.Body.VINE, Genome.Role.HELPER,
						List.of(Attack.BOLT), 0.8F), at);
				if (bud != null) still(bud);
			}
			if (phase == 3) k.orbit(Integer.MAX_VALUE / 2);
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			if (k.tickCount % 20 != 0) return;
			for (SpireGuard bud : k.company()) {
				// only its buds feed it, not the vines it calls to fight: three of them outheal no climber, but leave them and the fight drags on
				if (bud.genome().body() != Genome.Body.VINE || bud.genome().role() != Genome.Role.HELPER) continue;
				k.heal(k.getMaxHealth() * 0.003F);
				k.line(level, bud.position().add(0, 1.0, 0), k.position().add(0, k.getBbHeight() * 0.5, 0), ParticleTypes.HAPPY_VILLAGER, 0.9);
			}
		}

		@Override
		double orbitRadius() {
			return 3.6;
		}

		@Override
		int orbitCount() {
			return 4;
		}

		@Override
		ParticleOptions orbitMote(SpireKeeper k) {
			return Mc.dust(0x4F8A2E, 1.4F);
		}

		@Override
		Genome minion(SpireKeeper k, long seed) {
			return small(k, seed, Genome.Movement.WALK, Genome.Body.VINE, Genome.Role.FIGHTER, List.of(Attack.STRIKE), 0.7F);
		}
	}

	// ---- V. the Deep Serpent -------------------------------------------------------------------

	/**
	 * A serpent of the deep galleries (Terraria's Duke Fishron, Cataclysm's Leviathan): it dives into the floor and
	 * bursts up under a climber (bubbles rise where it will); its shots slow; at its first change it draws the room in
	 * round it; at its last it lunges three times running. Each time it comes up it stands a moment, open.
	 */
	static final class Serpent extends Art {
		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.BLUE;
		}

		@Override
		BlockState pillar() {
			return Blocks.PRISMARINE_BRICKS.defaultBlockState();
		}

		@Override
		int every() {
			return 150;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			if (phase >= 2 && n % 2 == 1) {
				k.pull(90);
				k.playSound(com.vnap.sound.SpireSounds.DIVE, 2.0F, 0.7F);
				return;
			}
			LivingEntity target = aim(k);
			if (target == null) return;
			level.sendParticles(ParticleTypes.SPLASH, k.getX(), k.getY() + 0.2, k.getZ(), 60, 1.0, 0.2, 1.0, 0.2);
			k.playSound(com.vnap.sound.SpireSounds.DIVE, 2.0F, 1.0F);
			k.setInvisible(true);
			k.untouchable(32);
			k.cast(40);
			// where it is not coming up, the water answers: a geyser under each of the others
			for (Player other : others(k, target)) {
				k.mark(other.position(), 0.0, 1.4, 34, ParticleTypes.BUBBLE, false, (l, m) -> {
					k.blow(m.at, 0.0, m.outer, k.damage() * 0.8F, 0.0, 1.1, null);
					l.sendParticles(ParticleTypes.SPLASH, m.at.x, m.at.y + 0.5, m.at.z, 40, 0.4, 1.4, 0.4, 0.2);
					l.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, m.at.x, m.at.y + 0.5, m.at.z, 20, 0.3, 1.6, 0.3, 0.1);
				});
			}
			k.mark(target.position(), 0.0, 2.0, 30, ParticleTypes.BUBBLE_COLUMN_UP, false, (l, m) -> {
				k.setInvisible(false);
				Mc.snap(k, m.at.x, m.at.y, m.at.z, k.getYRot(), k.getXRot());
				k.blow(m.at, 0.0, m.outer, k.damage() * 1.4F, 0.4, 0.9, null);
				l.sendParticles(ParticleTypes.SPLASH, m.at.x, m.at.y + 0.5, m.at.z, 80, 0.8, 1.2, 0.8, 0.3);
				l.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, m.at.x, m.at.y + 0.5, m.at.z, 40, 0.6, 1.5, 0.6, 0.1);
				k.playSound(com.vnap.sound.SpireSounds.DIVE, 2.0F, 1.1F);
				k.stun(35);
			});
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			if (phase == 3) k.chain(2);
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			// waves roll across the hall from wall to wall, a gap between them to step through
			if (phase < 2 || k.tickCount % (phase == 3 ? 120 : 180) != 0) return;
			net.minecraft.world.phys.AABB room = k.room();
			boolean alongX = k.rng().nextBoolean();
			double y = floorAt(k, k.getX(), k.getZ()).y;
			int gap = k.rng().nextInt(12);
			for (int i = 1; i < 14; i += 2) {
				if (Math.abs(i - gap) <= 2) continue;
				Vec3 from = alongX ? new Vec3(room.minX + 1.0, y, room.minZ + i) : new Vec3(room.minX + i, y, room.minZ + 1.0);
				k.roll(from, alongX ? new Vec3(0.3, 0.0, 0.0) : new Vec3(0.0, 0.0, 0.3), 50, k.damage() * 0.6F, ParticleTypes.SPLASH);
			}
			k.playSound(com.vnap.sound.SpireSounds.DIVE, 2.0F, 0.6F);
		}

		@Override
		void landed(SpireKeeper k, ServerLevel level, Attack attack) {
			if (attack == Attack.DASH && k.phase() == 3 && k.rng().nextInt(3) == 0) k.chain(2);
		}

		@Override
		void afflict(SpireKeeper k, LivingEntity target) {
			// the deep's blows leave water in the lungs: breath runs out on dry floor
			if (!com.vnap.spire.Afflictions.has(target, com.vnap.spire.Afflictions.WATERLOGGED)) target.addEffect(com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.WATERLOGGED, 100, k.phase() >= 2 ? 1 : 0));
		}

		@Override
		Genome minion(SpireKeeper k, long seed) {
			return small(k, seed, Genome.Movement.FLY, Genome.Body.RAY, Genome.Role.FIGHTER, List.of(Attack.STRIKE), 0.7F);
		}
	}

	// ---- VI. the Ice Colossus ------------------------------------------------------------------

	/**
	 * A colossus of the ice halls (Mowzie's Frostmaw, Twilight Forest's Alpha Yeti): a freezing breath down a cone, held
	 * long enough to step out of; at its first change ice grows over it (a shell to break first — broken, it reels) and
	 * icicles fall; at its last spikes run along the floor towards each climber.
	 */
	static final class Colossus extends Art {
		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.WHITE;
		}

		@Override
		BlockState pillar() {
			return Blocks.PACKED_ICE.defaultBlockState();
		}

		@Override
		int every() {
			return 150;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			if (phase == 3 && n % 2 == 1) {
				spikes(k);
				return;
			}
			LivingEntity target = aim(k);
			if (target == null) return;
			double at = angleTo(k, target.position());
			k.cast(70);
			k.playSound(com.vnap.sound.SpireSounds.FROST_BREATH, 2.0F, 1.0F);
			// the cold of the breath brings icicles down over those it is not breathing on
			for (Player other : others(k, target)) {
				if (Math.abs(net.minecraft.util.Mth.wrapDegrees(Math.toDegrees(angleTo(k, other.position()) - at))) < 30.0) continue;
				k.mark(k.near(other.position(), 1.5), 0.0, 1.2, 40, block(Blocks.ICE, true), true, (l, m) -> {
					k.blow(m.at, 0.0, m.outer, k.damage() * 0.9F, 0.2, 0.0, null);
					from(k, m.at, m.outer, p -> com.vnap.spire.Afflictions.frost(p, 1));
					l.sendParticles(block(Blocks.ICE, false), m.at.x, m.at.y + 0.4, m.at.z, 20, 0.4, 0.4, 0.4, 0.1);
				});
			}
			// the breath: gathers 20 ticks, then 40 down a cone 60 degrees wide and 8 long
			for (int t = 0; t < 60; t += 5) {
				int tick = t;
				Vec3 mouth = new Vec3(k.getX(), k.getY() + k.getBbHeight() * 0.7, k.getZ());
				k.later(1 + t, (l, m) -> {
					if (tick < 20) {
						l.sendParticles(ParticleTypes.SNOWFLAKE, mouth.x, mouth.y, mouth.z, 20, 0.5, 0.5, 0.5, 0.05);
						return;
					}
					for (int i = 0; i < 60; i++) {
						double a = at + (k.rng().nextDouble() - 0.5) * Math.PI / 3;
						double r = 1.0 + k.rng().nextDouble() * 7.5;
						double y = mouth.y - (mouth.y - k.getY() - 0.6) * r / 8.5;
						l.sendParticles(i % 3 == 0 ? Mc.dust(0xDDF4FF, 1.8F) : ParticleTypes.SNOWFLAKE, mouth.x + Math.cos(a) * r, y, mouth.z + Math.sin(a) * r,
								1, 0.1, 0.1, 0.1, 0.0);
					}
					for (Player player : k.climbers()) {
						Vec3 to = player.position().subtract(k.position());
						double d = Math.sqrt(to.x * to.x + to.z * to.z);
						double off = Math.abs(net.minecraft.util.Mth.wrapDegrees(Math.toDegrees(Math.atan2(to.z, to.x) - at)));
						if (d > 8.5 || off > 30.0) continue;
						k.hurtOne(player, k.damage() * 0.3F, 0.1);
						com.vnap.spire.Afflictions.frost(player, 1);
					}
				});
			}
		}

		/** Spikes of ice running out along the floor towards each climber, one after another. */
		private static void spikes(SpireKeeper k) {
			for (Player player : k.climbers()) {
				Vec3 way = player.position().subtract(k.position()).multiply(1.0, 0.0, 1.0);
				if (way.lengthSqr() < 1.0E-4) continue;
				way = way.normalize();
				for (int i = 0; i < 7; i++) {
					Vec3 at = k.position().add(way.scale(2.0 + i * 1.5)).add(0.0, player.getY() - k.getY(), 0.0);
					if (!k.room().inflate(0.5).contains(at)) break;
					k.mark(at, 0.0, 1.0, 18 + 3 * i, ParticleTypes.SNOWFLAKE, false, (l, m) -> {
						k.blow(m.at, 0.0, m.outer, k.damage() * 1.2F, 0.2, 0.6, null);
						l.sendParticles(block(Blocks.PACKED_ICE, false), m.at.x, m.at.y + 0.6, m.at.z, 20, 0.3, 0.8, 0.3, 0.1);
						l.playSound(null, BlockPos.containing(m.at), com.vnap.sound.SpireSounds.ICE_FORM, net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.4F);
					});
				}
			}
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			if (phase == 2) {
				k.shell(k.getMaxHealth() * 0.2F);
				k.playSound(com.vnap.sound.SpireSounds.ICE_FORM, 2.0F, 0.8F);
			}
		}

		/** An avalanche: rows of icicles coming down one after another across the hall, a gap in each to run through. */
		private static void avalanche(SpireKeeper k) {
			net.minecraft.world.phys.AABB room = k.room();
			boolean alongX = k.rng().nextBoolean();
			double y = floorAt(k, k.getX(), k.getZ()).y;
			int gap = 2 + k.rng().nextInt(10);
			for (int row = 1; row < 15; row += 2) {
				gap = Math.max(2, Math.min(12, gap - 2 + k.rng().nextInt(5)));
				for (int i = 1; i < 15; i += 2) {
					if (Math.abs(i - gap) <= 1) continue;
					Vec3 at = alongX ? new Vec3(room.minX + row, y, room.minZ + i) : new Vec3(room.minX + i, y, room.minZ + row);
					k.mark(at, 0.0, 1.1, 18 + row * 3, block(Blocks.ICE, true), true, (l, m) -> {
						k.blow(m.at, 0.0, m.outer, k.damage(), 0.2, 0.0, null);
						from(k, m.at, m.outer, p -> com.vnap.spire.Afflictions.frost(p, 1));
						l.sendParticles(block(Blocks.ICE, false), m.at.x, m.at.y + 0.4, m.at.z, 10, 0.4, 0.4, 0.4, 0.1);
					});
				}
			}
			k.playSound(com.vnap.sound.SpireSounds.ROCKFALL, 2.0F, 1.3F);
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			if (phase == 3 && k.tickCount % 200 == 0) avalanche(k);
			if (phase < 2 || k.tickCount % 100 != 0) return;
			for (Player player : k.climbers()) {
				for (int i = 0; i < 2; i++) {
					k.mark(k.near(player.position(), 3.5), 0.0, 1.2, 30, block(Blocks.ICE, true), true, (l, m) -> {
						k.blow(m.at, 0.0, m.outer, k.damage(), 0.2, 0.0, null);
						from(k, m.at, m.outer, p -> com.vnap.spire.Afflictions.frost(p, 1));
						l.sendParticles(block(Blocks.ICE, false), m.at.x, m.at.y + 0.4, m.at.z, 24, 0.4, 0.4, 0.4, 0.1);
						l.playSound(null, BlockPos.containing(m.at), com.vnap.sound.SpireSounds.ICE_FORM, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.2F);
					});
				}
			}
		}

		@Override
		Genome minion(SpireKeeper k, long seed) {
			return small(k, seed, Genome.Movement.WALK, Genome.Body.SENTINEL, Genome.Role.FIGHTER, List.of(Attack.STRIKE), 0.8F);
		}
	}

	// ---- VII. the Spore Queen ------------------------------------------------------------------

	/**
	 * A queen of the mushroom thicket (Bosses of Mass Destruction's Void Blossom): spore clouds bloom where the climbers
	 * stand and hang there; at her first change pods drift out and burst on whoever they reach; at her last the floor
	 * blooms in rings round her — the inner, then the outer — so the climber keeps stepping across.
	 */
	static final class SporeQueen extends Art {
		@Override
		boolean keepsAway() {
			return true;
		}

		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.PINK;
		}

		@Override
		BlockState pillar() {
			return Blocks.MUSHROOM_STEM.defaultBlockState();
		}

		@Override
		int every() {
			return 120;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			spores(k, level);
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			if (phase == 2) pods(k, level);
		}

		private void pods(SpireKeeper k, ServerLevel level) {
			for (int i = 0; i < 3; i++) {
				double a = Math.PI * 2 * i / 3;
				k.bring(level, minion(k, k.genome().seed() + 13L * i + k.tickCount), BlockPos.containing(k.getX() + Math.cos(a) * 2.5, k.getY() + 1.5,
						k.getZ() + Math.sin(a) * 2.5));
			}
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			if (phase >= 2 && k.tickCount % 300 == 0 && k.company().isEmpty()) pods(k, level);
			if (k.tickCount % 5 == 0) {
				for (SpireGuard pod : k.company()) {
					for (Player player : k.climbers()) {
						if (pod.distanceTo(player) > 1.8) continue;
						k.lingering(level, pod.position(), 2.0F, 80, com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.BLIGHT, 100, 0), ParticleTypes.SPORE_BLOSSOM_AIR);
						k.hurtOne(player, k.damage() * 0.8F, 0.4);
						level.playSound(null, pod.blockPosition(), com.vnap.sound.SpireSounds.SPORES, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 1.4F);
						pod.burst(level, 20);
						break;
					}
				}
			}
			if (phase == 3 && k.tickCount % 70 == 0) {
				boolean inner = k.tickCount / 70 % 2 == 0;
				k.mark(k.position(), inner ? 0.0 : 4.0, inner ? 4.0 : 7.5, 35, ParticleTypes.SPORE_BLOSSOM_AIR, false, (l, m) -> {
					k.blow(m.at, m.inner, m.outer, k.damage(), 0.2, 0.0, com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.BLIGHT, 100, 0));
					for (int i = 0; i < 40; i++) {
						double a = Math.PI * 2 * i / 40;
						double r = m.inner + (m.outer - m.inner) * k.rng().nextDouble();
						l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, m.at.x + Math.cos(a) * r, m.at.y + 0.4, m.at.z + Math.sin(a) * r, 3, 0.1, 0.3, 0.1, 0.0);
					}
					l.playSound(null, BlockPos.containing(m.at), com.vnap.sound.SpireSounds.SPORES, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.7F);
				});
			}
		}

		@Override
		void afflict(SpireKeeper k, LivingEntity target) {
			if (!com.vnap.spire.Afflictions.has(target, com.vnap.spire.Afflictions.BLIGHT)) target.addEffect(com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.BLIGHT, 140, 0));
		}

		@Override
		Genome minion(SpireKeeper k, long seed) {
			return small(k, seed, Genome.Movement.FLY, Genome.Body.WISP, Genome.Role.SWARM, List.of(Attack.STRIKE), 0.7F);
		}
	}

	// ---- VIII. the Hive Master -----------------------------------------------------------------

	/**
	 * The master of the hive (Terraria's Queen Bee): it charges three times running, its stingers poison, honey drips
	 * where the climbers stand and holds them; at its first change its bees come; at its last it charges five times.
	 */
	static final class HiveMaster extends Art {
		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.YELLOW;
		}

		@Override
		BlockState pillar() {
			return Blocks.HONEYCOMB_BLOCK.defaultBlockState();
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			if (n % 2 == 0) {
				k.chain(phase == 3 ? 4 : 2);
				k.playSound(com.vnap.sound.SpireSounds.BEES, 2.0F, 1.0F);
				return;
			}
			for (Player player : k.climbers()) {
				k.mark(player.position(), 0.0, 1.8, 25, ParticleTypes.FALLING_HONEY, true, (l, m) -> {
					k.blow(m.at, 0.0, m.outer, k.damage() * 0.6F, 0.0, 0.0, null);
					k.lingering(l, m.at, 1.8F, 120, com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.HONEYED, 60, 0), ParticleTypes.DRIPPING_HONEY);
					l.playSound(null, BlockPos.containing(m.at), com.vnap.sound.SpireSounds.HONEY_SEAL, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 1.0F);
				});
			}
		}

		@Override
		ParticleOptions orbitMote(SpireKeeper k) {
			return Mc.dust(0xF2C230, 1.2F);
		}

		@Override
		int orbitCount() {
			return 6;
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			if (phase == 3) k.orbit(Integer.MAX_VALUE / 2);
			for (int i = 0; i < (phase == 2 ? 3 : 2); i++) {
				double a = Math.PI * 2 * i / 3;
				k.bring(level, minion(k, k.genome().seed() + 7L * i + phase), BlockPos.containing(k.getX() + Math.cos(a) * 2, k.getY() + 1.5,
						k.getZ() + Math.sin(a) * 2));
			}
		}

		@Override
		void afflict(SpireKeeper k, LivingEntity target) {
			// stings stack: each one worse than the last, five at most
			com.vnap.spire.Afflictions.stack(target, com.vnap.spire.Afflictions.STUNG, 120, 4);
		}

		@Override
		Genome minion(SpireKeeper k, long seed) {
			return small(k, seed, Genome.Movement.FLY, Genome.Body.WISP, Genome.Role.SWARM, List.of(Attack.STRIKE), 0.5F);
		}
	}

	// ---- IX. the Stargazer ---------------------------------------------------------------------

	/**
	 * The keeper of the observatory (Terraria's Moon Lord): stars come down on and round the climbers; at its first
	 * change it draws constellations — points first, then the lines between them burn — and once in a while it lifts
	 * the climbers off the floor as stars fall under them; at its last everything comes faster.
	 */
	static final class Stargazer extends Art {
		@Override
		void afflict(SpireKeeper k, LivingEntity target) {
			// a star takes aim at where you will be: keep apart from the others when it falls
			if (!com.vnap.spire.Afflictions.has(target, com.vnap.spire.Afflictions.STARMARKED)) com.vnap.spire.Afflictions.mark(target, 80, k.phase() - 1);
		}

		@Override
		boolean keepsAway() {
			return true;
		}

		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.BLUE;
		}

		@Override
		BlockState pillar() {
			return Blocks.CALCITE.defaultBlockState();
		}

		@Override
		int every() {
			return 130;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			if (n % 4 == 3) {
				relocate(k, aim(k));
				return;
			}
			if (phase >= 2 && n % 3 == 1) {
				constellation(k);
				return;
			}
			if (phase >= 2 && n % 3 == 2) {
				// every climber marked at once: a star falls on each in three seconds, worse for whoever stands too near
				for (Player player : k.climbers()) com.vnap.spire.Afflictions.mark(player, 60, phase - 1);
				return;
			}
			starfall(k, level, phase == 3 ? 8 : 5);
		}

		/** Four stars, then the lines between them burn. */
		private static void constellation(SpireKeeper k) {
			Vec3 middle = new Vec3(k.room().getCenter().x, k.getY(), k.room().getCenter().z);
			Vec3[] stars = new Vec3[4];
			for (int i = 0; i < stars.length; i++) stars[i] = k.near(middle, 6.0);
			for (int i = 0; i < stars.length; i++) {
				Vec3 a = stars[i];
				Vec3 b = stars[(i + 1) % stars.length];
				for (int t = 3; t < 45; t += 6) {
					k.later(t, (l, m) -> k.line(l, a.add(0, 0.6, 0), b.add(0, 0.6, 0), ParticleTypes.END_ROD, 1.2));
				}
				k.mark(a, 0.0, 0.6, 45, ParticleTypes.END_ROD, true, (l, m) -> {
					k.line(l, a.add(0, 0.6, 0), b.add(0, 0.6, 0), ParticleTypes.ELECTRIC_SPARK, 0.3);
					for (Player player : k.climbers()) {
						if (Math.abs(player.getY() - a.y) < 3.0 && SpireKeeper.across(player.position(), a, b) < 0.8) k.hurtOne(player, k.damage() * 1.2F, 0.3);
					}
					l.playSound(null, BlockPos.containing(a), com.vnap.sound.SpireSounds.BEAM_FIRE, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 1.2F);
				});
			}
		}

		@Override
		Genome minion(SpireKeeper k, long seed) {
			return small(k, seed, Genome.Movement.FLY, Genome.Body.MOTH, Genome.Role.SHOOTER, List.of(Attack.BOLT), 0.7F);
		}
	}

	// ---- X. the Mind of the Spire --------------------------------------------------------------

	/**
	 * The Mind (DESIGN §1, the trial in three phases): it tries the climber with what every keeper did — stones,
	 * roots, stars, spores in turn; at its first change two echoes stand up for it (while they stand it takes half) and
	 * its beams begin; at its last the Core's shards turn round it, two beams sweep the whole room, and it draws the
	 * climber in.
	 */
	static final class Mind extends Art {
		@Override
		void afflict(SpireKeeper k, LivingEntity target) {
			// a delayed blow: three blows of yours on it lift it
			if (!com.vnap.spire.Afflictions.has(target, com.vnap.spire.Afflictions.DOOM)) target.addEffect(com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.DOOM, 100, 2));
		}

		@Override
		boolean keepsAway() {
			return true;
		}

		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.PURPLE;
		}

		@Override
		BlockState pillar() {
			return Blocks.CRYING_OBSIDIAN.defaultBlockState();
		}

		@Override
		int every() {
			return 120;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			if (phase >= 2 && n % 2 == 1) {
				sweep(k, phase == 3 ? Math.PI * 2 : Math.PI * 0.6, phase == 3 ? 2 : 1, ParticleTypes.END_ROD);
				if (phase == 3) k.pull(60);
				return;
			}
			k.playSound(com.vnap.sound.SpireSounds.WHISPER, 2.0F, 1.0F);
			switch (n / (phase >= 2 ? 2 : 1) % 4) {
				case 0 -> rockfall(k, level, phase - 1, Blocks.CRYING_OBSIDIAN);
				case 1 -> roots(k, level);
				case 2 -> starfall(k, level, 4 + phase);
				default -> spores(k, level);
			}
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			if (phase == 2) {
				for (int i = 0; i < 2; i++) {
					double a = Math.PI * i;
					k.bring(level, small(k, k.genome().seed() + 3L * i, Genome.Movement.WALK, Genome.Body.SENTINEL, Genome.Role.FIGHTER,
							List.of(Attack.STRIKE, Attack.SLAM), 1.3F), BlockPos.containing(k.getX() + Math.cos(a) * 3, k.getY(), k.getZ() + Math.sin(a) * 3));
				}
			}
			if (phase == 3) k.orbit(Integer.MAX_VALUE / 2);
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			if (k.tickCount % 4 != 0) return;
			for (SpireGuard echo : k.company()) {
				if (echo.genome().body() == Genome.Body.SENTINEL) {
					k.line(level, k.position().add(0, k.getBbHeight() * 0.5, 0), echo.position().add(0, 1.0, 0), ParticleTypes.REVERSE_PORTAL, 0.9);
				}
			}
		}

		@Override
		float taken(SpireKeeper k, DamageSource source, float amount) {
			for (SpireGuard echo : k.company()) if (echo.genome().body() == Genome.Body.SENTINEL) return amount * 0.5F;
			return amount;
		}

		@Override
		ParticleOptions orbitMote(SpireKeeper k) {
			return ParticleTypes.END_ROD;
		}

		@Override
		Genome minion(SpireKeeper k, long seed) {
			return small(k, seed, Genome.Movement.FLY, Genome.Body.WISP, Genome.Role.FIGHTER, List.of(Attack.STRIKE), 0.6F);
		}
	}

	// ---- the Schism's pair (floor 45) ------------------------------------------------------------

	/**
	 * The Mason and the Surveyor (Dark Souls' Ornstein and Smough): one heavy and close, building walls across the
	 * room and sending its hammer's blow along the floor; one light and far, ruling the floor into squares that burst
	 * one set after the other and sweeping a measuring beam (walls and pillars stop it). Together, she marks a climber
	 * and he leaps onto the mark. The one left alone takes up the other's tools and uses both, faster; the Surveyor
	 * with the hammer comes in close.
	 */
	static final class Pair extends Art {
		private final boolean surveyor;
		private final boolean alone;

		Pair(boolean surveyor, boolean alone) {
			this.surveyor = surveyor;
			this.alone = alone;
		}

		@Override
		void afflict(SpireKeeper k, LivingEntity target) {
			// the Mason's hammer splits armour a plate at a time; alone, two at once
			if (surveyor) return;
			for (int i = 0; i < (alone ? 2 : 1); i++) com.vnap.spire.Afflictions.stack(target, com.vnap.spire.Afflictions.SUNDERED, 200, 3);
		}

		@Override
		BossEvent.BossBarColor colour() {
			return surveyor ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED;
		}

		@Override
		@Nullable BlockState pillar() {
			return alone ? (surveyor ? Blocks.POLISHED_DEEPSLATE : Blocks.CHISELED_STONE_BRICKS).defaultBlockState() : null;
		}

		@Override
		int every() {
			return alone ? 100 : surveyor ? 120 : 140;
		}

		@Override
		boolean keepsAway() {
			return surveyor && !alone;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			if (!alone) {
				if (surveyor) {
					switch (n % 3) {
						case 0 -> grid(k);
						case 1 -> sweep(k, Math.PI * 0.6, 1, ParticleTypes.ENCHANTED_HIT);
						default -> relocate(k, aim(k));
					}
				} else {
					switch (n % 3) {
						case 0 -> hammer(k);
						case 1 -> walls(k);
						default -> {
							if (partner(k)) leap(k, 0x6FA8D6);
							else hammer(k);
						}
					}
				}
				return;
			}
			// alone: its own and the other's, one after another
			switch (n % 5) {
				case 0 -> grid(k);
				case 1 -> hammer(k);
				case 2 -> leap(k, surveyor ? 0x6FA8D6 : 0xC9773F);
				case 3 -> sweep(k, phase == 3 ? Math.PI * 2 : Math.PI * 0.7, phase == 3 ? 2 : 1, ParticleTypes.ENCHANTED_HIT);
				default -> walls(k);
			}
		}

		@Override
		Genome minion(SpireKeeper k, long seed) {
			return small(k, seed, Genome.Movement.WALK, Genome.Body.SENTINEL, Genome.Role.FIGHTER, List.of(Attack.STRIKE), 0.8F);
		}

		/** Whether the other of the pair still stands. */
		private static boolean partner(SpireKeeper k) {
			for (SpireKeeper other : k.level().getEntitiesOfClass(SpireKeeper.class, k.room().inflate(2.0), keeper -> keeper.isAlive() && !keeper.equals(k))) {
				if (other.genome().keeper() == Genome.SURVEYOR) return true;
			}
			return false;
		}

		/** The floor ruled into squares of three: one set bursts, then the other. */
		private static void grid(SpireKeeper k) {
			net.minecraft.world.phys.AABB room = k.room();
			double y = k.getY();
			List<Player> climbers = k.climbers();
			if (!climbers.isEmpty()) y = climbers.get(0).getY();
			for (int set = 0; set < 2; set++) {
				int warn = 30 + set * 30;
				for (double x = room.minX + 1.5; x < room.maxX - 1.0; x += 3.0) {
					for (double z = room.minZ + 1.5; z < room.maxZ - 1.0; z += 3.0) {
						int i = (int) ((x - room.minX) / 3) + (int) ((z - room.minZ) / 3);
						if (Math.floorMod(i, 2) != set) continue;
						k.mark(new Vec3(x, y, z), 0.0, 1.5, warn, ParticleTypes.ENCHANT, false, (l, m) -> {
							k.blow(m.at, 0.0, m.outer, k.damage(), 0.2, 0.2, null);
							l.sendParticles(ParticleTypes.ENCHANTED_HIT, m.at.x, m.at.y + 0.3, m.at.z, 8, 0.6, 0.1, 0.6, 0.1);
						});
					}
				}
			}
			k.playSound(com.vnap.sound.SpireSounds.CHALK, 2.0F, 1.0F);
		}

		/** The hammer's blow along the floor towards each climber, block by block. */
		private static void hammer(SpireKeeper k) {
			k.cast(25);
			k.playSound(com.vnap.sound.SpireSounds.MARK_IMPACT, 1.6F, 0.8F);
			for (Player player : k.climbers()) {
				Vec3 way = player.position().subtract(k.position()).multiply(1.0, 0.0, 1.0);
				if (way.lengthSqr() < 1.0E-4) continue;
				way = way.normalize();
				for (int i = 0; i < 8; i++) {
					Vec3 at = k.position().add(way.scale(1.5 + i * 1.5)).add(0.0, player.getY() - k.getY(), 0.0);
					if (!k.room().inflate(0.5).contains(at)) break;
					k.mark(at, 0.0, 1.1, 15 + 3 * i, block(Blocks.COBBLESTONE, false), false, (l, m) -> {
						k.blow(m.at, 0.0, m.outer, k.damage() * 1.2F, 0.4, 0.5, null);
						l.sendParticles(block(Blocks.COBBLESTONE, false), m.at.x, m.at.y + 0.4, m.at.z, 16, 0.4, 0.3, 0.4, 0.1);
						l.playSound(null, BlockPos.containing(m.at), com.vnap.sound.SpireSounds.MARK_IMPACT, net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.1F);
					});
				}
			}
		}

		/** A wall laid behind each climber, across its way back from the Mason: in the way, and cover from the beam. */
		private static void walls(SpireKeeper k) {
			BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
			for (Player player : k.climbers()) {
				Vec3 way = player.position().subtract(k.position()).multiply(1.0, 0.0, 1.0);
				if (way.lengthSqr() < 1.0E-4) continue;
				way = way.normalize();
				Vec3 across = new Vec3(-way.z, 0.0, way.x);
				Vec3 middle = player.position().add(way.scale(2.5));
				for (int i = -2; i <= 2; i++) {
					Vec3 at = middle.add(across.scale(i));
					for (int h = 0; h < 3; h++) {
						BlockPos pos = BlockPos.containing(at.x, player.getY() + h, at.z);
						int delay = 4 * (i + 2) + 2 * h;
						k.later(1 + delay, (l, m) -> k.raise(l, pos, stone, 200));
					}
				}
			}
			k.playSound(com.vnap.sound.SpireSounds.WALL_RISE, 2.0F, 1.0F);
		}

		/** A mark on the climber; the keeper comes down on it with all its weight. */
		private static void leap(SpireKeeper k, int colour) {
			LivingEntity target = aim(k);
			if (target == null) return;
			k.cast(40);
			k.mark(target.position(), 0.0, 2.2, 35, Mc.dust(colour, 1.6F), true, (l, m) -> {
				if (l.noCollision(k, k.getBoundingBox().move(m.at.subtract(k.position())))) Mc.snap(k, m.at.x, m.at.y, m.at.z, k.getYRot(), k.getXRot());
				k.blow(m.at, 0.0, m.outer, k.damage() * 1.5F, 0.9, 0.6, null);
				l.sendParticles(ParticleTypes.EXPLOSION, m.at.x, m.at.y + 0.3, m.at.z, 2, 0.5, 0.1, 0.5, 0.0);
				l.sendParticles(block(Blocks.STONE_BRICKS, false), m.at.x, m.at.y + 0.3, m.at.z, 40, 1.2, 0.2, 1.2, 0.15);
				k.playSound(com.vnap.sound.SpireSounds.MARK_IMPACT, 2.0F, 0.7F);
			});
		}
	}

	// ---- the trials of the half-chapters (floors 5, 15 … 95) ------------------------------------------

	/** Where a keeper's room's floor is, under a point (the climbers' level, or its own). */
	static Vec3 floorAt(SpireKeeper k, double x, double z) {
		List<Player> climbers = k.climbers();
		double y = climbers.isEmpty() ? k.getY() : climbers.get(0).getY();
		return new Vec3(x, y, z);
	}

	/** The loudest climber: running and striking are loud, crouching quiet; null when all keep still. */
	static @Nullable Player loudest(SpireKeeper k) {
		Player best = null;
		int most = 0;
		for (Player player : k.climbers()) {
			int noise = 1 + (player.isSprinting() ? 3 : 0) + (player.tickCount - player.getLastHurtMobTimestamp() < 40 ? 3 : 0) - (player.isShiftKeyDown() ? 3 : 0);
			if (noise > most) {
				most = noise;
				best = player;
			}
		}
		return best;
	}

	/**
	 * I½. The Root Wyrm (Seven Dimensions' sandworm, Zelda's Moldorm): a head and six coils on the floor that follow
	 * its path; hits on a coil go to the head, half as hard — on the tail half again as hard. It goes under the
	 * floor and a ridge of earth crawls to the loudest climber (running and striking are loud, crouching keeps you
	 * hidden), then it bursts up under them and lies stunned. Later it goes under oftener.
	 */
	static final class RootWyrm extends Art {
		private static final int COILS = 6;
		private static final int UNDER = 0;

		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.GREEN;
		}

		@Override
		int every() {
			return 220;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			// under for four seconds at the most: it is out (and open) most of the fight
			k.memory[UNDER] = phase == 3 ? 60 : 80;
			k.setInvisible(true);
			for (SpireGuard coil : k.parts(SpireGuard.Part.SEGMENT)) coil.setInvisible(true);
			k.untouchable(k.memory[UNDER]);
			k.playSound(com.vnap.sound.SpireSounds.WYRM_BURROW, 2.0F, 1.0F);
			level.sendParticles(block(Blocks.ROOTED_DIRT, false), k.getX(), k.getY() + 0.5, k.getZ(), 60, 1.2, 0.4, 1.2, 0.1);
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			if (k.parts(SpireGuard.Part.SEGMENT).isEmpty() && k.memory[7] == 0) {
				k.memory[7] = 1;
				for (int i = 0; i < COILS; i++) {
					float size = 1.6F - i * 0.13F;
					k.part(level, SpireGuard.Part.SEGMENT, Genome.Body.WYRM_SEGMENT, Genome.Role.TANK, List.of(Attack.STRIKE), size, k.position());
				}
			}
			// the coils along its path, a few steps apart
			List<Vec3> path = new java.util.ArrayList<>(k.trail());
			List<SpireGuard> coils = k.parts(SpireGuard.Part.SEGMENT);
			for (int i = 0; i < coils.size(); i++) {
				SpireGuard coil = coils.get(i);
				Vec3 at = path.isEmpty() ? k.position() : path.get(Math.min(path.size() - 1, (i + 1) * 5));
				Vec3 ahead = path.isEmpty() ? k.position() : path.get(Math.min(path.size() - 1, i * 5));
				float yaw = (float) Math.toDegrees(Math.atan2(ahead.z - at.z, ahead.x - at.x)) - 90.0F;
				Mc.snap(coil, at.x, at.y, at.z, yaw, 0.0F);
				coil.setInvisible(k.isInvisible());
			}
			if (k.memory[UNDER] <= 0) return;
			k.memory[UNDER]--;
			Player prey = loudest(k);
			if (k.tickCount % 2 == 0) {
				level.sendParticles(block(Blocks.ROOTED_DIRT, false), k.getX(), k.getY() + 0.1, k.getZ(), 6, 0.6, 0.1, 0.6, 0.02);
				if (k.tickCount % 20 == 0) k.playSound(com.vnap.sound.SpireSounds.WYRM_BURROW, 0.8F, 1.2F);
			}
			if (prey == null) {
				// nobody makes a sound: it wanders under the floor
				if (k.tickCount % 40 == 0) {
					Vec3 to = k.near(new Vec3(k.room().getCenter().x, k.getY(), k.room().getCenter().z), 6.0);
					k.getNavigation().moveTo(to.x, to.y, to.z, 1.4);
				}
			} else if (k.tickCount % 5 == 0) {
				k.getNavigation().moveTo(prey, 1.6);
			}
			if (prey != null && (k.distanceTo(prey) < 2.5 || k.memory[UNDER] == 1)) {
				k.memory[UNDER] = 0;
				Vec3 at = prey.position();
				k.getNavigation().stop();
				k.mark(at, 0.0, 2.0, 18, block(Blocks.ROOTED_DIRT, false), false, (l, m) -> erupt(k, l, m.at));
			} else if (k.memory[UNDER] == 1) {
				k.memory[UNDER] = 0;
				erupt(k, level, k.position());
			}
		}

		private static void erupt(SpireKeeper k, ServerLevel level, Vec3 at) {
			Mc.snap(k, at.x, at.y, at.z, k.getYRot(), 0.0F);
			k.setInvisible(false);
			k.calm();
			k.blow(at, 0.0, 2.0, k.damage() * 1.6F, 0.6, 1.0, null);
			level.sendParticles(block(Blocks.ROOTED_DIRT, false), at.x, at.y + 0.5, at.z, 90, 1.0, 1.0, 1.0, 0.25);
			k.playSound(com.vnap.sound.SpireSounds.WYRM_ERUPT, 2.0F, 1.0F);
			k.stun(60);
		}

		@Override
		Genome minion(SpireKeeper k, long seed) {
			return small(k, seed, Genome.Movement.WALK, Genome.Body.HOUND, Genome.Role.SWARM, List.of(Attack.STRIKE), 0.6F);
		}
	}

	/**
	 * II½. The Binder (Hollow Knight's Nosk, the Lich's copies): she lays out three of her own likeness about the
	 * room and changes places with one of them in a puff of ink; a likeness struck bursts in ink in the striker's
	 * face. The real one writes as she goes — light runs off her quill.
	 */
	static final class Binder extends Art {
		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.YELLOW;
		}

		@Override
		BlockState pillar() {
			return Blocks.CHISELED_BOOKSHELF.defaultBlockState();
		}

		@Override
		int every() {
			return 150;
		}

		@Override
		boolean keepsAway() {
			return true;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			int want = phase == 1 ? 3 : 4;
			List<SpireGuard> decoys = k.parts(SpireGuard.Part.DECOY);
			for (int i = decoys.size(); i < want; i++) {
				Vec3 at = k.near(new Vec3(k.room().getCenter().x, k.getY(), k.room().getCenter().z), 6.0);
				SpireGuard decoy = k.part(level, SpireGuard.Part.DECOY, Genome.Body.BINDER, Genome.Role.SWARM,
						phase == 3 ? List.of(Attack.BOLT, Attack.VOLLEY) : List.of(Attack.BOLT), k.genome().size(), at);
				// each of a group gets a decoy of its own to deal with
				List<Player> climbers = k.climbers();
				if (decoy != null) decoy.setTarget(climbers.isEmpty() ? k.getTarget() : climbers.get(i % climbers.size()));
			}
			decoys = k.parts(SpireGuard.Part.DECOY);
			if (decoys.isEmpty()) return;
			SpireGuard swap = decoys.get(k.rng().nextInt(decoys.size()));
			Vec3 mine = k.position();
			Vec3 theirs = swap.position();
			for (Vec3 at : List.of(mine, theirs)) level.sendParticles(Mc.dust(0x2A2640, 1.4F), at.x, at.y + 1.2, at.z, 30, 0.5, 0.8, 0.5, 0.03);
			Mc.snap(k, theirs.x, theirs.y, theirs.z, swap.getYRot(), 0.0F);
			Mc.snap(swap, mine.x, mine.y, mine.z, k.getYRot(), 0.0F);
			k.playSound(com.vnap.sound.SpireSounds.INK, 2.0F, 0.8F);
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			// the tell: light off the real one's quill
			if (k.tickCount % 4 == 0) {
				double a = Math.toRadians(k.yBodyRot);
				level.sendParticles(ParticleTypes.END_ROD, k.getX() - Math.cos(a) * 0.9, k.getY() + k.getBbHeight() * 0.45, k.getZ() - Math.sin(a) * 0.9, 1,
						0.05, 0.05, 0.05, 0.0);
			}
		}
	}

	/**
	 * III½. The Chronarch (Seven Dimensions' Chronos Keeper): its own last four seconds come back as an echo that
	 * strikes where it struck; bubbles of stopped time hold a climber nearly still; at each change it winds itself
	 * back to its health five seconds before — unless enough blows land while it winds, which stuns it.
	 */
	static final class Chronarch extends Art {
		private static final int WINDING = 0;
		private static final int TAKEN = 1;
		private static final java.util.Map<java.util.UUID, List<SpireKeeper.Frame>> REPLAYS = new java.util.WeakHashMap<>();

		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.WHITE;
		}

		@Override
		BlockState pillar() {
			return Blocks.WAXED_COPPER_BLOCK.defaultBlockState();
		}

		@Override
		int every() {
			return 130;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			if (n % 3 == 0) {
				SpireGuard echo = k.part(level, SpireGuard.Part.ECHO, Genome.Body.CHRONARCH, Genome.Role.SWARM, List.of(Attack.STRIKE), k.genome().size(),
						k.frames().isEmpty() ? k.position() : k.frames().get(0).pos());
				if (echo != null) {
					echo.setInvisible(true);
					REPLAYS.put(echo.getUUID(), k.frames());
				}
				k.playSound(com.vnap.sound.SpireSounds.ECHO, 1.6F, 1.0F);
			} else if (n % 3 == 1) {
				for (Player player : k.climbers()) {
					k.mark(player.position(), 0.0, 2.6, 25, ParticleTypes.REVERSE_PORTAL, false, (l, m) -> {
						k.blow(m.at, 0.0, m.outer, k.damage() * 0.4F, 0.0, 0.0, com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.STOPPED, 35, 0));
						l.sendParticles(ParticleTypes.ENCHANT, m.at.x, m.at.y + 1.0, m.at.z, 60, 1.2, 1.0, 1.2, 0.4);
						l.playSound(null, BlockPos.containing(m.at), com.vnap.sound.SpireSounds.CLOCK_STOP, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 1.0F);
					});
				}
			} else {
				k.chain(phase);
			}
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			// it winds itself back: blows while it winds break it off
			k.memory[WINDING] = 80;
			k.memory[TAKEN] = 0;
			k.cast(80);
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			// the echoes replay its last moves, a frame a tick, and strike where it struck
			for (SpireGuard echo : k.parts(SpireGuard.Part.ECHO)) {
				List<SpireKeeper.Frame> frames = REPLAYS.get(echo.getUUID());
				int at = echo.tickCount;
				if (frames == null || at >= frames.size()) {
					echo.discard();
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, echo.getX(), echo.getY() + 1.0, echo.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
					continue;
				}
				SpireKeeper.Frame frame = frames.get(at);
				Mc.snap(echo, frame.pos().x, frame.pos().y, frame.pos().z, frame.yaw(), 0.0F);
				if (frame.strike()) {
					k.blow(frame.pos(), 0.0, 3.0, k.damage() * 0.7F, 0.5, 0.0, null);
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, frame.pos().x, frame.pos().y + 1.0, frame.pos().z, 3, 0.8, 0.3, 0.8, 0.0);
				}
			}
			if (k.memory[WINDING] <= 0) return;
			k.memory[WINDING]--;
			if (k.tickCount % 3 == 0) ring(level, k.position().add(0, 0.2, 0), 2.5 + k.memory[WINDING] % 20 / 10.0);
			if (k.memory[TAKEN] * 100 >= k.getMaxHealth() * 8) {
				k.memory[WINDING] = 0;
				k.stun(80);
				k.playSound(com.vnap.sound.SpireSounds.KEEPER_STUN, 2.0F, 1.0F);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, k.getX(), k.getY() + 1.5, k.getZ(), 50, 0.8, 1.0, 0.8, 0.4);
			} else if (k.memory[WINDING] == 0) {
				float back = k.healthAgo(99);
				if (back > k.getHealth()) k.setHealth(back);
				k.playSound(com.vnap.sound.SpireSounds.CLOCK_REWIND, 2.0F, 1.0F);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, k.getX(), k.getY() + 1.0, k.getZ(), 80, 1.0, 1.5, 1.0, 0.2);
			}
		}

		private static void ring(ServerLevel level, Vec3 at, double r) {
			for (int i = 0; i < 16; i++) {
				double a = Math.PI * 2 * i / 16;
				level.sendParticles(ParticleTypes.ENCHANT, at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r, 1, 0.0, 0.1, 0.0, 0.0);
			}
		}

		@Override
		float taken(SpireKeeper k, DamageSource source, float amount) {
			if (k.memory[WINDING] > 0) k.memory[TAKEN] += (int) Math.ceil(amount * 100);
			return amount;
		}
	}

	/**
	 * IV½. The Heartwood (Seven Dimensions' Biomass Titan): it takes one form after another — blades (quick, close,
	 * dashing), a shield (nothing from the front gets through, shots come back), whips (long, they drag a climber in)
	 * — and roots itself in the floor to mend, which enough blows break off, stunning it.
	 */
	static final class Heartwood extends Art {
		private static final int MENDING = 0;
		private static final int TAKEN = 1;
		private static final int BLADES = 1;
		private static final int SHIELD = 2;
		private static final int WHIPS = 3;

		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.GREEN;
		}

		@Override
		BlockState pillar() {
			return Blocks.MOSS_BLOCK.defaultBlockState();
		}

		@Override
		int every() {
			return 220;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			if (n % 4 == 3 && k.getHealth() < k.getMaxHealth() * 0.9F) {
				k.memory[MENDING] = 100;
				k.memory[TAKEN] = 0;
				k.cast(100);
				k.playSound(com.vnap.sound.SpireSounds.ROOTS, 2.0F, 0.8F);
				return;
			}
			int form = 1 + Math.floorMod(n, 3);
			k.form(form);
			level.sendParticles(ParticleTypes.CHERRY_LEAVES, k.getX(), k.getY() + 2.0, k.getZ(), 60, 1.0, 1.5, 1.0, 0.05);
			k.playSound(com.vnap.sound.SpireSounds.WOOD_MORPH, 2.0F, 1.0F);
			if (form == BLADES) k.chain(phase);
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			if (k.form() == 0) k.form(BLADES);
			if (k.form() == WHIPS && k.tickCount % 60 == 0) {
				// the whips: a lash along the floor to each climber, and it hauls them in
				for (Player player : k.climbers()) {
					Vec3 from = k.position().add(0, 1.0, 0);
					Vec3 to = player.position().add(0, 1.0, 0);
					k.line(level, from, to, ParticleTypes.COMPOSTER, 0.4);
					k.later(15, (l, m) -> {
						if (player.isAlive() && player.distanceTo(k) < 14) {
							k.hurtOne(player, k.damage() * 0.8F, 0.0);
							Vec3 pull = k.position().subtract(player.position()).normalize().scale(1.1);
							player.push(pull.x, 0.35, pull.z);
							Mc.knocked(player);
						}
					});
				}
			}
			if (k.memory[MENDING] <= 0) return;
			k.memory[MENDING]--;
			if (k.tickCount % 20 == 0) k.heal(k.getMaxHealth() * 0.015F, level);
			if (k.tickCount % 2 == 0) level.sendParticles(block(Blocks.ROOTED_DIRT, false), k.getX(), k.getY() + 0.2, k.getZ(), 4, 1.0, 0.1, 1.0, 0.02);
			if (k.memory[TAKEN] * 100 >= k.getMaxHealth() * 6) {
				k.memory[MENDING] = 0;
				k.stun(80);
				k.playSound(com.vnap.sound.SpireSounds.KEEPER_STUN, 2.0F, 0.9F);
			}
		}

		@Override
		float taken(SpireKeeper k, DamageSource source, float amount) {
			if (k.memory[MENDING] > 0) k.memory[TAKEN] += (int) Math.ceil(amount * 100);
			if (k.form() != SHIELD || k.stunned()) return amount;
			Vec3 from = source.getSourcePosition();
			if (from == null) return amount;
			Vec3 toward = from.subtract(k.position()).multiply(1.0, 0.0, 1.0);
			Vec3 facing = Vec3.directionFromRotation(0.0F, k.getYRot());
			if (toward.lengthSqr() < 1.0E-4 || toward.normalize().dot(facing) < 0.2) return amount;
			// the shield: from the front nothing gets through, and a shot comes back
			if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile && source.getEntity() instanceof LivingEntity shooter
					&& k.level() instanceof ServerLevel level) {
				k.shoot(level, shooter, k.damage() * 0.8F, 0.0F);
			}
			k.playSound(SoundEvents.SHIELD_BLOCK, 1.2F, 0.6F);
			return 0.0F;
		}
	}

	/**
	 * VI½. The Frost Mirror (the Snow Queen; Ganon's reflected bolts): she sets panes of ice about the room and sends
	 * her beam off one of them round to a climber — a climber hiding behind a pillar is not safe while a pane sees
	 * them. The panes break to a blow; with none left her beam only goes straight.
	 */
	static final class FrostMirror extends Art {
		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.WHITE;
		}

		@Override
		BlockState pillar() {
			return Blocks.PACKED_ICE.defaultBlockState();
		}

		@Override
		int every() {
			return 120;
		}

		@Override
		boolean keepsAway() {
			return true;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			k.spots.removeIf(pos -> !level.getBlockState(pos).is(SpireBlocks.ICE_MIRROR));
			if (n % 3 == 0 || k.spots.isEmpty()) {
				for (int i = 0; i < 2 + phase; i++) {
					Vec3 at = k.near(new Vec3(k.room().getCenter().x, k.getY(), k.room().getCenter().z), 6.5);
					BlockPos foot = BlockPos.containing(at.x, floorAt(k, at.x, at.z).y, at.z);
					for (int h = 0; h < 3; h++) k.raise(level, foot.above(h), SpireBlocks.ICE_MIRROR.defaultBlockState(), 900);
					if (level.getBlockState(foot.above()).is(SpireBlocks.ICE_MIRROR)) k.spots.add(foot.above());
				}
				k.playSound(com.vnap.sound.SpireSounds.ICE_FORM, 2.0F, 1.2F);
				return;
			}
			// her lance turns from one of a group to the next
			List<Player> climbers = k.climbers();
			LivingEntity target = climbers.size() > 1 ? climbers.get(n % climbers.size()) : aim(k);
			if (target == null) return;
			Vec3 eye = k.position().add(0, 1.4, 0);
			Vec3 at = target.position().add(0, 1.0, 0);
			BlockPos pane = null;
			for (BlockPos pos : k.spots) {
				if (pane == null || Vec3.atCenterOf(pos).distanceTo(at) < Vec3.atCenterOf(pane).distanceTo(at)) pane = pos;
			}
			if (pane == null || n % 3 == 2) {
				lance(k, eye, at, k.damage() * 1.2F, 0);
				return;
			}
			Vec3 glass = Vec3.atCenterOf(pane);
			lance(k, eye, glass, k.damage() * 0.6F, 0);
			lance(k, glass, at, k.damage() * 1.3F, 10);
		}

		/** A shaft of frost from a to b: drawn faint first, then it strikes along its length and freezes. */
		private static void lance(SpireKeeper k, Vec3 a, Vec3 b, float damage, int delay) {
			for (int t = 0; t < 24; t += 4) {
				k.later(1 + delay + t, (l, m) -> k.line(l, a, b, ParticleTypes.SNOWFLAKE, 0.8));
			}
			k.later(28 + delay, (l, m) -> {
				k.line(l, a, b, Mc.dust(0xDDF4FF, 1.6F), 0.25);
				k.line(l, a, b, ParticleTypes.END_ROD, 0.6);
				for (Player player : k.climbers()) {
					if (SpireKeeper.across(player.position().add(0, 1.0, 0), a, b) < 0.9 && player.getY() < Math.max(a.y, b.y) + 1.5) {
						k.hurtOne(player, damage, 0.2);
						com.vnap.spire.Afflictions.frost(player, 2);
					}
				}
				l.playSound(null, BlockPos.containing(a), com.vnap.sound.SpireSounds.BEAM_FIRE, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 1.2F);
			});
		}
	}

	/**
	 * VII½. The Mycelium Hydra (Twilight Forest's Hydra, Zelda's Arrghus): a cap that does not move and three heads
	 * that spit spores. While a head stands the cap takes nothing; a head cut off grows again in fifteen seconds unless
	 * the cap is struck hard enough in that time.
	 */
	static final class MycelHydra extends Art {
		private static final int HEADS = 3;
		private static final int REGROW = 0;
		private static final int TAKEN = 1;

		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.PINK;
		}

		@Override
		BlockState pillar() {
			return Blocks.MUSHROOM_STEM.defaultBlockState();
		}

		@Override
		int every() {
			return 140;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			spores(k, level);
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			still(k);
			int heads = k.parts(SpireGuard.Part.HEAD).size();
			if (k.memory[7] == 0) {
				k.memory[7] = 1;
				grow(k, level, HEADS);
				return;
			}
			if (heads < HEADS && k.memory[REGROW] == 0) {
				k.memory[REGROW] = 300;
				k.memory[TAKEN] = 0;
			}
			if (k.memory[REGROW] > 0 && --k.memory[REGROW] == 0) {
				if (k.memory[TAKEN] < k.getMaxHealth() * 5) {
					grow(k, level, HEADS - k.parts(SpireGuard.Part.HEAD).size());
					k.playSound(com.vnap.sound.SpireSounds.HYDRA, 2.0F, 1.0F);
				}
			}
			if (k.tickCount % 4 == 0) {
				for (SpireGuard head : k.parts(SpireGuard.Part.HEAD)) {
					k.line(level, k.position().add(0, 1.0, 0), head.position().add(0, 0.5, 0), ParticleTypes.SPORE_BLOSSOM_AIR, 0.9);
				}
			}
		}

		private static void grow(SpireKeeper k, ServerLevel level, int count) {
			for (int i = 0; i < count; i++) {
				double a = Math.PI * 2 * (i + k.rng().nextDouble() * 0.3) / HEADS;
				Vec3 at = k.position().add(Math.cos(a) * 3.2, 0.0, Math.sin(a) * 3.2);
				SpireGuard head = k.part(level, SpireGuard.Part.HEAD, Genome.Body.HYDRA_HEAD, Genome.Role.SHOOTER, List.of(Attack.BOLT, Attack.VOLLEY), 1.4F, at);
				if (head != null) {
					still(head);
					head.setTarget(k.getTarget());
					level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 1.0, at.z, 30, 0.5, 0.8, 0.5, 0.0);
				}
			}
		}

		@Override
		float taken(SpireKeeper k, DamageSource source, float amount) {
			if (!k.parts(SpireGuard.Part.HEAD).isEmpty()) {
				if (k.level() instanceof ServerLevel level) {
					level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, k.getX(), k.getY() + 1.0, k.getZ(), 10, 0.8, 0.5, 0.8, 0.0);
				}
				k.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 1.4F);
				return 0.0F;
			}
			if (k.memory[REGROW] > 0) k.memory[TAKEN] += (int) Math.ceil(amount * 100);
			return amount;
		}
	}

	/**
	 * VIII½. The Honey Golem: honey drips where the climbers stand, and whoever is still in it when it sets is sealed
	 * in a cell of amber to break out of; it drinks from amber jars at the corners to mend — a jar broken while it
	 * drinks stuns it, and enough blows break the drinking off too.
	 */
	static final class HoneyGolem extends Art {
		private static final int DRINKING = 0;
		private static final int TAKEN = 1;

		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.YELLOW;
		}

		@Override
		int every() {
			return 140;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			k.spots.removeIf(pos -> !level.getBlockState(pos).is(SpireBlocks.AMBER));
			if (n % 3 == 1 && !k.spots.isEmpty() && k.getHealth() < k.getMaxHealth() * 0.95F) {
				BlockPos jar = k.spots.get(k.rng().nextInt(k.spots.size()));
				k.memory[DRINKING] = 120;
				k.memory[TAKEN] = 0;
				k.memory[2] = (int) jar.asLong();
				k.memory[3] = (int) (jar.asLong() >>> 32);
				k.getNavigation().moveTo(jar.getX() + 0.5, jar.getY(), jar.getZ() + 0.5, 1.2);
				return;
			}
			for (Player player : k.climbers()) {
				Vec3 at = player.position();
				k.mark(at, 0.0, 1.6, 40, ParticleTypes.DRIPPING_HONEY, true, (l, m) -> {
					k.lingering(l, m.at, 1.6F, 120, com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.HONEYED, 60, 0), ParticleTypes.DRIPPING_HONEY);
					for (Player stuck : k.climbers()) {
						Vec3 p = stuck.position();
						if (Math.hypot(p.x - m.at.x, p.z - m.at.z) > m.outer || Math.abs(p.y - m.at.y) > 1.5) continue;
						seal(k, l, stuck.blockPosition());
					}
				});
			}
		}

		/** A cell of amber round a climber: four sides two high and a lid, gone in eight seconds, or broken. */
		private static void seal(SpireKeeper k, ServerLevel level, BlockPos feet) {
			BlockState amber = SpireBlocks.AMBER.defaultBlockState();
			for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				k.raise(level, feet.relative(side), amber, 160);
				k.raise(level, feet.relative(side).above(), amber, 160);
			}
			k.raise(level, feet.above(2), amber, 160);
			level.playSound(null, feet, com.vnap.sound.SpireSounds.HONEY_SEAL, net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 0.8F);
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			if (phase != 2) return;
			// the jars at the four corners
			for (int[] c : new int[][] {{5, 5}, {-5, 5}, {5, -5}, {-5, -5}}) {
				BlockPos foot = k.home().offset(c[0], 0, c[1]);
				k.raise(level, foot, SpireBlocks.AMBER.defaultBlockState(), 20000);
				k.raise(level, foot.above(), SpireBlocks.AMBER.defaultBlockState(), 20000);
				if (level.getBlockState(foot).is(SpireBlocks.AMBER)) k.spots.add(foot);
			}
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			if (k.memory[DRINKING] <= 0) return;
			k.memory[DRINKING]--;
			BlockPos jar = BlockPos.of((k.memory[2] & 0xFFFFFFFFL) | ((long) k.memory[3] << 32));
			if (!level.getBlockState(jar).is(SpireBlocks.AMBER) || k.memory[TAKEN] * 100 >= k.getMaxHealth() * 6) {
				k.memory[DRINKING] = 0;
				k.stun(90);
				k.playSound(com.vnap.sound.SpireSounds.KEEPER_STUN, 2.0F, 1.1F);
				return;
			}
			if (k.distanceToSqr(Vec3.atCenterOf(jar)) < 12.0) {
				k.getNavigation().stop();
				if (k.tickCount % 20 == 0) k.heal(k.getMaxHealth() * 0.02F, level);
				k.line(level, Vec3.atCenterOf(jar), k.position().add(0, 2.0, 0), ParticleTypes.DRIPPING_HONEY, 0.6);
			}
		}

		@Override
		float taken(SpireKeeper k, DamageSource source, float amount) {
			if (k.memory[DRINKING] > 0) k.memory[TAKEN] += (int) Math.ceil(amount * 100);
			return amount;
		}
	}

	/**
	 * IX½. The Gravitor (Seven Dimensions' Rift Guardian, the Lich's shields): four planets turn round it and take
	 * most of every blow while they last (they can be struck down); it turns the room's pull over (the climbers rise
	 * and float down), and floor stones rise under a climber and fling them up.
	 */
	static final class Gravitor extends Art {
		private static final int PLANETS = 4;

		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.PURPLE;
		}

		@Override
		BlockState pillar() {
			return Blocks.AMETHYST_BLOCK.defaultBlockState();
		}

		@Override
		int every() {
			return 130;
		}

		@Override
		boolean keepsAway() {
			return true;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			switch (n % 3) {
				case 0 -> {
					for (Player player : k.climbers()) {
						player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 2));
						player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 90, 0));
						level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
					}
					k.playSound(com.vnap.sound.SpireSounds.GRAVITY, 2.0F, 0.8F);
				}
				case 1 -> {
					for (Player player : k.climbers()) {
						BlockPos foot = player.blockPosition();
						k.mark(player.position(), 0.0, 1.3, 25, block(Blocks.CALCITE, false), false, (l, m) -> {
							for (int h = 0; h < 2; h++) k.raise(l, foot.above(h), Blocks.CALCITE.defaultBlockState(), 60);
							k.blow(m.at, 0.0, m.outer + 0.5, k.damage() * 1.2F, 0.3, 1.3, null);
							l.sendParticles(block(Blocks.CALCITE, false), m.at.x, m.at.y + 1.0, m.at.z, 30, 0.5, 0.8, 0.5, 0.1);
						});
					}
				}
				default -> starfall(k, level, 3 + phase);
			}
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			k.memory[7] = 0;
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			List<SpireGuard> planets = k.parts(SpireGuard.Part.PLANET);
			if (k.memory[7] == 0) {
				k.memory[7] = 1;
				for (int i = planets.size(); i < PLANETS; i++) {
					SpireGuard planet = k.part(level, SpireGuard.Part.PLANET, Genome.Body.PLANET, Genome.Role.SWARM, List.of(Attack.STRIKE), 1.0F, k.position());
					if (planet != null) {
						AttributeInstance health = planet.getAttribute(Attributes.MAX_HEALTH);
						if (health != null) health.setBaseValue(Math.max(6.0, k.getMaxHealth() * 0.05));
						planet.setHealth(planet.getMaxHealth());
					}
				}
				planets = k.parts(SpireGuard.Part.PLANET);
			}
			for (int i = 0; i < planets.size(); i++) {
				double a = k.tickCount * 0.05 + Math.PI * 2 * i / planets.size();
				Mc.snap(planets.get(i), k.getX() + Math.cos(a) * 3.0, k.getY() + 1.6 + Math.sin(a * 2) * 0.5, k.getZ() + Math.sin(a) * 3.0, 0.0F, 0.0F);
			}
		}

		@Override
		float taken(SpireKeeper k, DamageSource source, float amount) {
			return k.parts(SpireGuard.Part.PLANET).isEmpty() ? amount : amount * 0.2F;
		}
	}

	/**
	 * X½. The Reflection (Seven Dimensions' Doppelganger, Zelda's Dark Link): it takes the climber's own blow and
	 * armour, keeps to the place across the room that mirrors theirs and closes in when they do; a blow from behind
	 * goes deep. At each change it splits — one more reflection, then two — and only the real one bears the crack.
	 */
	static final class Reflection extends Art {
		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.WHITE;
		}

		@Override
		int every() {
			return 160;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			k.chain(1);
		}

		@Override
		void phase(SpireKeeper k, ServerLevel level, int phase) {
			for (int i = k.parts(SpireGuard.Part.DECOY).size(); i < phase - 1; i++) {
				Vec3 at = k.near(new Vec3(k.room().getCenter().x, k.getY(), k.room().getCenter().z), 5.0);
				SpireGuard copy = k.part(level, SpireGuard.Part.DECOY, Genome.Body.REFLECTION, Genome.Role.FIGHTER, List.of(Attack.STRIKE, Attack.DASH),
						k.genome().size(), at);
				if (copy != null) {
					copy.wear(copy.worn() & ~(3 << Genome.FORM_SHIFT));
					List<Player> others = others(k, k.getTarget());
					copy.setTarget(others.isEmpty() ? k.getTarget() : others.get(i % others.size()));
				}
				level.sendParticles(ParticleTypes.WAX_ON, at.x, at.y + 1.0, at.z, 40, 0.4, 0.8, 0.4, 0.2);
				k.playSound(com.vnap.sound.SpireSounds.GLASS_SPLIT, 2.0F, 1.0F);
			}
		}

		@Override
		void tick(SpireKeeper k, ServerLevel level, int phase) {
			if (k.form() != 1) k.form(1);
			LivingEntity target = aim(k);
			// it mirrors one; for each other of a group a copy of it steps out of the glass from the start
			List<Player> others = others(k, target);
			if (k.tickCount % 100 == 0 && !others.isEmpty() && k.parts(SpireGuard.Part.DECOY).size() < Math.min(others.size(), 1 + phase)) {
				Player other = others.get(k.rng().nextInt(others.size()));
				Vec3 at = k.near(other.position(), 3.0);
				SpireGuard copy = k.part(level, SpireGuard.Part.DECOY, Genome.Body.REFLECTION, Genome.Role.FIGHTER, List.of(Attack.STRIKE, Attack.DASH),
						k.genome().size(), at);
				if (copy != null) {
					copy.wear(copy.worn() & ~(3 << Genome.FORM_SHIFT));
					copy.setTarget(other);
				}
				level.sendParticles(ParticleTypes.WAX_ON, at.x, at.y + 1.0, at.z, 40, 0.4, 0.8, 0.4, 0.2);
				k.playSound(com.vnap.sound.SpireSounds.GLASS_SPLIT, 2.0F, 1.0F);
			}
			if (!(target instanceof Player player)) return;
			// its blow and armour the climber's own
			if (k.tickCount % 40 == 0) {
				AttributeInstance damage = k.getAttribute(Attributes.ATTACK_DAMAGE);
				AttributeInstance armour = k.getAttribute(Attributes.ARMOR);
				if (damage != null) damage.setBaseValue(Math.max(k.genome().damage(), player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6));
				if (armour != null) armour.setBaseValue(Math.max(4.0, player.getAttributeValue(Attributes.ARMOR)));
			}
			if (k.tickCount % 10 == 0 && k.distanceTo(player) > 5.0) {
				// the place across the room that mirrors the climber's
				Vec3 middle = k.room().getCenter();
				Vec3 mirrored = new Vec3(middle.x * 2 - player.getX(), player.getY(), middle.z * 2 - player.getZ());
				k.getNavigation().moveTo(mirrored.x, mirrored.y, mirrored.z, 1.3);
			}
		}

		@Override
		float taken(SpireKeeper k, DamageSource source, float amount) {
			Vec3 from = source.getSourcePosition();
			if (from == null) return amount;
			Vec3 toward = from.subtract(k.position()).multiply(1.0, 0.0, 1.0);
			Vec3 facing = Vec3.directionFromRotation(0.0F, k.getYRot());
			return toward.lengthSqr() > 1.0E-4 && toward.normalize().dot(facing) < -0.4 ? amount * 2.0F : amount;
		}
	}

	// ---- the Nameless (outer/Rush) ---------------------------------------------------------------

	/**
	 * A Nameless boss's arts: drawn from its seed out of the pieces every keeper shares and that keep nothing of their
	 * own (Nimbatus's bosses: a few patterns, hundreds of bosses) — stones from above, roots, a starfall, spores, a
	 * sweeping beam, a step away — three at its first phase, one more at each change; its bar's colour and what its
	 * blows leave on a climber its own too.
	 */
	static final class Patchwork extends Art {
		private static final int PIECES = 6;
		private static final BossEvent.BossBarColor[] COLOURS = BossEvent.BossBarColor.values();
		private static final com.vnap.spire.Afflictions.Affliction[] AFFLICTIONS = {com.vnap.spire.Afflictions.ENTANGLED, com.vnap.spire.Afflictions.INKED,
				com.vnap.spire.Afflictions.JOLTED, com.vnap.spire.Afflictions.SAPPED, com.vnap.spire.Afflictions.FROSTBITE, com.vnap.spire.Afflictions.BLIGHT,
				com.vnap.spire.Afflictions.STUNG, com.vnap.spire.Afflictions.HONEYED, com.vnap.spire.Afflictions.SUNDERED};

		/** Its pieces, by number, in the order it has them (the first three from the start). */
		static int[] pieces(long seed) {
			java.util.List<Integer> all = new java.util.ArrayList<>();
			for (int i = 0; i < PIECES; i++) all.add(i);
			java.util.Collections.shuffle(all, new java.util.Random(com.vnap.spire.FloorGenerator.mix(seed, 47)));
			return all.stream().mapToInt(Integer::intValue).toArray();
		}

		@Override
		BossEvent.BossBarColor colour() {
			return BossEvent.BossBarColor.PURPLE;
		}

		@Override
		int every() {
			return 110;
		}

		@Override
		void art(SpireKeeper k, ServerLevel level, int phase, int n) {
			int[] pieces = pieces(k.genome().seed());
			int has = Math.min(pieces.length, 2 + phase);
			LivingEntity target = aim(k);
			switch (pieces[Math.floorMod(n, has)]) {
				case 0 -> rockfall(k, level, phase - 1, Blocks.COBBLED_DEEPSLATE);
				case 1 -> roots(k, level);
				case 2 -> starfall(k, level, 2 + phase * 2);
				case 3 -> spores(k, level);
				case 4 -> sweep(k, Math.PI * (0.5 + 0.25 * phase), phase, ParticleTypes.END_ROD);
				default -> relocate(k, target);
			}
		}

		@Override
		void afflict(SpireKeeper k, LivingEntity target) {
			long seed = k.genome().seed();
			com.vnap.spire.Afflictions.Affliction affliction = AFFLICTIONS[(int) Math.floorMod(seed, (long) AFFLICTIONS.length)];
			if (!com.vnap.spire.Afflictions.has(target, affliction)) target.addEffect(com.vnap.spire.Afflictions.of(affliction, 80, 0));
		}

		/** Its bar's own colour (the shared Art's is the same for every one of a kind). */
		static BossEvent.BossBarColor colourOf(long seed) {
			return COLOURS[(int) Math.floorMod(seed, (long) COLOURS.length)];
		}
	}
}
