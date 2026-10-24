package com.vnap.dialogue;

import com.vnap.config.LifeSettings;
import com.vnap.network.DialogueAnimationPayload;
import com.vnap.village.life.LifeBrain;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Saying a line: who talks now and until when, the cooldown book, "one voice in earshot", and keeping speaker and
 * listener turned to each other. Everything that makes a villager speak ends up in {@link #speak}.
 */
public final class Speech {
	/** Entities spawned by /dialoguetest: props of the stage, invisible to every reaction. */
	public static final String TEST_TAG = "vnap_dialogue_test";
	static final long SOON = 900L;
	static final long SELDOM = 3000L;
	private static final double EARSHOT = 16.0;
	/** Lines said on the way: the speaker keeps walking (hurt and panic lines, a parade of the ill). */
	private static final Set<String> ON_THE_MOVE = Set.of(("huhcbd gesjov gacgtq hmadgp nkcoqb qhpyaw uveohs caykki swomdw "
			+ "rtikom hfmwvf mytmrk ikrwzy fcbygh etkxko elryje rogpvp igebly vnaodx yzqpvi nsxmkr cifbit wyvzhk rueszy "
			+ "yjctyw qqyjjg hpnsfu vevdkl ahcvzd ecslqo ssbhiv ltdnvy uzdxum lpuocy slbqfwbayahw").split(" ", -1));

	private static long clock;
	private static String refusal = "";
	private static final Map<UUID, Talk> TALKING = new HashMap<>();
	private static final Map<UUID, Long> BUSY = new HashMap<>();
	private static final Map<UUID, Gaze> GAZE = new HashMap<>();
	private static final Map<String, Long> LAST_SAID = new HashMap<>();
	private static final Map<String, Deque<Integer>> HEARD = new HashMap<>();

	private record Talk(String group, long ends) {
	}

	/** Look at {@code target} (or {@code at}) until {@code until}; {@code still} also stands still. */
	private record Gaze(@Nullable UUID target, @Nullable Vec3 at, long until, boolean still) {
	}

	private Speech() {
	}

	static long now() {
		return clock;
	}

	static void advance() {
		clock++;
	}

	static boolean prop(Entity entity) {
		return com.vnap.Mc.tags(entity).contains(TEST_TAG);
	}

	// ---- public entry points -------------------------------------------------------------------------------

	/** A line by title in the speaker's own voice, by the village's systems (rites, family, town, mood). */
	public static boolean line(LivingEntity speaker, String title, String key, boolean seldom) {
		return title(speaker, title, key, seldom ? SELDOM : SOON, null);
	}

	/** A line said a little later, after whoever talks within earshot now (it waits its turn like any queued line). */
	public static boolean queue(LivingEntity speaker, String title, @Nullable Entity target, long delay) {
		if (!(speaker.level() instanceof ServerLevel level)) return false;
		DialogueCatalog.DialogueGroup group = Voices.forTitle(speaker, title);
		if (group == null) return false;
		SpeechQueue.add(level, speaker, group.id(), target, clock + Math.max(1L, delay), false);
		return true;
	}

	/** A villager's life line by id (house, workshop): once in a long while. */
	public static void lifeLine(Villager villager, String id) {
		say(villager, id, "life:" + villager.getUUID() + ":" + id, SELDOM, null);
	}

	/** Why the last line was not said: for /vnap say and the tests. */
	public static String lastRefusal() {
		return refusal;
	}

	/** What holds {@code entity} in a line now, for /vnap who; empty when nothing does. */
	public static String describe(Entity entity) {
		StringBuilder out = new StringBuilder();
		Talk talk = TALKING.get(entity.getUUID());
		if (talk != null && talk.ends() > clock) {
			DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(talk.group());
			out.append(" says '").append(group == null ? talk.group() : group.title()).append("' ").append(talk.ends() - clock).append('t');
		}
		Gaze gaze = GAZE.get(entity.getUUID());
		if (gaze != null && gaze.still() && gaze.until() > clock) out.append(" held ").append(gaze.until() - clock).append('t');
		long busy = BUSY.getOrDefault(entity.getUUID(), 0L) - clock;
		if (busy > 0) out.append(" busy ").append(busy).append('t');
		return out + SpeechQueue.describe(entity.getUUID());
	}

	/** /dialoguetest: one take, now, whatever the rules. Returns how long it runs. */
	public static long test(ServerLevel level, LivingEntity speaker, DialogueCatalog.DialogueGroup group, int take, Entity target) {
		hush(speaker);
		DialogueCatalog.DialogueVariant variant = group.variant(take);
		if (variant == null) return 0L;
		long length = VoicePitch.stretchTicks(variant.durationTicks(), VoicePitch.of(speaker));
		broadcast(speaker, group.id(), variant.index(), length);
		TALKING.put(speaker.getUUID(), new Talk(group.id(), clock + length));
		BUSY.put(speaker.getUUID(), clock + length + 10L);
		if (speaker instanceof Mob mob) turn(mob, target, target.getEyePosition(), clock + length, true);
		return length;
	}

	/** Cuts the speaker off: its client voice, face and the hold on it stop. */
	public static void hush(LivingEntity speaker) {
		if (!(speaker.level() instanceof ServerLevel)) return;
		UUID id = speaker.getUUID();
		TALKING.remove(id);
		BUSY.remove(id);
		GAZE.remove(id);
		broadcast(speaker, "", 0, 0L);
	}

	// ---- saying ---------------------------------------------------------------------------------------------

	static boolean say(LivingEntity speaker, String id, String key, long every, @Nullable Object toward) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(id);
		return group != null && speak(speaker, group, key, every, toward, false);
	}

	/** As {@link #say}, but any grown-up voice may take a villager's line. */
	static boolean sayAsAdult(LivingEntity speaker, String id, String key, long every, @Nullable Object toward) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(id);
		return group != null && speak(speaker, group, key, every, toward, true);
	}

	static boolean title(LivingEntity speaker, String title, String key, long every, @Nullable Object toward) {
		DialogueCatalog.DialogueGroup group = Voices.forTitle(speaker, title);
		if (group == null) return refuse("no group titled " + title + " for " + Voices.of(speaker));
		return speak(speaker, group, key, every, toward, false);
	}

	/** A villager's title, said by any grown-up (a woman in her own words). */
	static boolean titleAsAdult(LivingEntity speaker, String title, String key, long every, @Nullable Object toward) {
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byTitle(title, "villager");
		return group != null && speak(speaker, group, key, every, toward, true);
	}

	/**
	 * @param asked  the group the situation calls for; a woman says her own one instead, but the conditions and the
	 *               "is it playing" checks keep comparing against {@code asked}
	 * @param toward an Entity or a Vec3 to face while talking, or null
	 */
	static boolean speak(LivingEntity speaker, DialogueCatalog.DialogueGroup asked, String key, long every,
			@Nullable Object toward, boolean asAdult) {
		DialogueCatalog.DialogueGroup voiced = asked;
		if (Voices.woman(speaker) && asked.speaker().equals("villager")) {
			voiced = Voices.herLine(asked);
			if (voiced == null) return refuse("no woman's line for " + asked.title());
		}
		if (!(speaker.level() instanceof ServerLevel level)) return refuse("client side");
		String why = whyNot(level, speaker, asked, voiced, key, every, asAdult);
		if (why != null) return refuse(why);
		Deque<Integer> heard = HEARD.computeIfAbsent(voiced.id(), ignored -> new ArrayDeque<>());
		DialogueCatalog.DialogueVariant take = voiced.pick(LifeSettings.rareVoicelines(), new HashSet<>(heard));
		if (take == null) return false;
		long length = VoicePitch.stretchTicks(take.durationTicks(), VoicePitch.of(speaker));
		broadcast(speaker, voiced.id(), take.index(), length);
		UUID id = speaker.getUUID();
		TALKING.put(id, new Talk(asked.id(), clock + length));
		BUSY.put(id, clock + length + 10L);
		LAST_SAID.put(key, clock);
		LAST_SAID.put(voiceKey(speaker, voiced), clock);
		heard.remove(take.index());
		heard.addLast(take.index());
		while (heard.size() > Math.min(8, voiced.choices(LifeSettings.rareVoicelines()) - 1)) heard.pollFirst();
		if (speaker instanceof Villager villager) {
			if (!villager.isBaby()) SpeechQueue.echo(level, villager, voiced, length);
			Family.afterLine(level, villager, voiced, length);
		}
		if (speaker instanceof Mob mob) {
			Entity target = toward instanceof Entity entity ? entity : null;
			Vec3 at = target != null ? target.getEyePosition()
					: toward instanceof Vec3 v ? v : null;
			turn(mob, target, at, clock + length, !ON_THE_MOVE.contains(voiced.id()));
		}
		return true;
	}

	private static @Nullable String whyNot(ServerLevel level, LivingEntity speaker, DialogueCatalog.DialogueGroup asked,
			DialogueCatalog.DialogueGroup voiced, String key, long every, boolean asAdult) {
		boolean voice = Voices.writtenFor(speaker, voiced)
				|| (asAdult && Voices.grownUpVoice(speaker) && voiced.speaker().equals("villager"));
		if (!voice) return "wrong speaker " + Voices.of(speaker) + " for " + voiced.speaker();
		if (!level.getServer().tickRateManager().runsNormally()) return "ticks frozen";
		if (staged) return null;
		if (stageOnly) return "a scene is being set up";
		if (!LifeSettings.dialogueEnabled()) return "dialogue off";
		if (speaker instanceof Villager villager) {
			if (!Conditions.allows(villager, asked.id())) return "condition";
			if (villager.isSleeping() && !asked.id().equals("asqzby")) return "asleep";
		}
		if (busy(speaker)) return "busy";
		if (floorTaken(level, speaker)) return "floor taken";
		long wait = LifeSettings.scaleCooldown(every);
		// the caller's key names the situation; a woman's stand-in answers many of them (every "X at Work" is her one
		// "Woman at Work"), so the group she actually says keeps the same cooldown of its own
		if (!ready(key, wait) || !ready(voiceKey(speaker, voiced), wait)) return "cooldown";
		return null;
	}

	private static String voiceKey(LivingEntity speaker, DialogueCatalog.DialogueGroup voiced) {
		return "voice:" + speaker.getUUID() + ":" + voiced.id();
	}

	private static boolean refuse(String why) {
		refusal = why;
		return false;
	}

	private static void broadcast(LivingEntity speaker, String group, int take, long length) {
		// the players whose clients have this entity: a voice for one they do not have would wait in vain
		PacketDistributor.sendToPlayersTrackingEntity(speaker,
				new DialogueAnimationPayload(speaker.getUUID(), group, take, (int) length));
	}

	// ---- the book -------------------------------------------------------------------------------------------

	static boolean ready(String key, long every) {
		Long last = LAST_SAID.get(key);
		return last == null || clock - last >= every;
	}

	static void mark(String key) {
		LAST_SAID.put(key, clock);
	}

	static boolean busy(LivingEntity entity) {
		Talk talk = TALKING.get(entity.getUUID());
		return BUSY.getOrDefault(entity.getUUID(), 0L) > clock || (talk != null && talk.ends() > clock);
	}

	static long busyUntil(LivingEntity entity) {
		return BUSY.getOrDefault(entity.getUUID(), clock);
	}

	/** The group the entity is saying now (the one asked for), or null. */
	static @Nullable String saying(LivingEntity entity) {
		Talk talk = TALKING.get(entity.getUUID());
		return talk != null && talk.ends() > clock ? talk.group() : null;
	}

	static long endsAt(UUID id) {
		Talk talk = TALKING.get(id);
		return talk == null ? Long.MIN_VALUE : talk.ends();
	}

	/** A line asked for by hand (/vnap say !Title): not held back by another voice, being busy or a cooldown. */
	public static boolean staged;
	/** Only lines asked for by hand are said (/vnap stage on): a scene with no one else talking over it. In memory only. */
	public static boolean stageOnly;

	/**
	 * VOICES_AROUND others within earshot are talking already: a few voices at once sound like a village, more like a
	 * din (one at a time left a whole village silent while one villager spoke).
	 */
	static boolean floorTaken(ServerLevel level, LivingEntity speaker) {
		int talking = 0;
		for (Map.Entry<UUID, Talk> entry : TALKING.entrySet()) {
			if (entry.getValue().ends() <= clock || entry.getKey().equals(speaker.getUUID())) continue;
			Entity other = level.getEntity(entry.getKey());
			if (other != null && other.distanceToSqr(speaker) < EARSHOT * EARSHOT && ++talking >= VOICES_AROUND) return true;
		}
		return false;
	}

	/** Voices at most talking at once within earshot of one another. */
	static final int VOICES_AROUND = 3;

	/** The listener of a line: busy as long, and turned to the speaker. */
	static void listen(LivingEntity listener, Entity speaker, long length) {
		BUSY.put(listener.getUUID(), clock + length);
		if (listener instanceof Mob mob) turn(mob, speaker, speaker.getEyePosition(), clock + length, true);
	}

	private static void turn(Mob mob, @Nullable Entity target, @Nullable Vec3 at, long until, boolean still) {
		GAZE.put(mob.getUUID(), new Gaze(target == null ? null : target.getUUID(), at, until, still));
		keepTurned(mob, at, still);
	}

	// ---- each tick ------------------------------------------------------------------------------------------

	static void tick(MinecraftServer server) {
		TALKING.values().removeIf(talk -> talk.ends() <= clock);
		GAZE.entrySet().removeIf(entry -> {
			Gaze gaze = entry.getValue();
			if (gaze.until() <= clock) return true;
			for (ServerLevel level : server.getAllLevels()) {
				if (!(level.getEntity(entry.getKey()) instanceof Mob mob) || !mob.isAlive()) continue;
				Entity target = gaze.target() == null ? null : level.getEntity(gaze.target());
				keepTurned(mob, target != null && target.isAlive() ? target.getEyePosition() : gaze.at(), gaze.still());
				return false;
			}
			return true;
		});
	}

	/** Ticks frozen or dialogue turned off: everybody stops mid-word. */
	static void silenceAll(MinecraftServer server) {
		for (UUID id : Set.copyOf(TALKING.keySet())) {
			LivingEntity speaker = null;
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(id) instanceof LivingEntity living) speaker = living;
			}
			if (speaker != null) hush(speaker);
			else forget(id);
		}
	}

	static void prune() {
		LAST_SAID.values().removeIf(last -> last + 12000L < clock);
		BUSY.values().removeIf(until -> until < clock);
	}

	static void forget(UUID id) {
		TALKING.remove(id);
		BUSY.remove(id);
		GAZE.remove(id);
	}

	static void reset() {
		TALKING.clear();
		BUSY.clear();
		GAZE.clear();
		LAST_SAID.clear();
		HEARD.clear();
		clock = 0L;
	}

	// ---- turning to the listener --------------------------------------------------------------------------

	private static void keepTurned(Mob mob, @Nullable Vec3 at, boolean still) {
		// one about some business (a build, a road, a feast: LifeBrain.busy) talks as it goes: stopping it for every
		// word left builders and guests standing in a heap
		if (still && !(mob instanceof Villager villager && LifeBrain.busy(villager))) {
			mob.getNavigation().stop();
			mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			mob.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
			if (mob.onGround() && !mob.isPassenger()) mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
		}
		if (at == null) return;
		mob.getLookControl().setLookAt(at.x, at.y, at.z, 15.0F, 10.0F);
		double dx = at.x - mob.getX();
		double dz = at.z - mob.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (flat > 0.01) {
			float yaw = (float) Math.toDegrees(Mth.atan2(dz, dx)) - 90.0F;
			float neck = Mth.clamp(Mth.wrapDegrees(yaw - mob.yBodyRot), -65.0F, 65.0F);
			float body = ease(mob.yBodyRot, yaw - neck, 12.0F, 0.35F);
			mob.setYRot(body);
			mob.setYBodyRot(body);
			mob.setYHeadRot(ease(mob.getYHeadRot(), yaw, 15.0F, 0.4F));
		}
		float pitch = (float) -Math.toDegrees(Mth.atan2(at.y - mob.getEyeY(), flat));
		mob.setXRot(ease(mob.getXRot(), Mth.clamp(pitch, -90.0F, 90.0F), 10.0F, 0.3F));
	}

	/** A step toward {@code target}: a share of the way left, no less than a fifth of a degree, no more than {@code most}. */
	private static float ease(float from, float target, float most, float share) {
		float left = Math.abs(Mth.wrapDegrees(target - from));
		return Mth.approachDegrees(from, target, Mth.clamp(left * share, 0.2F, most));
	}
}
