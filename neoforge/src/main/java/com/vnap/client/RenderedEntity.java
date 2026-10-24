package com.vnap.client;

//? if >=1.21.2 {
/*import com.vnap.VillagerLife;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.jspecify.annotations.Nullable;
*///?}

/**
 * From 1.21.2 a renderer draws a render state, not the entity; the mod's layers and render events read the villager
 * (its synced data, its UUID) from the entity this class keeps in each state. On 1.21.1 they get the entity itself.
 */
public final class RenderedEntity {
    private RenderedEntity() {
    }

    //? if >=1.21.2 {
    /*private static final ContextKey<Entity> ENTITY = new ContextKey<>(VillagerLife.id("entity"));

    public static void register(RegisterRenderStateModifiersEvent event) {
        // every renderer: the base renderer of an entity and its state stands for all of them (NeoForge applies it to subclasses)
        event.<Entity, EntityRenderState>registerEntityModifier(new com.google.common.reflect.TypeToken<EntityRenderer<Entity, EntityRenderState>>() {
        }, (entity, state) -> state.setRenderData(ENTITY, entity));
    }

    public static <T> @Nullable T of(EntityRenderState state, Class<T> type) {
        Entity entity = state.getRenderData(ENTITY);
        return type.isInstance(entity) ? type.cast(entity) : null;
    }
    *///?}
}
