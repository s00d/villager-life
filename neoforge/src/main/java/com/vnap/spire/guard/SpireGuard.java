package com.vnap.spire.guard;

import com.vnap.Mc;
import com.vnap.Nbt;
import com.vnap.spire.Attack;
import com.vnap.spire.Genome;
import com.vnap.spire.SpireGuards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * A guard of the Builders' Archive (DESIGN §5): its genome is all it is — body, role, attacks, defence, size and
 * colours (the client gets the look, a packed int). It never burns, never despawns, drops nothing and never leaves
 * its floor; beaten, it bursts into light. Attacks run from their table (Attack): a strong one shows itself with
 * sparks and a chime from its first tick to the one it lands on.
 */
public abstract class SpireGuard extends Monster {
	private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(SpireGuard.class, EntityDataSerializers.INT);
	/** 1 while an attack is under way, 0 otherwise: the model raises its arms for it. */
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(SpireGuard.class, EntityDataSerializers.INT);
	/** Ticks between two attacks of any kind. */
	private static final int GAP = 10;
	private static final int SUMMONS = 3;
	/** A summoned one's health and blow as a share of its kind's. */
	public static final double MINION = 0.6;
	private static final int WARD_TICKS = 30;

	protected Genome genome = Genome.decode(0L, 1, "roots", false, false);
	protected String theme = "roots";
	protected BlockPos home = BlockPos.ZERO;
	/** The room it keeps to, and the floor's box it is never let out of. */
	protected AABB room = new AABB(BlockPos.ZERO);
	protected AABB box = new AABB(BlockPos.ZERO);
	private final java.util.EnumMap<Attack, Integer> cooldowns = new java.util.EnumMap<>(Attack.class);
	private int gap;
	private @Nullable Attack current;
	private int timer;
	private Vec3 aim = Vec3.ZERO;
	private int dashing;
	private boolean dashHit;
	private boolean leaping;
	private int wardUntil;
	private final List<UUID> summoned = new ArrayList<>();
	private boolean elite;
	private Part part = Part.NONE;
	private @Nullable UUID owner;

	/**
	 * What a guard is to the boss that brought it, when it is a piece of that boss's own: a worm's segment (follows
	 * the head, hits on it go to the head — on the tail half again), a hydra's head, a planet (orbits, shields), a
	 * decoy (looks like the boss; struck, it bursts in ink), an echo (replays the boss's last moves, see-through).
	 * Parts are not saved: a floor's boss brings them again.
	 */
	public enum Part {
		NONE, SEGMENT, HEAD, PLANET, DECOY, ECHO
	}

	protected SpireGuard(EntityType<? extends SpireGuard> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.FLYING_SPEED, 0.5)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	/** Makes it this guard: the genome decoded, the numbers set, where it stands and what it keeps to. */
	public void setup(Genome genome, String theme, BlockPos home, AABB room, AABB box) {
		this.genome = genome;
		this.theme = theme;
		this.home = home.immutable();
		this.room = room;
		this.box = box;
		entityData.set(LOOK, genome.look());
		base(Attributes.MAX_HEALTH, genome.health());
		base(Attributes.ATTACK_DAMAGE, genome.damage());
		base(Attributes.MOVEMENT_SPEED, genome.speed());
		base(Attributes.SCALE, genome.size());
		base(Attributes.ARMOR, genome.defence() == Genome.Defence.ARMOR ? 6.0 : genome.isKeeper() ? 8.0 : 0.0);
		base(Attributes.KNOCKBACK_RESISTANCE, genome.isKeeper() ? 1.0 : genome.role() == Genome.Role.TANK ? 0.6 : 0.0);
		setHealth(getMaxHealth());
		setPersistenceRequired();
		addTag(SpireGuards.TAG);
		Mc.restrictTo(this, home, 12);
	}

	private void base(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = getAttribute(attribute);
		if (instance != null) instance.setBaseValue(value);
	}

	public Genome genome() {
		return genome;
	}

	/** Takes on another make where it stands (one of the Schism's pair taking up the other's tools): full health. */
	public void become(Genome next) {
		genome = next;
		entityData.set(LOOK, next.look());
		base(Attributes.MAX_HEALTH, next.health());
		base(Attributes.ATTACK_DAMAGE, next.damage());
		base(Attributes.MOVEMENT_SPEED, next.speed());
		base(Attributes.SCALE, next.size());
		setHealth(getMaxHealth());
	}

	/** An elite's health and blow against its kind's (SpireGameTests: a fight of seconds, not a wall that kills first). */
	public static final double ELITE_HEALTH = 1.5;
	public static final double ELITE_DAMAGE = 1.1;
	public static final double ELITE_ARMOR = 2.0;

