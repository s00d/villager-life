package com.vnap.fab.mixin.client;

//? if >=26.2 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * A block entity the level draws wherever it is (SectionCompilerMixin) is drawn in that pass and not its section's:
 * the same answer as when it was added, though its box may have grown since.
 ^/
@SuppressWarnings("unused")
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class GlobalBlockEntitiesMixin {
	@WrapOperation(method = "tryExtractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRenderOffScreen()Z"))
	private boolean vnap$global(BlockEntityRenderer<BlockEntity, ?> renderer, Operation<Boolean> vanilla, BlockEntity blockEntity) {
		var level = Minecraft.getInstance().level;
		return vanilla.call(renderer) || (level != null && level.getGloballyRenderedBlockEntities().contains(blockEntity));
	}
}
*///?} else {
/** From 26.2 only (the build leaves this mixin out before). */
public final class GlobalBlockEntitiesMixin {
	private GlobalBlockEntitiesMixin() {
	}
}
//?}
