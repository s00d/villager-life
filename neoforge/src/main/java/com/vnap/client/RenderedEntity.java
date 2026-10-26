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
        }, (entity, state) -> put(state, entity));
    }

    public static <T> @Nullable T of(EntityRenderState state, Class<T> type) {
        Entity entity = get(state);
        return type.isInstance(entity) ? type.cast(entity) : null;
    }
    *///?}
    // a render state's data: NeoForge's methods of the state, on Forge and Fabric the shim's (com.vnap.neo.client.RenderData)
    //? if >=1.21.2 && (forge || fabric) {
    /*private static void put(EntityRenderState state, Entity entity) {
        if (state instanceof com.vnap.neo.client.RenderData data) data.vnap$setRenderData(ENTITY, entity);
    }

    private static @Nullable Entity get(EntityRenderState state) {
        return state instanceof com.vnap.neo.client.RenderData data ? data.vnap$getRenderData(ENTITY) : null;
    }

    public static float partialTick(EntityRenderState state) {
        return state instanceof com.vnap.neo.client.RenderData data ? data.vnap$partialTick() : 0.0F;
    }
    *///?} else if >=1.21.2 {
    /*public static float partialTick(EntityRenderState state) {
        return state.partialTick;
    }

    private static void put(EntityRenderState state, Entity entity) {
        state.setRenderData(ENTITY, entity);
    }

    private static @Nullable Entity get(EntityRenderState state) {
        return state.getRenderData(ENTITY);
    }
    *///?}
}
