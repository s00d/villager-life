package com.vnap.spire;

import com.vnap.VillagerLife;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;

/**
 * What the Spire's chapters leave on a climber (docs/tower/BOSSES.md, "Недуги"): one affliction of its own a chapter,
 * each asking something different of the player, after games that did it well. The chapter's guards give its weak form,
 * its keeper and trial the strong one. Root: Entangled (Diablo's immobilise, struggle free by moving). Archive: Inked
 * (Splatoon's ink on the screen). Clockwork: Jolted (Hades — your next swing shocks you, so wait); the Chronarch's
 * Stopped (time stands: nor step nor swing). Garden: Sapped (League's Grievous Wounds — your healing is drunk).
 * Deep: Waterlogged (your breath runs out on dry floor). Ice: Frostbite (Elden Ring's build-up: it bursts at the fifth
 * and you take more for a while). Spores: Blight (it spreads to whoever stands by you). Hive: Stung (stacks, each
 * worse) and Honeyed (no sprint, no high jump). Stars: Starmarked (a falling star where you stand when it ends —
 * keep apart). Summit: Doom (Hades — a delayed blow; three blows of yours on the Mind lift it). Schism: Sundered
 * (WoW's Sunder Armor, armour off a stack at a time).
 */
public final class Afflictions {
	public static final Affliction ENTANGLED = new Affliction(Kind.ENTANGLED, 0x6B8E3A, ParticleTypes.COMPOSTER);
	public static final Affliction INKED = new Affliction(Kind.INKED, 0x1C1A2E, com.vnap.Mc.dust(0x2A2640, 1.0F));
	public static final Affliction JOLTED = new Affliction(Kind.JOLTED, 0xE8A33A, ParticleTypes.ELECTRIC_SPARK);
	public static final Affliction STOPPED = new Affliction(Kind.STOPPED, 0xB8B0A0, ParticleTypes.ENCHANT);
	public static final Affliction SAPPED = new Affliction(Kind.SAPPED, 0x7FB24A, ParticleTypes.CHERRY_LEAVES);
	public static final Affliction WATERLOGGED = new Affliction(Kind.WATERLOGGED, 0x3A7FB8, ParticleTypes.BUBBLE);
	public static final Affliction FROSTBITE = new Affliction(Kind.FROSTBITE, 0xA8D8F0, ParticleTypes.SNOWFLAKE);
	public static final Affliction BLIGHT = new Affliction(Kind.BLIGHT, 0x9A6AB0, ParticleTypes.SPORE_BLOSSOM_AIR);
	public static final Affliction STUNG = new Affliction(Kind.STUNG, 0xE0B020, ParticleTypes.WAX_ON);
	public static final Affliction HONEYED = new Affliction(Kind.HONEYED, 0xD89A20, ParticleTypes.DRIPPING_HONEY);
	public static final Affliction STARMARKED = new Affliction(Kind.STARMARKED, 0xE8E0FF, ParticleTypes.END_ROD);
	public static final Affliction DOOM = new Affliction(Kind.DOOM, 0x8A2040, ParticleTypes.REVERSE_PORTAL);
	public static final Affliction SUNDERED = new Affliction(Kind.SUNDERED, 0x8A7A6A, ParticleTypes.WHITE_ASH);

	private static final List<String> NAMES = List.of("entangled", "inked", "jolted", "stopped", "sapped", "waterlogged", "frostbite", "blight", "stung",
			"honeyed", "starmarked", "doom", "sundered");
	private static final List<Affliction> ALL = List.of(ENTANGLED, INKED, JOLTED, STOPPED, SAPPED, WATERLOGGED, FROSTBITE, BLIGHT, STUNG, HONEYED,
			STARMARKED, DOOM, SUNDERED);
	/** Frostbite at this amplifier is the burst's after-state: no more build-up, more damage taken. */
	public static final int BITTEN = 9;
	public static final int FROST_BURST = 4;

