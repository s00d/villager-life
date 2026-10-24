package com.vnap.item;

import com.vnap.VillagerLife;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
//? if >=1.21.4 {
/*import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
*///?} else {
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.List;
//?}

import java.util.EnumMap;
import java.util.Map;

public final class ParishArmor {
    //? if >=1.21.4 {
    /*public static final ArmorMaterial MATERIAL = create();
    *///?} else
    public static final Holder<ArmorMaterial> MATERIAL = Holder.direct(create());

    private ParishArmor() {
    }

    //? if >=1.21.4 {
    /*private static ArmorMaterial create() {
        Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        for (ArmorType type : ArmorType.values()) {
            defense.put(type, 1);
        }
        defense.put(ArmorType.CHESTPLATE, 4);
        // durability as leather's, but the vestment is unbreakable (ParishVestmentItem), as on 1.21.1
        return new ArmorMaterial(5, defense, 12, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR,
                ResourceKey.create(EquipmentAssets.ROOT_ID, VillagerLife.id("parish")));
    }
    *///?} else {
    private static ArmorMaterial create() {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            defense.put(type, 1);
        }
        defense.put(ArmorItem.Type.CHESTPLATE, 4);
        return new ArmorMaterial(
                defense,
                12,
                SoundEvents.ARMOR_EQUIP_LEATHER,
                () -> Ingredient.of(net.minecraft.world.item.Items.LEATHER),
                List.of(new ArmorMaterial.Layer(VillagerLife.id("parish"))),
                0.0F,
                0.0F
        );
    }
    //?}
}
