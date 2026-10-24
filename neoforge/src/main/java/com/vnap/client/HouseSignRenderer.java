package com.vnap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vnap.block.town.HouseSignBlock;
import com.vnap.block.town.HouseSignBlockEntity;
//? if <1.21.9
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The names on a nameplate, burnt into its board: the head's name large, the spouse and the children's line smaller
 * under it, each shrunk to the board's width (the home marker, line 1, is not shown). The board's face is 14 by 8
 * pixels on a wall (y 4-12, against the wall behind) and 14 by 7 on a post (y 8-15, in the post's middle).
 */
//? if >=1.21.9 {
/*public final class HouseSignRenderer implements BlockEntityRenderer<HouseSignBlockEntity, Draw.Held<HouseSignBlockEntity>> {
*///?} else
public final class HouseSignRenderer implements BlockEntityRenderer<HouseSignBlockEntity> {
	private static final int INK = 0xFF3A2414;
	private static final float WIDTH = 12.0F / 16.0F;
	private static final float NAME = 1.0F / 90.0F;
	private static final float SMALL = 1.0F / 140.0F;

	public HouseSignRenderer(BlockEntityRendererProvider.Context context) {
	}

	//? if >=1.21.9 {
	/*@Override
	public Draw.Held<HouseSignBlockEntity> createRenderState() {
		return new Draw.Held<>();
	}

	@Override
	public void extractRenderState(HouseSignBlockEntity sign, Draw.Held<HouseSignBlockEntity> state, float partialTick, net.minecraft.world.phys.Vec3 camera,
			net.minecraft.client.renderer.feature.ModelFeatureRenderer.@org.jspecify.annotations.Nullable CrumblingOverlay breaking) {
		BlockEntityRenderer.super.extractRenderState(sign, state, partialTick, camera, breaking);
		state.be = sign;
	}

	@Override
	public void submit(Draw.Held<HouseSignBlockEntity> state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector nodes,
			net.minecraft.client.renderer.state.CameraRenderState camera) {
		if (state.be != null) render(state.be, pose, new Draw(nodes));
	}
	*///?} else if >=1.21.5 {
	/*@Override
	public void render(HouseSignBlockEntity sign, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, net.minecraft.world.phys.Vec3 camera) {
		render(sign, pose, new Draw(buffers));
	}
	*///?} else {
	@Override
	public void render(HouseSignBlockEntity sign, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		render(sign, pose, new Draw(buffers));
	}
	//?}

	private static void render(HouseSignBlockEntity sign, PoseStack pose, Draw draw) {
		Level level = sign.getLevel();
		if (level == null) return;
		BlockState state = sign.getBlockState();
		if (!(state.getBlock() instanceof HouseSignBlock)) return;
		Font font = Minecraft.getInstance().font;
		List<Component> rows = new ArrayList<>();
		List<Float> scales = new ArrayList<>();
		for (int line : new int[]{0, 2, 3}) {
			Component text = com.vnap.Mc.signText(sign, line);
			int width = font.width(text);
			if (width == 0) continue;
			rows.add(text);
			scales.add(Math.min(line == 0 ? NAME : SMALL, WIDTH / width));
		}
		if (rows.isEmpty()) return;
		boolean post = state.getValue(HouseSignBlock.POST);
		float height = (post ? 5.0F : 6.0F) / 16.0F;
		float total = 0.0F;
		for (float scale : scales) total += scale * font.lineHeight;
		float fit = Math.min(1.0F, height / total);
		Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
		int light = McClient.light(level, sign.getBlockPos());
		pose.pushPose();
		pose.translate(0.5, 0.5, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
		// local +Z looks out; the board's face, its middle
		pose.translate(0.0, post ? 3.5 / 16.0 : 0.0, post ? 1.0 / 16.0 + 0.005 : -6.0 / 16.0 + 0.005);
		float y = total * fit / 2.0F;
		for (int i = 0; i < rows.size(); i++) {
			float scale = scales.get(i) * fit;
			pose.pushPose();
			pose.translate(0.0, y, 0.0);
			pose.scale(scale, -scale, scale);
			draw.text(pose, rows.get(i), -font.width(rows.get(i)) / 2.0F, 0.0F, INK, light);
			pose.popPose();
			y -= scale * font.lineHeight;
		}
		pose.popPose();
	}

	@Override
	public int getViewDistance() {
		return 32;
	}
}
