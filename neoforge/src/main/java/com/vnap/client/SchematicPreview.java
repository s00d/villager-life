package com.vnap.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
//? if <26.2
import net.minecraft.client.renderer.MultiBufferSource;
//? if <26.1
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
//? if <1.21.5 && !fabric {
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
//?}
//? if >=1.21.6
/*import org.jspecify.annotations.Nullable;*/

/** Isometric, slowly turning block render of a rite schematic for GUIs. */
public final class SchematicPreview {
    private static final int FULL_BRIGHT = 0xF000F0;

    private SchematicPreview() {
    }

    /** Draws the schematic up to {@code layers} layers from the bottom; {@code Integer.MAX_VALUE} draws all. */
    public static void render(GuiGraphics graphics, SchematicSpec spec, int x, int y, int width, int height, int layers) {
        if (spec.blocks.isEmpty()) {
            return;
        }
        float extent = Math.max(Math.max(spec.sizeX, spec.sizeZ) * 1.42F, spec.sizeY * 1.2F);
        float scale = Math.min(width, height) / extent * 0.95F;
        float spin = (float) ((System.currentTimeMillis() % 24000L) / 24000.0 * 360.0);
        //? if >=26.2 && (forge || fabric) {
        /*// the game's own GUI state (the extractor keeps it to itself on Forge and Fabric)
        Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.addPicturesInPictureState(
                new Picture(spec, layers, spin, x, y, x + width, y + height, scale, null));
        *///?} else if >=26.1 && (forge || fabric) {
        /*Minecraft.getInstance().gameRenderer.getGameRenderState().guiRenderState.addPicturesInPictureState(
                new Picture(spec, layers, spin, x, y, x + width, y + height, scale, null));
        *///?} else if >=1.21.6 && fabric {
        /*// the GUI's state, which Fabric's widener opens
        graphics.guiRenderState.submitPicturesInPictureState(new Picture(spec, layers, spin, x, y, x + width, y + height, scale, null));
        *///?} else if >=1.21.6 && forge {
        /*graphics.getRenderState().submitPicturesInPictureState(new Picture(spec, layers, spin, x, y, x + width, y + height, scale, null));
        *///?} else if >=1.21.6 {
        /*graphics.submitPictureInPictureRenderState(new Picture(spec, layers, spin, x, y, x + width, y + height, scale, null));
        *///?} else {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + width / 2.0F, y + height / 2.0F, 150.0F);
        pose.scale(scale, -scale, scale);
        turn(pose, spec, spin);
        Lighting.setupForEntityInInventory();
        //? if >=1.21.2 {
        /*graphics.drawSpecial(buffers -> draw(pose, buffers, spec, layers));
        *///?} else
        draw(pose, graphics.bufferSource(), spec, layers);
        graphics.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
        //?}
    }

    private static void turn(PoseStack pose, SchematicSpec spec, float spin) {
        pose.mulPose(Axis.XP.rotationDegrees(30.0F));
        pose.mulPose(Axis.YP.rotationDegrees(45.0F + spin));
        pose.translate(-spec.sizeX / 2.0F, -spec.sizeY / 2.0F, -spec.sizeZ / 2.0F);
    }

