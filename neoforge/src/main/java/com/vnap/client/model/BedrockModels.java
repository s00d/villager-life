package com.vnap.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entity models made in Blockbench ("Bedrock Entity" / GeckoLib projects): {@code geo/<name>.geo.json} geometry and
 * {@code animations/<name>.animation.json} clips, baked into vanilla model parts and keyframe animations so the
 * renderers and EMF keep working on plain {@link net.minecraft.client.model.geom.ModelPart}s. Axes follow
 * Blockbench's own Bedrock and Java exporters: Bedrock is Y up with X mirrored, vanilla is Y down from 24 px.
 * Only box UV and numeric keyframes (no Molang) are read.
 */
public final class BedrockModels {
    private BedrockModels() {
    }

    /** The geometry {@code assets/<ns>/geo/<path>.geo.json}, read from the current resources (a pack may replace it). */
    public static LayerDefinition geometry(ResourceLocation id) {
        JsonObject root = read(Minecraft.getInstance().getResourceManager(), id.withPath(p -> "geo/" + p + ".geo.json"));
        JsonObject model = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = model.getAsJsonObject("description");
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        Map<String, float[]> pivots = new HashMap<>();
        // bones may name a parent listed after them
        List<JsonObject> bones = new ArrayList<>();
        for (JsonElement bone : model.getAsJsonArray("bones")) bones.add(bone.getAsJsonObject());
        while (!bones.isEmpty()) {
            int before = bones.size();
            bones.removeIf(bone -> addBone(mesh, parts, pivots, bone));
            if (bones.size() == before) throw new IllegalStateException(id + ": bone with a missing parent: " + bones.get(0).get("name"));
        }
        return LayerDefinition.create(mesh, description.get("texture_width").getAsInt(), description.get("texture_height").getAsInt());
    }

    private static boolean addBone(MeshDefinition mesh, Map<String, PartDefinition> parts, Map<String, float[]> pivots, JsonObject bone) {
        String name = bone.get("name").getAsString();
        String parentName = bone.has("parent") ? bone.get("parent").getAsString() : null;
        PartDefinition parent = parentName == null ? mesh.getRoot() : parts.get(parentName);
        float[] parentPivot = parentName == null ? new float[]{0.0F, 24.0F, 0.0F} : pivots.get(parentName);
        if (parent == null || parentPivot == null) return false;
        float[] pivot = vec(bone, "pivot", 0.0F);
        float[] rotation = vec(bone, "rotation", 0.0F);
        CubeListBuilder cubes = CubeListBuilder.create();
        List<JsonObject> turned = new ArrayList<>();
        if (bone.has("cubes")) {
            for (JsonElement element : bone.getAsJsonArray("cubes")) {
                JsonObject cube = element.getAsJsonObject();
                if (cube.has("rotation")) turned.add(cube);
                else box(cubes, cube, pivot);
            }
        }
        PartDefinition part = parent.addOrReplaceChild(name, cubes, pose(pivot, parentPivot, rotation));
        // a turned cube is its own part, as Blockbench's Java export does
        for (int i = 0; i < turned.size(); i++) {
            JsonObject cube = turned.get(i);
            float[] cubePivot = vec(cube, "pivot", 0.0F);
            CubeListBuilder single = CubeListBuilder.create();
            box(single, cube, cubePivot);
            part.addOrReplaceChild(name + "_r" + (i + 1), single, pose(cubePivot, pivot, vec(cube, "rotation", 0.0F)));
        }
        parts.put(name, part);
        pivots.put(name, pivot);
        return true;
    }

    private static PartPose pose(float[] pivot, float[] parentPivot, float[] degrees) {
        return PartPose.offsetAndRotation(pivot[0] - parentPivot[0], parentPivot[1] - pivot[1], pivot[2] - parentPivot[2],
                degrees[0] * Mth.DEG_TO_RAD, degrees[1] * Mth.DEG_TO_RAD, degrees[2] * Mth.DEG_TO_RAD);
    }

