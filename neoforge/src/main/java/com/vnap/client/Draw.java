package com.vnap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
//? if >=1.21.9 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
*///?} else
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Where the mod's renderers draw: into buffers on the spot until 1.21.8; from 1.21.9 onto the frame's submit list,
 * drawn later in batches (so a pose is taken as it is when the piece is handed over).
 */
public final class Draw {
    /** Vertices for one render type, at the pose they were handed over with. */
    @FunctionalInterface
    public interface Geometry {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }

    //? if >=1.21.9 {
    /*private final SubmitNodeCollector nodes;

    public Draw(SubmitNodeCollector nodes) {
        this.nodes = nodes;
    }

    public SubmitNodeCollector nodes() {
        return nodes;
    }
    *///?} else {
    private final MultiBufferSource buffers;

    public Draw(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    public MultiBufferSource buffers() {
        return buffers;
    }
    //?}

    public void geometry(PoseStack pose, RenderType type, Geometry geometry) {
        //? if >=1.21.9 {
        /*nodes.submitCustomGeometry(pose, type, geometry::render);
        *///?} else
        geometry.render(pose.last(), buffers.getBuffer(type));
    }

    public void item(ItemStack stack, ItemDisplayContext context, int light, int overlay, PoseStack pose, @Nullable Level level, int seed) {
        //? if >=1.21.9 {
        /*ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, context, level, null, seed);
        state.submit(pose, nodes, light, overlay, 0);
        *///?} else
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, context, light, overlay, pose, buffers, level, seed);
    }

    /** A line of text in the pose's XY plane (y down, as on a sign), facing +Z, no shadow. */
    public void text(PoseStack pose, net.minecraft.network.chat.Component text, float x, float y, int color, int light) {
        //? if >=1.21.9 {
        /*nodes.submitText(pose, x, y, text.getVisualOrderText(), false, net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET, light, color, 0, 0);
        *///?} else
        Minecraft.getInstance().font.drawInBatch(text, x, y, color, false, pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.POLYGON_OFFSET, 0, light);
    }

    /** A model of the mod's own (McClient.registerModel), cutout. */
    public void model(ResourceLocation id, PoseStack pose, int light, int overlay) {
        //? if >=26.1 && forge {
        /*java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new java.util.ArrayList<>();
        McClient.model(id).collectParts(net.minecraft.util.RandomSource.create(42L), parts, net.minecraftforge.client.model.data.ModelData.EMPTY);
        nodes.submitBlockModel(pose, McClient.blockSheet(), parts,
                net.minecraft.client.renderer.block.BlockModelRenderState.EMPTY_TINTS, light, overlay, 0);
        *///?} else if >=26.1 {
        /*var model = McClient.model(id);
        if (model == null) return;
        java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new java.util.ArrayList<>();
        // a part of the mod's own, in no level: an empty moving block stands for one
        var nowhere = new net.minecraft.client.renderer.block.MovingBlockRenderState();
        model.collectParts(nowhere, nowhere.blockPos, nowhere.blockState, net.minecraft.util.RandomSource.create(42L), parts);
        nodes.submitBlockModel(pose, McClient.blockSheet(), parts,
                net.minecraft.client.renderer.block.BlockModelRenderState.EMPTY_TINTS, light, overlay, 0);
        *///?} else
        geometry(pose, McClient.blockSheet(), (at, buffer) -> McClient.drawModel(id, at, buffer, light, overlay));
    }

    /** An entity model in its pose (from 1.21.9 set from the state when it is drawn); {@code order} draws later over earlier. */
    //? if >=1.21.9 {
    /*public <S> void entityModel(net.minecraft.client.model.Model<? super S> model, S state, PoseStack pose, RenderType type, int light,
            int overlay, int color, int order) {
        nodes.order(order).submitModel(model, state, pose, type, light, overlay, color, null, 0, null);
    }
    *///?} else {
    @SuppressWarnings("UnusedVariable") // the state poses the model from 1.21.9; the order too
    public <S> void entityModel(net.minecraft.client.model.Model model, S state, PoseStack pose, RenderType type, int light,
            int overlay, int color, int order) {
        model.renderToBuffer(pose, buffers.getBuffer(type), light, overlay, color);
    }
    //?}

    /** A pose stack starting at the pose, for model parts drawn inside a Geometry. */
    public static PoseStack stack(PoseStack.Pose at) {
        PoseStack stack = new PoseStack();
        stack.last().pose().set(at.pose());
        stack.last().normal().set(at.normal());
        return stack;
    }

    //? if >=1.21.9 {
    /*public static class Held<T extends net.minecraft.world.level.block.entity.BlockEntity>
            extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState {
        public @Nullable T be;
        public float partialTick;
    }
    *///?}
    // (Held: a block entity renderer's state from 1.21.9, the block entity itself, drawn as before when the frame is submitted)
}
