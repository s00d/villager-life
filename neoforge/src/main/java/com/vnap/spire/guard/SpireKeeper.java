package com.vnap.spire.guard;

import com.vnap.Mc;
import com.vnap.Nbt;
import com.vnap.spire.Attack;
import com.vnap.spire.Genome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The keeper of a chapter, on every tenth floor in the exit room (DESIGN §5): a bar over the screen and a fight in
 * three phases, each keeper with arts of its own (KeeperArts). First it meets the climber with two of its attacks and
 * its art; under two thirds of its health it rears and the room changes (pillars of its chapter rise round it) and its
 * second art opens; under a third it uses all it has, faster. Each change is a pause, untouchable for a moment.
 * <p>
 * The arts are built of a few readable pieces, all shown before they hurt: marks on the floor (a ring and a filling
 * disc, then the blow), beams that sweep from a line drawn first, things that roll across the room, shards orbiting
 * it, a pull towards it. A stun is the climber's window: it stands, its guard down, and takes half again.
 */
public final class SpireKeeper extends SpireGuard {
	/** A pause at the start of the fight and at each change of phase, untouchable. */
	private static final int PAUSE = 40;
	private static final int PILLAR = 3;
	private static final float STUNNED = 1.5F;

	private final ServerBossEvent bar = new ServerBossEvent(
			//? if >=26.1
			/*java.util.UUID.randomUUID(),*/
			net.minecraft.network.chat.Component.translatable("entity.villager_life.spire_keeper"),
			BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
	private int phase = 1;
	private int pause;
	private boolean met;
	private int rising;
	/** Ticks to its next art, how many it has done. */
	private int next = 60;
	private int arts;
	private int stun;
	/** Ticks it stands still for an art of its own (a breath, a dive), its attacks held. */
	private int casting;
	private int pull;
	private float shell;
	private boolean shelled;
	private int chain;
	private int orbit;
	private final List<Mark> marks = new ArrayList<>();
	private final List<Beam> beams = new ArrayList<>();
	private final List<Roller> rollers = new ArrayList<>();
	/** Blocks its arts put up for a while (the Mason's walls), taken down when it falls. */
	private final List<BlockPos> raised = new ArrayList<>();
	private KeeperArts.Art art = KeeperArts.of(0);
	/** Where it went, newest first (a worm's segments are laid along it). */
	private final java.util.ArrayDeque<Vec3> trail = new java.util.ArrayDeque<>();
	/** Its last moves for an echo to replay: position, facing, and whether it struck then. */
	private final java.util.ArrayDeque<Frame> frames = new java.util.ArrayDeque<>();
	/** A few numbers an art keeps on its keeper (a form, a timer, a count): the arts themselves are shared. */
	public final int[] memory = new int[8];
	/** Places an art keeps (the Frost Mirror's panes, the Honey Golem's jars). */
	public final List<BlockPos> spots = new ArrayList<>();
	/** Its health a tick at a time over the last five seconds (the Chronarch rewinds to it). */
	private final float[] past = new float[100];
	private boolean struck;
	/** How many climbers its health was set for: more come in, it grows to meet them. */
	private int scaledFor = 1;
	/** Ticks till its fight's music starts again (a track's length). */
	private int music;
	/** Its coming (KeeperShow): -1 waiting unseen for a climber, then 0..ENTER, then done (ENTER + 1). */
	private int entering = -1;
	private @Nullable Vec3 enterAt;
	/** Its end (KeeperShow): -1 alive, then 0..FALL; the blow that felled it, for the death at the end. */
	private int dying = -1;
	private @Nullable DamageSource felledBy;
	private boolean finishing;

	record Frame(Vec3 pos, float yaw, boolean strike) {
	}

	static final int TRAIL = 120;
	static final int FRAMES = 80;

	public SpireKeeper(EntityType<? extends SpireKeeper> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5));
	}

	@Override
	public void setup(Genome genome, String theme, BlockPos home, AABB room, AABB box) {
		super.setup(genome, theme, home, room, box);
		art = KeeperArts.of(genome.keeper());
		bar.setName(getDisplayName());
		bar.setColor(art.colour());
	}

	public ServerBossEvent bossBar() {
		return bar;
	}

	public int phase() {
		return phase;
	}

	public boolean stunned() {
		return stun > 0;
	}

	public float shell() {
		return shell;
	}

	@Override
	protected List<Attack> attacks() {
		List<Attack> all = genome.attacks();
		return phase == 1 ? all.subList(0, Math.min(2, all.size())) : all;
	}

	@Override
	protected double haste() {
		return phase == 3 ? 1.6 : phase == 2 ? 1.25 : 1.0;
	}

	@Override
	protected boolean fits(Attack attack, LivingEntity target, double distance) {
		return pause <= 0 && stun <= 0 && casting <= 0 && super.fits(attack, target, distance);
	}

	@Override
	protected boolean keepsAway() {
		return art.keepsAway();
	}

	@Override
	protected Genome minion(long seed) {
		return art.minion(this, seed);
	}

	/**
	 * A keeper's call for help is shown before it is answered: rings of the Archive's light where its little ones
	 * will stand, and only then they step out — two, three from its first change, never more than four of them about.
	 */
	@Override
	protected void summon(ServerLevel level) {
		int about = 0;
		for (SpireGuard guard : company()) if (guard.part() == Part.NONE) about++;
		int want = Math.min(4 - about, phase >= 2 ? 3 : 2);
		for (int i = 0; i < want; i++) {
			Vec3 at = near(position(), 4.0);
			long seed = genome.seed() + tickCount * 7L + i;
			mark(at, 0.0, 1.2, 25, ParticleTypes.REVERSE_PORTAL, false, (l, m) -> {
				BlockPos spot = BlockPos.containing(m.at);
				if (!l.noCollision(EntityType.ZOMBIE.getSpawnAABB(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5))) spot = blockPosition();
				SpireGuard one = bring(l, minion(seed), spot);
				if (one != null) one.setTarget(getTarget());
				l.sendParticles(ParticleTypes.REVERSE_PORTAL, m.at.x, m.at.y + 1.0, m.at.z, 30, 0.3, 0.8, 0.3, 0.1);
			});
		}
		playSound(com.vnap.sound.SpireSounds.POWER, 1.6F, 1.4F);
	}

	/**
	 * A keeper's affliction rides only on its big, telegraphed blows — a slam, a quake, a dash, a leap, a volley —
	 * never on a plain strike or bolt, and is not renewed while it lasts: dodge those and you stay clean.
	 */
	@Override
	public void sting(LivingEntity target, @Nullable Attack by) {
		if (by == Attack.SLAM || by == Attack.QUAKE || by == Attack.DASH || by == Attack.LEAP || by == Attack.VOLLEY) art.afflict(this, target);
	}

	@Override
	protected void landed(ServerLevel level, Attack attack) {
		struck = true;
		art.landed(this, level, attack);
	}

	/** Its path so far, newest first. */
	public java.util.Deque<Vec3> trail() {
		return trail;
	}

	/** Its last moves, oldest first, for an echo. */
	public List<Frame> frames() {
		return new ArrayList<>(frames);
	}

	/** Whether this part is the last of its segments (the soft tail). */
	public boolean tail(SpireGuard segment) {
		SpireGuard last = null;
		for (SpireGuard part : company()) if (part.part() == Part.SEGMENT) last = part;
		return segment.equals(last);
	}

	/** Its parts of one kind. */
	public List<SpireGuard> parts(Part kind) {
		List<SpireGuard> out = new ArrayList<>();
		for (SpireGuard guard : company()) if (guard.part() == kind) out.add(guard);
		return out;
	}

	/** Brings a part of its own (a segment, a head, a planet, a decoy, an echo) at a spot. */
	public @Nullable SpireGuard part(ServerLevel level, Part kind, Genome.Body body, Genome.Role role, List<Attack> attacks, float size, Vec3 at) {
		Genome small = Genome.part(genome, genome.seed() + company().size() * 31L + tickCount, body, role, attacks, size);
		SpireGuard guard = summon(level, small, BlockPos.containing(at));
		if (guard != null) {
			guard.partOf(this, kind);
			Mc.snap(guard, at.x, at.y, at.z, getYRot(), 0.0F);
		}
		return guard;
	}

	/** Its form for the client (two bits of the look). */
	public void form(int form) {
		wear((worn() & ~(3 << Genome.FORM_SHIFT)) | ((form & 3) << Genome.FORM_SHIFT));
	}

	public int form() {
		return look().form();
	}

	public void heal(float amount, ServerLevel level) {
		heal(amount);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight(), getZ(), 4, 0.5, 0.3, 0.5, 0.0);
	}

	public int pause() {
		return pause;
	}

	/** Its health {@code ticks} ago (at most five seconds). */
	public float healthAgo(int ticks) {
		float then = past[Math.floorMod(tickCount - Math.min(ticks, past.length - 1), past.length)];
		return then > 0 ? then : getHealth();
	}

	public void phaseTo(int phase) {
		this.phase = phase;
	}

	@Override
	protected void finished(ServerLevel level, Attack attack) {
		if (attack == Attack.DASH && chain > 0) {
			chain--;
			ready(Attack.DASH);
		}
	}

	/** What keepers cost the server: nanoseconds and ticks of their own work since last read (SpirePerformanceGameTests, /vnap spire bench). */
	private static long benchNanos;
	private static long benchTicks;
	static final int MOST_MARKS = 160;
	static final int MOST_RAISED = 96;

	public static long[] bench(boolean reset) {
		long[] out = {benchNanos, benchTicks};
		if (reset) {
			benchNanos = 0;
			benchTicks = 0;
		}
		return out;
	}

	public int marking() {
		return marks.size();
	}

	public int raising() {
		return raised.size();
	}

	@Override
	public void aiStep() {
		long started = System.nanoTime();
		try {
			step();
		} finally {
			if (!level().isClientSide()) {
				benchNanos += System.nanoTime() - started;
				benchTicks++;
			}
		}
	}

	private void step() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level) || !isAlive()) return;
		if (show(level)) return;
		if (tickCount % 5 == 0 && com.vnap.mood.MoodConfig.debugCommands()) tellMarks();
		bar.setProgress(getHealth() / getMaxHealth());
		if (!met && getTarget() instanceof Player) {
			met = true;
			rear(level);
		}
		if (pause > 0) pause--;
		if (met && tickCount % 40 == 0) group();
		if (met && --music <= 0) {
			// again when the track is over (their lengths: keeper 131 s, trial 123 s, the Mind's 176 s)
			music = genome.keeper() == 9 ? 3540 : genome.keeper() >= Genome.MASON ? 2480 : 2640;
			level.playSound(null, getX(), getY(), getZ(), music(), net.minecraft.sounds.SoundSource.RECORDS, 3.0F, 1.0F);
		}
		float left = getHealth() / getMaxHealth();
		int due = left <= 1.0F / 3.0F ? 3 : left <= 2.0F / 3.0F ? 2 : 1;
		while (phase < due) {
			phase++;
			rear(level);
			if (phase == 2 && art.pillar() != null) rising = PILLAR * 6;
			art.phase(this, level, phase);
		}
		if (rising > 0 && rising-- % 6 == 0) raise(level, PILLAR - rising / 6);
		if (stun > 0 || casting > 0) getNavigation().stop();
		if (stun > 0 && stun-- % 4 == 0) {
			level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() + 0.3, getZ(), 3, 0.4, 0.1, 0.4, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.6, getZ(), 2, 0.6, 0.4, 0.6, 0.02);
		}
		if (casting > 0) casting--;
		if (met && pause <= 0 && stun <= 0 && casting <= 0 && --next <= 0) {
			playSound(com.vnap.sound.SpireSounds.MARK_WARN, 1.4F, 1.0F);
			art.art(this, level, phase, arts++);
			next = (int) (art.every() / haste());
		}
		if (met) art.tick(this, level, phase);
		past[tickCount % past.length] = getHealth();
		trail.addFirst(position());
		if (trail.size() > TRAIL) trail.removeLast();
		frames.addLast(new Frame(position(), getYRot(), struck));
		struck = false;
		if (frames.size() > FRAMES) frames.removeFirst();
		marks(level);
		beams(level);
		rollers(level);
		if (pull > 0) pulling(level);
		if (orbit > 0) orbiting(level);
		if (shelled && shell > 0 && tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + getBbHeight() * 0.5, getZ(), 2, getBbWidth() * 0.6, getBbHeight() * 0.4,
					getBbWidth() * 0.6, 0.0);
		}
	}

	/**
	 * More climbers in its room than it was set for: half again its health for each more (its fraction kept), never
	 * less after one leaves — a group fights a keeper, not a quarter of one.
	 */
	private void group() {
		int now = Math.min(4, climbers().size());
		if (now <= scaledFor) return;
		float fraction = getHealth() / getMaxHealth();
		net.minecraft.world.entity.ai.attributes.AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
		if (health == null) return;
		health.setBaseValue(genome.health() * (1.0 + 0.5 * (now - 1)));
		setHealth(getMaxHealth() * fraction);
		scaledFor = now;
	}

	/** Its fight's music: the Mind's own, the trials' and the Schism's, the keepers'. */
	private net.minecraft.sounds.SoundEvent music() {
		int k = genome.keeper();
		return k == 9 ? com.vnap.sound.SpireSounds.MUSIC_MIND : k >= Genome.MASON ? com.vnap.sound.SpireSounds.MUSIC_TRIAL : com.vnap.sound.SpireSounds.MUSIC_KEEPER;
	}

	/** It rears: a pause, a sound, a ring of light, the bar takes the phase's colour. */
	private void rear(ServerLevel level) {
		pause = PAUSE;
		getNavigation().stop();
		playSound(phase == 1 ? com.vnap.sound.SpireSounds.KEEPER_APPEAR : com.vnap.sound.SpireSounds.KEEPER_PHASE, 3.0F, 1.0F);
		ParticleOptions mote = Mc.dust(look().glow(), 1.6F);
		for (int i = 0; i < 32; i++) {
			double a = Math.PI * 2 * i / 32;
			level.sendParticles(mote, getX() + Math.cos(a) * 3.0, getY() + 0.3, getZ() + Math.sin(a) * 3.0, 1, 0.0, 0.05, 0.0, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(a) * 2.5, getY() + 0.3, getZ() + Math.sin(a) * 2.5, 1, 0.0, 0.05, 0.0, 0.02);
		}
		bar.setColor(phase == 3 ? art.colour() == BossEvent.BossBarColor.RED ? BossEvent.BossBarColor.PINK : BossEvent.BossBarColor.RED : art.colour());
		next = Math.max(next, PAUSE + 10);
	}

	/** One more block of the pillars round its post: where the floor is free, never on a climber. */
	private void raise(ServerLevel level, int height) {
		BlockState stone = art.pillar();
		if (stone == null) return;
		for (int[] corner : new int[][] {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
			BlockPos at = home.offset(corner[0], height - 1, corner[1]);
			if (!level.getBlockState(at).isAir() || !level.getEntitiesOfClass(Player.class, new AABB(at)).isEmpty()) continue;
			level.setBlock(at, stone, 3);
			level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 4, 0.4, 0.2, 0.4, 0.02);
		}
		playSound(SoundEvents.DEEPSLATE_BRICKS_PLACE, 1.6F, 0.7F);
	}

	// ---- what the arts are made of --------------------------------------------------------

	/** Its attack damage, which every art is a share of. */
	public float damage() {
		return (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	/** The climbers in its room. */
	public List<Player> climbers() {
		return level().getEntitiesOfClass(Player.class, room.inflate(1.0), SpireGuard::fair);
	}

	public AABB room() {
		return room;
	}

	/** A point inside the room near {@code at} (some way off it at random), on the floor the climbers stand on. */
	public Vec3 near(Vec3 at, double spread) {
		double x = at.x + (random.nextDouble() * 2 - 1) * spread;
		double z = at.z + (random.nextDouble() * 2 - 1) * spread;
		return new Vec3(Math.max(room.minX + 1.0, Math.min(room.maxX - 1.0, x)), at.y, Math.max(room.minZ + 1.0, Math.min(room.maxZ - 1.0, z)));
	}

	/** Hurts every climber within {@code inner}..{@code outer} of {@code at} (across, and up to 3 above or below it). */
	public void blow(Vec3 at, double inner, double outer, float damage, double push, double lift, @Nullable MobEffectInstance effect) {
		for (Player player : climbers()) {
			double dx = player.getX() - at.x;
			double dz = player.getZ() - at.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d < inner || d > outer || Math.abs(player.getY() - at.y) > 3.0) continue;
			hit(player, damage, push);
			if (lift > 0) player.push(0.0, lift, 0.0);
			if (effect != null) player.addEffect(new MobEffectInstance(effect));
			Mc.knocked(player);
		}
	}

	/** A mark on the floor, shown for {@code warn} ticks (a ring and a disc filling it), then {@code then} happens. */
	public Mark mark(Vec3 at, double inner, double outer, int warn, ParticleOptions tell, boolean fromAbove, BiConsumer<ServerLevel, Mark> then) {
		Mark mark = new Mark(at, inner, outer, warn, tell, fromAbove, then);
		// a bound on what one keeper has pending: past it the oldest mark is let go
		if (marks.size() >= MOST_MARKS) marks.remove(0);
		marks.add(mark);
		return mark;
	}

	/** Something it does {@code ticks} from now, shown by nothing (the steps of a breath, the dots of a constellation). */
	public void later(int ticks, BiConsumer<ServerLevel, Mark> then) {
		marks.add(new Mark(position(), 0.0, 0.0, ticks, null, false, then));
	}

	/** A beam from it: drawn still for {@code warn} ticks, then it fires for {@code fire} ticks turning {@code turn} radians a tick. */
	public void beam(double angle, double turn, int warn, int fire, double length, float damage, ParticleOptions light) {
		beams.add(new Beam(angle, turn, warn, fire, length, damage, light));
	}

	/** Something that rolls straight across the room from {@code from}, {@code step} a tick, hurting what it meets. */
	public void roll(Vec3 from, Vec3 step, int ticks, float damage, ParticleOptions trail) {
		rollers.add(new Roller(from, step, ticks, damage, trail));
	}

	/** Its guard down for a while: it stands and takes half again of every blow, a shield or not. */
	public void stun(int ticks) {
		if (stun <= 0) playSound(com.vnap.sound.SpireSounds.KEEPER_STUN, 2.0F, 1.0F);
		stun = Math.max(stun, ticks);
		getNavigation().stop();
	}

	/** It stands for an art of its own (its attacks wait). */
	public void cast(int ticks) {
		casting = Math.max(casting, ticks);
		getNavigation().stop();
	}

	public void pull(int ticks) {
		pull = Math.max(pull, ticks);
	}

	public void orbit(int ticks) {
		orbit = Math.max(orbit, ticks);
	}

	/** A shell of {@code amount} health over its own; shattered, it is stunned. */
	public void shell(float amount) {
		shell = amount;
		shelled = true;
	}

	/** Its next dashes come one after another. */
	public void chain(int dashes) {
		chain = dashes;
		ready(Attack.DASH);
	}

	/** No more pause: it is open at once (a worm come up out of the floor). */
	public void calm() {
		pause = 0;
	}

	public void untouchable(int ticks) {
		pause = Math.max(pause, ticks);
	}

	public net.minecraft.util.RandomSource rng() {
		return random;
	}

	/** Bring one of its own at a spot (counted, burst when it falls). */
	public @Nullable SpireGuard bring(ServerLevel level, Genome small, BlockPos at) {
		return summon(level, small, at);
	}

	public List<SpireGuard> company() {
		return brought();
	}

	public void hurtOne(LivingEntity target, float damage, double push) {
		hit(target, damage, push);
	}

	public void lingering(ServerLevel level, Vec3 at, float radius, int ticks, MobEffectInstance effect, ParticleOptions particle) {
		cloud(level, at, radius, ticks, effect, particle);
	}

	public void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, double every) {
		Vec3 way = to.subtract(from);
		int n = Math.max(1, (int) (way.length() / every));
		for (int i = 0; i <= n; i++) {
			Vec3 at = from.add(way.scale(i / (double) n));
			level.sendParticles(particle, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** How far a climber stands across from the segment a..b. */
	public static double across(Vec3 p, Vec3 a, Vec3 b) {
		double ax = b.x - a.x;
		double az = b.z - a.z;
		double len = ax * ax + az * az;
		double t = len < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, ((p.x - a.x) * ax + (p.z - a.z) * az) / len));
		double dx = p.x - (a.x + ax * t);
		double dz = p.z - (a.z + az * t);
		return Math.sqrt(dx * dx + dz * dz);
	}

	private void marks(ServerLevel level) {
		for (int i = marks.size() - 1; i >= 0; i--) {
			Mark mark = marks.get(i);
			mark.left--;
			if (mark.left <= 0) {
				marks.remove(i);
				mark.then.accept(level, mark);
				continue;
			}
			if (mark.tell == null || mark.left % 3 != 0) continue;
			double done = 1.0 - mark.left / (double) mark.warn;
			// the edge in the keeper's colour, its own sign along it, and a ring closing in from the edge as it comes
			ParticleOptions edge = Mc.dust(look().glow(), 1.5F);
			Vec3 low = mark.at.add(0.0, 0.1, 0.0);
			ring(level, low, mark.outer, edge, 10.0);
			ring(level, low, mark.outer, mark.tell, 2.5);
			if (mark.inner > 0) ring(level, low, mark.inner, edge, 10.0);
			ring(level, low, mark.outer - (mark.outer - mark.inner) * done, ParticleTypes.ELECTRIC_SPARK, 4.0);
			if (mark.fromAbove) level.sendParticles(mark.tell, mark.at.x, mark.at.y + 5.0, mark.at.z, 2, mark.outer * 0.4, 0.2, mark.outer * 0.4, 0.0);
		}
	}

	private void beams(ServerLevel level) {
		Vec3 eye = new Vec3(getX(), getY() + 1.1, getZ());
		for (int i = beams.size() - 1; i >= 0; i--) {
			Beam beam = beams.get(i);
			Vec3 end = reach(level, eye, beam.angle, beam.length);
			if (beam.warn > 0) {
				beam.warn--;
				if (beam.warn % 3 == 0) line(level, eye, end, ParticleTypes.ELECTRIC_SPARK, 0.8);
				if (beam.warn == 0) playSound(com.vnap.sound.SpireSounds.BEAM_FIRE, 2.0F, 1.0F);
				continue;
			}
			line(level, eye, end, beam.light, 0.3);
			line(level, eye.add(0.0, 0.25, 0.0), end.add(0.0, 0.25, 0.0), Mc.dust(look().glow(), 1.2F), 0.4);
			if (beam.fire % 5 == 0) {
				for (Player player : climbers()) {
					if (Math.abs(player.getY() + 1.0 - eye.y) < 2.5 && across(player.position(), eye, end) < 0.9) hit(player, beam.damage, 0.3);
				}
			}
			beam.angle += beam.turn;
			if (--beam.fire <= 0) beams.remove(i);
		}
	}

	/** Where a beam from {@code eye} at {@code angle} stops: its length, or the first solid block (pillars and walls are cover). */
	private static Vec3 reach(ServerLevel level, Vec3 eye, double angle, double length) {
		Vec3 step = new Vec3(Math.cos(angle) * 0.5, 0.0, Math.sin(angle) * 0.5);
		Vec3 at = eye;
		for (double d = 0.0; d < length; d += 0.5) {
			Vec3 next = at.add(step);
			BlockPos pos = BlockPos.containing(next);
			if (d > 1.5 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return at;
			at = next;
		}
		return at;
	}

	/** A block of its art for {@code ticks}, where nothing stands and no climber is; gone again after, or when it falls. */
	public void raise(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
		if (raised.size() >= MOST_RAISED || !level.getBlockState(pos).isAir() || !room.contains(Vec3.atCenterOf(pos))
				|| !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) return;
		level.setBlock(pos, state, 3);
		raised.add(pos.immutable());
		later(ticks, (l, m) -> lower(l, pos, state));
	}

	private void lower(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockState(pos).equals(state)) {
			level.removeBlock(pos, false);
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
					pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
		}
		raised.remove(pos);
	}

	/**
	 * One of the Schism's pair fell: this one takes up its tools — the Mason the Surveyor's compass, the Surveyor the
	 * Mason's hammer. A stream of light from the fallen to it, a long pause, then a new body, full health and both
	 * arts, the fight from its first phase again.
	 */
	private void rise(ServerLevel level, SpireKeeper fallen) {
		int next = genome.keeper() == Genome.MASON ? Genome.MASON_ALONE : Genome.SURVEYOR_ALONE;
		line(level, fallen.position().add(0.0, fallen.getBbHeight() * 0.5, 0.0), position().add(0.0, getBbHeight() * 0.5, 0.0), ParticleTypes.END_ROD, 0.3);
		line(level, fallen.position().add(0.0, fallen.getBbHeight() * 0.5, 0.0), position().add(0.0, getBbHeight() * 0.5, 0.0), Mc.dust(look().glow(), 2.0F), 0.4);
		become(Genome.duo(genome.seed(), genome.floor(), theme, next));
		// its new health is a lone keeper's: scaled for the group in the room afresh
		scaledFor = 1;
		group();
		art = KeeperArts.of(next);
		phase = 1;
		marks.clear();
		beams.clear();
		rollers.clear();
		bar.setName(getDisplayName());
		rear(level);
		pause = PAUSE * 2;
		level.sendParticles(Mc.flash(), getX(), getY() + getBbHeight() / 2, getZ(), 2, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.0, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		playSound(com.vnap.sound.SpireSounds.POWER, 3.0F, 1.0F);
	}

	private void rollers(ServerLevel level) {
		for (int i = rollers.size() - 1; i >= 0; i--) {
			Roller roller = rollers.get(i);
			roller.at = roller.at.add(roller.step);
			level.sendParticles(roller.trail, roller.at.x, roller.at.y + 0.6, roller.at.z, 3, 0.3, 0.5, 0.3, 0.02);
			ring(level, roller.at.add(0.0, 0.7, 0.0), 0.7, Mc.dust(0xC9773F, 1.4F), 12.0);
			level.sendParticles(ParticleTypes.CRIT, roller.at.x, roller.at.y + 0.2, roller.at.z, 4, 0.4, 0.1, 0.4, 0.2);
			for (Player player : climbers()) {
				if (player.position().distanceTo(roller.at) < 1.3) hit(player, roller.damage, 0.8);
			}
			if (--roller.left <= 0 || !room.inflate(0.5).contains(roller.at)) rollers.remove(i);
		}
	}

	/** The climbers drawn in towards it, a spiral of bubbles showing the pull. */
	private void pulling(ServerLevel level) {
		pull--;
		for (Player player : climbers()) {
			Vec3 to = position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() < 4.0 || to.lengthSqr() > 196.0) continue;
			player.push(to.normalize().x * 0.045, 0.0, to.normalize().z * 0.045);
			Mc.knocked(player);
		}
		for (int i = 0; i < 3; i++) {
			double a = tickCount * 0.25 + Math.PI * 2 * i / 3;
			double r = 7.0 - (tickCount % 20) * 0.3;
			level.sendParticles(ParticleTypes.BUBBLE_POP, getX() + Math.cos(a) * r, getY() + 0.3, getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.SPLASH, getX() + Math.cos(a) * r, getY() + 0.2, getZ() + Math.sin(a) * r, 2, 0.1, 0.0, 0.1, 0.0);
		}
	}

	/** Shards (thorns, motes) round it: whoever stands in their ring is cut, every half second. */
	private void orbiting(ServerLevel level) {
		orbit--;
		double r = art.orbitRadius() * Math.max(1.0, genome.size() * 0.8);
		ParticleOptions mote = art.orbitMote(this);
		int count = art.orbitCount();
		for (int i = 0; i < count; i++) {
			double a = tickCount * 0.12 + Math.PI * 2 * i / count;
			level.sendParticles(mote, getX() + Math.cos(a) * r, getY() + 1.2, getZ() + Math.sin(a) * r, 2, 0.05, 0.3, 0.05, 0.0);
		}
		if (tickCount % 10 != 0) return;
		for (Player player : climbers()) {
			double d = Math.sqrt(player.distanceToSqr(getX(), player.getY(), getZ()));
			if (Math.abs(d - r) < 0.8) hit(player, damage() * 0.4F, 0.4);
		}
	}

	@Override
	protected float defend(DamageSource source, float amount) {
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
		if (pause > 0) return 0.0F;
		float left = stun > 0 ? amount * STUNNED : super.defend(source, amount);
		left = art.taken(this, source, left);
		if (shell > 0 && left > 0) {
			float soaked = Math.min(shell, left);
			shell -= soaked;
			left -= soaked;
			if (level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, 0.6, 0.6, 0.6, 0.05);
				if (shell <= 0) {
					level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
							net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState()), getX(), getY() + getBbHeight() * 0.5, getZ(), 60,
							getBbWidth() * 0.5, getBbHeight() * 0.4, getBbWidth() * 0.5, 0.2);
					playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.6F);
					stun(70);
				} else {
					playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.6F);
				}
			}
			if (left <= 0) return 0.0F;
		}
		return left;
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bar.removePlayer(player);
	}

	/** Dev worlds: its marks to the climbers' clients, for the dev bot (KeeperMarksPayload). */
	private void tellMarks() {
		List<double[]> out = new ArrayList<>();
		for (Mark mark : marks) out.add(new double[] {mark.at.x, mark.at.z, mark.inner, mark.outer});
		var payload = new com.vnap.network.KeeperMarksPayload(out);
		for (Player player : climbers()) {
			if (player instanceof ServerPlayer climber) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(climber, payload);
		}
	}

	/** At once in its hall, without its coming (a test, a check that wants its fight now). */
	public void appear() {
		entering = KeeperShow.ENTER + 1;
		KeeperShow.size(this, 1.0);
		setInvisible(false);
		com.vnap.Mc.invulnerable(this, false);
		setNoAi(false);
		bar.setVisible(true);
	}

	/** Its coming and its going: true while either holds its fight off. */
	private boolean show(ServerLevel level) {
		if (dying >= 0) {
			KeeperShow.fall(this, level, dying);
			if (++dying > KeeperShow.FALL) {
				finishing = true;
				com.vnap.Mc.invulnerable(this, false);
				setInvisible(true);
				setHealth(0.0F);
				die(felledBy != null ? felledBy : damageSources().generic());
			}
			return true;
		}
		if (entering > KeeperShow.ENTER) return false;
		if (entering < 0) {
			// not in its hall till a climber is: unseen, untouchable, still
			setInvisible(true);
			com.vnap.Mc.invulnerable(this, true);
			setNoAi(true);
			bar.setVisible(false);
			if (climbers().isEmpty()) return true;
			entering = 0;
			enterAt = position();
		}
		Vec3 at = enterAt != null ? enterAt : position();
		KeeperShow.enter(this, level, entering, at);
		if (++entering > KeeperShow.ENTER) {
			com.vnap.Mc.invulnerable(this, false);
			setNoAi(false);
			bar.setVisible(true);
		}
		return true;
	}

	@Override
	public void die(DamageSource source) {
		// a keeper (not one of its parts) goes its own way first; the death proper comes at the end of it
		if (!finishing && part() == Part.NONE && level() instanceof ServerLevel) {
			if (dying < 0) {
				dying = 0;
				felledBy = source;
				setHealth(1.0F);
				com.vnap.Mc.invulnerable(this, true);
				setNoAi(true);
				bar.setVisible(false);
				marks.clear();
				beams.clear();
				rollers.clear();
				if (level() instanceof ServerLevel level) releaseCompany(level);
			}
			return;
		}
		super.die(source);
		bar.setProgress(0.0F);
		bar.removeAllPlayers();
		marks.clear();
		beams.clear();
		rollers.clear();
		setInvisible(false);
		if (!(level() instanceof ServerLevel level)) return;
		for (BlockPos pos : List.copyOf(raised)) lower(level, pos, level.getBlockState(pos));
		// the fight is over: its music stops and the climbers hear it won (the pair's first to fall is not the end)
		boolean last = level.getEntitiesOfClass(SpireKeeper.class, room.inflate(2.0), keeper -> keeper.isAlive() && !keeper.equals(this)).isEmpty();
		if (last) {
			for (Player player : climbers()) {
				if (player instanceof ServerPlayer climber) {
					climber.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(Mc.id(music()), net.minecraft.sounds.SoundSource.RECORDS));
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), com.vnap.sound.SpireSounds.VICTORY, net.minecraft.sounds.SoundSource.RECORDS, 1.5F, 1.0F);
			}
		}
		int me = genome.keeper();
		if (me != Genome.MASON && me != Genome.SURVEYOR) return;
		for (SpireKeeper other : level.getEntitiesOfClass(SpireKeeper.class, room.inflate(2.0), keeper -> keeper.isAlive() && !keeper.equals(this))) {
			int them = other.genome.keeper();
			if (them == Genome.MASON || them == Genome.SURVEYOR) other.rise(level, this);
		}
	}

	@Override
	//? if >=1.21.6 {
	/*public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
		super.addAdditionalSaveData(output);
		CompoundTag tag = new CompoundTag();
	*///?} else {
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
	//?}
		tag.putInt("phase", phase);
		//? if >=1.21.6
		/*Mc.put(output, tag);*/
	}

	@Override
	//? if >=1.21.6 {
	/*public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
		super.readAdditionalSaveData(input);
		CompoundTag tag = Mc.tagOf(input);
	*///?} else {
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
	//?}
		phase = Math.max(1, Nbt.getInt(tag, "phase"));
		art = KeeperArts.of(genome.keeper());
		bar.setName(getDisplayName());
		bar.setColor(art.colour());
	}

	/** A mark on the floor: where, the ring it covers, how long it shows, what it does. */
	public static final class Mark {
		public final Vec3 at;
		public final double inner;
		public final double outer;
		final int warn;
		int left;
		final @Nullable ParticleOptions tell;
		final boolean fromAbove;
		final BiConsumer<ServerLevel, Mark> then;

		Mark(Vec3 at, double inner, double outer, int warn, @Nullable ParticleOptions tell, boolean fromAbove, BiConsumer<ServerLevel, Mark> then) {
			this.at = at;
			this.inner = inner;
			this.outer = outer;
			this.warn = warn;
			this.left = warn;
			this.tell = tell;
			this.fromAbove = fromAbove;
			this.then = then;
		}
	}

	private static final class Beam {
		double angle;
		final double turn;
		int warn;
		int fire;
		final double length;
		final float damage;
		final ParticleOptions light;

		Beam(double angle, double turn, int warn, int fire, double length, float damage, ParticleOptions light) {
			this.angle = angle;
			this.turn = turn;
			this.warn = warn;
			this.fire = fire;
			this.length = length;
			this.damage = damage;
			this.light = light;
		}
	}

	private static final class Roller {
		Vec3 at;
		final Vec3 step;
		int left;
		final float damage;
		final ParticleOptions trail;

		Roller(Vec3 at, Vec3 step, int left, float damage, ParticleOptions trail) {
			this.at = at;
			this.step = step;
			this.left = left;
			this.damage = damage;
			this.trail = trail;
		}
	}
}
