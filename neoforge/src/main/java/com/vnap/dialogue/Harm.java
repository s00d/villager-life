package com.vnap.dialogue;

import com.vnap.network.HurtEffectNetwork;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Getting hurt and dying: the cry, the bystander's gasp, the panic after it. */
final class Harm {
	private static final String PANIC = "uzdxum";
	private static final String GUN = "Attacked with a Gun";
	/** A hurt line is not cut by the next hit; a wound that lasts (fire, cactus) says it again every time. */
	private static final Set<String> LOCKING = Set.of(("huhcbd gesjov gacgtq hmadgp nkcoqb qhpyaw uveohs caykki swomdw rtikom "
			+ "hfmwvf mytmrk ikrwzy fcbygh elryje rogpvp etkxko igebly vnaodx yzqpvi nsxmkr cifbit wyvzhk rueszy yjctyw "
			+ "qqyjjg hpnsfu vevdkl ahcvzd ecslqo ssbhiv ltdnvy onindz xemyaj yebifs").split(" ", -1));
	private static final Set<String> LASTING = Set.of("elryje", "rogpvp", "etkxko", "igebly", "vnaodx");
	private static final Map<String, String> BY_ATTACKER = Map.of("evoker", "nkcoqb", "pillager", "qhpyaw",
			"ravager", "uveohs", "vex", "caykki", "vindicator", "swomdw", "witch", "rtikom", "zoglin", "hfmwvf",
			"drowned", "mytmrk", "husk", "mytmrk");
	/** Falling things and fireworks, before who did it. */
	private static final List<Map.Entry<List<ResourceKey<DamageType>>, String>> FIRST = List.of(
			Map.entry(List.of(DamageTypes.FALLING_ANVIL), "yzqpvi"),
			Map.entry(List.of(DamageTypes.STALAGMITE, DamageTypes.FALLING_STALACTITE), "nsxmkr"),
			Map.entry(List.of(DamageTypes.FIREWORKS), "gesjov"));
	/** What hurt it, when nobody did. */
	private static final List<Map.Entry<List<ResourceKey<DamageType>>, String>> BY_KIND = List.of(
			Map.entry(List.of(DamageTypes.LIGHTNING_BOLT), "ikrwzy"),
			Map.entry(List.of(DamageTypes.EXPLOSION, DamageTypes.PLAYER_EXPLOSION), "fcbygh"),
			Map.entry(List.of(DamageTypes.LAVA), "elryje"),
			Map.entry(List.of(DamageTypes.IN_FIRE, DamageTypes.ON_FIRE, DamageTypes.CAMPFIRE, DamageTypes.HOT_FLOOR), "etkxko"),
			Map.entry(List.of(DamageTypes.CACTUS, DamageTypes.SWEET_BERRY_BUSH), "rogpvp"),
			Map.entry(List.of(DamageTypes.FREEZE), "igebly"),
			Map.entry(List.of(DamageTypes.IN_WALL, DamageTypes.CRAMMING), "vnaodx"),
			Map.entry(List.of(DamageTypes.FALL), "cifbit"));
	private static final Map<UUID, Long> FRIGHTENED = new HashMap<>();
	private static final Map<UUID, Integer> PLAYER_DEATHS = new HashMap<>();

	private Harm() {
	}

	static boolean locks(String group) {
		return LOCKING.contains(group);
	}

	/** Ticks since the villager was last hurt (huge when never). */
	static long sinceFright(UUID id) {
		Long at = FRIGHTENED.get(id);
		return at == null ? Long.MAX_VALUE : Speech.now() - at;
	}

	static void calmed(Villager villager) {
		FRIGHTENED.remove(villager.getUUID());
	}

