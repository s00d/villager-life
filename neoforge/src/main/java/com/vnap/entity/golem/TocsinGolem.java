package com.vnap.entity.golem;

import com.vnap.Nbt;
import com.vnap.entity.VillageEntities;
import com.vnap.fx.VillageParticles;
import com.vnap.item.LifeItems;
import com.vnap.mood.MoodConfig;
import com.vnap.network.ScriptShakePayload;
import com.vnap.progress.VillageAdvancements;
import com.vnap.sound.GolemSounds;
import com.vnap.village.script.VillageScriptDirector;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Tocsin Golem: an iron bell-bearer the village forges in despair.
 * Three health segments (66% / 33%) with invulnerable transitions, a rage meter that
 * punishes kiting, and short windows (stuck fist, vent, stun) where it takes extra damage.
 */
public final class TocsinGolem extends Monster {
    public static final byte FLAG_BACK_OPEN = 1;
    public static final byte FLAG_VENT = 2;
    public static final byte FLAG_STUNNED = 4;
    public static final byte FLAG_PLATES_SHED = 8;
    public static final byte FLAG_BELL_CRACKED = 16;
    public static final int RAGE_MAX = 5;
    private static final float PHASE_TWO = 0.66F;
    private static final float PHASE_THREE = 0.33F;
    private static final int LEAVE_TICKS = 20 * 60 * 5;
    private static final int BELL_COOLDOWN = 20 * 30;

    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(TocsinGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(TocsinGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(TocsinGolem.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> RAGE = SynchedEntityData.defineId(TocsinGolem.class, EntityDataSerializers.INT);
    /** The boss bar's id as a string ("" none): the optional UUID serializer is gone from 1.21.5. */
    private static final EntityDataAccessor<String> BAR = SynchedEntityData.defineId(TocsinGolem.class, EntityDataSerializers.STRING);

    /** Client animation clips, one per {@link GolemAction}. */
    public final AnimationState[] actionStates = new AnimationState[GolemAction.values().length];
    public final AnimationState idleState = new AnimationState();

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            //? if >=26.1
            /*UUID.randomUUID(),*/
            Component.translatable("entity.villager_life.tocsin_golem"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private final Set<UUID> fighters = new HashSet<>();
    private final IntSet chargeHits = new IntOpenHashSet();
    private final List<Pool> pools = new ArrayList<>();
    private final Map<BlockPos, Long> bellCooldowns = new HashMap<>();

    private long villageId = Long.MIN_VALUE;
    private BlockPos arena = BlockPos.ZERO;
    private GolemAction action = GolemAction.NONE;
    private GolemAction lastAttack = GolemAction.NONE;
    private @Nullable GolemAction queued;
    private int actionTick;
    private int actionSeq;
    private int stunLength;
    private int cooldown = 30;
    private int attacksSinceVent;
    private boolean hitPlayer;
    private long lastRageDrop;
    private int noPlayerTicks;
    private Vec3 chargeDir = Vec3.ZERO;
    private int @Nullable [] ringHeights;

    private record Pool(Vec3 pos, long until) {
    }

    public TocsinGolem(EntityType<? extends TocsinGolem> type, Level level) {
        super(type, level);
        for (int i = 0; i < actionStates.length; i++) {
            actionStates[i] = new AnimationState();
        }
        this.xpReward = 120;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    /** Called once by the forge ritual: ties the golem to its village and scales health to the crowd. */
    public void bind(long villageId, BlockPos arena) {
        this.villageId = villageId;
        this.arena = arena.immutable();
        int players = 0;
        if (level() instanceof ServerLevel server) {
            players = server.getEntitiesOfClass(Player.class, new AABB(arena).inflate(48.0),
                    p -> !p.isSpectator()).size();
        }
        double health = MoodConfig.bossMaxHealth() * (1.0 + MoodConfig.bossHealthPerPlayer() * Math.max(0, players - 1));
        AttributeInstance max = getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(health);
        }
        setHealth((float) health);
        begin(GolemAction.AWAKEN);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ACTION, 0);
        builder.define(PHASE, 0);
        builder.define(FLAGS, (byte) 0);
        builder.define(RAGE, 0);
        builder.define(BAR, "");
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this, PlateCrawler.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    // ---- synced accessors -------------------------------------------------------------

    public GolemAction action() {
        return GolemAction.byId(entityData.get(ACTION) & 31);
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public boolean flag(byte flag) {
        return (entityData.get(FLAGS) & flag) != 0;
    }

    private void setFlag(byte flag, boolean on) {
        byte flags = entityData.get(FLAGS);
        entityData.set(FLAGS, (byte) (on ? flags | flag : flags & ~flag));
    }

    public int rage() {
        return entityData.get(RAGE);
    }

    public Optional<UUID> barId() {
        String bar = entityData.get(BAR);
        return bar.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(bar));
    }

    public long villageId() {
        return villageId;
    }

    public BlockPos arena() {
        return arena;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ACTION.equals(key) && level().isClientSide()) {
            for (AnimationState state : actionStates) {
                state.stop();
            }
            GolemAction now = action();
            if (now != GolemAction.NONE) {
                actionStates[now.id].start(tickCount);
            }
        }
    }

    // ---- ticking ----------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            idleState.animateWhen(action() == GolemAction.NONE && !walkAnimation.isMoving(), tickCount);
            clientParticles();
        }
    }

    private void clientParticles() {
        // the forge in its chest always burns: embers out of the grate, smoke out of the stack on its head
        Vec3 front = Vec3.directionFromRotation(0.0F, yBodyRot);
        if (random.nextInt(5) == 0) {
            level().addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, getX() + front.x * 0.75 + (random.nextDouble() - 0.5) * 0.5,
                    getY() + 1.8 + random.nextDouble() * 0.4, getZ() + front.z * 0.75 + (random.nextDouble() - 0.5) * 0.5, front.x * 0.02, 0.01, front.z * 0.02);
        }
        if (tickCount % 6 == 0) {
            level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, getX(), getY() + getBbHeight() + 0.2, getZ(), 0.0, 0.06, 0.0);
        }
        if (onGround() && walkAnimation.isMoving() && tickCount % 9 == 0) {
            for (int i = 0; i < 6; i++) {
                level().addParticle(net.minecraft.core.particles.ParticleTypes.POOF, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 0.05,
                        getZ() + (random.nextDouble() - 0.5) * 1.6, 0.0, 0.01, 0.0);
            }
        }
        if (flag(FLAG_VENT) || flag(FLAG_BACK_OPEN)) {
            Vec3 back = Vec3.directionFromRotation(0.0F, yBodyRot).scale(-0.9);
            level().addParticle(VillageParticles.STEAM_PUFF, getX() + back.x, getY() + 2.9, getZ() + back.z,
                    back.x * 0.05, 0.08, back.z * 0.05);
        }
        if (phase() >= 3 && random.nextInt(3) == 0) {
            level().addParticle(VillageParticles.MOLTEN_DRIP, getX() + (random.nextDouble() - 0.5) * 1.4,
                    getY() + 2.6, getZ() + (random.nextDouble() - 0.5) * 1.4, 0.0, 0.0, 0.0);
        }
        if (phase() >= 2 && random.nextInt(6) == 0) {
            level().addParticle(VillageParticles.RUST_FLAKE, getX() + (random.nextDouble() - 0.5) * 2.0,
                    getY() + 1.0 + random.nextDouble() * 2.5, getZ() + (random.nextDouble() - 0.5) * 2.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    //? if >=1.21.2 {
    /*protected void customServerAiStep(ServerLevel server) {
        super.customServerAiStep(server);
    *///?} else {
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel server = (ServerLevel) level();
    //?}
        if (barId().isEmpty()) {
            entityData.set(BAR, bossEvent.getId().toString());
        }
        if (arena.equals(BlockPos.ZERO)) {
            arena = blockPosition();
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
        tickPools(server);
        if (phase() >= 1) {
            tickArena(server);
        }
        if (tickCount % 100 == 0) {
            sendVillagersHome(server);
        }
        if (tickLeave(server)) {
            return;
        }
        if (action != GolemAction.NONE) {
            actionTick++;
            runAction(server);
            if (action != GolemAction.NONE && actionTick >= actionLength()) {
                endAction();
            }
            return;
        }
        if (checkPhaseGate()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            healOutOfCombat(server);
            if (distanceToSqr(Vec3.atBottomCenterOf(arena)) > 36.0) {
                getNavigation().moveTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, 0.8);
            }
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        double dist = distanceTo(target);
        boolean targetInArena = horizontalDist(target.position(), Vec3.atCenterOf(arena)) <= MoodConfig.bossArenaRadius() + 4;
        if (horizontalDist(position(), Vec3.atCenterOf(arena)) > MoodConfig.bossArenaRadius() + 2 || !targetInArena) {
            getNavigation().moveTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, 1.0);
        } else if (dist > 3.2) {
            getNavigation().moveTo(target, 1.0);
        } else {
            getNavigation().stop();
        }
        if (cooldown <= 0) {
            GolemAction next = choose(target, dist);
            if (next != GolemAction.NONE) {
                begin(next);
            }
        }
    }

    private int actionLength() {
        return action == GolemAction.STUN ? stunLength : action.length;
    }

    private boolean checkPhaseGate() {
        float pct = getHealth() / getMaxHealth();
        if (phase() == 1 && pct <= PHASE_TWO + 0.001F) {
            begin(GolemAction.KNEEL);
            return true;
        }
        if (phase() == 2 && pct <= PHASE_THREE + 0.001F) {
            begin(GolemAction.CRACK);
            return true;
        }
        return false;
    }

    private void begin(GolemAction next) {
        action = next;
        actionTick = 0;
        actionSeq = (actionSeq + 1) & 0xFFFF;
        hitPlayer = false;
        chargeHits.clear();
        entityData.set(ACTION, (actionSeq << 5) | next.id);
        getNavigation().stop();
        if (next == GolemAction.STUN) {
            setFlag(FLAG_STUNNED, true);
        }
    }

    private void endAction() {
        GolemAction done = action;
        action = GolemAction.NONE;
        entityData.set(ACTION, ((actionSeq + 1) & 0xFFFF) << 5);
        actionSeq = (actionSeq + 1) & 0xFFFF;
        setFlag(FLAG_BACK_OPEN, false);
        setFlag(FLAG_VENT, false);
        setFlag(FLAG_STUNNED, false);
        switch (done) {
            case AWAKEN -> entityData.set(PHASE, 1);
            case KNEEL -> {
                entityData.set(PHASE, 2);
                AttributeInstance armor = getAttribute(Attributes.ARMOR);
                if (armor != null) {
                    armor.setBaseValue(6.0);
                }
            }
            case CRACK -> {
                entityData.set(PHASE, 3);
                AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) {
                    speed.setBaseValue(0.28);
                }
            }
            default -> {
            }
        }
        if (done.attack()) {
            lastAttack = done;
            if (!hitPlayer) {
                entityData.set(RAGE, Math.min(RAGE_MAX, rage() + 1));
            }
            if (phase() >= 3 && ++attacksSinceVent >= 3) {
                attacksSinceVent = 0;
                queued = GolemAction.VENT;
            }
        }
        cooldown = switch (phase()) {
            case 3 -> 16;
            case 2 -> 20;
            default -> 24;
        };
        if (done == GolemAction.CHAIN_PULL && hitPlayer) {
            queued = GolemAction.SWING_COMBO;
            cooldown = 2;
        }
        if (done == GolemAction.VENT || done == GolemAction.STUN) {
            cooldown = 10;
        }
    }