	/**
	 * An arena's elite: bigger, more health, harder and quicker, gold about it and named so. One an arena from the
	 * first chapter's last floors (FloorGenerator), saved with it.
	 */
	public void elite(boolean heal) {
		elite = true;
		entityData.set(LOOK, genome.look() | Genome.ELITE);
		base(Attributes.MAX_HEALTH, genome.health() * ELITE_HEALTH);
		base(Attributes.ATTACK_DAMAGE, genome.damage() * ELITE_DAMAGE);
		base(Attributes.SCALE, genome.size() * 1.25);
		base(Attributes.ARMOR, (genome.defence() == Genome.Defence.ARMOR ? 6.0 : 0.0) + ELITE_ARMOR);
		if (heal) setHealth(getMaxHealth());
	}

	public boolean isElite() {
		return elite;
	}

	/** What a guard's blow or shot leaves of its chapter, from the second chapter on (Afflictions). */
	public void sting(LivingEntity target, @Nullable Attack by) {
		if (genome.isKeeper() || Genome.chapter(genome.floor()) < 1) return;
		com.vnap.spire.Afflictions.weak(theme, target, getRandom());
	}

	/** Makes it a part of {@code boss}: still and weightless for the parts the boss moves, the boss's look for a decoy or echo. */
	public void partOf(SpireGuard boss, Part kind) {
		part = kind;
		owner = boss.getUUID();
		if (kind == Part.SEGMENT || kind == Part.PLANET || kind == Part.ECHO) {
			setNoAi(true);
			setNoGravity(true);
		}
		if (kind == Part.DECOY || kind == Part.ECHO) {
			entityData.set(LOOK, boss.entityData.get(LOOK) | (kind == Part.ECHO ? Genome.ECHO : 0));
			base(Attributes.SCALE, boss.genome.size());
		}
		if (kind == Part.ECHO) com.vnap.Mc.invulnerable(this, true);
	}

	public Part part() {
		return part;
	}

