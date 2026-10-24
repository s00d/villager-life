package com.vnap.block;

import com.vnap.Nbt;
import com.vnap.mood.MoodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class MonumentBlockEntity extends BlockEntity {
    private boolean active;
    private final List<String> plaqueLines = new ArrayList<>();

    public MonumentBlockEntity(BlockPos pos, BlockState state) {
        super(LifeBlocks.MONUMENT_BE, pos, state);
        plaqueLines.add("plaque.villager_life.monument");
        plaqueLines.add("plaque.villager_life.empty");
        plaqueLines.add("");
        plaqueLines.add("");
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MonumentBlockEntity be) {
        if (!be.active || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getGameTime() % 20L != 0L) {
            return;
        }
        int radius = MoodConfig.monumentBuffRadius();
        AABB box = new AABB(pos).inflate(radius);
        for (Player player : serverLevel.getEntitiesOfClass(Player.class, box, p -> true)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false));
        }
        if (com.vnap.Mc.isNight(serverLevel)) {
            for (Villager villager : serverLevel.getEntitiesOfClass(Villager.class, box, v -> v.isAlive())) {
                villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false));
            }
        }
    }

    public void setActive(boolean active) {
        this.active = active;
        setChanged();
    }

    public boolean isActive() {
        return active;
    }

    public void setPlaqueLines(List<String> lines) {
        plaqueLines.clear();
        plaqueLines.addAll(lines);
        setChanged();
    }

    public List<String> plaqueLines() {
        return List.copyOf(plaqueLines);
    }

    @Override
    //? if >=1.21.6 {
    /*protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
    *///?} else {
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    //?}
        tag.putBoolean("active", active);
        ListTag list = new ListTag();
        for (String line : plaqueLines) {
            list.add(StringTag.valueOf(line));
        }
        tag.put("lines", list);
        //? if >=1.21.6
        /*com.vnap.Mc.put(output, tag);*/
    }

    @Override
    //? if >=1.21.6 {
    /*protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        CompoundTag tag = com.vnap.Mc.tagOf(input);
    *///?} else {
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    //?}
        active = Nbt.getBoolean(tag, "active");
        plaqueLines.clear();
        ListTag list = Nbt.getList(tag, "lines", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            plaqueLines.add(Nbt.getString(list, i));
        }
    }
}