    private GolemAction choose(LivingEntity target, double dist) {
        if (queued != null) {
            GolemAction next = queued;
            queued = null;
            return next;
        }
        if (rage() >= RAGE_MAX) {
            entityData.set(RAGE, 0);
            return GolemAction.CHARGE;
        }
        List<GolemAction> pool = new ArrayList<>();
        int total = 0;
        for (GolemAction candidate : GolemAction.values()) {
            if (!candidate.attack() || candidate.phase > phase() || !inRange(candidate, target, dist)) {
                continue;
            }
            pool.add(candidate);
            total += candidate.weight;
        }
        if (pool.size() > 1 && pool.remove(lastAttack)) {
            total -= lastAttack.weight;
        }
        if (pool.isEmpty()) {
            return GolemAction.NONE;
        }
        int roll = random.nextInt(total);
        for (GolemAction candidate : pool) {
            roll -= candidate.weight;
            if (roll < 0) {
                return candidate;
            }
        }
        return pool.get(0);
    }

    private boolean inRange(GolemAction attack, LivingEntity target, double dist) {
        return switch (attack) {
            case SWING_COMBO -> dist <= 4.5;
            case GRAB_THROW -> dist <= 3.6;
            case FIST_SLAM -> dist <= 8.0;
            case BELL_TOLL -> dist <= 6.5;
            case BELL_WAVE -> dist <= 14.0;
            case ANVIL_RAIN -> dist <= 24.0;
            case CHAIN_PULL -> dist >= 6.0 && dist <= 16.0 && hasLineOfSight(target);
            case CHARGE -> dist >= 5.0 && dist <= 20.0;
            case MOLTEN_SLAM -> dist <= 6.0;
            default -> false;
        };
    }

