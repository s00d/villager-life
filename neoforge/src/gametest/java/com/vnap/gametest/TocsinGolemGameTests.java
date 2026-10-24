package com.vnap.gametest;

import com.mojang.authlib.GameProfile;
import com.vnap.block.AltarBlockEntity;
import com.vnap.block.AltarState;
import com.vnap.block.LifeBlocks;
import com.vnap.compat.RiteCatalog;
import com.vnap.entity.VillageEntities;
import com.vnap.entity.golem.GolemAction;
import com.vnap.entity.golem.TocsinGolem;
import com.vnap.item.LifeItems;
import com.vnap.mood.MoodFlags;
import com.vnap.mood.MoodState;
import com.vnap.mood.ScriptKind;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.mood.VillageRites;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.script.VillageScriptDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class TocsinGolemGameTests {
    private static final int AWAKE = GolemAction.AWAKEN.length + 5;

    @GameTest(batch = "tocsin", template = "platform", timeoutTicks = 200)
    public static void awakeningShieldsThenPhaseOneStopsAtTwoThirds(GameTestHelper helper) {
        TocsinGolem golem = golem(helper);
        ServerPlayer player = player(helper);
        float full = golem.getHealth();
        Checks.assertTrue(helper, !com.vnap.Mc.hurt(golem, helper.getLevel().damageSources().playerAttack(player), 50.0F),
                "the golem cannot be hurt while it wakes");
        helper.runAfterDelay(AWAKE, () -> {
            Checks.assertTrue(helper, golem.phase() == 1, "after waking the golem is in phase one, was " + golem.phase());
            com.vnap.Mc.invulnerableTime(golem, 0);
            com.vnap.Mc.hurt(golem, helper.getLevel().damageSources().playerAttack(player), 10_000.0F);
            float floor = full * 0.66F;
            Checks.assertTrue(helper, golem.getHealth() >= floor - 0.01F && golem.getHealth() < full,
                    "a huge hit stops at the phase gate " + floor + ", health " + golem.getHealth());
            Checks.assertTrue(helper, golem.isAlive(), "the gate keeps the golem alive");
            golem.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "tocsin", template = "platform", timeoutTicks = 200)
    public static void villagersNeitherHurtNorGetHurt(GameTestHelper helper) {
        TocsinGolem golem = golem(helper);
        Villager villager = helper.spawn(EntityType.VILLAGER, 3, 2, 3);
        villager.setNoAi(true);
        helper.runAfterDelay(AWAKE, () -> {
            float before = golem.getHealth();
            com.vnap.Mc.invulnerableTime(golem, 0);
            Checks.assertTrue(helper, !com.vnap.Mc.hurt(golem, helper.getLevel().damageSources().mobAttack(villager), 20.0F),
                    "a villager cannot hurt the golem");
            Checks.assertTrue(helper, golem.getHealth() == before, "golem health is untouched");
            float villagerHealth = villager.getHealth();
            golem.hurtAround(villager.position(), 3.0, 20.0F, 0.5);
            Checks.assertTrue(helper, villager.getHealth() == villagerHealth, "the golem's area hit spares villagers");
            golem.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "tocsin", template = "platform", timeoutTicks = 200)
    public static void villageBellStunsAndStaffOnlyCountersItsToll(GameTestHelper helper) {
        TocsinGolem golem = golem(helper);
        ServerPlayer player = player(helper);
        helper.runAfterDelay(AWAKE, () -> {
            if (golem.action() != GolemAction.BELL_TOLL) {
                Checks.assertTrue(helper, !golem.counterToll(), "a staff toll does nothing outside the golem's own toll");
            }
            TocsinGolem.onBellRung(helper.getLevel(), golem.blockPosition(), player);
            Checks.assertTrue(helper, golem.action() == GolemAction.STUN, "a rung village bell stuns the golem, was " + golem.action());
            golem.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "tocsin", template = "platform")
    public static void leavingPutsTheForgeBackToReady(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        BlockPos core = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(core, LifeBlocks.ALTAR_CORE.defaultBlockState(), Block.UPDATE_ALL);
        state.altarPos = core;
        state.flags = MoodFlags.set(MoodFlags.set(state.flags, MoodFlags.HAS_ALTAR), MoodFlags.ALTAR_TAINTED);
        state.script = ScriptKind.BOSS_ACTIVE;
        VillageScriptDirector.onBossLeft(level, state.villageId);
        Checks.assertTrue(helper, state.script == ScriptKind.NONE, "the boss slot is free again");
        Checks.assertTrue(helper, !state.altarTainted(), "the forge is no longer tainted");
        Checks.assertTrue(helper, state.hasAltar(), "the forge itself stays");
        Checks.assertTrue(helper, level.getBlockEntity(core) instanceof AltarBlockEntity altar && altar.altarState() == AltarState.READY,
                "the forge core is ready to summon again");
        helper.succeed();
    }

    @GameTest(batch = "tocsin", template = "platform")
    public static void lootTableGivesEachFighterACore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TocsinGolem golem = golem(helper);
        ServerPlayer player = player(helper);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(com.vnap.Mc.lootTable(golem));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, golem)
                .withParameter(LootContextParams.ORIGIN, golem.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .create(LootContextParamSets.ENTITY);
        List<ItemStack> drops = new ArrayList<>();
        table.getRandomItems(params, drops::add);
        Checks.assertTrue(helper, drops.stream().anyMatch(s -> s.is(LifeItems.TOCSIN_CORE)), "a roll always gives a tocsin core: " + drops);
        Checks.assertTrue(helper, drops.stream().anyMatch(s -> s.is(net.minecraft.world.item.Items.IRON_INGOT)), "a roll gives iron: " + drops);
        golem.discard();
        helper.succeed();
    }

    @GameTest(batch = "tocsin", template = "platform")
    public static void bundledSchematicsMatchTheDataPack(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getResourceManager();
        for (ScriptKind kind : VillageRites.BUILDS) {
            String name = VillageRites.schematicName(kind);
            SchematicSpec bundled = SchematicSpec.loadBundled(name);
            SchematicSpec served = SchematicSpec.load(manager, name);
            Checks.assertTrue(helper, !bundled.blocks.isEmpty() && bundled.blocks.size() == served.blocks.size(),
                    name + " reads the same from the jar as from the data pack");
        }
        Checks.assertTrue(helper, RiteCatalog.rites().size() == VillageRites.BUILDS.size(), "every rite has a catalog entry");
        Checks.assertTrue(helper, RiteCatalog.rites().stream().allMatch(r -> !r.materials().isEmpty()), "every rite lists materials");
        helper.succeed();
    }

    @GameTest(batch = "tocsin", template = "platform", timeoutTicks = 100)
    public static void everySchematicPlacesWhole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 12, 0));
        for (ScriptKind kind : VillageRites.BUILDS) {
            SchematicSpec spec = SchematicSpec.loadBundled(VillageRites.schematicName(kind));
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(0, -1, 0), origin.offset(spec.sizeX, -1, spec.sizeZ))) {
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            for (SchematicSpec.PlacedBlock placed : spec.blocks) {
                BlockPos at = origin.offset(placed.rel());
                level.setBlock(at, Block.updateFromNeighbourShapes(placed.state(), level, at), Block.UPDATE_CLIENTS);
            }
            int missing = 0;
            for (SchematicSpec.PlacedBlock placed : spec.blocks) {
                if (!level.getBlockState(origin.offset(placed.rel())).is(placed.state().getBlock())) {
                    missing++;
                }
            }
            List<String> lost = new ArrayList<>();
            for (SchematicSpec.PlacedBlock placed : spec.blocks) {
                var now = level.getBlockState(origin.offset(placed.rel()));
                if (!now.is(placed.state().getBlock())) {
                    lost.add(placed.rel().toShortString() + " " + placed.state().getBlock() + " -> " + now.getBlock());
                }
            }
            Checks.assertTrue(helper, missing == 0, kind + " leaves " + missing + " blocks unplaced: " + lost);
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(0, -1, 0), origin.offset(spec.sizeX, spec.sizeY, spec.sizeZ))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        helper.succeed();
    }

    private static TocsinGolem golem(GameTestHelper helper) {
        TocsinGolem golem = helper.spawn(VillageEntities.TOCSIN_GOLEM, new Vec3(2.5, 2.0, 2.5));
        golem.bind(Long.MIN_VALUE, helper.absolutePos(new BlockPos(2, 1, 2)));
        return golem;
    }

    private static MoodState village(GameTestHelper helper) {
        BlockPos meeting = helper.absolutePos(new BlockPos(2, 1, 2));
        return VillageMoodSavedData.get(helper.getLevel()).getOrCreate(meeting.asLong(), meeting);
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "tester"), ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(level.getServer(),
                new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 0))));
        return player;
    }
}
