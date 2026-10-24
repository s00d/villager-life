package com.vnap.dialogue;

import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.MoodHooks;
import com.vnap.village.home.Mayors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Trading: the opening word by reputation, the goodbye, the thanks for a deal; the mayor's office; the wandering trader. */
public final class Trades {
	private static final Map<UUID, Session> OPEN = new HashMap<>();
	private static final Map<UUID, Boolean> TRADER_HIDDEN = new HashMap<>();

	private static final class Session {
		private final ServerLevel level;
		private final UUID merchant;
		private final long since;
		private boolean shown;
		private boolean dealt;

		private Session(ServerLevel level, UUID merchant, long since) {
			this.level = level;
			this.merchant = merchant;
			this.since = since;
		}
	}

	private Trades() {
	}

	/** A player clicked a merchant: its window may follow (or the merchant refuses, which is a line too). */
	static void clicked(ServerLevel level, Player player, Entity merchant) {
		OPEN.put(player.getUUID(), new Session(level, merchant.getUUID(), Speech.now()));
	}

	/** NeoForge's trade event: the player took a deal. */
	static void dealt(AbstractVillager merchant, Player player) {
		Session session = OPEN.get(player.getUUID());
		if (session != null && session.merchant.equals(merchant.getUUID())) session.dealt = true;
		if (!(merchant.level() instanceof ServerLevel level)) return;
		String thanks = null;
		if (merchant instanceof Villager villager) {
			MoodHooks.onTradeCompleted(level, villager, player);
			if (!Voices.mayorMan(villager)) thanks = "xmkwxd";
		} else if (merchant instanceof WanderingTrader) {
			thanks = "bvrbhy";
		}
		if (thanks != null) SpeechQueue.add(level, merchant, thanks, player, Math.max(Speech.now() + 1L, Speech.busyUntil(merchant) + 1L));
	}

	static void tick(MinecraftServer server) {
		OPEN.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Session session = entry.getValue();
			Entity merchant = session.level.getEntity(session.merchant);
			if (player != null && player.containerMenu instanceof MerchantMenu) {
				if (!session.shown) {
					session.shown = true;
					opened(merchant, player);
				}
				return false;
			}
			if (!session.shown && Speech.now() - session.since <= 20L) return false;
			if (!session.shown && player != null && merchant instanceof Villager villager) {
				String no = refusal(villager, player);
				if (no != null) Speech.say(villager, no, "trade_unavailable:" + villager.getUUID() + ":" + no, Speech.SOON, player);
			}
			if (session.shown) closed(merchant, player, session.dealt);
			return true;
		});
	}

	private static void opened(@org.jspecify.annotations.Nullable Entity merchant, ServerPlayer player) {
		if (merchant instanceof Villager villager) {
			String line = greeting(villager, player);
			Speech.say(villager, line, "trade_open:" + villager.getUUID() + ":" + line, Speech.SOON, player);
		} else if (merchant instanceof WanderingTrader trader) {
			Speech.say(trader, "yubpbb", "trade_open:" + trader.getUUID(), Speech.SOON, player);
		}
	}

	private static void closed(@org.jspecify.annotations.Nullable Entity merchant, @org.jspecify.annotations.Nullable ServerPlayer player,
			boolean dealt) {
		if (merchant instanceof Villager villager) {
			String line = Voices.mayorMan(villager) ? dealt ? "shrrya" : "bgzmea"
					: dealt ? "czvvwy" : Speech.now() % 2L == 0L ? "lilimm" : "laztau";
			Speech.say(villager, line, "trade_close:" + villager.getUUID(), 10L, player);
		} else if (merchant instanceof WanderingTrader trader) {
			Speech.say(trader, dealt ? "uzdvsi" : "erbcfn", "trade_close:" + trader.getUUID(), 10L, player);
		}
	}

	private static String greeting(Villager villager, Player player) {
		if (Voices.mayorMan(villager)) return Cast.MAYOR.trades();
		String no = refusal(villager, player);
		if (no != null) return no;
		int standing = villager.getPlayerReputation(player);
		if (standing < -225) return "xduuwm";
		if (standing < -75) return "qmdvft";
		if (standing >= 75) return "vlrsrn";
		return standing >= 25 ? "kuhvdv" : Cast.VILLAGER.trades();
	}

	/** Why a plain villager will not trade, as its line; null when it will. */
	private static @org.jspecify.annotations.Nullable String refusal(Villager villager, Player player) {
		if (Voices.mayorMan(villager)) return null;
		String trade = VillagerMoments.profession(villager);
		if (trade.equals("nitwit")) return "nukxsf";
		if (trade.equals("none")) return "nlbhku";
		if (villager.level() instanceof ServerLevel level && level.isRaided(villager.blockPosition())) return "klabhl";
		if (villager.getOffers().isEmpty()) return "zalmof";
		return villager.getPlayerReputation(player) <= -150 ? "lhdgsy" : null;
	}

	/**
	 * The mayor takes no trade and sells nothing; out of office it gets its old trade and offers back.
	 * Called on load, on election and resignation, and every look at a villager near a player.
	 */
	public static void mayorOffers(Villager villager) {
		if (!(villager instanceof VillagerLifeData data)) return;
		if (!Mayors.isMayor(villager)) {
			if (data.vnap$hasOriginalVillagerState()) data.vnap$restoreOriginalVillagerState();
			return;
		}
		data.vnap$captureOriginalVillagerState();
		if (!com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE)) {
			villager.setVillagerData(com.vnap.Mc.withLevel(com.vnap.Mc.withProfession(villager.getVillagerData(), VillagerProfession.NONE), 1));
		}
		villager.getOffers().clear();
	}

	/** The wandering trader near a player: hello, its invisibility trick, the rain, a word on the road. */
	static void wanderer(ServerLevel level, ServerPlayer player) {
		WanderingTrader trader = Near.closest(level.getEntitiesOfClass(WanderingTrader.class,
				AABB.ofSize(player.position(), 32.0, 16.0, 32.0), t -> t.isAlive() && t.hasLineOfSight(player)), player.position());
		if (trader == null) return;
		String id = trader.getUUID().toString();
		if (Speech.say(trader, "hxlyuc", "approach:" + player.getUUID() + ":" + id, Speech.SELDOM, player)) return;
		boolean hidden = trader.hasEffect(MobEffects.INVISIBILITY);
		boolean wasHidden = Boolean.TRUE.equals(TRADER_HIDDEN.put(trader.getUUID(), hidden));
		if (hidden) {
			long llamas = level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(trader.position(), 24, 12, 24),
					e -> e.isAlive() && Near.path(e).equals("trader_llama")).size();
			String line = wasHidden ? "dbzjqi" : llamas >= 2 ? "myajyt" : llamas == 1 ? "jkeahu" : "vggdrt";
			if (Speech.say(trader, line, "invisible:" + id + ":" + line, Speech.SELDOM, player)) return;
		}
		if (level.isRainingAt(trader.blockPosition()) && Speech.say(trader, "kxoqky", "rain:" + id, Speech.SELDOM, null)) return;
		if (Speech.now() % 100L == 0L && Near.moving(trader)) Speech.say(trader, "stqafd", "idle:" + id, Speech.SELDOM, null);
	}

	static void forget(UUID id) {
		OPEN.remove(id);
		OPEN.values().removeIf(session -> session.merchant.equals(id));
		TRADER_HIDDEN.remove(id);
	}

	static void clear() {
		OPEN.clear();
		TRADER_HIDDEN.clear();
	}
}
