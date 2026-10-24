package com.vnap.mood;

import com.vnap.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import org.jspecify.annotations.Nullable;

public final class MoodState {
    public final long villageId;
    public BlockPos meetingPos;
    public float standing;
    public float mood;
    public long lastHappyCrossTick;
    public long lastAngryCrossTick;
    public ScriptKind script = ScriptKind.NONE;
    public long scriptCooldownUntilTick;
    public @Nullable BlockPos monumentPos;
    public @Nullable BlockPos altarPos;
    /** Origin of the structure being raised, kept so a reload resumes on the same spot. */
    public @Nullable BlockPos scriptSite;
    public int flags;
    public String founderName = "";

    public MoodState(long villageId, BlockPos meetingPos) {
        this.villageId = villageId;
        this.meetingPos = meetingPos.immutable();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", villageId);
        tag.put("meeting", com.vnap.Mc.posTag(meetingPos));
        tag.putFloat("standing", standing);
        tag.putFloat("mood", mood);
        tag.putLong("happyCross", lastHappyCrossTick);
        tag.putLong("angryCross", lastAngryCrossTick);
        tag.putString("script", script.name());
        tag.putLong("cooldown", scriptCooldownUntilTick);
        if (monumentPos != null) {
            tag.put("monument", com.vnap.Mc.posTag(monumentPos));
        }
        if (altarPos != null) {
            tag.put("altar", com.vnap.Mc.posTag(altarPos));
        }
        if (scriptSite != null) {
            tag.put("site", com.vnap.Mc.posTag(scriptSite));
        }
        tag.putInt("flags", flags);
        tag.putString("founder", founderName == null ? "" : founderName);
        return tag;
    }

    public static MoodState load(CompoundTag tag) {
        long id = Nbt.getLong(tag, "id");
        BlockPos meeting = com.vnap.Mc.readPos(tag, "meeting").orElse(BlockPos.ZERO);
        MoodState state = new MoodState(id, meeting);
        state.standing = Nbt.getFloat(tag, "standing");
        state.mood = Nbt.getFloat(tag, "mood");
        state.lastHappyCrossTick = Nbt.getLong(tag, "happyCross");
        state.lastAngryCrossTick = Nbt.getLong(tag, "angryCross");
        state.script = ScriptKind.fromId(Nbt.getString(tag, "script"));
        state.scriptCooldownUntilTick = Nbt.getLong(tag, "cooldown");
        state.monumentPos = com.vnap.Mc.readPos(tag, "monument").orElse(null);
        state.altarPos = com.vnap.Mc.readPos(tag, "altar").orElse(null);
        state.scriptSite = com.vnap.Mc.readPos(tag, "site").orElse(null);
        state.flags = Nbt.getInt(tag, "flags");
        state.founderName = Nbt.getString(tag, "founder");
        return state;
    }

    public boolean hasMonument() {
        return MoodFlags.has(flags, MoodFlags.HAS_MONUMENT);
    }

    public boolean hasAltar() {
        return MoodFlags.has(flags, MoodFlags.HAS_ALTAR);
    }

    public boolean altarTainted() {
        return MoodFlags.has(flags, MoodFlags.ALTAR_TAINTED);
    }

    public boolean bossSlainOnce() {
        return MoodFlags.has(flags, MoodFlags.BOSS_SLAIN_ONCE);
    }

    public boolean scarred() {
        return MoodFlags.has(flags, MoodFlags.SCAR);
    }
}
