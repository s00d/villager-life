package com.vnap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vnap.VillagerLife;
import com.vnap.entity.VillagerLifeData;
import com.vnap.network.VillageMoodPayload;
import com.vnap.village.workshop.Workshops;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
//? if <1.21.9
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;

import java.util.HashMap;
import java.util.Map;

/**
 * Outfits drawn like the vanilla profession layer, over the villager UV map: mood stages near the village
 * meeting point build up from a wreath to a vestment (or a hood to a dark vestment), and profession levels
 * add an apron at 2, a belt tool at 3 and a master's hat at 5, which keeps the head from the wreath and hood.
 * A woman wears a dress in her profession's colours and her hair under all of that: painted on the head, and a braid,
 * a ponytail or a bun behind it; a baby girl wears a bow on her head.
 */
//? if >=1.21.2 {
/*public final class VillagerOutfitLayer extends RenderLayer<net.minecraft.client.renderer.entity.state.VillagerRenderState, VillagerModel> {
*///?} else
public final class VillagerOutfitLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
	private static final double REACH = 64.0 * 64.0;
	private static final float[] BRIGHT_AT = {90.0F, 50.0F, 75.0F, 60.0F, 20.0F};
	private static final ResourceLocation[] BRIGHT = textures("vestment_light", "ribbon", "sash", "badge", "wreath");
	private static final float[] GLOOM_AT = {-20.0F, -90.0F, -50.0F, -60.0F, -75.0F};
	private static final ResourceLocation[] GLOOM = textures("hood", "vestment_dark", "armband", "ash", "soot");
	private static final Map<String, ResourceLocation[]> PROFESSION = new HashMap<>();
	private static final Map<Integer, Integer> LAST_SPARK = new HashMap<>();
	private static final String[] STYLES = {"braid", "ponytail", "bun"};
	private static final String[] COLOURS = {"brown", "blonde", "black", "red", "grey"};
	private static final int GREY = 4;
	private static final ResourceLocation ELDER_MAN = textures("elder_man")[0];
	private static final ResourceLocation MAYOR = textures("mayor")[0];
	private static final ResourceLocation MAYOR_HAT = textures("mayor_hat")[0];
	private static final ResourceLocation SHOPKEEPER = textures("shopkeeper")[0];
	private static final ResourceLocation SHOPKEEPER_CAP = textures("shopkeeper_cap")[0];
	/** Clothes from the spire, by number less one (spire/SpireOutfits). */
	private static final ResourceLocation[] SPIRE = com.vnap.spire.SpireOutfits.NAMES.stream()
			.map(name -> textures("spire_" + name)[0]).toArray(ResourceLocation[]::new);
	private static final ResourceLocation[][] HAIR = new ResourceLocation[STYLES.length][];
	private static final ResourceLocation FACE = textures("face_female")[0];
	private static final Map<String, ResourceLocation> DRESS = new HashMap<>();
	private static final ResourceLocation PLAIN_DRESS = textures("dress_none")[0];
	/** Braid, ponytail and bun (STYLES order) behind the head, and the bow, each a part in the head's frame. */
	private static final ModelPart[] HAIRDO = hairdos();
	private static final ModelPart BOW = HAIRDO[STYLES.length];
	private static final ResourceLocation[] HAIRDO_TEXTURE = new ResourceLocation[COLOURS.length];
	private static final ResourceLocation BOW_TEXTURE = VillagerLife.id("textures/entity/female/bow.png");

	static {
		for (String profession : Workshops.PROFESSIONS) {
			PROFESSION.put(profession, textures(profession + "_apron", profession + "_tool", profession + "_hat"));
			DRESS.put(profession, textures("dress_" + profession)[0]);
		}
		DRESS.put("nitwit", textures("dress_nitwit")[0]);
		for (int style = 0; style < STYLES.length; style++) {
			String[] names = new String[COLOURS.length];
			for (int colour = 0; colour < COLOURS.length; colour++) names[colour] = "hair_" + STYLES[style] + "_" + COLOURS[colour];
			HAIR[style] = textures(names);
		}
		for (int colour = 0; colour < COLOURS.length; colour++) {
			HAIRDO_TEXTURE[colour] = VillagerLife.id("textures/entity/female/braid_" + COLOURS[colour] + ".png");
		}
	}

	//? if >=1.21.9 {
	/*private net.minecraft.client.renderer.entity.state.VillagerRenderState current = new net.minecraft.client.renderer.entity.state.VillagerRenderState();

	public VillagerOutfitLayer(RenderLayerParent<net.minecraft.client.renderer.entity.state.VillagerRenderState, VillagerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector nodes, int packedLight,
			net.minecraft.client.renderer.entity.state.VillagerRenderState state, float yRot, float xRot) {
		Villager villager = RenderedEntity.of(state, Villager.class);
		if (villager == null) return;
		current = state;
		dress(poseStack, new Draw(nodes), packedLight, villager);
	}
	*///?} else if >=1.21.2 {
	/*private net.minecraft.client.renderer.entity.state.VillagerRenderState current = new net.minecraft.client.renderer.entity.state.VillagerRenderState();

	public VillagerOutfitLayer(RenderLayerParent<net.minecraft.client.renderer.entity.state.VillagerRenderState, VillagerModel> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			net.minecraft.client.renderer.entity.state.VillagerRenderState state, float yRot, float xRot) {
		Villager villager = RenderedEntity.of(state, Villager.class);
		if (villager == null) return;
		current = state;
		dress(poseStack, new Draw(buffer), packedLight, villager);
	}
	*///?} else {
	public VillagerOutfitLayer(RenderLayerParent<Villager, VillagerModel<Villager>> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Villager villager,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		dress(poseStack, new Draw(buffer), packedLight, villager);
	}
	//?}

	private void dress(PoseStack poseStack, Draw buffer, int packedLight, Villager villager) {
		if (villager.isInvisible()) {
			return;
		}
		layer = 0;
		VillagerData data = villager.getVillagerData();
		ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(com.vnap.Mc.profession(data));
		ResourceLocation[] gear = key.getNamespace().equals("minecraft") ? PROFESSION.get(key.getPath()) : null;
		if (!villager.isBaby() && villager instanceof VillagerLifeData look && look.vnap$female()) {
			ResourceLocation dress = key.getNamespace().equals("minecraft") ? DRESS.get(key.getPath()) : null;
			draw(poseStack, buffer, packedLight, villager, dress != null ? dress : PLAIN_DRESS);
			draw(poseStack, buffer, packedLight, villager, HAIR[style(villager)][hairShown(villager)]);
			onHead(poseStack, buffer, packedLight, HAIRDO[style(villager)], HAIRDO_TEXTURE[hairShown(villager)]);
			draw(poseStack, buffer, packedLight, villager, FACE);
		}
		if (villager.isBaby() && villager instanceof VillagerLifeData girl && girl.vnap$female()) {
			onHead(poseStack, buffer, packedLight, BOW, BOW_TEXTURE);
		}
		if (!villager.isBaby() && villager instanceof VillagerLifeData old && old.vnap$elder() && !old.vnap$female()) {
			draw(poseStack, buffer, packedLight, villager, ELDER_MAN);
		}
		VillagerLifeData life = villager instanceof VillagerLifeData found ? found : null;
		// a hero in its cuirass wears the Builders' surcoat and nothing of its trade or mood over it: one knight, not a patchwork
		ResourceLocation surcoat = com.vnap.client.spire.HeroLook.surcoat(villager);
		if (surcoat != null) {
			draw(poseStack, buffer, packedLight, villager, surcoat);
			com.vnap.client.spire.HeroLook.draw(getParentModel().root(), poseStack, buffer, packedLight, villager);
			return;
		}
		boolean mayor = !villager.isBaby() && life != null && life.vnap$mayor();
		boolean topHat = mayor && life != null && !life.vnap$female();
		if (mayor) draw(poseStack, buffer, packedLight, villager, MAYOR);
		if (topHat) draw(poseStack, buffer, packedLight, villager, MAYOR_HAT);
		boolean keeper = !villager.isBaby() && life != null && life.vnap$shopkeeper();
		boolean cap = keeper && life != null && !life.vnap$female();
		if (keeper) draw(poseStack, buffer, packedLight, villager, SHOPKEEPER);
		if (cap) draw(poseStack, buffer, packedLight, villager, SHOPKEEPER_CAP);
		boolean hatted = (gear != null && !villager.isBaby() && com.vnap.Mc.villagerLevel(villager.getVillagerData()) >= 5) || topHat || cap;
		if (gear != null && !villager.isBaby()) {
			int level = com.vnap.Mc.villagerLevel(villager.getVillagerData());
			if (level >= 2) {
				draw(poseStack, buffer, packedLight, villager, gear[0]);
			}
			if (level >= 3) {
				draw(poseStack, buffer, packedLight, villager, gear[1]);
			}
			if (level >= 5) {
				draw(poseStack, buffer, packedLight, villager, gear[2]);
			}
		}
		int spire = life == null || villager.isBaby() ? 0 : life.vnap$spireOutfit();
		if (spire > 0 && spire <= SPIRE.length) draw(poseStack, buffer, packedLight, villager, SPIRE[spire - 1]);
		float mood = moodNear(villager);
		for (int i = 0; i < BRIGHT.length; i++) {
			if (mood >= BRIGHT_AT[i] && !(hatted && i == BRIGHT.length - 1)) {
				draw(poseStack, buffer, packedLight, villager, BRIGHT[i]);
			}
		}
		for (int i = 0; i < GLOOM.length; i++) {
			if (mood <= GLOOM_AT[i] && !(hatted && i == 0)) {
				draw(poseStack, buffer, packedLight, villager, GLOOM[i]);
			}
		}
		if (mood >= 90.0F || mood <= -90.0F) {
			spark(villager, mood > 0);
		}
		com.vnap.client.spire.HeroLook.draw(getParentModel().root(), poseStack, buffer, packedLight, villager);
	}

	/** Each piece over the last: the dress, then the hair over it, and so on (from 1.21.9 an order of its own). */
	@SuppressWarnings("UnusedVariable") // read from 1.21.9
	private int layer;

	@SuppressWarnings("UnusedVariable") // the villager is drawn from its render state from 1.21.2
	private void draw(PoseStack poseStack, Draw buffer, int light, Villager villager, ResourceLocation texture) {
		layer++;
		//? if >=1.21.9 {
		/*renderColoredCutoutModel(getParentModel(), texture, poseStack, buffer.nodes(), light, current, -1, layer);
		*///?} else if >=1.21.2 {
		/*renderColoredCutoutModel(getParentModel(), texture, poseStack, buffer.buffers(), light, current, -1);
		*///?} else
		renderColoredCutoutModel(getParentModel(), texture, poseStack, buffer.buffers(), light, villager, -1);
	}

	private void onHead(PoseStack poseStack, Draw buffer, int light, ModelPart part, ResourceLocation texture) {
		poseStack.pushPose();
		getParentModel().getHead().translateAndRotate(poseStack);
		buffer.geometry(poseStack, McClient.entityCutoutNoCull(texture),
				(at, consumer) -> part.render(Draw.stack(at), consumer, light, OverlayTexture.NO_OVERLAY));
		poseStack.popPose();
	}

	private static ModelPart[] hairdos() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("braid", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-1.0F, -4.0F, 3.6F, 2.0F, 10.0F, 1.0F)
				.texOffs(8, 0).addBox(-1.5F, -8.0F, 3.6F, 3.0F, 3.0F, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("ponytail", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-1.0F, -7.0F, 4.4F, 2.0F, 10.0F, 1.0F)
				.texOffs(8, 0).addBox(-1.5F, -7.0F, 3.6F, 3.0F, 3.0F, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("bun", CubeListBuilder.create()
				.texOffs(8, 0).addBox(-1.5F, -10.0F, 3.2F, 3.0F, 3.0F, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("bow", CubeListBuilder.create()
				.texOffs(0, 0).addBox(1.5F, -2.5F, -1.0F, 6.0F, 4.0F, 2.0F)
				.texOffs(0, 8).addBox(-7.5F, -2.5F, -1.0F, 6.0F, 4.0F, 2.0F)
				.texOffs(0, 16).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offset(1.5F, -10.0F, -1.0F));
		ModelPart baked = LayerDefinition.create(mesh, 32, 32).bakeRoot();
		ModelPart bow = baked.getChild("bow");
		// a bow on top of the head, not one as wide as it
		bow.xScale = 0.4F;
		bow.yScale = 0.4F;
		bow.zScale = 0.4F;
		return new ModelPart[] {baked.getChild("braid"), baked.getChild("ponytail"), baked.getChild("bun"), bow};
	}

	/** Hair colour index, fixed by the UUID. */
	public static int hair(Villager villager) {
		return Math.floorMod(villager.getUUID().hashCode() >>> 3, 4);
	}

	/** The colour drawn: an elder's hair is grey whatever it was. */
	public static int hairShown(Villager villager) {
		return villager instanceof VillagerLifeData data && data.vnap$elder() ? GREY : hair(villager);
	}

	/** Hairstyle index (braid, ponytail, bun), fixed by the UUID. */
	public static int style(Villager villager) {
		return Math.floorMod(villager.getUUID().hashCode() >>> 7, STYLES.length);
	}

	private static float moodNear(Villager villager) {
		VillageMoodPayload mood = VillageClientState.lastMood;
		if (mood == null) {
			return 0.0F;
		}
		BlockPos meeting = mood.meetingPos();
		if (meeting == null || villager.distanceToSqr(meeting.getX() + 0.5, meeting.getY(), meeting.getZ() + 0.5) > REACH) {
			return 0.0F;
		}
		return mood.mood();
	}

	private static void spark(Villager villager, boolean light) {
		int tick = villager.tickCount;
		if (tick % 16 != 0 || LAST_SPARK.getOrDefault(villager.getId(), -1) == tick) {
			return;
		}
		LAST_SPARK.put(villager.getId(), tick);
		if (LAST_SPARK.size() > 256) {
			LAST_SPARK.clear();
		}
		double x = villager.getX() + (villager.getRandom().nextDouble() - 0.5) * 0.6;
		double y = villager.getY() + 1.1 + villager.getRandom().nextDouble() * 0.4;
		double z = villager.getZ() + (villager.getRandom().nextDouble() - 0.5) * 0.6;
		villager.level().addParticle(light ? ParticleTypes.END_ROD : ParticleTypes.SMOKE, x, y, z, 0.0, 0.02, 0.0);
	}

	private static ResourceLocation[] textures(String... names) {
		ResourceLocation[] out = new ResourceLocation[names.length];
		for (int i = 0; i < names.length; i++) {
			out[i] = VillagerLife.id("textures/entity/outfit/" + names[i] + ".png");
		}
		return out;
	}
}
