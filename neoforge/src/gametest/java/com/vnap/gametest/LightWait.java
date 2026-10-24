package com.vnap.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Waits for the light of blocks just set: the light engine works them out on its own thread, and after a quick run of
 * tests it is far behind, so a fixed wait read lit rooms as dark now and then. The engine says when a chunk's light is
 * done (the same wait the game makes before it sends a chunk to a player).
 */
final class LightWait {
	private LightWait() {
	}

	static void after(GameTestHelper helper, ServerLevel level, BoundingBox box, Runnable then) {
		List<CompletableFuture<?>> pending = new ArrayList<>();
		for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
			for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
				pending.add(level.getChunkSource().getLightEngine().waitForPendingTasks(x, z));
			}
		}
		CompletableFuture<Void> all = CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
		poll(helper, all, then);
	}

	// A test server with nothing to wait for ticks as fast as it can (1600 ticks a second with every test on one), while
	// the light thread keeps its own pace: a tick spent waiting gives that thread a moment, or the test times out in ticks.
	private static void poll(GameTestHelper helper, CompletableFuture<Void> all, Runnable then) {
		helper.runAfterDelay(1, () -> {
			if (all.isDone()) {
				then.run();
				return;
			}
			try {
				all.get(5, java.util.concurrent.TimeUnit.MILLISECONDS);
			} catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException ignored) {
				// still working (or failed: the check that follows reads what light there is)
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			poll(helper, all, then);
		});
	}
}