	static {
		ENTANGLED.addAttributeModifier(Attributes.MOVEMENT_SPEED, VillagerLife.id("entangled"), -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ENTANGLED.addAttributeModifier(Attributes.JUMP_STRENGTH, VillagerLife.id("entangled_jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		STOPPED.addAttributeModifier(Attributes.MOVEMENT_SPEED, VillagerLife.id("stopped"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		STOPPED.addAttributeModifier(Attributes.JUMP_STRENGTH, VillagerLife.id("stopped_jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		HONEYED.addAttributeModifier(Attributes.JUMP_STRENGTH, VillagerLife.id("honeyed_jump"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		HONEYED.addAttributeModifier(Attributes.MOVEMENT_SPEED, VillagerLife.id("honeyed"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		SUNDERED.addAttributeModifier(Attributes.ARMOR, VillagerLife.id("sundered"), -2.0, AttributeModifier.Operation.ADD_VALUE);
	}

	private Afflictions() {
	}

	public static void register(RegisterEvent.RegisterHelper<MobEffect> helper) {
		for (int i = 0; i < ALL.size(); i++) helper.register(VillagerLife.id(NAMES.get(i)), ALL.get(i));
	}

	public static void listen() {
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingHealEvent.class, Afflictions::onHeal);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.AttackEntityEvent.class, Afflictions::onAttack);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent.class, Afflictions::onHurt);
		NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added.class, (net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) -> {
			if (event.getEntity() instanceof Player player && event.getEffectInstance().getEffect().value() instanceof Affliction) {
				com.vnap.progress.Discoveries.discover(player, "spire_affliction");
			}
		});
	}

	public static Holder<MobEffect> holder(Affliction affliction) {
		return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(affliction);
	}

	public static MobEffectInstance of(Affliction affliction, int ticks, int amplifier) {
		return new MobEffectInstance(holder(affliction), ticks, amplifier);
	}

	public static boolean has(LivingEntity entity, Affliction affliction) {
		return entity.hasEffect(holder(affliction));
	}

	public static int level(LivingEntity entity, Affliction affliction) {
		MobEffectInstance on = entity.getEffect(holder(affliction));
		return on == null ? -1 : on.getAmplifier();
	}

	/** One more stack (up to {@code most}), the time renewed; returns the stacks now (1 for the first). */
	public static int stack(LivingEntity entity, Affliction affliction, int ticks, int most) {
		int now = Math.min(most - 1, level(entity, affliction) + 1);
		entity.removeEffect(holder(affliction));
		entity.addEffect(of(affliction, ticks, now));
		return now + 1;
	}

	/** Frost builds up; at the fifth it bursts: a sixth of the climber's health and more taken for six seconds. */
	public static void frost(LivingEntity entity, int add) {
		if (level(entity, FROSTBITE) >= BITTEN) return;
		int stacks = 0;
		for (int i = 0; i < add; i++) stacks = stack(entity, FROSTBITE, 160, FROST_BURST + 1);
		entity.setTicksFrozen(Math.min(entity.getTicksRequiredToFreeze() + 20, stacks * entity.getTicksRequiredToFreeze() / (FROST_BURST + 1)));
		if (stacks <= FROST_BURST) return;
		entity.removeEffect(holder(FROSTBITE));
		entity.addEffect(of(FROSTBITE, 120, BITTEN));
		com.vnap.Mc.hurt(entity, entity.damageSources().freeze(), entity.getMaxHealth() / 6.0F);
		entity.setTicksFrozen(entity.getTicksRequiredToFreeze() + 60);
		if (com.vnap.Mc.levelOf(entity) instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, entity.getX(), entity.getY() + 1.0, entity.getZ(), 40, 0.5, 0.8, 0.5, 0.15);
			level.playSound(null, entity.blockPosition(), com.vnap.sound.SpireSounds.FROST_BURST, net.minecraft.sounds.SoundSource.PLAYERS, 1.4F, 1.0F);
		}
	}

	/** A chapter guard's blow or shot: the weak form of its chapter's affliction. */
	public static void weak(String theme, LivingEntity target, net.minecraft.util.RandomSource random) {
		// a chapter's draught keeps its guards' weak afflictions off on its floors
		if (Draughts.warded(target)) return;
		switch (theme) {
			case "roots" -> target.addEffect(of(ENTANGLED, 30, 0));
			case "archive" -> stack(target, INKED, 100, 3);
			case "workshop" -> target.addEffect(of(JOLTED, 100, 0));
			case "gardens" -> target.addEffect(of(SAPPED, 100, 0));
			case "galleries" -> target.addEffect(of(WATERLOGGED, 60, 0));
			case "ice" -> frost(target, 1);
			case "spores" -> {
				if (!has(target, BLIGHT)) target.addEffect(of(BLIGHT, 80, 0));
			}
			case "hive" -> stack(target, STUNG, 80, 3);
			case "observatory" -> {
				if (!has(target, STARMARKED) && random.nextInt(4) == 0) mark(target, 60, 0);
			}
			case "summit" -> {
				if (!has(target, DOOM) && random.nextInt(3) == 0) target.addEffect(of(DOOM, 100, 0));
			}
			default -> {
			}
		}
	}

	/** A falling star on its way to where the climber will stand when this ends. */
	public static void mark(LivingEntity target, int ticks, int amplifier) {
		target.addEffect(of(STARMARKED, ticks, amplifier));
		if (com.vnap.Mc.levelOf(target) instanceof ServerLevel level) {
			level.playSound(null, target.blockPosition(), com.vnap.sound.SpireSounds.STAR_MARK, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 1.0F);
		}
	}

	private static void onHeal(LivingHealEvent event) {
		if (has(event.getEntity(), SAPPED)) event.setAmount(event.getAmount() * 0.6F);
	}

	private static void onAttack(AttackEntityEvent event) {
		Player player = event.getEntity();
		if (has(player, STOPPED)) {
			event.setCanceled(true);
			return;
		}
		int jolt = level(player, JOLTED);
		if (jolt < 0 || com.vnap.Mc.levelOf(player).isClientSide()) return;
		player.removeEffect(holder(JOLTED));
		com.vnap.Mc.hurt(player, player.damageSources().lightningBolt(), 3.0F + 2.0F * jolt);
		if (com.vnap.Mc.levelOf(player) instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.4, 0.8, 0.4, 0.2);
			level.playSound(null, player.blockPosition(), com.vnap.sound.SpireSounds.JOLT, net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 1.2F);
		}
	}

	private static void onHurt(LivingIncomingDamageEvent event) {
		LivingEntity hurt = event.getEntity();
		if (level(hurt, FROSTBITE) >= BITTEN) event.setAmount(event.getAmount() * 1.2F);
		// three blows on the Spire's own lift Doom off whoever struck them
		if (event.getSource().getEntity() instanceof Player player && hurt instanceof com.vnap.spire.guard.SpireGuard
				&& has(player, DOOM)) {
			int left = level(player, DOOM) - 1;
			MobEffectInstance on = player.getEffect(holder(DOOM));
			int ticks = on == null ? 0 : on.getDuration();
			player.removeEffect(holder(DOOM));
			if (left >= 0) player.addEffect(of(DOOM, ticks, left));
		}
	}

	enum Kind {
		ENTANGLED, INKED, JOLTED, STOPPED, SAPPED, WATERLOGGED, FROSTBITE, BLIGHT, STUNG, HONEYED, STARMARKED, DOOM, SUNDERED
	}

	/** One chapter's affliction: its colour, its motes about the afflicted, what it does as it lasts. */
	public static final class Affliction extends MobEffect {
		private final Kind kind;
		private final ParticleOptions mote;

		Affliction(Kind kind, int colour, ParticleOptions mote) {
			super(MobEffectCategory.HARMFUL, colour);
			this.kind = kind;
			this.mote = mote;
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return duration % 10 == 0 || duration == 1;
		}

		@Override
		//? if >=1.21.2 {
		/*public boolean applyEffectTick(ServerLevel server, LivingEntity entity, int amplifier) {
		*///?} else
		public boolean applyEffectTick(LivingEntity entity, int amplifier) {
			if (!(com.vnap.Mc.levelOf(entity) instanceof ServerLevel level)) return true;
			MobEffectInstance on = entity.getEffect(holder(this));
			int left = on == null ? 0 : on.getDuration();
			// low about the body, never at the eyes: the afflicted sees them too, in first person right before its face
			level.sendParticles(mote, entity.getX(), entity.getY() + 0.2, entity.getZ(), 2, 0.35, 0.15, 0.35, 0.0);
			if (kind == Kind.WATERLOGGED && !entity.isInWater()) {
				entity.setAirSupply(Math.max(-19, entity.getAirSupply() - 6 * (amplifier + 1)));
				if (entity.getAirSupply() <= -19) {
					entity.setAirSupply(0);
					com.vnap.Mc.hurt(entity, entity.damageSources().drown(), 1.0F);
				}
			} else if (kind == Kind.STUNG && left % 20 == 0) {
				com.vnap.Mc.hurt(entity, entity.damageSources().magic(), 0.5F * (amplifier + 1));
			} else if (kind == Kind.BLIGHT && left % 40 == 0) {
				com.vnap.Mc.hurt(entity, entity.damageSources().magic(), 1.0F);
				for (Player near : level.getEntitiesOfClass(Player.class, new AABB(entity.blockPosition()).inflate(3.0), p -> !p.getUUID().equals(entity.getUUID()) && !has(p, BLIGHT)
						&& com.vnap.spire.guard.SpireGuard.fair(p))) {
					near.addEffect(of(BLIGHT, Math.max(40, left / 2), 0));
				}
			} else if (kind == Kind.ENTANGLED && entity.getDeltaMovement().horizontalDistanceSqr() > 0.0001 && on != null && left > 12) {
				// struggling against it wears it through
				entity.removeEffect(holder(this));
				entity.addEffect(of(this, left - 6, amplifier));
			}
			if (left == 1) ends(level, entity, amplifier);
			return true;
		}

		private void ends(ServerLevel level, LivingEntity entity, int amplifier) {
			if (kind == Kind.STARMARKED) {
				level.sendParticles(com.vnap.Mc.flash(), entity.getX(), entity.getY() + 0.5, entity.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(ParticleTypes.FIREWORK, entity.getX(), entity.getY() + 0.5, entity.getZ(), 30, 0.4, 0.4, 0.4, 0.2);
				level.playSound(null, entity.blockPosition(), com.vnap.sound.SpireSounds.STARFALL, net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 1.0F);
				float damage = 4.0F + 2.0F * amplifier;
				for (Player near : level.getEntitiesOfClass(Player.class, new AABB(entity.blockPosition()).inflate(2.5), com.vnap.spire.guard.SpireGuard::fair)) {
					com.vnap.Mc.hurt(near, near.damageSources().magic(), near.getUUID().equals(entity.getUUID()) ? damage : damage * 1.5F);
				}
			} else if (kind == Kind.DOOM) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, entity.getX(), entity.getY() + 1.0, entity.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
				level.playSound(null, entity.blockPosition(), com.vnap.sound.SpireSounds.DOOM, net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 1.0F);
				com.vnap.Mc.hurt(entity, entity.damageSources().magic(), 6.0F + 3.0F * amplifier);
			}
		}
	}
}