    // ---- actions ----------------------------------------------------------------------

    private void runAction(ServerLevel server) {
        int t = actionTick;
        LivingEntity target = getTarget();
        switch (action) {
            case AWAKEN -> awaken(server, t);
            case SWING_COMBO -> {
                if (t < 12 || (t > 15 && t < 22)) {
                    face(target);
                }
                if (t == 14 || t == 24) {
                    server.playSound(null, blockPosition(), com.vnap.sound.GolemSounds.GOLEM_SWING, SoundSource.HOSTILE, 1.4F, 1.0F);
                    hurtCone(4.4, 0.25, 12.0F, 0.9);
                }
            }
            case FIST_SLAM -> fistSlam(server, t, target);
            case GRAB_THROW -> {
                if (t < 14) {
                    face(target);
                }
                if (t == 16 && target != null && distanceTo(target) <= 3.8 && inFront(target, 0.3)) {
                    if (strike(target, 8.0F)) {
                        Vec3 away = horizontal(target.position().subtract(position())).scale(1.5);
                        target.setDeltaMovement(away.x, 0.95, away.z);
                        com.vnap.Mc.knocked(target);
                        server.playSound(null, blockPosition(), com.vnap.sound.GolemSounds.CLANG, SoundSource.HOSTILE, 1.4F, 0.8F);
                    }
                }
            }
            case BELL_TOLL -> bellToll(server, t);
            case BELL_WAVE -> bellWave(server, t);
            case ANVIL_RAIN -> anvilRain(server, t);
            case CHAIN_PULL -> chainPull(server, t, target);
            case CHARGE -> charge(server, t, target);
            case MOLTEN_SLAM -> moltenSlam(server, t, target);
            case VENT -> {
                if (t == 1) {
                    setFlag(FLAG_VENT, true);
                    server.playSound(null, blockPosition(), GolemSounds.STEAM, SoundSource.HOSTILE, 1.6F, 0.7F);
                }
                if (t % 4 == 0) {
                    server.sendParticles(VillageParticles.STEAM_PUFF, getX(), getY() + 3.0, getZ(), 6, 0.6, 0.3, 0.6, 0.05);
                }
            }
            case STUN -> {
                if (t % 5 == 0) {
                    server.sendParticles(ParticleTypes.CRIT, getX(), getY() + 4.1, getZ(), 6, 0.5, 0.1, 0.5, 0.05);
                }
            }
            case KNEEL -> kneel(server, t);
            case CRACK -> crack(server, t);
            default -> {
            }
        }
    }

