package com.vnap.mixin.client;

import com.vnap.client.DialogueSoundState;
import com.vnap.entity.VillagerLifeData;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The vanilla villager model, posed a little after vanilla: a speaking villager nods along with its line, and a woman
 * has a smaller nose. The model is shared by every villager, so the nose is set back for everyone else each time.
 */
@Mixin(VillagerModel.class)
@SuppressWarnings("unused")
public abstract class VillagerModelMixin {
	//? if >=1.21.2 {
	/*@Inject(method = "setupAnim", at = @At("TAIL"))
	private void vnap$pose(net.minecraft.client.renderer.entity.state.VillagerRenderState state, CallbackInfo ci) {
		vnap$pose(com.vnap.client.RenderedEntity.of(state, Villager.class), state.ageInTicks);
	}
	*///?} else {
	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void vnap$pose(net.minecraft.world.entity.Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch, CallbackInfo ci) {
		vnap$pose(entity instanceof Villager villager ? villager : null, ageInTicks);
	}
	//?}

	@SuppressFBWarnings(
			value = "BC_IMPOSSIBLE_CAST",
			justification = "Mixin merges this class into VillagerModel. SpotBugs cannot see that merge.")
	private void vnap$pose(@Nullable Villager villager, float age) {
		ModelPart head = ((HeadedModel) (Object) this).getHead();
		ModelPart nose = head.getChild("nose");
		boolean woman = villager instanceof VillagerLifeData data && !villager.isBaby() && data.vnap$female();
		nose.xScale = woman ? 0.75F : 1.0F;
		nose.yScale = woman ? 0.85F : 1.0F;
		nose.zScale = woman ? 0.6F : 1.0F;
		// the box sits 4 to 6 pixels in front of the pivot: a shorter nose moves out to stay on the face
		nose.z = woman ? -1.6F : 0.0F;
		if (villager != null && DialogueSoundState.speaking(villager.getUUID())) {
			head.xRot += 0.07F * Mth.sin(age * 0.6F);
			head.zRot += 0.04F * Mth.sin(age * 0.23F);
		}
	}
}
