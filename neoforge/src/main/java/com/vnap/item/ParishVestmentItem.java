package com.vnap.item;

import com.vnap.effect.ParishHymnEffect;
import com.vnap.mood.MoodState;
import com.vnap.mood.VillageMoodApi;
import com.vnap.mood.VillageMoodSavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
//? if <1.21.5
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

//? if >=1.21.5 {
/*public class ParishVestmentItem extends net.minecraft.world.item.Item {
*///?} else
public class ParishVestmentItem extends ArmorItem {
    public ParishVestmentItem(Properties properties) {
        //? if >=1.21.5 {
        /*super(properties.humanoidArmor(ParishArmor.MATERIAL, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE)
                .component(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE));
        *///?} else if >=1.21.4 {
        /*super(ParishArmor.MATERIAL, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE,
                properties.component(net.minecraft.core.component.DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(false)));
        *///?} else
        super(ParishArmor.MATERIAL, Type.CHESTPLATE, properties);
    }

    @Override
    //? if >=1.21.5 {
    /*public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level, Entity entity, net.minecraft.world.entity.@org.jspecify.annotations.Nullable EquipmentSlot slot) {
    *///?} else
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (player.getItemBySlot(EquipmentSlot.CHEST) != stack || player.tickCount % 40 != 0) {
            return;
        }
        VillageMoodApi.VillageRef ref = VillageMoodApi.resolve(com.vnap.Mc.serverLevel(player), player.blockPosition());
        if (ref == null) {
            return;
        }
        MoodState mood = VillageMoodSavedData.get(com.vnap.Mc.serverLevel(player)).get(ref.villageId());
        if (mood == null) {
            return;
        }
        player.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ParishHymnEffect.INSTANCE), 80, 0, true, false, true));
    }
}
