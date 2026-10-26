package com.vnap.village.town;

import com.vnap.Nbt;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.dialogue.VillagerHome;
import com.vnap.mood.VillageMoodApi;
import com.vnap.network.ErrandPayload;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Coins;
import com.vnap.village.market.Market;
import com.vnap.village.market.Prices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Errands: what the town's people need that they could not get for themselves, posted on the notice board for a player
 * to bring (as the job boards of MineColonies and the quests of Millénaire): a household's wants it could not afford or
 * the stall had sold out of (Market.obtain), food for an empty pantry, the thing a groom wishes for his wedding. A player
 * takes one errand at a time at the board (taking another puts the first back), carries the thing to the villager (a
 * hint on the screen points the way) and hands it over: paid by the household, or by the treasury when it cannot,
 * and the village is glad of it. An errand nobody brought in ERRAND_DAYS is taken down. One place for all of it: the
 * wedding wish is an errand too, the board's letters to the neighbours tell of them.
 */
public final class Errands extends SavedData {
	private static final String NAME = "villager_life_errands";
	/** Open errands a town keeps on its board at most. */
	static final int OPEN = 6;
	/** Days an errand stays up. */
	static final int DAYS = 5;
	/** Ticks between updates of the hint on the screen of a player on an errand. */
	static final int HINT_EVERY = 40;
	public static final String WANT = "want";
	public static final String FOOD = "food";
	public static final String WISH = "wish";

	private final List<Errand> errands = new ArrayList<>();
	private int nextId = 1;

	/** One errand: who wants what (an item's id or Market.FOOD), how many, the reward, who carries it. */
	public static final class Errand {
		public final int id;
		public final ResourceKey<Level> dimension;
		public final BlockPos bell;
		public final UUID villager;
		public final String name;
		public final String want;
		public final int count;
		public final int reward;
		public final String kind;
		public final long posted;
		public @Nullable UUID taker;
		/** Where the villager was last seen (the hint points there while it is out of sight). */
		public BlockPos seen;

		Errand(int id, ResourceKey<Level> dimension, BlockPos bell, UUID villager, String name, String want, int count, int reward, String kind,
			   long posted, BlockPos seen) {
			this.id = id;
			this.dimension = dimension;
			this.bell = bell.immutable();
			this.villager = villager;
			this.name = name;
			this.want = want;
			this.count = count;
			this.reward = reward;
			this.kind = kind;
			this.posted = posted;
			this.seen = seen.immutable();
		}

		/** What the board and the hint show: the item (any food shows as bread), its count. */
		public ItemStack shown() {
			return new ItemStack(item(want), count);
		}

		/** The stack in hand will do: the item itself (any bed for a bed, any food for food, any flower for a flower). */
		public boolean accepts(ItemStack stack) {
			if (stack.isEmpty()) return false;
			if (want.equals(Market.FOOD)) return stack.has(DataComponents.FOOD);
			if (want.equals("minecraft:poppy")) return stack.is(net.minecraft.tags.ItemTags.SMALL_FLOWERS);
			return Market.matches(stack, item(want));
		}
	}

