package com.vnap.item;

import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Shops;
import com.vnap.village.town.Roads;
import com.vnap.village.town.TownPlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
//? if <1.21.2
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A blank village map: used in a town it turns into a filled map of it, drawn at once, centred on the bell, with a
 * mark for the bell, the town hall, the stall and the town's buildings (chapel, bakehouse, tavern).
 */
public final class TownMapItem extends Item {
	public TownMapItem(Properties properties) {
		super(properties);
	}

	@Override
	//? if >=1.21.2 {
	/*public net.minecraft.world.InteractionResult use(Level level, Player player, InteractionHand hand) {
	*///?} else
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server)) return com.vnap.Mc.success(held);
		Mayors.Office office = Towns.of(server, player.blockPosition());
		if (office == null) {
			com.vnap.Mc.say(player, Component.translatable("board.villager_life.no_town"), true);
			return com.vnap.Mc.fail(held);
		}
		BlockPos bell = office.bell;
		ItemStack map = centred(server, bell, SCALE);
		MapItemSavedData.addTargetDecoration(map, bell, "bell", MapDecorationTypes.TARGET_POINT);
		for (com.vnap.village.home.HouseProject house : com.vnap.mood.VillageMoodSavedData.get(server).houses()) {
			if (house.done && Mayors.HALLS.contains(house.plan) && Towns.ours(server, bell, house.origin)) mark(map, house.origin, "hall", MapDecorationTypes.RED_BANNER);
		}
		Shops.Shop shop = Shops.near(server, bell);
		if (shop != null && shop.counter != null) mark(map, shop.counter, "stall", MapDecorationTypes.YELLOW_BANNER);
		Roads.Network network = Roads.get(server.getServer()).near(server.dimension(), bell);
		if (network != null) {
			mark(map, TownPlots.at(network, TownPlots.Kind.CHAPEL, BlockPos.ZERO), "chapel", MapDecorationTypes.WHITE_BANNER);
			mark(map, TownPlots.at(network, TownPlots.Kind.BAKEHOUSE, BlockPos.ZERO), "bakehouse", MapDecorationTypes.BROWN_BANNER);
			mark(map, TownPlots.at(network, TownPlots.Kind.TAVERN, BlockPos.ZERO), "tavern", MapDecorationTypes.ORANGE_BANNER);
		}
		map.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.translatable("item.villager_life.town_map.filled"));
		com.vnap.progress.VillageAdvancements.award(player, "town/map");
		if (!player.getAbilities().instabuild) held.shrink(1);
		if (held.isEmpty()) return com.vnap.Mc.success(map);
		if (!player.getInventory().add(map)) com.vnap.Mc.toss(player, map);
		return com.vnap.Mc.success(held);
	}

	private static final byte SCALE = 1;

	/**
	 * A map centred on the bell itself (a vanilla map snaps to a 128-block grid: the town sat in its corner), drawn at
	 * once from the loaded world the way a held map draws it: each pixel the colour of its column's top block, shaded
	 * by the step from its neighbour to the north. Columns not loaded stay blank and fill in as the map is carried.
	 */
	private static ItemStack centred(ServerLevel level, BlockPos centre, byte scale) {
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putString("dimension", com.vnap.Mc.id(level.dimension()).toString());
		// blank: the codec of 1.21.5 wants the colours there (the load of 1.21.1 fills them in itself)
		tag.putByteArray("colors", new byte[128 * 128]);
		tag.putInt("xCenter", centre.getX());
		tag.putInt("zCenter", centre.getZ());
		tag.putByte("scale", scale);
		tag.putBoolean("trackingPosition", true);
		tag.putBoolean("unlimitedTracking", true);
		tag.putBoolean("locked", false);
		//? if >=1.21.5 {
		/*MapItemSavedData data = MapItemSavedData.CODEC.parse(level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag).getOrThrow();
		*///?} else
		MapItemSavedData data = MapItemSavedData.load(tag, level.registryAccess());
		int step = 1 << scale;
		int[] previous = new int[128];
		for (int px = 0; px < 128; px++) {
			for (int pz = -1; pz < 128; pz++) {
				int x = centre.getX() + (px - 64) * step;
				int z = centre.getZ() + (pz - 64) * step;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					if (pz >= 0) previous[px] = Integer.MIN_VALUE;
					continue;
				}
				int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				BlockPos top = new BlockPos(x, y, z);
				net.minecraft.world.level.block.state.BlockState state = level.getBlockState(top);
				while (y > level.getMinBuildHeight() && state.getMapColor(level, top).equals(net.minecraft.world.level.material.MapColor.NONE)) {
					top = top.below();
					state = level.getBlockState(top);
					y--;
				}
				if (pz >= 0) {
					double d = previous[px] == Integer.MIN_VALUE ? 0.0 : (y - previous[px]) * 4.0 / (scale + 4) + (((px + pz) & 1) - 0.5) * 0.4;
					net.minecraft.world.level.material.MapColor.Brightness brightness = d > 0.6 ? net.minecraft.world.level.material.MapColor.Brightness.HIGH
							: d < -0.6 ? net.minecraft.world.level.material.MapColor.Brightness.LOW : net.minecraft.world.level.material.MapColor.Brightness.NORMAL;
					data.setColor(px, pz, state.getMapColor(level, top).getPackedId(brightness));
				}
				previous[px] = y;
			}
		}
		net.minecraft.world.level.saveddata.maps.MapId id = level.getFreeMapId();
		level.setMapData(id, data);
		ItemStack map = new ItemStack(net.minecraft.world.item.Items.FILLED_MAP);
		map.set(net.minecraft.core.component.DataComponents.MAP_ID, id);
		return map;
	}

	private static void mark(ItemStack map, @org.jspecify.annotations.Nullable BlockPos pos, String id, Holder<MapDecorationType> type) {
		if (pos != null) MapItemSavedData.addTargetDecoration(map, pos, id, type);
	}
}
