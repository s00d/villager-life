package com.vnap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vnap.VillagerLife;
import com.vnap.entity.HandCart;
//? if <1.21.9
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
//? if >=1.21.2 {
/*import net.minecraft.client.renderer.entity.state.EntityRenderState;
*///?} else
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.List;

/** The hand cart from its JSON parts (models/entity/hand_cart*): body, two wheels turning as it rolls, its load. */
//? if >=1.21.2 {
/*public final class HandCartRenderer extends EntityRenderer<HandCart, EntityRenderState> {
*///?} else
public final class HandCartRenderer extends EntityRenderer<HandCart> {
	public static final ResourceLocation BODY = part("hand_cart");
	public static final ResourceLocation WHEEL = part("hand_cart_wheel");
	public static final ResourceLocation LOW = part("hand_cart_load_low");
	public static final ResourceLocation HIGH = part("hand_cart_load_high");
	/** Where the goods on top lie, in model pixels: {x, y, z, yaw}. */
	private static final float[][] HEAP = {{6, 10.5F, 5, 20}, {10, 10.5F, 11, -35}, {7, 11.5F, 10, 60}, {10, 11.5F, 5, -10},
			{6, 12.5F, 8, 80}, {9, 13, 8, -50}, {8, 13.5F, 4, 10}, {8, 14, 12, 40}};
	public HandCartRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.6F;
	}

	private static ResourceLocation part(String name) {
		return VillagerLife.id("entity/" + name);
	}

	//? if >=1.21.5 {
	/*public static void registerModels(ModelEvent.RegisterStandalone event) {
	*///?} else
	public static void registerModels(ModelEvent.RegisterAdditional event) {
		for (ResourceLocation model : List.of(BODY, WHEEL, LOW, HIGH, DecorRenderer.SIGN_BOARD)) McClient.registerModel(event, model);
	}

	//? if >=1.21.2 {
	/*@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	*///?}
	//? if >=1.21.9 {
	/*@Override
	public void submit(EntityRenderState state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector nodes,
			net.minecraft.client.renderer.state.CameraRenderState camera) {
		HandCart cart = RenderedEntity.of(state, HandCart.class);
		if (cart != null) draw(cart, state.partialTick, pose, new Draw(nodes), state.lightCoords);
		super.submit(state, pose, nodes, camera);
	}
	*///?} else if >=1.21.2 {
	/*@Override
	public void render(EntityRenderState state, PoseStack pose, MultiBufferSource buffers, int light) {
		HandCart cart = RenderedEntity.of(state, HandCart.class);
		if (cart != null) draw(cart, state.partialTick, pose, new Draw(buffers), light);
		super.render(state, pose, buffers, light);
	}
	*///?} else {
	@Override
	public void render(HandCart cart, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		draw(cart, partialTick, pose, new Draw(buffers), light);
		super.render(cart, yaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(HandCart cart) {
		return InventoryMenu.BLOCK_ATLAS;
	}
	//?}

	private void draw(HandCart cart, float partialTick, PoseStack pose, Draw draw, int light) {
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, cart.yRotO, cart.getYRot())));
		pose.translate(-0.5, 0.0, -0.5);
		draw.model(BODY, pose, light, OverlayTexture.NO_OVERLAY);
		float turn = Mth.lerp(partialTick, cart.wheelO, cart.wheel);
		for (float x : new float[]{0.0F, 16.0F}) {
			pose.pushPose();
			pose.translate(x / 16.0, 5.0 / 16.0, 0.5);
			pose.mulPose(Axis.XP.rotation(turn));
			pose.translate(-0.5, -0.5, -0.5);
			draw.model(WHEEL, pose, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
		int fill = cart.fill();
		if (fill > 0) draw.model(fill >= 3 ? HIGH : LOW, pose, light, OverlayTexture.NO_OVERLAY);
		ItemStack top = cart.top();
		if (!top.isEmpty()) {
			// what it carries lies on top of the sacks, more of it the fuller the cart
			boolean block = top.getItem() instanceof BlockItem;
			for (int i = 0; i < Math.min(HEAP.length, fill * 2); i++) {
				float[] at = HEAP[i];
				pose.pushPose();
				pose.translate(at[0] / 16.0, (at[1] + (fill >= 3 ? 2.0F : 0.0F)) / 16.0, at[2] / 16.0);
				pose.mulPose(Axis.YP.rotationDegrees(at[3]));
				if (!block) pose.mulPose(Axis.XP.rotationDegrees(90.0F));
				pose.scale(0.45F, 0.45F, 0.45F);
				draw.item(top, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, cart.level(), cart.getId() + i);
				pose.popPose();
			}
		}
		pose.popPose();
	}

}
