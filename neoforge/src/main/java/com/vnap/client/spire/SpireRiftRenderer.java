package com.vnap.client.spire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vnap.VillagerLife;
import com.vnap.client.Draw;
import com.vnap.client.McClient;
import com.vnap.spire.SpireBlocks;
import com.vnap.spire.SpireRiftBlock;
//? if <1.21.9
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * The Core's rift over a spire's cut: a sheet of the violet deep laid across the top in the world's own grid (so the
 * blocks of it read as one surface), wisps and sparks of light drifting over it at their own speeds, the whole of it
 * breathing slowly; where the sheet ends, a fringe of light rises off the cut edge; out of its heart, a beam of light
 * stands into the sky. Seen from the whole town.
 */
//? if >=1.21.9 {
/*public final class SpireRiftRenderer implements BlockEntityRenderer<TheEndPortalBlockEntity, Draw.Held<TheEndPortalBlockEntity>> {
*///?} else
public final class SpireRiftRenderer implements BlockEntityRenderer<TheEndPortalBlockEntity> {
	private static final ResourceLocation VOID = VillagerLife.id("textures/block/spire/rift_void.png");
	private static final ResourceLocation SWIRL = VillagerLife.id("textures/block/spire/rift_swirl.png");
	private static final ResourceLocation MOTES = VillagerLife.id("textures/block/spire/rift_motes.png");
	private static final ResourceLocation EDGE = VillagerLife.id("textures/block/spire/rift_edge.png");
	private static final ResourceLocation BEAM = VillagerLife.id("textures/block/spire/rift_beam.png");
	/** How high the beam out of the rift's heart stands. */
	private static final float BEAM_HEIGHT = 64.0F;
	private static final int FULL_BRIGHT = 0xF000F0;
	/** The textures tile every this many blocks. */
	private static final float TILE = 4.0F;
	private static final float TOP = 0.62F;
	private static final float UNDER = 0.48F;

	public SpireRiftRenderer(BlockEntityRendererProvider.Context context) {
	}

	//? if >=1.21.9 {
	/*@Override
	public Draw.Held<TheEndPortalBlockEntity> createRenderState() {
		return new Draw.Held<>();
	}

	@Override
	public void extractRenderState(TheEndPortalBlockEntity rift, Draw.Held<TheEndPortalBlockEntity> state, float partialTick, net.minecraft.world.phys.Vec3 camera,
			net.minecraft.client.renderer.feature.ModelFeatureRenderer.@org.jspecify.annotations.Nullable CrumblingOverlay breaking) {
		BlockEntityRenderer.super.extractRenderState(rift, state, partialTick, camera, breaking);
		state.be = rift;
		state.partialTick = partialTick;
	}

	@Override
	public void submit(Draw.Held<TheEndPortalBlockEntity> state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector nodes,
			net.minecraft.client.renderer.state.CameraRenderState camera) {
		if (state.be != null) render(state.be, state.partialTick, pose, new Draw(nodes));
	}
	*///?} else if >=1.21.5 {
	/*@Override
	public void render(TheEndPortalBlockEntity rift, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, net.minecraft.world.phys.Vec3 camera) {
		render(rift, partialTick, pose, new Draw(buffers));
	}
	*///?} else {
	@Override
	public void render(TheEndPortalBlockEntity rift, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		render(rift, partialTick, pose, new Draw(buffers));
	}
	//?}