	/** A climber a guard's blows are for: alive, not watching (spectator) and not making the place (creative). */
	public static boolean fair(Player player) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative();
	}

	/** An echo is invisible yet seen, see-through, by everyone (the way a teammate's invisible body is). */
	@Override
	public boolean isInvisibleTo(Player player) {
		return look().echo() ? false : super.isInvisibleTo(player);
	}

	public @Nullable SpireGuard owner() {
		UUID id = owner;
		return id != null && level() instanceof ServerLevel level && level.getEntity(id) instanceof SpireGuard boss && boss.isAlive() ? boss : null;
	}

	/** Sets the look the client draws (a boss's form). */
	public void wear(int look) {
		entityData.set(LOOK, look);
	}

	public int worn() {
		return entityData.get(LOOK);
	}

	/** Where it stands on its floor. */
	public BlockPos home() {
		return home;
	}

	/**
	 * What a guard gives off while it stands about, client side only: motes of its eyes' colour rising off it, a trail
	 * behind a flyer on the move, a slow ring of them at a keeper's feet. Its threat reads before it moves.
	 */
	@Override
	public void aiStep() {
		super.aiStep();
		if (!level().isClientSide() || !isAlive()) return;
		Genome.Look look = look();
		boolean keeper = look.keeper() >= 0;
		if (tickCount % (keeper ? 2 : 5) == 0) {
			double w = getBbWidth() * 0.6;
			level().addParticle(com.vnap.Mc.dust(look.glow(), keeper ? 1.2F : 0.7F), getX() + (random.nextDouble() - 0.5) * w * 2,
					getY() + random.nextDouble() * getBbHeight(), getZ() + (random.nextDouble() - 0.5) * w * 2, 0.0, 0.02, 0.0);
		}
		if (keeper && tickCount % 4 == 0) {
			double a = tickCount * 0.15;
			double r = getBbWidth() * 0.9 + 0.5;
			level().addParticle(ParticleTypes.END_ROD, getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r, 0.0, 0.01, 0.0);
		}
		if (look.elite() && tickCount % 3 == 0) {
			double a = tickCount * 0.3;
			level().addParticle(com.vnap.Mc.dust(0xFFD24A, 1.0F), getX() + Math.cos(a) * 0.4, getY() + getBbHeight() + 0.25, getZ() + Math.sin(a) * 0.4, 0.0, 0.0, 0.0);
			level().addParticle(com.vnap.Mc.dust(0xFFD24A, 0.8F), getX() + (random.nextDouble() - 0.5) * getBbWidth(), getY() + random.nextDouble() * getBbHeight(),
					getZ() + (random.nextDouble() - 0.5) * getBbWidth(), 0.0, 0.03, 0.0);
		}
		if (keeper && tickCount % 3 == 0 && !isInvisible()) {
			double w = getBbWidth() * 0.7;
			level().addParticle(KeeperArts.mote(look.keeper()), getX() + (random.nextDouble() - 0.5) * w * 2, getY() + getBbHeight() * (0.3 + random.nextDouble() * 0.7),
					getZ() + (random.nextDouble() - 0.5) * w * 2, 0.0, 0.0, 0.0);
		}
		if (getDeltaMovement().horizontalDistanceSqr() > 0.002 && !onGround() && tickCount % 2 == 0) {
			level().addParticle(com.vnap.Mc.dust(look.glow(), 0.5F), xo, yo + getBbHeight() * 0.5, zo, 0.0, 0.0, 0.0);
		}
	}

	public Genome.Look look() {
		return Genome.Look.of(entityData.get(LOOK));
	}

	public int action() {
		return entityData.get(ACTION);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(LOOK, 0);
		builder.define(ACTION, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new Fight(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, SpireGuard.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected Component getTypeName() {
		if (genome.isKeeper()) return Component.translatable("spire.villager_life.keeper." + Genome.keeperName(genome.keeper()));
		Component name = Component.translatable("spire.villager_life.guard." + genome.body().model());
		return elite ? Component.translatable("spire.villager_life.elite", name) : name;
	}

	/**
	 * Over a guard, on the climber's screen: its name and a bar of its health, once it is hurt or a climber is right by it (a crowd's names would run into each other)
	 * (a keeper has its boss bar; its echoes, decoys and coils none, or they would give the true one away). Client side
	 * only: the server's name, in death messages, stays the plain one.
	 */
	@Override
	public boolean shouldShowName() {
		if (!level().isClientSide()) return super.shouldShowName();
		Genome.Look look = look();
		if (look.keeper() >= 0 || look.echo() || look.body() == Genome.Body.WYRM_SEGMENT || !isAlive()) return false;
		return NAMED.test(this);
	}

	/**
	 * Which guards wear their health over the head (the client sets it: the one looked at and the nearest), so a pack
	 * does not stack its plates into one smear.
	 */
	public static java.util.function.Predicate<SpireGuard> NAMED = guard -> false;

	@Override
	public Component getDisplayName() {
		if (!level().isClientSide()) return super.getDisplayName();
		Genome.Look look = look();
		Component kind = Component.translatable("spire.villager_life.guard." + look.body().model());
		net.minecraft.network.chat.MutableComponent line = (look.elite() ? Component.translatable("spire.villager_life.elite", kind) : kind).copy();
		float share = Math.max(0.0F, Math.min(1.0F, getHealth() / Math.max(1.0F, getMaxHealth())));
		int filled = Math.round(share * 10);
		int colour = share > 0.6F ? 0x6BD66B : share > 0.3F ? 0xE6C34A : 0xE0533F;
		line.append(Component.literal("  " + "■".repeat(filled)).withColor(colour));
		line.append(Component.literal("■".repeat(10 - filled)).withColor(0x4A4A4A));
		return line;
	}

	// ---- the fight ------------------------------------------------------------------------

	/** Goes for its target and uses its attacks on it; it moves the way its kind moves (approach). */
	private static final class Fight extends Goal {
		private final SpireGuard guard;

		Fight(SpireGuard guard) {
			this.guard = guard;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = guard.getTarget();
			return target != null && target.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			guard.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = guard.getTarget();
			if (target == null) return;
			guard.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (guard.current != null || guard.dashing > 0) return;
			double distance = guard.distanceTo(target);
			Attack next = guard.choose(target, distance);
			if (next != null) guard.begin(next, target);
			else guard.approach(target, distance);
		}
	}

	/** Keeps a shooter's, a helper's and a summoner's distance; the rest close in. */
	protected boolean keepsAway() {
		Genome.Role role = genome.role();
		return role == Genome.Role.SHOOTER || role == Genome.Role.HELPER || role == Genome.Role.SUMMONER;
	}

	/** Moves towards (or about) its target while no attack is under way: on foot by default. */
	protected void approach(LivingEntity target, double distance) {
		if (tickCount % 10 != 0) return;
		if (keepsAway() && distance < 5.0) {
			Vec3 away = position().subtract(target.position()).normalize().scale(4.0).add(position());
			getNavigation().moveTo(away.x, away.y, away.z, 1.1);
		} else if (!keepsAway() || distance > 11.0) {
			getNavigation().moveTo(target, 1.1);
		} else {
			getNavigation().stop();
		}
	}

	/** What it does when nobody is about (a walker strolls with its goal; a flyer circles, a swimmer swims). */
	protected void idle() {
	}

	/** The attacks this guard may use now (a keeper holds some back early in the fight). */
	protected List<Attack> attacks() {
		return genome.attacks();
	}

	/** Cooldowns run this much faster (a keeper later in its fight; an elite is harder, not quicker to swing). */
	protected double haste() {
		return 1.0;
	}

	/**
	 * How much longer than the attack's own its cooldown is for this kind: the heavy ones swing slowly and hard (a
	 * tank trading blow for blow every second outlasted a climber in the gear of its floor).
	 */
	public static double pace(Genome.Role role) {
		return switch (role) {
			case TANK -> 2.5;
			// a keeper's plain blow comes between its arts, not as their equal
			case KEEPER -> 1.8;
			case FIGHTER, LEAPER -> 1.4;
			default -> 1.0;
		};
	}

	private @Nullable Attack choose(LivingEntity target, double distance) {
		if (gap > 0) return null;
		List<Attack> list = attacks();
		// the dearer ones first: the plain blow is what is left
		for (int i = list.size() - 1; i >= 0; i--) {
			Attack attack = list.get(i);
			if (cooldowns.getOrDefault(attack, 0) > 0 || distance > attack.range * Math.max(1.0, genome.size())) continue;
			if (fits(attack, target, distance)) return attack;
		}
		return null;
	}

	/** Whether the attack makes sense against this target now. */
	protected boolean fits(Attack attack, LivingEntity target, double distance) {
		return switch (attack) {
			case BOLT, VOLLEY -> hasLineOfSight(target);
			case DASH -> distance > 3.5 && hasLineOfSight(target);
			case LEAP -> distance > 3.0 && onGround();
			case BLINK -> distance > 4.0;
			case MEND -> !hurtAllies(10.0).isEmpty();
			case HASTE -> allies(10.0).stream().anyMatch(ally -> !ally.equals(this) && !ally.hasEffect(MobEffects.MOVEMENT_SPEED));
			case SUMMON -> live(summoned) < SUMMONS;
			default -> true;
		};
	}

	private void begin(Attack attack, LivingEntity target) {
		current = attack;
		timer = 0;
		aim = target.position();
		entityData.set(ACTION, 1);
		getNavigation().stop();
		if (attack.strong()) playSound(com.vnap.sound.SpireSounds.GUARD_TELL, 1.2F, 0.8F + attack.impact / 60.0F);
		net.minecraft.sounds.SoundEvent voice = com.vnap.sound.SpireSounds.attack(genome.body());
		if (voice != null) playSound(voice, 1.0F, 0.9F + random.nextFloat() * 0.2F);
	}

	private void tickAttack(ServerLevel level) {
		Attack attack = current;
		if (attack == null) return;
		timer++;
		LivingEntity target = getTarget();
		if (target != null && attack != Attack.LEAP && attack != Attack.DASH) aim = target.position();
		if (attack.strong() && timer < attack.impact && timer % 2 == 0) telegraph(level, attack);
		if (timer == attack.impact) {
			land(level, attack, target);
			landed(level, attack);
		}
		if (timer >= attack.duration) {
			current = null;
			entityData.set(ACTION, 0);
			cooldowns.put(attack, (int) (attack.cooldown * pace(genome.role()) / haste()));
			gap = GAP;
			finished(level, attack);
		}
	}

	/** Sparks where it will land: round itself for a slam or a quake, along the line for a dash, on the spot for a leap. */
	private void telegraph(ServerLevel level, Attack attack) {
		ParticleOptions spark = ParticleTypes.ELECTRIC_SPARK;
		switch (attack) {
			case SLAM, QUAKE -> ring(level, position(), attack.range * Math.max(1.0, genome.size()), spark);
			case DASH -> {
				Vec3 step = aim.subtract(position());
				for (int i = 1; i <= 6; i++) {
					Vec3 at = position().add(step.scale(i / 6.0));
					level.sendParticles(spark, at.x, at.y + 0.2, at.z, 1, 0.1, 0.0, 0.1, 0.0);
				}
			}
			case LEAP -> ring(level, aim, 2.0, spark);
			case BLINK -> level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + getBbHeight() / 2, getZ(), 6, 0.4, 0.6, 0.4, 0.05);
			default -> level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() + 0.3, getZ(), 2, 0.3, 0.2, 0.3, 0.01);
		}
	}

	protected static void ring(ServerLevel level, Vec3 centre, double radius, ParticleOptions particle) {
		ring(level, centre, radius, particle, 6.0);
	}

	/** A ring of {@code perBlock} particles to a block of its round. */
	protected static void ring(ServerLevel level, Vec3 centre, double radius, ParticleOptions particle, double perBlock) {
		int points = Math.max(8, (int) (radius * perBlock));
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * i / points;
			level.sendParticles(particle, centre.x + Math.cos(a) * radius, centre.y + 0.15, centre.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private void land(ServerLevel level, Attack attack, @Nullable LivingEntity target) {
		float damage = (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * attack.power);
		double reach = attack.range * Math.max(1.0, genome.size());
		switch (attack) {
			case STRIKE -> {
				if (target != null && distanceTo(target) <= reach + 0.5) {
					hit(target, damage, 0.4);
					Mc.swing(this, net.minecraft.world.InteractionHand.MAIN_HAND);
				}
			}
			case SLAM, QUAKE -> {
				for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(reach, 2.0, reach), SpireGuard::fair)) {
					if (player.distanceTo(this) <= reach && (attack == Attack.SLAM || player.onGround())) hit(player, damage, 0.9);
				}
				level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				ring(level, position(), reach, ParticleTypes.CLOUD);
				playSound(com.vnap.sound.SpireSounds.MARK_IMPACT, 1.6F, 0.9F);
			}
			case DASH -> {
				Vec3 way = aim.subtract(position()).multiply(1.0, 0.0, 1.0);
				if (way.lengthSqr() > 1.0E-4) setDeltaMovement(way.normalize().scale(0.9).add(0.0, 0.1, 0.0));
				dashing = 10;
				dashHit = false;
				playSound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.0F, 1.3F);
			}
			case BOLT -> {
				if (target != null) shoot(level, target, damage, 0.0F);
			}
			case VOLLEY -> {
				if (target != null) {
					for (float spread : new float[] {-12.0F, 0.0F, 12.0F}) shoot(level, target, damage, spread);
				}
			}
			case LEAP -> {
				Vec3 way = aim.subtract(position());
				setDeltaMovement(way.x * 0.12, 0.6, way.z * 0.12);
				leaping = true;
				playSound(SoundEvents.GOAT_LONG_JUMP, 1.0F, 1.2F);
			}
			case MEND -> {
				for (SpireGuard ally : hurtAllies(reach)) {
					// a keeper and its parts mend only by their own arts: a mender beside it would undo the fight
					if (ally instanceof SpireKeeper || ally.part() != Part.NONE) continue;
					ally.heal(Math.max(4.0F, ally.getMaxHealth() * 0.25F));
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY() + ally.getBbHeight(), ally.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
				}
				playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.4F);
			}
			case HASTE -> {
				for (SpireGuard ally : allies(reach)) {
					if (ally.equals(this)) continue;
					ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0));
					level.sendParticles(ParticleTypes.WAX_ON, ally.getX(), ally.getY() + ally.getBbHeight(), ally.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
				}
				playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.8F);
			}
			case SUMMON -> summon(level);
			case BLINK -> {
				if (target != null) blink(level, target);
			}
		}
	}

	protected void hit(LivingEntity target, float damage, double push) {
		boolean hurt = Mc.hurt(target, damageSources().mobAttack(this), damage);
		if (hurt) sting(target, current);
		if (hurt && push > 0) {
			Vec3 way = target.position().subtract(position()).multiply(1.0, 0.0, 1.0);
			//? if >=26.2 {
			/*if (way.lengthSqr() > 1.0E-4) target.knockback(push, -way.x, -way.z, damageSources().mobAttack(this), damage);
			*///?} else
			if (way.lengthSqr() > 1.0E-4) target.knockback(push, -way.x, -way.z);
		}
	}

	protected SpireBolt shoot(ServerLevel level, LivingEntity target, float damage, float spread) {
		SpireBolt bolt = new SpireBolt(level, this, damage, Genome.Look.of(entityData.get(LOOK)).glow());
		bolt.by = current;
		bolt.setPos(getX(), getEyeY() - 0.1, getZ());
		double dx = target.getX() - getX();
		double dz = target.getZ() - getZ();
		double dy = target.getY(0.5) - bolt.getY() + Math.sqrt(dx * dx + dz * dz) * 0.08;
		Vec3 way = new Vec3(dx, dy, dz).yRot(spread * Mth.DEG_TO_RAD);
		bolt.shoot(way.x, way.y, way.z, 1.0F, 2.0F);
		level.addFreshEntity(bolt);
		playSound(com.vnap.sound.SpireSounds.GUARD_BOLT, 1.0F, 0.9F + random.nextFloat() * 0.2F);
		return bolt;
	}

	protected void summon(ServerLevel level) {
		for (int i = 0; i < 2 && live(summoned) < SUMMONS; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			BlockPos at = BlockPos.containing(getX() + Math.cos(a) * 1.5, getY(), getZ() + Math.sin(a) * 1.5);
			if (!level.noCollision(EntityType.ZOMBIE.getSpawnAABB(at.getX() + 0.5, at.getY(), at.getZ() + 0.5))) at = blockPosition();
			summon(level, minion(com.vnap.spire.FloorGenerator.mix(genome.seed(), tickCount + i)), at);
		}
		playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 1.6F);
	}

	/** The little one its summons brings: a hound of the swarm (a keeper brings its chapter's own). */
	protected Genome minion(long seed) {
		return Genome.summoned(seed, genome.floor(), theme);
	}

	/** One of its own at a spot, counted with the rest it brought (they burst when it falls). */
	protected @Nullable SpireGuard summon(ServerLevel level, Genome small, BlockPos at) {
		SpireGuard guard = SpireGuards.make(level, small, theme, at, room, box);
		if (guard == null) return null;
		// a brought one is company, not a second guard of the floor: lighter and weaker than its kind
		base(guard, Attributes.MAX_HEALTH, guard.getAttributeBaseValue(Attributes.MAX_HEALTH) * MINION);
		base(guard, Attributes.ATTACK_DAMAGE, guard.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) * MINION);
		guard.setHealth(guard.getMaxHealth());
		summoned.add(guard.getUUID());
		if (getPersistentData().contains(SpireGuards.OUTER)) guard.getPersistentData().putLong(SpireGuards.OUTER, com.vnap.Nbt.getLong(getPersistentData(), SpireGuards.OUTER));
		level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.02);
		return guard;
	}

	private static void base(LivingEntity who, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		net.minecraft.world.entity.ai.attributes.AttributeInstance instance = who.getAttribute(attribute);
		if (instance != null) instance.setBaseValue(value);
	}

	/** What it brought that still stands. */
	protected List<SpireGuard> brought() {
		List<SpireGuard> out = new ArrayList<>();
		if (level() instanceof ServerLevel level) {
			live(summoned);
			for (UUID id : summoned) if (level.getEntity(id) instanceof SpireGuard guard) out.add(guard);
		}
		return out;
	}

	/** An attack under way (or the run of a dash). */
	protected boolean busy() {
		return current != null || dashing > 0;
	}

	/** The attack may come again at once. */
	protected void ready(Attack attack) {
		cooldowns.put(attack, 0);
		gap = 0;
	}

	/** Called on the tick an attack lands. */
	protected void landed(ServerLevel level, Attack attack) {
	}

	/** Called when an attack is over and its cooldown set. */
	protected void finished(ServerLevel level, Attack attack) {
	}


	private void blink(ServerLevel level, LivingEntity target) {
		Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(2.0 + getBbWidth() / 2));
		AABB there = getBoundingBox().move(behind.subtract(position()));
		if (!level.noCollision(this, there) || !box.contains(behind)) return;
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.0, getZ(), 16, 0.3, 0.6, 0.3, 0.05);
		Mc.snap(this, behind.x, behind.y, behind.z, getYRot(), getXRot());
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.0, getZ(), 16, 0.3, 0.6, 0.3, 0.05);
		playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 1.6F);
	}

	protected int live(List<UUID> ids) {
		if (!(level() instanceof ServerLevel level)) return 0;
		ids.removeIf(id -> !(level.getEntity(id) instanceof SpireGuard guard) || !guard.isAlive());
		return ids.size();
	}

	protected List<SpireGuard> allies(double radius) {
		return level().getEntitiesOfClass(SpireGuard.class, getBoundingBox().inflate(radius), SpireGuard::isAlive);
	}

	private List<SpireGuard> hurtAllies(double radius) {
		List<SpireGuard> hurt = new ArrayList<>();
		for (SpireGuard ally : allies(radius)) if (ally.getHealth() < ally.getMaxHealth() * 0.7F) hurt.add(ally);
		return hurt;
	}

	// ---- every tick -----------------------------------------------------------------------

	@Override
	//? if >=1.21.2 {
	/*protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
	*///?} else {
	protected void customServerAiStep() {
		super.customServerAiStep();
		ServerLevel level = (ServerLevel) level();
	//?}
		cooldowns.replaceAll((attack, left) -> Math.max(0, left - 1));
		if (gap > 0) gap--;
		tickAttack(level);
		if (dashing > 0) {
			dashing--;
			LivingEntity target = getTarget();
			if (!dashHit && target != null && getBoundingBox().inflate(0.3).intersects(target.getBoundingBox())) {
				dashHit = true;
				hit(target, (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * Attack.DASH.power), 1.0);
			}
		}
		if (leaping && onGround() && timer > Attack.LEAP.impact + 3) splash(level);
		if (genome.defence() == Genome.Defence.REGEN && tickCount % 40 == 0 && getHealth() < getMaxHealth()) heal(1.0F);
		if (getTarget() == null && current == null) idle();
		if (tickCount % 20 == 0 && !box.contains(position())) back();
		// wedged out of sight of its climber close by (a corner behind the dressing): it steps out to where it stood,
		// or the floor could never be cleared
		LivingEntity target = getTarget();
		if (!genome.isKeeper() && target != null && distanceToSqr(target) < 64.0 && !hasLineOfSight(target)) {
			if (++hidden > HIDDEN_TICKS) {
				hidden = 0;
				back();
			}
		} else {
			hidden = 0;
		}
	}

	private static final int HIDDEN_TICKS = 160;
	private int hidden;

	/** A leap come down: a blow round where it lands and a puddle that slows. */
	private void splash(ServerLevel level) {
		leaping = false;
		float damage = (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * Attack.LEAP.power);
		for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(2.2, 1.0, 2.2), SpireGuard::fair)) hit(player, damage, 0.5);
		cloud(level, position(), 2.2F, 100, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), ParticleTypes.ITEM_SLIME);
		playSound(SoundEvents.SLIME_SQUISH, 1.2F, 0.8F);
	}

	/** A lingering patch on the floor that puts its effect on whoever stands in it. */
	protected void cloud(ServerLevel level, Vec3 at, float radius, int ticks, MobEffectInstance effect, ParticleOptions particle) {
		AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
		cloud.setRadius(radius);
		cloud.setDuration(ticks);
		cloud.setRadiusPerTick(0.0F);
		cloud.setWaitTime(0);
		cloud.setOwner(this);
		cloud.addEffect(effect);
		//? if >=1.21.5 {
		/*cloud.setCustomParticle(particle);
		*///?} else
		cloud.setParticle(particle);
		level.addFreshEntity(cloud);
	}

	/** Out of its floor (knocked, pushed, fallen): back where it stood. */
	protected void back() {
		getNavigation().stop();
		setDeltaMovement(Vec3.ZERO);
		fallDistance = 0.0F;
		Mc.snap(this, home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
	}

	// ---- defence --------------------------------------------------------------------------

	/** The damage that gets through its defence: a shield in front, a ward after a hit; a keeper's stance. */
	protected float defend(DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
		if (part == Part.ECHO) return 0.0F;
		if (part == Part.SEGMENT) {
			// a blow on the body is a blow on the head: the last segment (the tail) is the soft part
			SpireGuard boss = owner();
			if (boss != null && level() instanceof ServerLevel level) {
				boolean tail = boss instanceof SpireKeeper keeper && keeper.tail(this);
				Mc.hurt(boss, source, amount * (tail ? 1.5F : 0.5F));
				level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.7, getZ(), tail ? 10 : 3, 0.3, 0.3, 0.3, 0.1);
			}
			return 0.0F;
		}
		if (part == Part.DECOY && level() instanceof ServerLevel level) {
			// a decoy: ink in the face of whoever struck it, and it is gone
			if (source.getEntity() instanceof LivingEntity striker) com.vnap.spire.Afflictions.stack(striker, com.vnap.spire.Afflictions.INKED, 80, 3);
			level.sendParticles(Mc.dust(0x2A2640, 1.4F), getX(), getY() + 1.0, getZ(), 40, 0.6, 0.8, 0.6, 0.05);
			playSound(com.vnap.sound.SpireSounds.INK, 1.6F, 1.0F);
			burst(level, 8);
			return 0.0F;
		}
		if (genome.defence() == Genome.Defence.WARD && tickCount < wardUntil) {
			if (level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.0, getZ(), 8, 0.4, 0.6, 0.4, 0.1);
			return 0.0F;
		}
		Vec3 from = source.getSourcePosition();
		if (genome.defence() == Genome.Defence.SHIELD && from != null) {
			Vec3 toward = from.subtract(position()).multiply(1.0, 0.0, 1.0);
			Vec3 facing = Vec3.directionFromRotation(0.0F, getYRot());
			if (toward.lengthSqr() > 1.0E-4 && toward.normalize().dot(facing) > 0.4) {
				playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + random.nextFloat() * 0.3F);
				return amount * 0.2F;
			}
		}
		return amount;
	}

	//? if >=1.21.2 {
	/*@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		float left = defend(source, amount);
		if (left <= 0.0F) return false;
		boolean hurt = super.hurtServer(level, source, left);
	*///?} else {
	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide()) return super.hurt(source, amount);
		float left = defend(source, amount);
		if (left <= 0.0F) return false;
		boolean hurt = super.hurt(source, left);
	//?}
		if (hurt && genome.defence() == Genome.Defence.WARD) wardUntil = tickCount + WARD_TICKS;
		return hurt;
	}

	// ---- no drops, no despawning, no burning: a construct of the Archive ------------------

	@Override
	//? if >=1.21.11 {
	/*protected boolean shouldDropLoot(ServerLevel level) {
	*///?} else
	protected boolean shouldDropLoot() {
		return false;
	}

	@Override
	//? if >=1.21.2 {
	/*protected int getBaseExperienceReward(ServerLevel level) {
	*///?} else
	protected int getBaseExperienceReward() {
		return 0;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		if (elite && random.nextInt(3) == 0) return com.vnap.sound.SpireSounds.ELITE;
		return com.vnap.sound.SpireSounds.idle(genome.body());
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return com.vnap.sound.SpireSounds.GUARD_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return genome.isKeeper() ? com.vnap.sound.SpireSounds.KEEPER_DEATH : com.vnap.sound.SpireSounds.GUARD_DEATH;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(level() instanceof ServerLevel level)) return;
		releaseCompany(level);
	}

	/** Its little ones go with it (a keeper's, as soon as it starts to fall). */
	protected void releaseCompany(ServerLevel level) {
		for (UUID id : summoned) {
			if (level.getEntity(id) instanceof SpireGuard small && small.isAlive()) small.burst(level, 12);
		}
		summoned.clear();
	}

	/** Gone in a burst of light: sparks and a few motes like the Core's shards. */
	public void burst(ServerLevel level, int motes) {
		level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() / 2, getZ(), motes, 0.3, 0.4, 0.3, 0.12);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() / 2, getZ(), motes, 0.4, 0.5, 0.4, 0.2);
		level.sendParticles(Mc.flash(), getX(), getY() + getBbHeight() / 2, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, blockPosition(), com.vnap.sound.SpireSounds.GUARD_DEATH, SoundSource.HOSTILE, 1.0F, 1.1F);
		discard();
	}

	@Override
	protected void tickDeath() {
		deathTime++;
		if (deathTime >= 6 && level() instanceof ServerLevel level && !isRemoved()) burst(level, genome.isKeeper() ? 60 : 16);
	}

	// ---- saved --------------------------------------------------------------------------

	@Override
	//? if >=1.21.6 {
	/*public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
		super.addAdditionalSaveData(output);
		CompoundTag tag = new CompoundTag();
	*///?} else {
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
	//?}
		tag.putLong("genome", genome.seed());
		tag.putInt("floor", genome.floor());
		tag.putString("theme", theme);
		tag.putBoolean("water", genome.movement() == Genome.Movement.SWIM);
		tag.putBoolean("keeper", genome.isKeeper());
		if (genome.keeper() >= Genome.MASON) tag.putInt("boss", genome.keeper());
		if (part != Part.NONE) tag.putBoolean("part", true);
		if (elite) tag.putBoolean("elite", true);
		tag.putLong("home", home.asLong());
		putBox(tag, "room", room);
		putBox(tag, "box", box);
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
		if (!tag.contains("genome")) return;
		theme = Nbt.getString(tag, "theme");
		if (Nbt.getBoolean(tag, "part")) discard();
		genome = tag.contains("boss") ? Genome.byKeeper(Nbt.getLong(tag, "genome"), Nbt.getInt(tag, "floor"), theme, Nbt.getInt(tag, "boss"))
				: Genome.decode(Nbt.getLong(tag, "genome"), Nbt.getInt(tag, "floor"), theme, Nbt.getBoolean(tag, "water"), Nbt.getBoolean(tag, "keeper"));
		entityData.set(LOOK, genome.look());
		home = BlockPos.of(Nbt.getLong(tag, "home"));
		if (Nbt.getBoolean(tag, "elite")) elite(false);
		room = getBox(tag, "room");
		box = getBox(tag, "box");
	}

	private static void putBox(CompoundTag tag, String key, AABB aabb) {
		CompoundTag box = new CompoundTag();
		box.putDouble("x0", aabb.minX);
		box.putDouble("y0", aabb.minY);
		box.putDouble("z0", aabb.minZ);
		box.putDouble("x1", aabb.maxX);
		box.putDouble("y1", aabb.maxY);
		box.putDouble("z1", aabb.maxZ);
		tag.put(key, box);
	}

	private static AABB getBox(CompoundTag tag, String key) {
		CompoundTag box = Nbt.getCompound(tag, key);
		return new AABB(Nbt.getDouble(box, "x0"), Nbt.getDouble(box, "y0"), Nbt.getDouble(box, "z0"), Nbt.getDouble(box, "x1"),
				Nbt.getDouble(box, "y1"), Nbt.getDouble(box, "z1"));
	}
}