	static void hurt(LivingEntity victim, DamageSource source, float amount) {
		if (Speech.prop(victim) || amount <= 0.0F || !(com.vnap.Mc.levelOf(victim) instanceof ServerLevel level)) return;
		golemFight(level, victim, source);
		gasp(level, victim);
		String saying = Speech.saying(victim);
		if (saying != null && LOCKING.contains(saying)) return;
		if (victim.isAlive() && (victim instanceof Villager || victim instanceof WanderingTrader)) {
			HurtEffectNetwork.send(level, victim, victim instanceof Villager v && v.isBaby());
		}
		Entity by = source.getEntity();
		if (victim instanceof WanderingTrader trader) {
			cry(trader, by instanceof Player ? "vevdkl" : "wyvzhk", "hurt:" + trader.getUUID(), source);
			return;
		}
		if (!(victim instanceof Villager villager)) return;
		FRIGHTENED.put(villager.getUUID(), Speech.now());
		String key = "hurt:" + villager.getUUID();
		if (villager.isBaby()) {
			cry(villager, by instanceof Player && by.equals(source.getDirectEntity()) ? "ahcvzd" : "ecslqo", key, source);
			return;
		}
		if (Voices.mayorMan(villager)) {
			cry(villager, by instanceof Player ? Cast.MAYOR.attack() : Cast.MAYOR.hurt(), key, source);
			return;
		}
		if (by instanceof Player player && Near.homeWithin(villager, villager.blockPosition(), 4)) {
			String now = Speech.saying(villager);
			if ("lpuocy".equals(now) || "slbqfwbayahw".equals(now)) return;
			Speech.hush(villager);
			attackedAtHome(level, villager, player);
			return;
		}
		if (by instanceof Player player && by.equals(source.getDirectEntity()) && ModFlavorMatchers.looksLikeGun(player.getMainHandItem())) {
			if (Speech.title(villager, GUN, "hurt_gun:" + villager.getUUID(), Speech.SOON, by)) panic(level, villager, by, 40L);
			return;
		}
		String line = line(source);
		if (cry(villager, line, key + ":" + line, source)) panic(level, villager, by, DialogueCatalog.durationTicks(line) + 2L);
	}

	/** After the first cry, once in a while: "run!" toward whoever did it. */
	private static void panic(ServerLevel level, Villager villager, @Nullable Entity by, long after) {
		String key = "panic:" + villager.getUUID();
		if (!Speech.ready(key, Speech.SOON)) return;
		Speech.mark(key);
		SpeechQueue.add(level, villager, PANIC, by, Speech.now() + after);
	}

	private static boolean cry(LivingEntity victim, String line, String key, DamageSource source) {
		long every = LASTING.contains(line) ? 1L : source.getEntity() == null ? Speech.SOON : DialogueCatalog.durationTicks(line) + 20L;
		if (line.equals(Speech.saying(victim)) || !Speech.ready(key, every)) return false;
		Speech.hush(victim);
		return victim instanceof WanderingTrader
				? Speech.sayAsAdult(victim, line, key, every, source.getEntity())
				: Speech.say(victim, line, key, every, source.getEntity());
	}

	private static String line(DamageSource source) {
		String direct = source.getDirectEntity() == null ? "" : Near.path(source.getDirectEntity());
		String thrown = switch (direct) {
			case "arrow" -> "huhcbd";
			case "snowball" -> "dlrxes";
			default -> null;
		};
		if (thrown != null) return thrown;
		for (Map.Entry<List<ResourceKey<DamageType>>, String> kind : FIRST) {
			if (kind.getKey().stream().anyMatch(source::is)) return kind.getValue();
		}
		if (direct.equals("firework_rocket")) return "gesjov";
		if (direct.endsWith("potion")) {
			boolean magic = List.of(DamageTypes.MAGIC, DamageTypes.INDIRECT_MAGIC).stream().anyMatch(source::is);
			return magic ? "gacgtq" : "hmadgp";
		}
		Entity by = source.getEntity();
		if (by instanceof Player player && by.equals(source.getDirectEntity())) {
			String tool = Near.path(player.getMainHandItem());
			return tool.endsWith("_sword") ? "rueszy" : tool.endsWith("_axe") ? "yjctyw" : tool.endsWith("_hoe") ? "qqyjjg"
					: tool.endsWith("_shovel") ? "hpnsfu" : "vevdkl";
		}
		String attacker = by == null ? "" : Near.path(by);
		String known = attacker.contains("zombie") ? "mytmrk" : BY_ATTACKER.get(attacker);
		if (known != null) return known;
		for (Map.Entry<List<ResourceKey<DamageType>>, String> kind : BY_KIND) {
			if (kind.getKey().stream().anyMatch(source::is)) return kind.getValue();
		}
		return "wyvzhk";
	}