	private static void render(TheEndPortalBlockEntity rift, float partialTick, PoseStack pose, Draw draw) {
		Level level = rift.getLevel();
		if (level == null) return;
		BlockPos at = rift.getBlockPos();
		float t = (level.getGameTime() % 240000L) + partialTick;
		float breath = 0.75F + 0.25F * (float) Math.sin(t * 0.035 + (at.getX() + at.getZ()) * 0.15);
		int glow = (int) (255 * breath);
		// the deep, drifting slowly; then wisps one way and sparks the other, added on as light
		layer(draw, pose, McClient.entityCutout(VOID), at, t * 0.0006F, t * 0.0002F, 1.0F, 255, false);
		layer(draw, pose, McClient.eyes(SWIRL), at, -t * 0.0011F, t * 0.0007F, 0.75F, glow, true);
		layer(draw, pose, McClient.eyes(MOTES), at, t * 0.0004F, -t * 0.0016F, 1.6F, 255, true);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(at.relative(side)).is(SpireBlocks.RIFT)) continue;
			edge(draw, pose, side, at, t, glow);
		}
		if (rift.getBlockState().hasProperty(SpireRiftBlock.HEART) && rift.getBlockState().getValue(SpireRiftBlock.HEART)) {
			// a narrow bright core turning one way, a wide faint sheath the other, both fading out as they rise
			beam(draw, pose, t, t * 0.6F, 0.3F, glow);
			beam(draw, pose, t, -t * 0.35F, 0.8F, glow / 2);
		}
	}

	/** A square column of light standing on the heart, turned by {@code degrees}, {@code half} wide each way of its axis. */
	private static void beam(Draw draw, PoseStack pose, float t, float degrees, float half, int brightness) {
		pose.pushPose();
		pose.translate(0.5, TOP, 0.5);
		pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(degrees));
		float scroll = -t * 0.01F;
		float v1 = scroll + BEAM_HEIGHT / 8.0F;
		draw.geometry(pose, McClient.eyes(BEAM), (p, buffer) -> {
			Matrix4f m = p.pose();
			float[][] sides = {{-half, -half, half, -half}, {half, -half, half, half}, {half, half, -half, half}, {-half, half, -half, -half}};
			for (float[] side : sides) {
				float nx = (side[0] + side[2]) / 2, nz = (side[1] + side[3]) / 2;
				column(buffer, p, m, side[0], side[1], side[2], side[3], scroll, v1, brightness, nx, nz);
				column(buffer, p, m, side[2], side[3], side[0], side[1], scroll, v1, brightness, -nx, -nz);
			}
		});
		pose.popPose();
	}

	private static void column(VertexConsumer buffer, PoseStack.Pose p, Matrix4f m, float x0, float z0, float x1, float z1, float v0, float v1,
			int brightness, float nx, float nz) {
		vertex(buffer, p, m, x0, 0, z0, 0, v0, brightness, nx, nz);
		vertex(buffer, p, m, x1, 0, z1, 1, v0, brightness, nx, nz);
		vertex(buffer, p, m, x1, BEAM_HEIGHT, z1, 1, v1, 0, nx, nz);
		vertex(buffer, p, m, x0, BEAM_HEIGHT, z0, 0, v1, 0, nx, nz);
	}

	/** One sheet over the top (and its underside, where air is under it), its texture scrolled by (du, dv), scaled. */
	private static void layer(Draw draw, PoseStack pose, RenderType type, BlockPos at, float du, float dv, float scale, int alpha, boolean lift) {
		float u0 = Math.floorMod(at.getX(), (int) TILE) / TILE * scale + du;
		float v0 = Math.floorMod(at.getZ(), (int) TILE) / TILE * scale + dv;
		float span = scale / TILE;
		float top = TOP + (lift ? 0.004F : 0.0F);
		float under = UNDER - (lift ? 0.004F : 0.0F);
		draw.geometry(pose, type, (p, buffer) -> {
			Matrix4f m = p.pose();
			vertex(buffer, p, m, 0, top, 0, u0, v0, alpha, 1);
			vertex(buffer, p, m, 0, top, 1, u0, v0 + span, alpha, 1);
			vertex(buffer, p, m, 1, top, 1, u0 + span, v0 + span, alpha, 1);
			vertex(buffer, p, m, 1, top, 0, u0 + span, v0, alpha, 1);
			vertex(buffer, p, m, 0, under, 0, u0, v0, alpha, -1);
			vertex(buffer, p, m, 1, under, 0, u0 + span, v0, alpha, -1);
			vertex(buffer, p, m, 1, under, 1, u0 + span, v0 + span, alpha, -1);
			vertex(buffer, p, m, 0, under, 1, u0, v0 + span, alpha, -1);
		});
	}

	/** A fringe of light rising off the sheet's open side, both faces, flickering along. */
	private static void edge(Draw draw, PoseStack pose, Direction side, BlockPos at, float t, int glow) {
		float along = (side.getAxis() == Direction.Axis.X ? at.getZ() : at.getX()) / TILE + t * 0.002F;
		float x0, z0, x1, z1;
		switch (side) {
			case NORTH -> { x0 = 0; z0 = 0; x1 = 1; z1 = 0; }
			case SOUTH -> { x0 = 1; z0 = 1; x1 = 0; z1 = 1; }
			case WEST -> { x0 = 0; z0 = 1; x1 = 0; z1 = 0; }
			default -> { x0 = 1; z0 = 0; x1 = 1; z1 = 1; }
		}
		float span = 1.0F / TILE;
		float low = UNDER - 0.1F;
		float high = TOP + 1.1F;
		RenderType type = McClient.eyes(EDGE);
		draw.geometry(pose, type, (p, buffer) -> {
			Matrix4f m = p.pose();
			float nx = side.getStepX(), nz = side.getStepZ();
			vertex(buffer, p, m, x0, low, z0, along, 1, glow, nx, nz);
			vertex(buffer, p, m, x1, low, z1, along + span, 1, glow, nx, nz);
			vertex(buffer, p, m, x1, high, z1, along + span, 0, glow, nx, nz);
			vertex(buffer, p, m, x0, high, z0, along, 0, glow, nx, nz);
			vertex(buffer, p, m, x0, high, z0, along, 0, glow, -nx, -nz);
			vertex(buffer, p, m, x1, high, z1, along + span, 0, glow, -nx, -nz);
			vertex(buffer, p, m, x1, low, z1, along + span, 1, glow, -nx, -nz);
			vertex(buffer, p, m, x0, low, z0, along, 1, glow, -nx, -nz);
		});
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f m, float x, float y, float z, float u, float v, int alpha, int ny) {
		buffer.addVertex(m, x, y, z).setColor(alpha, alpha, alpha, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(FULL_BRIGHT).setNormal(pose, 0.0F, ny, 0.0F);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f m, float x, float y, float z, float u, float v, int alpha, float nx, float nz) {
		buffer.addVertex(m, x, y, z).setColor(alpha, alpha, alpha, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(FULL_BRIGHT).setNormal(pose, nx, 0.0F, nz);
	}

	@Override
	public AABB getRenderBoundingBox(TheEndPortalBlockEntity rift) {
		return new AABB(rift.getBlockPos()).inflate(0.0, 1.5, 0.0).expandTowards(0.0, BEAM_HEIGHT, 0.0);
	}

	@Override
	public int getViewDistance() {
		return 256;
	}
}
