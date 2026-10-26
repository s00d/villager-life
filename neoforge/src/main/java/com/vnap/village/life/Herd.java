package com.vnap.village.life;

import com.vnap.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;

import java.util.List;

/**
 * Animals belong to one workshop: a {@value #TAG} tag and the workshop anchor in their persistent data. Only a
 * workshop's own adults are bred and culled; named animals, animals on a lead and young ones never are. A calf
 * of two herd animals joins the herd.
 */
public final class Herd {
    public static final String TAG = "vnap_herd";
    public static final String OWNER = "vnap_owner";
    public static final int MIN = 2;
    public static final int MAX = 6;

    private Herd() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BabyEntitySpawnEvent.class, (BabyEntitySpawnEvent event) -> {
            Mob parent = event.getParentA();
            if (event.getChild() instanceof Animal child && com.vnap.Mc.tags(parent).contains(TAG)) {
                adopt(child, BlockPos.of(Nbt.getLong(parent.getPersistentData(), OWNER)));
            }
        });
    }

    public static void adopt(Animal animal, BlockPos anchor) {
        animal.addTag(TAG);
        animal.getPersistentData().putLong(OWNER, anchor.asLong());
        animal.setPersistenceRequired();
    }

    public static boolean owned(Animal animal, BlockPos anchor) {
        return com.vnap.Mc.tags(animal).contains(TAG) && Nbt.getLong(animal.getPersistentData(), OWNER) == anchor.asLong();
    }

    /** May never be harmed or taken by a villager. */
    public static boolean protectedAnimal(Animal animal) {
        return animal.hasCustomName() || animal.isLeashed() || animal.isBaby();
    }

    public static boolean free(Animal animal) {
        return !com.vnap.Mc.tags(animal).contains(TAG) && !animal.hasCustomName() && !animal.isLeashed() && !animal.isBaby();
    }

    public static <T extends Animal> List<T> of(Life life, Class<T> kind) {
        return life.level.getEntitiesOfClass(kind, new AABB(life.state.anchor).inflate(Life.RADIUS + 4.0),
                animal -> animal.isAlive() && owned(animal, life.state.anchor));
    }
}
