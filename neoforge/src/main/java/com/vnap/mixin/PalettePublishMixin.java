package com.vnap.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.MissingPaletteEntryException;
import org.spongepowered.asm.mixin.Mixin;

import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.LockSupport;

/**
 * The light engine reads a chunk's blocks on its own thread while the server thread sets them. A section's palette
 * gains a new block in place (the value, then its size, then the block's index in the storage), all plain writes:
 * on a weakly ordered CPU (Apple's) the light thread may see the index before the size and the game crashes with
 * "Missing Palette entry". A town's builders and a Spire floor (24000 blocks a tick) make it likely. The index is
 * read again once the writes are seen; a palette that truly lacks it still throws.
 */
@Mixin({LinearPalette.class, HashMapPalette.class})
public abstract class PalettePublishMixin {
	private static final int TRIES = 64;

	@WrapMethod(method = "valueFor")
	private Object vnap$published(int id, Operation<Object> original) {
		for (int i = 0;; i++) {
			try {
				return original.call(id);
			} catch (MissingPaletteEntryException missing) {
				if (i >= TRIES) throw missing;
				VarHandle.fullFence();
				if (i < 8) Thread.onSpinWait();
				else LockSupport.parkNanos(20_000L);
			}
		}
	}
}
