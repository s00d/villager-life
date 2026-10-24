package com.vnap.client.dev;

import com.vnap.client.spire.SpireHud;
import com.vnap.network.SpireFloorPayload;
import com.vnap.spire.SpireBlocks;
import com.vnap.spire.SpirePortalBlock;
import com.vnap.spire.guard.SpireGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Spire floor after floor as a player climbs it: the floor's tablet, then its guards (whatever of the Spire's is
 * nearest; a boss's parts and little ones fall to that too), then the chest, then the onward portal. Gear of the floor's
 * chapter at +hone is put on by command at each floor (the climb checks the floors, not the gear's economy); a floor
 * not passed in time is logged with what was left and where, and the climb goes on from the next one.
 */
final class ClimbTask implements DevBot.Task {
	private static final String[] TIERS = {"root", "archive", "clockwork", "garden", "deep", "ice", "spore", "hive", "star", "summit"};
	private static final int STUCK = 300;
	private final int to;
	private final int hone;
	private final int limit;
	private final Vec3 gate;
	private int floor;
	private int ticks;
	private int lost;
	private int deaths;
	private int stucks;
	private int best;
	private int sinceBetter;
	private int waitingSince;
	private boolean noteDone;
	private boolean chestDone;
	private int chestTicks;
	private final Set<BlockPos> notes = new HashSet<>();
	private @Nullable BlockPos chest;
	private @Nullable BlockPos portal;
	private boolean scanned;
	private String goalKey = "";
	/** Guards the bot could not get at on this floor (by way of trying round them twice): each told in the log. */
	private final Set<java.util.UUID> unreachable = new HashSet<>();
	/** Tries round the same goal (a sidestep and a jump, the way looked for anew) before it is given up as out of reach. */
	private int tries;
	private int sidestep;
	/** Something the floor cannot be passed without is out of reach (the portal, every guard left): the floor ends. */
	private boolean giveUp;

	ClimbTask(DevBot bot, int from, int to, int hone, int seconds) {
		this.to = to;
		this.hone = hone;
		this.limit = seconds * 20;
		this.gate = bot.player().position();
		bot.log("climb " + from + ".." + to + " at +" + hone + ", " + seconds + " s a floor, entering from " + round(gate));
		bot.command("gamerule sendCommandFeedback false");
		bot.command("gamemode survival");
		// already on that floor (set up by hand for a fight): from here, not in again
		if (SpireHud.current().floor() == from) {
			arrived(bot, SpireHud.current());
			return;
		}
		enter(bot, from);
	}

	private void enter(DevBot bot, int next) {
		bot.command("execute in minecraft:overworld run tp @s " + gate.x + " " + gate.y + " " + gate.z);
		bot.command("vnap spire floor " + next);
		bot.command("vnap spire enter");
		floor = next;
		waitingSince = 0;
		// entering again starts the floor over: what it saw of the last slot (its portal, its scan) is stale
		ticks = 0;
	}

	@Override
	public void stopped(DevBot bot) {
		bot.command("gamerule sendCommandFeedback true");
	}

	@Override
	public boolean tick(DevBot bot) {
		if (bot.respawning()) {
			if (lost++ == 0) {
				deaths++;
				bot.log("  died on floor " + floor + " (" + deaths + ")");
				scanned = false;
			}
			return true;
		}
		lost = 0;
		SpireFloorPayload here = SpireHud.current();
		if (here.floor() != floor) {
			if (here.floor() > floor && ticks > 0) {
				arrived(bot, here);
				return floor <= to;
			}
			// on the way in, or between floors
			if (++waitingSince == 600) {
				bot.log("  floor " + floor + " never came (" + here.floor() + "): entering again");
				enter(bot, floor);
			}
			bot.release();
			return true;
		}
		waitingSince = 0;
		if (ticks == 0) arrived(bot, here);
		ticks++;
		// the floor's blocks once the player stands on it (not while still sinking through the last floor's portal)
		BlockPos far = portal;
		if (scanned && far != null && far.distSqr(bot.player().blockPosition()) > 120 * 120) scanned = false;
		if (!scanned && ticks > 40 && bot.player().onGround()) scan(bot);
		// the chest is set down only once the floor is cleared: look again then
		if (scanned && here.cleared() && chest == null && !chestDone && ticks % 40 == 0) scan(bot);
		bot.closeScreen();
		if (!giveUp && !here.cleared() && !unreachable.isEmpty() && scanned && nearestGuard(bot, unreachable) == null) giveUp = true;
		if (ticks > limit || giveUp) {
			left(bot, here);
			if (floor + 1 > to) return false;
			enter(bot, floor + 1);
			ticks = 0;
			return true;
		}
		boolean going = play(bot, here);
		if (ticks % 200 == 0) {
			// what it is about every ten seconds: a floor gone quiet in the log shows where it stands and at what
			SpireGuard aim = focus;
			bot.log(String.format(java.util.Locale.ROOT, "  ..%d s: %s at %s, %d/%d left, focus %s%s, hp %.0f", ticks / 20, doing, bot.player().blockPosition().toShortString(),
					here.guards(), here.total(), aim == null ? "none" : aim.look().body().model() + " " + aim.blockPosition().toShortString(),
					aim == null ? "" : String.format(java.util.Locale.ROOT, " %.1f off, sight %s", aim.distanceTo(bot.player()), bot.player().hasLineOfSight(aim)),
					bot.player().getHealth()));
		}
		return going;
	}

	/** What play() did last tick, for the log. */
	private String doing = "";

	private void arrived(DevBot bot, SpireFloorPayload here) {
		if (ticks > 0) bot.log("floor " + floor + ": PASSED in " + ticks / 20 + " s, deaths " + deaths + ", stuck " + stucks);
		floor = here.floor();
		ticks = 1;
		deaths = 0;
		stucks = 0;
		unreachable.clear();
		tries = 0;
		giveUp = false;
		best = Integer.MAX_VALUE;
		sinceBetter = 0;
		noteDone = false;
		chestDone = false;
		chestTicks = 0;
		scanned = false;
		notes.clear();
		chest = null;
		portal = null;
		focus = null;
		if (floor > to) return;
		// what a player would have eaten and drunk (and what a death takes off)
		if (!bot.player().hasEffect(net.minecraft.world.effect.MobEffects.SATURATION)) bot.command("effect give @s saturation infinite 1 true");
		if (!bot.player().hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)) bot.command("effect give @s regeneration infinite 0 true");
		String tier = TIERS[Math.min(9, (floor - 1) / 10)];
		for (String[] piece : new String[][] {{"weapon.mainhand", "sword"}, {"armor.head", "helmet"}, {"armor.chest", "chestplate"},
				{"armor.legs", "leggings"}, {"armor.feet", "boots"}}) {
			bot.command("item replace entity @s " + piece[0] + " with villager_life:spire_" + tier + "_" + piece[1] + "[custom_data={vnap_hone:" + hone + "}]");
		}
		bot.log("floor " + floor + ": " + here.theme() + ", " + (here.cleared() ? "cleared already" : here.guards() + "/" + here.total() + " guards"));
	}

	/** Where the floor's tablets, chest and onward portal stand (the slot round the player). */
	private void scan(DevBot bot) {
		Level level = bot.level();
		List<BlockPos> chests = new ArrayList<>();
		BlockPos centre = bot.player().blockPosition();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = -72; x <= 72; x++) {
			for (int z = -72; z <= 72; z++) {
				for (int y = -12; y <= 20; y++) {
					at.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
					BlockState state = level.getBlockState(at);
					if (state.is(SpireBlocks.NOTE)) notes.add(at.immutable());
					else if (state.is(SpireBlocks.CHEST)) chests.add(at.immutable());
					else if (state.is(SpireBlocks.PORTAL) && state.getValue(SpirePortalBlock.KIND) == SpirePortalBlock.Kind.ONWARD && portal == null) portal = at.immutable();
				}
			}
		}
		// the floor's chest stands before the onward portal; another is a hidden cache behind a cracked wall
		BlockPos way = portal;
		chest = way == null ? null : chests.stream().filter(c -> c.distSqr(way) < 100).min(Comparator.comparingDouble(c -> c.distSqr(way))).orElse(null);
		scanned = true;
		bot.log("  sees " + notes.size() + " tablet(s), chest " + (chest == null ? "none" : chest.toShortString()) + ", onward " + (portal == null ? "none" : portal.toShortString()));
	}

	private boolean play(DevBot bot, SpireFloorPayload here) {
		if (!scanned) return true;
		// one guard at a time, as a player picks one: the nearest changes every step as they move, and chasing whichever
		// is nearest threw the way away each time; one already in reach is struck all the same
		if (focus == null || !focus.isAlive() || focus.isRemoved() || focus.distanceToSqr(bot.player()) > 72 * 72) focus = nearestGuard(bot, unreachable);
		// a brood's hound is no focus: the one who brings them is
		if (brood && focus != null && focus.look().body() == com.vnap.spire.Genome.Body.HOUND) {
			SpireGuard own = bot.level().getEntitiesOfClass(SpireGuard.class, bot.player().getBoundingBox().inflate(72, 24, 72),
					guard -> guard.isAlive() && !unreachable.contains(guard.getUUID()) && guard.look().body() != com.vnap.spire.Genome.Body.HOUND && !guard.look().echo())
					.stream().min(Comparator.comparingDouble(guard -> guard.distanceToSqr(bot.player()))).orElse(null);
			if (own != null) focus = own;
		}
		SpireGuard nearest = nearestGuard(bot, unreachable);
		// one at its heels comes before the one it was going to, as a player turns on what is biting it
		if (nearest != null && focus != null && !nearest.getUUID().equals(focus.getUUID()) && nearest.distanceTo(bot.player()) < 6 && focus.distanceTo(bot.player()) > 10 && !brood) focus = nearest;
		// twenty seconds on the nearest and the floor's count stays: they are a summoner's brood, coming again as fast as
		// they fall (a bot killed a hound a second for minutes); a player goes for the one who brings them
		if (here.guards() != nearestLeft) {
			nearestLeft = here.guards();
			nearestSince = ticks;
			brood = false;
		}
		if (!brood && ticks - nearestSince > 400 && doing.startsWith("fighting the nearest")) {
			brood = true;
			bot.log("  the nearest are a brood (" + here.guards() + " left for 20 s): going for the floor's own");
		}
		if (nearest != null && bot.inReach(nearest) && (focus == null || !bot.inReach(focus)) && !(brood && focus != null)) {
			doing = String.format(java.util.Locale.ROOT, "fighting the nearest %s %.0f/%.0f at %s", nearest.getName().getString(), nearest.getHealth(), nearest.getMaxHealth(),
					nearest.blockPosition().toShortString());
			bot.fight(nearest);
			return true;
		}
		if (focus != null && bot.inReach(focus)) {
			doing = "fighting";
			bot.fight(focus);
			return true;
		}
		// past a brood: straight at the one who brings them while it is in sight, through its hounds (a path round them
		// kept the bot standing in a corner while they bit)
		if (brood && focus != null && straight(bot, focus) && bot.player().hasLineOfSight(focus) && focus.distanceTo(bot.player()) < 24) {
			doing = "charging the summoner";
			bot.approach(focus);
			if (nearest != null && bot.inReach(nearest)) bot.strike(nearest);
			return true;
		}
		// a cleared floor: what still stands is not the floor's (a brood, a straggler), struck only when it comes close
		Entity guard = here.cleared() ? null : focus;
		if (guard instanceof SpireGuard close && straight(bot, close) && bot.player().hasLineOfSight(guard) && guard.position().subtract(bot.player().position()).horizontalDistance() < 4.5
				&& Math.abs(guard.getY() - bot.player().getY()) < 2.5) {
			// close and in sight: straight at it, as a player steps in for the blow
			doing = "stepping in";
			bot.approach(guard);
			return true;
		}
		BlockPos tablet = noteDone ? null : notes.stream().min(Comparator.comparingDouble(n -> n.distSqr(bot.player().blockPosition()))).orElse(null);
		if (tablet != null) {
			if (bot.player().getEyePosition().distanceTo(Vec3.atCenterOf(tablet)) < 3.5) {
				bot.use(tablet);
				noteDone = true;
				bot.log("  read the tablet");
				return true;
			}
			goTo(bot, "tablet", tablet, cell -> cell.distSqr(tablet) <= 5);
			return true;
		}
		if (guard != null) {
			doing = "going to its guard";
			// a flyer hangs over the floor: under it, where a blow still reaches up to it
			BlockPos at = groundUnder(bot, guard.blockPosition());
			goTo(bot, "guard", "guard " + guard.getUUID(), at, cell -> Vec3.atBottomCenterOf(cell).add(0, 1.6, 0).distanceTo(guard.getBoundingBox().getCenter()) < 2.6
					|| (!guard.onGround() && Math.abs(cell.getX() - at.getX()) + Math.abs(cell.getZ() - at.getZ()) <= 1 && cell.getY() == at.getY()));
			return true;
		}
		if (!here.cleared()) {
			// guards the HUD counts that the client cannot see (far, unloaded): towards the floor's middle
			BlockPos far = portal != null ? portal : bot.player().blockPosition();
			doing = "looking for unseen guards";
			goTo(bot, "unseen guards", far, cell -> cell.distSqr(far) <= 4);
			return true;
		}
		BlockPos box = chest;
		if (!chestDone && box != null) {
			if (bot.player().getEyePosition().distanceTo(Vec3.atCenterOf(box)) < 3.5) {
				if (chestTicks++ == 0) bot.use(box);
				var menu = bot.player().containerMenu;
				if (chestTicks > 10 && !(menu instanceof net.minecraft.world.inventory.InventoryMenu) && bot.mc().gameMode != null) {
					for (int slot = 0; slot < 27 && slot < menu.slots.size(); slot++) {
						com.vnap.client.McClient.quickMove(bot.player(), menu.containerId, slot);
					}
				}
				if (chestTicks > 14) {
					chestDone = true;
					bot.log("  took the chest");
				}
				return true;
			}
			goTo(bot, "chest", box, cell -> cell.distSqr(box) <= 5);
			return true;
		}
		BlockPos onward = portal;
		if (onward == null) {
			scanned = false;
			return true;
		}
		goTo(bot, "onward", onward, cell -> cell.equals(onward) || cell.equals(onward.below()));
		return true;
	}

	/** The cell to stand in under a point: the first floor down from it (the point itself when nothing is under it). */
	private static BlockPos groundUnder(DevBot bot, BlockPos from) {
		for (int dy = 0; dy < 8; dy++) {
			BlockPos cell = from.below(dy);
			if (!bot.level().getBlockState(cell.below()).getCollisionShape(bot.level(), cell.below()).isEmpty()) return cell;
		}
		return from;
	}

	private void goTo(DevBot bot, String what, BlockPos aim, java.util.function.Predicate<BlockPos> goal) {
		goTo(bot, what, what + aim.getX() / 4 + "," + aim.getZ() / 4, aim, goal);
	}

	/**
	 * Towards a goal; {@code key} names it for the count of no progress (a new goal, another guard or the chest after
	 * the last guard, starts its own). A guard is one goal wherever it moves: keyed by where it was, a flyer drifting
	 * about reset the count for ever and the bot stood still for minutes.
	 */
	private void goTo(DevBot bot, String what, String key, BlockPos aim, java.util.function.Predicate<BlockPos> goal) {
		if (!key.equals(goalKey)) {
			goalKey = key;
			best = Integer.MAX_VALUE;
			sinceBetter = 0;
			sinceBest = 0;
			tries = 0;
		}
		if (sidestep > 0) {
			// round whatever it walked into, as a player does: a step aside with a jump, then the way anew
			sidestep--;
			var options = bot.mc().options;
			boolean left = (tries % 2) == 1;
			options.keyLeft.setDown(left);
			options.keyRight.setDown(!left);
			options.keyUp.setDown(sidestep > 10);
			options.keyJump.setDown(sidestep % 10 < 3);
			if (sidestep == 0) {
				bot.release();
				bot.forgetPath();
			}
			return;
		}
		if (!bot.walk(aim, goal)) {
			if (sinceBetter++ % 100 == 0) bot.log("  no way to " + what + " at " + aim.toShortString() + " from " + bot.player().blockPosition().toShortString());
		}
		int left = bot.pathLeft();
		// knocked about by a blow is no sign of a way that goes nowhere; but three times that long, blows or not, is (a
		// shooter's arrows reset the count for minutes while the bot stood still)
		if (bot.player().hurtTime > 0) sinceBetter = 0;
		if (left < best) {
			best = left;
			sinceBetter = 0;
			sinceBest = 0;
		} else if (++sinceBetter > STUCK || ++sinceBest > 3 * STUCK) {
			sinceBest = 0;
			stucks++;
			BlockPos me = bot.player().blockPosition();
			SpireGuard guard = nearestGuard(bot, Set.of());
			String why = guard == null ? "" : String.format(java.util.Locale.ROOT, " (nearest guard %s %.1f from the eyes, %s, sight %s)",
					guard.look().body().model(), bot.player().getEyePosition().distanceTo(guard.getBoundingBox().getCenter()),
					guard.onGround() ? "on the ground" : "in the air", bot.player().hasLineOfSight(guard));
			best = Integer.MAX_VALUE;
			sinceBetter = 0;
			if (++tries <= 2) {
				bot.log("  STUCK going to " + what + " at " + aim.toShortString() + " from " + me.toShortString() + ", " + left + " cells left" + why + ": round it (" + tries + ")");
				sidestep = 24;
				return;
			}
			// no teleports: what a player could not get at is a fault of the floor (or of the bot), there to be looked into
			SpireGuard chased = what.equals("guard") ? focus : null;
			bot.log("  UNREACHABLE " + (chased != null ? chased.look().body().model() + " " + chased.blockPosition().toShortString() : what + " " + aim.toShortString())
					+ " from " + me.toShortString() + ", " + left + " cells left" + why);
			tries = 0;
			if (chased != null) {
				unreachable.add(chased.getUUID());
				focus = null;
			} else if (what.equals("tablet")) {
				noteDone = true;
			} else if (what.equals("chest")) {
				chestDone = true;
			} else {
				giveUp = true;
			}
		}
	}

	private @Nullable SpireGuard focus;
	/**
	 * Straight at it is still worth it: five seconds on and no nearer by a block (glass, bars, a rail or a dais edge in
	 * sight but in the way; a bot stood minutes so), and the way goes round by the path for this guard.
	 */
	private boolean straight(DevBot bot, SpireGuard guard) {
		if (guard.getUUID().equals(chargeBlocked)) return false;
		double gap = guard.distanceTo(bot.player());
		if (!guard.getUUID().equals(chargeAt) || gap < chargeGap - 1.0) {
			chargeAt = guard.getUUID();
			chargeGap = gap;
			chargeSince = ticks;
		} else if (ticks - chargeSince > 100) {
			chargeBlocked = guard.getUUID();
			bot.log("  no nearer straight at the " + guard.look().body().model() + " for 5 s: round by the way");
			return false;
		}
		return true;
	}

	private int nearestLeft = -1;
	private java.util.@Nullable UUID chargeAt;
	private java.util.@Nullable UUID chargeBlocked;
	private double chargeGap;
	private int chargeSince;
	private int sinceBest;
	private int nearestSince;
	private boolean brood;

	private static @Nullable SpireGuard nearestGuard(DevBot bot, Set<java.util.UUID> skip) {
		AABB around = bot.player().getBoundingBox().inflate(72, 24, 72);
		List<SpireGuard> guards = new ArrayList<>(bot.level().getEntitiesOfClass(SpireGuard.class, around, guard -> guard.isAlive() && !skip.contains(guard.getUUID())));
		guards.removeIf(guard -> guard.look().echo());
		// a keeper with its own about it (the hydra's heads, the planets, the brood) is struck last: while they stand it
		// often takes nothing at all, and a bot hitting the cap ten minutes long was the floor's whole run
		guards.removeIf(guard -> guard.look().keeper() >= 0 && guards.stream().anyMatch(other -> other.look().keeper() < 0 && other.distanceToSqr(guard) < 24 * 24));
		Vec3 me = bot.player().position();
		// the small ones first, as a player clears the room before the boss: a keeper counts as twenty blocks further off
		return guards.stream().min(Comparator.comparingDouble(guard -> guard.position().distanceToSqr(me) + (guard.look().keeper() >= 0 ? 400.0 : 0.0)))
				.orElse(null);
	}

	private void left(DevBot bot, SpireFloorPayload here) {
		bot.log("floor " + floor + ": NOT PASSED in " + ticks / 20 + " s, " + here.guards() + "/" + here.total() + " guards left, tablet " + noteDone
				+ ", deaths " + deaths + ", stuck " + stucks + (giveUp ? ", GAVE UP: out of reach (" + unreachable.size() + " guards)" : ""));
		Vec3 me = bot.player().position();
		for (SpireGuard guard : bot.level().getEntitiesOfClass(SpireGuard.class, bot.player().getBoundingBox().inflate(72, 24, 72), Entity::isAlive)) {
			bot.log("  left: " + guard.look().body().model() + (guard.look().keeper() >= 0 ? " (boss)" : "") + " at " + round(guard.position()) + ", " + Math.round(guard.position().distanceTo(me))
					+ " away, " + Math.round(guard.getY() - me.y) + " high, " + Math.round(guard.getHealth()) + " hp");
		}
	}

	private static String round(Vec3 at) {
		return Math.round(at.x) + " " + Math.round(at.y) + " " + Math.round(at.z);
	}
}
