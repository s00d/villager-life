package com.vnap.gametest;

import com.vnap.Nbt;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.vnap.block.LifeBlocks;
import com.vnap.item.BlessingStaffItem;
import com.vnap.item.LifeItems;
import com.vnap.mood.MoodFlags;
import com.vnap.mood.MoodState;
import com.vnap.mood.ScriptKind;
import com.vnap.mood.VillageMoodApi;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.mood.VillageRiteActions;
import com.vnap.mood.VillageRites;
import com.vnap.village.script.ActiveScript;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.script.VillageScriptDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class MoodLogicGameTests {
    private static final List<String> SCHEMATICS = List.of(
            "festoon", "vigil", "festival", "mourning",
            "pavilion", "pyre", "tocsin_forge", "monument");

    @GameTest(template = "platform")
    public static void plusHundredStartsTheMonumentAtOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        VillageMoodApi.setExact(level, state, 100.0F, true);
        Checks.assertTrue(helper, state.script == ScriptKind.BUILD_MONUMENT, "+100 starts the monument first, was " + state.script);
        VillageMoodApi.setExact(level, state, 100.0F, true);
        Checks.assertTrue(helper, state.script == ScriptKind.BUILD_MONUMENT, "a busy slot does not start the next rite");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void minusHundredStartsTheForgeAtOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        VillageMoodApi.setExact(level, state, -100.0F, true);
        Checks.assertTrue(helper, state.script == ScriptKind.BUILD_ALTAR, "-100 starts the forge first, was " + state.script);
        Checks.assertTrue(helper, !state.hasAltar(), "the forge flag waits for the consecration");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void positiveBackfillGoesDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        VillageMoodApi.setExact(level, state, 100.0F, true);
        List<ScriptKind> seen = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            seen.add(state.script);
            VillageRites.markDone(state, state.script);
            releaseSlot(helper, state);
            VillageRiteActions.offer(level, state);
        }
        Checks.assertTrue(helper, seen.equals(List.of(ScriptKind.BUILD_MONUMENT, ScriptKind.BUILD_PAVILION,
                ScriptKind.BUILD_FESTIVAL, ScriptKind.BUILD_FESTOON)), "positive order, was " + seen);
        Checks.assertTrue(helper, state.script == ScriptKind.NONE, "nothing is left after the garlands");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void negativeBackfillSummonsRightAfterTheForge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        VillageMoodApi.setExact(level, state, -95.0F, true);
        Checks.assertTrue(helper, state.script == ScriptKind.BUILD_ALTAR, "the forge goes first");
        VillageRites.markDone(state, ScriptKind.BUILD_ALTAR);
        releaseSlot(helper, state);
        VillageRiteActions.offer(level, state);
        Checks.assertTrue(helper, state.script == ScriptKind.SUMMON, "at -95 the summon follows the forge, was " + state.script);
        state.flags = MoodFlags.set(state.flags, MoodFlags.ALTAR_TAINTED);
        List<ScriptKind> seen = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            releaseSlot(helper, state);
            VillageRiteActions.offer(level, state);
            seen.add(state.script);
            VillageRites.markDone(state, state.script);
        }
        Checks.assertTrue(helper, seen.equals(List.of(ScriptKind.BUILD_PYRE, ScriptKind.BUILD_MOURNING, ScriptKind.BUILD_VIGIL)),
                "the lesser rites follow, was " + seen);
        helper.succeed();
    }

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void noWorkersRetriesInThirtySeconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        VillageMoodApi.setExact(level, state, 30.0F, true);
        Checks.assertTrue(helper, state.script == ScriptKind.BUILD_FESTOON, "garlands start");
        state.scriptSite = helper.absolutePos(new BlockPos(2, 150, 2));
        long start = level.getGameTime();
        helper.runAfterDelay(105, () -> {
            Checks.assertTrue(helper, state.script == ScriptKind.NONE, "one villager is too few, the rite stops");
            long wait = state.scriptCooldownUntilTick - level.getGameTime();
            Checks.assertTrue(helper, wait > 0 && wait <= 20L * 30L, "the retry is at most 30 seconds away, was " + wait);
            Checks.assertTrue(helper, "no_workers".equals(VillageScriptDirector.lastReason(helper.getLevel(), state.villageId)),
                    "the reason is kept, was " + VillageScriptDirector.lastReason(helper.getLevel(), state.villageId));
            Checks.assertTrue(helper, state.mood == 30.0F, "an early stop costs no mood");
            Checks.assertTrue(helper, level.getGameTime() - start >= 100L, "the director waited for villagers");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void twoVillagersAreEnough(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        Villager second = helper.spawn(EntityType.VILLAGER, 3, 2, 2);
        second.setNoAi(true);
        VillageMoodApi.setExact(level, state, 30.0F, true);
        helper.runAfterDelay(25, () -> {
            ActiveScript script = VillageScriptDirector.getActive(helper.getLevel(), state.villageId);
            Checks.assertTrue(helper, script != null && script.workers.size() >= 2,
                    "two villagers start the garlands, phase " + (script == null ? "none" : script.phase));
            VillageScriptDirector.abort(level, state, false);
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void savedScriptResumesWithoutTheRunningOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        state.mood = 30.0F;
        state.script = ScriptKind.BUILD_FESTOON;
        state.scriptSite = helper.absolutePos(new BlockPos(0, 1, 0));
        dropRunning(helper, state);
        helper.runAfterDelay(3, () -> {
            ActiveScript script = VillageScriptDirector.getActive(helper.getLevel(), state.villageId);
            if (script == null) {
                Checks.fail(helper, "the saved rite picks up after a reload");
                return;
            }
            Checks.assertTrue(helper, script.kind == ScriptKind.BUILD_FESTOON, "the same rite resumes");
            Checks.assertTrue(helper, script.siteOrigin.equals(state.scriptSite), "it keeps the same site");
            VillageScriptDirector.abort(level, state, false);
            helper.succeed();
        });
    }

    @GameTest(template = "platform", timeoutTicks = 800)
    public static void bossSlotWithoutABossClears(GameTestHelper helper) {
        MoodState state = village(helper);
        state.mood = -95.0F;
        state.script = ScriptKind.BOSS_ACTIVE;
        state.flags = MoodFlags.HAS_ALTAR | MoodFlags.ALTAR_TAINTED;
        state.scriptCooldownUntilTick = Long.MAX_VALUE;
        dropRunning(helper, state);
        helper.runAfterDelay(620, () -> {
            Checks.assertTrue(helper, state.script == ScriptKind.NONE, "a missing boss frees the slot, was " + state.script);
            Checks.assertTrue(helper, !state.altarTainted(), "the forge can be used again");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void secondAltarIsNotStarted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        VillageMoodApi.setExact(level, state, -60.0F, true);
        Checks.assertTrue(helper, state.script == ScriptKind.BUILD_ALTAR, "the first altar starts");
        VillageRites.markDone(state, ScriptKind.BUILD_ALTAR);
        releaseSlot(helper, state);
        VillageRiteActions.offer(level, state);
        Checks.assertTrue(helper, state.script != ScriptKind.BUILD_ALTAR, "a second altar does not start while the flag is set");
        releaseSlot(helper, state);
        state.flags = MoodFlags.MOURNING | MoodFlags.VIGIL;
        helper.setBlock(new BlockPos(2, 1, 2), LifeBlocks.ALTAR_CORE.defaultBlockState());
        VillageRiteActions.offer(level, state);
        Checks.assertTrue(helper, state.script == ScriptKind.NONE, "an existing core does not start another altar");
        Checks.assertTrue(helper, state.hasAltar(), "the existing core is claimed");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void bossDeathSetsMoodToZero(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        state.mood = -40.0F;
        long before = level.getGameTime();
        VillageScriptDirector.onBossSlain(level, state.villageId);
        Checks.assertTrue(helper, state.mood == 0.0F, "boss death sets mood to 0");
        Checks.assertTrue(helper, state.script == ScriptKind.NONE, "boss death does not start a build");
        Checks.assertTrue(helper, state.scarred() && state.bossSlainOnce(), "scar and the slain flag stay");
        Checks.assertTrue(helper, state.scriptCooldownUntilTick > before, "the summon cooldown stays");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void peakCoreBreakSetsMinus100(GameTestHelper helper) {
        MoodState state = village(helper);
        state.mood = 95.0F;
        state.flags = MoodFlags.set(state.flags, MoodFlags.HAS_ALTAR);
        BlockPos core = new BlockPos(2, 1, 2);
        helper.setBlock(core, LifeBlocks.ALTAR_CORE.defaultBlockState());
        breakBlock(helper, core);
        Checks.assertTrue(helper, state.mood == -100.0F, "breaking the core at a feast sets -100, was " + state.mood);
        Checks.assertTrue(helper, !state.hasAltar(), "breaking the core clears the altar flag");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void baseBreakDoesNotClearTheAltar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        VillageMoodApi.setExact(level, state, 95.0F, false);
        state.flags = MoodFlags.set(state.flags, MoodFlags.HAS_ALTAR);
        BlockPos base = new BlockPos(1, 1, 2);
        helper.setBlock(base, LifeBlocks.ALTAR_BASE.defaultBlockState());
        breakBlock(helper, base);
        Checks.assertTrue(helper, state.hasAltar(), "breaking the base keeps the altar flag");
        Checks.assertTrue(helper, state.mood == 90.0F, "breaking the base only costs 5, was " + state.mood);
        BlockPos pillar = new BlockPos(3, 1, 2);
        helper.setBlock(pillar, LifeBlocks.ALTAR_PILLAR.defaultBlockState());
        breakBlock(helper, pillar);
        Checks.assertTrue(helper, state.hasAltar(), "breaking a pillar keeps the altar flag");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void ephemeralFlagsClearNearZero(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        state.flags = MoodFlags.FESTOON | MoodFlags.VIGIL | MoodFlags.FESTIVAL
                | MoodFlags.HAS_MONUMENT | MoodFlags.HAS_ALTAR;
        state.mood = 40.0F;
        VillageMoodApi.setExact(level, state, 0.0F, false);
        Checks.assertTrue(helper, !MoodFlags.has(state.flags, MoodFlags.EPHEMERAL), "garlands and candles clear near zero");
        Checks.assertTrue(helper, MoodFlags.has(state.flags, MoodFlags.FESTIVAL), "the festival totem stays");
        Checks.assertTrue(helper, state.hasMonument() && state.hasAltar(), "monument and altar stay");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void staffMovesTenAndStopsAtTheRail(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MoodState state = village(helper);
        ServerPlayer player = player(helper);
        ItemStack stack = new ItemStack(LifeItems.BLESSING_STAFF);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlessingStaffItem staff = (BlessingStaffItem) LifeItems.BLESSING_STAFF;
        int full = staff.getUseDuration(stack, player);
        int charged = full - BlessingStaffItem.CHARGE_TICKS;
        staff.releaseUsing(stack, level, player, full - 5);
        Checks.assertTrue(helper, state.mood == 0.0F, "a quick tap does not toll");
        staff.releaseUsing(stack, level, player, charged);
        Checks.assertTrue(helper, state.mood == 10.0F, "a charged toll is +10, was " + state.mood);
        Checks.assertTrue(helper, com.vnap.Mc.onCooldown(player, new ItemStack(staff)), "the staff cools down after a toll");
        VillageMoodApi.setExact(level, state, 100.0F, false);
        com.vnap.Mc.clearCooldown(player, staff);
        staff.releaseUsing(stack, level, player, charged);
        Checks.assertTrue(helper, state.mood == 100.0F, "mood stays on the rail");
        VillageMoodApi.setExact(level, state, 0.0F, false);
        com.vnap.Mc.clearCooldown(player, staff);
        player.setShiftKeyDown(true);
        staff.releaseUsing(stack, level, player, charged);
        Checks.assertTrue(helper, state.mood == -10.0F, "a sneaking toll curses by 10, was " + state.mood);
        VillageMoodApi.setExact(level, state, -100.0F, false);
        com.vnap.Mc.clearCooldown(player, staff);
        staff.releaseUsing(stack, level, player, charged);
        Checks.assertTrue(helper, state.mood == -100.0F, "the low rail holds");
        helper.succeed();
    }

	/** The mood is the village's opinion of the player: with nobody doing anything it stays where it is. */
	@GameTest(template = "platform", timeoutTicks = 1600)
	public static void moodStaysPutWithoutThePlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoodState state = village(helper);
		VillageMoodApi.setExact(level, state, 15.0F, false);
		state.standing = 40.0F;
		// longer than a minute: the old drift pulled 3 a minute toward zero
		helper.runAfterDelay(1300, () -> {
			Checks.assertTrue(helper, state.mood == 15.0F, "mood does not drift by itself, was " + state.mood);
			Checks.assertTrue(helper, state.standing == 40.0F, "standing does not drift by itself, was " + state.standing);
			helper.succeed();
		});
	}

	@GameTest(template = "platform")
	public static void aBlockPutBackReturnsWhatBreakingItCost(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoodState state = village(helper);
		VillageMoodApi.setExact(level, state, 0.0F, false);
		state.standing = 0.0F;
		ServerPlayer breaker = player(helper);
		BlockPos base = new BlockPos(1, 1, 2);
		helper.setBlock(base, LifeBlocks.ALTAR_BASE.defaultBlockState());
		breakBlock(helper, base, breaker);
		Checks.assertTrue(helper, state.mood == -5.0F && state.standing == -10.0F,
				"breaking the base costs 5 mood and 10 standing, was " + state.mood + " / " + state.standing);
		place(helper, base, Blocks.STONE, breaker);
		Checks.assertTrue(helper, state.mood == -5.0F, "another block in its place returns nothing");
		helper.setBlock(base, Blocks.AIR);
		place(helper, base, LifeBlocks.ALTAR_BASE, player(helper));
		Checks.assertTrue(helper, state.mood == -5.0F, "another player's block returns nothing");
		helper.setBlock(base, Blocks.AIR);
		place(helper, base, LifeBlocks.ALTAR_BASE, breaker);
		Checks.assertTrue(helper, state.mood == 0.0F && state.standing == 0.0F,
				"the same block back returns it all, was " + state.mood + " / " + state.standing);
		helper.setBlock(base, Blocks.AIR);
		place(helper, base, LifeBlocks.ALTAR_BASE, breaker);
		Checks.assertTrue(helper, state.mood == 0.0F, "it is returned once");
		helper.succeed();
	}

    @GameTest(template = "platform")
    public static void moodStateSaveKeepsFields(GameTestHelper helper) {
        BlockPos meeting = helper.absolutePos(new BlockPos(2, 1, 2));
        MoodState state = new MoodState(7L, meeting);
        state.mood = 33.0F;
        state.flags = MoodFlags.HAS_ALTAR | MoodFlags.FESTIVAL;
        state.altarPos = meeting.offset(1, 0, 0);
        state.monumentPos = meeting.offset(0, 0, 1);
        state.script = ScriptKind.BUILD_VIGIL;
        MoodState loaded = MoodState.load(state.save());
        Checks.assertTrue(helper, loaded.mood == 33.0F, "mood survives save");
        Checks.assertTrue(helper, loaded.flags == state.flags, "flags survive save");
        Checks.assertTrue(helper, meeting.offset(1, 0, 0).equals(loaded.altarPos), "the altar position survives save");
        Checks.assertTrue(helper, meeting.offset(0, 0, 1).equals(loaded.monumentPos), "the monument position survives save");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void unknownScriptIdReadsAsNone(GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", 1L);
        tag.put("meeting", writePos(helper.absolutePos(BlockPos.ZERO)));
        tag.putString("script", "NOT_A_RITE");
        MoodState loaded = MoodState.load(tag);
        Checks.assertTrue(helper, loaded.script == ScriptKind.NONE, "an unknown script id reads as NONE");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void schematicsLoadAndBlocksExist(GameTestHelper helper) throws IOException {
        var manager = helper.getLevel().getServer().getResourceManager();
        for (String name : SCHEMATICS) {
            SchematicSpec spec = SchematicSpec.load(manager, name);
            Checks.assertTrue(helper, !spec.blocks.isEmpty(), name + " loads blocks");
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
                    "villager_life", SchematicSpec.STRUCTURES + name + ".nbt");
            try (var stream = manager.open(file)) {
                var palette = Nbt.getList(com.vnap.village.script.SchematicSpec.upToDate(net.minecraft.nbt.NbtIo.readCompressed(stream, net.minecraft.nbt.NbtAccounter.unlimitedHeap())), "palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
                for (int i = 0; i < palette.size(); i++) {
                    var entry = Nbt.getCompound(palette, i);
                    boolean known = !net.minecraft.nbt.NbtUtils.readBlockState(com.vnap.Mc.blocks(), entry).isAir() || entry.toString().contains(":air");
                    Checks.assertTrue(helper, known, entry + " is in the registry");
                }
            }
        }
        helper.succeed();
    }

    private static MoodState village(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos meeting = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager villager = helper.spawn(EntityType.VILLAGER, 2, 2, 2);
        villager.setNoAi(true);
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT,
                GlobalPos.of(level.dimension(), meeting));
        Checks.assertTrue(helper, !level.getEntitiesOfClass(Villager.class, new AABB(meeting).inflate(8.0), Villager::isAlive).isEmpty(),
                "spawned villager is queryable");
        return VillageMoodSavedData.get(level).getOrCreate(meeting.asLong(), meeting);
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "tester"), ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(level.getServer(),
                new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))));
        return player;
    }

    private static void breakBlock(GameTestHelper helper, BlockPos relative) {
        breakBlock(helper, relative, player(helper));
    }

    private static void breakBlock(GameTestHelper helper, BlockPos relative, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relative);
        var event = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) {
            level.destroyBlock(pos, false, player);
        }
    }

    /** The player sets the block down: the block is in place when the event comes, as in the game. */
    private static void place(GameTestHelper helper, BlockPos relative, Block block, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relative);
        helper.setBlock(relative, block);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, pos),
                Blocks.STONE.defaultBlockState(), player));
    }

    private static void releaseSlot(GameTestHelper helper, MoodState state) {
        VillageScriptDirector.forgetRunning(helper.getLevel(), state.villageId);
        state.script = ScriptKind.NONE;
        state.scriptCooldownUntilTick = 0L;
    }

    private static void dropRunning(GameTestHelper helper, MoodState state) {
        VillageScriptDirector.forgetRunning(helper.getLevel(), state.villageId);
    }

    private static net.minecraft.nbt.Tag writePos(BlockPos pos) {
        return com.vnap.Mc.posTag(pos);
    }
}
