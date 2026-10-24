package com.vnap.spire.gear;

import com.vnap.spire.Heroes;
import com.vnap.spire.SpireGuards;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * The property of each tier (docs/tower/DESIGN.md §7). A property is on while any worn piece or the held weapon is of
 * its tier (a hero's too: its blows carry its tiers' marks, its armour stings and breaks falls): two pieces of one tier give it once, not twice, and pieces of different tiers each give their own.
 * <ul>
 * <li>Root: standing still crouched heals 1 every 3 seconds.</li>
 * <li>Archive: guards and monsters in {@link #ARCHIVE_RANGE} glow.</li>
 * <li>Clockwork: three hits within 3 seconds give Haste II (a fifth faster swings) for 3 seconds.</li>
 * <li>Garden: poison and hunger do not hold.</li>
 * <li>Deep: breathing under water; a guard felled heals 4.</li>
 * <li>Ice: a hit slows the struck for 2 seconds.</li>
 * <li>Spore: a hit, at most every 2 seconds, sheds spores on monsters within 3 blocks of the struck: poison and weakness.</li>
 * <li>Hive: who strikes the wearer is stung (poison).</li>
 * <li>Star: sight in the dark, half the fall damage.</li>
 * <li>Summit: every 30 seconds a ward of absorption (2 hearts), when the last is spent.</li>
 * </ul>
 */
public final class GearEffects {
	public static final double ARCHIVE_RANGE = 20.0;
	private static final String COMBO = "vnap_gear_combo";
	private static final String COMBO_AT = "vnap_gear_combo_at";
	private static final String SPORE_AT = "vnap_gear_spore_at";
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};

	private GearEffects() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(GearEffects::onTick);
		NeoForge.EVENT_BUS.addListener(GearEffects::onDamage);
		NeoForge.EVENT_BUS.addListener(GearEffects::onDeath);
		NeoForge.EVENT_BUS.addListener(GearEffects::onAttributes);
	}

	/** A piece's attributes are its tier's and its honing's (vanilla's sword, axe and armour carry fixed ones from 1.21.2). */
	private static void onAttributes(ItemAttributeModifierEvent event) {
		if (!(event.getItemStack().getItem() instanceof GearItem)) return;
		event.clearModifiers();
		for (ItemAttributeModifiers.Entry entry : SpireGear.attributes(event.getItemStack()).modifiers()) {
			event.addModifier(entry.attribute(), entry.modifier(), entry.slot());
		}
	}

	/** The tiers whose property is on for {@code entity}. */
	public static Set<GearTier> worn(LivingEntity entity) {
		Set<GearTier> out = EnumSet.noneOf(GearTier.class);
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = entity.getItemBySlot(slot);
			if (stack.getItem() instanceof GearItem gear && (slot != EquipmentSlot.MAINHAND || !gear.piece().isArmour())) out.add(gear.tier());
		}
		// a hero carries its sword in the off hand (spire/Heroes)
		if (entity instanceof Villager villager && Heroes.weapon(villager).getItem() instanceof GearItem sword) out.add(sword.tier());
		return out;
	}

	private static void effect(LivingEntity entity, Holder<MobEffect> effect, int ticks, int amplifier) {
		entity.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, true));
	}

	private static void onTick(PlayerTickEvent.Post event) {
		Player player = event.getEntity();
		if (!(player.level() instanceof ServerLevel level) || player.tickCount % 20 != 0 || player.isSpectator()) return;
		Set<GearTier> on = worn(player);
		if (on.isEmpty()) return;
		if (on.contains(GearTier.ROOT) && player.tickCount % 60 == 0 && player.isCrouching() && player.onGround()
				&& player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4 && player.getHealth() < player.getMaxHealth()) {
			player.heal(1.0F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.2, player.getZ(), 4, 0.3, 0.1, 0.3, 0.0);
		}
		if (on.contains(GearTier.ARCHIVE)) {
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ARCHIVE_RANGE),
					e -> e.isAlive() && (e instanceof Enemy || com.vnap.Mc.tags(e).contains(SpireGuards.TAG)))) {
				effect(near, MobEffects.GLOWING, 30, 0);
			}
		}
		if (on.contains(GearTier.GARDEN)) {
			player.removeEffect(MobEffects.POISON);
			player.removeEffect(MobEffects.HUNGER);
		}
		if (on.contains(GearTier.DEEP)) effect(player, MobEffects.WATER_BREATHING, 60, 0);
		if (on.contains(GearTier.STAR)) {
			MobEffectInstance sight = player.getEffect(MobEffects.NIGHT_VISION);
			// under 10 seconds night vision flickers
			if (sight == null || sight.getDuration() < 240) effect(player, MobEffects.NIGHT_VISION, 300, 0);
		}
		if (on.contains(GearTier.SUMMIT) && player.tickCount % 600 == 0 && player.getAbsorptionAmount() <= 0.0F) {
			effect(player, MobEffects.ABSORPTION, 600, 0);
		}
	}

	private static void onDamage(LivingIncomingDamageEvent event) {
		LivingEntity hurt = event.getEntity();
		if (!(hurt.level() instanceof ServerLevel level)) return;
		if (event.getSource().is(DamageTypeTags.IS_FALL) && worn(hurt).contains(GearTier.STAR)) event.setAmount(event.getAmount() * 0.5F);
		if (event.getSource().getEntity() instanceof LivingEntity attacker && !attacker.equals(hurt)) {
			if (attacker.equals(event.getSource().getDirectEntity()) && worn(hurt).contains(GearTier.HIVE)) effect(attacker, MobEffects.POISON, 60, 0);
			if (attacker instanceof Player || attacker instanceof Villager) struck(level, attacker, hurt, attacker.equals(event.getSource().getDirectEntity()));
		}
	}

	private static void struck(ServerLevel level, LivingEntity player, LivingEntity target, boolean melee) {
		Set<GearTier> on = worn(player);
		if (on.isEmpty()) return;
		long now = level.getGameTime();
		CompoundTag data = player.getPersistentData();
		if (on.contains(GearTier.CLOCKWORK) && melee) {
			int combo = now - com.vnap.Nbt.getLong(data, COMBO_AT) <= 60 ? com.vnap.Nbt.getInt(data, COMBO) + 1 : 1;
			data.putLong(COMBO_AT, now);
			if (combo >= 3) {
				effect(player, MobEffects.DIG_SPEED, 60, 1);
				combo = 0;
			}
			data.putInt(COMBO, combo);
		}
		if (on.contains(GearTier.ICE)) {
			effect(target, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
			level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
		}
		if (on.contains(GearTier.SPORE) && now - com.vnap.Nbt.getLong(data, SPORE_AT) >= 40) {
			data.putLong(SPORE_AT, now);
			level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, target.getX(), target.getY(0.5), target.getZ(), 30, 1.5, 0.6, 1.5, 0.0);
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.0),
					e -> e.isAlive() && (e.equals(target) || e instanceof Enemy))) {
				effect(near, MobEffects.POISON, 40, 0);
				effect(near, MobEffects.WEAKNESS, 60, 0);
			}
		}
	}

	private static void onDeath(LivingDeathEvent event) {
		if (!com.vnap.Mc.tags(event.getEntity()).contains(SpireGuards.TAG) || !(event.getSource().getEntity() instanceof Player player)) return;
		if (worn(player).contains(GearTier.DEEP)) player.heal(4.0F);
	}
}