	public static Errands get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, Errands::new, Errands::load, Errands::save);
	}

	public List<Errand> all() {
		return errands;
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, (ServerTickEvent.Post event) -> {
			MinecraftServer server = event.getServer();
			if (server.getTickCount() % HINT_EVERY == 0) hints(server);
		});
	}

	// ---- posting ------------------------------------------------------------------------------

	/** Once a day per town (Roads.plan): stale errands down, the people's unmet wants up (OPEN at most). */
	static void daily(ServerLevel level, Roads.Network network, long day) {
		Errands all = get(level.getServer());
		Mayors.Office office = Towns.of(level, network.bell);
		// who had taken an errand that goes down now: their hint goes out too (it stayed on the screen till they left)
		java.util.Set<UUID> takers = new java.util.HashSet<>();
		for (Errand errand : all.errands) if (errand.taker != null) takers.add(errand.taker);
		all.errands.removeIf(errand -> errand.dimension.equals(level.dimension()) && errand.bell.closerThan(network.bell, TownRules.SPAN)
				&& (day - errand.posted >= DAYS || (office != null && !office.residents.containsKey(errand.villager) && level.getEntity(errand.villager) == null)));
		// met meanwhile another way (bought at the stall, the wish granted by hand): down from the board
		all.errands.removeIf(errand -> errand.dimension.equals(level.dimension()) && level.getEntity(errand.villager) instanceof Villager villager
				&& !stillWanted(errand, Family.state(villager)));
		for (Villager villager : Towns.people(level, network.bell, villager -> !villager.isBaby())) {
			FamilyState family = Family.state(villager);
			for (FamilyState.Want want : family.wants) {
				// a want of today may still be bought at the stall: the board gets it the day after
				// a workshop's order (Market.CHEST, into its chest) is no errand for a player, nor a key that names no item
				if (want.since >= day || want.count <= 0 || item(want.item).equals(Items.AIR)) continue;
				all.post(level, network.bell, villager, want.item, want.item.equals(Market.FOOD) ? FOOD : WANT, want.count, day);
			}
			// a wedding wish not granted yet (FamilyEvents.askForWish)
			if (!family.wish.isEmpty() && !family.wishGranted) all.post(level, network.bell, villager, family.wish, WISH, 1, day);
		}
		for (UUID taker : takers) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(taker);
			if (player != null && active(level.getServer(), taker) == null) hint(level.getServer(), player);
		}
		all.setDirty();
	}

	private static boolean stillWanted(Errand errand, FamilyState family) {
		if (errand.kind.equals(WISH)) return errand.want.equals(family.wish) && !family.wishGranted;
		for (FamilyState.Want want : family.wants) if (want.item.equals(errand.want) && want.count > 0) return true;
		return false;
	}

	/** An errand for this villager's want, unless one is up for it already or the board is full. */
	@Nullable Errand post(ServerLevel level, BlockPos bell, Villager villager, String want, String kind, int count, long day) {
		int open = 0;
		for (Errand errand : errands) {
			if (!errand.dimension.equals(level.dimension()) || !errand.bell.closerThan(bell, TownRules.SPAN)) continue;
			if (errand.villager.equals(villager.getUUID()) && errand.want.equals(want)) return errand;
			open++;
		}
		if (open >= OPEN) return null;
		Errand errand = new Errand(nextId++, level.dimension(), bell, villager.getUUID(), VillagerHome.nameOf(villager), want, count,
				reward(want, count), kind, day, villager.blockPosition());
		errands.add(errand);
		setDirty();
		return errand;
	}

	/**
	 * The item a want names (any food as bread), or air: a workshop's order ("chest:minecraft:flint") or an item of a
	 * mod gone. ResourceLocation.parse threw on the first, in the server's tick, and took the server down.
	 */
	static Item item(String want) {
		if (want.equals(Market.FOOD)) return Items.BREAD;
		ResourceLocation id = ResourceLocation.tryParse(want);
		return id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
	}

	/** Half again what the things cost at the stall: worth the walk. */
	static int reward(String want, int count) {
		ItemStack one = new ItemStack(item(want));
		return Math.max(2, Prices.price(one) * count * 3 / 2);
	}

	/** The town's errands, for its board. */
	public static List<Errand> of(ServerLevel level, BlockPos bell) {
		List<Errand> out = new ArrayList<>();
		for (Errand errand : get(level.getServer()).errands) {
			if (errand.dimension.equals(level.dimension()) && errand.bell.closerThan(bell, TownRules.SPAN)) out.add(errand);
		}
		return out;
	}

	public static @Nullable Errand active(MinecraftServer server, UUID player) {
		for (Errand errand : get(server).errands) if (player.equals(errand.taker)) return errand;
		return null;
	}

	// ---- the player -------------------------------------------------------------------------

	/** The player takes the errand (one at a time: one taken before goes back on the board). False: gone or another's. */
	public static boolean take(ServerLevel level, Player player, int id) {
		Errands all = get(level.getServer());
		Errand errand = null;
		for (Errand one : all.errands) if (one.id == id) errand = one;
		if (errand == null || (errand.taker != null && !errand.taker.equals(player.getUUID()))) return false;
		for (Errand one : all.errands) if (player.getUUID().equals(one.taker)) one.taker = null;
		errand.taker = player.getUUID();
		all.setDirty();
		if (player instanceof ServerPlayer server) hint(level.getServer(), server);
		return true;
	}

	/** The player gives up its errand: it is back on the board for anyone. */
	public static boolean cancel(ServerLevel level, Player player) {
		Errands all = get(level.getServer());
		boolean any = false;
		for (Errand one : all.errands) {
			if (player.getUUID().equals(one.taker)) {
				one.taker = null;
				any = true;
			}
		}
		all.setDirty();
		if (player instanceof ServerPlayer server) hint(level.getServer(), server);
		return any;
	}

	/**
	 * The player hands the villager what its errand asks for (Family.give, first): the things are the household's now
	 * (the want met, the wish granted), the player is paid and the village glad. False when this is not the errand's
	 * villager or thing (the stack in hand is then given the usual way).
	 */
	public static boolean deliver(ServerLevel level, Player player, Villager villager, ItemStack stack) {
		Errands all = get(level.getServer());
		Errand errand = active(level.getServer(), player.getUUID());
		if (errand == null || !errand.villager.equals(villager.getUUID()) || !errand.accepts(stack)) return false;
		if (stack.getCount() < errand.count) {
			com.vnap.Mc.say(player, Component.translatable("errand.villager_life.more", errand.count, errand.shown().getHoverName()), true);
			return true;
		}
		ItemStack brought = stack.split(errand.count);
		FamilyState family = Family.state(villager);
		switch (errand.kind) {
			case WISH -> {
				family.wishGranted = true;
				Speech.queue(villager, "Man Thanks for the Wish", player, 1L);
			}
			default -> {
				family.wants.removeIf(want -> want.item.equals(errand.want));
				villager.getInventory().addItem(brought);
				Speech.queue(villager, Family.female(villager) ? "Woman Thanks for the Flower" : "Man Thanks for the Flower", player, 1L);
			}
		}
		// the household pays when it can (it wanted the things); the treasury pays for what it cannot
		if (!Market.charge(level, villager, errand.reward)) {
			Mayors.Office office = Towns.of(level, errand.bell);
			if (office != null) Market.spendTreasury(level, office, errand.reward);
		}
		Coins.give(player, errand.reward);
		VillageMoodApi.applyAt(level, villager.blockPosition(), 2.0F, 1.0F, "errand");
		com.vnap.Mc.say(player, Component.translatable("errand.villager_life.done", errand.name, Coins.text(errand.reward)), false);
		all.errands.remove(errand);
		all.setDirty();
		if (player instanceof ServerPlayer server) hint(level.getServer(), server);
		com.vnap.progress.VillageAdvancements.award(player, "town/errand");
		return true;
	}

	/** Every few seconds: where the villager of each player's errand is now, sent to that player's screen. */
	private static void hints(MinecraftServer server) {
		Errands all = get(server);
		for (Errand errand : all.errands) {
			if (errand.taker == null) continue;
			ServerLevel level = server.getLevel(errand.dimension);
			if (level != null && level.getEntity(errand.villager) instanceof Villager villager) errand.seen = villager.blockPosition();
			ServerPlayer player = server.getPlayerList().getPlayer(errand.taker);
			if (player != null) hint(server, player);
		}
	}

	static void hint(MinecraftServer server, ServerPlayer player) {
		Errand errand = active(server, player.getUUID());
		PacketDistributor.sendToPlayer(player, errand == null ? ErrandPayload.NONE
				: new ErrandPayload(true, errand.name, errand.shown(), errand.reward, errand.seen, errand.dimension.equals(player.level().dimension())));
	}

	// ---- saving ------------------------------------------------------------------------------

	public static Errands load(CompoundTag tag, HolderLookup.Provider registries) {
		Errands all = new Errands();
		all.nextId = Math.max(1, Nbt.getInt(tag, "NextId"));
		for (Tag entry : Nbt.getList(tag, "Errands", Tag.TAG_COMPOUND)) {
			CompoundTag e = (CompoundTag) entry;
			ResourceLocation dimension = ResourceLocation.tryParse(Nbt.getString(e, "Dimension"));
			if (dimension == null || !Nbt.hasUUID(e, "Villager")) continue;
			Errand errand = new Errand(Nbt.getInt(e, "Id"), ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(Nbt.getLong(e, "Bell")), Nbt.getUUID(e, "Villager"),
					Nbt.getString(e, "Name"), Nbt.getString(e, "Want"), Nbt.getInt(e, "Count"), Nbt.getInt(e, "Reward"), Nbt.getString(e, "Kind"), Nbt.getLong(e, "Posted"), BlockPos.of(Nbt.getLong(e, "Seen")));
			if (Nbt.hasUUID(e, "Taker")) errand.taker = Nbt.getUUID(e, "Taker");
			all.errands.add(errand);
		}
		return all;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putInt("NextId", nextId);
		ListTag list = new ListTag();
		for (Errand errand : errands) {
			CompoundTag e = new CompoundTag();
			e.putInt("Id", errand.id);
			e.putString("Dimension", com.vnap.Mc.id(errand.dimension).toString());
			e.putLong("Bell", errand.bell.asLong());
			Nbt.putUUID(e, "Villager", errand.villager);
			e.putString("Name", errand.name);
			e.putString("Want", errand.want);
			e.putInt("Count", errand.count);
			e.putInt("Reward", errand.reward);
			e.putString("Kind", errand.kind);
			e.putLong("Posted", errand.posted);
			e.putLong("Seen", errand.seen.asLong());
			if (errand.taker != null) Nbt.putUUID(e, "Taker", errand.taker);
			list.add(e);
		}
		tag.put("Errands", list);
		return tag;
	}
}
