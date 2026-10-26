package com.vnap.fab.neoforge.registries;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.state.BlockState;

public final class GameData {
	private GameData() {
	}

	/** Which block state is which point of interest: vanilla's own map (com.vnap.fab.mixin.PoiTypesAccessor). */
	public static Map<BlockState, Holder<PoiType>> getBlockStatePointOfInterestTypeMap() {
		return com.vnap.fab.mixin.PoiTypesAccessor.vnap$typeByState();
	}
}
