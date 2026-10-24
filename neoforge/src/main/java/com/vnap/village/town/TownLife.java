package com.vnap.village.town;

import com.vnap.dialogue.Family;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.Workshops;
import com.vnap.sound.GolemSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the town's buildings are for, once built: the chapel's weekly service (the bells, the villagers in its pews)
 * and its weddings at the altar; noons and evenings at the tavern (some villagers go and sit at its tables, a word or
 * two is said there, a traveller may take a room: Travellers). Every 100 ticks
 * from Family.tickAll, only for towns whose bell is entity-ticking (somebody is there to see it).
 */
public final class TownLife {
	static final float SERVICE_AT = 0.43F;
	static final float TAVERN_AT = 0.49F;
	/** Noon: a few step into the tavern for a bite and a rest from the day's work. */
	static final float LUNCH_AT = 0.25F;
	/** A villager's own visits to the town's buildings: the hours for them, and the chance per building per look (100 ticks). */
	static final float VISITS_FROM = 0.05F;
	static final float VISITS_TO = 0.46F;
	static final float VISIT_CHANCE = 0.03F;
	/** Ticks a visitor stays; a tavern evening's and a service's guests stay longer. */
	static final float VISIT_STAY = 0.04F;
	/** Until when a tavern evening and a service hold their guests (fractions of the day). */
	static final float TAVERN_UNTIL = TAVERN_AT + 0.08F;
	static final float SERVICE_UNTIL = SERVICE_AT + 0.05F;
	static final float LUNCH_UNTIL = LUNCH_AT + 0.04F;
	/** In front of the chapel's altar, where a couple stands to be wed. */
	static final BlockPos ALTAR = new BlockPos(5, 1, 15);
	/** The middle of the chapel's nave, where its hymn sounds from. */
	static final BlockPos NAVE = new BlockPos(5, 3, 11);
	static final long MUSIC_EVERY = 1240L;

	private TownLife() {
	}