    private void awaken(ServerLevel server, int t) {
        if (t == 1) {
            server.playSound(null, blockPosition(), GolemSounds.AWAKEN, SoundSource.HOSTILE, 2.0F, 1.0F);
        }
        if (t < 50 && t % 3 == 0) {
            server.sendParticles(VillageParticles.RUST_FLAKE, getX(), getY() + 2.0, getZ(), 6, 0.9, 1.4, 0.9, 0.02);
            server.sendParticles(VillageParticles.IRON_SPARK, getX(), getY() + 0.2, getZ(), 3, 1.2, 0.1, 1.2, 0.1);
        }
        if (t == 50) {
            server.playSound(null, blockPosition(), GolemSounds.TOLL, SoundSource.HOSTILE, 2.5F, 0.8F);
            VillageParticles.ring(server, getX(), getY() + 0.1, getZ(), 9.0);
            shake(server, 1.0F, 32.0);
            for (LivingEntity entity : victims(getBoundingBox().inflate(8.0, 2.0, 8.0))) {
                Vec3 push = horizontal(entity.position().subtract(position())).scale(1.2);
                entity.setDeltaMovement(push.x, 0.45, push.z);
                com.vnap.Mc.knocked(entity);
                strike(entity, 4.0F);
            }
        }
        if (t == 60) {
            GolemLines.say(server, this, GolemLines.AWAKEN);
            for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48.0), p -> true)) {
                VillageAdvancements.award(player, "tocsin/awaken");
            }
        }
    }

    private void fistSlam(ServerLevel server, int t, @Nullable LivingEntity target) {
        if (t < 18) {
            face(target);
        }
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        if (t >= 8 && t < 20 && t % 2 == 0) {
            for (int i = 2; i <= 8; i += 2) {
                server.sendParticles(ParticleTypes.SMOKE, getX() + forward.x * i, getY() + 0.1, getZ() + forward.z * i,
                        1, 0.2, 0.0, 0.2, 0.0);
            }
        }
        if (t == 20) {
            server.playSound(null, blockPosition(), GolemSounds.SLAM, SoundSource.HOSTILE, 2.0F, 1.0F);
            shake(server, 0.9F, 24.0);
            BlockState below = server.getBlockState(blockPosition().below());
            for (int i = 1; i <= 8; i++) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below.isAir() ? Blocks.STONE.defaultBlockState() : below),
                        getX() + forward.x * i, getY() + 0.2, getZ() + forward.z * i, 10, 0.4, 0.2, 0.4, 0.2);
            }
            for (LivingEntity entity : victims(getBoundingBox().inflate(9.0, 2.0, 9.0))) {
                Vec3 rel = entity.position().subtract(position());
                double along = rel.x * forward.x + rel.z * forward.z;
                double side = Math.abs(rel.x * forward.z - rel.z * forward.x);
                if (along > 0.0 && along <= 8.5 && side <= 1.6) {
                    if (strike(entity, 14.0F) && entity instanceof Player player) {
                        com.vnap.Mc.disableShield(player);
                    }
                }
            }
            setFlag(FLAG_BACK_OPEN, true);
        }
        if (t > 20 && t < 70 && t % 5 == 0) {
            Vec3 back = forward.scale(-0.9);
            server.sendParticles(VillageParticles.STEAM_PUFF, getX() + back.x, getY() + 2.8, getZ() + back.z, 3, 0.2, 0.2, 0.2, 0.03);
        }
        if (t == 70) {
            setFlag(FLAG_BACK_OPEN, false);
            server.playSound(null, blockPosition(), GolemSounds.CLANG, SoundSource.HOSTILE, 1.2F, 0.8F);
        }
    }

    private void bellToll(ServerLevel server, int t) {
        if (t == 10) {
            VillageParticles.ring(server, getX(), getY() + 0.1, getZ(), 7.0);
            server.playSound(null, blockPosition(), com.vnap.sound.GolemSounds.TOLL, SoundSource.HOSTILE, 1.5F, 1.0F);
        }
        if (t < 30 && t % 4 == 0) {
            server.sendParticles(VillageParticles.IRON_SPARK, getX(), getY() + 2.7, getZ(), 4, 0.4, 0.3, 0.4, 0.05);
        }
        if (t == 30) {
            server.playSound(null, blockPosition(), GolemSounds.TOLL, SoundSource.HOSTILE, 3.0F, 0.6F);
            shake(server, 0.6F, 20.0);
            for (LivingEntity entity : victims(getBoundingBox().inflate(7.0, 2.5, 7.0))) {
                if (horizontalDist(entity.position(), position()) <= 7.0 && strike(entity, 10.0F)) {
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                    Vec3 push = horizontal(entity.position().subtract(position())).scale(0.7);
                    entity.push(push.x, 0.25, push.z);
                    com.vnap.Mc.knocked(entity);
                }
            }
        }
    }

    private void bellWave(ServerLevel server, int t) {
        if (t == 4) {
            server.playSound(null, blockPosition(), GolemSounds.TOLL, SoundSource.HOSTILE, 2.0F, 0.7F);
        }
        for (int k = 0; k < 3; k++) {
            int hit = 16 + k * 10;
            double radius = 5.0 + k * 5.0;
            if (t == hit - 10) {
                VillageParticles.ring(server, getX(), getY() + 0.1, getZ(), radius);
            }
            if (t == hit) {
                server.playSound(null, blockPosition(), GolemSounds.TOLL, SoundSource.HOSTILE, 1.6F, 0.7F + k * 0.15F);
                ringDust(server, radius);
                for (LivingEntity entity : victims(getBoundingBox().inflate(radius + 1.0, 2.0, radius + 1.0))) {
                    double d = horizontalDist(entity.position(), position());
                    boolean grounded = entity.onGround() || entity.getY() - getY() < 0.9;
                    if (d >= radius - 1.2 && d <= radius + 0.6 && grounded && strike(entity, 8.0F)) {
                        entity.push(0.0, 0.45, 0.0);
                        com.vnap.Mc.knocked(entity);
                    }
                }
            }
        }
    }

    private void ringDust(ServerLevel server, double radius) {
        BlockState below = server.getBlockState(blockPosition().below());
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK,
                below.isAir() ? Blocks.STONE.defaultBlockState() : below);
        int points = (int) (radius * 6);
        for (int i = 0; i < points; i++) {
            double a = i / (double) points * Math.PI * 2.0;
            server.sendParticles(dust, getX() + Math.cos(a) * radius, getY() + 0.2, getZ() + Math.sin(a) * radius,
                    2, 0.1, 0.1, 0.1, 0.1);
        }
    }

    private void anvilRain(ServerLevel server, int t) {
        if (t < 20 && t % 4 == 0) {
            server.sendParticles(VillageParticles.IRON_SPARK, getX(), getY() + 4.2, getZ(), 5, 0.6, 0.2, 0.6, 0.1);
        }
        if (t != 20) {
            return;
        }
        server.playSound(null, blockPosition(), GolemSounds.CHAIN, SoundSource.HOSTILE, 1.6F, 0.6F);
        List<Vec3> spots = new ArrayList<>();
        for (Player player : server.getEntitiesOfClass(Player.class, new AABB(arena).inflate(MoodConfig.bossArenaRadius() + 4.0),
                p -> !p.isSpectator() && !p.isCreative())) {
            if (spots.size() >= 3) {
                break;
            }
            spots.add(player.position().add(player.getDeltaMovement().scale(10.0)));
        }
        while (spots.size() < 6) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 3.0 + random.nextDouble() * 8.0;
            spots.add(position().add(Math.cos(a) * r, 0.0, Math.sin(a) * r));
        }
        for (int i = 0; i < spots.size(); i++) {
            Vec3 spot = spots.get(i);
            int ground = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(spot.x), (int) Math.floor(spot.z));
            TocsinAnvil.drop(server, this, spot.x, ground, spot.z, 11.0 + i * 1.5);
        }
    }

    private void chainPull(ServerLevel server, int t, @Nullable LivingEntity target) {
        if (t < 18) {
            face(target);
        }
        if (t != 18 || target == null || distanceTo(target) > 16.5 || !hasLineOfSight(target)) {
            return;
        }
        server.playSound(null, blockPosition(), GolemSounds.CHAIN, SoundSource.HOSTILE, 1.8F, 0.8F);
        Vec3 from = position().add(0.0, 2.4, 0.0);
        Vec3 to = target.position().add(0.0, 1.0, 0.0);
        for (int i = 0; i <= 16; i++) {
            Vec3 p = from.lerp(to, i / 16.0);
            server.sendParticles(VillageParticles.IRON_SPARK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (strike(target, 4.0F)) {
            Vec3 pull = horizontal(position().subtract(target.position())).scale(Math.min(2.2, distanceTo(target) * 0.17));
            target.setDeltaMovement(pull.x, 0.4, pull.z);
            com.vnap.Mc.knocked(target);
        }
    }

    private void charge(ServerLevel server, int t, @Nullable LivingEntity target) {
        if (t < 20) {
            face(target);
            if (t % 3 == 0) {
                server.sendParticles(VillageParticles.STEAM_PUFF, getX(), getY() + 0.3, getZ(), 4, 0.8, 0.1, 0.8, 0.05);
            }
            if (t == 4) {
                server.playSound(null, blockPosition(), GolemSounds.STEAM, SoundSource.HOSTILE, 1.5F, 0.6F);
            }
            return;
        }
        if (t == 20) {
            chargeDir = target != null ? horizontal(target.position().subtract(position())) : Vec3.directionFromRotation(0.0F, getYRot());
            server.playSound(null, blockPosition(), com.vnap.sound.GolemSounds.GOLEM_ROAR, SoundSource.HOSTILE, 1.6F, 1.0F);
        }
        if (t > 50) {
            return;
        }
        if (t > 22 && horizontalCollision && !breakSoftBlocks(server)) {
            server.playSound(null, blockPosition(), GolemSounds.SLAM, SoundSource.HOSTILE, 2.0F, 1.2F);
            shake(server, 1.0F, 24.0);
            stun(40);
            return;
        }
        setDeltaMovement(chargeDir.x * 0.72, getDeltaMovement().y, chargeDir.z * 0.72);
        for (LivingEntity entity : victims(getBoundingBox().inflate(0.8))) {
            if (chargeHits.add(entity.getId()) && strike(entity, 16.0F)) {
                entity.setDeltaMovement(chargeDir.x * 1.6, 0.6, chargeDir.z * 1.6);
                com.vnap.Mc.knocked(entity);
            }
        }
        if (t % 2 == 0) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, server.getBlockState(blockPosition().below())),
                    getX(), getY() + 0.1, getZ(), 6, 0.8, 0.0, 0.8, 0.1);
        }
    }

    private boolean breakSoftBlocks(ServerLevel server) {
        if (!MoodConfig.bossBreaksLeaves() || !com.vnap.Mc.mobGriefing(server)) {
            return false;
        }
        boolean broke = false;
        AABB ahead = getBoundingBox().move(chargeDir.scale(0.8));
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(ahead.minX, ahead.minY, ahead.minZ),
                BlockPos.containing(ahead.maxX, ahead.maxY, ahead.maxZ))) {
            BlockState state = server.getBlockState(pos);
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.IMPERMEABLE)) {
                server.destroyBlock(pos, true, this);
                broke = true;
            }
        }
        return broke;
    }

    private void moltenSlam(ServerLevel server, int t, @Nullable LivingEntity target) {
        if (t < 20) {
            face(target);
        }
        if (t < 22 && t % 3 == 0) {
            server.sendParticles(VillageParticles.MOLTEN_DRIP, getX(), getY() + 3.2, getZ(), 4, 0.6, 0.3, 0.6, 0.0);
        }
        if (t != 22) {
            return;
        }
        server.playSound(null, blockPosition(), GolemSounds.SLAM, SoundSource.HOSTILE, 2.0F, 0.8F);
        server.playSound(null, blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 1.4F, 0.6F);
        shake(server, 1.0F, 24.0);
        hurtAround(position(), 4.5, 14.0F, 0.8);
        long until = server.getGameTime() + 120L;
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        pools.add(new Pool(position().add(forward.scale(2.5)), until));
        for (int i = 0; i < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 3.0 + random.nextDouble() * 4.0;
            pools.add(new Pool(position().add(Math.cos(a) * r, 0.0, Math.sin(a) * r), until));
        }
    }

    private void kneel(ServerLevel server, int t) {
        if (t == 1) {
            server.playSound(null, blockPosition(), GolemSounds.CRACK, SoundSource.HOSTILE, 1.8F, 1.1F);
        }
        if (t == 18) {
            setFlag(FLAG_PLATES_SHED, true);
            server.playSound(null, blockPosition(), GolemSounds.CLANG, SoundSource.HOSTILE, 2.0F, 0.6F);
            server.sendParticles(VillageParticles.RUST_FLAKE, getX(), getY() + 2.0, getZ(), 40, 1.2, 1.0, 1.2, 0.08);
            spawnCrawlers(server, 3);
        }
        if (t == 30) {
            GolemLines.say(server, this, GolemLines.PHASE_TWO);
        }
    }

    private void crack(ServerLevel server, int t) {
        if (t == 1) {
            server.playSound(null, blockPosition(), GolemSounds.TOLL, SoundSource.HOSTILE, 3.0F, 0.5F);
        }
        if (t == 30) {
            setFlag(FLAG_BELL_CRACKED, true);
            server.playSound(null, blockPosition(), GolemSounds.CRACK, SoundSource.HOSTILE, 2.5F, 0.7F);
            server.sendParticles(VillageParticles.MOLTEN_DRIP, getX(), getY() + 2.6, getZ(), 30, 0.6, 0.4, 0.6, 0.1);
            server.sendParticles(VillageParticles.IRON_SPARK, getX(), getY() + 2.6, getZ(), 30, 0.6, 0.4, 0.6, 0.3);
            VillageParticles.ring(server, getX(), getY() + 0.1, getZ(), 12.0);
            shake(server, 1.2F, 32.0);
            spawnCrawlers(server, 2);
        }
        if (t == 44) {
            GolemLines.say(server, this, GolemLines.PHASE_THREE);
        }
    }

    private void spawnCrawlers(ServerLevel server, int count) {
        int alive = server.getEntities(VillageEntities.PLATE_CRAWLER, getBoundingBox().inflate(48.0), Entity::isAlive).size();
        for (int i = 0; i < Math.min(count, PlateCrawler.CAP - alive); i++) {
            PlateCrawler crawler = com.vnap.Mc.create(VillageEntities.PLATE_CRAWLER, server);
            if (crawler == null) {
                continue;
            }
            double a = random.nextDouble() * Math.PI * 2.0;
            com.vnap.Mc.snap(crawler, getX() + Math.cos(a) * 1.8, getY() + 1.5, getZ() + Math.sin(a) * 1.8, random.nextFloat() * 360.0F, 0.0F);
            crawler.setDeltaMovement(Math.cos(a) * 0.4, 0.4, Math.sin(a) * 0.4);
            server.addFreshEntity(crawler);
        }
    }

    // ---- arena, pools, leaving --------------------------------------------------------

    private void tickArena(ServerLevel server) {
        long now = server.getGameTime();
        int radius = MoodConfig.bossArenaRadius();
        if (now % 10L == 0L) {
            int points = 36;
            if (ringHeights == null) {
                ringHeights = new int[points];
                List<BlockPos> ring = com.vnap.village.town.Rings.ring(arena, radius, points);
                for (int i = 0; i < points; i++) {
                    ringHeights[i] = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ring.get(i).getX(), ring.get(i).getZ());
                }
            }
            int offset = (int) (now / 10L % 3L);
            for (int i = offset; i < points; i += 3) {
                double a = i / (double) points * Math.PI * 2.0;
                server.sendParticles(VillageParticles.IRON_SPARK, arena.getX() + 0.5 + Math.cos(a) * radius, ringHeights[i] + 0.15,
                        arena.getZ() + 0.5 + Math.sin(a) * radius, 1, 0.0, 0.02, 0.0, 0.0);
            }
        }
        if (now % 20L != 0L) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(arena);
        for (Player player : server.getEntitiesOfClass(Player.class, new AABB(arena).inflate(radius + 16.0),
                p -> !p.isSpectator() && !p.isCreative())) {
            double d = horizontalDist(player.position(), center);
            if (d > radius + 1.0 && d < radius + 16.0) {
                Vec3 pull = horizontal(center.subtract(player.position())).scale(0.6);
                player.push(pull.x, 0.1, pull.z);
                com.vnap.Mc.knocked(player);
                server.playSound(null, player.blockPosition(), GolemSounds.CHAIN, SoundSource.HOSTILE, 0.6F, 1.2F);
                server.sendParticles(VillageParticles.IRON_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    private void tickPools(ServerLevel server) {
        if (pools.isEmpty()) {
            return;
        }
        long now = server.getGameTime();
        pools.removeIf(pool -> pool.until() <= now);
        for (Pool pool : pools) {
            if (now % 4L == 0L) {
                server.sendParticles(VillageParticles.MOLTEN_DRIP, pool.pos().x, pool.pos().y + 0.1, pool.pos().z, 2, 0.6, 0.0, 0.6, 0.0);
                server.sendParticles(ParticleTypes.SMALL_FLAME, pool.pos().x, pool.pos().y + 0.1, pool.pos().z, 1, 0.5, 0.0, 0.5, 0.01);
            }
            if (now % 20L == 0L) {
                for (LivingEntity entity : victims(new AABB(pool.pos(), pool.pos()).inflate(1.3, 1.0, 1.3))) {
                    com.vnap.Mc.hurt(entity, damageSources().hotFloor(), 2.0F);
                }
            }
        }
    }

    private boolean tickLeave(ServerLevel server) {
        boolean anyone = !server.getEntitiesOfClass(Player.class, new AABB(arena).inflate(64.0), p -> !p.isSpectator()).isEmpty();
        noPlayerTicks = anyone ? 0 : noPlayerTicks + 1;
        if (noPlayerTicks < LEAVE_TICKS) {
            return false;
        }
        server.sendParticles(VillageParticles.RUST_FLAKE, getX(), getY() + 2.0, getZ(), 60, 1.0, 1.5, 1.0, 0.05);
        server.playSound(null, blockPosition(), GolemSounds.TOLL, SoundSource.HOSTILE, 1.5F, 0.4F);
        GolemLines.say(server, this, GolemLines.LEFT);
        discardCrawlers(server);
        discard();
        if (villageId != Long.MIN_VALUE) {
            VillageScriptDirector.onBossLeft(server, villageId);
        }
        return true;
    }

    private void healOutOfCombat(ServerLevel server) {
        if (server.getGameTime() % 20L != 0L || phase() == 0) {
            return;
        }
        float share = switch (phase()) {
            case 2 -> PHASE_TWO;
            case 3 -> PHASE_THREE;
            default -> 1.0F;
        };
        float cap = share * getMaxHealth();
        if (getHealth() < cap) {
            setHealth(Math.min(cap, getHealth() + 4.0F));
        }
    }

    // ---- damage -----------------------------------------------------------------------

    //? if >=1.21.2 {
    /*@Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount);
        }
    *///?} else {
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
    //?}
        if (source.getEntity() instanceof AbstractVillager || source.getEntity() instanceof PlateCrawler) {
            return false;
        }
        if (action.invulnerable || phase() == 0) {
            clang(source);
            return false;
        }
        float scaled = amount;
        int side = side(source);
        if (flag(FLAG_BACK_OPEN)) {
            if (side < 0) {
                scaled *= 2.0F;
                if (source.getEntity() instanceof ServerPlayer player) {
                    VillageAdvancements.award(player, "tocsin/heart");
                }
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(VillageParticles.IRON_SPARK, getX(), getY() + 2.6, getZ(), 12, 0.3, 0.3, 0.3, 0.25);
                }
            } else if (side > 0) {
                scaled *= 0.5F;
                clang(source);
            }
        }
        if (flag(FLAG_VENT)) {
            scaled *= side < 0 ? 2.0F : 1.5F;
        }
        if (flag(FLAG_STUNNED)) {
            scaled *= 1.5F;
        }
        float floor = switch (phase()) {
            case 1 -> PHASE_TWO * getMaxHealth();
            case 2 -> PHASE_THREE * getMaxHealth();
            default -> 0.0F;
        };
        if (floor > 0.0F && getHealth() - scaled < floor) {
            scaled = Math.max(0.0F, getHealth() - floor);
        }
        if (scaled <= 0.0F) {
            return false;
        }
        if (source.getEntity() instanceof Player player) {
            fighters.add(player.getUUID());
            long now = level().getGameTime();
            if (now - lastRageDrop >= 20L) {
                lastRageDrop = now;
                entityData.set(RAGE, Math.max(0, rage() - 1));
            }
        }
        //? if >=1.21.2 {
        /*boolean hurt = super.hurtServer(level, source, scaled);
        *///?} else
        boolean hurt = super.hurt(source, scaled);
        return hurt;
    }

    /** +1 front, -1 back, 0 flank or unknown. */
    private int side(DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return 0;
        }
        Vec3 to = horizontal(from.subtract(position()));
        double dot = to.dot(Vec3.directionFromRotation(0.0F, yBodyRot));
        return dot > 0.35 ? 1 : dot < -0.35 ? -1 : 0;
    }

    private void clang(DamageSource source) {
        if (level() instanceof ServerLevel server && source.getEntity() != null) {
            server.playSound(null, blockPosition(), GolemSounds.CLANG, SoundSource.HOSTILE, 1.0F, 1.0F);
            server.sendParticles(VillageParticles.IRON_SPARK, getX(), getY() + 2.0, getZ(), 5, 0.6, 0.6, 0.6, 0.2);
        }
    }

    private List<LivingEntity> victims(AABB box) {
        return level().getEntitiesOfClass(LivingEntity.class, box, this::canHit);
    }

    private boolean canHit(LivingEntity entity) {
        return entity.isAlive()
                && !(entity instanceof AbstractVillager)
                && !(entity instanceof TocsinGolem)
                && !(entity instanceof PlateCrawler)
                && !(entity instanceof Player player && (player.isSpectator() || player.isCreative()));
    }

    private boolean strike(LivingEntity entity, float damage) {
        if (!canHit(entity)) {
            return false;
        }
        boolean hurt = com.vnap.Mc.hurt(entity, damageSources().mobAttack(this), damage);
        if (hurt && entity instanceof Player) {
            hitPlayer = true;
        }
        return hurt;
    }

    /** Area hit; also used by falling anvils. */
    public void hurtAround(Vec3 center, double radius, float damage, double knock) {
        for (LivingEntity entity : victims(new AABB(center, center).inflate(radius, 1.5, radius))) {
            if (horizontalDist(entity.position(), center) <= radius && strike(entity, damage) && knock > 0.0) {
                Vec3 push = horizontal(entity.position().subtract(center)).scale(knock);
                entity.push(push.x, 0.3, push.z);
                com.vnap.Mc.knocked(entity);
            }
        }
    }

    private void hurtCone(double range, double minDot, float damage, double knock) {
        Vec3 look = Vec3.directionFromRotation(0.0F, getYRot());
        for (LivingEntity entity : victims(getBoundingBox().inflate(range, 1.5, range))) {
            Vec3 to = horizontal(entity.position().subtract(position()));
            if (to.dot(look) >= minDot && horizontalDist(entity.position(), position()) <= range && strike(entity, damage)) {
                entity.push(to.x * knock, 0.25, to.z * knock);
                com.vnap.Mc.knocked(entity);
            }
        }
    }

    // ---- outside triggers -------------------------------------------------------------

    /** Any stagger: wall impact, village bell, counter-toll. Never interrupts a phase change. */
    public void stun(int ticks) {
        if (action.invulnerable || isDeadOrDying()) {
            return;
        }
        stunLength = ticks;
        begin(GolemAction.STUN);
        if (level() instanceof ServerLevel server) {
            server.playSound(null, blockPosition(), GolemSounds.CLANG, SoundSource.HOSTILE, 1.6F, 0.6F);
        }
    }

    /** Villagers that hear the tocsin hide in their homes, the same way they answer a rung bell during a raid. */
    private void sendVillagersHome(ServerLevel server) {
        long now = server.getGameTime();
        for (Villager villager : server.getEntitiesOfClass(Villager.class, getBoundingBox().inflate(48.0),
                v -> v.isAlive() && !v.isBaby())) {
            villager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
        }
    }

    /** A staff toll while the golem winds up its own: stagger it. */
    public boolean counterToll() {
        if (action != GolemAction.BELL_TOLL || actionTick >= 30) {
            return false;
        }
        stun(20);
        return true;
    }

    /** Someone rang a village bell near the arena. */
    public static void onBellRung(ServerLevel level, BlockPos bell, Player player) {
        double reach = MoodConfig.bossArenaRadius() + 8.0;
        for (TocsinGolem golem : level.getEntities(VillageEntities.TOCSIN_GOLEM, new AABB(bell).inflate(reach), Entity::isAlive)) {
            long now = level.getGameTime();
            Long until = golem.bellCooldowns.get(bell);
            if ((until != null && until > now) || golem.phase() == 0) {
                continue;
            }
            golem.bellCooldowns.put(bell.immutable(), now + BELL_COOLDOWN);
            golem.stun(40);
            VillageParticles.ring(level, bell.getX() + 0.5, bell.getY() + 0.1, bell.getZ() + 0.5, 4.0);
            if (player instanceof ServerPlayer serverPlayer) {
                VillageAdvancements.award(serverPlayer, "tocsin/bell");
            }
        }
    }

    // ---- death and loot ---------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        begin(GolemAction.DEATH);
        server.playSound(null, blockPosition(), GolemSounds.DEATH, SoundSource.HOSTILE, 2.0F, 1.0F);
        discardCrawlers(server);
        pools.clear();
        GolemLines.say(server, this, GolemLines.SLAIN);
        for (UUID id : fighters) {
            if (server.getPlayerByUUID(id) instanceof ServerPlayer player) {
                VillageAdvancements.award(player, "tocsin/silence");
            }
        }
        if (villageId != Long.MIN_VALUE) {
            VillageScriptDirector.onBossSlain(server, villageId);
        }
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel server) {
            if (deathTime % 4 == 0) {
                server.sendParticles(VillageParticles.RUST_FLAKE, getX(), getY() + 2.0, getZ(), 12, 0.9, 1.2, 0.9, 0.04);
                server.sendParticles(VillageParticles.STEAM_PUFF, getX(), getY() + 2.8, getZ(), 4, 0.6, 0.4, 0.6, 0.04);
            }
            if (deathTime == 40) {
                server.playSound(null, blockPosition(), GolemSounds.SLAM, SoundSource.HOSTILE, 2.0F, 0.6F);
                shake(server, 1.0F, 24.0);
            }
        }
        if (deathTime >= GolemAction.DEATH.length && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    /** Every player who hurt the golem rolls the loot table for themselves; the staff drops once. */
    @Override
    //? if >=1.21.2 {
    /*protected void dropFromLootTable(ServerLevel server, DamageSource source, boolean attackedRecently) {
    *///?} else {
    protected void dropFromLootTable(DamageSource source, boolean attackedRecently) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
    //?}
        //? if >=1.21.2 {
        /*ResourceKey<LootTable> key = getLootTable().orElse(null);
        if (key == null) return;
        *///?} else
        ResourceKey<LootTable> key = getLootTable();
        LootTable table = server.getServer().reloadableRegistries().getLootTable(key);
        Set<UUID> rollers = new HashSet<>(fighters);
        if (lastHurtByPlayer != null) {
            rollers.add(lastHurtByPlayer.getUUID());
        }
        for (UUID id : rollers) {
            if (!(server.getPlayerByUUID(id) instanceof ServerPlayer player) || player.distanceToSqr(this) > 96.0 * 96.0) {
                continue;
            }
            LootParams params = new LootParams.Builder(server)
                    .withParameter(LootContextParams.THIS_ENTITY, this)
                    .withParameter(LootContextParams.ORIGIN, position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
                    .withLuck(player.getLuck())
                    .create(LootContextParamSets.ENTITY);
            table.getRandomItems(params, stack -> dropFor(server, player, stack));
        }
        com.vnap.Mc.drop(this, new ItemStack(LifeItems.BLESSING_STAFF));
    }

    private void dropFor(ServerLevel server, ServerPlayer player, ItemStack stack) {
        ItemEntity item = new ItemEntity(server, getX(), getY() + 1.0, getZ(), stack);
        item.setTarget(player.getUUID());
        item.setDefaultPickUpDelay();
        server.addFreshEntity(item);
    }

    private void discardCrawlers(ServerLevel server) {
        for (PlateCrawler crawler : server.getEntities(VillageEntities.PLATE_CRAWLER, getBoundingBox().inflate(64.0), e -> true)) {
            server.sendParticles(VillageParticles.RUST_FLAKE, crawler.getX(), crawler.getY() + 0.3, crawler.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
            crawler.discard();
        }
    }

    // ---- helpers ----------------------------------------------------------------------

    private void face(@Nullable LivingEntity target) {
        if (target == null) {
            return;
        }
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private boolean inFront(Entity entity, double minDot) {
        return horizontal(entity.position().subtract(position())).dot(Vec3.directionFromRotation(0.0F, getYRot())) >= minDot;
    }

    private static Vec3 horizontal(Vec3 v) {
        Vec3 flat = new Vec3(v.x, 0.0, v.z);
        return flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
    }

    private static double horizontalDist(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void shake(ServerLevel server, float intensity, double radius) {
        for (ServerPlayer player : server.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(radius), p -> true)) {
            float falloff = (float) Math.max(0.0, 1.0 - player.distanceTo(this) / radius);
            if (falloff > 0.05F) {
                PacketDistributor.sendToPlayer(player, new ScriptShakePayload(intensity * falloff));
            }
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(GolemSounds.STEP, 1.2F, 1.0F);
        if (level() instanceof ServerLevel server && phase() >= 1) {
            shake(server, 0.2F, 10.0);
        }
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return GolemSounds.HURT;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
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
        tag.putLong("village", villageId);
        tag.putLong("arena", arena.asLong());
        tag.putInt("phase", phase());
        tag.putByte("flags", (byte) (entityData.get(FLAGS) & (FLAG_PLATES_SHED | FLAG_BELL_CRACKED)));
        //? if >=1.21.6
        /*com.vnap.Mc.put(output, tag);*/
    }

    @Override
    //? if >=1.21.6 {
    /*public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        CompoundTag tag = com.vnap.Mc.tagOf(input);
    *///?} else {
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    //?}
        villageId = tag.contains("village") ? Nbt.getLong(tag, "village") : Long.MIN_VALUE;
        arena = BlockPos.of(Nbt.getLong(tag, "arena"));
        int phase = Math.max(1, Nbt.getInt(tag, "phase"));
        entityData.set(PHASE, phase);
        entityData.set(FLAGS, Nbt.getByte(tag, "flags"));
        if (hasCustomName()) {
            bossEvent.setName(getDisplayName());
        }
    }
}
