package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.level.NoteBlockEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's NoteBlockEvent.Play: a note about to sound, cancellable. */
@SuppressWarnings("unused")
@Mixin(NoteBlock.class)
public abstract class WorldEventsMixin {
	@Inject(method = "triggerEvent", at = @At("HEAD"), cancellable = true)
	private void vnap$note(BlockState state, Level level, BlockPos pos, int id, int param, CallbackInfoReturnable<Boolean> info) {
		if (Bridge.fire(new NoteBlockEvent.Play(level, pos, state)).isCanceled()) info.setReturnValue(false);
	}
}
