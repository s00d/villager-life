package com.vnap.client.golem;

import com.vnap.VillagerLife;
import com.vnap.client.model.BedrockModels;
import com.vnap.entity.golem.GolemAction;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Locale;
import java.util.Map;

/** The Tocsin Golem's clips from {@code animations/tocsin_golem.animation.json}: idle, walk and one per action. */
final class TocsinGolemAnimations {
    final AnimationDefinition idle;
    final AnimationDefinition walk;
    private final AnimationDefinition[] byAction = new AnimationDefinition[GolemAction.values().length];

    TocsinGolemAnimations(ResourceManager resources) {
        Map<String, AnimationDefinition> clips = BedrockModels.animations(resources, VillagerLife.id("tocsin_golem"));
        idle = require(clips, "idle");
        walk = require(clips, "walk");
        for (GolemAction action : GolemAction.values()) {
            if (action != GolemAction.NONE) byAction[action.id] = clips.getOrDefault(action.name().toLowerCase(Locale.ROOT), idle);
        }
    }

    private static AnimationDefinition require(Map<String, AnimationDefinition> clips, String name) {
        AnimationDefinition clip = clips.get(name);
        if (clip == null) throw new IllegalStateException("tocsin_golem.animation.json has no " + name);
        return clip;
    }

    AnimationDefinition forAction(int ordinal) {
        AnimationDefinition clip = byAction[ordinal];
        return clip != null ? clip : idle;
    }
}
