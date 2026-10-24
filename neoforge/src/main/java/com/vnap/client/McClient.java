package com.vnap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
//? if >=1.21.5 {
/*import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import java.util.HashMap;
import java.util.Map;
*///?} else if >=1.21.4 {
/*import net.minecraft.client.resources.model.BakedModel;
*///?} else {
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
//?}

/** Client calls that differ between the Minecraft versions the mod builds for (see com.vnap.Mc for the rest). */
public final class McClient {
    private McClient() {
    }

    //? if >=1.21.2
    /*private static int tint = -1;*/

    /** The colour the next blits are tinted with (GuiGraphics.setColor of 1.21.1; later blits take a colour). */
    public static void setColor(GuiGraphics graphics, float red, float green, float blue, float alpha) {
        //? if >=1.21.2 {
        /*tint = net.minecraft.util.ARGB.colorFromFloat(alpha, red, green, blue);
        *///?} else
        graphics.setColor(red, green, blue, alpha);
    }

    /** A w×h piece of the texture at (u, v), drawn at (x, y). */
    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int w, int h, int textureWidth, int textureHeight) {
        //? if >=1.21.6 {
        /*graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, textureWidth, textureHeight, tint);
        *///?} else if >=1.21.2 {
        /*graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, texture, x, y, u, v, w, h, textureWidth, textureHeight, tint);
        *///?} else
        graphics.blit(texture, x, y, u, v, w, h, textureWidth, textureHeight);
    }

    /** The uWidth×vHeight piece at (u, v), stretched over w×h at (x, y). */
    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int w, int h, float u, float v, int uWidth, int vHeight,
                            int textureWidth, int textureHeight) {
        //? if >=1.21.6 {
        /*graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, uWidth, vHeight, textureWidth, textureHeight, tint);
        *///?} else if >=1.21.2 {
        /*graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, texture, x, y, u, v, w, h, uWidth, vHeight, textureWidth, textureHeight, tint);
        *///?} else
        graphics.blit(texture, x, y, w, h, u, v, uWidth, vHeight, textureWidth, textureHeight);
    }

    /** A GUI sprite (textures/gui/sprites) over w×h at (x, y), scaled as its .mcmeta says (nine-slice, tile, stretch). */
    public static void sprite(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        //? if >=1.21.6 {
        /*graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
        *///?} else if >=1.21.2 {
        /*graphics.blitSprite(net.minecraft.client.renderer.RenderType::guiTextured, sprite, x, y, w, h);
        *///?} else
        graphics.blitSprite(sprite, x, y, w, h);
    }

    public static void blitSprite(GuiGraphics graphics, ResourceLocation sprite, int textureWidth, int textureHeight, int u, int v, int x, int y,
                                  int w, int h) {
        //? if >=1.21.6 {
        /*graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, textureWidth, textureHeight, u, v, x, y, w, h);
        *///?} else if >=1.21.2 {
        /*graphics.blitSprite(net.minecraft.client.renderer.RenderType::guiTextured, sprite, textureWidth, textureHeight, u, v, x, y, w, h);
        *///?} else
        graphics.blitSprite(sprite, textureWidth, textureHeight, u, v, x, y, w, h);
    }

    // The GUI's own transform: a 3D PoseStack until 1.21.5, a 2D Matrix3x2fStack from 1.21.6 (no depth: what is drawn
    // later is on top, so a z offset has nothing left to do)

    public static void push(GuiGraphics graphics) {
        //? if >=1.21.6 {
        /*graphics.pose().pushMatrix();
        *///?} else
        graphics.pose().pushPose();
    }

    public static void pop(GuiGraphics graphics) {
        //? if >=1.21.6 {
        /*graphics.pose().popMatrix();
        *///?} else
        graphics.pose().popPose();
    }

    public static void translate(GuiGraphics graphics, float x, float y, float z) {
        //? if >=1.21.6 {
        /*graphics.pose().translate(x, y);
        *///?} else
        graphics.pose().translate(x, y, z);
    }

    public static void scale(GuiGraphics graphics, float x, float y, float z) {
        //? if >=1.21.6 {
        /*graphics.pose().scale(x, y);
        *///?} else
        graphics.pose().scale(x, y, z);
    }

    public static void rotate(GuiGraphics graphics, float degrees) {
        //? if >=1.21.6 {
        /*graphics.pose().rotate(degrees * net.minecraft.util.Mth.DEG_TO_RAD);
        *///?} else
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
    }

    // Tooltips are drawn after the screen from 1.21.6 (set for the next frame, not drawn on the spot)

    public static void tooltip(GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.world.item.ItemStack stack, int x, int y) {
        //? if >=1.21.6 {
        /*graphics.setTooltipForNextFrame(font, stack, x, y);
        *///?} else
        graphics.renderTooltip(font, stack, x, y);
    }

    public static void tooltip(GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text, int x, int y) {
        //? if >=1.21.6 {
        /*graphics.setTooltipForNextFrame(font, text, x, y);
        *///?} else
        graphics.renderTooltip(font, text, x, y);
    }

    public static void tooltip(GuiGraphics graphics, net.minecraft.client.gui.Font font, java.util.List<net.minecraft.network.chat.Component> lines, int x, int y) {
        //? if >=1.21.6 {
        /*graphics.setComponentTooltipForNextFrame(font, lines, x, y);
        *///?} else
        graphics.renderComponentTooltip(font, lines, x, y);
    }

    public static void tooltip(GuiGraphics graphics, net.minecraft.client.gui.Font font, java.util.List<net.minecraft.network.chat.Component> lines,
                               java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> image, int x, int y) {
        //? if >=1.21.6 {
        /*graphics.setTooltipForNextFrame(font, lines, image, x, y);
        *///?} else
        graphics.renderTooltip(font, lines, image, x, y);
    }

    // Models loaded on their own (models/<id>.json), for parts the mod draws itself

    //? if >=1.21.5 {
    /*private static final Map<ResourceLocation, StandaloneModelKey<BlockStateModel>> KEYS = new HashMap<>();

    private static StandaloneModelKey<BlockStateModel> key(ResourceLocation id) {
        return KEYS.computeIfAbsent(id, name -> new StandaloneModelKey<>(name::toString));
    }

    public static void registerModel(ModelEvent.RegisterStandalone event, ResourceLocation id) {
        event.register(key(id), SimpleUnbakedStandaloneModel.blockStateModel(id));
    }
    *///?} else {
    public static void registerModel(ModelEvent.RegisterAdditional event, ResourceLocation id) {
        //? if >=1.21.4 {
        /*event.register(id);
        *///?} else
        event.register(ModelResourceLocation.standalone(id));
    }
    //?}

    //? if >=1.21.5 {
    /*public static @org.jspecify.annotations.Nullable BlockStateModel model(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(key(id));
    }
    *///?}

    /** Draws the model (cutout, no tint) at the pose (from 26.1 a model is submitted as its parts: Draw.model). */
    @SuppressWarnings("UnusedVariable") // a part drawn on its own: no level, an empty one at the origin
    public static void drawModel(ResourceLocation id, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26.1 {
        /*throw new UnsupportedOperationException("models are submitted from 26.1");
        *///?} else if >=1.21.5 {
        /*BlockStateModel model = minecraft.getModelManager().getStandaloneModel(key(id));
        if (model == null) return;
        net.minecraft.client.renderer.block.ModelBlockRenderer.renderModel(pose, type -> buffer, model, 1.0F, 1.0F, 1.0F, light, overlay,
                net.minecraft.world.level.EmptyBlockAndTintGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        *///?} else if >=1.21.4 {
        /*BakedModel model = minecraft.getModelManager().getStandaloneModel(id);
        if (model == null) return;
        minecraft.getBlockRenderer().getModelRenderer().renderModel(pose, buffer, null, model, 1.0F, 1.0F, 1.0F, light, overlay,
                net.neoforged.neoforge.client.model.data.ModelData.EMPTY, net.minecraft.client.renderer.RenderType.cutout());
        *///?} else {
        BakedModel model = minecraft.getModelManager().getModel(ModelResourceLocation.standalone(id));
        minecraft.getBlockRenderer().getModelRenderer().renderModel(pose, buffer, null, model, 1.0F, 1.0F, 1.0F, light, overlay,
                net.neoforged.neoforge.client.model.data.ModelData.EMPTY, net.minecraft.client.renderer.RenderType.cutout());
        //?}
    }

    // Entity render types: methods of RenderType until 1.21.10, of RenderTypes from 1.21.11

    public static net.minecraft.client.renderer.RenderType eyes(ResourceLocation texture) {
        //? if >=1.21.11 {
        /*return net.minecraft.client.renderer.rendertype.RenderTypes.eyes(texture);
        *///?} else
        return net.minecraft.client.renderer.RenderType.eyes(texture);
    }

    public static net.minecraft.client.renderer.RenderType entityCutout(ResourceLocation texture) {
        //? if >=1.21.11 {
        /*return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture);
        *///?} else
        return net.minecraft.client.renderer.RenderType.entityCutout(texture);
    }

    public static net.minecraft.client.renderer.RenderType entityCutoutNoCull(ResourceLocation texture) {
        //? if >=26.1 {
        /*return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture);
        *///?} else if >=1.21.11 {
        /*return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutoutNoCull(texture);
        *///?} else
        return net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture);
    }

    /** Opens the screen (none closes it): the game's GUI holds the screen from 26.2. */
    public static void setScreen(net.minecraft.client.gui.screens.@org.jspecify.annotations.Nullable Screen screen) {
        //? if >=26.2 {
        /*Minecraft.getInstance().gui.setScreen(screen);
        *///?} else
        Minecraft.getInstance().setScreen(screen);
    }

    /** A click of a mouse button on a screen at gui coordinates (press and release; an event record from 1.21.9). */
    public static void click(net.minecraft.client.gui.screens.Screen screen, double x, double y, int button) {
        //? if >=1.21.9 {
        /*net.minecraft.client.input.MouseButtonEvent event = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(button, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        *///?} else {
        screen.mouseClicked(x, y, button);
        screen.mouseReleased(x, y, button);
        //?}
    }

    /** The open screen, or null. */
    public static net.minecraft.client.gui.screens.@org.jspecify.annotations.Nullable Screen screen() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else
        return Minecraft.getInstance().screen;
    }

    /** F1: the HUD is hidden. */
    public static boolean hudHidden() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.hud.isHidden();
        *///?} else
        return Minecraft.getInstance().options.hideGui;
    }

    /** The packed block and sky light at the position. */
    public static int light(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        //? if >=26.2 {
        /*return net.minecraft.util.LightCoordsUtil.getLightCoords(level, pos);
        *///?} else if >=26.1 {
        /*return net.minecraft.client.renderer.LevelRenderer.getLightCoords(level, pos);
        *///?} else
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos);
    }

    /** Block models drawn on their own (a part, a moving block): cutout, from the block atlas. */
    public static net.minecraft.client.renderer.RenderType blockSheet() {
        //? if >=26.2 {
        /*return net.minecraft.client.renderer.rendertype.RenderTypes.cutoutMovingBlock();
        *///?} else
        return net.minecraft.client.renderer.Sheets.cutoutBlockSheet();
    }

    /** The stack in the player's hand slot. */
    public static net.minecraft.world.item.ItemStack selected(net.minecraft.world.entity.player.Inventory inventory) {
        //? if >=1.21.5 {
        /*return inventory.getSelectedItem();
        *///?} else
        return inventory.getSelected();
    }

    /** The hotbar slot in hand. */
    public static void select(net.minecraft.world.entity.player.Inventory inventory, int slot) {
        //? if >=1.21.5 {
        /*inventory.setSelectedSlot(slot);
        *///?} else
        inventory.selected = slot;
    }

    /** Where the camera is. */
    public static net.minecraft.world.phys.Vec3 camera() {
        //? if >=26.2 {
        /*return net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
        *///?} else if >=1.21.11 {
        /*return net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().position();
        *///?} else
        return net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    /** The player's own arm swing (from 26.3 the server tells the others by itself). */
    public static void swing(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        //? if >=26.3 {
        /*player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        *///?} else
        player.swing(hand);
    }

    /** A shift-click on the slot of the open container. */
    public static void quickMove(net.minecraft.world.entity.player.Player player, int containerId, int slot) {
        var gameMode = Minecraft.getInstance().gameMode;
        if (gameMode == null) return;
        //? if >=26.1 {
        /*gameMode.handleContainerInput(containerId, slot, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
        *///?} else
        gameMode.handleInventoryMouseClick(containerId, slot, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
    }
}
