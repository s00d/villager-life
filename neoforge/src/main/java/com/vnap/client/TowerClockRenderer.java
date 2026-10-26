package com.vnap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vnap.VillagerLife;
import com.vnap.block.clock.TowerClockBlockEntity;
//? if <1.21.9
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * The tower clock's dial, three blocks across, laid over the wall in front of its block, and its two hands. The
 * texture (textures/block/tower_clock_dial.png, 64x64, 16 pixels to a block like the blocks): the dial in the top-left
 * 48x48, the hour hand at x 52-55, y 0-15 and the minute hand at x 58-61, y 0-23, each pointing up, its pivot three
 * pixels above its bottom (the counterweight's stub below).
 */
//? if >=1.21.9 {
/*public final class TowerClockRenderer implements BlockEntityRenderer<TowerClockBlockEntity, Draw.Held<TowerClockBlockEntity>> {
*///?} else
public final class TowerClockRenderer implements BlockEntityRenderer<TowerClockBlockEntity> {
	private static final ResourceLocation TEXTURE = VillagerLife.id("textures/block/tower_clock_dial.png");
	private static final float PX = 1.0F / 64.0F;

	public TowerClockRenderer(BlockEntityRendererProvider.Context context) {
	}

	//? if >=1.21.9 {
	/*@Override
	public Draw.Held<TowerClockBlockEntity> createRenderState() {
		return new Draw.Held<>();
	}

	@Override
	public void extractRenderState(TowerClockBlockEntity clock, Draw.Held<TowerClockBlockEntity> state, float partialTick, net.minecraft.world.phys.Vec3 camera,
			net.minecraft.client.renderer.feature.ModelFeatureRenderer.@org.jspecify.annotations.Nullable CrumblingOverlay breaking) {
		BlockEntityRenderer.super.extractRenderState(clock, state, partialTick, camera, breaking);
		state.be = clock;
	}

	@Override
	public void submit(Draw.Held<TowerClockBlockEntity> state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector nodes,
			net.minecraft.client.renderer.state.CameraRenderState camera) {
		if (state.be != null) render(state.be, pose, new Draw(nodes));
	}
	*///?} else if >=1.21.5 {
	/*@Override
	public void render(TowerClockBlockEntity clock, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, net.minecraft.world.phys.Vec3 camera) {
		render(clock, pose, new Draw(buffers));
	}
	*///?} else {
	@Override
	public void render(TowerClockBlockEntity clock, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		render(clock, pose, new Draw(buffers));
	}
	//?}

	private static void render(TowerClockBlockEntity clock, PoseStack pose, Draw draw) {
		Level level = clock.getLevel();
		if (level == null) return;
		Direction facing = clock.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
		// lit like the air in front of the wall, not the inside of the block
		int front = McClient.light(level, clock.getBlockPos().relative(facing));
		long time = clock.shownTime(com.vnap.Mc.dayTime(level));
		float hours = (com.vnap.dialogue.Family.timeOfDay(time) / 1000.0F + 6.0F) % 12.0F;
		float minutes = hours % 1.0F;
		RenderType type = com.vnap.client.McClient.entityCutout(TEXTURE);
		pose.pushPose();
		pose.translate(0.5, 0.5, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
		// local +Z points out of the wall
		pose.translate(0.0, 0.0, 0.5 + 0.004);
		draw.geometry(pose, type, (at, buffer) -> quad(buffer, at, -1.5F, -1.5F, 1.5F, 1.5F, 0, 0, 48, 48, front));
		hand(draw, type, pose, hours / 12.0F * 360.0F, 52, 16, front, 0.002F);
		hand(draw, type, pose, minutes * 360.0F, 58, 24, front, 0.004F);
		pose.popPose();
	}

	/** A hand from the dial's middle, turned clockwise from twelve by {@code degrees}; its sprite {@code rows} tall. */
	private static void hand(Draw draw, RenderType type, PoseStack pose, float degrees, int u, int rows, int light, float lift) {
		pose.pushPose();
		pose.translate(0.0, 0.0, lift);
		pose.mulPose(Axis.ZP.rotationDegrees(-degrees));
		draw.geometry(pose, type, (at, buffer) -> quad(buffer, at, -2 / 16.0F, -3 / 16.0F, 2 / 16.0F, (rows - 3) / 16.0F, u, 0, u + 4, rows, light));
		pose.popPose();
	}

	/** A quad in the pose's XY plane facing +Z, the texture's top at y1. */
	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float x1, float y1, int u0, int v0, int u1, int v1, int light) {
		Matrix4f m = pose.pose();
		float a = u0 * PX, b = u1 * PX, top = v0 * PX, bottom = v1 * PX;
		vertex(buffer, pose, m, x0, y0, a, bottom, light);
		vertex(buffer, pose, m, x1, y0, b, bottom, light);
		vertex(buffer, pose, m, x1, y1, b, top, light);
		vertex(buffer, pose, m, x0, y1, a, top, light);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f m, float x, float y, float u, float v, int light) {
		buffer.addVertex(m, x, y, 0.0F).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
	}

	//? if !forge {
	@Override
	public AABB getRenderBoundingBox(TowerClockBlockEntity clock) {
		return clock.renderBox();
	}
	//?}

	@Override
	public int getViewDistance() {
		return 128;
	}
}