    private static void box(CubeListBuilder builder, JsonObject cube, float[] pivot) {
        if (!cube.get("uv").isJsonArray()) throw new IllegalStateException("per-face UV is not supported: " + cube);
        JsonArray uv = cube.getAsJsonArray("uv");
        float[] origin = vec(cube, "origin", 0.0F);
        float[] size = vec(cube, "size", 0.0F);
        boolean mirror = cube.has("mirror") && cube.get("mirror").getAsBoolean();
        float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0.0F;
        builder.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).mirror(mirror)
                .addBox(origin[0] - pivot[0], pivot[1] - origin[1] - size[1], origin[2] - pivot[2], size[0], size[1], size[2],
                        new CubeDeformation(inflate));
    }

    /** Every clip of {@code assets/<ns>/animations/<path>.animation.json}, by its name after the last dot. */
    public static Map<String, AnimationDefinition> animations(ResourceManager resources, ResourceLocation id) {
        JsonObject root = read(resources, id.withPath(p -> "animations/" + p + ".animation.json"));
        Map<String, AnimationDefinition> clips = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("animations").entrySet()) {
            String name = entry.getKey().substring(entry.getKey().lastIndexOf('.') + 1);
            clips.put(name, clip(entry.getValue().getAsJsonObject()));
        }
        return clips;
    }

    private static AnimationDefinition clip(JsonObject clip) {
        AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(clip.get("animation_length").getAsFloat());
        if (clip.has("loop") && clip.get("loop").getAsBoolean()) builder.looping();
        if (clip.has("bones")) {
            for (Map.Entry<String, JsonElement> bone : clip.getAsJsonObject("bones").entrySet()) {
                JsonObject channels = bone.getValue().getAsJsonObject();
                channel(builder, bone.getKey(), channels.get("rotation"), AnimationChannel.Targets.ROTATION, 0);
                channel(builder, bone.getKey(), channels.get("position"), AnimationChannel.Targets.POSITION, 1);
                channel(builder, bone.getKey(), channels.get("scale"), AnimationChannel.Targets.SCALE, 2);
            }
        }
        return builder.build();
    }

    private static void channel(AnimationDefinition.Builder builder, String bone, @Nullable JsonElement data, AnimationChannel.Target target, int kind) {
        if (data == null) return;
        List<Keyframe> keys = new ArrayList<>();
        if (data.isJsonObject()) {
            for (Map.Entry<String, JsonElement> key : data.getAsJsonObject().entrySet()) {
                float time = Float.parseFloat(key.getKey());
                JsonElement value = key.getValue();
                AnimationChannel.Interpolation lerp = AnimationChannel.Interpolations.LINEAR;
                if (value.isJsonObject()) {
                    JsonObject frame = value.getAsJsonObject();
                    if (frame.has("lerp_mode") && frame.get("lerp_mode").getAsString().equals("catmullrom")) lerp = AnimationChannel.Interpolations.CATMULLROM;
                    value = frame.has("post") ? frame.get("post") : frame.get("pre");
                }
                keys.add(new Keyframe(time, value(value, kind), lerp));
            }
        } else {
            keys.add(new Keyframe(0.0F, value(data, kind), AnimationChannel.Interpolations.LINEAR));
        }
        builder.addAnimation(bone, new AnimationChannel(target, keys.toArray(Keyframe[]::new)));
    }

    /** Bedrock values in vanilla terms, as Blockbench's vanilla Java animation export writes them. */
    private static org.joml.Vector3f value(JsonElement element, int kind) {
        JsonArray array = element.getAsJsonArray();
        float x = array.get(0).getAsFloat();
        float y = array.get(1).getAsFloat();
        float z = array.get(2).getAsFloat();
        return switch (kind) {
            case 0 -> KeyframeAnimations.degreeVec(-x, -y, z);
            case 1 -> KeyframeAnimations.posVec(-x, y, z);
            default -> KeyframeAnimations.scaleVec(x, y, z);
        };
    }

    private static float[] vec(JsonObject object, String key, float fallback) {
        if (!object.has(key)) return new float[]{fallback, fallback, fallback};
        JsonArray array = object.getAsJsonArray(key);
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static JsonObject read(ResourceManager resources, ResourceLocation file) {
        Resource resource = resources.getResource(file).orElseThrow(() -> new IllegalStateException("missing model " + file));
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }
}