    //? if >=26.2 {
    /*// From 1.21.6 a GUI draws 3D only into a texture of its own (a picture in picture); from 26.2 it hands the renderer a submit list
    public record Picture(SchematicSpec spec, int layers, float spin, int x0, int y0, int x1, int y1, float scale,
                          net.minecraft.client.gui.navigation.@Nullable ScreenRectangle scissorArea)
            implements net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState {
        @Override
        public net.minecraft.client.gui.navigation.@Nullable ScreenRectangle bounds() {
            return net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
        }
    }

    public static final class PictureRenderer extends net.minecraft.client.gui.render.pip.PictureInPictureRenderer<Picture> {
        @Override
        public Class<Picture> getRenderStateClass() {
            return Picture.class;
        }

        @Override
        protected void renderToTexture(Picture picture, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector nodes) {
            turn(pose, picture.spec(), picture.spin());
            submit(pose, nodes, picture.spec(), picture.layers());
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0F;
        }

        @Override
        protected String getTextureLabel() {
            return "villager_life_schematic";
        }
    }

    public static void register(net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) {
        event.register(Picture.class, PictureRenderer::new);
    }

    private static void submit(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector nodes, SchematicSpec spec, int layers) {
        for (SchematicSpec.PlacedBlock placed : spec.blocks) {
            if (placed.rel().getY() >= layers || placed.state().getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            pose.pushPose();
            pose.translate(placed.rel().getX(), placed.rel().getY(), placed.rel().getZ());
            var block = new net.minecraft.client.renderer.block.BlockModelRenderState();
            McClient.blockModel(block, placed.state());
            block.submit(pose, nodes, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
    *///?} else if >=1.21.6 {
    /*// From 1.21.6 a GUI draws 3D only into a texture of its own (a picture in picture), as the sign screen draws its sign
    public record Picture(SchematicSpec spec, int layers, float spin, int x0, int y0, int x1, int y1, float scale,
                          net.minecraft.client.gui.navigation.@Nullable ScreenRectangle scissorArea)
            implements net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState {
        @Override
        public net.minecraft.client.gui.navigation.@Nullable ScreenRectangle bounds() {
            return net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
        }
    }

    public static final class PictureRenderer extends net.minecraft.client.gui.render.pip.PictureInPictureRenderer<Picture> {
        public PictureRenderer(MultiBufferSource.BufferSource buffers) {
            super(buffers);
        }

        @Override
        public Class<Picture> getRenderStateClass() {
            return Picture.class;
        }

        @Override
        protected void renderToTexture(Picture picture, PoseStack pose) {
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
            turn(pose, picture.spec(), picture.spin());
            draw(pose, bufferSource, picture.spec(), picture.layers());
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0F;
        }

        @Override
        protected String getTextureLabel() {
            return "villager_life_schematic";
        }
    }

    public static void register(net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) {
        event.register(Picture.class, PictureRenderer::new);
    }
    *///?}

    //? if <26.2 {
    // a schematic has no level: an empty one at the origin (NeoForge's form with a level); from 26.1 blocks are submitted
    @SuppressWarnings("UnusedVariable")
    private static void draw(PoseStack pose, MultiBufferSource buffers, SchematicSpec spec, int layers) {
        //? if >=26.1 {
        /*var features = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
        *///?} else
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        for (SchematicSpec.PlacedBlock placed : spec.blocks) {
            if (placed.rel().getY() >= layers || placed.state().getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            pose.pushPose();
            pose.translate(placed.rel().getX(), placed.rel().getY(), placed.rel().getZ());
            //? if >=26.1 {
            /*var block = new net.minecraft.client.renderer.block.BlockModelRenderState();
            McClient.blockModel(block, placed.state());
            block.submit(pose, features.getSubmitNodeStorage(), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            *///?} else if fabric {
            /*// vanilla's: each layer of the block's model in the render type vanilla gives the state
            blocks.renderSingleBlock(placed.state(), pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            *///?} else if >=1.21.5 && forge {
            /*McClient.block(blocks, placed.state(), pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            *///?} else if >=1.21.5 {
            /*blocks.renderSingleBlock(placed.state(), pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    net.minecraft.world.level.EmptyBlockAndTintGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO);
            *///?} else {
            for (RenderType type : blocks.getBlockModel(placed.state())
                    .getRenderTypes(placed.state(), RandomSource.create(42L), ModelData.EMPTY)) {
                blocks.renderSingleBlock(placed.state(), pose, buffers, FULL_BRIGHT,
                        //? if >=1.21.2 {
                        /*OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderTypeHelper.getEntityRenderType(type));
                        *///?} else
                        OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderTypeHelper.getEntityRenderType(type, false));
            }
            //?}
            pose.popPose();
        }
        //? if >=26.1
        /*features.renderAllFeatures();*/
    }
    //?}

    /** Layers to show for a build-up animation that loops every few seconds. */
    public static int animatedLayers(SchematicSpec spec) {
        int cycle = spec.sizeY + 3;
        int step = (int) ((System.currentTimeMillis() / 700L) % cycle);
        return Mth.clamp(step + 1, 1, Integer.MAX_VALUE);
    }
}
