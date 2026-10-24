package com.vnap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vnap.block.DecorBlock;
import com.vnap.block.decor.DecorBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
//? if <1.21.9
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws the items a rack, shelf or dummy holds at the spots its mechanic names, turned with the block. */
//? if >=1.21.9 {
/*public final class DecorRenderer implements BlockEntityRenderer<DecorBlockEntity, Draw.Held<DecorBlockEntity>> {
*///?} else
public final class DecorRenderer implements BlockEntityRenderer<DecorBlockEntity> {
    private final InfantRenderer infant;

    public DecorRenderer(BlockEntityRendererProvider.Context context) {
        infant = new InfantRenderer(context);
    }

    //? if >=1.21.9 {
    /*@Override
    public Draw.Held<DecorBlockEntity> createRenderState() {
        return new Draw.Held<>();
    }

    @Override
    public void extractRenderState(DecorBlockEntity be, Draw.Held<DecorBlockEntity> state, float partialTick, net.minecraft.world.phys.Vec3 camera,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.@org.jspecify.annotations.Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, camera, breaking);
        state.be = be;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(Draw.Held<DecorBlockEntity> state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector nodes,
            net.minecraft.client.renderer.state.CameraRenderState camera) {
        if (state.be != null) render(state.be, state.partialTick, pose, new Draw(nodes), state.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
    }
    *///?} else if >=1.21.5 {
    /*@Override
    public void render(DecorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, net.minecraft.world.phys.Vec3 camera) {
        render(be, partialTick, pose, new Draw(buffers), light, overlay);
    }
    *///?} else {
    @Override
    public void render(DecorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        render(be, partialTick, pose, new Draw(buffers), light, overlay);
    }
    //?}

    private void render(DecorBlockEntity be, float partialTick, PoseStack pose, Draw draw, int light, int overlay) {
        if (be.getBlockState().getBlock() instanceof DecorBlock decor && decor.name().equals("cradle")) infant.render(be, partialTick, pose, draw, light);
        if (be.getBlockState().getBlock() instanceof DecorBlock decor && decor.name().equals("shop_sign")) {
            sign(be, partialTick, pose, draw, light, overlay);
            return;
        }
        float[][] spots = be.mechanic().display();
        if (spots.length == 0) {
            return;
        }
        float turn = be.getBlockState().getValue(DecorBlock.FACING).toYRot() - 180.0F;
        for (int i = 0; i < spots.length && i < be.items.getContainerSize(); i++) {
            ItemStack stack = be.items.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            float[] spot = spots[i];
            pose.pushPose();
            pose.translate(0.5, 0.0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-turn));
            pose.translate(spot[0] / 16.0 - 0.5, spot[1] / 16.0, spot[2] / 16.0 - 0.5);
            // a fifth number lays the item down (goods heaped in a crate)
            if (spot.length > 4) pose.mulPose(Axis.XP.rotationDegrees(spot[4]));
            pose.scale(spot[3], spot[3], spot[3]);
            draw.item(stack, ItemDisplayContext.FIXED, light, overlay, pose, be.getLevel(), (int) be.getBlockPos().asLong() + i);
            pose.popPose();
        }
    }

    public static final net.minecraft.resources.ResourceLocation SIGN_BOARD = com.vnap.VillagerLife.id("block/decor/shop_sign_board");

    /**
     * The sign's board on its chains, swinging about the bracket (the block's model): a breath in calm weather, more
     * in rain, hard in a storm; each sign its own beat. Its ware is painted on both faces.
     */
    private void sign(DecorBlockEntity be, float partialTick, PoseStack pose, Draw draw, int light, int overlay) {
        net.minecraft.world.level.Level level = be.getLevel();
        if (level == null) return;
        float time = level.getGameTime() + partialTick;
        long seed = be.getBlockPos().asLong();
        float storm = level.getThunderLevel(partialTick);
        float rain = level.getRainLevel(partialTick);
        float amplitude = 3.0F + 6.0F * rain + 10.0F * storm;
        float speed = 0.07F + 0.05F * rain + 0.08F * storm;
        float swing = amplitude * net.minecraft.util.Mth.sin(time * speed + (seed & 63)) + amplitude * 0.3F * net.minecraft.util.Mth.sin(time * speed * 2.3F + (seed >> 6 & 63));
        float turn = be.getBlockState().getValue(DecorBlock.FACING).toYRot() - 180.0F;
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-turn));
        pose.translate(-0.5, 0.0, -0.5);
        // the hinge: under the bracket, the length of the board
        pose.translate(0.5, 14.5 / 16.0, 0.5);
        pose.mulPose(Axis.ZP.rotationDegrees(swing));
        pose.translate(-0.5, -14.5 / 16.0, -0.5);
        draw.model(SIGN_BOARD, pose, light, overlay);
        ItemStack ware = be.items.getItem(0);
        if (!ware.isEmpty()) {
            for (int face = -1; face <= 1; face += 2) {
                pose.pushPose();
                pose.translate(0.5 + face * 0.05, 6.5 / 16.0, 0.5);
                pose.mulPose(Axis.YP.rotationDegrees(face * 90.0F));
                pose.scale(0.42F, 0.42F, 0.42F);
                draw.item(ware, ItemDisplayContext.FIXED, light, overlay, pose, level, (int) seed);
                pose.popPose();
            }
        }
        pose.popPose();
    }
}
