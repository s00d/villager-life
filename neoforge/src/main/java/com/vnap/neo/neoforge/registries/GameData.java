package com.vnap.neo.neoforge.registries;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.state.BlockState;

public final class GameData {
	private GameData() {
	}

	/** Which block state is which point of interest (vanilla's PoiTypes reads Forge's map). */
	public static Map<BlockState, Holder<PoiType>> getBlockStatePointOfInterestTypeMap() {
		return net.minecraftforge.registries.GameData.PoiTypeCallbacks.getStateToPoi();
	}
}
