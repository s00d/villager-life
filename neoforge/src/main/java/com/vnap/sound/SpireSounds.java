package com.vnap.sound;

import com.vnap.VillagerLife;
import com.vnap.spire.Genome;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Spire's own sounds (fal ElevenLabs SFX v2 and Lyria, tools/art/sfx/batch5.tsv and batch6.tsv; the events in
 * tools/art/gen_sounds.py): the floors, the gear, every guard's voice, the keepers and their arts, the fight's music.
 */
public final class SpireSounds {
	private static final List<SoundEvent> ALL = new ArrayList<>();

	public static final SoundEvent GROW = add("spire.grow");
	public static final SoundEvent PORTAL_IN = add("spire.portal_in");
	public static final SoundEvent PORTAL_OUT = add("spire.portal_out");
	public static final SoundEvent FLOOR_CLEAR = add("spire.floor_clear");
	public static final SoundEvent CHEST = add("spire.chest");
	public static final SoundEvent SECRET_BREAK = add("spire.secret_break");
	public static final SoundEvent CACHE = add("spire.cache");
	public static final SoundEvent TABLET = add("spire.tablet");
	public static final SoundEvent HONE = add("spire.hone");
	public static final SoundEvent RAISE = add("spire.raise");
	public static final SoundEvent STAGE = add("spire.stage");
	public static final SoundEvent GUARD_HIT = add("spire.guard.hit");
	public static final SoundEvent GUARD_DEATH = add("spire.guard.death");
	public static final SoundEvent GUARD_TELL = add("spire.guard.tell");
	public static final SoundEvent GUARD_BOLT = add("spire.guard.bolt");
	public static final SoundEvent ELITE = add("spire.guard.elite");
	public static final SoundEvent KEEPER_APPEAR = add("spire.keeper.appear");
	public static final SoundEvent KEEPER_PHASE = add("spire.keeper.phase");
	public static final SoundEvent KEEPER_DEATH = add("spire.keeper.death");
	public static final SoundEvent KEEPER_STUN = add("spire.keeper.stun");
	public static final SoundEvent MARK_WARN = add("spire.mark.warn");
	public static final SoundEvent MARK_IMPACT = add("spire.mark.impact");
	public static final SoundEvent BEAM_CHARGE = add("spire.beam.charge");
	public static final SoundEvent BEAM_FIRE = add("spire.beam.fire");
	public static final SoundEvent VICTORY = add("spire.victory");
	public static final SoundEvent WYRM_BURROW = add("spire.wyrm.burrow");
	public static final SoundEvent WYRM_ERUPT = add("spire.wyrm.erupt");
	public static final SoundEvent PAGES = add("spire.pages");
	public static final SoundEvent INK = add("spire.ink");
	public static final SoundEvent CLOCK_STOP = add("spire.clock.stop");
	public static final SoundEvent CLOCK_REWIND = add("spire.clock.rewind");
	public static final SoundEvent ECHO = add("spire.echo");
	public static final SoundEvent WOOD_MORPH = add("spire.wood_morph");
	public static final SoundEvent ROOTS = add("spire.roots");
	public static final SoundEvent ICE_FORM = add("spire.ice_form");
	public static final SoundEvent FROST_BREATH = add("spire.frost_breath");
	public static final SoundEvent SPORES = add("spire.spores");
	public static final SoundEvent HYDRA = add("spire.hydra");
	public static final SoundEvent HONEY_SEAL = add("spire.honey_seal");
	public static final SoundEvent BEES = add("spire.bees");
	public static final SoundEvent GRAVITY = add("spire.gravity");
	public static final SoundEvent STARFALL = add("spire.starfall");
	public static final SoundEvent GLASS_SPLIT = add("spire.glass_split");
	public static final SoundEvent WALL_RISE = add("spire.wall_rise");
	public static final SoundEvent JOLT = add("spire.jolt");
	public static final SoundEvent DOOR_UNLOCK = add("spire.door_unlock");
	public static final SoundEvent DOOR_GRIND = add("spire.door_grind");
	public static final SoundEvent LEVER = add("spire.lever");
	public static final SoundEvent GLYPH = add("spire.glyph");
	public static final SoundEvent CHIME = add("spire.chime");
	public static final SoundEvent PUZZLE_SOLVED = add("spire.puzzle_solved");
	public static final SoundEvent PUZZLE_WRONG = add("spire.puzzle_wrong");
	public static final SoundEvent TRAP_CLICK = add("spire.trap_click");
	/** The megastructures (mega/): coming alive and going dark, the quarry's dig, a Waygate's field and crossing, a keystone set. */
	public static final SoundEvent MEGA_FORMED = add("mega.formed");
	public static final SoundEvent MEGA_BROKEN = add("mega.broken");
	public static final SoundEvent QUARRY_START = add("mega.quarry_start");
	public static final SoundEvent QUARRY_WORK = add("mega.quarry_work");
	public static final SoundEvent QUARRY_DONE = add("mega.quarry_done");
	public static final SoundEvent GATE_OPEN = add("mega.gate_open");
	public static final SoundEvent GATE_CROSS = add("mega.gate_cross");
	public static final SoundEvent KEYSTONE = add("mega.keystone");
	public static final SoundEvent SAWMILL_START = add("mega.sawmill_start");
	public static final SoundEvent SAWMILL_WORK = add("mega.sawmill_work");
	public static final SoundEvent SMELTER_START = add("mega.smelter_start");
	public static final SoundEvent SMELTER_WORK = add("mega.smelter_work");
	public static final SoundEvent CALM_START = add("mega.calm_start");
	public static final SoundEvent DOME_START = add("mega.dome_start");
	public static final SoundEvent WELL_START = add("mega.well_start");
	public static final SoundEvent WELL_GATHER = add("mega.well_gather");
	public static final SoundEvent DRAIN_START = add("mega.drain_start");
	public static final SoundEvent DRAIN_WORK = add("mega.drain_work");
	public static final SoundEvent SPIKES = add("spire.spikes");
	public static final SoundEvent DART = add("spire.dart");
	public static final SoundEvent GONG = add("spire.gong");
	public static final SoundEvent GEARS = add("spire.gears");
	public static final SoundEvent BLOOM = add("spire.bloom");
	public static final SoundEvent VALVE = add("spire.valve");
	public static final SoundEvent PUFF = add("spire.puff");
	public static final SoundEvent HONEY_DRIP = add("spire.honey_drip");
	public static final SoundEvent ORRERY = add("spire.orrery");
	public static final SoundEvent RESONATOR = add("spire.resonator");
	public static final SoundEvent PLATE = add("spire.plate");
	public static final SoundEvent FROST_BURST = add("spire.frost_burst");
	public static final SoundEvent DOOM = add("spire.doom");
	public static final SoundEvent STAR_MARK = add("spire.star_mark");
	public static final SoundEvent AFFLICT = add("spire.afflict");
	public static final SoundEvent CHALK = add("spire.chalk");
	public static final SoundEvent POWER = add("spire.power");
	public static final SoundEvent STEAM = add("spire.steam");
	public static final SoundEvent DIVE = add("spire.dive");
	public static final SoundEvent ROCKFALL = add("spire.rockfall");
	public static final SoundEvent WHISPER = add("spire.whisper");
	public static final SoundEvent MUSIC_KEEPER = add("music.spire_keeper");
	public static final SoundEvent MUSIC_TRIAL = add("music.spire_trial");
	public static final SoundEvent MUSIC_MIND = add("music.spire_mind");
	public static final SoundEvent MUSIC_FLOOR = add("music.spire_floor");
	/** Each guard body's voice: idle and attack (the parts of bosses keep their boss's). */
	private static final Map<Genome.Body, SoundEvent> IDLE = new EnumMap<>(Genome.Body.class);
	private static final Map<Genome.Body, SoundEvent> ATTACK = new EnumMap<>(Genome.Body.class);

	static {
		for (Genome.Body body : List.of(Genome.Body.SENTINEL, Genome.Body.HOUND, Genome.Body.VINE, Genome.Body.MOTH, Genome.Body.RAY, Genome.Body.WISP,
				Genome.Body.FISH)) {
			IDLE.put(body, add("spire." + body.model() + ".idle"));
			ATTACK.put(body, add("spire." + body.model() + ".attack"));
		}
	}

	private SpireSounds() {
	}

	public static @Nullable SoundEvent idle(Genome.Body body) {
		return IDLE.get(body);
	}

	public static @Nullable SoundEvent attack(Genome.Body body) {
		return ATTACK.get(body);
	}

	private static SoundEvent add(String path) {
		SoundEvent event = SoundEvent.createVariableRangeEvent(VillagerLife.id(path));
		ALL.add(event);
		return event;
	}

	public static void register(RegisterEvent.RegisterHelper<SoundEvent> helper) {
		for (SoundEvent event : ALL) helper.register(com.vnap.Mc.id(event), event);
	}
}