	/** Where a wedding is held: before the chapel's altar once the town has its chapel, else at the bell. */
	public static BlockPos weddingPlace(ServerLevel level, BlockPos bell) {
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), bell);
		if (network == null || !TownPlots.built(network, TownPlots.Kind.CHAPEL)) return bell;
		BlockPos altar = TownPlots.at(network, TownPlots.Kind.CHAPEL, ALTAR);
		return altar == null || !level.isLoaded(altar) ? bell : altar;
	}

	/** The chapel's bells ring (a wedding, a funeral, a service), from its tower; nothing without a built chapel. */
	public static void ringChapel(ServerLevel level, BlockPos bell, net.minecraft.sounds.SoundEvent sound) {
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), bell);
		if (network == null || !TownPlots.built(network, TownPlots.Kind.CHAPEL)) return;
		BlockPos tower = TownPlots.at(network, TownPlots.Kind.CHAPEL, new BlockPos(5, 9, 3));
		if (tower != null) level.playSound(null, tower, sound, SoundSource.BLOCKS, 3.0F, 1.0F);
	}

	public static void tick(ServerLevel level) {
		long day = Family.day(level);
		float time = Family.dayFraction(level);
		boolean service = day % TownRules.SERVICE_EVERY == 0 && time >= SERVICE_AT && time < SERVICE_AT + 0.02F;
		boolean evening = time >= TAVERN_AT && time < TAVERN_AT + 0.02F;
		boolean fairHours = time >= 0.08F && time < 0.45F;
		boolean morning = time >= 0.02F && time < 0.05F;
		boolean lunch = time >= LUNCH_AT && time < LUNCH_AT + 0.02F;
		boolean visits = time >= VISITS_FROM && time < VISITS_TO;
		for (Roads.Network network : Roads.get(level.getServer()).networks()) {
			if (!network.dimension.equals(level.dimension()) || !level.isPositionEntityTicking(network.bell)) continue;
			TownStory.tick(level, network);
			Music.tick(level, network);
			if (level.getGameTime() % TownRules.LITTER_EVERY < 100) Upkeep.litter(level, network);
		}
		if (!service && !evening && !fairHours && !morning && !lunch && !visits) return;
		for (Roads.Network network : Roads.get(level.getServer()).networks()) {
			if (!network.dimension.equals(level.dimension()) || !level.isPositionEntityTicking(network.bell)) continue;
			if (fairHours) fairMusic(level, network);
			if (visits && !service) visits(level, network, time, day % TownRules.SERVICE_EVERY == 0);
			if (morning && day - network.requestDay >= TownRules.REQUEST_EVERY) {
				network.requestDay = day;
				Roads.get(level.getServer()).setDirty();
				ask(level, network);
			}
			if (service && network.servedDay != day && TownPlots.built(network, TownPlots.Kind.CHAPEL)) {
				network.servedDay = day;
				gather(level, network, TownPlots.Kind.CHAPEL, 1.0F, SERVICE_UNTIL, null);
				// the hymn from the nave (the chapel's organ and its singers: no jukebox in a church)
				BlockPos nave = TownPlots.at(network, TownPlots.Kind.CHAPEL, NAVE);
				if (nave != null) level.playSound(null, nave, GolemSounds.HYMN_MUSIC, SoundSource.RECORDS, 1.0F, 1.0F);
				ringChapel(level, network.bell, GolemSounds.CHAPEL_BELLS);
			}
			if (evening && network.eveningDay != day && TownPlots.built(network, TownPlots.Kind.TAVERN)) {
				network.eveningDay = day;
				// not every evening for everyone: a few go, the rest stay about the bell
				gather(level, network, TownPlots.Kind.TAVERN, 0.4F, TAVERN_UNTIL, "Rests at the Tavern");
				// someone puts a record on in the tavern: its fireside tune, or a waltz
				BlockPos corner = musicCorner(level, network);
				if (corner != null) Music.play(level, network, corner, level.getRandom().nextBoolean() ? com.vnap.item.LifeItems.DISC_TAVERN
						: com.vnap.item.LifeItems.DISC_EVENING, com.vnap.village.life.Agenda.until(TAVERN_UNTIL));
				Travellers.arrive(level, network, day);
				tale(level, network);
			}
			if (lunch && network.lunchDay != day && TownPlots.built(network, TownPlots.Kind.TAVERN)) {
				network.lunchDay = day;
				gather(level, network, TownPlots.Kind.TAVERN, 0.15F, LUNCH_UNTIL, "Rests at the Tavern");
			}
		}
	}

	/** A letter to the town's neighbours telling of the newest errand on its board (Errands). */
	private static void ask(ServerLevel level, Roads.Network network) {
		Errands.Errand newest = null;
		for (Errands.Errand errand : Errands.of(level, network.bell)) {
			if (errand.taker == null && (newest == null || errand.posted > newest.posted)) newest = errand;
		}
		if (newest == null) return;
		com.vnap.village.post.Letters.toNeighbours(level, network.bell, newest.name, "request", newest.count, newest.shown().getHoverName());
	}

	/** The fair's day (the caravan's, Fair): music on the square while a player is about to hear it. */
	private static void fairMusic(ServerLevel level, Roads.Network network) {
		com.vnap.village.market.Shops.Shop shop = com.vnap.village.market.Shops.near(level, network.bell);
		if (shop == null || !com.vnap.village.market.Fair.today(level, shop)) return;
		long now = level.getGameTime();
		if (now - network.musicAt < MUSIC_EVERY) return;
		if (level.getNearestPlayer(network.bell.getX(), network.bell.getY(), network.bell.getZ(), 48.0, false) == null) return;
		network.musicAt = now;
		level.playSound(null, network.bell, GolemSounds.FAIR_MUSIC, SoundSource.RECORDS, 1.0F, 1.0F);
	}

	/**
	 * Now and then one of the town walks into one of its buildings on its own (Millénaire's and MCA's villagers' habit):
	 * to pray a while in the chapel, for the smell of the bakehouse, to the hall with a word for the mayor, for a drink
	 * at the tavern. A grown-up with time on its hands (no trade, or its work done), not minding a baby, not on its way
	 * somewhere; it says why it came once there, sits or looks about a while and goes on (its brain takes it elsewhere).
	 * The service's day is the town's day of rest (Breeze's Settlements' rest days): workers too, three times as often.
	 */
	private static void visits(ServerLevel level, Roads.Network network, float time, boolean restDay) {
		float chance = restDay ? VISIT_CHANCE * 3 : VISIT_CHANCE;
		for (TownPlots.Kind kind : TownPlots.Kind.values()) {
			if (!TownPlots.built(network, kind) || level.getRandom().nextFloat() >= chance) continue;
			BlockPos spot = switch (kind) {
				case CHAPEL, TAVERN -> {
					List<BlockPos> seats = seats(level, network, kind);
					yield seats.isEmpty() ? null : seats.get(level.getRandom().nextInt(seats.size()));
				}
				case BAKEHOUSE -> TownPlots.at(network, kind, Bakehouse.BAKER);
				// no visitors' words for these: the granary is the stall's, the school the children's mornings
				case GRANARY, SCHOOL, MARKET -> null;
			};
			String title = switch (kind) {
				case CHAPEL -> "Prays in the Chapel";
				case TAVERN -> "Rests at the Tavern";
				case BAKEHOUSE -> "Visits the Bakehouse";
				case GRANARY, SCHOOL, MARKET -> "";
			};
			if (spot != null) visit(level, network, spot, title, time, restDay, kind == TownPlots.Kind.CHAPEL ? "chapel" : kind == TownPlots.Kind.TAVERN ? "tavern" : "");
		}
		if (level.getRandom().nextFloat() < chance) {
			BlockPos hall = hallInside(level, network);
			if (hall != null) visit(level, network, hall, "Visits the Town Hall", time, restDay, "");
		}
	}

	private static void visit(ServerLevel level, Roads.Network network, BlockPos spot, String title, float time, boolean restDay, String place) {
		if (!level.isLoaded(spot)) return;
		boolean afterWork = restDay || time >= 0.375F;
		List<Villager> idle = Towns.people(level, network.bell, villager -> !villager.isBaby() && !villager.isSleeping() && !villager.isNoAi() && com.vnap.village.life.Agenda.top(level, villager) == null
				&& !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY) && (afterWork || !villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE))
				&& !com.vnap.dialogue.Infants.minding(level, villager));
		if (idle.isEmpty()) return;
		// by liking: the pious to the chapel, the sociable to the tavern, a homebody seldom anywhere
		double total = 0;
		for (Villager one : idle) total += com.vnap.dialogue.Temperament.liking(one, place);
		double r = level.getRandom().nextDouble() * total;
		Villager villager = idle.get(idle.size() - 1);
		for (Villager one : idle) {
			r -= com.vnap.dialogue.Temperament.liking(one, place);
			if (r <= 0) {
				villager = one;
				break;
			}
		}
		// it says why it came once there, stays a little while (half an hour of the day), goes on
		com.vnap.village.life.Agenda.add(villager, new com.vnap.village.life.Agenda.Item("visit", spot, com.vnap.village.life.Agenda.VISIT, 1, 0.5F,
				com.vnap.village.life.Agenda.until(Math.min(0.99F, time + VISIT_STAY)), title, null));
	}

	/** A spot inside the town's hall, two in from its door. */
	private static @org.jspecify.annotations.Nullable BlockPos hallInside(ServerLevel level, Roads.Network network) {
		for (com.vnap.village.home.HouseProject house : com.vnap.mood.VillageMoodSavedData.get(level).houses()) {
			if (!house.done || !Mayors.HALLS.contains(house.plan) || !house.bell.closerThan(network.bell, TownRules.SPAN)) continue;
			for (Facade.Opening door : Facade.doors(level, house)) return door.pos().relative(door.out().getOpposite(), 2);
		}
		return null;
	}

	/** A free floor cell in the tavern beside one of its seats, for a jukebox (null: none). */
	private static @org.jspecify.annotations.Nullable BlockPos musicCorner(ServerLevel level, Roads.Network network) {
		List<BlockPos> seats = seats(level, network, TownPlots.Kind.TAVERN);
		for (BlockPos seat : seats) {
			for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				BlockPos at = seat.relative(side);
				if (!seats.contains(at) && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
						&& level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) return at;
			}
		}
		return null;
	}

	/** A traveller at the tavern of an evening tells of the road (Travellers). */
	private static void tale(ServerLevel level, Roads.Network network) {
		for (Villager traveller : Towns.people(level, network.bell, Travellers::passing)) {
			com.vnap.dialogue.Speech.queue(traveller, "Traveller Tells of the Road", null, 900L);
			return;
		}
	}

	/** Grown-ups of the town walk to the building's seats (its chairs, its pews); each goes with {@code chance}. Those going. */
	static List<Villager> gather(ServerLevel level, Roads.Network network, TownPlots.Kind kind, float chance, float until, @org.jspecify.annotations.Nullable String say) {
		List<Villager> gone = new ArrayList<>();
		List<BlockPos> seats = seats(level, network, kind);
		if (seats.isEmpty()) return gone;
		Collections.shuffle(seats, new java.util.Random(level.getGameTime()));
		int i = 0;
		for (Villager villager : Towns.people(level, network.bell, villager -> !villager.isBaby() && !villager.isSleeping() && !villager.isNoAi()
				&& !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY))) {
			if (i >= seats.size()) break;
			if (Mayors.isMayor(villager) && kind == TownPlots.Kind.TAVERN) continue;
			String place = kind == TownPlots.Kind.CHAPEL ? "chapel" : kind == TownPlots.Kind.TAVERN ? "tavern" : "";
			if (villager.getRandom().nextFloat() >= chance * com.vnap.dialogue.Temperament.liking(villager, place) || Travellers.passing(villager)) continue;
			// the first two say a word when they sit down (a toast, the day's talk)
			com.vnap.village.life.Agenda.add(villager, new com.vnap.village.life.Agenda.Item(kind.name().toLowerCase(java.util.Locale.ROOT), seats.get(i++),
					com.vnap.village.life.Agenda.ROUTINE, 1, 0.55F, com.vnap.village.life.Agenda.until(until), gone.size() < 2 ? say : null, null));
			gone.add(villager);
		}
		return gone;
	}

	/** The seats of a building: the stairs on its ground floor that face into the room (chairs, pews), not its flights. */
	static List<BlockPos> seats(ServerLevel level, Roads.Network network, TownPlots.Kind kind) {
		TownPlots.Plot plot = TownPlots.plot(network, kind);
		List<BlockPos> out = new ArrayList<>();
		if (plot == null) return out;
		for (SchematicSpec.PlacedBlock block : Workshops.schematic(level, kind.plan).blocks) {
			if (block.rel().getY() != 1 || !(block.state().getBlock() instanceof StairBlock)) continue;
			// the stairs of a flight run along a wall (x 1 or 5-6 in the plans), chairs and pews stand in the room
			if (kind == TownPlots.Kind.TAVERN && block.rel().getX() == 1) continue;
			// the chapel's pews face the altar (its tower's flights and the altar's steps face elsewhere)
			if (kind == TownPlots.Kind.CHAPEL && block.state().getValue(StairBlock.FACING) != net.minecraft.core.Direction.NORTH) continue;
			out.add(BuildJob.at(plot.origin, plot.rotation, block.rel()));
		}
		return out;
	}
}
