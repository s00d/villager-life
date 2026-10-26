package com.vnap.client.golem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vnap.entity.golem.TocsinAnvil;
//? if <1.21.9
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
//? if <1.21.9
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21.2 {
/*import com.vnap.client.RenderedEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
*///?} else
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
//? if <1.21.5
import net.neoforged.neoforge.client.model.data.ModelData;

//? if >=1.21.2 {
/*public final class TocsinAnvilRenderer extends EntityRenderer<TocsinAnvil, EntityRenderState> {
*///?} else
public final class TocsinAnvilRenderer extends EntityRenderer<TocsinAnvil> {
    //? if <1.21.9
    private final BlockRenderDispatcher blocks;

    public TocsinAnvilRenderer(EntityRendererProvider.Context context) {
        super(context);
        //? if <1.21.9
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.6F;
        this.shadowStrength = 1.0F;
    }

    //? if >=1.21.2 {
    /*@Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    *///?}
    //? if >=1.21.9 {
    /*@Override
    public void submit(EntityRenderState state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector nodes,
            net.minecraft.client.renderer.state.CameraRenderState camera) {
        TocsinAnvil anvil = RenderedEntity.of(state, TocsinAnvil.class);
        if (anvil != null) {
            poseStack.pushPose();
            turn(anvil, com.vnap.client.RenderedEntity.partialTick(state), poseStack);
            anvilBlock(anvil, state, poseStack, nodes);
            poseStack.popPose();
        }
        super.submit(state, poseStack, nodes, camera);
    }
    *///?} else if >=1.21.2 {
    /*@Override
    public void render(EntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int light) {
        TocsinAnvil anvil = RenderedEntity.of(state, TocsinAnvil.class);
        if (anvil != null) spin(anvil, com.vnap.client.RenderedEntity.partialTick(state), poseStack, buffers, light);
        super.render(state, poseStack, buffers, light);
    }
    *///?} else {
    @Override
    public void render(TocsinAnvil anvil, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        spin(anvil, partialTick, poseStack, buffers, light);
        super.render(anvil, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(TocsinAnvil anvil) {
        return InventoryMenu.BLOCK_ATLAS;
    }
    //?}

    //? if >=26.2 {
    /*@SuppressWarnings("UnusedVariable") // the state's light: a moving block lights itself from the level
    private static void anvilBlock(TocsinAnvil anvil, EntityRenderState state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector nodes) {
        var block = new net.minecraft.client.renderer.block.MovingBlockRenderState();
        block.blockState = Blocks.ANVIL.defaultBlockState();
        block.blockPos = anvil.blockPosition();
        block.randomSeedPos = anvil.blockPosition();
        if (anvil.level() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
            block.biome = level.getBiome(block.blockPos);
            block.cardinalLighting = level.cardinalLighting();
            block.lightEngine = level.getLightEngine();
        }
        nodes.submitMovingBlock(poseStack, block, 0);
    }
    *///?} else if >=26.1 {
    /*@SuppressWarnings("UnusedVariable") // the state's light: a moving block lights itself from the level
    private static void anvilBlock(TocsinAnvil anvil, EntityRenderState state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector nodes) {
        var block = new net.minecraft.client.renderer.block.MovingBlockRenderState();
        block.blockState = Blocks.ANVIL.defaultBlockState();
        block.blockPos = anvil.blockPosition();
        block.randomSeedPos = anvil.blockPosition();
        if (anvil.level() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
            block.biome = level.getBiome(block.blockPos);
            block.cardinalLighting = level.cardinalLighting();
            block.lightEngine = level.getLightEngine();
        }
        nodes.submitMovingBlock(poseStack, block);
    }
    *///?} else if >=1.21.9 {
    /*@SuppressWarnings("UnusedVariable") // the anvil itself, from 26.1
    private static void anvilBlock(TocsinAnvil anvil, EntityRenderState state, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector nodes) {
        nodes.submitBlock(poseStack, Blocks.ANVIL.defaultBlockState(), state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
    }
    *///?}

    private static void turn(TocsinAnvil anvil, float partialTick, PoseStack poseStack) {
        poseStack.mulPose(Axis.YP.rotationDegrees(anvil.getYRot() + (anvil.tickCount + partialTick) * 9.0F));
        poseStack.translate(-0.5, 0.0, -0.5);
    }

    //? if <1.21.9 {
    private void spin(TocsinAnvil anvil, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        turn(anvil, partialTick, poseStack);
        //? if fabric {
        /*blocks.renderSingleBlock(Blocks.ANVIL.defaultBlockState(), poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
        *///?} else if >=1.21.5 && forge {
        /*blocks.renderSingleBlock(Blocks.ANVIL.defaultBlockState(), poseStack, buffers, light, OverlayTexture.NO_OVERLAY, net.minecraftforge.client.model.data.ModelData.EMPTY, RenderType.solid());
        *///?} else if >=1.21.5 {
        /*blocks.renderSingleBlock(Blocks.ANVIL.defaultBlockState(), poseStack, buffers, light, OverlayTexture.NO_OVERLAY, anvil.level(), anvil.blockPosition());
        *///?} else
        blocks.renderSingleBlock(Blocks.ANVIL.defaultBlockState(), poseStack, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.solid());
        poseStack.popPose();
    }
    //?}
}