	/** A player hit a villager by its own home: a neighbour shouts, the victim answers; alone, it shouts itself. */
	private static void attackedAtHome(ServerLevel level, Villager villager, Player player) {
		Villager neighbour = Near.villager(level, villager.position(), 10.0,
				other -> !other.equals(villager) && Near.awakeAdultSees(other, villager));
		if (neighbour != null && Speech.sayAsAdult(neighbour, "slbqfwswxeva", "home_witness:" + neighbour.getUUID(), 20L, villager)) {
			SpeechQueue.add(level, villager, "slbqfwbayahw", neighbour,
					Speech.now() + DialogueCatalog.durationTicks("slbqfwswxeva") + 2L);
		} else {
			Speech.say(villager, "lpuocy", "attacked_home:" + villager.getUUID(), 20L, player);
		}
	}

	private static void gasp(ServerLevel level, LivingEntity victim) {
		Villager witness = Near.villager(level, victim.position(), 16.0,
				v -> !v.equals(victim) && Near.awakeAdultSees(v, victim) && !Speech.busy(v));
		if (witness != null) Speech.sayAsAdult(witness, "pkvhpv", "witness_hurt:" + witness.getUUID(), Speech.SOON, victim);
	}

	/** A player and the iron golem going at each other in sight of the village. */
	private static void golemFight(ServerLevel level, LivingEntity victim, DamageSource source) {
		Entity by = source.getEntity();
		ServerPlayer player = Near.path(victim).equals("iron_golem") && by instanceof ServerPlayer p ? p
				: victim instanceof ServerPlayer hit && by != null && Near.path(by).equals("iron_golem") ? hit : null;
		if (player == null || player.isCreative()) return;
		Villager witness = Near.villager(level, victim.position(), 16.0, v -> Near.awakeAdultSees(v, player) && !Speech.busy(v));
		if (witness != null) Speech.sayAsAdult(witness, "qffeco", "golem_attack:" + witness.getUUID(), Speech.SOON, player);
	}

	static void died(LivingEntity dead, DamageSource source) {
		if (Speech.prop(dead)) return;
		Speech.hush(dead);
		if (!(com.vnap.Mc.levelOf(dead) instanceof ServerLevel level)) return;
		if (dead instanceof Villager villager) {
			if (villager.isBaby()) Speech.say(villager, "ecslqo", "death:" + villager.getUUID(), 1L, null);
			else Speech.sayAsAdult(villager, "hivgme", "death:" + villager.getUUID(), 1L, null);
		}
		if (Near.path(dead).equals("iron_golem") && source.getEntity() instanceof Player killer) {
			Villager witness = Near.villager(level, dead.position(), 16.0, v -> Near.awakeAdultSees(v, dead));
			if (witness != null) Speech.title(witness, "Kill a Villager's Iron Golem", "golem_kill:" + witness.getUUID(), Speech.SOON, killer);
		}
		if (dead instanceof Villager || dead instanceof WanderingTrader) {
			Villager witness = Near.villager(level, dead.position(), 16.0,
					v -> !v.equals(dead) && !v.isBaby() && !v.isSleeping());
			if (witness != null) Speech.sayAsAdult(witness, "pmqrpb", "witness_death:" + witness.getUUID(), Speech.SOON, dead);
		} else if (dead instanceof ServerPlayer player) {
			int times = PLAYER_DEATHS.merge(player.getUUID(), 1, Integer::sum);
			String line = level.getLevelData().isHardcore() ? "elcjbb" : times > 1 ? "dxcjqn" : "hzjycq";
			Villager witness = Near.villager(level, player.position(), 16.0, v -> Near.awakeAdultSees(v, player));
			if (witness != null) Speech.sayAsAdult(witness, line, "player_death:" + witness.getUUID() + ":" + times, Speech.SOON, player);
		}
	}

	/** A player's deaths are kept until it logs out (it leaves its level on respawn too). */
	static void forgetPlayer(UUID id) {
		PLAYER_DEATHS.remove(id);
	}

	static void forget(UUID id) {
		FRIGHTENED.remove(id);
	}

	static void clear() {
		FRIGHTENED.clear();
		PLAYER_DEATHS.clear();
	}
}
