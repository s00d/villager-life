package com.vnap.block;

import com.vnap.Nbt;
import com.vnap.mood.MoodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AltarBlockEntity extends BlockEntity {
    private AltarState altarState = AltarState.SMOULDERING;
    private float charge;
    private boolean readyAnnounced;

    public AltarBlockEntity(BlockPos pos, BlockState state) {
        super(LifeBlocks.ALTAR_BE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AltarBlockEntity be) {
        if (level.isClientSide()) {
            if (be.altarState == AltarState.SMOULDERING && level.getRandom().nextFloat() < MoodConfig.altarSmolderParticleChance()) {
                level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        0.0, 0.02, 0.0);
            } else if (be.altarState == AltarState.READY || be.altarState == AltarState.CHARGING) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                        0.0, 0.01, 0.0);
            } else if (be.altarState == AltarState.TAINTED) {
                level.addParticle(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        0.0, 0.01, 0.0);
            }
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (be.altarState == AltarState.SMOULDERING && serverLevel.getGameTime() % 40L == 0L) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    2, 0.15, 0.1, 0.15, 0.0);
        }
    }

    public void setAltarState(AltarState altarState) {
        this.altarState = altarState;
        setChanged();
    }

    public AltarState altarState() {
        return altarState;
    }

    public void setCharge(float charge) {
        this.charge = charge;
        setChanged();
    }

    public float charge() {
        return charge;
    }

    public boolean consumeReadyAnnounce() {
        if (readyAnnounced) {
            return false;
        }
        readyAnnounced = true;
        setChanged();
        return true;
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
        tag.putString("state", altarState.name());
        tag.putFloat("charge", charge);
        tag.putBoolean("readyAnnounced", readyAnnounced);
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
        altarState = AltarState.fromId(Nbt.getString(tag, "state"));
        charge = Nbt.getFloat(tag, "charge");
        readyAnnounced = Nbt.getBoolean(tag, "readyAnnounced");
    }
}
