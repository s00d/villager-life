package com.vnap.sound;

import com.vnap.VillagerLife;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/** The mod's own sound events (sounds.json, tools/art/gen_sounds.py): golem, forge, staff, turret, tower clock, machines. */
public final class GolemSounds {
    private static final List<SoundEvent> ALL = new ArrayList<>();

    public static final SoundEvent STEP = add("golem.step");
    public static final SoundEvent HURT = add("golem.hurt");
    public static final SoundEvent DEATH = add("golem.death");
    public static final SoundEvent TOLL = add("golem.toll");
    public static final SoundEvent CLANG = add("golem.clang");
    public static final SoundEvent SLAM = add("golem.slam");
    public static final SoundEvent STEAM = add("golem.steam");
    public static final SoundEvent CRACK = add("golem.crack");
    public static final SoundEvent AWAKEN = add("golem.awaken");
    public static final SoundEvent CHAIN = add("golem.chain");
    public static final SoundEvent CRAWLER = add("crawler.step");
    public static final SoundEvent FORGE_INGOT = add("forge.ingot");
    public static final SoundEvent FORGE_TOLL = add("forge.toll");
    public static final SoundEvent STAFF_TOLL = add("staff.toll");
    public static final SoundEvent TURRET_SHOT = add("turret.shot");
    public static final SoundEvent TURRET_LOAD = add("turret.load");
    public static final SoundEvent CLOCK_STRIKE = add("clock.strike");
    public static final SoundEvent CLOCK_WIND = add("clock.wind");
    public static final SoundEvent GOLEM_ROAR = add("golem.roar");
    public static final SoundEvent GOLEM_SWING = add("golem.swing");
    public static final SoundEvent SHOP_BUY = add("shop.buy");
    public static final SoundEvent SHOP_SELL = add("shop.sell");
    public static final SoundEvent WAGE = add("market.wage");
    public static final SoundEvent CARAVAN = add("market.caravan");
    public static final SoundEvent WEDDING = add("family.wedding");
    public static final SoundEvent STAFF_CHARGE = add("staff.charge");
    public static final SoundEvent STAFF_FIZZLE = add("staff.fizzle");
    public static final SoundEvent RITE_BRIGHT = add("rite.bright");
    public static final SoundEvent RITE_MOURN = add("rite.mourn");
    public static final SoundEvent RITE_DARK = add("rite.dark");
    public static final SoundEvent RITE_SHATTER = add("rite.shatter");
    public static final SoundEvent CHAPEL_BELLS = add("chapel.bells");
    public static final SoundEvent CHAPEL_TOLL = add("chapel.toll");
    public static final SoundEvent MAILBOX = add("post.mailbox");
    public static final SoundEvent LETTER = add("post.letter");
    public static final SoundEvent BOARD_PIN = add("board.pin");
    public static final SoundEvent WATCH_ALARM = add("watch.alarm");
    public static final SoundEvent DONATION = add("town.donation");
    public static final SoundEvent FAIR_MUSIC = add("music.fair");
    public static final SoundEvent FEAST_MUSIC = add("music.feast");
    public static final SoundEvent TAVERN_MUSIC = add("music.tavern");
    public static final SoundEvent HYMN_MUSIC = add("music.hymn");
    public static final SoundEvent EVENING_MUSIC = add("music.evening");
    public static final SoundEvent CART_ROLL = add("cart.roll");
    public static final SoundEvent CART_LOAD = add("cart.load");
    public static final SoundEvent DOOR_BELL = add("door_bell.ring");
    public static final SoundEvent POT_BUBBLE = add("pot.bubble");
    public static final SoundEvent SIGN_CREAK = add("sign.creak");
    /** A workstation at work and done, per machine id (Machines): machine.<id>.work / .done. */
    private static final java.util.Map<String, SoundEvent> MACHINE = new java.util.HashMap<>();

    static {
        for (String machine : new String[]{"greenhouse_box", "smokehouse", "dye_vat", "fish_trap", "reading_desk", "survey_table", "font",
                "tanning_vat", "kiln", "armor_bench", "grinding_wheel", "weapon_forge"}) {
            MACHINE.put(machine + ".work", add("machine." + machine + ".work"));
            MACHINE.put(machine + ".done", add("machine." + machine + ".done"));
        }
    }

    public static @org.jspecify.annotations.Nullable SoundEvent machine(String id, boolean done) {
        return MACHINE.get(id + (done ? ".done" : ".work"));
    }

    private GolemSounds() {
    }

    private static SoundEvent add(String path) {
        SoundEvent event = SoundEvent.createVariableRangeEvent(VillagerLife.id(path));
        ALL.add(event);
        return event;
    }

    public static void register(RegisterEvent.RegisterHelper<SoundEvent> helper) {
        for (SoundEvent event : ALL) {
            helper.register(com.vnap.Mc.id(event), event);
        }
    }
}
