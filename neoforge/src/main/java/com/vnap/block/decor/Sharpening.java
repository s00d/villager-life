package com.vnap.block.decor;

import com.vnap.Nbt;
import com.vnap.VillagerLife;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** A whetted edge: +1 damage for the next {@link #CHARGES} melee hits, kept in the weapon's custom data. */
public final class Sharpening {
    public static final int CHARGES = 20;
    private static final String KEY = "vnap_sharp";

    private Sharpening() {
    }

    public static int charges(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : Nbt.getInt(data.copyTag(), KEY);
    }

    public static void set(ItemStack stack, int charges) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (charges > 0) {
                tag.putInt(KEY, charges);
            } else {
                tag.remove(KEY);
            }
        });
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        }
    }

    static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof LivingEntity attacker) || !attacker.equals(event.getSource().getEntity())) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        int charges = charges(weapon);
        if (charges <= 0) {
            return;
        }
        event.setAmount(event.getAmount() + 1.0F);
        set(weapon, charges - 1);
    }

    static void onTooltip(ItemTooltipEvent event) {
        int charges = charges(event.getItemStack());
        if (charges > 0) {
            event.getToolTip().add(Component.translatable("tooltip." + VillagerLife.MOD_ID + ".sharpened", charges)
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
