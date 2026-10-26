package com.vnap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vnap.mega.MegaBlocks;
import com.vnap.mega.MegaControllerBlockEntity;
import com.vnap.mega.MegaKind;
//? if <1.21.9
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A megastructure's moving parts. The Quarry: a shaft of the Builders' frame from its bed down to the layer it digs,
 * and at its foot the drill head (a Colosseum part, its crown) turning fast while it digs, slowly when it waits. A
 * Waygate: its keystone, turning and bobbing over the controller, once it has one.
 */
//? if >=1.21.9 {
/*public final class MegaControllerRenderer implements BlockEntityRenderer<MegaControllerBlockEntity, Draw.Held<MegaControllerBlockEntity>> {
*///?} else
public final class MegaControllerRenderer implements BlockEntityRenderer<MegaControllerBlockEntity> {
	/** Shaft pieces drawn at most (deeper, the head is still drawn at its layer). */
	private static final int SHAFT = 48;

	public MegaControllerRenderer(BlockEntityRendererProvider.Context context) {
	}

	//? if >=1.21.9 {
	/*@Override
	public Draw.Held<MegaControllerBlockEntity> createRenderState() {
		return new Draw.Held<>();
	}

	@Override
	public void extractRenderState(MegaControllerBlockEntity controller, Draw.Held<MegaControllerBlockEntity> state, float partialTick, net.minecraft.world.phys.Vec3 camera,
			net.minecraft.client.renderer.feature.ModelFeatureRenderer.@org.jspecify.annotations.Nullable CrumblingOverlay breaking) {
		BlockEntityRenderer.super.extractRenderState(controller, state, partialTick, camera, breaking);
		state.be = controller;
	}

	@Override
	public void submit(Draw.Held<MegaControllerBlockEntity> state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector nodes,
			net.minecraft.client.renderer.state.CameraRenderState camera) {
		if (state.be != null) render(state.be, 0.0F, pose, new Draw(nodes));
	}
	*///?} else if >=1.21.5 {
	/*@Override
	public void render(MegaControllerBlockEntity controller, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, net.minecraft.world.phys.Vec3 camera) {
		render(controller, partialTick, pose, new Draw(buffers));
	}
	*///?} else {
	@Override
	public void render(MegaControllerBlockEntity controller, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		render(controller, partialTick, pose, new Draw(buffers));
	}
	//?}

	private static void render(MegaControllerBlockEntity controller, float partialTick, PoseStack pose, Draw draw) {
		Level level = controller.getLevel();
		if (level == null || !controller.formed()) return;
		float time = level.getGameTime() + partialTick;
		BlockPos at = controller.getBlockPos();
		if (controller.kind() == MegaKind.QUARRY) {
			BlockPos centre = controller.digCentre();
			int depth = at.getY() - controller.digLayer();
			float spin = time * (controller.digging() ? 24.0F : 2.0F);
			ItemStack shaft = new ItemStack(MegaBlocks.FRAME);
			pose.pushPose();
			pose.translate(centre.getX() - at.getX() + 0.5, 0.0, centre.getZ() - at.getZ() + 0.5);
			for (int i = 1; i < Math.min(depth, SHAFT); i++) {
				pose.pushPose();
				pose.translate(0.0, 0.5 - i, 0.0);
				pose.mulPose(Axis.YP.rotationDegrees(spin * 0.5F));
				pose.scale(0.45F, 1.0F, 0.45F);
				draw.item(shaft, ItemDisplayContext.NONE, McClient.light(level, at.below(i).offset(centre.getX() - at.getX(), 0, centre.getZ() - at.getZ())),
						OverlayTexture.NO_OVERLAY, pose, level, 0);
				pose.popPose();
			}
			// the head: a crown of three turning, a little apart, at the layer
			pose.translate(0.0, 0.5 - depth, 0.0);
			pose.mulPose(Axis.YP.rotationDegrees(spin));
			int light = McClient.light(level, centre.atY(controller.digLayer() + 1));
			for (int i = 0; i < 3; i++) {
				pose.pushPose();
				pose.mulPose(Axis.YP.rotationDegrees(i * 120.0F));
				pose.translate(0.35, 0.0, 0.0);
				pose.mulPose(Axis.ZP.rotationDegrees(25.0F));
				pose.scale(0.6F, 0.9F, 0.6F);
				draw.item(new ItemStack(MegaBlocks.PART_COLOSSEUM), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, level, 0);
				pose.popPose();
			}
			pose.popPose();
		} else {
			DyeColor key = controller.key();
			if (key == null) return;
			pose.pushPose();
			pose.translate(0.5, 1.25 + Math.sin(time * 0.08) * 0.06, 0.5);
			pose.mulPose(Axis.YP.rotationDegrees(time * 3.0F));
			pose.scale(0.6F, 0.6F, 0.6F);
			draw.item(new ItemStack(MegaBlocks.key(key)), ItemDisplayContext.FIXED, McClient.light(level, at.above()), OverlayTexture.NO_OVERLAY, pose, level, 0);
			pose.popPose();
		}
	}

	//? if !forge {
	@Override
	public AABB getRenderBoundingBox(MegaControllerBlockEntity controller) {
		return controller.renderBox();
	}
	//?}

	@Override
	public int getViewDistance() {
		return 96;
	}
}
