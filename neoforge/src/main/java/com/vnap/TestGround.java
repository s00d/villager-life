package com.vnap;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * What every game test runs in (GameTestServerMixin on 1.21.1; the test registration from 1.21.5).
 *
 * Every batch is set out on the same ground: the towns an earlier batch's tests left on it (an office with its
 * residents, a road network, houses, things waiting to be set down, a stall) would be picked up by this test as its
 * own (Mayors.officeFor takes any office within SPAN), and it then went one way or the other by which tests ran
 * before. Each test starts with what lies within its own ground cleared.
 *
 * One log line per test, {@code vnap test: <batch> <name> <ticks>t <ms>ms own <ms>ms}: a batch lasts as long as its
 * slowest test; "own" is the test's start alone (its setup, often a whole town planned at once), the rest of a
 * batch's tests wait on the same server thread meanwhile. tools/dev/gts.sh ranks them. A finished test's town goes
 * too: hundreds of them left behind made every look over the offices slower for the tests after.
 */
public final class TestGround {
    private TestGround() {
    }

    public static Consumer<GameTestHelper> onFreshGround(String batch, String name, Consumer<GameTestHelper> test) {
        return helper -> {
            clear(helper.getLevel(), helper.getBounds());
            //? if >=26.3
            /*plains(helper);*/
            long start = System.nanoTime();
            try {
                test.accept(helper);
            } finally {
                timed(helper, batch, name, (System.nanoTime() - start) / 1_000_000L);
            }
        };
    }

    //? if >=26.3 {
    /*// the test world is a sandstone desert from 26.3; the tests are written for the plains (palettes, ground, streams)
    private static void plains(GameTestHelper helper) {
        AABB box = helper.getBounds().inflate(16.0);
        ServerLevel level = helper.getLevel();
        var plains = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        // a chunk's column at a time: the fill keeps under the game's limit of blocks changed at once
        for (int x = (int) Math.floor(box.minX); x <= box.maxX; x += 16) {
            for (int z = (int) Math.floor(box.minZ); z <= box.maxZ; z += 16) {
                net.minecraft.server.commands.FillBiomeCommand.fill(level, net.minecraft.core.BlockPos.containing(x, box.minY, z),
                        net.minecraft.core.BlockPos.containing(Math.min(x + 15, box.maxX), box.maxY, Math.min(z + 15, box.maxZ)), plains);
            }
        }
    }
    *///?}

    private static void timed(GameTestHelper helper, String batch, String name, long own) {
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {
            }

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                clear(helper.getLevel(), helper.getBounds());
                VillagerLife.LOGGER.info("vnap test: {} {} {}t {}ms own {}ms", batch, name, helper.getTick(), info.getRunTime(), own);
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                clear(helper.getLevel(), helper.getBounds());
                VillagerLife.LOGGER.info("vnap test: {} {} {}t {}ms own {}ms FAILED", batch, name, helper.getTick(), info.getRunTime(), own);
            }

            @Override
            public void testAddedForRerun(GameTestInfo old, GameTestInfo fresh, GameTestRunner runner) {
            }
        });
    }

    private static void clear(ServerLevel level, AABB bounds) {
        MinecraftServer server = level.getServer();
        java.util.function.Predicate<net.minecraft.core.BlockPos> inside = pos -> bounds.contains(Vec3.atCenterOf(pos));
        com.vnap.village.home.Mayors.get(server).offices().removeIf(office -> inside.test(office.bell));
        com.vnap.village.town.Roads.get(server).networks().removeIf(network -> inside.test(network.bell));
        // a house by its town's bell, as the offices and roads: a town's house may stand beyond its test's ground, on the
        // ground of the test beside it, whose start or end took it away from under it
        com.vnap.mood.VillageMoodSavedData.get(level).houses().removeIf(house -> inside.test(house.bell));
        // a village's mood and its rite: an earlier test on this ground leaves them under the same meeting point
        com.vnap.mood.VillageMoodSavedData.get(level).all().removeIf(state -> {
            if (!inside.test(state.meetingPos)) return false;
            com.vnap.village.script.VillageScriptDirector.forgetRunning(level, state.villageId);
            return true;
        });
        com.vnap.village.home.Placements.get(server).jobs().removeIf(job -> inside.test(job.pos()));
        com.vnap.village.market.Shops.get(server).shops().removeIf(shop -> inside.test(shop.bell));
    }

    /**
     * Whether {@code vl.tests} ({@code ./gradlew runGameTestServer -Ptests=GenerationsGameTests,homes,anelderpassesawayatnight})
     * lets this test run: a test class of {@code com.vnap.gametest}, a batch or a test name. Without it every test runs.
     */
    public static boolean selected(String batch, String name) {
        String filter = System.getProperty("vl.tests", "").trim();
        if (filter.isEmpty()) return true;
        String test = name.toLowerCase(java.util.Locale.ROOT);
        String group = batch.toLowerCase(java.util.Locale.ROOT);
        for (String token : com.google.common.base.Splitter.on(",").split(filter)) {
            String wanted = token.trim();
            if (wanted.isEmpty()) continue;
            String lower = wanted.toLowerCase(java.util.Locale.ROOT);
            if (lower.equals(test) || lower.equals(group) || group.startsWith(lower + ":") || declares(wanted, test)) return true;
        }
        return false;
    }

    private static boolean declares(String className, String test) {
        try {
            for (java.lang.reflect.Method method : Class.forName("com.vnap.gametest." + className).getDeclaredMethods()) {
                if (method.getName().toLowerCase(java.util.Locale.ROOT).equals(test)) return true;
            }
        } catch (ClassNotFoundException ignored) {
            // not a class: a batch or a test name
        }
        return false;
    }
}
